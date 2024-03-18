import 'package:flutter/services.dart';

///通道名称
const dynamic _channelName = 'flutter_native_android_work';

///
/// create_user: zhengzaihong
/// email:1096877329@qq.com
/// create_date: 2024/2/26
/// create_time: 16:37
/// describe: 此库主线只实现Android 原生相关任务或功能
/// IOS涉及可能会较少，此库主要针对android
///
class AndroidWork {
  const AndroidWork()  :_channel = const MethodChannel(_channelName);

  final MethodChannel _channel;

  /// 判断是否为模拟器
  Future<dynamic> isSimulator() async => await _channel.invokeMethod('isSimulator');

  /// 获取模拟器信息
  Future<dynamic> getSimulatorInfo() async => await _channel.invokeMethod('getSimulatorInfo');

  /// 判断是否为代理
  Future<dynamic> isProxy() async => await _channel.invokeMethod('isProxy');

  /// 判断是否开启VPN
  Future<dynamic> isOpenVPN() async => await _channel.invokeMethod('isOpenVPN');

  /// 获取Android版本
  Future<dynamic> get platformVersion async {
    return await _channel.invokeMethod('getPlatformVersion');
  }

  /// 获取设备唯一标识
   Future<dynamic> get deviceIMEINumber async {
    return await _channel.invokeMethod("getIMEINumber");
  }

  /// 获取设备API级
   Future<dynamic> get apiLevel async {
    return await _channel.invokeMethod("getAPILevel");
  }

  /// 获取设备型号
   Future<dynamic> get deviceModel async {
    return await _channel.invokeMethod("getModel");
  }

  /// 获取设备厂商
   Future<dynamic> get deviceManufacturer async {
    return await _channel.invokeMethod("getManufacturer");
  }

  /// 获取设备名称
   Future<dynamic> get deviceName async {
    return  await _channel.invokeMethod("getDevice");
  }

  /// 获取产品名称
   Future<dynamic> get productName async {
    return await _channel.invokeMethod("getProduct");
  }

  /// 获取CPU名称
   Future<dynamic> get cpuName async {
    return await _channel.invokeMethod("getCPUType");
  }

  /// 获取硬件
   Future<dynamic> get hardware async {
    return await _channel.invokeMethod("getHardware");
  }
}
