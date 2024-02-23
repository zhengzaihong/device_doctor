import 'package:flutter/services.dart';

///通道名称
const String _channelName = 'flutter_native_intent';


class AndroidWork {
  const AndroidWork()  :
        _channel = const MethodChannel(_channelName);

  final MethodChannel _channel;
}
