import 'dart:io';

import 'device_doctor_desktop_base.dart';
import 'device_doctor_platform_interface.dart';
import 'platform_util.dart';

/// Windows 平台检测实现（纯 Dart，经 pubspec `dartPluginClass` 注册）。
///
/// 对齐 Android 端能力：模拟器/虚拟机指纹、系统代理、VPN、特权环境、设备信息。
/// 检测手段：WMI（`wmic`/`powershell` CIM）、注册表 `reg query`、MAC OUI、
/// 进程枚举与虚拟网卡名。
class DeviceDoctorWindows extends DesktopDeviceDoctorBase {
  /// Flutter 桌面端插件注册入口。
  static void registerWith() {
    DeviceDoctorPlatform.instance = DeviceDoctorWindows();
  }

  @override
  String get platformId => 'windows';

  // 常见虚拟化网卡 OUI 前缀（MAC 前 3 字节）。
  static const List<String> _vmOuiPrefixes = [
    '08:00:27', // VirtualBox
    '0a:00:27', // VirtualBox (NAT)
    '00:05:69', // VMware
    '00:0c:29', // VMware
    '00:1c:14', // VMware
    '00:50:56', // VMware
    '52:54:00', // QEMU/KVM
    '52:55:00', // QEMU/KVM (parallels alt)
    '00:15:5d', // Microsoft Hyper-V
    '00:03:ff', // Microsoft Virtual PC
    '00:16:3e', // Xensource/XCP
    '00:1c:42', // Parallels
  ];

  // 常见虚拟机/模拟器相关进程名（小写）。
  static const List<String> _vmProcesses = [
    'vboxservice.exe', 'vboxtray.exe', 'vboxsvc', // VirtualBox guest
    'vmtoolsd.exe', 'vmwaretray.exe', 'vmusr.exe', // VMware guest
    'qemu-ga.exe', // QEMU guest agent
    'vmtools', 'xenguest', 'xsdaemon', // Xen guest
    'vmwp.exe', 'vmms.exe', // Hyper-V host workers
    'dnplayer.exe', 'ldbox64.exe', 'ldvmonitor', // 雷电
    'bluestacks.exe', 'hd-service.exe', 'hd-boot.exe', // BlueStacks
    'mumu.exe', 'mumuvmmheadless.exe', 'nemux64.exe', // MuMu/网易
    'nox.exe', 'nox_adb.exe', 'bignox', // 夜神
    'memu.exe', 'cemu_hp.exe', 'memuc.exe', // 逍遥
    'microvirt', 'aow_exe.exe', 'androidemulator',
  ];

