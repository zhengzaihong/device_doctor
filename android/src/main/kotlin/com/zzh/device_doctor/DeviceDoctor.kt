package com.zzh.device_doctor

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
import com.zzh.device_doctor.simulator.EmulatorCheckUtil
import com.zzh.device_doctor.simulator.Tools
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.TimeUnit

class DeviceDoctor(private var activity: Activity?, private var applicationContext: Context?) {

    companion object {
        private const val TAG = "DeviceDoctor"
        /** 确凿 root 管理器/root 引擎包。注意：只见 su 二进制或 /data/adb 目录 ≠ root（真机镜像常自带）。 */
        private val ROOT_PACKAGES = arrayOf(
            "com.topjohnwu.magisk",
            "io.github.vvb2060.magisk",
            "io.github.huskydg.magisk",
            "eu.chainfire.supersu",
            "com.koushikdutta.superuser",
            "com.noshufou.android.su",
            "com.kingroot.kinguser",
            "com.kingroot.master",
            "com.thirdparty.superuser",
            "me.weishu.kernelsu",
            "me.weishu.kernelsu_next",
            "com.rifsxd.ksunext",
            // 国产模拟器/云手机自带 root 开关
            "com.mumu.superuser",
            "com.netease.mumu.superuser",
            "com.bignox.superuser",
            "com.microvirt.superuser",
            "com.ld.superuser",
            "com.vmos.pro",
            "com.x8.sandbox"
        )
        /** Magisk/KernelSU/APatch 专属路径（真机无这些目录）。 */
        private val MAGISK_TRACE_PATHS = arrayOf(
            "/sbin/.magisk",
            "/dev/.magisk",
            "/magisk",
            "/data/adb/magisk",
            "/data/adb/ksu",
            "/data/adb/ap",
            "/data/adb/modules",
            "/data/adb/service.d",
            "/data/adb/post-fs-data.d"
        )
    }

    fun setActivity(activity: Activity?) { this.activity = activity }
    fun setApplicationContext(applicationContext: Context?) { this.applicationContext = applicationContext }

    private fun checkContext(): Boolean {
        if (applicationContext == null) {
            Log.w(TAG, "Context not initialized")
            return false
        }
        return true
    }

    private fun contextOrThrow(): Context =
        applicationContext ?: throw IllegalStateException("applicationContext not initialized")

    fun requireContext(): Context = contextOrThrow()

    fun getSimulatorInfo(): MutableList<String?>? {
        if (!checkContext()) return null
        return Tools.getSimulatorInfo(applicationContext!!)
    }

    fun isSimulator(callback: (info: Any) -> Unit) {
        if (!checkContext()) return
        EmulatorCheckUtil.getSingleInstance().readSysProperty(applicationContext!!) { info -> callback.invoke(info) }
    }

    suspend fun isRoot(): Boolean {
        if (!checkContext()) return false
        val reason = detectRoot()
        if (!TextUtils.isEmpty(reason)) {
            Log.d(TAG, "isRoot=true: $reason")
            return true
        }
        return false
    }

    /**
     * Root 判定。原则：仅凭 su 二进制存在或 /data/adb 目录存在不等于已提权
     *（真机系统镜像普遍自带 su），因此必须命中确凿证据才判 root。
     * 返回首个命中的依据描述，未命中返回 null。
     */
    private fun detectRoot(): String? {
        // 1) 进程本身已是 uid 0（adb root / 系统整体提权）
        try {
            if (android.os.Process.myUid() == 0) return "uid=0"
        } catch (_: Exception) {}
        // 2) 真实 root 管理器包已安装
        try {
            val pm = contextOrThrow().packageManager
            for (pkg in ROOT_PACKAGES) {
                try { pm.getPackageInfo(pkg, 0); return "root-manager:$pkg" }
                catch (_: PackageManager.NameNotFoundException) {} catch (_: Exception) {}
            }
        } catch (_: Exception) {}
        // 3) su 实测可提权到 uid 0
        if (canExecSuWithTimeout()) return "su-uid0"
        // 4) Magisk/KernelSU 专属挂载/目录痕迹
        if (magiskOrKsuTraces()) return "magisk-ksu"
        // 5) SELinux 非 Enforcing：root 化/定制镜像的典型状态
        if (selinuxNotEnforcing()) return "selinux-permissive"
        // 6) adbd 以 root 运行：service.adb.root=1
        if (serviceAdbRoot()) return "adb-root"
        // 弱信号：test-keys / debuggable / 解锁 BL 在部分出厂真机上会单独出现，
        // 任一单个都不判 root，凑齐 2 个才判。
        var weak = 0
        val weakWhy = ArrayList<String>()
        if (hasTestKeys()) { weak++; weakWhy.add("test-keys") }
        if (debuggableBuild()) { weak++; weakWhy.add("debuggable") }
        if (unlockedBootloader()) { weak++; weakWhy.add("unlocked-bl") }
        if (weak >= 2) return "weak:" + weakWhy.joinToString(",")
        return null
    }

