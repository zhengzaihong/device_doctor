package com.zzh.android_work
import android.util.Log
import com.zzh.android_work.utils.DeviceCheckUtils
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler

/**
* create_user: zhengzaihong
* email:1096877329@qq.com
* create_date: 2023/3/31
* create_time: 13:51
* describe: 原生通道消息处理
*/
class MethodCallHandlerImpl(private val plugin: AndroidWork) : MethodCallHandler {

    private var methodChannel: MethodChannel? = null

   open fun startListening(messenger: BinaryMessenger?) {

        messenger?.let {
            if (methodChannel != null) {
                Log.wtf(TAG, "销毁上一次数据通道，马上建立新数据通道")
                stopListening()
            }
            methodChannel = MethodChannel(it, methodChannelName)
            methodChannel?.setMethodCallHandler(this)
            Log.wtf(TAG, "新数据通道创建完毕！")
        }
    }


   open fun stopListening() {
        if (methodChannel == null) {
            return
        }
        methodChannel?.setMethodCallHandler(null)
        methodChannel = null
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {

        call.run {

            when {
                "getRunningAppProcesses".equals(call.method, ignoreCase = true) -> {
                    plugin?.let {
                       result.success( it.getRunningAppProcesses())
                    }
                }
                "checkDeviceIsEmulator".equals(call.method, ignoreCase = true) -> {
                    plugin.let {
                        result.success(it.checkDeviceIsEmulator())
                    }
                }
                else -> {
                    result.notImplemented()
                }
            }
        }
    }

    companion object {
        const val methodChannelName = "flutter_native_android_work"

        const val TAG = "AndroidWorkTag"
    }
}