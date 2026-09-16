import 'dart:async';
import 'dart:io';

import 'device_doctor_platform_interface.dart';
import 'platform_util.dart';

/// 单个检测维度命中的描述（对齐 Android 原生 `CheckResult` 语义）。
class DesktopCheckHit {
  const DesktopCheckHit(this.source, this.detail, {this.strong = false, this.score = 1});

  /// 命中来源，如 `dmi.bios_vendor`、`registry.BIOS`、`mac_oui`。
  final String source;

  /// 命中的原始值/说明。
  final String detail;

  /// 强命中 = 确定性虚拟化证据（等效 Android `hardwareHit`，直接判定模拟器）。
  final bool strong;

  /// 计入可疑分（Android 侧非强命中每维度 +1，可定制）。
  final int score;

  Map<String, dynamic> toMap() =>
      {'source': source, 'detail': detail, 'strong': strong, 'score': score};

  @override
  String toString() => '$source: $detail';
}

/// Windows / Linux / macOS 桌面端平台实现的公共骨架。
///
/// 与 Android 端功能对齐的能力矩阵：
/// - `isSimulator`：多维度虚拟化指纹计分（`value` 可疑分 + `hardwareHit` 强命中），
///   带 5 分钟缓存（同 Android `CACHE_TTL_MS`）；
/// - `getSimulatorInfo`：返回品牌/指纹命中文案列表；
/// - `isProxy`：环境变量 + 系统代理（各平台重写 `detectSystemProxy`）；
/// - `isOpenVPN`：网络接口名启发 + 各平台重写 `detectSystemVpn`；
/// - `isRootEnv`：桌面端语义为「进程是否以 root/Administrator 特权运行」，
///   并附带常见提权/调试工具指纹（各平台重写）；
/// - `getSignatures`：桌面端无 APK 签名概念，恒返回空列表；
/// - 设备信息族：各平台重写 `collectDeviceInfo`。
abstract class DesktopDeviceDoctorBase extends DeviceDoctorPlatform {
  static const Duration _cacheTtl = Duration(minutes: 5);

  List<DesktopCheckHit>? _cachedHits;
  DateTime? _cachedAt;
  DeviceInfoSnapshot? _cachedDeviceInfo;

  final List<String> _customEmulatorTokens = <String>[];

  // ── 子类需实现的平台钩子 ────────────────────────────────────────────

  /// `windows` / `linux` / `macos`，写入 isSimulator 结果 map。
  String get platformId;

  /// 虚拟化指纹探针（DMI、注册表、WMI、mac_oui、cpuinfo 等）。
  Future<List<DesktopCheckHit>> detectVirtualization();

  /// 系统代理探针（Windows 注册表 / GNOME gsettings / macOS scutil）。
  Future<bool> detectSystemProxy();

  /// 系统级 VPN 探针（接口名启发之外的增强，可返回 null 表示未检出）。
  Future<bool?> detectSystemVpn();

  /// root/特权环境探针，返回 true 表示已提权。
  Future<bool> detectPrivilegedEnv();

  /// 设备信息（型号/厂商/CPU 等），带缓存。
  Future<DeviceInfoSnapshot> probeDeviceInfo();

  /// 平台唯一机器码（对齐 Android IMEI/DRM ID 语义）。
  Future<String?> probeMachineId();

  // ── 接口实现 ────────────────────────────────────────────────────────

  @override
  Future<String> getPlatformVersion() async {
    final info = await _deviceInfo();
    return info.platformVersion;
  }

  @override
  Future<int> getApiLevel() async {
    final info = await _deviceInfo();
    return info.osMajorVersion;
  }

  @override
  Future<String> getModel() async => (await _deviceInfo()).model;

  @override
  Future<String> getManufacturer() async => (await _deviceInfo()).manufacturer;

  @override
  Future<String> getDevice() async => (await _deviceInfo()).deviceName;

  @override
  Future<String> getProduct() async => (await _deviceInfo()).product;

  @override
  Future<String> getCpuType() async => (await _deviceInfo()).cpu;

  @override
  Future<String> getHardware() async => (await _deviceInfo()).hardware;

  @override
  Future<String?> getImeiNumber() async {
    try {
      final id = await probeMachineId();
      return (id == null || id.isEmpty) ? null : id;
    } catch (_) {
      return null;
    }
  }

  @override
  Future<Map<String, dynamic>> isSimulator() async {
    final hits = await _detectWithCache();
    int score = 0;
    String hardwareHit = '';
    final sources = <String>[];
    for (final h in hits) {
      score += h.score;
      sources.add('${h.source}=${h.detail}');
      if (h.strong && hardwareHit.isEmpty) {
        hardwareHit = '${h.source}:${h.detail}';
      }
    }
    final custom = _customEmulatorTokens;
    if (custom.isNotEmpty) {
      score += custom.length;
      sources.add('custom=${custom.join(",")}');
    }
    return <String, dynamic>{
      'platform': platformId,
      'hardwareHit': hardwareHit,
      'value': score,
      'hits': hits.map((h) => h.toMap()).toList(),
      'sources': sources,
      'model': (await _deviceInfo()).model,
      'manufacturer': (await _deviceInfo()).manufacturer,
      'hardware': (await _deviceInfo()).hardware,
      'note': 'desktop native checks (aligned with Android scoring)',
    };
  }

  @override
  Future<List<String>> getSimulatorInfo() async {
    final hits = await _detectWithCache();
    final out = <String>[];
    for (final h in hits) {
      final label = _brandLabelFor(h);
      if (!out.contains(label)) out.add(label);
    }
    return out;
  }

