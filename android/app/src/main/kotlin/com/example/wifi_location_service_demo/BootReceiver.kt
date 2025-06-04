package com.example.wifi_location_service_demo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * 端末起動直後（BOOT_COMPLETED）に呼ばれ、
 * Foreground Service を自動的に開始します。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val serviceIntent = Intent(context, WifiLocationService::class.java)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }
}
