package com.zzh.device_doctor

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding

/**
* create_user: zhengzaihong
* email:1096877329@qq.com
* describe: 设备体检插件入口（原 android_work，更名 device_doctor）
*/
open class DeviceDoctorPlugin : FlutterPlugin, ActivityAware {

  private var workPlugin: DeviceDoctor = DeviceDoctor(null, null)
  private var impl: DeviceDoctorMethodCallHandler = DeviceDoctorMethodCallHandler(workPlugin)

  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    workPlugin.setApplicationContext(binding.applicationContext)
    workPlugin.setActivity(null)
    impl.startListening(binding.binaryMessenger)
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    workPlugin.setApplicationContext(null)
    workPlugin.setActivity(null)
    impl.stopListening()
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    workPlugin.setActivity(binding.activity)
  }

  override fun onDetachedFromActivity() {
    workPlugin.setActivity(null)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    onDetachedFromActivity()
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    onAttachedToActivity(binding)
  }
}

/**
* 兼容别名：宿主如曾直接引用 `com.zzh.android_work.AndroidWorkPlugin`，
* 该类继续注册同一处理逻辑。新代码请使用 [DeviceDoctorPlugin]。
*/
@Deprecated("使用 DeviceDoctorPlugin")
class AndroidWorkPlugin : DeviceDoctorPlugin()