    /**
     * Magisk / KernelSU / APatch 的专属挂载与目录痕迹。
     * 注意：真机也常存在 /data/adb 空目录，不能作为依据；只认这些确凿的
     * Magisk/KSU 专属子路径，与 mount table 里的 magisk/kernelsu 关键字。
     */
    private fun magiskOrKsuTraces(): Boolean {
        return try {
            for (p in MAGISK_TRACE_PATHS) {
                try { if (File(p).exists()) return true } catch (_: Exception) {}
            }
            val mounts = readText("/proc/self/mounts") ?: readText("/proc/mounts") ?: ""
            val low = mounts.lowercase()
            low.contains("magisk") || low.contains("kernelsu") || low.contains("ksunext")
        } catch (_: Exception) { false }
    }

    /** `getenforce`/`/sys/fs/selinux/enforce`：Permissive 或 0 视为已 root 化环境。 */
    private fun selinuxNotEnforcing(): Boolean {
        return try {
            val flag = readText("/sys/fs/selinux/enforce")?.trim()
            if (flag != null) {
                if (flag.isEmpty()) return false
                return flag != "1"
            }
            val out = execQuick("getenforce")
            if (out.isNullOrEmpty()) false else !out.trim().equals("Enforcing", ignoreCase = true)
        } catch (_: Exception) { false }
    }

    /** adbd 本身以 root 运行（模拟器/工程机常开）。用户版量产真机不会出现。 */
    private fun serviceAdbRoot(): Boolean {
        return try {
            val cm = Class.forName("android.os.SystemProperties")
            val get = cm.getMethod("get", String::class.java)
            (get.invoke(null, "service.adb.root") as? String) == "1"
        } catch (_: Exception) { false }
    }

    private fun hasTestKeys(): Boolean {
        return try { Build.TAGS != null && Build.TAGS.contains("test-keys") } catch (_: Exception) { false }
    }

    /** ro.debuggable=1 且 ro.secure=0：量产真机绝不会同时满足。 */
    private fun debuggableBuild(): Boolean {
        return try {
            val cm = Class.forName("android.os.SystemProperties")
            val get = cm.getMethod("get", String::class.java)
            val debuggable = (get.invoke(null, "ro.debuggable") as? String) == "1"
            val secure = (get.invoke(null, "ro.secure") as? String)
            debuggable && (secure == "0" || secure.isNullOrEmpty())
        } catch (_: Exception) { false }
    }

    /** `ro.boot.verifiedbootstate`/`ro.boot.vbmeta.device_state` 解锁：可注入 su 的环境。 */
    private fun unlockedBootloader(): Boolean {
        return try {
            val cm = Class.forName("android.os.SystemProperties")
            val get = cm.getMethod("get", String::class.java)
            val vboot = (get.invoke(null, "ro.boot.verifiedbootstate") as? String)?.lowercase()
            val state = (get.invoke(null, "ro.boot.vbmeta.device_state") as? String)?.lowercase()
            vboot == "orange" || vboot == "red" || state == "unlocked"
        } catch (_: Exception) { false }
    }

    private fun readText(path: String): String? {
        return try {
            val f = File(path)
            if (!f.exists() || !f.canRead()) return null
            f.inputStream().use { it.readBytes().toString(StandardCharsets.UTF_8) }
        } catch (_: Exception) { null }
    }

