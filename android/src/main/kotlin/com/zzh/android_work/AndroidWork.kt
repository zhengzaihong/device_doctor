package com.zzh.android_work

import android.app.Activity
import android.content.Context
import android.media.MediaDrm
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Proxy
import android.os.Build
import android.provider.Settings
import android.telephony.TelephonyManager
import android.text.TextUtils
import android.util.Log
import androidx.annotation.RequiresApi
import com.zzh.android_work.simulator.EmulatorCheckUtil
import com.zzh.android_work.simulator.Tools
import java.util.UUID
import java.io.File
import java.io.DataOutputStream



/**
 * create_user: zhengzaihong
 * email:1096877329@qq.com
 * create_date: 2023/3/31
 * create_time: 14:22
 * describe:
 */
class AndroidWork(private var activity: Activity?, private var applicationContext: Context?) {

    companion object {
        private const val TAG = "AndroidWork"
    }

    fun setActivity(activity: Activity?) {
        this.activity = activity
    }

    fun setApplicationContext(applicationContext: Context?) {
        this.applicationContext = applicationContext
    }

    private fun checkContext(): Boolean {
        if (applicationContext == null) {
            Log.wtf(TAG, "上下文未初始化，请先初始化")
            return false
        }
        return true
    }


    fun getSimulatorInfo(): MutableList<Any?>? {
        if (!checkContext()){
            return null
        }
        return Tools.getSimulatorInfo(applicationContext!!)
    }

    // 检查是否是模拟器 同步
    fun isSimulator(callback: (info: Any) -> Unit) {
        if (!checkContext()){
            return
        }
        EmulatorCheckUtil.getSingleInstance().readSysProperty(applicationContext!!) { emulatorInfo -> callback.invoke(emulatorInfo) }
    }

    suspend fun isRoot(): Boolean {
        if (!checkContext()){
            return false
        }
        var process:Process?  = null
        try {
            process = Runtime.getRuntime().exec("su")
            var os: DataOutputStream = DataOutputStream(process.getOutputStream())
            os.writeBytes("echo root\n")
            os.writeBytes("exit\n")
            os.flush()
            process?.waitFor()
            if (process?.exitValue() == 0) {
                return true
            }

            var file: File = File("/system/bin/su")
            if (file.exists()) {
                return true
            }
            var buildTags: String? = Build.TAGS
            if (buildTags != null && buildTags.contains("test-keys")) {
                return true
            }
        } catch (e: Exception) {
          return false
        } finally {
            process?.destroy()
        }
        return false
    }


    suspend fun isProxy(): Boolean {
        if (!checkContext()){
            return false
        }
        val IS_ICS_OR_LATER = Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH
        val proxyAddress: String
        val proxyPort: Int
        if (IS_ICS_OR_LATER) {
            val proxySettings =
                Settings.System.getString(applicationContext!!.contentResolver, "http_proxy")
            return !TextUtils.isEmpty(proxySettings)
        } else {
            proxyAddress = Proxy.getHost(applicationContext!!)
            proxyPort = Proxy.getPort(applicationContext!!)
        }
        return !TextUtils.isEmpty(proxyAddress) && proxyPort != -1
    }

    suspend fun isOpenVPN(): Boolean? {
        val connectivityManager =
            activity!!.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager?.activeNetwork
            val caps =
                connectivityManager?.getNetworkCapabilities(activeNetwork)
            return caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val networks = connectivityManager?.allNetworks
            networks?.let {
                for (i in it) {
                    val caps = connectivityManager.getNetworkCapabilities(i)
                    if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) {
                        return true
                    }
                }
            }

        }
        val networkInfo = connectivityManager.activeNetworkInfo
        if (networkInfo != null && networkInfo.isConnected) {
            return networkInfo.type == ConnectivityManager.TYPE_VPN
        }
        return false
    }


    fun getIMEINo(): String? {
        var imeiNumber: String? = ""
        val telephonyManager =
            activity!!.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return getDeviceUniqueID()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (telephonyManager?.imei != null) {
                return telephonyManager.imei
            }
        } else {
            if (telephonyManager?.deviceId != null) {
                return telephonyManager.deviceId
            }
        }
        return imeiNumber
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    fun getDeviceUniqueID(): String? {
        val wideVineUuid = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)
        return try {
            val wvDrm = MediaDrm(wideVineUuid)
            val wideVineId = wvDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
            val stringWithSymbols = wideVineId.contentToString()
            val strWithoutBrackets = stringWithSymbols.replace("\\[".toRegex(), "")
            val strWithoutBrackets1 = strWithoutBrackets.replace("]".toRegex(), "")
            val strWithoutComma = strWithoutBrackets1.replace(",".toRegex(), "")
            val strWithoutHyphen = strWithoutComma.replace("-".toRegex(), "")
            val strWithoutSpace = strWithoutHyphen.replace(" ".toRegex(), "")
            strWithoutSpace.substring(0, 15)
        } catch (e: Exception) {
            ""
        }
    }
}