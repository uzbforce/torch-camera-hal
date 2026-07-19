package com.example

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class TorchMode {
    STANDARD,      // High-level System Torch (CameraManager.setTorchMode)
    CAMERA_API     // Hardware fallback using Camera2 CaptureSession (our background service)
}

class TorchController(private val context: Context) {
    private val tag = "TorchController"
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val prefs = context.getSharedPreferences("torch_prefs", Context.MODE_PRIVATE)
    
    private val _currentMode = MutableStateFlow(getPersistedMode())
    val currentMode: StateFlow<TorchMode> = _currentMode.asStateFlow()

    private val _isStandardTorchOn = MutableStateFlow(false)
    val isStandardTorchOn: StateFlow<Boolean> = _isStandardTorchOn.asStateFlow()

    private val _torchStrength = MutableStateFlow(prefs.getInt("torch_strength_level", 1))
    val torchStrength: StateFlow<Int> = _torchStrength.asStateFlow()

    private var cameraId: String? = null

    init {
        try {
            // Find rear-facing camera with a flash
            cameraId = cameraManager.cameraIdList.firstOrNull { id ->
                val chars = cameraManager.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK &&
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: cameraManager.cameraIdList.firstOrNull()

            // Listen to real-time system torch toggles to synchronize state
            cameraManager.registerTorchCallback(object : CameraManager.TorchCallback() {
                override fun onTorchModeChanged(id: String, enabled: Boolean) {
                    if (id == cameraId) {
                        _isStandardTorchOn.value = enabled
                    }
                }
            }, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            Log.e(tag, "Failed to initialize TorchController", e)
        }
    }

    private fun getPersistedMode(): TorchMode {
        val saved = prefs.getString("selected_mode", TorchMode.CAMERA_API.name)
        return try {
            TorchMode.valueOf(saved ?: TorchMode.CAMERA_API.name)
        } catch (e: Exception) {
            TorchMode.CAMERA_API
        }
    }

    fun setMode(mode: TorchMode) {
        prefs.edit().putString("selected_mode", mode.name).apply()
        _currentMode.value = mode
        // If switching modes, stop active torch running in the prior mode
        if (mode == TorchMode.STANDARD) {
            stopCameraApiTorch()
        } else {
            stopStandardTorch()
        }
    }

    fun toggleTorch(): Boolean {
        val mode = getPersistedMode()
        return when (mode) {
            TorchMode.STANDARD -> {
                val newState = !_isStandardTorchOn.value
                setStandardTorch(newState)
                newState
            }
            TorchMode.CAMERA_API -> {
                val newState = !TorchService.isTorchOn.value
                if (newState) {
                    startCameraApiTorch()
                } else {
                    stopCameraApiTorch()
                }
                newState
            }
        }
    }

    fun setTorchState(enabled: Boolean) {
        val mode = getPersistedMode()
        when (mode) {
            TorchMode.STANDARD -> setStandardTorch(enabled)
            TorchMode.CAMERA_API -> {
                if (enabled) startCameraApiTorch() else stopCameraApiTorch()
            }
        }
    }

    fun getMaxTorchStrengthLevel(): Int {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val id = cameraId ?: return 1
            try {
                val chars = cameraManager.getCameraCharacteristics(id)
                val field = CameraCharacteristics::class.java.getDeclaredField("FLASH_INFO_STRENGTH_MAX_LEVEL")
                @Suppress("UNCHECKED_CAST")
                val key = field.get(null) as? CameraCharacteristics.Key<Int>
                if (key != null) {
                    return chars.get(key) ?: 1
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to read max torch strength via reflection", e)
            }
        }
        return 1
    }

    fun setTorchStrengthLevel(level: Int) {
        prefs.edit().putInt("torch_strength_level", level).apply()
        _torchStrength.value = level
        // If torch is currently active in standard mode, update its strength live
        if (_isStandardTorchOn.value && _currentMode.value == TorchMode.STANDARD) {
            setStandardTorch(true)
        }
    }

    private fun setStandardTorch(enabled: Boolean) {
        val id = cameraId ?: return
        try {
            if (enabled) {
                val level = _torchStrength.value
                val maxLevel = getMaxTorchStrengthLevel()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && maxLevel > 1) {
                    val targetLevel = level.coerceIn(1, maxLevel)
                    try {
                        val method = CameraManager::class.java.getMethod(
                            "turnOnTorchWithStrengthLevel",
                            String::class.java,
                            Int::class.javaPrimitiveType
                        )
                        method.invoke(cameraManager, id, targetLevel)
                    } catch (e: Exception) {
                        Log.e(tag, "turnOnTorchWithStrengthLevel via reflection failed", e)
                        cameraManager.setTorchMode(id, true)
                    }
                } else {
                    cameraManager.setTorchMode(id, true)
                }
                _isStandardTorchOn.value = true
            } else {
                cameraManager.setTorchMode(id, false)
                _isStandardTorchOn.value = false
            }
        } catch (e: Exception) {
            Log.e(tag, "setTorchMode failed", e)
            TorchService.errorMessage.value = "Standard mode failed: ${e.localizedMessage}. Try Camera API fallback mode!"
        }
    }

    private fun stopStandardTorch() {
        val id = cameraId ?: return
        try {
            cameraManager.setTorchMode(id, false)
            _isStandardTorchOn.value = false
        } catch (e: Exception) {
            Log.e(tag, "stopStandardTorch failed", e)
        }
    }

    private fun startCameraApiTorch() {
        val intent = Intent(context, TorchService::class.java).apply {
            action = TorchService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    private fun stopCameraApiTorch() {
        val intent = Intent(context, TorchService::class.java).apply {
            action = TorchService.ACTION_STOP
        }
        context.startService(intent)
    }
}
