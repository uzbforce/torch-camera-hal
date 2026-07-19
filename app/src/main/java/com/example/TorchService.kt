package com.example

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Log
import android.view.Surface
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.flow.MutableStateFlow

class TorchService : Service() {

    companion object {
        private const val TAG = "TorchService"
        const val CHANNEL_ID = "torch_service_channel"
        const val NOTIFICATION_ID = 8829
        
        const val ACTION_START = "com.example.action.START"
        const val ACTION_STOP = "com.example.action.STOP"

        // State flows to communicate with UI
        val isTorchOn = MutableStateFlow(false)
        val errorMessage = MutableStateFlow<String?>(null)
    }

    private var cameraManager: CameraManager? = null
    private var cameraDevice: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var surfaceTexture: SurfaceTexture? = null
    private var dummySurface: Surface? = null
    
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate: TorchService initialized")
        createNotificationChannel()
        startBackgroundThread()
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        Log.d(TAG, "onStartCommand: action = $action")
        
        if (action == ACTION_STOP) {
            stopTorch()
            return START_NOT_STICKY
        }

        if (action == ACTION_START) {
            startTorchInForeground()
        }

        return START_NOT_STICKY
    }

    private fun startTorchInForeground() {
        // Build notification with "Turn Off" quick action button
        val stopIntent = Intent(this, TorchService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Torch Active")
            .setContentText("Camera API fallback mode is running.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Turn Off", stopPendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            openCameraAndTurnOnTorch()
        } catch (e: Exception) {
            Log.e(TAG, "startForeground failed", e)
            errorMessage.value = "Failed to start service: ${e.message}"
            stopSelf()
        }
    }

    private fun openCameraAndTurnOnTorch() {
        val manager = cameraManager ?: return
        try {
            val cameraId = findBackCameraWithFlash(manager)
            if (cameraId == null) {
                errorMessage.value = "No compatible back camera with flash found."
                stopTorch()
                return
            }

            // Open the camera device
            manager.openCamera(cameraId, object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    Log.d(TAG, "Camera opened successfully")
                    cameraDevice = camera
                    setupTorchCaptureSession(camera)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    Log.d(TAG, "Camera disconnected")
                    stopTorch()
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    Log.e(TAG, "Camera error: $error")
                    errorMessage.value = "Camera opened error: $error"
                    stopTorch()
                }
            }, backgroundHandler)

        } catch (e: SecurityException) {
            Log.e(TAG, "Camera permission missing", e)
            errorMessage.value = "Camera permission missing. Please grant permission."
            stopTorch()
        } catch (e: Exception) {
            Log.e(TAG, "Error opening camera", e)
            errorMessage.value = "Error opening camera: ${e.localizedMessage}"
            stopTorch()
        }
    }

    private fun setupTorchCaptureSession(camera: CameraDevice) {
        try {
            // Create a lightweight, off-screen dummy surface texture
            surfaceTexture = SurfaceTexture(10).apply {
                setDefaultBufferSize(640, 480)
            }
            val surface = Surface(surfaceTexture)
            dummySurface = surface

            val captureRequestBuilder = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                addTarget(surface)
                // Critical keys to force torch mode on
                set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
                set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            }

            @Suppress("DEPRECATION")
            camera.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(session: CameraCaptureSession) {
                    Log.d(TAG, "Camera capture session configured")
                    captureSession = session
                    try {
                        session.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler)
                        isTorchOn.value = true
                        errorMessage.value = null
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start repeating flash request", e)
                        errorMessage.value = "Flash command failed: ${e.message}"
                        stopTorch()
                    }
                }

                override fun onConfigureFailed(session: CameraCaptureSession) {
                    Log.e(TAG, "Camera session configuration failed")
                    errorMessage.value = "Camera session configuration failed"
                    stopTorch()
                }
            }, backgroundHandler)

        } catch (e: Exception) {
            Log.e(TAG, "Error setting up capture session", e)
            errorMessage.value = "Capture setup failed: ${e.message}"
            stopTorch()
        }
    }

    private fun findBackCameraWithFlash(manager: CameraManager): String? {
        for (id in manager.cameraIdList) {
            val chars = manager.getCameraCharacteristics(id)
            val lensFacing = chars.get(CameraCharacteristics.LENS_FACING)
            val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
            if (lensFacing == CameraCharacteristics.LENS_FACING_BACK && hasFlash) {
                return id
            }
        }
        // Fallback: search for any camera with flash if no rear lens facing is found
        for (id in manager.cameraIdList) {
            val chars = manager.getCameraCharacteristics(id)
            val hasFlash = chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
            if (hasFlash) {
                return id
            }
        }
        return null
    }

    private fun stopTorch() {
        Log.d(TAG, "Stopping torch and resetting services")
        
        try {
            captureSession?.stopRepeating()
            captureSession?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing capture session", e)
        } finally {
            captureSession = null
        }

        try {
            cameraDevice?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing camera device", e)
        } finally {
            cameraDevice = null
        }

        dummySurface?.release()
        dummySurface = null
        
        surfaceTexture?.release()
        surfaceTexture = null

        isTorchOn.value = false
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun startBackgroundThread() {
        backgroundThread = HandlerThread("TorchCameraBackground").also { it.start() }
        backgroundHandler = Handler(backgroundThread!!.looper)
    }

    private fun stopBackgroundThread() {
        backgroundThread?.quitSafely()
        try {
            backgroundThread?.join()
            backgroundThread = null
            backgroundHandler = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping background thread", e)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Hardware Flashlight",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the flashlight active using Camera Hardware API workaround."
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        stopTorch()
        stopBackgroundThread()
        super.onDestroy()
        Log.d(TAG, "onDestroy: TorchService cleaned up")
    }
}
