import Flutter
import UIKit

/// 兼容类：历史 iOS 注册类名 `AndroidWorkPlugin`（0.2.x 及更早宿主的
/// GeneratedPluginRegistrant 仍按名注册）。行为完全等同 [DeviceDoctorPlugin]。
@available(*, deprecated, renamed: "DeviceDoctorPlugin")
@objc(AndroidWorkPlugin)
public class AndroidWorkPlugin: DeviceDoctorPlugin {

  public override static func register(with registrar: FlutterPluginRegistrar) {
    DeviceDoctorPlugin.register(with: registrar)
  }
}
