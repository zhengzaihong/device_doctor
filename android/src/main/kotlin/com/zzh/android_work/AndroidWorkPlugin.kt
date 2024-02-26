package com.zzh.android_work

import androidx.annotation.NonNull

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding

/**
* create_user: zhengzaihong
* email:1096877329@qq.com
* create_date: 2024/2/26
* create_time: 15:45
* describe: 初始化插件和通道
*/
class AndroidWorkPlugin: FlutterPlugin, ActivityAware {

  private var workPlugin: AndroidWork = AndroidWork( /*activity=*/null,  /*applicationContext=*/null)
  private var impl: MethodCallHandlerImpl = MethodCallHandlerImpl(workPlugin)

  override fun onAttachedToEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    workPlugin.setApplicationContext(binding.applicationContext)
    workPlugin.setActivity(null)
    impl.startListening(binding.binaryMessenger)

  }


  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
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
