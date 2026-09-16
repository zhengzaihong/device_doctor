import 'dart:io' show File, HttpClient, Platform, NetworkInterface;
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'device_doctor_platform_interface.dart';

const String _kChannelNew = 'device_doctor';
const String _kChannelLegacy = 'flutter_native_android_work';

class MethodChannelDeviceDoctor extends DeviceDoctorPlatform {
  @visibleForTesting
  MethodChannel get methodChannel => const MethodChannel(_kChannelNew);

  MethodChannel get _legacyChannel => const MethodChannel(_kChannelLegacy);

  Future<T?> _invokeWithFallback<T>(String method, [dynamic args]) async {
    try {
      final v = await methodChannel.invokeMethod<T>(method, args);
      if (v != null) return v;
    } on MissingPluginException {
      // fall through to legacy channel / dart fallback
    } on PlatformException catch (e) {
      // iOS/Android 对未实现方法返回 notImplemented，需继续走兜底而非崩溃。
      if (e.code != 'notImplemented') rethrow;
    }
    try {
      return await _legacyChannel.invokeMethod<T>(method, args);
    } on MissingPluginException {
      return null;
    } on PlatformException catch (e) {
      if (e.code != 'notImplemented') rethrow;
      return null;
    }
  }

  // ── Dart 回退：非 Android/iOS 时的启发式 ───────────────────────────────

  bool _proxyFromEnv() {
    try {
      final env = Platform.environment;
      String? pick(List<String> keys) {
        for (final k in keys) {
          final v = env[k] ?? env[k.toUpperCase()];
          if (v != null && v.isNotEmpty && v != 'null' && v != '-1' && v != '0') return v;
        }
        return null;
      }

      final host = pick(['http_proxy', 'https_proxy', 'HTTP_PROXY', 'HTTPS_PROXY', 'all_proxy', 'ALL_PROXY']);
      if (host != null) return true;
      final sysHost = _trySystemProxyHost();
      if (sysHost != null && sysHost.isNotEmpty) return true;
    } catch (_) {}
    return false;
  }

  String? _trySystemProxyHost() {
    try {
      // HttpClient.findProxyFromEnvironment is available but needs a URI;
      // probe with a dummy http URI.
      final f = HttpClient.findProxyFromEnvironment(Uri.parse('http://example.com'));
      // returns e.g. "PROXY host:port" or "DIRECT"
      if (f.contains('PROXY')) return f;
    } catch (_) {}
    return null;
  }

  Future<bool> _vpnFromInterfaces() async {
    try {
      final ifs = await NetworkInterface.list(includeLoopback: false, includeLinkLocal: false);
      for (final i in ifs) {
        final n = i.name.toLowerCase();
        if (n.contains('tun') || n.contains('tap') || n.contains('vpn') || n.contains('ppp') || n.contains('utun') || n.contains('ipsec')) {
          return true;
        }
      }
    } catch (_) {}
    if (!kIsWeb) {
      try {
        final env = Platform.environment;
        if (env.containsKey('VPN_ACTIVE') || env.containsKey('TUN_DEVICE')) return true;
      } catch (_) {}
    }
    return false;
  }

  Future<Map<String, dynamic>> _simulatorHeuristic() async {
    const isWeb = kIsWeb;
    final os = isWeb ? 'web' : Platform.operatingSystem; // android, ios, windows, macos, linux
    final env = isWeb ? <String, String>{} : Platform.environment;
    int score = 0;
    String hit = '';
    String envHit = '';
    // 常见云手机/虚拟化指纹
    final lowEnv = env.keys.fold<String>('', (a, k) => '$a $k=${env[k]}').toLowerCase();
    if (lowEnv.contains('vbox') || lowEnv.contains('virtualbox')) { score += 3; envHit = 'vbox env'; }
    if (lowEnv.contains('vmware')) { score += 3; envHit = 'vmware env'; }
    if (lowEnv.contains('qemu') || lowEnv.contains('kvm')) { score += 3; envHit = 'qemu/kvm env'; }
    if (Platform.isWindows) {
      final id = (env['PROCESSOR_IDENTIFIER'] ?? '').toLowerCase();
      if (id.contains('virtual') || id.contains('qemu')) { score += 3; envHit = 'processor virtual'; }
    }
    if (Platform.isLinux) {
      try {
        final cpuInfo = await File('/proc/cpuinfo').readAsString();
        final low = cpuInfo.toLowerCase();
        if (low.contains('hypervisor')) score += 2;
        if (low.contains('qemu') || low.contains('virtualbox') || low.contains('vmware')) { score += 3; hit = 'linux cpuinfo vm'; }
      } catch (_) {}
    }
    return <String, dynamic>{
      'platform': os,
      'hardwareHit': hit,
      'envHit': envHit,
      'value': score,
      'note': 'dart heuristic (non-Android)',
    };
  }

