import 'package:flutter/services.dart';

///通道名称
const String _channelName = 'flutter_native_android_work';


class AndroidWork {
  const AndroidWork()  :
        _channel = const MethodChannel(_channelName);

  final MethodChannel _channel;



  ///获取Android运行的进程
  Future<dynamic> getRunningAppProcesses() async => await _channel.invokeMethod('getRunningAppProcesses');
}
