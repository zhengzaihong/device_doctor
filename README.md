# android_work

[![pub package](https://img.shields.io/pub/v/android_work.svg)](https://pub.dev/packages/android_work)
[![license](https://img.shields.io/github/license/zhengzaihong/android_work)](LICENSE)

Android 真机/模拟器、Root、代理/VPN、APK 签名与设备信息检测的 Flutter 插件（风控组件主打模拟器检测）。iOS 侧能力未实现，调用会在 iOS 上抛 `MissingPluginException`，请做平台判断或通过 `AndroidWorkPlatform` 注入自定义实现。

---

## 特性一览

| 能力 | 方法 | 说明 |
|------|------|------|
| 模拟器计分检测 | `isSimulator()` / `isEmulator({threshold})` | 20+ 维度计分 + `ro.hardware` 指纹 + 包名/文件探针 |
| 品牌识别 | `getSimulatorInfo()` | `mumu` / `蓝叠` / `雷电` / `夜神` / `逍遥` 等 |
| Root / Magisk | `isRootEnv()` | su 多路径 + 管理器包名 + `test-keys` + 带超时实测 |
| 代理 | `isProxy()` | JVM 代理属性 → `ConnectivityManager.defaultProxy` → `Settings.System` |
| VPN | `isOpenVPN()` | `TRANSPORT_VPN` |
| APK 签名摘要 | `getSignatures(type)` | `MD5` `SHA-1` `SHA-256`，去重小写 hex |
| 设备信息 | `platformVersion` `apiLevel` `deviceModel` `deviceManufacturer` `deviceName` `productName` `cpuName` `hardware` `deviceIMEINumber` | 直透 `Build.*`，见下「权限与隐私」 |

---

## 安装

```yaml
dependencies:
  android_work: ^0.2.0
```

```dart
import 'package:android_work/android_work.dart';
const work = AndroidWork();
```

## 快速开始

```dart
const work = AndroidWork();

// 推荐：阈值判定（hardwareHit 强命中优先）
if (await work.isEmulator()) {
  // 标记为模拟器，阈值默认 4，可按业务调参
}
if (await work.isEmulator(threshold: 8)) { /* 更严格 */ }

// 细粒度：读取完整计分表自行判决
final raw = await work.isSimulator();
// raw: { hardware, hardwareHit, flavor, model, manufacturer, board,
//        platform, baseBand, sensorNumber, supportCamera,
//        supportCameraFlash, supportBluetooth, hasLightSensor,
//        cgroupResult, value }
debugPrint('score=${raw['value']} hit=${raw['hardwareHit']}');

// 品牌
final brands = await work.getSimulatorInfo(); // e.g. ["mumu"]

// 环境
final isProxy = await work.isProxy();
final isVpn   = await work.isOpenVPN();
final isRoot  = await work.isRootEnv();

// 签名
final sha256 = await work.getSignatures('SHA-256');

// 设备
final model = await work.deviceModel;
final imei  = await work.deviceIMEINumber; // Android 10+ 可能为 null

// 缓存：结果 TTL 5 分钟，强制重检
await work.clearSimulatorCache();

// 运行时指纹追加（与内置 + assets/emulator_config.json 合并）
await work.addCustomEmulatorPackages(['com.example.myemu', '/data/data/com.example.myemu']);
await work.clearSimulatorCache(); // 已在 addCustom 内部自动清，也可显式再清
```

更多可见 `example/lib/main.dart`（`permission_handler` 授权 + 一键全量检测示例）。

---

## 计分模型与阈值

| 信号 | 权重 |
|------|------|
| `ro.build.flavor` `ro.product.model/manufacturer/board/board.platform` 可疑 | +1 / 项 |
| `gsm.version.baseband` 缺失或 `1.0.0.0` | +2 |
| `Tools.isSimulator()`（指纹/拨号能力/运营商名等） | +1 |
| 命中模拟器包名/文件探针 | +3 |
| 传感器数 ≤7、缺相机/闪光灯/蓝牙/光感/GPS 等 | +1 / 项 |
| `intel`/`amd` CPU / 双 ABI (`x86`+`arm`) / 已知 pipe / `cgroup` 可疑 | +1 / 项 |
| **`ro.hardware` 指纹命中（MUMU/夜神/雷电等）** | **+100（等效确定）** |

- `hardwareHit != ""` 时 `isEmulator()` 直接返回 `true`，不再看分数。
- 否则比较 `value >= threshold`，默认 `threshold = 4`。建议风控场景取 4，一般场景 6–8，强风控可配合 `hardwareHit` 双重判决。

性能：首次检测走 IO 线程（`getInstalledPackages` 批量 + `cat /proc/*` 等），结果缓存 5 分钟；重复调用命中内存缓存 <1ms。包名扫描亦 5 分钟 TTL。`clearSimulatorCache()` 与 `addCustomEmulatorPackages()` 会原子清缓存。

---

## 指纹库外置

- **内置**：`android/src/main/kotlin/.../simulator/Tools.java` 的 `PKG_NAMES` 等常量。
- **打包资产**：`android/src/main/assets/emulator_config.json`（随 AAR 分发，随版本灰度）。
- **运行时**：`addCustomEmulatorPackages(List<String>)`（`/` 开头的视为文件路径，其余视为包名）。

三层合并取并集；业务无需发版即可追加新模拟器。可通过替换打包时的 `emulator_config.json` 实现「热指纹随版本灰度」。

---

## 权限与隐私

插件 `AndroidManifest.xml` 仅声明：

```xml
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" android:maxSdkVersion="28" />
```

- `ACCESS_NETWORK_STATE`：`isProxy`/`isOpenVPN` 需读取网络能力；无此权限在部分 ROM 上会抛 `SecurityException`，插件内部已 catch 降级为 `false`。
- `READ_PHONE_STATE`：仅为 `deviceIMEINumber` 在 Android 9 及以下读取 IMEI 所需；`android:maxSdkVersion="28"` 避免 Android 10+ 被 Play 要求敏感权限说明。Android 10+ IMEI 本就不可得，插件会优先走 Widevine `MediaDrm` 设备 ID，拿不到则返回 `null`（Dart 侧透传 `PlatformException(UNAVAILABLE)` 转 `null`）。
- `QUERY_ALL_PACKAGES`：**不**由插件声明。`Tools.getInstalledSimulatorPackages()` 先尝试 `getInstalledPackages(0)` 批量，Android 11+ 受包可见性限制时自动回退逐包 `getPackageInfo`；宿主如需高命中可自行在 `AndroidManifest.xml` 加 `QUERY_ALL_PACKAGES`（Play 审核需合规理由）。
- `GET_TASKS`：已移除（API 21 废弃）。
- 合规：请勿在未告知用户的情况下采集 `deviceIMEINumber`；涉及 GDPR/《个人信息保护法》时需先取得用户同意并在隐私政策中披露。

---

## 各方法的局限性

- **模拟器检测**：计分模型对云手机/加壳模拟器可能漏检；入门级真机（无闪光灯/传感器少）可能误加分，靠阈值与 `hardwareHit` 双重判决缓解。
- **Root**：只能证明「存在 su/管理 App/可执行 su」，不能证明「当前 App 已获 root 权限」；带 Magisk Hide 的设备可能绕过。
- **代理/VPN**：系统代理与 VPN 检测依赖系统 API，应用内自建 SOCKS/分应用代理可能检测不到。
- **签名**：读取的是**本应用**签名，不是任意包；`SHA-256` 推荐用于 Play 签名校验。

## Mock / 测试

Dart 侧走 `AndroidWorkPlatform.instance`，测试中注入任意实现或用 `MethodChannel` mock：

```dart
setUp(() {
  AndroidWorkPlatform.instance = MyFakePlatform();
});
// 或
TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger
    .setMockMethodCallHandler(
  const MethodChannel('flutter_native_android_work'), (call) async => ...);
```

插件自带 `test/android_work_test.dart`（11 用例，覆盖计分阈值、`hardwareHit` 短路、异常回退、类型过滤等），执行 `flutter test`。

## 兼容性

- `minSdk 21`、`compileSdk 35`、`AGP 8.1.4`、`Kotlin 1.9.22`、`Java 11`。
- Dart `>=3.4.4`，Flutter `>=3.22.0`。

## 致谢

原 `PackInfo.java` / `Https` 全局信任证书等历史实现已清理；本版本由 T0–T4 四轮加固（安全、稳定、性能、工程化）重构。

## License

见 [LICENSE](LICENSE)。
