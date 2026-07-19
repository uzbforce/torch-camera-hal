package com.example

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LogLevel {
    INFO, SUCCESS, WARNING, ERROR
}

data class LogMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: String,
    val level: LogLevel,
    val text: String
)

class TorchViewModel(application: Application) : AndroidViewModel(application) {
    private val tag = "TorchViewModel"
    private val context = application.applicationContext
    
    val torchController = TorchController(context)

    // Mode state
    val currentMode: StateFlow<TorchMode> = torchController.currentMode

    // Brightness state
    val torchStrength: StateFlow<Int> = torchController.torchStrength

    fun getMaxTorchStrengthLevel(): Int = torchController.getMaxTorchStrengthLevel()

    fun setTorchStrengthLevel(level: Int) {
        val maxLevel = getMaxTorchStrengthLevel()
        torchController.setTorchStrengthLevel(level)
        addLog(LogLevel.INFO, "Updated torch strength level: $level / $maxLevel")
    }

    // Real-time diagnostics console logs
    private val _logs = MutableStateFlow<List<LogMessage>>(emptyList())
    val logs: StateFlow<List<LogMessage>> = _logs.asStateFlow()

    // Combined active torch state (from either standard callback or service state)
    val isTorchActive: StateFlow<Boolean> = combine(
        currentMode,
        torchController.isStandardTorchOn,
        TorchService.isTorchOn
    ) { mode, stdOn, serviceOn ->
        when (mode) {
            TorchMode.STANDARD -> stdOn
            TorchMode.CAMERA_API -> serviceOn
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = false
    )

    // Current error reported from camera callbacks
    val currentError: StateFlow<String?> = TorchService.errorMessage

    init {
        addLog(LogLevel.INFO, "Smart Torch initialized successfully.")
        addLog(LogLevel.INFO, "Android Version SDK: ${android.os.Build.VERSION.SDK_INT}")
        addLog(LogLevel.INFO, "ROM Manufacturer: ${android.os.Build.MANUFACTURER}, Model: ${android.os.Build.MODEL}")
        addLog(LogLevel.WARNING, "Standard setTorchMode is often buggy on custom ROMs.")

        // Listen for standard torch changes to post logs
        viewModelScope.launch {
            torchController.isStandardTorchOn.collect { on ->
                if (currentMode.value == TorchMode.STANDARD) {
                    addLog(
                        if (on) LogLevel.SUCCESS else LogLevel.INFO,
                        "System standard torch changed to: ${if (on) "ON" else "OFF"}"
                    )
                }
            }
        }

        // Listen for Camera API service changes to post logs
        viewModelScope.launch {
            TorchService.isTorchOn.collect { on ->
                if (currentMode.value == TorchMode.CAMERA_API) {
                    addLog(
                        if (on) LogLevel.SUCCESS else LogLevel.INFO,
                        "Camera API workaround torch changed to: ${if (on) "ON" else "OFF"}"
                    )
                }
            }
        }

        // Listen for errors
        viewModelScope.launch {
            TorchService.errorMessage.collect { err ->
                if (err != null) {
                    addLog(LogLevel.ERROR, err)
                }
            }
        }
    }

    fun setMode(mode: TorchMode) {
        if (currentMode.value == mode) return
        torchController.setMode(mode)
        addLog(LogLevel.INFO, "Switched mode to: ${mode.name}")
    }

    fun toggleTorch(onRequestCameraPermission: () -> Unit) {
        // In Camera API mode, verify we have permission first
        if (currentMode.value == TorchMode.CAMERA_API) {
            val hasPermission = ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                addLog(LogLevel.WARNING, "Camera API requires runtime camera permission. Requesting...")
                onRequestCameraPermission()
                return
            }
        }

        val wasActive = isTorchActive.value
        addLog(LogLevel.INFO, "Toggling power switch. Active state was: $wasActive")

        if (wasActive) {
            torchController.setTorchState(false)
        } else {
            torchController.setTorchState(true)
        }
    }

    fun addLog(level: LogLevel, text: String) {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
        val time = sdf.format(Date())
        val message = LogMessage(timestamp = time, level = level, text = text)
        
        // Keep logs capped at last 100 items to avoid excessive memory
        val currentList = _logs.value.takeLast(99)
        _logs.value = currentList + message
    }

    fun clearLogs() {
        _logs.value = emptyList()
        addLog(LogLevel.INFO, "Diagnostics console cleared.")
    }

    fun onCameraPermissionGranted() {
        addLog(LogLevel.SUCCESS, "Camera permission successfully GRANTED.")
        torchController.setTorchState(true)
    }

    fun onCameraPermissionDenied() {
        addLog(LogLevel.ERROR, "Camera permission was DENIED. Fallback API cannot open hardware device.")
    }

    override fun onCleared() {
        super.onCleared()
    }
}
