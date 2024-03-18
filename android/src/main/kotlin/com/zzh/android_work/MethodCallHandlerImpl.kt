package com.zzh.android_work
import android.Manifest
import android.os.Build
import android.util.Log
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

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

        when (call.method) {

            "getSimulatorInfo" -> {
               result.success(plugin.getSimulatorInfo())
            }

            "isSimulator" -> {
                plugin.isSimulator {
                    result.success(it)
                }
            }

            "isProxy" -> {
                GlobalScope.launch {
                    val isProxy = async(Dispatchers.IO) {
                        plugin.isProxy()
                    }.await()
                    result.success(isProxy)
                }
            }
            "isOpenVPN" -> {
                GlobalScope.launch {
                    val isOpenVPN = async(Dispatchers.IO) {
                        plugin.isOpenVPN()
                    }.await()
                    result.success(isOpenVPN)
                }
            }



            "getPlatformVersion" -> {
                result.success("Android " + Build.VERSION.RELEASE)
            }
            "getIMEINumber" -> {
                val imeiNo: String? = plugin.getIMEINo()
                if (imeiNo != null && imeiNo == Manifest.permission.READ_PHONE_STATE) {
                    result.error(
                        Manifest.permission.READ_PHONE_STATE,
                        "Permission is not granted!",
                        null
                    )
                } else if (!imeiNo.isNullOrEmpty()) {
                    result.success(imeiNo)
                }
            }
            "getAPILevel" -> {
                result.success(Build.VERSION.SDK_INT)
            }
            "getModel" -> {
                result.success(Build.MODEL)
            }
            "getManufacturer" -> {
                result.success(Build.MANUFACTURER)
            }
            "getDevice" -> {
                result.success(Build.DEVICE)
            }
            "getProduct" -> {
                result.success(Build.PRODUCT)
            }
            "getCPUType" -> {
                result.success(Build.CPU_ABI)
            }
            "getHardware" -> {
                result.success(Build.HARDWARE)
            }
            else -> {
                result.notImplemented()
            }

        }

    }

    companion object {
        const val methodChannelName = "flutter_native_android_work"

        const val TAG = "AndroidWorkTag"
    }
}