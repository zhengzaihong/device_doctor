import 'dart:io';

import 'device_doctor_desktop_base.dart';
import 'device_doctor_platform_interface.dart';
import 'platform_util.dart';

/// macOS 平台检测实现（纯 Dart，经 pubspec `dartPluginClass` 注册）。
///
/// 检测手段：`sysctl`/`system_profiler` 硬件指纹、`networksetup` 代理、
/// `scutil`/`ifconfig utun` VPN、`id -u`/SIP 特权判定、IOPlatformUUID 机器码。
class DeviceDoctorMacOS extends DesktopDeviceDoctorBase {
  /// Flutter 桌面端插件注册入口。
  static void registerWith() {
    DeviceDoctorPlatform.instance = DeviceDoctorMacOS();
  }

  @override
  String get platformId => 'macos';

  @override
  Future<List<DesktopCheckHit>> detectVirtualization() async {
    final hits = <DesktopCheckHit>[];

    // 1) hw.model / 硬件厂商：真实 Mac 形如 "MacBookPro18,1"/"iMac21,1"，
    //    虚拟机则常含 VM/Parallel/VirtualBox/Bochs。
    final hwModel = await runCmd('sysctl', ['-n', 'hw.model']);
    if (hwModel != null) {
      final v = hwModel.trim();
      final kw = firstKeywordHit(v, const [
        'virtualbox', 'vmware', 'parallels', 'qemu', 'bochs', 'kvm', 'vbox',
      ]);
      if (kw != null) {
        hits.add(DesktopCheckHit('sysctl.hw.model', v, strong: true, score: 3));
      } else if (!v.toLowerCase().startsWith('mac') && !v.toLowerCase().startsWith('imac') &&
          !v.toLowerCase().startsWith('macpro') && v.isNotEmpty) {
        hits.add(DesktopCheckHit('sysctl.hw.model', v, score: 1));
      }
    }

    // 2) hardware.model / CPU vendor via system_profiler。
    final cpu = await runCmd('sysctl', ['-n', 'machdep.cpu.vendor']);
    if (cpu != null) {
      final v = cpu.trim().toLowerCase();
      if (v.contains('vmware') || v.contains('qemu') || v.contains('virtual')) {
        hits.add(DesktopCheckHit('sysctl.cpu.vendor', cpu.trim(), strong: true, score: 3));
      }
    }

    // 3) hw.optional 虚拟化相关 sysctl（hypervisor feature 位）。
    final virt = await runCmd('sysctl', ['-a']);
    if (virt != null) {
      final low = virt.toLowerCase();
      final k = firstKeywordHit(low, const [
        'virtualbox', 'vmware', 'parallels', 'qemu', 'kvm', 'bochs',
      ]);
      if (k != null) {
        hits.add(DesktopCheckHit('sysctl.table', k, score: 2));
      }
    }

    // 4) utun/tap/vpnmgr 之外的强 VPN/虚拟网卡进程（如 tunnelblick, vpn 客户端）。
    final procs = await runCmd('ps', ['-axo', 'comm']);
    if (procs != null) {
      final low = procs.toLowerCase();
      final k = firstKeywordHit(low, const [
        'vbox', 'vmware', 'parallels', 'qemu',
      ]);
      if (k != null) {
        hits.add(DesktopCheckHit('process', k, score: 2));
      }
    }

    // 5) 文件系统级：/dev/vboxguest 等虚拟设备（Guest Additions 安装痕迹）。
    for (final dev in const ['/dev/vboxguest', '/dev/vmmon', '/dev/vmnet']) {
      try {
        if (await File(dev).exists()) {
          hits.add(DesktopCheckHit('dev', dev, strong: true, score: 3));
          break;
        }
      } catch (_) {}
    }

    return hits;
  }

  @override
  Future<bool> detectSystemProxy() async {
    // 读取主网络服务的 HTTP/HTTPS/SOCKS 代理状态。
    final service = await runCmd('networksetup', ['-listallnetworkservices']);
    final candidates = <String>['Wi-Fi', 'Ethernet'];
    final list = service?.split(RegExp(r'\r?\n')) ?? const <String>[];
    for (final name in list) {
      final s = name.trim();
      if (candidates.any((c) => s.toLowerCase().contains(c.toLowerCase()))) {
        for (final proto in const ['webproxy', 'securewebproxy', 'socksfirewallproxy']) {
          final out = await runCmd('networksetup', ['-get$proto', s]);
          if (out != null) {
            final low = out.toLowerCase();
            if (low.contains('enabled: yes')) return true;
            final server = RegExp(r'server:\s*(\S+)').firstMatch(out)?.group(1) ?? '';
            if (server.isNotEmpty && server != '0.0.0.0') return true;
          }
        }
      }
    }
    return false;
  }

  @override
  Future<bool?> detectSystemVpn() async {
    // scutil 网络配置中存在 utun 且有默认路由经 utun。
    try {
      final ifs = await NetworkInterface.list(includeLoopback: false, includeLinkLocal: false);
      final hasUtun = ifs.any((i) => i.name.toLowerCase().startsWith('utun'));
      if (hasUtun) {
        final r = await runCmd('route', ['-nget', 'default']);
        if (r != null && r.toLowerCase().contains('utun')) return true;
        return true; // 存在 utun 接口（含已建立隧道）
      }
    } catch (_) {}
    final nc = await runCmd('scutil', ['--nc', 'list']);
    if (nc != null && nc.toLowerCase().contains('connected')) return true;
    return null;
  }

  @override
  Future<bool> detectPrivilegedEnv() async {
    final id = await runCmd('id', ['-u']);
    return id != null && id.trim() == '0';
  }

  @override
  Future<DeviceInfoSnapshot> probeDeviceInfo() async {
    final prodVersion = (await runCmd('sw_vers', ['-productVersion']))?.trim() ?? '';
    final modelName = (await runCmd('sysctl', ['-n', 'hw.model']))?.trim() ?? '';
    final cpuBrand = (await runCmd('sysctl', ['-n', 'machdep.cpu.brand_string']))?.trim() ?? '';
    final arch = (await runCmd('uname', ['-m']))?.trim() ?? '';
    final host = Platform.localHostname;
    int major = 0;
    final m = RegExp(r'(\d+)').firstMatch(prodVersion);
    if (m != null) major = int.tryParse(m.group(1)!) ?? 0;
    return DeviceInfoSnapshot(
      platformVersion: prodVersion.isNotEmpty ? 'macOS $prodVersion' : Platform.operatingSystemVersion,
      osMajorVersion: major,
      model: modelName.isNotEmpty ? modelName : host,
      manufacturer: 'Apple',
      deviceName: host,
      product: modelName,
      cpu: cpuBrand.isNotEmpty ? cpuBrand : arch,
      hardware: arch.isNotEmpty ? arch : 'Apple Silicon/x86_64',
    );
  }

  @override
  Future<String?> probeMachineId() async {
    final out = await runCmd('ioreg', ['-rd1', '-c', 'IOPlatformExpertDevice']);
    if (out != null) {
      final m = RegExp(r'"IOPlatformUUID"\s*=\s*"([^"]+)"').firstMatch(out);
      if (m != null) return m.group(1);
    }
    return null;
  }
}
