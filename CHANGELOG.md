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
