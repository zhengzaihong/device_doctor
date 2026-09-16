Pod::Spec.new do |s|
  s.name             = 'device_doctor'
  s.version          = '0.3.0'
  s.summary          = 'Device environment inspection plugin (emulator/root/proxy/VPN/signature). Android deep detection, iOS device-info subset.'
  s.description      = <<-DESC
跨平台设备环境检测 Flutter 插件：模拟器/虚拟机、Root、代理/VPN、APK 签名与设备信息。
Android 侧为原生深度检测；iOS 侧提供设备信息子集，其余方法回 FlutterMethodNotImplemented 由 Dart 启发式兜底。
                       DESC
  s.homepage         = 'https://github.com/zhengzaihong/device_doctor'
  s.license          = { :file => '../LICENSE' }
  s.author           = { 'zhengzaihong' => '1096877329@qq.com' }
  s.source           = { :path => '.' }
  s.source_files = 'Classes/**/*'
  s.dependency 'Flutter'
  s.platform = :ios, '12.0'

  # Flutter.framework does not contain a i386 slice.
  s.pod_target_xcconfig = { 'DEFINES_MODULE' => 'YES', 'EXCLUDED_ARCHS[sdk=iphonesimulator*]' => 'i386' }
  s.swift_version = '5.0'
end