  @override
  Future<bool> isProxy() async {
    if (Platform.environment.isNotEmpty) {
      final v = pickEnv(Platform.environment, const [
        'http_proxy', 'https_proxy', 'all_proxy', 'ftp_proxy',
      ]);
      if (v != null) return true;
    }
    try {
      if (await detectSystemProxy()) return true;
    } catch (_) {}
    try {
      // 与 Android/Dart 兜底一致：findProxyFromEnvironment 探针。
      final f = HttpClient.findProxyFromEnvironment(Uri.parse('http://example.com'));
      if (f.toUpperCase().contains('PROXY')) return true;
    } catch (_) {}
    return false;
  }

  @override
  Future<bool> isOpenVPN() async {
    try {
      final ifs = await NetworkInterface.list(includeLoopback: false, includeLinkLocal: false);
      for (final i in ifs) {
        final n = i.name.toLowerCase();
        if (n.contains('tun') || n.contains('tap') || n.contains('vpn') ||
            n.contains('ppp') || n.contains('utun') || n.contains('ipsec') ||
            n.contains('wireguard')) {
          return true;
        }
      }
    } catch (_) {}
    try {
      final sysVpn = await detectSystemVpn();
      if (sysVpn == true) return true;
    } catch (_) {}
    return false;
  }

  @override
  Future<bool> isRootEnv() async {
    try {
      if (await detectPrivilegedEnv()) return true;
    } catch (_) {}
    return false;
  }

  @override
  Future<List<String>> getSignatures(String type) async {
    // 桌面端没有 APK/IPA 签名概念；保持与接口文档一致返回空列表。
    return const <String>[];
  }

  @override
  Future<void> clearSimulatorCache() async {
    _cachedHits = null;
    _cachedAt = null;
    _cachedDeviceInfo = null;
  }

  @override
  Future<void> addCustomEmulatorPackages(List<String> packages) async {
    for (final p in packages) {
      final t = p.trim();
      if (t.isNotEmpty && !_customEmulatorTokens.contains(t)) {
        _customEmulatorTokens.add(t.toLowerCase());
      }
    }
    await clearSimulatorCache();
  }

  // ── 内部工具 ────────────────────────────────────────────────────────

  Future<List<DesktopCheckHit>> _detectWithCache() async {
    final now = DateTime.now();
    final cached = _cachedHits;
    if (cached != null &&
        _cachedAt != null &&
        now.difference(_cachedAt!) < _cacheTtl) {
      return cached;
    }
    late List<DesktopCheckHit> hits;
    try {
      hits = await detectVirtualization();
    } catch (_) {
      hits = const [];
    }
    _cachedHits = hits;
    _cachedAt = now;
    return hits;
  }

  Future<DeviceInfoSnapshot> _deviceInfo() async {
    final cached = _cachedDeviceInfo;
    if (cached != null) return cached;
    DeviceInfoSnapshot info;
    try {
      info = await probeDeviceInfo();
    } catch (_) {
      info = DeviceInfoSnapshot.fallback(platformId);
    }
    _cachedDeviceInfo = info;
    return info;
  }

  /// 将命中项映射为模拟器/虚拟化品牌文案（对齐 Android `getSimulatorInfo` 列表语义）。
  String _brandLabelFor(DesktopCheckHit h) {
    final low = '${h.source} ${h.detail}'.toLowerCase();
    if (low.contains('virtualbox') || low.contains('vbox') || low.contains('innotek')) {
      return 'Oracle VirtualBox';
    }
    if (low.contains('vmware')) return 'VMware Workstation/ESXi';
    if (low.contains('qemu') || low.contains('kvm')) return 'QEMU/KVM';
    if (low.contains('xen')) return 'Xen';
    if (low.contains('hyperv') || low.contains('virtual machine') || low.contains('microsoft') && low.contains('vm')) {
      return 'Microsoft Hyper-V';
    }
    if (low.contains('parallels')) return 'Parallels Desktop';
    if (low.contains('bochs')) return 'QEMU (Bochs BIOS)';
    if (low.contains('docker') || low.contains('containerd') || low.contains('lxc')) {
      return 'Container (Docker/LXC)';
    }
    if (low.contains('wsl')) return 'Windows Subsystem for Linux';
    if (low.contains('cloud') || low.contains('ec2') || low.contains('gce') || low.contains('compute')) {
      return 'Cloud VM';
    }
    return '${h.source}:${h.detail}';
  }
}

/// 桌面设备信息快照（各平台 probe 汇总后的统一结构）。
class DeviceInfoSnapshot {
  const DeviceInfoSnapshot({
    required this.platformVersion,
    required this.osMajorVersion,
    required this.model,
    required this.manufacturer,
    required this.deviceName,
    required this.product,
    required this.cpu,
    required this.hardware,
  });

  factory DeviceInfoSnapshot.fallback(String platformId) {
    String host = '';
    try {
      host = Platform.localHostname;
    } catch (_) {}
    return DeviceInfoSnapshot(
      platformVersion: Platform.operatingSystemVersion,
      osMajorVersion: 0,
      model: host,
      manufacturer: platformId,
      deviceName: host,
      product: '',
      cpu: Platform.version,
      hardware: platformId,
    );
  }

  final String platformVersion;
  final int osMajorVersion;
  final String model;
  final String manufacturer;
  final String deviceName;
  final String product;
  final String cpu;
  final String hardware;
}
