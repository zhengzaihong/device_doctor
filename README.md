# device_doctor

[![pub package](https://img.shields.io/pub/v/device_doctor.svg)](https://pub.dev/packages/device_doctor)
[![license](https://img.shields.io/github/license/zhengzaihong/device_doctor)](LICENSE)

跨平台设备环境检测 Flutter 插件（前身 `android_work`）：模拟器/虚拟机、Root/越狱、代理/VPN、APK 签名与设备信息。Android 为原生深度检测，iOS 提供设备信息，Windows / Linux / macOS 提供原生桌面检测模块（纯 Dart 平台实现），无对应实现时回退 Dart 启发式。

> 由 `android_work` 升级的项目无需修改 import 路径与类名（兼容别名见下文）。

---

## 功能一览

| 模块 | 接口 | 说明 |
|------|------|------|
| 模拟器/虚拟机检测 | `isSimulator()` / `isEmulator({threshold})` | Android 20+ 维度计分 + `ro.hardware` 强命中；Windows/Linux/macOS 桌面原生检测（DMI/WMI/注册表/MAC OUI/进程/驱动等）；其余 Dart 启发式 |
| 模拟器品牌 | `getSimulatorInfo()` | `mumu` / `雷电` / `夜神` / `BlueStacks` / `逍遥` 等；桌面端返回 VirtualBox/VMware/QEMU/Hyper-V/容器等命中文案 |
| Root / Magisk | `isRootEnv()` | Android：uid 0 / su 提权实测 / root 管理器包 / Magisk·KSU 痕迹 / SELinux / adb root；su 二进制存在不单独判 root，真机不误报；桌面端：root/Administrator 特权运行判定 |
| 代理 | `isProxy()` | Android：JVM 属性 + `ConnectivityManager.defaultProxy` + `Settings.System`；Windows：注册表 Internet Settings；Linux：gsettings/环境变量；macOS：networksetup |
| VPN | `isOpenVPN()` | Android：`TRANSPORT_VPN`；桌面：接口名（tun/tap/utun/ppp/ipsec/wireguard）+ 系统探针（`ip route` 默认路由 / `Get-VpnConnection` / `scutil --nc`） |
| APK 签名 | `getSignatures(type)` | `MD5` `SHA-1` `SHA-256`（小写 hex；仅 Android 有意义，桌面端恒返回 []） |
| 设备信息 | `platformVersion` `apiLevel` `deviceModel` `deviceManufacturer` `deviceName` `productName` `cpuName` `hardware` `deviceIMEINumber` | Android 读 `Build.*`；Windows 读 WMI/DMI；Linux 读 `/sys/class/dmi`+`/etc/os-release`；macOS 读 `sysctl`/`sw_vers`；`deviceIMEINumber` 桌面端返回机器唯一 ID（MachineGuid / machine-id / IOPlatformUUID） |
| 缓存清理 | `clearSimulatorCache()` | 检测结果 5min TTL（与 Android 一致），可手动清缓存重测 |
| 自定义指纹 | `addCustomEmulatorPackages()` | 追加自定义指纹（`/` 前缀视为文件路径，其余为包名/关键字） |

---

## 安装

```yaml
dependencies:
  device_doctor: ^1.0.0  //旧版：android_work:0.1.0
```

```dart
import 'package:device_doctor/device_doctor.dart';
const doctor = DeviceDoctor();
```

桌面平台（Windows/Linux/macOS）通过 `dartPluginClass` 自动注册对应平台实现，无需任何额外初始化。

## 快速开始

```dart
const doctor = DeviceDoctor();

// 模拟器/虚拟机（hardwareHit 强命中直接返回 true）
if (await doctor.isEmulator()) {
  // 可疑分 >= 阈值（默认 5）即判定为虚拟环境
}
if (await doctor.isEmulator(threshold: 8)) { /* 更严格 */ }

// 完整计分表（含各维度明细，可用于自定义策略）
final raw = await doctor.isSimulator();
// Android: { hardware, hardwareHit, flavor, model, manufacturer, board,
//            platform, baseBand, sensorNumber, supportCamera, ..., value }
// 桌面端:  { platform, hardwareHit, value, hits: [{source, detail, strong, score}],
//            sources, model, manufacturer, hardware, note }

// 环境检测
final proxy = await doctor.isProxy();
final vpn   = await doctor.isOpenVPN();
final root  = await doctor.isRootEnv();
final sigs  = await doctor.getSignatures('SHA-256'); // 或 'MD5' / 'SHA-1'

// 追加自定义指纹（检测更激进）
await doctor.addCustomEmulatorPackages(['com.some.newemu.launcher']);

// 结果缓存（模拟器/包名/CPU 信息）
await doctor.clearSimulatorCache();
```

## 检测原理（Android）

| 维度 | 计分 |
|------|------|
| 品牌/型号/主板/平台/指纹 等特征值命中 | +1/项 |
| 基带版本 `1.0.0.0` | +2 |
| 传感器 ≤7、缺相机/闪光灯/蓝牙/光照/GPS/温度 | +1/项 |
| Intent/`Build.*` generic/vbox 等 | +1 |
| `ro.hardware` 品牌强特征（cancro→MUMU、nox/x86→夜神、android_x86→雷电） | **+100（短路命中）** |
| 已安装模拟器指纹包/文件路径 | +3 |
| `/proc/cpuinfo` 含 intel/amd、qemu pipes、x86+arm 双 ABI | +1/项 |

判定阈值默认 `threshold: 5`；建议 `threshold: 8` 配合 `getSimulatorInfo()` 做品牌白/黑名单。

## 检测原理（Windows / Linux / macOS）

| 平台 | 虚拟化检测 | 代理 | VPN | 特权环境 |
|------|-----------|------|-----|---------|
| Windows | WMI（`Win32_ComputerSystem`/`Win32_BIOS`/`Win32_DiskDrive`）、MAC OUI（VirtualBox/VMware/QEMU/Hyper-V/Xen/Parallels 前缀）、虚拟机与安卓模拟器进程（雷电/蓝叠/MuMu/夜神/逍遥…）、环境变量指纹 | 注册表 `Internet Settings`（ProxyEnable/ProxyServer）+ 环境变量 | `Get-VpnConnection`（PowerShell CIM）+ 接口名 | `fltmc` / `net session` 退出码（是否管理员运行） |
| Linux | `/sys/class/dmi/id/*`（sys_vendor/product_name/bios_vendor…）、`systemd-detect-virt`、`/proc/cpuinfo` hypervisor 位、Docker/LXC 容器指纹（`/.dockerenv`、`/proc/1/cgroup`）、云厂商 BIOS | gsettings（GNOME 手动/自动代理）+ 环境变量 | `ip route` 默认路由出口接口（tun/ppp/wg）+ 接口名 | `id -u` / `/proc/self/status`（euid=0） |
| macOS | `sysctl hw.model`/`machdep.cpu.vendor`、`/dev/vboxguest` 等虚拟设备节点、虚拟化进程 | `networksetup -getwebproxy/securewebproxy/socksfirewallproxy` | `utun` 接口 + 默认路由 + `scutil --nc list` | `id -u`（euid=0） |

强命中（`strong`，等效 Android `hardwareHit`）：DMI/WMI 厂商型号直接暴露虚拟化品牌、MAC OUI 虚拟化前缀、`systemd-detect-virt`、已知安卓模拟器进程、虚拟设备节点等。

## 指纹库

内置指纹见 `android/src/main/assets/emulator_config.json`（Android 端加载）；桌面端指纹内置于各平台实现（`device_doctor_windows.dart` / `device_doctor_linux.dart` / `device_doctor_macos.dart`），可通过 `addCustomEmulatorPackages()` 运行时追加。

## 兼容迁移（android_work → device_doctor）

```dart
// 旧（仍可用）
import 'package:android_work/android_work.dart';
const work = AndroidWork();

// 新
import 'package:device_doctor/device_doctor.dart';
const doctor = DeviceDoctor();
```

* 旧类名 **继续可用**：`AndroidWork`/`AndroidWorkPlatform`/`MethodChannelAndroidWork` 等为兼容别名；
* 原生双通道：新 `device_doctor`、旧 `flutter_native_android_work` 同时注册，新→旧→Dart 兜底三级回退；
* 旧 `AndroidManifest` 无需修改（插件自动注册）。

## 权限与隐私

* Android 需要 `ACCESS_NETWORK_STATE`（代理/VPN 判定）；`READ_PHONE_STATE(maxSdk 28)`；
* `deviceIMEINumber`：Android 10+ 无法读取 IMEI（回退 Widevine DRM ID）；桌面端返回机器唯一 ID。**请勿明文上报，评估合规要求（GDPR / 个人信息保护法）**
* Android 11+ 读取安装包列表需 `QUERY_ALL_PACKAGES`（Google Play 政策敏感，建议结合运行时策略）。

## 局限与免责

* 模拟器/Root/签名/VPN 深度检测 Android 最完善；iOS 提供设备信息（identifierForVendor 兜底机器码），其余项走 iOS Dart 启发式兜底；
* 检测结果基于 `Build.*`/DMI/WMI/cpuinfo 等可被定制 ROM 或反检测工具伪造；
* Hook 框架（Frida/Xposed）等高级对抗不在本插件范围；
* Root/特权检测在 Android 14+ 部分 OEM ROM 上 `su` 路径不可见，受 SELinux 限制存在漏检。

## 测试 Mock

```dart
class FakeDoctor extends DeviceDoctorPlatform {
  @override
  Future<bool> isRootEnv() async => false;
  // ... 其余按需
}
DeviceDoctorPlatform.instance = FakeDoctor();
```

`test/device_doctor_test.dart` 覆盖：阈值判定、桌面计分表结构、自定义指纹、签名兜底等用例，无需真实设备即可 `flutter test`。

## 环境

| 项 | 要求 |
|------|------|
| Flutter | ≥ 3.22 |
| Dart | ≥ 3.4 |
| Android minSdk | 21（compileSdk 35，AGP 8.1+，Kotlin 1.9） |
| iOS | ≥ 12.0（设备信息模块） |
| Windows / Linux / macOS | Flutter 桌面（纯 Dart 平台实现，无本地依赖） |

## License

见 [LICENSE](LICENSE)。
