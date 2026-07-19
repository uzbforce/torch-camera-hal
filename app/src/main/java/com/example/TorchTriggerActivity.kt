package com.example

import android.app.Activity
import android.os.Bundle
import android.util.Log

class TorchTriggerActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("TorchTriggerActivity", "onCreate: triggering torch via transparent activity to satisfy foreground requirements")
        try {
            val controller = TorchController(applicationContext)
            controller.toggleTorch()
        } catch (e: Exception) {
            Log.e("TorchTriggerActivity", "Error toggling torch in foreground transition activity", e)
        }
        finish()
    }
}
