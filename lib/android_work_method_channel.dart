import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'android_work_platform_interface.dart';

const String _kChannelName = 'flutter_native_android_work';

class MethodChannelAndroidWork extends AndroidWorkPlatform {
  @visibleForTesting
  final MethodChannel methodChannel = const MethodChannel(_kChannelName);

  @override
  Future<String> getPlatformVersion() async {
    final v = await methodChannel.invokeMethod<String>('getPlatformVersion');
    return v ?? 'Unknown';
  }

  @override
  Future<int> getApiLevel() async {
    final v = await methodChannel.invokeMethod<int>('getAPILevel');
    return v ?? 0;
  }

  @override
  Future<String> getModel() async {
    final v = await methodChannel.invokeMethod<String>('getModel');
    return v ?? '';
  }

  @override
  Future<String> getManufacturer() async {
    final v = await methodChannel.invokeMethod<String>('getManufacturer');
    return v ?? '';
  }

  @override
  Future<String> getDevice() async {
    final v = await methodChannel.invokeMethod<String>('getDevice');
    return v ?? '';
  }

  @override
  Future<String> getProduct() async {
    final v = await methodChannel.invokeMethod<String>('getProduct');
    return v ?? '';
  }

  @override
  Future<String> getCpuType() async {
    final v = await methodChannel.invokeMethod<String>('getCPUType');
    return v ?? '';
  }

  @override
  Future<String> getHardware() async {
    final v = await methodChannel.invokeMethod<String>('getHardware');
    return v ?? '';
  }

  @override
  Future<String?> getImeiNumber() async {
    try {
      return await methodChannel.invokeMethod<String>('getIMEINumber');
    } on PlatformException {
      return null;
    }
  }

  @override
  Future<Map<String, dynamic>> isSimulator() async {
    final raw = await methodChannel.invokeMethod<dynamic>('isSimulator');
    if (raw is Map) return Map<String, dynamic>.from(raw);
    return <String, dynamic>{'value': 0};
  }

  @override
  Future<List<String>> getSimulatorInfo() async {
    final raw = await methodChannel.invokeMethod<dynamic>('getSimulatorInfo');
    if (raw is List) return raw.whereType<String>().toList();
    return <String>[];
  }

  @override
  Future<bool> isProxy() async {
    final v = await methodChannel.invokeMethod<bool>('isProxy');
    return v ?? false;
  }

  @override
  Future<bool> isOpenVPN() async {
    final v = await methodChannel.invokeMethod<bool>('isOpenVPN');
    return v ?? false;
  }

  @override
  Future<bool> isRootEnv() async {
    final v = await methodChannel.invokeMethod<bool>('isRootEnv');
    return v ?? false;
  }

  @override
  Future<List<String>> getSignatures(String type) async {
    final normalized = type.trim().toUpperCase();
    final raw =
        await methodChannel.invokeMethod<dynamic>('getSignature', {'type': normalized});
    if (raw is List) return raw.whereType<String>().toList();
    return <String>[];
  }

  @override
  Future<void> clearSimulatorCache() async {
    await methodChannel.invokeMethod<void>('clearSimulatorCache');
  }

  @override
  Future<void> addCustomEmulatorPackages(List<String> packages) async {
    await methodChannel.invokeMethod<void>('customEmulatorPackages', {
      'packages': packages,
    });
  }
}
