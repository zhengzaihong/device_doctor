import Flutter
import UIKit

/// iOS 侧设备信息实现（模拟器/Root/代理等深度检测仅 Android 提供，其余回
/// `FlutterMethodNotImplemented`，由 Dart 层启发式兜底）。
///
/// 同时注册新通道 `device_doctor` 与旧通道 `flutter_native_android_work`，
/// 保证升级宿主与未迁移宿主都能收到响应。任何未覆盖方法必须显式回
/// `FlutterMethodNotImplemented`，否则 Dart Future 将永久挂起。
@objc(DeviceDoctorPlugin)
public class DeviceDoctorPlugin: NSObject, FlutterPlugin {

  private static var didRegister = false
  private static let registerLock = NSLock()

  public static func register(with registrar: FlutterPluginRegistrar) {
    registerLock.lock()
    defer { registerLock.unlock() }
    // AndroidWorkPlugin(兼容别名) 与 DeviceDoctorPlugin 可能先后被注册，去重防双通道 handler 冲突。
    guard !didRegister else { return }
    didRegister = true
    let instance = DeviceDoctorPlugin()
    for name in [DeviceDoctorPlugin.channelName, DeviceDoctorPlugin.legacyChannelName] {
      let channel = FlutterMethodChannel(name: name, binaryMessenger: registrar.messenger())
      registrar.addMethodCallDelegate(instance, channel: channel)
    }
  }

  static let channelName = "device_doctor"
  static let legacyChannelName = "flutter_native_android_work"

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
    switch call.method {
    case "getPlatformVersion":
      result("iOS " + UIDevice.current.systemVersion)
    case "getIMEINumber":
      result(UIDevice.current.identifierForVendor?.uuidString)
    case "getModel":
      result(UIDevice.current.modelName)
    case "getAPILevel":
      result(UIDevice.current.systemVersion)
    case "getManufacturer":
      result("Apple")
    case "getDevice":
      result(UIDevice.current.name)
    case "getProduct":
      result(UIDevice.current.model)
    case "getCPUType":
      result(UIDevice.current.getCPUName())
    case "getHardware":
      result(UIDevice.current.systemName)
    default:
      // 让 Dart 层 MissingPluginException 分支接管（启发式回退）。
      result(FlutterMethodNotImplemented)
    }
  }
}
