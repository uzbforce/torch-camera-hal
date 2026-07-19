package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class TorchTileService : TileService() {
    private val tag = "TorchTileService"
    private var serviceScope: CoroutineScope? = null
    private lateinit var torchController: TorchController

    override fun onCreate() {
        super.onCreate()
        torchController = TorchController(applicationContext)
    }

    override fun onStartListening() {
        super.onStartListening()
        Log.d(tag, "onStartListening: updating tile state")
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

        // Observe torch state and update tile
        serviceScope?.launch {
            combine(
                torchController.currentMode,
                torchController.isStandardTorchOn,
                TorchService.isTorchOn
            ) { mode, stdOn, serviceOn ->
                when (mode) {
                    TorchMode.STANDARD -> stdOn
                    TorchMode.CAMERA_API -> serviceOn
                }
            }.collect { isActive ->
                updateTileState(isActive)
            }
        }
    }

    override fun onStopListening() {
        super.onStopListening()
        Log.d(tag, "onStopListening")
        serviceScope?.cancel()
        serviceScope = null
    }

    override fun onClick() {
        super.onClick()
        Log.d(tag, "onClick: toggling torch from quick tile")

        val currentMode = torchController.currentMode.value

        if (currentMode == TorchMode.STANDARD) {
            // Standard mode doesn't have background start restrictions or need camera permission
            torchController.toggleTorch()
            return
        }

        // Check camera permission for Camera API workaround mode
        val hasPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission) {
            Toast.makeText(this, "Camera permission needed. Opening TorchFix...", Toast.LENGTH_LONG).show()
            val intent = Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            launchActivityWithCollapse(intent)
            return
        }

        // To satisfy Android 14+ FGS restrictions for CAMERA type,
        // we start a transparent activity to toggle the state from the foreground.
        val intent = Intent(this, TorchTriggerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        launchActivityWithCollapse(intent)
    }

    private fun launchActivityWithCollapse(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = android.app.PendingIntent.getActivity(
                this,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTileState(isActive: Boolean) {
        val tile = qsTile ?: return
        tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        
        // Use our beautiful custom flashlight vector drawable icon
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            tile.icon = Icon.createWithResource(this, com.example.R.drawable.ic_tile_torch)
        }
        
        tile.label = "TorchFix"
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (isActive) "ON" else "OFF"
        }
        tile.updateTile()
    }
}
