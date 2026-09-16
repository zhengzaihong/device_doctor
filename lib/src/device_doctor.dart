import 'device_doctor_platform_interface.dart';

/// 面向多平台的设备环境检测门面。
///
/// 能力：模拟器/虚拟机、代理/VPN、Root/越狱、APK 签名、通用设备信息。
/// Android 侧为原生深度检测；iOS/Windows/macOS/Linux/Web 为 Dart 回退
///（见各平台 methodChannel 的 `defaultTargetPlatform` 分支）。
///
/// ## 模拟器判定（Android 原生，约 30 维度）
///
/// 共分三层，由 [isEmulator] 统一归约：
///
/// 1. **确定命中**：`hardwareHit`/`brandByProp` 非空即返回 `true`，
///    忽略阈值。来源：`ro.hardware` / `ro.kernel.qemu` /
///    全量 `getprop` 聚合扫出的厂商关键字（mumu/nemu/nox/ldmnq/
////    microvirt/bluestacks/genymotion/vbox/goldfish/ranchu）。
/// 2. **品牌指纹（+3）**：包名命中（MuMu12 `com.mumu.launcher` 等 90+ 内置 +
///    assets `emulator_config.json` 热更新 + `addCustomEmulatorPackages`
///    运行时追加；Android 11+ 另有 Manifest `<queries>` 兜底 + 模糊关键字扫描）。
/// 3. **环境线索（各 +1~2）**：x86 ABI、`GenuineIntel/AuthenticAMD`、
///    虚拟设备节点（vbox/qemu_pipe）、模拟器驱动库、`/proc/mounts`（vboxsf/
////    virtio-9p）、GPU 软渲染栈、虚拟传感器签名、init.svc 守护进程、
///    电池/通话/输入设备异常、cgroup 容器指纹等。明细见返回 map 的 `hits` 字段。
///
/// 返回的 map 结构：`{value, hardwareHit, brandByProp, hits, ...}`，
/// 可用 [DeviceDoctor.clearSimulatorCache] 清除 5min TTL 缓存后重测。
///
/// ```dart
/// const doctor = DeviceDoctor();
/// if (await doctor.isEmulator()) { /* ... */ }
/// ```
class DeviceDoctor {
  const DeviceDoctor();

  DeviceDoctorPlatform get _platform => DeviceDoctorPlatform.instance;

  /// 模拟器/虚拟机计分表（Android 为原生 20+ 维度；其他平台为 Dart 启发式）。
  /// `value` 为可疑分；`hardwareHit` 非空为强命中（等效确定）；`hits` 为全部命中的明细列表。
  Future<Map<String, dynamic>> isSimulator() => _platform.isSimulator();

  /// 是否为模拟器/虚拟机。
  ///
  /// `hardwareHit`/`brandByProp` 非空直接 true（确定命中，忽略阈值）；
  /// 否则 `score >= threshold`（默认 5）。新镜像（MuMu12/雷电9 等）伪装了
  /// Build 字段，靠环境线索累计分数即可越过阈值；建议保留默认阈值。
  Future<bool> isEmulator({int threshold = 5}) async {
    final m = await _platform.isSimulator();
    final v = m['value'];
    final score = v is int ? v : int.tryParse(v.toString()) ?? 0;
    final hwHit = m['hardwareHit']?.toString() ?? '';
    if (hwHit.isNotEmpty) return true;
    final propBrand = m['brandByProp']?.toString() ?? '';
    if (propBrand.isNotEmpty) return true;
    return score >= threshold;
  }

  Future<List<String>> getSimulatorInfo() => _platform.getSimulatorInfo();

  /// 是否存在 HTTP(S) 代理。
  Future<bool> isProxy() => _platform.isProxy();

  /// 是否启用 VPN（`TRANSPORT_VPN` / Dart 回退接口不等）。
  Future<bool> isOpenVPN() => _platform.isOpenVPN();

  /// 是否 Root/越狱。
  ///
  /// Android 判定原则：仅凭 su 二进制存在不等同于已提权（真机镜像常自带 su）。
  /// 命中以下**确凿证据**之一才返回 true：进程 uid 0、真实 root 管理器包
  ///（Magisk/KernelSU/模拟器 root 开关）、su 实测能提权到 uid=0、
  /// Magisk/KSU 专属挂载痕迹、SELinux 非 Enforcing、adbd 以 root 运行。
  /// test-keys / debuggable / 解锁 bootloader 属于弱信号，任一个都不单独判 root，
  /// 凑齐 2 个才判。误判时看 logcat `DeviceDoctor isRoot=true:` 会打印依据。
  Future<bool> isRootEnv() => _platform.isRootEnv();

  /// 清空原生/缓存（模拟器 5min TTL、包名、CPU 信息）。
  Future<void> clearSimulatorCache() => _platform.clearSimulatorCache();

  /// 追加自定义模拟器指纹（/`/` 前缀视为文件路径，其余为包名），与内置 + assets 合并。
  Future<void> addCustomEmulatorPackages(List<String> packages) =>
      _platform.addCustomEmulatorPackages(packages);

  /// APK/包签名摘要：`MD5`/`SHA-1`/`SHA-256`（大小写不敏感，非法返回 []；仅 Android 有意义）。
  Future<List<String>> getSignatures(String type) => _platform.getSignatures(type);

  /// 如 `Android 14` / `iOS 17` / `Windows 10` 等。
  Future<String> get platformVersion => _platform.getPlatformVersion();

  /// Android 10+ 受限，可能为 null；其他平台返回标识或 null。
  Future<String?> get deviceIMEINumber => _platform.getImeiNumber();

  Future<int> get apiLevel => _platform.getApiLevel();
  Future<String> get deviceModel => _platform.getModel();
  Future<String> get deviceManufacturer => _platform.getManufacturer();
  Future<String> get deviceName => _platform.getDevice();
  Future<String> get productName => _platform.getProduct();
  Future<String> get cpuName => _platform.getCpuType();
  Future<String> get hardware => _platform.getHardware();
}