  @override
  Future<List<DesktopCheckHit>> detectVirtualization() async {
    final hits = <DesktopCheckHit>[];

    // 1) WMI 计算机制造商/型号/BIOS（最强指纹）。
    final cs = await _wmiQuery(
      'Win32_ComputerSystem',
      'Manufacturer,Model,PCSystemType',
    );
    if (cs != null) {
      final mfg = _wmiValue(cs, 'Manufacturer');
      final model = _wmiValue(cs, 'Model');
      final combined = '$mfg $model';
      final kw = firstKeywordHit(combined, const [
        'virtualbox', 'vmware', 'qemu', 'kvm', 'xen', 'bochs',
        'hyper-v', 'microsoft virtual', 'parallels', 'innotek', 'virtual machine',
      ]);
      if (kw != null) {
        hits.add(DesktopCheckHit('wmi.computersystem', combined, strong: true, score: 3));
      }
    }

    final bios = await _wmiQuery('Win32_BIOS', 'Manufacturer,SVersion,Version');
    if (bios != null) {
      final biosInfo = '${_wmiValue(bios, 'Manufacturer')} ${_wmiValue(bios, 'SVersion')} ${_wmiValue(bios, 'Version')}';
      final kw = firstKeywordHit(biosInfo, const [
        'virtualbox', 'vmware', 'qemu', 'sea bios', 'coreboot', 'bochs', 'innotek',
      ]);
      if (kw != null) {
        hits.add(DesktopCheckHit('wmi.bios', biosInfo, strong: true, score: 3));
      }
    }

    // 2) 环境变量指纹（云/容器/虚拟化常见变量）。
    final env = Platform.environment;
    final envProbe = env.keys
        .map((k) => '$k=${env[k]}')
        .join(' ')
        .toLowerCase();
    for (final entry in const [
      ['vbox', 'vbox env'],
      ['vmware', 'vmware env'],
      ['qemu', 'qemu env'],
      ['kvm', 'kvm env'],
      ['container', 'container env'],
      ['docker', 'docker env'],
      ['aws', 'aws env'],
      ['gce', 'gce env'],
      ['azure', 'azure env'],
    ]) {
      if (envProbe.contains(entry[0])) {
        hits.add(DesktopCheckHit('env', entry[1], score: 2));
        break;
      }
    }

    // 3) MAC 地址 OUI 前缀。
    final macs = await _collectMacAddresses();
    for (final mac in macs) {
      final prefix = mac.length >= 8 ? mac.substring(0, 8).toLowerCase() : mac.toLowerCase();
      if (_vmOuiPrefixes.contains(prefix)) {
        hits.add(DesktopCheckHit('mac_oui', mac, strong: true, score: 3));
        break;
      }
    }

    // 4) 虚拟机/模拟器进程枚举。
    final procs = await runCmd('tasklist', ['/fo', 'csv', '/nh'],
        timeout: const Duration(seconds: 3));
    if (procs != null) {
      final low = procs.toLowerCase();
      for (final p in _vmProcesses) {
        if (low.contains(p)) {
          hits.add(DesktopCheckHit('process', p, strong: true, score: 3));
          break;
        }
      }
    }

    // 5) 虚拟磁盘/光驱设备名（IDE 通道），偏强提示。
    final disk = await _wmiQuery('Win32_DiskDrive', 'Model,InterfaceType');
    if (disk != null) {
      final kw = firstKeywordHit(disk, const [
        'virtual', 'vmware', 'vbox', 'qemu', 'microsoft virtual',
      ]);
      if (kw != null) {
        hits.add(DesktopCheckHit('wmi.disk', kw, score: 2));
      }
    }

    return hits;
  }

  @override
  Future<bool> detectSystemProxy() async {
    try {
      final out = await runCmd(
        'reg',
        [
          'query',
          r'HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings',
          '/v',
          'ProxyEnable',
        ],
      );
      if (out != null && out.contains('0x1')) {
        final server = await runCmd(
          'reg',
          [
            'query',
            r'HKCU\Software\Microsoft\Windows\CurrentVersion\Internet Settings',
            '/v',
            'ProxyServer',
          ],
        );
        if (server != null && server.toUpperCase().contains('PROXY')) {
          return true;
        }
        // ProxyEnable=1 即认为有代理配置。
        return true;
      }
    } catch (_) {}
    return false;
  }

  @override
  Future<bool?> detectSystemVpn() async {
    // PowerShell 的 VPN 连接状态（中文/英文系统均输出枚举值 Connected）。
    final out = await runCmd(
      'powershell',
      [
        '-NoProfile',
        '-NonInteractive',
        '-Command',
        r"Get-VpnConnection -AllUserConnection -ErrorAction SilentlyContinue | Where-Object {`$_.ConnectionStatus -eq 'Connected'} | Select-Object -ExpandProperty Name",
      ],
      timeout: const Duration(seconds: 2),
    );
    if (out != null && out.trim().isNotEmpty) return true;
    return null;
  }

  @override
  Future<bool> detectPrivilegedEnv() async {
    // fltmc / net session 仅管理员可成功执行，以退出码判定（与系统语言无关）。
    final fltmc = await runCmdOk('fltmc', [], timeout: const Duration(milliseconds: 900));
    if (fltmc == true) return true;
    final net = await runCmdOk('net', ['session'], timeout: const Duration(milliseconds: 900));
    return net == true;
  }

