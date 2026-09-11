package com.zzh.android_work
import android.os.Build
import android.util.Log
import com.zzh.android_work.simulator.EmulatorCheckUtil
import com.zzh.android_work.simulator.Tools
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
* create_user: zhengzaihong
* email:1096877329@qq.com
* create_date: 2023/3/31
* create_time: 13:51
* describe: 原生通道消息处理
*/
class MethodCallHandlerImpl(private val plugin: AndroidWork) : MethodCallHandler {

    private var methodChannel: MethodChannel? = null

    private var mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun ensureScope() {
        val job = mainScope.coroutineContext[ kotlinx.coroutines.Job ]
        if (job == null || job.isCancelled) {
            mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        }
    }

   open fun startListening(messenger: BinaryMessenger?) {
        ensureScope()
        messenger?.let {
            if (methodChannel != null) {
                Log.w(TAG, "销毁上一次数据通道，马上建立新数据通道")
                stopListeningInternal()
            }
            methodChannel = MethodChannel(it, methodChannelName)
            methodChannel?.setMethodCallHandler(this)
            Log.d(TAG, "新数据通道创建完毕！")
        }
    }

    private fun stopListeningInternal() {
        if (methodChannel == null) return
        methodChannel?.setMethodCallHandler(null)
        methodChannel = null
    }

   open fun stopListening() {
        mainScope.cancel()
        mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        stopListeningInternal()
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getSimulatorInfo" -> {
                ensureScope()
                mainScope.launch {
                    try {
                        val info = withContext(Dispatchers.IO) { plugin.getSimulatorInfo() }
                        result.success(info)
                    } catch (e: Exception) {
                        Log.w(TAG, "getSimulatorInfo error", e)
                        result.error("ERROR", e.message, null)
                    }
                }
            }
            "isSimulator" -> {
                ensureScope()
                mainScope.launch {
                    try {
                        val info = withContext(Dispatchers.IO) {
                            EmulatorCheckUtil.getSingleInstance().getEmulatorInfoSync(plugin.requireContext())
                        }
                        result.success(info)
                    } catch (e: Exception) {
                        Log.w(TAG, "isSimulator error", e)
                        result.error("ERROR", e.message, null)
                    }
                }
            }
            "clearSimulatorCache" -> {
                try {
                    EmulatorCheckUtil.getSingleInstance().clearCache()
                    Tools.clearCache()
                    EmulatorCheckUtil.clearCpuCache()
                    result.success(true)
                } catch (e: Exception) {
                    Log.w(TAG, "clearSimulatorCache error", e)
                    result.error("ERROR", e.message, null)
                }
            }
            "customEmulatorPackages" -> {
                try {
                    @Suppress("UNCHECKED_CAST")
                    val raw = call.argument<Any>("packages")
                    val pkgs: List<String> = when (raw) {
                        is List<*> -> raw.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }
                        else -> emptyList()
                    }
                    Tools.addCustomPackages(pkgs)
                    EmulatorCheckUtil.getSingleInstance().clearCache()
                    result.success(true)
                } catch (e: Exception) {
                    Log.w(TAG, "customEmulatorPackages error", e)
                    result.error("ERROR", e.message, null)
                }
            }
            "isProxy" -> {
                ensureScope()
                mainScope.launch {
                    try {
                        val isProxy = withContext(Dispatchers.IO) { plugin.isProxy() }
                        result.success(isProxy)
                    } catch (e: Exception) {
                        Log.w(TAG, "isProxy error", e)
                        result.error("ERROR", e.message, null)
                    }
                }
            }
            "isOpenVPN" -> {
                ensureScope()
                mainScope.launch {
                    try {
                        val isOpenVPN = withContext(Dispatchers.IO) { plugin.isOpenVPN() }
                        result.success(isOpenVPN)
                    } catch (e: Exception) {
                        Log.w(TAG, "isOpenVPN error", e)
                        result.error("ERROR", e.message, null)
                    }
                }
            }
            "isRootEnv" -> {
                ensureScope()
                mainScope.launch {
                    try {
                        val root = withContext(Dispatchers.IO) { plugin.isRoot() }
                        result.success(root)
                    } catch (e: Exception) {
                        Log.w(TAG, "isRoot error", e)
                        result.error("ERROR", e.message, null)
                    }
                }
            }
            "getSignature" -> {
                val type = call.argument<String?>("type") ?: "md5"
                ensureScope()
                mainScope.launch {
                    try {
                        val listSignatures = withContext(Dispatchers.IO) { plugin.getSignature(type.uppercase()) }
                        result.success(listSignatures)
                    } catch (e: Exception) {
                        Log.w(TAG, "getSignature error", e)
                        result.error("ERROR", e.message, null)
                    }
                }
            }
            "getPlatformVersion" -> {
                result.success("Android " + Build.VERSION.RELEASE)
            }
            "getIMEINumber" -> {
                try {
                    val imeiNo: String? = plugin.getIMEINo()
                    if (imeiNo.isNullOrEmpty()) {
                        result.error("UNAVAILABLE", "IMEI/DeviceID unavailable (permission denied or Android 10+ restricted)", null)
                    } else {
                        result.success(imeiNo)
                    }
                } catch (e: SecurityException) {
                    result.error("PERMISSION_DENIED", e.message ?: "READ_PHONE_STATE required", null)
                } catch (e: Exception) {
                    Log.w(TAG, "getIMEINumber error", e)
                    result.error("ERROR", e.message, null)
                }
            }
            "getAPILevel" -> result.success(Build.VERSION.SDK_INT)
            "getModel" -> result.success(Build.MODEL)
            "getManufacturer" -> result.success(Build.MANUFACTURER)
            "getDevice" -> result.success(Build.DEVICE)
            "getProduct" -> result.success(Build.PRODUCT)
            "getCPUType" -> result.success(Build.CPU_ABI)
            "getHardware" -> result.success(Build.HARDWARE)
            else -> result.notImplemented()
        }
    }

    companion object {
        const val methodChannelName = "flutter_native_android_work"

        const val TAG = "AndroidWorkTag"
    }
}
