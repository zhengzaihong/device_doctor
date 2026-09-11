import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'android_work_method_channel.dart';

/// Platform interface for `android_work`.
///
/// All platform implementations must extend this class and override every
/// method that the plugin exposes. The default instance is
/// [MethodChannelAndroidWork] (Android via `flutter_native_android_work`).
abstract class AndroidWorkPlatform extends PlatformInterface {
  AndroidWorkPlatform() : super(token: _token);

  static final Object _token = Object();

  static AndroidWorkPlatform _instance = MethodChannelAndroidWork();

  /// The current platform implementation.
  static AndroidWorkPlatform get instance => _instance;

  /// Allows platform-specific implementations to override the default instance.
  static set instance(AndroidWorkPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  // ── Device info ────────────────────────────────────────────────────────

  /// e.g. "Android 14". Never null.
  Future<String> getPlatformVersion() {
    throw UnimplementedError('getPlatformVersion() has not been implemented.');
  }

  /// `Build.VERSION.SDK_INT`, e.g. 34.
  Future<int> getApiLevel() {
    throw UnimplementedError('getApiLevel() has not been implemented.');
  }

  /// `Build.MODEL`, e.g. "Pixel 8".
  Future<String> getModel() {
    throw UnimplementedError('getModel() has not been implemented.');
  }

  /// `Build.MANUFACTURER`, e.g. "Google".
  Future<String> getManufacturer() {
    throw UnimplementedError('getManufacturer() has not been implemented.');
  }

  /// `Build.DEVICE`, e.g. "husky".
  Future<String> getDevice() {
    throw UnimplementedError('getDevice() has not been implemented.');
  }

  /// `Build.PRODUCT`, e.g. "husky".
  Future<String> getProduct() {
    throw UnimplementedError('getProduct() has not been implemented.');
  }

  /// `Build.CPU_ABI` / `SUPPORTED_ABIS[0]`.
  Future<String> getCpuType() {
    throw UnimplementedError('getCpuType() has not been implemented.');
  }

  /// `Build.HARDWARE`, e.g. "husky".
  Future<String> getHardware() {
    throw UnimplementedError('getHardware() has not been implemented.');
  }

  /// Device identifier. Android 10+ restricted — see [AndroidWork.deviceIMEINumber].
  Future<String?> getImeiNumber() {
    throw UnimplementedError('getImeiNumber() has not been implemented.');
  }

  // ── Emulator / environment ────────────────────────────────────────────

  /// Full emulator scoring map.
  ///
  /// Keys: `hardware`, `hardwareHit`, `flavor`, `model`, `manufacturer`,
  /// `board`, `platform`, `baseBand`, `sensorNumber`, `supportCamera`,
  /// `supportCameraFlash`, `supportBluetooth`, `hasLightSensor`,
  /// `cgroupResult`, `value` (int suspicious score).
  ///
  /// Raw scores are **not** a boolean — interpret with a threshold (default 4)
  /// or check `hardwareHit.isNotEmpty` for a strong hit. The native result
  /// is cached for 5 min; call [clearSimulatorCache] to force a fresh scan.
  Future<Map<String, dynamic>> isSimulator() {
    throw UnimplementedError('isSimulator() has not been implemented.');
  }

  /// Brand tag for the detected emulator, e.g. "mumu", "蓝叠", "雷电", or [].
  Future<List<String>> getSimulatorInfo() {
    throw UnimplementedError('getSimulatorInfo() has not been implemented.');
  }

  /// Whether a system / app HTTP proxy is active (JVM props + `ConnectivityManager.defaultProxy`).
  Future<bool> isProxy() {
    throw UnimplementedError('isProxy() has not been implemented.');
  }

  /// Whether a VPN transport is active (`NetworkCapabilities.TRANSPORT_VPN`).
  /// Requires `ACCESS_NETWORK_STATE` (declared by the plugin manifest).
  Future<bool> isOpenVPN() {
    throw UnimplementedError('isOpenVPN() has not been implemented.');
  }

  /// Whether the device appears rooted (su binaries, root packages, `which su`, `test-keys`).
  Future<bool> isRootEnv() {
    throw UnimplementedError('isRootEnv() has not been implemented.');
  }

  /// APK signing digests. `type` one of `MD5`, `SHA-1`, `SHA-256` (case-insensitive).
  /// Returns de-duplicated lower-case hex strings; unknown type returns [].
  Future<List<String>> getSignatures(String type) {
    throw UnimplementedError('getSignatures() has not been implemented.');
  }

  /// Clears native caches: emulator score (5 min TTL), package hit cache, and CPU-info cache.
  Future<void> clearSimulatorCache() {
    throw UnimplementedError('clearSimulatorCache() has not been implemented.');
  }

  /// Adds custom package/file/path fingerprints at runtime. They are merged
  /// with the built-in list and the bundled `emulator_config.json`.
  Future<void> addCustomEmulatorPackages(List<String> packages) {
    throw UnimplementedError('addCustomEmulatorPackages() has not been implemented.');
  }
}
