import 'android_work_platform_interface.dart';

export 'android_work_platform_interface.dart' show AndroidWorkPlatform;
export 'android_work_method_channel.dart' show MethodChannelAndroidWork;

/// 面向 Android 的设备风控/环境检测插件门面。
///
/// 能力：模拟器检测（计分模型）、Root 检测、代理/VPN 检测、APK 签名摘要、
/// 常规设备信息读取。iOS 侧未实现这些方法，调用会抛
/// [MissingPluginException]/`UnimplementedError`，请用
/// [AndroidWorkPlatform] 注入自己的实现或做平台判断。
///
/// ```dart
/// const work = AndroidWork();
/// if (await work.isEmulator()) { /* ... */ }
/// ```
///
/// 合规提示：`deviceIMEINumber` 在 Android 10+ 受限并可能需要用户授权，
/// 请勿在未告知用户的场景下采集设备标识（GDPR/个保法）。
class AndroidWork {
  const AndroidWork();

  AndroidWorkPlatform get _platform => AndroidWorkPlatform.instance;

  /// 模拟器检测详情（原生计分表，结果缓存 5 分钟）。
  ///
  /// 返回字段：`hardware`、`hardwareHit`（命中模拟器硬件指纹的品牌描述，如
  /// "MUMU模拟器"；强命中时非空）、`flavor`、`model`、`manufacturer`、
  /// `board`、`platform`、`baseBand`、`sensorNumber`、`supportCamera`、
  /// `supportCameraFlash`、`supportBluetooth`、`hasLightSensor`、
  /// `cgroupResult`、`value`（可疑分 int）。
  ///
  /// 评分规则：每个可疑特征 +1，基带缺失 +2，命中模拟器专属包名 +3，
  /// 命中 `ro.hardware` 指纹 +100（等效确定）。建议阈值 4 分以上视为模拟器。
  Future<Map<String, dynamic>> isSimulator() => _platform.isSimulator();

  /// 是否为模拟器（阈值判定，默认 `score >= 4`）。
  ///
  /// `hardwareHit` 非空时直接判定为 true（指纹强命中优先于分数）。
  /// 需要更细粒度控制请用 [isSimulator] 自行解读计分表。
  Future<bool> isEmulator({int threshold = 4}) async {
    final m = await _platform.isSimulator();
    final v = m['value'];
    final score = v is int ? v : int.tryParse(v.toString()) ?? 0;
    final hwHit = m['hardwareHit']?.toString() ?? '';
    if (hwHit.isNotEmpty) return true;
    return score >= threshold;
  }

  /// 已安装模拟器品牌标签，如 `["mumu"]`、`["蓝叠"]`、`["雷电"]`；未检出返回空列表。
  Future<List<String>> getSimulatorInfo() => _platform.getSimulatorInfo();

  /// 是否存在 HTTP(S) 代理。
  ///
  /// 依次检查 JVM 代理属性 → `ConnectivityManager.defaultProxy()`（API 23+）→
  /// 遗留 `Settings.System "http_proxy"`；任一命中即 true。
  Future<bool> isProxy() => _platform.isProxy();

  /// 是否启用 VPN（`TRANSPORT_VPN`），需要 `ACCESS_NETWORK_STATE`。
  Future<bool> isOpenVPN() => _platform.isOpenVPN();

  /// 是否 Root/Magisk 环境（su 二进制、root 管理器包名、`which su`、`test-keys`）。
  Future<bool> isRootEnv() => _platform.isRootEnv();

  /// 清空原生侧缓存（模拟器详情 5min TTL、包名命中、CPU 信息），
  /// 下一次 [isSimulator]/[isEmulator] 强制重检。
  Future<void> clearSimulatorCache() => _platform.clearSimulatorCache();

  /// 注册自定义模拟器指纹（包名/路径），与内置列表、`emulator_config.json` 合并。
  Future<void> addCustomEmulatorPackages(List<String> packages) =>
      _platform.addCustomEmulatorPackages(packages);

  /// 获取 APK 签名摘要。
  ///
  /// [type] 支持 `MD5`、`SHA-1`、`SHA-256`（大小写不敏感，非法值返回空列表）。
  /// Android 9+ 读取 `SigningInfo`，8 及以下读取已废弃的 `GET_SIGNATURES`。
  /// 无需额外权限（签名属于本应用自身信息）。
  Future<List<String>> getSignatures(String type) => _platform.getSignatures(type);

  /// 系统版本描述，如 `"Android 14"`。
  Future<String> get platformVersion => _platform.getPlatformVersion();

  /// 设备唯一标识。
  ///
  /// 优先级：Android 9+ 走 Widevine `MediaDrm` 设备 ID（hex 前 15 位）；
  /// Android 8/9 尝试 `TelephonyManager.imei`；7 及以下尝试 `deviceId`。
  /// 均不可用时返回 null。Android 10+ 无 `READ_PHONE_STATE` 时 IMEI 不可得。
  Future<String?> get deviceIMEINumber => _platform.getImeiNumber();

  /// `Build.VERSION.SDK_INT`。
  Future<int> get apiLevel => _platform.getApiLevel();

  /// `Build.MODEL`。
  Future<String> get deviceModel => _platform.getModel();

  /// `Build.MANUFACTURER`。
  Future<String> get deviceManufacturer => _platform.getManufacturer();

  /// `Build.DEVICE`。
  Future<String> get deviceName => _platform.getDevice();

  /// `Build.PRODUCT`。
  Future<String> get productName => _platform.getProduct();

  /// CPU ABI。
  Future<String> get cpuName => _platform.getCpuType();

  /// `Build.HARDWARE`。
  Future<String> get hardware => _platform.getHardware();
}
