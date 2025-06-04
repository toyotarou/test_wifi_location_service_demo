package com.example.wifi_location_service_demo

/* ───────────── Android 基本 ───────────── */
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.net.wifi.ScanResult
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

/* ───────────── Google Play Services ───── */
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices

/* ───────────── Flutter headless Engine ── */
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.embedding.engine.loader.FlutterLoader
import io.flutter.plugin.common.MethodChannel

/* ───────────── Java / Time ────────────── */
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/* ───────────── Pigeon クラス ───────────── */
import com.example.wifi_location_service_demo.Coordinate
import com.example.wifi_location_service_demo.CoordinatePushApi

/**
 * Foreground Service:
 *  - 端末再起動後も自動起動（BootReceiver から）
 *  - 1 分おきに Wi-Fi スキャン + 位置取得
 *  - 取得座標を UI へ (Pigeon) & 背景 Dart isolate へ (MethodChannel) 送信
 */
class WifiLocationService : Service() {

    /* ── 通知 & 周期 ─────────────────────── */
    private val NOTIF_ID = 1
    private val CHANNEL_ID = "wifi_location"
    private val INTERVAL_MS = 60_000L      // 1 分

    /* ── Android API ─────────────────────── */
    private lateinit var wifiManager: WifiManager
    private lateinit var fused: FusedLocationProviderClient

    /* ── 背景 FlutterEngine ──────────────── */
    private lateinit var bgEngine: FlutterEngine
    private lateinit var bgChannel: MethodChannel
    private val BG_CHANNEL_NAME = "bg_channel"

    /* ── Pigeon: UI 側へプッシュ ─────────── */
    companion object {
        @Volatile
        var coordinateApi: CoordinatePushApi? = null
    }

    /* ── 1 分ごとに実行する Runnable ─────── */
    private val handler = Handler(Looper.getMainLooper())
    private val scanTask = object : Runnable {
        override fun run() {
            sendReal()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    /* ───────────── Lifecycle ───────────── */

    override fun onCreate() {
        super.onCreate()

        /* Android サービス初期化 */
        wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        fused = LocationServices.getFusedLocationProviderClient(this)

        /* ★ 背景 FlutterEngine を headless で起動 */
        val loader = FlutterLoader()
        loader.startInitialization(this)
        loader.ensureInitializationComplete(this, null)

        bgEngine = FlutterEngine(this).apply {
            dartExecutor.executeDartEntrypoint(
                DartExecutor.DartEntrypoint(
                    loader.findAppBundlePath(),   // → app.dill へのパス
                    "backgroundMain"              // → lib/background_main.dart の関数名
                )
            )
        }
        bgChannel = MethodChannel(bgEngine.dartExecutor.binaryMessenger, BG_CHANNEL_NAME)

        /* Foreground 通知 */
        createChannel()
        startForeground(NOTIF_ID, buildNotif("Wi-Fi スキャン実行中…"))

        /* 周期タスク開始 */
        handler.post(scanTask)
    }

    /** 強制終了しても OS が Service を再生成するよう START_STICKY を返す */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(scanTask)
        bgEngine.destroy()        // headless Engine を解放
        super.onDestroy()
    }

    /** バインドしない Foreground Service */
    override fun onBind(intent: Intent?): IBinder? = null

    /* ───────────── 実測値送信 ───────────── */

    private fun sendReal() {
        // Android 13+ では NEARBY_WIFI_DEVICES 権限が必要
        if (checkSelfPermission(android.Manifest.permission.NEARBY_WIFI_DEVICES)
            != PackageManager.PERMISSION_GRANTED
        ) return

        wifiManager.startScan()
        val best: ScanResult =
            wifiManager.scanResults.maxByOrNull { it.level } ?: return

        fused.lastLocation.addOnSuccessListener { loc ->
            if (loc == null) return@addOnSuccessListener

            val now = LocalDateTime.now()
            val date = now.toLocalDate().toString()
            val time = now.toLocalTime().format(DateTimeFormatter.ISO_LOCAL_TIME)

            /* ---------- UI が前面にあれば Pigeon で送る ---------- */
            coordinateApi?.onCoordinate(
                Coordinate(
                    lat = loc.latitude,
                    lng = loc.longitude,
                    ssid = best.SSID ?: "(unknown)",
                    epochMillis = System.currentTimeMillis(),
                    date = date,
                    time = time
                )
            ) {}

            /* ---------- 背景 Dart isolate に保存依頼 ---------- */
            val map = mapOf(
                "lat" to loc.latitude,
                "lng" to loc.longitude,
                "ssid" to (best.SSID ?: "(unknown)"),
                "epoch" to System.currentTimeMillis(),
                "date" to date,
                "time" to time
            )
            bgChannel.invokeMethod("saveCoordinate", map)
        }
    }

    /* ───────────── 通知関連 ───────────── */

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Wi-Fi Location Background Service",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun buildNotif(text: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Wi-Fi Location Running")
            .setContentText(text)
            .setOngoing(true)
            .build()
}
