import 'dart:async';
import 'package:device_info_plus/device_info_plus.dart';
import 'package:flutter/material.dart';
import 'package:permission_handler/permission_handler.dart';
import 'package:wifi_location_service_demo/pigeon/wifi_coordinate.dart';

void main() {
  runApp(const MyApp());
}

/* ------------------ アプリ ------------------ */
class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Wi-Fi Coordinate Demo',
      theme: ThemeData(useMaterial3: true, colorSchemeSeed: Colors.indigo),
      home: const HomePage(),
    );
  }
}

/* ------------------ 状態保持 ------------------ */
class CoordinateStore extends ChangeNotifier {
  final List<Coordinate> _items = [];

  List<Coordinate> get items => List.unmodifiable(_items);

  void add(Coordinate c) {
    _items.insert(0, c); // 新しい順で先頭に
    notifyListeners();
  }

  void clear() {
    _items.clear();
    notifyListeners();
  }
}

/* ------------------ Flutter ← Kotlin コールバック ------------------ */
class CoordinateReceiver extends CoordinatePushApi {
  CoordinateReceiver(this._store);

  final CoordinateStore _store;

  @override
  void onCoordinate(Coordinate coordinate) {
    _store.add(coordinate);
  }
}

/* ------------------ 画面 ------------------ */
class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  final _store = CoordinateStore();
  bool _serviceRunning = false;
  late final ServiceControlApi _serviceApi;

  ///
  @override
  void initState() {
    super.initState();
    _serviceApi = ServiceControlApi(); // Pigeon Host API
    CoordinatePushApi.setUp(CoordinateReceiver(_store)); // 受信ハンドラ登録
    _requestPermissions();
  }

  // ///
  // Future<void> _requestPermissions() async {
  //   await Permission.location.request();
  //   if (await Permission.location.isDenied) {
  //     if (mounted) {
  //       ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('位置情報の許可が必要です')));
  //     }
  //   }
  // }

  Future<void> _requestPermissions() async {
    // 位置情報
    final loc = await Permission.location.request();
    if (!loc.isGranted) {
      _show('位置情報が許可されていません');
      return;
    }
    // Android 13+ Wi-Fi
    if (await Permission.location.isGranted && Theme.of(context).platform == TargetPlatform.android) {
      final info = await DeviceInfoPlugin().androidInfo;

      if (info.version.sdkInt >= 33) {
        // 設定アプリを開く案内
        _show(
          'Wi-Fi スキャン許可が必要です。設定→アプリ→権限で'
          '「付近のデバイス」を許可してください',
        );
      }
    }
  }

  void _show(String msg) => ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(msg)));

  ///
  Future<void> _toggleService() async {
    if (_serviceRunning) {
      await _serviceApi.stopService();
    } else {
      await _serviceApi.startService();
    }
    setState(() => _serviceRunning = !_serviceRunning);
  }

  ///
  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Wi-Fi Coordinate Demo')),
      floatingActionButton: FloatingActionButton.extended(
        onPressed: _toggleService,
        label: Text(_serviceRunning ? 'STOP' : 'START'),
        icon: Icon(_serviceRunning ? Icons.stop : Icons.play_arrow),
      ),
      body: AnimatedBuilder(
        animation: _store,
        builder: (context, _) {
          if (_store.items.isEmpty) {
            return const Center(child: Text('まだデータがありません'));
          }
          return ListView.separated(
            itemCount: _store.items.length,
            separatorBuilder: (_, __) => const Divider(height: 1),
            itemBuilder: (context, i) {
              final c = _store.items[i];
              return ListTile(
                leading: const Icon(Icons.wifi_tethering),
                title: Text(
                  '${c.lat.toStringAsFixed(5)}, '
                  '${c.lng.toStringAsFixed(5)}',
                ),
                subtitle: Text('${c.date}  ${c.time}  [${c.ssid}]'),
              );
            },
          );
        },
      ),
    );
  }
}
