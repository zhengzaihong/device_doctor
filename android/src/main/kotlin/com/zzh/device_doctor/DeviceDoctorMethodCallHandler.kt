package com.zzh.device_doctor

import android.os.Build
import android.util.Log
import com.zzh.device_doctor.simulator.EmulatorCheckUtil
import com.zzh.device_doctor.simulator.Tools
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

class DeviceDoctorMethodCallHandler(private val plugin: DeviceDoctor) : MethodCallHandler {

    private var channelNew: MethodChannel? = null
    private var channelLegacy: MethodChannel? = null
    private var mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private fun ensureScope() {
        val job = mainScope.coroutineContext[kotlinx.coroutines.Job]
        if (job == null || job.isCancelled) mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    open fun startListening(messenger: BinaryMessenger?) {
        ensureScope()
        messenger?.let {
            if (channelNew != null) stopListeningInternal()
            channelNew = MethodChannel(it, methodChannelName).also { c -> c.setMethodCallHandler(this) }
            channelLegacy = MethodChannel(it, methodChannelNameLegacy).also { c -> c.setMethodCallHandler(this) }
            Log.d(TAG, "DeviceDoctor channels ready: $methodChannelName + $methodChannelNameLegacy")
        }
    }

    private fun stopListeningInternal() {
        channelNew?.setMethodCallHandler(null); channelNew = null
        channelLegacy?.setMethodCallHandler(null); channelLegacy = null
    }

    open fun stopListening() {
        mainScope.cancel()
        mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        stopListeningInternal()
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "getSimulatorInfo" -> {
                ensureScope(); mainScope.launch {
                    try { result.success(withContext(Dispatchers.IO) { plugin.getSimulatorInfo() }) }
                    catch (e: Exception) { Log.w(TAG, "getSimulatorInfo", e); result.error("ERROR", e.message, null) }
                }
            }
            "isSimulator" -> {
                ensureScope(); mainScope.launch {
                    try {
                        val info = withContext(Dispatchers.IO) { EmulatorCheckUtil.getSingleInstance().getEmulatorInfoSync(plugin.requireContext()) }
                        result.success(info)
                    } catch (e: Exception) { Log.w(TAG, "isSimulator", e); result.error("ERROR", e.message, null) }
                }
            }
            "clearSimulatorCache" -> try {
                EmulatorCheckUtil.getSingleInstance().clearCache(); Tools.clearCache(); EmulatorCheckUtil.clearCpuCache(); result.success(true)
            } catch (e: Exception) { Log.w(TAG, "clearSimulatorCache", e); result.error("ERROR", e.message, null) }
            "customEmulatorPackages" -> try {
                @Suppress("UNCHECKED_CAST") val raw = call.argument<Any>("packages")
                val pkgs: List<String> = when (raw) { is List<*> -> raw.mapNotNull { it?.toString()?.trim()?.takeIf { s -> s.isNotEmpty() } }; else -> emptyList() }
                Tools.addCustomPackages(pkgs); EmulatorCheckUtil.getSingleInstance().clearCache(); result.success(true)
            } catch (e: Exception) { Log.w(TAG, "customEmulatorPackages", e); result.error("ERROR", e.message, null) }
            "isProxy" -> { ensureScope(); mainScope.launch {
                try { result.success(withContext(Dispatchers.IO) { plugin.isProxy() }) }
                catch (e: Exception) { Log.w(TAG, "isProxy", e); result.error("ERROR", e.message, null) }
            } }
            "isOpenVPN" -> { ensureScope(); mainScope.launch {
                try { result.success(withContext(Dispatchers.IO) { plugin.isOpenVPN() }) }
                catch (e: Exception) { Log.w(TAG, "isOpenVPN", e); result.error("ERROR", e.message, null) }
            } }
            "isRootEnv" -> { ensureScope(); mainScope.launch {
                try { result.success(withContext(Dispatchers.IO) { plugin.isRoot() }) }
                catch (e: Exception) { Log.w(TAG, "isRoot", e); result.error("ERROR", e.message, null) }
            } }
            "getSignature" -> {
                val type = call.argument<String?>("type") ?: "md5"
                ensureScope(); mainScope.launch {
                    try { result.success(withContext(Dispatchers.IO) { plugin.getSignature(type.uppercase()) }) }
                    catch (e: Exception) { Log.w(TAG, "getSignature", e); result.error("ERROR", e.message, null) }
                }
            }
            "getPlatformVersion" -> result.success("Android " + Build.VERSION.RELEASE)
            "getIMEINumber" -> try {
                val v: String? = plugin.getIMEINo()
                if (v.isNullOrEmpty()) result.error("UNAVAILABLE", "IMEI/DeviceID unavailable", null) else result.success(v)
            } catch (e: SecurityException) { result.error("PERMISSION_DENIED", e.message, null) }
              catch (e: Exception) { Log.w(TAG, "getIMEINumber", e); result.error("ERROR", e.message, null) }
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
        const val methodChannelName = "device_doctor"
        const val methodChannelNameLegacy = "flutter_native_android_work"
        const val TAG = "DeviceDoctor"
    }
}

/** 兼容别名：老 Handler 名保留 */
@Deprecated("Use DeviceDoctorMethodCallHandler")
typealias MethodCallHandlerImpl = DeviceDoctorMethodCallHandler
