package com.example.wifi_location_service_demo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.*
import androidx.core.app.NotificationCompat
import java.time.*
import java.time.format.DateTimeFormatter
import com.example.wifi_location_service_demo.Coordinate
import com.example.wifi_location_service_demo.CoordinatePushApi


/* import 追加 */
import android.net.wifi.WifiManager
import android.content.pm.PackageManager
import android.net.wifi.ScanResult
import com.google.android.gms.location.*


import android.content.Context          // ★ これを追加


class WifiLocationService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private val NOTIF_ID = 1
    private val CHANNEL_ID = "wifi_location"


    /* 追加フィールド */
    private lateinit var wifiManager: WifiManager
    private lateinit var fused: FusedLocationProviderClient


    ///
    companion object {
        @Volatile
        var coordinateApi: CoordinatePushApi? = null
    }

    ///
    /* ---------- Service ライフサイクル ---------- */
    override fun onCreate() {
        super.onCreate()


        /* ★ ここで代入 */
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        fused = LocationServices.getFusedLocationProviderClient(this)





        createChannel()
        startForeground(NOTIF_ID, buildNotif("Scanning…"))
        handler.post(scanTask)
    }

    ///
    override fun onDestroy() {
        handler.removeCallbacks(scanTask)
        super.onDestroy()
    }

    ///
    /* ★ 追加：明示的に START_STICKY を返す */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // ここでは特に intent を解析しない
        return START_STICKY
    }

    ///
    /* バインドしない Foreground Service */
    override fun onBind(intent: Intent?): IBinder? = null

    ///
    /* ---------- 1 分ごとに座標送信 ---------- */
    private val scanTask = object : Runnable {
        override fun run() {
//            sendDummy()
            sendReal()

            handler.postDelayed(this, 60_000)  // 1 分
        }
    }

    ///
    private fun sendDummy() {
        val now = LocalDateTime.now()
        val coord = Coordinate(
            lat = 35.0 + Math.random(),
            lng = 139.0 + Math.random(),
            ssid = "SampleSSID",
            epochMillis = System.currentTimeMillis(),
            date = now.toLocalDate().toString(),
            time = now.toLocalTime()
                .format(DateTimeFormatter.ISO_LOCAL_TIME)
        )
        /* callback が必須なので空ラムダを渡す */
        coordinateApi?.onCoordinate(coord) {}
    }


    /* ---------- 実測値送信 ---------- */
    private fun sendReal() {
        if (checkSelfPermission(android.Manifest.permission.NEARBY_WIFI_DEVICES)
            != PackageManager.PERMISSION_GRANTED
        ) return        // 権限が拒否されている場合は送信せず

        wifiManager.startScan()                             // 非同期スキャン開始
        val results: List<ScanResult> = wifiManager.scanResults
        val best = results.maxByOrNull { it.level } ?: return // 電波強度最大 SSID を採用

        fused.lastLocation.addOnSuccessListener { loc ->
            if (loc == null) return@addOnSuccessListener
            val now = LocalDateTime.now()
            val coord = Coordinate(
                lat = loc.latitude,
                lng = loc.longitude,
                ssid = best.SSID ?: "(unknown)",
                epochMillis = System.currentTimeMillis(),
                date = now.toLocalDate().toString(),
                time = now.toLocalTime()
                    .format(DateTimeFormatter.ISO_LOCAL_TIME)
            )
            coordinateApi?.onCoordinate(coord) {}
        }
    }


    ///
    /* ---------- 通知 ---------- */
    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Wi-Fi Coordinate",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    ///
    private fun buildNotif(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Wi-Fi Location")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
}