    private fun execQuick(command: String): String? {
        var p: Process? = null
        return try {
            p = Runtime.getRuntime().exec(command)
            val finished = waitForWithTimeout(p, 700)
            val out = p.inputStream.bufferedReader().readText()
            if (!finished) { try { p.destroy() } catch (_: Exception) {} }
            out
        } catch (_: Exception) { null } finally { try { p?.destroy() } catch (_: Exception) {} }
    }

    private fun canExecSuWithTimeout(): Boolean {
        // su 授权弹框会阻塞到用户点击：超时后进程仍活着≠有 root，必须同时校验 uid/id 输出。
        // 无人值守时用超短超时，存在弹框就返回 false 而不是一直挂起。
        var process: Process? = null
        var os: DataOutputStream? = null
        var reader: BufferedReader? = null
        try {
            process = Runtime.getRuntime().exec("su")
            os = DataOutputStream(process.outputStream)
            os.writeBytes("id\n")
            os.writeBytes("exit\n")
            os.flush()
            try { os.close() } catch (_: Exception) {}
            os = null
            val finished = waitForWithTimeout(process, 700)
            if (!finished) { try { process.destroy() } catch (_: Exception) {} ; return false }
            if (process.exitValue() != 0) return false
            reader = BufferedReader(InputStreamReader(process.inputStream, StandardCharsets.UTF_8))
            val out = reader.readLine() ?: ""
            // 典型输出: uid=0(root) gid=0(root) ...；模拟器 su 常输出 uid=0 无 root 字样，需同时兼容
            return out.contains("uid=0") || out.contains("(root)")
        } catch (_: Exception) {
            return false
        } finally {
            try { reader?.close() } catch (_: Exception) {}
            try { os?.close() } catch (_: Exception) {}
            try { process?.destroy() } catch (_: Exception) {}
        }
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
        if (!checkContext()) return false
        try {
            val httpHost = System.getProperty("http.proxyHost")
            val httpPort = System.getProperty("http.proxyPort")
            if (!httpHost.isNullOrEmpty() && httpHost != "null" && !httpPort.isNullOrEmpty() && httpPort != "-1" && httpPort != "0") return true
            val httpsHost = System.getProperty("https.proxyHost")
            if (!httpsHost.isNullOrEmpty() && httpsHost != "null") return true
        } catch (_: Exception) {}
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val cm = contextOrThrow().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                val proxyInfo = cm.defaultProxy
                if (proxyInfo != null && !proxyInfo.host.isNullOrEmpty()) return true
            } else {
                @Suppress("DEPRECATION") val host = Proxy.getHost(contextOrThrow())
                @Suppress("DEPRECATION") val port = Proxy.getPort(contextOrThrow())
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
            val cm = contextOrThrow().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val activeNetwork = cm.activeNetwork
                if (activeNetwork != null) {
                    val caps = cm.getNetworkCapabilities(activeNetwork)
                    if (caps != null) return caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                }
                val networks = cm.allNetworks
                for (net in networks) {
                    val caps = cm.getNetworkCapabilities(net)
                    if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) return true
                }
                return false
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                val networks = cm.allNetworks
                for (net in networks) {
                    val caps = cm.getNetworkCapabilities(net)
                    if (caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true) return true
                }
            }
            @Suppress("DEPRECATION") val ni = cm.activeNetworkInfo
            @Suppress("DEPRECATION") ni != null && ni.isConnected && ni.type == ConnectivityManager.TYPE_VPN
        } catch (e: Exception) {
            Log.w(TAG, "isOpenVPN error", e)
            false
        }
    }

    suspend fun getSignature(type: String): List<String> {
        if (!checkContext()) return emptyList()
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
                val pi = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                val si = pi.signingInfo ?: return emptyList()
                for (sig in si.apkContentsSigners) {
                    val md = MessageDigest.getInstance(digestAlg)
                    md.update(sig.toByteArray())
                    out.add(bytesToHex(md.digest()))
                }
            } else {
                @Suppress("DEPRECATION")
                val pi = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                val sigs = pi.signatures ?: return emptyList()
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
            val tm = contextOrThrow().getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try { val imei = tm.imei; if (!imei.isNullOrEmpty()) return imei } catch (_: SecurityException) {}
            }
            @Suppress("DEPRECATION")
            try { val did = tm.deviceId; if (!did.isNullOrEmpty()) return did } catch (_: SecurityException) {}
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
