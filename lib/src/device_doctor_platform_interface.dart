import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'device_doctor_method_channel.dart';

/// Platform interface for `device_doctor`.
///
/// All platform implementations must extend this class and override the
/// methods that the plugin exposes. The default instance is
/// [MethodChannelDeviceDoctor] (Android 深度检测，其他平台 Dart 回退)。
abstract class DeviceDoctorPlatform extends PlatformInterface {
  DeviceDoctorPlatform() : super(token: _token);
  static final Object _token = Object();
  static DeviceDoctorPlatform _instance = MethodChannelDeviceDoctor();
  static DeviceDoctorPlatform get instance => _instance;
  static set instance(DeviceDoctorPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  // ── Device info ────────────────────────────────────────────────────────

  Future<String> getPlatformVersion() {
    throw UnimplementedError('getPlatformVersion() has not been implemented.');
  }

  Future<int> getApiLevel() {
    throw UnimplementedError('getApiLevel() has not been implemented.');
  }

  Future<String> getModel() {
    throw UnimplementedError('getModel() has not been implemented.');
  }

  Future<String> getManufacturer() {
    throw UnimplementedError('getManufacturer() has not been implemented.');
  }

  Future<String> getDevice() {
    throw UnimplementedError('getDevice() has not been implemented.');
  }

  Future<String> getProduct() {
    throw UnimplementedError('getProduct() has not been implemented.');
  }

  Future<String> getCpuType() {
    throw UnimplementedError('getCpuType() has not been implemented.');
  }

  Future<String> getHardware() {
    throw UnimplementedError('getHardware() has not been implemented.');
  }

  Future<String?> getImeiNumber() {
    throw UnimplementedError('getImeiNumber() has not been implemented.');
  }

  // ── Emulator / environment ────────────────────────────────────────────

  Future<Map<String, dynamic>> isSimulator() {
    throw UnimplementedError('isSimulator() has not been implemented.');
  }

  Future<List<String>> getSimulatorInfo() {
    throw UnimplementedError('getSimulatorInfo() has not been implemented.');
  }

  Future<bool> isProxy() {
    throw UnimplementedError('isProxy() has not been implemented.');
  }

  Future<bool> isOpenVPN() {
    throw UnimplementedError('isOpenVPN() has not been implemented.');
  }

  Future<bool> isRootEnv() {
    throw UnimplementedError('isRootEnv() has not been implemented.');
  }

  Future<List<String>> getSignatures(String type) {
    throw UnimplementedError('getSignatures() has not been implemented.');
  }

  Future<void> clearSimulatorCache() {
    throw UnimplementedError('clearSimulatorCache() has not been implemented.');
  }

  Future<void> addCustomEmulatorPackages(List<String> packages) {
    throw UnimplementedError('addCustomEmulatorPackages() has not been implemented.');
  }
}

/// 兼容别名：老包名 `android_work` 的 Platform 名称保留指向新实现。
typedef AndroidWorkPlatform = DeviceDoctorPlatform;
