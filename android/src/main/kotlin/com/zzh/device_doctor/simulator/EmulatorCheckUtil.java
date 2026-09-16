package com.zzh.device_doctor.simulator;

import static android.content.Context.SENSOR_SERVICE;
import static com.zzh.device_doctor.simulator.CheckResult.RESULT_EMULATOR;
import static com.zzh.device_doctor.simulator.CheckResult.RESULT_MAYBE_EMULATOR;
import static com.zzh.device_doctor.simulator.CheckResult.RESULT_UNKNOWN;
import static com.zzh.device_doctor.simulator.Tools.getInstalledSimulatorPackages;
import static com.zzh.device_doctor.simulator.Tools.getSimulatorBrand;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.BatteryManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;
import android.view.InputDevice;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public class EmulatorCheckUtil {
    private static final String TAG = "EmulatorCheck";
    private static final long CACHE_TTL_MS = 5 * 60 * 1000L;
    private static final Object CACHE_LOCK = new Object();
    private static volatile Map<String, Object> sCachedMap = null;
    private static volatile long sCachedAt = 0;

    private static volatile String sCachedCpuInfo = null;
    private static volatile String sCachedAbiStr = null;

    private EmulatorCheckUtil() {}

    private static class SingletonHolder {
        private static final EmulatorCheckUtil INSTANCE = new EmulatorCheckUtil();
    }

    public static final EmulatorCheckUtil getSingleInstance() {
        return SingletonHolder.INSTANCE;
    }

    private static int scoreIfSuspicious(CheckResult r) {
        return r.result != RESULT_UNKNOWN ? 1 : 0;
    }

    public void clearCache() {
        synchronized (CACHE_LOCK) {
            sCachedMap = null;
            sCachedAt = 0;
        }
    }

    public static void clearCpuCache() {
        sCachedCpuInfo = null;
        sCachedAbiStr = null;
    }

    private Map<String, Object> buildMap(Context context) {
        int suspectCount = 0;
        List<String> hits = new ArrayList<>();

        CheckResult flavorResult = checkFeaturesByFlavor();
        suspectCount += scoreIfSuspicious(flavorResult);
        if (flavorResult.result == RESULT_EMULATOR) hits.add("flavor=" + flavorResult.value);

        CheckResult modelResult = checkFeaturesByModel();
        suspectCount += scoreIfSuspicious(modelResult);
        if (modelResult.result == RESULT_EMULATOR) hits.add("model=" + modelResult.value);

        CheckResult manufacturerResult = checkFeaturesByManufacturer();
        suspectCount += scoreIfSuspicious(manufacturerResult);
        if (manufacturerResult.result == RESULT_EMULATOR) hits.add("manufacturer=" + manufacturerResult.value);

        CheckResult boardResult = checkFeaturesByBoard();
        suspectCount += scoreIfSuspicious(boardResult);
        if (boardResult.result == RESULT_EMULATOR) hits.add("board=" + boardResult.value);

        CheckResult platformResult = checkFeaturesByPlatform();
        suspectCount += scoreIfSuspicious(platformResult);
        if (platformResult.result == RESULT_EMULATOR) hits.add("platform=" + platformResult.value);

        CheckResult baseBandResult = checkFeaturesByBaseBand();
        if (baseBandResult.result != RESULT_UNKNOWN) {
            suspectCount += 2;
        }
        if (baseBandResult.result == RESULT_EMULATOR) hits.add("baseband=" + baseBandResult.value);

        int sensorNumber = getSensorNumber(context);
        if (sensorNumber <= 7) ++suspectCount;

        boolean supportCameraFlash = supportCameraFlash(context);
        if (!supportCameraFlash) ++suspectCount;
        boolean supportCamera = supportCamera(context);
        if (!supportCamera) ++suspectCount;
        boolean supportBluetooth = supportBluetooth(context);
        if (!supportBluetooth) ++suspectCount;

        boolean hasLightSensor = hasLightSensor(context);
        if (!hasLightSensor) ++suspectCount;

        boolean hasGPS = supportGPS(context);
        if (!hasGPS) ++suspectCount;

        boolean hasTemperature = supportTemperature(context);
        if (!hasTemperature) ++suspectCount;

        boolean hasSensorLight = supportSensorLight(context);
        if (!hasSensorLight) ++suspectCount;

        try {
            if(Tools.isSimulator(context)){
                ++suspectCount;
            }
        } catch (Exception e) {
            Log.w(TAG, "Tools.isSimulator error", e);
        }
        String hardwareHit = isSimulatorHardware();
        if(!TextUtils.isEmpty(hardwareHit)){
            suspectCount+=100;
            hits.add("hardware=" + hardwareHit);
        }

        String brandByProp = brandByProperties();
        if(!TextUtils.isEmpty(brandByProp)){
            // 属性里直接泄漏模拟器品牌（如 ro.*.mumu / vbox86）视为确定命中
            suspectCount += 100;
            hits.add("propBrand=" + brandByProp);
            if (TextUtils.isEmpty(hardwareHit)) hardwareHit = brandByProp;
        }

        String pkgHit = "";
        try {
            pkgHit = checkHasSimulatorMainPackage(context);
        } catch (Exception e) {
            Log.w(TAG, "checkHasSimulatorMainPackage error", e);
        }
        if(!TextUtils.isEmpty(pkgHit)){
            suspectCount+=3;
            hits.add("pkg=" + pkgHit);
        }
        if(checkIsNotRealPhone()){
            ++suspectCount;
            hits.add("cpu=intel/amd");
        }
        if(checkPipes()){
            ++suspectCount;
            hits.add("qemu_pipe");
        }

        // MuMu12/LD9 等新镜像不再挂 qemu_pipe,改为 vbox/virtualbox 设备节点。
        String pipeHit = checkVirtualDeviceNodes();
        if(!TextUtils.isEmpty(pipeHit)){
            suspectCount += 2;
            hits.add("vnode=" + pipeHit);
        }

        String driverHit = checkDriverFiles();
        if(!TextUtils.isEmpty(driverHit)){
            suspectCount += 2;
            hits.add("driver=" + driverHit);
        }

        String propHit = scanAllProperties();
        if(!TextUtils.isEmpty(propHit)){
            suspectCount += 3;
            hits.add("prop=" + propHit);
        }

        if(isX86AbiDevice()){
            ++suspectCount;
            hits.add("abi=x86");
        }
        if(isYeShenEmulator()){
            ++suspectCount;
        }

        String cpuVendorHit = checkCpuVendorSignature();
        if(!TextUtils.isEmpty(cpuVendorHit)){
            ++suspectCount;
            hits.add("cpuinfo=" + cpuVendorHit);
        }

        if(hasNoTelephonyFeature(context)){
            ++suspectCount;
            hits.add("no_telephony");
        }
        if(hasNoRealBattery(context)){
            ++suspectCount;
            hits.add("fake_battery");
        }
        if(hasOnlyVirtualInput(context)){
            ++suspectCount;
            hits.add("virtual_input");
        }

        String fstabHit = checkMountTable();
        if(!TextUtils.isEmpty(fstabHit)){
            ++suspectCount;
            hits.add("mount=" + fstabHit);
        }

        String kernelHit = checkKernelVersion();
        if(!TextUtils.isEmpty(kernelHit)){
            suspectCount += 2;
            hits.add("kernel=" + kernelHit);
        }

        String gpuHit = checkGpuStack();
        if(!TextUtils.isEmpty(gpuHit)){
            suspectCount += 2;
            hits.add("gpu=" + gpuHit);
        }

        String sensorHit = checkSensorSignatures(context);
        if(!TextUtils.isEmpty(sensorHit)){
            suspectCount += 2;
            hits.add("sensor=" + sensorHit);
        }

        String initSvcHit = checkInitServices();
        if(!TextUtils.isEmpty(initSvcHit)){
            suspectCount += 2;
            hits.add("initsvc=" + initSvcHit);
        }

        CheckResult cgroupResult = checkFeaturesByCgroup();
        if (cgroupResult.result == RESULT_MAYBE_EMULATOR){
            ++suspectCount;
        }

        Map<String,Object> map = new LinkedHashMap<>();
        map.put("hardware",getProperty("ro.hardware"));
        map.put("hardwareHit", hardwareHit);
        map.put("brandByProp", brandByProp);
        map.put("flavor",flavorResult.value);
        map.put("model",modelResult.value);
        map.put("manufacturer",manufacturerResult.value);
        map.put("board",boardResult.value);
        map.put("platform",platformResult.value);
        map.put("baseBand",baseBandResult.value);
        map.put("sensorNumber",sensorNumber);
        map.put("supportCamera",supportCamera);
        map.put("supportCameraFlash",supportCameraFlash);
        map.put("supportBluetooth",supportBluetooth);
        map.put("hasLightSensor",hasLightSensor);
        map.put("cgroupResult",cgroupResult.value);
        map.put("hits",hits);
        map.put("value",suspectCount);
        return map;
    }

    public Map<String, Object> getEmulatorInfoSync(Context context) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        Map<String, Object> cached = sCachedMap;
        long at = sCachedAt;
        if (cached != null && System.currentTimeMillis() - at < CACHE_TTL_MS) {
            return new HashMap<>(cached);
        }
        synchronized (CACHE_LOCK) {
            cached = sCachedMap;
            at = sCachedAt;
            if (cached != null && System.currentTimeMillis() - at < CACHE_TTL_MS) {
                return new HashMap<>(cached);
            }
            Map<String, Object> fresh = buildMap(context.getApplicationContext() != null ? context.getApplicationContext() : context);
            sCachedMap = new HashMap<>(fresh);
            sCachedAt = System.currentTimeMillis();
            return fresh;
        }
    }

    public void readSysProperty(Context context, EmulatorCheckCallback callback) {
        if (context == null) throw new IllegalArgumentException("context must not be null");
        Map<String, Object> map;
        try {
            map = getEmulatorInfoSync(context);
        } catch (Exception e) {
            Log.w(TAG, "getEmulatorInfoSync error", e);
            map = new HashMap<>();
            map.put("value", 0);
            map.put("error", e.getMessage());
        }
        if (callback != null) callback.findEmulator(map);
    }

    private static String getProperty(String propName) {
        String property = CommandUtil.getSingleInstance().getProperty(propName);
        return TextUtils.isEmpty(property) ? null : property;
    }

    private CheckResult checkFeaturesByFlavor() {
        String flavor = getProperty("ro.build.flavor");
        if (null == flavor) return new CheckResult(RESULT_MAYBE_EMULATOR, null);
        String tempValue = flavor.toLowerCase();
        if (tempValue.contains("vbox")) return new CheckResult(RESULT_EMULATOR, flavor);
        if (tempValue.contains("sdk_gphone")) return new CheckResult(RESULT_EMULATOR, flavor);
        if (tempValue.contains("mumu") || tempValue.contains("nemu")
                || tempValue.contains("nox") || tempValue.contains("ldmnq")
                || tempValue.contains("microvirt") || tempValue.contains("memu")
                || tempValue.contains("bluestacks")) return new CheckResult(RESULT_EMULATOR, flavor);
        return new CheckResult(RESULT_UNKNOWN, flavor);
    }

    private CheckResult checkFeaturesByModel() {
        String model = getProperty("ro.product.model");
        if (null == model) return new CheckResult(RESULT_MAYBE_EMULATOR, null);
        String tempValue = model.toLowerCase();
        if (tempValue.contains("google_sdk")) return new CheckResult(RESULT_EMULATOR, model);
        if (tempValue.contains("emulator")) return new CheckResult(RESULT_EMULATOR, model);
        if (tempValue.contains("android sdk built for x86")) return new CheckResult(RESULT_EMULATOR, model);
        // 国产模拟器 Android 12+ 镜像常见伪装机型（MuMu12 默认 MuMu、雷电9 默认 LD-Player）
        if (tempValue.contains("mumu") || tempValue.contains("nemu")
                || tempValue.contains("ld-") || tempValue.contains("ldplayer")
                || tempValue.contains("nox") || tempValue.contains("memu")
                || tempValue.contains("microvirt") || tempValue.contains("vbox86")
                || tempValue.contains("redfinger") || tempValue.contains("vmos")
                || tempValue.contains("cloudphone")) return new CheckResult(RESULT_EMULATOR, model);
        return new CheckResult(RESULT_UNKNOWN, model);
    }

    private CheckResult checkFeaturesByManufacturer() {
        String manufacturer = getProperty("ro.product.manufacturer");
        if (null == manufacturer) return new CheckResult(RESULT_MAYBE_EMULATOR, null);
        String tempValue = manufacturer.toLowerCase();
        if (tempValue.contains("genymotion")) return new CheckResult(RESULT_EMULATOR, manufacturer);
        if (tempValue.contains("netease")) return new CheckResult(RESULT_EMULATOR, manufacturer);
        if (tempValue.contains("mumu") || tempValue.contains("nemu")
                || tempValue.contains("nox") || tempValue.contains("bignox")
                || tempValue.contains("leidian") || tempValue.contains("microvirt")
                || tempValue.contains("tiantian") || tempValue.contains("bluestacks")
                || tempValue.contains("vbox") || tempValue.contains("innotek")) return new CheckResult(RESULT_EMULATOR, manufacturer);
        return new CheckResult(RESULT_UNKNOWN, manufacturer);
    }

    private CheckResult checkFeaturesByBoard() {
        String board = getProperty("ro.product.board");
        if (null == board) return new CheckResult(RESULT_MAYBE_EMULATOR, null);
        String tempValue = board.toLowerCase();
        if (tempValue.contains("android")) return new CheckResult(RESULT_EMULATOR, board);
        if (tempValue.contains("goldfish")) return new CheckResult(RESULT_EMULATOR, board);
        if (tempValue.contains("vbox") || tempValue.contains("mumu") || tempValue.contains("nemu")
                || tempValue.contains("nox") || tempValue.contains("ranchu")) return new CheckResult(RESULT_EMULATOR, board);
        return new CheckResult(RESULT_UNKNOWN, board);
    }

    private CheckResult checkFeaturesByPlatform() {
        String platform = getProperty("ro.board.platform");
        if (null == platform) return new CheckResult(RESULT_MAYBE_EMULATOR, null);
        String tempValue = platform.toLowerCase();
        if (tempValue.contains("android")) return new CheckResult(RESULT_EMULATOR, platform);
        if (tempValue.contains("vbox") || tempValue.contains("virtualbox")
                || tempValue.contains("mumu") || tempValue.contains("ldmnq")
                || tempValue.contains("goldfish") || tempValue.contains("ranchu")) return new CheckResult(RESULT_EMULATOR, platform);
        return new CheckResult(RESULT_UNKNOWN, platform);
    }

    private CheckResult checkFeaturesByBaseBand() {
        String baseBandVersion = getProperty("gsm.version.baseband");
        if (null == baseBandVersion) return new CheckResult(RESULT_MAYBE_EMULATOR, null);
        if (baseBandVersion.contains("1.0.0.0")) return new CheckResult(RESULT_EMULATOR, baseBandVersion);
        return new CheckResult(RESULT_UNKNOWN, baseBandVersion);
    }

    private int getSensorNumber(Context context) {
        try {
            SensorManager sm = (SensorManager) context.getSystemService(SENSOR_SERVICE);
            if (sm == null) return 0;
            return sm.getSensorList(Sensor.TYPE_ALL).size();
        } catch (Exception e) {
            return 0;
        }
    }

    private boolean supportCamera(Context context) {
        try { return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA); } catch (Exception e) { return false; }
    }

    private boolean supportCameraFlash(Context context) {
        try { return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH); } catch (Exception e) { return false; }
    }

    private boolean supportBluetooth(Context context) {
        try { return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH); } catch (Exception e) { return false; }
    }

    private boolean supportGPS(Context context) {
        try { return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS); } catch (Exception e) { return false; }
    }

    private boolean supportTemperature(Context context) {
        try { return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_AMBIENT_TEMPERATURE); } catch (Exception e) { return false; }
    }

    private boolean supportSensorLight(Context context) {
        try { return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_LIGHT); } catch (Exception e) { return false; }
    }

    private boolean hasLightSensor(Context context) {
        try {
            SensorManager sensorManager = (SensorManager) context.getSystemService(SENSOR_SERVICE);
            if (sensorManager == null) return false;
            Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
            return sensor != null;
        } catch (Exception e) {
            return false;
        }
    }

    private CheckResult checkFeaturesByCgroup() {
        String filter = CommandUtil.getSingleInstance().exec("cat /proc/self/cgroup");
        if (null == filter) return new CheckResult(RESULT_MAYBE_EMULATOR, null);
        String low = filter.toLowerCase();
        if (low.contains("docker") || low.contains("lxc") || low.contains("kubepods")
                || low.contains("burstable") || low.contains("qemu") || low.contains("goldfish")
                || low.contains("emu") || low.contains("mumu") || low.contains("nox")) {
            return new CheckResult(RESULT_EMULATOR, filter.trim());
        }
        return new CheckResult(RESULT_UNKNOWN, filter);
    }

    public static String checkHasSimulatorMainPackage(Context context) {
        List<String> pathList = getInstalledSimulatorPackages(context);
        return getSimulatorBrand(pathList);
    }

    public static String isSimulatorHardware() {
        // 内核级硬指纹：ro.kernel.qemu / ro.boot.qemu 为 1 时 100% 为 qemu/ranchu 系模拟器
        if ("1".equals(getProperty("ro.kernel.qemu")) || "1".equals(getProperty("ro.boot.qemu"))) {
            return "QEMU/Ranchu模拟器";
        }
        String hardware = getProperty("ro.hardware");
        if (TextUtils.isEmpty(hardware)) return "";
        String tempValue = hardware.toLowerCase();
        if(tempValue.startsWith("cancro")){
            return "MUMU模拟器";
        }else if(tempValue.contains("mumu") || tempValue.contains("nemu")){
            return "MUMU模拟器";
        }else if(tempValue.contains("nox") || tempValue.contains("x86")){
            return "夜神模拟器";
        }else if(tempValue.contains("android_x86")){
            return "雷电模拟器";
        }else if(tempValue.contains("vbox") || tempValue.contains("virtualbox")){
            return "VirtualBox系模拟器";
        }else if(tempValue.contains("ranchu") || tempValue.contains("goldfish")){
            return "Android官方模拟器";
        }
        return "";
    }

    /**
     * 全量 system property 聚合扫描：一次 `getprop` 导出里搜全部可疑关键字。
     * MuMu12 等新镜像的主力伪装在 Build.*，但厂商驱动/渠道/内核指纹常残留在杂项属性里。
     * 命中返回首个 key=value（截断），未命中返回空串。
     */
    private static String scanAllProperties() {
        Map<String, String> all;
        try {
            all = CommandUtil.getSingleInstance().getAllProperties();
        } catch (Exception e) {
            Log.w(TAG, "getAllProperties error", e);
            return "";
        }
        if (all == null || all.isEmpty()) return "";
        for (Map.Entry<String, String> e : all.entrySet()) {
            String rawKey = e.getKey();
            String rawVal = e.getValue();
            String key = rawKey == null ? "" : rawKey.toLowerCase();
            String val = rawVal == null ? "" : rawVal.toLowerCase();
            // qemu 类键由 isSimulatorHardware() 按值精确判定，这里跳过避免 `ro.kernel.qemu=0` 误报
            if (key.contains("qemu")) continue;
            if (isNoisyKey(key)) continue;
            // 只认非空值或键名命中，空值命中会放大噪声
            if (TextUtils.isEmpty(val) && !keyHit(key)) continue;
            if (keyHit(key) || valueHit(val)) {
                String hit = rawKey + "=" + rawVal;
                return hit.length() > 120 ? hit.substring(0, 120) : hit;
            }
        }
        return "";
    }

    private static boolean keyHit(String key) {
        for (String kw : PROP_EMU_KEYWORDS) if (key.contains(kw)) return true;
        return false;
    }

    private static boolean valueHit(String val) {
        for (String kw : PROP_EMU_KEYWORDS) if (val.contains(kw)) return true;
        return false;
    }

    private static final String[] PROP_EMU_KEYWORDS = {
            "mumu", "nemu", "netease", "nox", "bignox", "ldmnq", "ldplayer",
            "microvirt", "memu", "bluestacks", "bstfolder", "genymotion",
            "vbox", "virtualbox", "innotek", "goldfish", "ranchu", "qemu",
            "ttvm", "windroy", "tiantian", "vphone", "redfinger"
    };

    private static boolean isNoisyKey(String key) {
        return key.equals("ro.build.type") || key.equals("ro.build.tags")
                || key.equals("ro.build.display.id") || key.equals("ro.build.description");
    }

    /** 属性直接泄漏品牌（ro.*.mumu/vbox86 等）视为确定命中，返回品牌名。 */
    private static String brandByProperties() {
        String[] keys = {
                "ro.hardware", "ro.product.board", "ro.product.device", "ro.product.model",
                "ro.product.brand", "ro.product.manufacturer", "ro.product.name",
                "ro.board.platform", "ro.build.product", "ro.build.characteristics",
                "ro.kernel.qemu", "ro.kernel.qemu.gles", "ro.hardware.chipname"
        };
        StringBuilder peek = new StringBuilder();
        for (String k : keys) {
            String v = getProperty(k);
            if (TextUtils.isEmpty(v)) continue;
            String low = v.toLowerCase();
            if (low.contains("mumu") || low.contains("nemu") || low.contains("netease")) return "mumu";
            if (low.contains("nox") || low.contains("bignox")) return "夜神";
            if (low.contains("ldmnq") || low.contains("ldplayer") || low.contains("flysilkworm")) return "雷电";
            if (low.contains("bluestacks")) return "蓝叠";
            if (low.contains("microvirt") || low.contains("memu")) return "逍遥";
            if (low.contains("genymotion")) return "genymotion";
            if (low.contains("vbox") || low.contains("virtualbox")
                    || low.contains("goldfish") || low.contains("ranchu")) return "sdk-emulator";
            if (peek.length() < 200) peek.append(k).append('=').append(v).append(';');
        }
        Log.d(TAG, "brandByProperties peek: " + peek);
        return "";
    }

    private static boolean checkIsNotRealPhone() {
        String cpuInfo = getCachedCpuInfo();
        return cpuInfo.contains("intel") || cpuInfo.contains("amd");
    }

    private static String getCachedCpuInfo() {
        String cached = sCachedCpuInfo;
        if (cached != null) return cached;
        synchronized (EmulatorCheckUtil.class) {
            if (sCachedCpuInfo != null) return sCachedCpuInfo;
            sCachedCpuInfo = readCpuInfoInternal();
            return sCachedCpuInfo;
        }
    }

    private static String readCpuInfoInternal() {
        // /proc/cpuinfo 可直接读，避免依赖 fork/exec（Android 15 部分镜像限制 sh 子进程）
        String direct = readProcFile("/proc/cpuinfo");
        if (!TextUtils.isEmpty(direct)) return direct.toLowerCase();
        String viaShell = CommandUtil.getSingleInstance().exec("cat /proc/cpuinfo");
        return viaShell == null ? "" : viaShell.toLowerCase();
    }

    /** 无权限/无 exec 场景下读取内核伪文件，失败返回空串。 */
    static String readProcFile(String path) {
        java.io.FileInputStream in = null;
        try {
            File f = new File(path);
            if (!f.exists() || !f.canRead()) return "";
            in = new java.io.FileInputStream(f);
            byte[] buf = new byte[8192];
            StringBuilder sb = new StringBuilder();
            int n;
            int limit = 256 * 1024;
            while ((n = in.read(buf)) > 0) {
                sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                if (sb.length() > limit) break;
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
        }
    }

    private static final String[] known_pipes = {"/dev/socket/qemud", "/dev/qemu_pipe"};
    private static boolean checkPipes() {
        for (String pipes : known_pipes) {
            File qemu_socket = new File(pipes);
            if (qemu_socket.exists()) {
                Log.v("Result:", "Find pipes!");
                return true;
            }
        }
        Log.i(TAG, "Not Find pipes!");
        return false;
    }

    private static String getCpuInfo() {
        String cached = sCachedAbiStr;
        if (cached != null) return cached;
        synchronized (EmulatorCheckUtil.class) {
            if (sCachedAbiStr != null) return sCachedAbiStr;
            String[] abis;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                abis = Build.SUPPORTED_ABIS;
            } else {
                abis = new String[]{Build.CPU_ABI, Build.CPU_ABI2};
            }
            StringBuilder abiStr = new StringBuilder();
            for (String abi : abis) { abiStr.append(abi); abiStr.append(','); }
            sCachedAbiStr = abiStr.toString();
            return sCachedAbiStr;
        }
    }

    private static boolean isYeShenEmulator() {
        String abiStr = getCpuInfo();
        if (abiStr != null && abiStr.length() > 0) {
            boolean isSupportX86 = abiStr.contains("x86_64") || abiStr.contains("x86");
            boolean isSupportArm = abiStr.contains("armeabi") || abiStr.contains("armeabi-v7a") || abiStr.contains("arm64-v8a");
            return isSupportX86 && isSupportArm;
        }
        return false;
    }

    /** 主 ABI 为 x86/x86_64 且无 ARM 翻译层时，真机概率极低（雷电9/MuMu12 常见）。 */
    private static boolean isX86AbiDevice() {
        String abiStr = getCpuInfo();
        return !TextUtils.isEmpty(abiStr) && (abiStr.contains("x86_64") || abiStr.contains("x86"));
    }

    /**
     * x86 虚拟机的 /proc/cpuinfo 带 model name / vendor_id 行；
     * ARM 真机则带 `Hardware`/`Processor`。据此识别 hypervisor 痕迹。
     */
    private static String checkCpuVendorSignature() {
        String cpuInfo = getCachedCpuInfo();
        if (TextUtils.isEmpty(cpuInfo)) return "";
        if (cpuInfo.contains("genuineintel") || cpuInfo.contains("authenticamd")) return "x86vendor";
        if (cpuInfo.contains("hypervisor")) return "hypervisor";
        if (cpuInfo.contains("kvm") || cpuInfo.contains("virtual")) return "virtual-cpu";
        return "";
    }

    /** vbox/qemu/MuMu 专属设备节点：Android 12+ 镜像不再提供 qemu_pipe，需扩集合。 */
    private static String checkVirtualDeviceNodes() {
        for (String node : VIRTUAL_DEVICE_NODES) {
            try {
                if (new File(node).exists()) return node;
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private static final String[] VIRTUAL_DEVICE_NODES = {
            "/dev/vboxguest", "/dev/vboxuser", "/dev/vboxvgavbox", "/dev/vboxvga",
            "/dev/dri/card0", "/dev/ashmem", "/dev/qemu_pipes", "/dev/goldfish_pipe",
            "/dev/socket/qemud", "/dev/mumu_pipe", "/dev/nemu_pipe", "/dev/ldmnq_pipe",
            "/sys/module/vboxguest", "/sys/module/vboxsf", "/sys/module/virtio_net",
            "/sys/bus/virtio", "/sys/class/vbox", "/sys/hypervisor/type",
            "/proc/tty/driver/vbox", "/dev/socket/genyd"
    };

    /** 模拟器镜像常残留的驱动/库文件（vboxfs、houdini、nemu、nox 等）。 */
    private static String checkDriverFiles() {
        for (String f : VIRTUAL_DRIVER_FILES) {
            try {
                if (new File(f).exists()) return f;
            } catch (Exception ignored) {
            }
        }
        return "";
    }

    private static final String[] VIRTUAL_DRIVER_FILES = {
            "/system/lib/libc_malloc_debug_qemu.so", "/system/lib64/libc_malloc_debug_qemu.so",
            "/system/bin/qemu-props", "/system/bin/qemu_props",
            "/system/lib/libdroid4x.so", "/system/lib64/libdroid4x.so",
            "/system/lib/libnoxspeedup.so", "/system/lib/libnox.so",
            "/system/bin/ldinit", "/system/bin/ldvssh", "/system/bin/nox-prop",
            "/system/lib/libmumudetect.so", "/system/bin/mumu-prop", "/system/bin/nemu",
            "/system/lib/hw/gralloc.mumu.so", "/system/lib/hw/gralloc.nox.so",
            "/system/lib/hw/gralloc.ld.so", "/system/lib/hw/camera.vbox86.so",
            "/system/lib/hw/sensors.vbox86.so", "/system/lib/hw/gps.noah.so",
            "/system/lib/arm/libhoudini.so", "/system/lib64/arm64/libhoudini.so",
            "/system/lib/libndk_translation.so", "/system/bin/arm_translation",
            "/system/etc/gps.conf"
    };

    /** 无通话能力：真机手机必有 FEATURE_TELEPHONY（平板/盒子除外，权重保持 1）。 */
    private static boolean hasNoTelephonyFeature(Context context) {
        try {
            return !context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
        } catch (Exception e) {
            return false;
        }
    }

    /** 电量信息全为 0 / 恒定 100%：多数模拟器不模拟真实电池曲线。 */
    private static boolean hasNoRealBattery(Context context) {
        try {
            Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) return false;
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int present = battery.getIntExtra(BatteryManager.EXTRA_PRESENT, -1);
            int plugged = battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1);
            if (level <= 0 || scale <= 0) return true;
            // 长期恒定 100% 且始终接电：典型模拟器电源桩
            return present == 1 && level == scale && plugged != 0;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 内核版本串残留模拟器构建机/驱动痕迹：真机为厂商 SDK 构建，
     * MuMu/雷电等的 `/proc/version` 常带 `nemu`/`vbox`/`builder@` 等特征。
     */
    private static String checkKernelVersion() {
        String ver = readProcFile("/proc/version").toLowerCase();
        if (TextUtils.isEmpty(ver)) ver = CommandUtil.getSingleInstance().exec("cat /proc/version");
        if (TextUtils.isEmpty(ver)) return "";
        String low = ver.toLowerCase();
        for (String kw : KERNEL_EMU_KEYWORDS) {
            if (low.contains(kw)) return kw;
        }
        return "";
    }

    private static final String[] KERNEL_EMU_KEYWORDS = {
            "nemu", "mumu", "vbox", "virtualbox", "goldfish", "ranchu",
            "qemu", "genymotion", "nox", "ldmnq", "microvirt"
    };

    /** GPU/EGL 实现名：模拟器使用 emulation/angle/swiftshader/ranchu 等软渲染栈。 */
    private static String checkGpuStack() {
        String[] keys = {"ro.hardware.egl", "ro.hardware.vulkan", "ro.gfx.driver.1",
                "ro.hardware.gralloc", "ro.hardware.hwcomposer", "ro.hardware.audio.primary"};
        for (String k : keys) {
            String v = getProperty(k);
            if (TextUtils.isEmpty(v)) continue;
            String low = v.toLowerCase();
            if (low.contains("emulation") || low.contains("swiftshader") || low.contains("angle")
                    || low.contains("ranchu") || low.contains("goldfish") || low.contains("vbox")
                    || low.contains("cancro") || low.contains("nemutouch") || low.contains("mumu")) {
                return k + "=" + v;
            }
        }
        return "";
    }

    /** 传感器名/厂商机带虚拟化字样：真机为 AKM/Bosch/ST/Qualcomm 等真实厂商。 */
    private static String checkSensorSignatures(Context context) {
        try {
            SensorManager sm = (SensorManager) context.getSystemService(SENSOR_SERVICE);
            if (sm == null) return "";
            List<Sensor> all = sm.getSensorList(Sensor.TYPE_ALL);
            if (all == null || all.isEmpty()) return "no_sensors";
            for (Sensor s : all) {
                String name = (s.getName() == null ? "" : s.getName().toLowerCase());
                String vendor = (s.getVendor() == null ? "" : s.getVendor().toLowerCase());
                String joined = name + " " + vendor;
                if (joined.contains("qemu") || joined.contains("goldfish") || joined.contains("vbox")
                        || joined.contains("virtual") || joined.contains("nemu") || joined.contains("mumu")
                        || joined.contains("genymotion") || joined.contains("simulated")
                        || vendor.equals("the android project") || vendor.contains("android opensource")) {
                    return s.getName() + "/" + s.getVendor();
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "checkSensorSignatures error", e);
        }
        return "";
    }

    /** MuMu12/雷电9 常带 `init.svc.*` 专属守护进程项，真机不存在。 */
    private static String checkInitServices() {
        String out = CommandUtil.getSingleInstance().exec("getprop | grep -E 'init.svc\\.[^]]*(nemu|mumu|nox|ldmnq|vbox|qemu|goldfish|genyd|microvirt)'");
        if (TextUtils.isEmpty(out)) return "";
        String first = out.split("\n")[0].trim();
        return first.length() > 120 ? first.substring(0, 120) : first;
    }

    /** 输入设备枚举：模拟器常注册 nemu/mumu/vbox 命名的虚拟 HID。 */
    private static boolean hasOnlyVirtualInput(Context context) {
        try {
            android.hardware.input.InputManager im =
                    (android.hardware.input.InputManager) context.getSystemService(Context.INPUT_SERVICE);
            if (im == null) return false;
            int[] ids = im.getInputDeviceIds();
            if (ids == null || ids.length == 0) return false;
            for (int id : ids) {
                InputDevice device = im.getInputDevice(id);
                if (device == null) continue;
                String name = device.getName() == null ? "" : device.getName().toLowerCase();
                if (name.contains("vbox") || name.contains("qemu") || name.contains("goldfish")
                        || name.contains("nemuvinput") || name.contains("nemu")
                        || name.contains("mumu") || name.contains("nox")
                        || name.contains("virtio") || name.contains("virtualbox")) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "hasOnlyVirtualInput error", e);
        }
        return false;
    }

    /**
     * /proc/mounts 中的虚拟化特征：vboxsf 共享目录、9p/virtio 挂载、
     *模拟器常见只读 system overlay 与 `emu/` 路径。
     */
    private static String checkMountTable() {
        String mounts = CommandUtil.getSingleInstance().exec("cat /proc/mounts");
        if (TextUtils.isEmpty(mounts)) mounts = CommandUtil.getSingleInstance().exec("mount");
        if (TextUtils.isEmpty(mounts)) return "";
        String low = mounts.toLowerCase();
        if (low.contains("vboxsf") || low.contains("vboxguest")) return "vboxsf";
        if (low.contains("9p") || low.contains("virtio")) return "virtio-9p";
        if (low.contains("/dev/socket/qemud")) return "qemud-mount";
        if (low.contains("nemu") || low.contains("mumu")) return "nemu-share";
        if (low.contains("noxcache") || low.contains("/nox")) return "nox-share";
        if (low.contains("docker") || low.contains("overlay on / type")) return "container";
        return "";
    }
}
