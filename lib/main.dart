import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:isar/isar.dart';

import 'pigeon/wifi_coordinate.dart';
import 'background_main.dart'; // CoordinateModel 定義
import 'isar_service.dart'; // ★ 追加

void main() => runApp(const MyApp());

/* ─── アプリ全体 ─── */
class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Wi-Fi Location Demo',
      theme: ThemeData(useMaterial3: true, colorSchemeSeed: Colors.indigo),
      home: const HomePage(),
    );
  }
}

/* ─── 座標をメモリ保持して UI 更新 ─── */
class CoordinateStore extends ChangeNotifier {
  final _list = <Coordinate>[];

  List<Coordinate> get items => List.unmodifiable(_list);

  void add(Coordinate c) {
    _list.insert(0, c);
    notifyListeners();
  }
}

/* ─── Kotlin → Flutter コールバック ─── */
class CoordinateReceiver extends CoordinatePushApi {
  CoordinateReceiver(this._store);

  final CoordinateStore _store;

  @override
  void onCoordinate(Coordinate c) async {
    /* 1) 画面即時表示用にメモリへ */
    _store.add(c);

    /* 2) 同期で Isar 永続化 */
    final isar = await IsarService.open();
    await isar.writeTxn(() async {
      await isar.coordinateModels.put(
        CoordinateModel()
          ..lat = c.lat
          ..lng = c.lng
          ..ssid = c.ssid
          ..epoch = c.epochMillis
          ..date = c.date
          ..time = c.time,
      );
    });
  }
}

/* ─── メイン画面 ─── */
class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final _store = CoordinateStore();
  late final ServiceControlApi _api;
  bool _running = false;

  ///
  @override
  void initState() {
    super.initState();
    _api = ServiceControlApi();
    CoordinatePushApi.setup(CoordinateReceiver(_store));
    unawaited(_requestPerms());
  }

  ///
  Future<void> _requestPerms() async {
    await Permission.location.request();
    if (defaultTargetPlatform == TargetPlatform.android) {
      final info = await DeviceInfoPlugin().androidInfo;
      if (info.version.sdkInt >= 33) {
        // 近接 Wi-Fi 許可案内は必要に応じて実装
      }
    }
  }

  ///
  Future<void> _toggle() async {
    _running ? await _api.stopService() : await _api.startService();
    if (mounted) setState(() => _running = !_running);
  }

  void _openHistory() => Navigator.push(context, MaterialPageRoute(builder: (_) => const HistoryPage()));

  ///
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Wi-Fi Location Demo'),
        leading: IconButton(icon: const Icon(Icons.history), tooltip: '履歴', onPressed: _openHistory),
      ),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _toggle,
        label: Text(_running ? 'STOP' : 'START'),
        icon: Icon(_running ? Icons.stop : Icons.play_arrow),
      ),
      body: AnimatedBuilder(
        animation: _store,
        builder: (_, __) => _store.items.isEmpty
            ? const Center(child: Text('まだデータがありません'))
            : ListView.separated(
                itemCount: _store.items.length,
                separatorBuilder: (_, __) => const Divider(height: 1),
                itemBuilder: (_, i) {
                  final c = _store.items[i];
                  return ListTile(
                    leading: const Icon(Icons.wifi_tethering),
                    title: Text('${c.lat.toStringAsFixed(5)}, ${c.lng.toStringAsFixed(5)}'),
                    subtitle: Text('${c.date} ${c.time} [${c.ssid}]'),
                  );
                },
              ),
      ),
    );
  }
}

/* ─── 履歴画面 ─── */
class HistoryPage extends StatefulWidget {
  const HistoryPage({super.key});

  @override
  State<HistoryPage> createState() => _HistoryPageState();
}

class _HistoryPageState extends State<HistoryPage> {
  late Future<List<CoordinateModel>> _future;

  ///
  @override
  void initState() {
    super.initState();
    _future = _load();
  }

  ///
  Future<List<CoordinateModel>> _load() async {
    final isar = await IsarService.open();
    return await isar.coordinateModels.where().sortByEpochDesc().findAll();
  }

  ///
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('保存履歴')),
      body: FutureBuilder<List<CoordinateModel>>(
        future: _future,
        builder: (_, snap) {
          if (!snap.hasData) {
            return const Center(child: CircularProgressIndicator());
          }
          final list = snap.data!;
          if (list.isEmpty) return const Center(child: Text('Isar にデータがありません'));
          return ListView.separated(
            itemCount: list.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (_, i) {
              final c = list[i];
              return ListTile(
                leading: const Icon(Icons.save),
                title: Text('${c.lat.toStringAsFixed(5)}, ${c.lng.toStringAsFixed(5)}'),
                subtitle: Text('${c.date} ${c.time} [${c.ssid}]'),
              );
            },
          );
        },
      ),
    );
  }
}
