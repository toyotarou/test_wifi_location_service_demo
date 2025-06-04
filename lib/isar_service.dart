import 'package:isar/isar.dart';
import 'package:path_provider/path_provider.dart';
import 'background_main.dart'; // CoordinateModelSchema を流用

/// UI isolate と headless isolate で
/// 同じ Isar インスタンスを共有するための簡易シングルトン。
class IsarService {
  IsarService._(); // インスタンス化させない
  static Isar? _isar;

  /// 最初の呼び出しで open。以降はキャッシュを返す。
  static Future<Isar> open() async {
    if (_isar != null) return _isar!;
    final dir = await getApplicationDocumentsDirectory();
    _isar = await Isar.open([CoordinateModelSchema], directory: dir.path);
    return _isar!;
  }
}
