import 'dart:io';

import 'device_doctor_desktop_base.dart';
import 'device_doctor_platform_interface.dart';
import 'platform_util.dart';

/// Linux 平台检测实现（纯 Dart，经 pubspec `dartPluginClass` 注册）。
///
/// 检测手段：DMI（`/sys/class/dmi/id`）、`/proc/cpuinfo` hypervisor 位、
/// `systemd-detect-virt`、cgroup 容器指纹、gsettings 代理、`ip route` 默认网关、
/// `id -u` 特权判定、`/etc/machine-id` 机器码。
class DeviceDoctorLinux extends DesktopDeviceDoctorBase {
  /// Flutter 桌面端插件注册入口。
  static void registerWith() {
    DeviceDoctorPlatform.instance = DeviceDoctorLinux();
  }

  @override
  String get platformId => 'linux';

  static const List<String> _dmiKeywords = [
    'qemu', 'kvm', 'virtualbox', 'innotek', 'vmware', 'bochs', 'xen',
    'hyper-v', 'microsoft corporation', 'parallels', 'vbox',
    'google compute engine', 'amazon ec2', 'openstack', 'cloudinit',
    'coreboot',
  ];

  static const Map<String, String> _dmiFiles = {
    'sys_vendor': '/sys/class/dmi/id/sys_vendor',
    'product_name': '/sys/class/dmi/id/product_name',
    'board_vendor': '/sys/class/dmi/id/board_vendor',
    'bios_vendor': '/sys/class/dmi/id/bios_vendor',
    'chassis_vendor': '/sys/class/dmi/id/chassis_vendor',
  };

  @override
  Future<List<DesktopCheckHit>> detectVirtualization() async {
    final hits = <DesktopCheckHit>[];

    // 1) DMI 厂商/产品名。
    for (final entry in _dmiFiles.entries) {
      final v = await readFileSafe(entry.value);
      if (v == null) continue;
      final kw = firstKeywordHit(v, _dmiKeywords);
      if (kw != null) {
        hits.add(DesktopCheckHit('dmi.${entry.key}', v.trim(), strong: true, score: 3));
      }
    }

    // 2) systemd-detect-virt（权威）。
    final virt = await runCmd('systemd-detect-virt', ['--virtualization']);
    if (virt != null) {
      final v = virt.trim().toLowerCase();
      if (v.isNotEmpty && v != 'none') {
        hits.add(DesktopCheckHit('systemd-detect-virt', v, strong: true, score: 3));
      }
    }

    // 3) /proc/cpuinfo hypervisor 标志。
    final cpu = await readFileSafe('/proc/cpuinfo');
    if (cpu != null) {
      final low = cpu.toLowerCase();
      if (low.contains('hypervisor')) {
        hits.add(const DesktopCheckHit('cpuinfo.flags', 'hypervisor', score: 2));
      }
      final kw = firstKeywordHit(low, const ['qemu', 'virtualbox', 'vmware', 'kvm', 'bochs']);
      if (kw != null) {
        hits.add(DesktopCheckHit('cpuinfo.model', kw, strong: true, score: 3));
      }
    }

    // 4) 容器指纹：/.dockerenv、cgroup。
    try {
      if (await File('/.dockerenv').exists()) {
        hits.add(const DesktopCheckHit('container', '.dockerenv', strong: true, score: 3));
      }
    } catch (_) {}
    final cgroup = await readFileSafe('/proc/1/cgroup');
    if (cgroup != null) {
      final kw = firstKeywordHit(cgroup, const ['docker', 'containerd', 'kubepods', 'lxc', 'snap']);
      if (kw != null) {
        hits.add(DesktopCheckHit('cgroup', kw, score: 2));
      }
    }

    // 5) 云实例 metadata vendor 文件（部分发行版）。
    final cloudInit = await readFileSafe('/sys/class/dmi/id/bios_version');
    if (cloudInit != null && firstKeywordHit(cloudInit, const ['alibaba', 'tencent', 'aws', 'aliyun']) != null) {
      hits.add(DesktopCheckHit('bios_version', cloudInit.trim(), score: 2));
    }

    return hits;
  }

