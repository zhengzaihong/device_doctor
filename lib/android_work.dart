import 'package:flutter/services.dart';

///通道名称
const String _channelName = 'flutter_native_android_work';

///
/// create_user: zhengzaihong
/// email:1096877329@qq.com
/// create_date: 2024/2/26
/// create_time: 16:37
/// describe: 此库只实现Android 原生相关任务或功能
///
class AndroidWork {
  const AndroidWork()  :_channel = const MethodChannel(_channelName);

  final MethodChannel _channel;

  ///获取Android运行的进程
  Future<dynamic> getRunningAppProcesses() async => await _channel.invokeMethod('getRunningAppProcesses');

  ///检查当前设备是否是模拟器 返回值大于3 基本就可以断定为 模拟器了
  Future<dynamic> checkDeviceIsEmulator() async => await _channel.invokeMethod('checkDeviceIsEmulator');
}