  @override
  Future<DeviceInfoSnapshot> probeDeviceInfo() async {
    final os = Platform.operatingSystemVersion; // e.g. "10.0.22631"
    final major = _parseWindowsMajor(os);
    final cs = await _wmiQuery(
      'Win32_ComputerSystem',
      'Manufacturer,Model,Name',
    );
    final cpu = await _wmiQuery('Win32_Processor', 'Name');
    final bios = await _wmiQuery('Win32_BIOS', 'Manufacturer');
    final manufacturer = cs != null ? _wmiValue(cs, 'Manufacturer') : 'Windows';
    final model = cs != null ? _wmiValue(cs, 'Model') : '';
    final name = cs != null ? _wmiValue(cs, 'Name') : Platform.localHostname;
    final cpuName = cpu != null ? _wmiValue(cpu, 'Name') : Platform.version;
    return DeviceInfoSnapshot(
      platformVersion: 'Windows $os',
      osMajorVersion: major,
      model: model.isNotEmpty ? model : name,
      manufacturer: manufacturer.isNotEmpty ? manufacturer : 'Windows',
      deviceName: name,
      product: model,
      cpu: cpuName.trim(),
      hardware: (bios?.isNotEmpty ?? false) ? _wmiValue(bios!, 'Manufacturer') : 'PC/AT',
    );
  }

  @override
  Future<String?> probeMachineId() async {
    try {
      final out = await runCmd(
        'reg',
        ['query', r'HKLM\SOFTWARE\Microsoft\Cryptography', '/v', 'MachineGuid'],
      );
      if (out != null) {
        final m = RegExp(r'MachineGuid\s+REG_SZ\s+(\S+)').firstMatch(out);
        if (m != null) return m.group(1);
      }
    } catch (_) {}
    return null;
  }

  // ── Windows 专属工具 ────────────────────────────────────────────────

  int _parseWindowsMajor(String version) {
    // Windows 11 仍是 10.0.x，用 22000+ 构建号粗略区分。
    final m = RegExp(r'(\d+)\.(\d+)\.(\d+)').firstMatch(version);
    if (m == null) {
      return version.trim().toLowerCase().startsWith('11') ? 11 : 10;
    }
    final major = int.tryParse(m.group(1)!) ?? 10;
    final build = int.tryParse(m.group(3)!) ?? 0;
    if (major >= 11) return 11;
    if (major == 10 && build >= 22000) return 11;
    return major;
  }

  /// 优先使用 `wmic`（快），失败回退 PowerShell CIM。返回原始多行文本。
  Future<String?> _wmiQuery(String className, String props) async {
    final wmic = await runCmd(
      'wmic',
      [className, 'get', props, '/format:list'],
      timeout: const Duration(milliseconds: 1500),
    );
    if (wmic != null && wmic.contains('=')) return wmic;
    // 回退：PowerShell Get-CimInstance
    final ps = await runCmd(
      'powershell',
      [
        '-NoProfile',
        '-NonInteractive',
        '-Command',
        "Get-CimInstance -ClassName $className | Select-Object $props | Format-List",
      ],
      timeout: const Duration(seconds: 3),
    );
    return ps;
  }

  String _wmiValue(String block, String key) {
    for (final raw in block.split(RegExp(r'\r?\n'))) {
      final line = raw.trim();
      if (line.isEmpty) continue;
      final idx = line.indexOf('=');
      if (idx <= 0) continue;
      if (line.substring(0, idx).trim().toLowerCase() == key.toLowerCase()) {
        return line.substring(idx + 1).trim();
      }
    }
    return '';
  }

  Future<List<String>> _collectMacAddresses() async {
    // NetworkInterface 不暴露 MAC，Windows 上走 ipconfig /all。
    final macs = <String>[];
    final out = await runCmd('ipconfig', ['/all'], timeout: const Duration(seconds: 2));
    if (out != null) {
      final matches = RegExp(r'([0-9A-Fa-f]{2}[:-]){5}[0-9A-Fa-f]{2}')
          .allMatches(out);
      for (final m in matches) {
        macs.add(m.group(0)!.replaceAll('-', ':').toLowerCase());
      }
    }
    return macs;
  }
}
