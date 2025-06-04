package com.example.wifi_location_service_demo

import android.content.Intent
import androidx.core.content.ContextCompat
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine

/* ★ 生成クラスを直接 import */
import com.example.wifi_location_service_demo.CoordinatePushApi
import com.example.wifi_location_service_demo.ServiceControlApi

class MainActivity : FlutterActivity() {

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        /* Kotlin → Flutter 送信用インスタンス */
        val pushApi = CoordinatePushApi(flutterEngine.dartExecutor.binaryMessenger)
        WifiLocationService.coordinateApi = pushApi     // Service から呼び出すため保持

        /* Flutter → Kotlin 受信用実装 */
        ServiceControlApi.setUp(
            flutterEngine.dartExecutor.binaryMessenger,
            object : ServiceControlApi {
                override fun startService() {
                    val i = Intent(this@MainActivity, WifiLocationService::class.java)
                    ContextCompat.startForegroundService(this@MainActivity, i)
                }

                override fun stopService() {
                    stopService(Intent(this@MainActivity, WifiLocationService::class.java))
                }
            }
        )
    }
}
