// pigeons/wifi_coordinate.dart
import 'package:pigeon/pigeon.dart';

class Coordinate {
  /// 緯度
  double lat;

  /// 経度
  double lng;

  /// 接続またはスキャンした Wi-Fi の SSID
  String ssid;

  /// 端末で測定したエポック時刻（ミリ秒）
  int epochMillis;

  /// 測定した日付（例: "2025-06-04"）
  String date;

  /// 測定した時刻（例: "08:31:52"）
  String time;

  Coordinate({
    required this.lat,
    required this.lng,
    required this.ssid,
    required this.epochMillis,
    required this.date,
    required this.time,
  });
}

/// Flutter → Kotlin：サービス制御
@HostApi()
abstract class ServiceControlApi {
  void startService();

  void stopService();
}

/// Kotlin → Flutter：座標 Push
@FlutterApi()
abstract class CoordinatePushApi {
  void onCoordinate(Coordinate coordinate);
}
