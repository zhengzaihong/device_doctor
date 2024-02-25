package com.zzh.android_work

import androidx.annotation.NonNull

import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result

/** AndroidWorkPlugin */
class AndroidWorkPlugin: FluFlutterPlugin, ActivityAware {

  private var workPlugin: AndroidWork = AndroidWork( /*activity=*/null,  /*applicationContext=*/null)
  private var impl: MethodCallHandlerImpl = MethodCallHandlerImpl(launcher)


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
