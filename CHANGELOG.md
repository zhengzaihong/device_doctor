
## 1.0.0

* 【更名】包与插件整体由 `android_work` 更名为 `device_doctor`（`AndroidWork` → `DeviceDoctor`，
  Kotlin `com.zzh.android_work` → `com.zzh.device_doctor`，podspec `android_work` → `device_doctor`）
* 【兼容】旧 `package:android_work/android_work.dart` import 路径继续可用（顶层 re-export shim）；
  旧类名 `AndroidWork`/`AndroidWorkPlatform`/`MethodChannelAndroidWork`/`AndroidWorkPlugin` 保留为
  typedef/Deprecated 别名；原生同时注册新通道 `device_doctor` 与旧通道 `flutter_native_android_work`，
  Dart 侧按 新通道 → 旧通道 → Dart 启发式 三级回退
* 【多平台】新增 Windows/macOS/Linux/Web Dart 回退：代理（环境变量/`HttpClient.findProxyFromEnvironment`）、
  VPN（`NetworkInterface` 接口名 tun/tap/utun/ppp/ipsec）、模拟器启发式（`/proc/cpuinfo` hypervisor、
  VBOX/VMWARE/QEMU 环境变量、PROCESSOR_IDENTIFIER）
* 【桌面平台】新增 Windows / Linux / macOS 纯 Dart 平台实现（pubspec `dartPluginClass` 自动注册），
  与 Android 端功能对齐：
  - `DeviceDoctorWindows`：WMI（ComputerSystem/BIOS/DiskDrive）、注册表 BIOS/Internet Settings/MachineGuid、
    MAC OUI（VirtualBox/VMware/QEMU/Hyper-V/Xen/Parallels）、安卓模拟器与虚拟机进程枚举、
    `Get-VpnConnection`、`fltmc`/`net session` 管理员探针、`ipconfig /all` 网卡采集；
  - `DeviceDoctorLinux`：`/sys/class/dmi/id/*`、`systemd-detect-virt`、`/proc/cpuinfo` hypervisor、
    Docker/LXC 容器指纹、gsettings 代理、`ip route` 默认路由 VPN 接口、`id -u`/`/proc/self/status` 提权判定、
    `/etc/os-release`+DMI 设备信息、`/etc/machine-id` 机器码；
  - `DeviceDoctorMacOS`：`sysctl hw.model`/CPU vendor、虚拟设备节点（`/dev/vboxguest` 等）、
    `networksetup` 代理、`utun`+`scutil --nc` VPN、`IOPlatformUUID` 机器码、`sw_vers` 设备信息；
  - 输出结构与 Android 契约对齐（`value` 可疑分 + `hardwareHit` 强命中 + `hits` 明细），结果 5min TTL 缓存，
    `addCustomEmulatorPackages()` 桌面端生效；`getSignatures` 桌面端按语义返回空
* 【稳定】method channel 兜底修复：iOS/Android `notImplemented`（PlatformException）不再上抛，
  继续走 legacy 通道与 Dart 回退，修复 iOS 上 `isProxy` 等未实现方法抛异常的问题
* 【测试】新增桌面平台单测：阈值判定、桌面计分表结构、自定义指纹计分、签名空兜底
* 【iOS】`DeviceDoctorPlugin` 注册双通道并覆盖全部设备信息方法，未支持方法显式回
  `FlutterMethodNotImplemented`（修复旧 `SwiftDeviceInformationPlugin` 无 default 分支导致 Future 挂起）；
  删除冗余的 `SwiftDeviceInformationPlugin`/`DeviceInformationPlugin.h/.m`
* 【清理】删除旧包 `com.zzh.android_work` 下重复的 Handler/simulator Java 实现（仅保留 typealias 兼容），
  消除同一 legacy 通道被两套 handler 抢占的注册冲突
* 【测试】测试迁移至 `test/device_doctor_test.dart`：新通道、legacy 回退、无原生回退、兼容别名 4 组用例


* 【安全】移除全局信任所有证书的 `Https.handleSSLHandshake()`（修复整 App HTTPS 校验失效）
* 【稳定】修复 `isOpenVPN`/`getIMEINumber` 无 Activity 时 NPE；`getIMEINumber` 不再挂起不回包（补全 error/success 分支）
* 【稳定】`GlobalScope` 替换为结构化 `CoroutineScope`，插件 detach 时取消；异常统一回传 `result.error`
* 【检测】修复模拟器强特征（`vbox`/`sdk_gphone`/`genymotion`/`netease` 等）不计分的权重 Bug
* 【检测】`ro.hardware` 判空防 NPE；模拟器指纹包名去重并增补（雷电9/MUMU 克隆器/BlueStacks 5 等）
* 【架构】通道统一为 `flutter_native_android_work`，Dart 层走 `AndroidWorkPlatform` 强类型接口（可 mock、可测试）
* 【架构】`isSimulator` 返回完整计分表 `Map<String,dynamic>`，新增 `isEmulator({threshold})` 与 `hardwareHit` 强命中短路
* 【性能】模拟器检测/包名扫描/CPU 信息读取全部移至 IO 线程，结果 5 分钟 TTL 缓存，新增 `clearSimulatorCache()`
* 【性能】55 次 `getPackageInfo` IPC 改为一次 `getInstalledPackages` 批量匹配，失败回退逐包查询
* 【加固】`isRoot` 支持 Magisk 路径/包名检测与 900ms 超时；`CommandUtil` 修复 stderr 死锁与 512 倍数截断
* 【加固】`getSignature` 支持 MD5/SHA-1/SHA-256（大小写归一化、去重、`SigningInfo` 判空）
* 【权限】删除废弃 `GET_TASKS`；`READ_PHONE_STATE` 限 `maxSdkVersion=28`；新增 `ACCESS_NETWORK_STATE`
* 【指纹库】内置 `emulator_config.json` 外置指纹，支持 `addCustomEmulatorPackages()` 运行时追加
* 【构建】Kotlin 1.9.22 / AGP 8.1.4 / compileSdk 35 / Java 11；新增单元测试

## 0.1.0

Get Android device information