  // ── 覆盖接口 ──────────────────────────────────────────────────────────

  @override
  Future<String> getPlatformVersion() async {
    final v = await _invokeWithFallback<String>('getPlatformVersion');
    if (v != null && v.isNotEmpty) return v;
    if (kIsWeb) return 'Web ${defaultTargetPlatform.name}';
    try {
      return Platform.operatingSystemVersion;
    } catch (_) {
      return 'Unknown';
    }
  }

  @override
  Future<int> getApiLevel() async {
    final v = await _invokeWithFallback<int>('getAPILevel');
    if (v != null) return v;
    return 0;
  }

  @override
  Future<String> getModel() async {
    final v = await _invokeWithFallback<String>('getModel');
    if (v != null && v.isNotEmpty) return v;
    if (kIsWeb) return 'Web';
    try { return Platform.localHostname; } catch (_) { return ''; }
  }

  @override
  Future<String> getManufacturer() async {
    final v = await _invokeWithFallback<String>('getManufacturer');
    if (v != null) return v;
    if (Platform.isWindows) return 'Windows';
    if (Platform.isMacOS) return 'Apple';
    if (Platform.isLinux) return 'Linux';
    if (Platform.isIOS) return 'Apple';
    return '';
  }

  @override
  Future<String> getDevice() async {
    final v = await _invokeWithFallback<String>('getDevice');
    if (v != null) return v;
    try { return Platform.localHostname; } catch (_) { return ''; }
  }

  @override
  Future<String> getProduct() async {
    final v = await _invokeWithFallback<String>('getProduct');
    return v ?? '';
  }

  @override
  Future<String> getCpuType() async {
    final v = await _invokeWithFallback<String>('getCPUType');
    return v ?? (kIsWeb ? 'wasm' : Platform.version);
  }

  @override
  Future<String> getHardware() async {
    final v = await _invokeWithFallback<String>('getHardware');
    return v ?? '';
  }

  @override
  Future<String?> getImeiNumber() async {
    try {
      final v = await _invokeWithFallback<String>('getIMEINumber');
      if (v != null && v.isNotEmpty) return v;
    } on PlatformException {
      return null;
    }
    return null; // non-Android: not applicable
  }

  @override
  Future<Map<String, dynamic>> isSimulator() async {
    final raw = await _invokeWithFallback<dynamic>('isSimulator');
    if (raw is Map) return Map<String, dynamic>.from(raw);
    return _simulatorHeuristic();
  }

  @override
  Future<List<String>> getSimulatorInfo() async {
    final raw = await _invokeWithFallback<dynamic>('getSimulatorInfo');
    if (raw is List) return raw.whereType<String>().toList();
    final m = await isSimulator();
    final hit = m['hardwareHit']?.toString() ?? '';
    if (hit.isNotEmpty) return [hit];
    return <String>[];
  }

  @override
  Future<bool> isProxy() async {
    final v = await _invokeWithFallback<bool>('isProxy');
    if (v != null) return v;
    return _proxyFromEnv();
  }

  @override
  Future<bool> isOpenVPN() async {
    final v = await _invokeWithFallback<bool>('isOpenVPN');
    if (v != null) return v;
    return _vpnFromInterfaces();
  }

  @override
  Future<bool> isRootEnv() async {
    final v = await _invokeWithFallback<bool>('isRootEnv');
    if (v != null) return v;
    // Windows/Linux/macOS: 无可靠无权限探针，保守返回 false（可用 Dart 启发式扩展）
    if (kIsWeb) return false;
    return false;
  }

  @override
  Future<List<String>> getSignatures(String type) async {
    final normalized = type.trim().toUpperCase();
    final raw = await _invokeWithFallback<dynamic>('getSignature', {'type': normalized});
    if (raw is List) return raw.whereType<String>().toList();
    return <String>[];
  }

  @override
  Future<void> clearSimulatorCache() async {
    await _invokeWithFallback<void>('clearSimulatorCache');
  }

  @override
  Future<void> addCustomEmulatorPackages(List<String> packages) async {
    // 无原生接收端（桌面/Web）时 no-op：指纹仅保存在原生侧。
    await _invokeWithFallback<void>('customEmulatorPackages', {'packages': packages});
  }
}

/// 兼容别名
typedef MethodChannelAndroidWork = MethodChannelDeviceDoctor;
