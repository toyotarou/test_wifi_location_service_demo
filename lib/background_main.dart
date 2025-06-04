// lib/background_main.dart
//
// Foreground Service から起動された “ヘッドレス” FlutterEngine が
// このファイルの `backgroundMain()` を実行します。
// Kotlin → Dart の MethodChannel で受け取った座標を Isar に書き込み、
// 必要ならバッテリー節約のために最小限の処理だけ行う設計です。

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter/foundation.dart';
import 'package:isar/isar.dart';
import 'package:path_provider/path_provider.dart' show getApplicationDocumentsDirectory;

/* ─────────── Isar モデル ─────────── */
// モデルファイルは自由に定義してください。
// ここでは最小構成の CoordinateModel を例示。
part 'background_main.g.dart';

@collection
class CoordinateModel {
  Id id = Isar.autoIncrement;

  late double lat;
  late double lng;
  late String ssid;
  late int epoch; // millis
  late String date;
  late String time;
}

/* ─────────── エントリポイント ─────────── */

@pragma('vm:entry-point') // ← headless で呼び出すため必須
Future<void> backgroundMain() async {
  WidgetsFlutterBinding.ensureInitialized();

  /* 1. Isar を開く（シングルインスタンス） */
  final dir = await getApplicationDocumentsDirectory();
  final isar = await Isar.open([CoordinateModelSchema], directory: dir.path);

  /* 2. MethodChannel をセットアップ */
  const channel = MethodChannel('bg_channel');
  channel.setMethodCallHandler((call) async {
    if (call.method == 'saveCoordinate') {
      final m = Map<String, dynamic>.from(call.arguments as Map);
      final coord = CoordinateModel()
        ..lat = (m['lat'] as num).toDouble()
        ..lng = (m['lng'] as num).toDouble()
        ..ssid = m['ssid'] as String
        ..epoch = m['epoch'] as int
        ..date = m['date'] as String
        ..time = m['time'] as String;

      await isar.writeTxn(() async => await isar.coordinateModels.put(coord));
    }
  });

  /* 3. isolate は常駐するだけなので終了しない */
  if (kDebugMode) {
    // 開発中にログ確認したい場合
    debugPrint('[backgroundMain] Initialized and waiting for data…');
  }
}
