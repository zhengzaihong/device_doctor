## Unreleased

* 【检测】模拟器深度检测（重点修复 MuMu12/Android15 伪装镜像漏报）：
  单次 `getprop` 全量导出聚合扫描 20+ 厂商关键字（`ro.kernel.qemu=1` 误报防护，
  `ro.kernel.qemu=0` 真机不再计分）；`brandByProp` 跨属性品牌直判（
  `ro.hardware`/`board`/`device`/`model`/`brand`/`manufacturer`/`platform`）
  命中即等效强命中；新增内核版本串（nemu/vbox/goldfish）、GPU 软渲染栈
  （emulation/swiftshader/angle/ranchu）、传感器名/厂商签名、init.svc 专属
  守护进程、挂载表（vboxsf/virtio-9p/qemu-mount/nemu-nox 共享）、电池桩/
  通话缺失/虚拟输入设备等约 10 个新维度，计分明细经 `hits` 字段透出
* 【检测】Mode/Board/Platform/Manufacturer/Flavor 新增国产模拟器伪装机型关键字
  （mumu/nemu/ldplayer/nox/memu/vbox86/redfinger/vmos/cloudphone 等）；
  cgroup 识别 docker/lxc/qemu/mumu/nox 痕迹
* 【检测】包名指纹：内置 90+（MuMu12/雷电9/夜神/逍遥/蓝叠5/VMOS/红手指等）、
  `emulator_config.json` v2 同步扩充、模糊关键字兜底改名组件；修复 Android 11+
  包可见性裁剪导致的静默漏报（Manifest `<queries>` 预声明 + 逐包探测 + 文件
  探针常开）；`getSimulatorInfo`/`getSimulatorBrand` 遍历全部命中取品牌，
  `loadApps` 全量枚举不再只认 BlueStacks
* 【稳定】`CommandUtil.exec` 并发读 stdout：修复 `getprop` 全量输出超 64KB
  管道阻塞必超时的问题；新增 `getAllProperties()` 一次导出复用
* 【检测】Root 判定收紧（修复未 root 真机被误判）：仅凭 su 二进制存在或
  `/data/adb` 目录存在不再判 root；确凿证据才判：进程 uid 0、真实 root
  管理器包、su 实测提权到 uid=0、Magisk-KSU 专属痕迹、SELinux Permissive、
  adbd root 运行；test-keys/debuggable/解锁 BL 降为弱信号，任一单个不判，
  凑齐 2 个才判；命中时 logcat 打印 `isRoot=true: <依据>`
* 【文档】`isEmulator`/`isRootEnv` 判定规则写入 Dart doc；示例 README 补充
  `hits`/`brandByProp` 字段说明（待补充）

## 0.3.0

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

## 0.2.0

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
