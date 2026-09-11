package com.zzh.android_work

import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
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
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit


class AndroidWork(private var activity: Activity?, private var applicationContext: Context?) {

    companion object {
        private const val TAG = "AndroidWork"
        private val SU_PATHS = arrayOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/xbin/daemonsu",
            "/sbin/magisk",
            "/system/bin/magisk",
            "/data/adb/magisk/busybox"
        )
        private val ROOT_PACKAGES = arrayOf(
            "com.topjohnwu.magisk",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.noshufou.android.su",
            "com.kingroot.kinguser",
            "com.thirdparty.superuser"
        )
    }

    fun setActivity(activity: Activity?) {
        this.activity = activity
    }

    fun setApplicationContext(applicationContext: Context?) {
        this.applicationContext = applicationContext
    }

    private fun checkContext(): Boolean {
        if (applicationContext == null) {
            Log.w(TAG, "上下文未初始化，请先初始化")
            return false
        }
        return true
    }

    private fun contextOrThrow(): Context {
        return applicationContext ?: throw IllegalStateException("applicationContext 未初始化，请确认插件已 attach 到引擎")
    }

    fun requireContext(): Context = contextOrThrow()

    fun getSimulatorInfo(): MutableList<Any?>? {
        if (!checkContext()){
            return null
        }
        return Tools.getSimulatorInfo(applicationContext!!)
    }

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
        if (Build.TAGS != null && Build.TAGS.contains("test-keys")) return true

        for (path in SU_PATHS) {
            try { if (File(path).exists()) return true } catch (_: Exception) {}
        }

        try {
            val pm = contextOrThrow().packageManager
            for (pkg in ROOT_PACKAGES) {
                try { pm.getPackageInfo(pkg, 0); return true } catch (_: PackageManager.NameNotFoundException) {} catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        if (canExecSuWithTimeout()) return true
        if (whichSuExists()) return true

        return false
    }

    private fun canExecSuWithTimeout(): Boolean {
        var process: Process? = null
        var os: DataOutputStream? = null
        try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            os.writeBytes("echo root\n")
            os.writeBytes("exit\n")
            os.flush()
            try { os.close() } catch (_: Exception) {}
            os = null
            val finished = waitForWithTimeout(process, 900)
            if (finished && process.exitValue() == 0) return true
            if (!finished) { try { process.destroy() } catch (_: Exception) {} }
        } catch (_: Exception) {
        } finally {
            try { os?.close() } catch (_: Exception) {}
            try { process?.destroy() } catch (_: Exception) {}
        }
        return false
    }

    private fun whichSuExists(): Boolean {
        var p: Process? = null
        var reader: BufferedReader? = null
        try {
            p = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val finished = waitForWithTimeout(p, 600)
            reader = BufferedReader(InputStreamReader(p.inputStream, StandardCharsets.UTF_8))
            val out = reader.readLine()
            if (!out.isNullOrEmpty() && out.contains("su")) return true
            if (!finished) { try { p.destroy() } catch (_: Exception) {} }
        } catch (_: Exception) {
        } finally {
            try { reader?.close() } catch (_: Exception) {}
            try { p?.destroy() } catch (_: Exception) {}
        }
        return false
    }

    private fun waitForWithTimeout(process: Process, timeoutMs: Long): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            } else {
                val t = Thread { try { process.waitFor() } catch (_: InterruptedException) {} }
                t.start()
                t.join(timeoutMs)
                if (t.isAlive) { t.interrupt(); try { process.destroy() } catch (_: Exception) {}; false } else true
            }
        } catch (_: Exception) { false }
    }

    suspend fun isProxy(): Boolean {
        if (!checkContext()){
            return false
        }
        try {
            val httpHost = System.getProperty("http.proxyHost")
            val httpPort = System.getProperty("http.proxyPort")
            if (!httpHost.isNullOrEmpty() && httpHost != "null"
                && !httpPort.isNullOrEmpty() && httpPort != "-1" && httpPort != "0") return true
            val httpsHost = System.getProperty("https.proxyHost")
            if (!httpsHost.isNullOrEmpty() && httpsHost != "null") return true
        } catch (_: Exception) {}

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val cm = contextOrThrow().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val proxyInfo = cm.defaultProxy
                if (proxyInfo != null && !proxyInfo.host.isNullOrEmpty()) return true
            } else {
                @Suppress("DEPRECATION")
                val host = Proxy.getHost(contextOrThrow())
                @Suppress("DEPRECATION")
                val port = Proxy.getPort(contextOrThrow())
                if (!TextUtils.isEmpty(host) && port != -1) return true
            }
        } catch (_: Exception) {}

        try {
            val legacy = Settings.System.getString(contextOrThrow().contentResolver, "http_proxy")
            if (!TextUtils.isEmpty(legacy)) return true
        } catch (_: Exception) {}

        return false
    }

    suspend fun isOpenVPN(): Boolean {
        if (!checkContext()) return false
        return try {
            val connectivityManager =
                contextOrThrow().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNetwork = connectivityManager.activeNetwork
                if (activeNetwork != null) {
                    val caps = connectivityManager.getNetworkCapabilities(activeNetwork)
                    if (caps != null) return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                }
                val networks = connectivityManager.allNetworks
                for (net in networks) {
                    val caps = connectivityManager.getNetworkCapabilities(net)
                    if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) return true
                }
                return false
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val networks = connectivityManager.allNetworks
                for (net in networks) {
                    val caps = connectivityManager.getNetworkCapabilities(net)
                    if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) return true
                }
            }
            @Suppress("DEPRECATION")
            val networkInfo = connectivityManager.activeNetworkInfo
            @Suppress("DEPRECATION")
            networkInfo != null && networkInfo.isConnected && networkInfo.type == ConnectivityManager.TYPE_VPN
        } catch (e: Exception) {
            Log.w(TAG, "isOpenVPN error", e)
            false
        }
    }

    suspend fun getSignature(type:String): List<String> {
        if (!checkContext()){
            return emptyList()
        }
        val normalized = type.trim().uppercase()
        val digestAlg = when (normalized) {
            "MD5" -> "MD5"
            "SHA-1", "SHA1" -> "SHA-1"
            "SHA-256", "SHA256", "SHA_256" -> "SHA-256"
            else -> return emptyList()
        }

        val pm: PackageManager = contextOrThrow().packageManager
        val packageName: String = contextOrThrow().packageName
        val out = LinkedHashSet<String>()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val signingInfo = packageInfo.signingInfo ?: return emptyList()
                val sigs = signingInfo.apkContentsSigners
                for (sig in sigs) {
                    val md = MessageDigest.getInstance(digestAlg)
                    md.update(sig.toByteArray())
                    out.add(bytesToHex(md.digest()))
                }
            } else {
                @Suppress("DEPRECATION")
                val packageInfo = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                val sigs = packageInfo.signatures ?: return emptyList()
                for (sig in sigs) {
                    val md = MessageDigest.getInstance(digestAlg)
                    md.update(sig.toByteArray())
                    out.add(bytesToHex(md.digest()))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "getSignature error", e)
            return emptyList()
        }
        return out.toList()
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) sb.append(String.format("%02x", b))
        return sb.toString()
    }

    fun getIMEINo(): String? {
        if (!checkContext()) return null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val drmId = getDeviceUniqueID()
            if (!drmId.isNullOrEmpty()) return drmId
        }
        return try {
            val telephonyManager =
                contextOrThrow().getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    val imei = telephonyManager.imei
                    if (!imei.isNullOrEmpty()) return imei
                } catch (_: SecurityException) {
                }
            }
            @Suppress("DEPRECATION")
            try {
                val deviceId = telephonyManager.deviceId
                if (!deviceId.isNullOrEmpty()) return deviceId
            } catch (_: SecurityException) {
            }
            getDeviceUniqueID()
        } catch (e: Exception) {
            Log.w(TAG, "getIMEINo error", e)
            getDeviceUniqueID()
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN_MR2)
    fun getDeviceUniqueID(): String? {
        val wideVineUuid = UUID(-0x121074568629b532L, -0x5c37d8232ae2de13L)
        var wvDrm: MediaDrm? = null
        return try {
            wvDrm = MediaDrm(wideVineUuid)
            val wideVineId = wvDrm.getPropertyByteArray(MediaDrm.PROPERTY_DEVICE_UNIQUE_ID)
            if (wideVineId == null || wideVineId.isEmpty()) return ""
            val sb = StringBuilder(wideVineId.size * 2)
            for (b in wideVineId) sb.append(String.format("%02x", b))
            val hex = sb.toString().replace(" ", "")
            if (hex.length >= 15) hex.substring(0, 15) else hex
        } catch (e: Exception) {
            Log.w(TAG, "getDeviceUniqueID error", e)
            ""
        } finally {
            try { wvDrm?.release() } catch (_: Exception) {}
        }
    }
}
