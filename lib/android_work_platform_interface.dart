import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'android_work_method_channel.dart';

abstract class AndroidWorkPlatform extends PlatformInterface {
  /// Constructs a AndroidWorkPlatform.
  AndroidWorkPlatform() : super(token: _token);

  static final Object _token = Object();

  static AndroidWorkPlatform _instance = MethodChannelAndroidWork();

  /// The default instance of [AndroidWorkPlatform] to use.
  ///
  /// Defaults to [MethodChannelAndroidWork].
  static AndroidWorkPlatform get instance => _instance;

  /// Platform-specific implementations should set this with their own
  /// platform-specific class that extends [AndroidWorkPlatform] when
  /// they register themselves.
  static set instance(AndroidWorkPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<String?> getPlatformVersion() {
    throw UnimplementedError('platformVersion() has not been implemented.');
  }
}