  @override
  Future<bool> detectSystemProxy() async {
    try {
      final mode = await runCmd(
        'gsettings',
        ['get', 'org.gnome.system.proxy', 'mode'],
      );
      if (mode != null) {
        final m = mode.toLowerCase();
        if (m.contains('manual') || m.contains('auto')) {
          final host = await runCmd(
            'gsettings',
            ['get', 'org.gnome.system.proxy.http', 'host'],
          );
          if (host != null && host.replaceAll("'", '').trim().isNotEmpty) {
            return true;
          }
        }
      }
    } catch (_) {}
    return false;
  }

  @override
  Future<bool?> detectSystemVpn() async {
    // 解析默认路由出口接口，匹配 VPN 命名（tun/ppp/wg/ipsec）。
    final route = await runCmd('ip', ['route', 'show', 'default']);
    if (route != null) {
      final m = RegExp(r'dev\s+(\S+)').firstMatch(route);
      if (m != null) {
        final dev = m.group(1)!.toLowerCase();
        if (dev.contains('tun') || dev.contains('tap') ||
            dev.contains('ppp') || dev.contains('wg') ||
            dev.contains('ipsec') || dev.contains('vpn')) {
          return true;
        }
      }
    }
    return null;
  }

  @override
  Future<bool> detectPrivilegedEnv() async {
    final id = await runCmd('id', ['-u']);
    if (id != null && id.trim() == '0') return true;
    try {
      final status = await readFileSafe('/proc/self/status');
      if (status != null) {
        final m = RegExp(r'Uid:\s+(\d+)').firstMatch(status);
        if (m != null && m.group(1) == '0') return true;
      }
    } catch (_) {}
    return false;
  }

  @override
  Future<DeviceInfoSnapshot> probeDeviceInfo() async {
    final osRelease = await readFileSafe('/etc/os-release');
    String prettyName = '';
    int major = 0;
    if (osRelease != null) {
      final kv = parseKeyValue(osRelease);
      prettyName = kv['pretty_name'] ?? kv['name'] ?? '';
      final versionId = kv['version_id'] ?? '';
      final m = RegExp(r'(\d+)').firstMatch(versionId);
      if (m != null) major = int.tryParse(m.group(1)!) ?? 0;
    }
    final manufacturer = (await readFileSafe('/sys/class/dmi/id/sys_vendor'))?.trim() ?? '';
    final model = (await readFileSafe('/sys/class/dmi/id/product_name'))?.trim() ?? '';
    final board = (await readFileSafe('/sys/class/dmi/id/board_name'))?.trim() ?? '';
    final bios = (await readFileSafe('/sys/class/dmi/id/bios_vendor'))?.trim() ?? '';
    String cpu = '';
    final cpuInfo = await readFileSafe('/proc/cpuinfo');
    if (cpuInfo != null) {
      final m = RegExp(r'model name\s*:\s*(.+)').firstMatch(cpuInfo);
      cpu = m?.group(1)?.trim() ?? '';
      if (cpu.isEmpty) {
        final arm = RegExp(r'Hardware\s*:\s*(.+)').firstMatch(cpuInfo);
        cpu = arm?.group(1)?.trim() ?? '';
      }
    }
    final kernel = Platform.operatingSystemVersion; // e.g. "Linux host 5.15.0 #1 SMP ..."
    return DeviceInfoSnapshot(
      platformVersion: prettyName.isNotEmpty ? prettyName : 'Linux $kernel',
      osMajorVersion: major,
      model: model.isNotEmpty ? model : Platform.localHostname,
      manufacturer: manufacturer.isNotEmpty ? manufacturer : 'Linux',
      deviceName: Platform.localHostname,
      product: board.isNotEmpty ? board : model,
      cpu: cpu,
      hardware: bios.isNotEmpty ? bios : 'Linux',
    );
  }

  @override
  Future<String?> probeMachineId() async {
    final a = await readFileSafe('/etc/machine-id');
    if (a != null && a.trim().isNotEmpty) return a.trim();
    final b = await readFileSafe('/var/lib/dbus/machine-id');
    if (b != null && b.trim().isNotEmpty) return b.trim();
    return null;
  }
}
