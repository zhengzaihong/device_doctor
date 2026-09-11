package com.zzh.android_work.simulator;

import static android.content.Context.SENSOR_SERVICE;
import static com.zzh.android_work.simulator.CheckResult.RESULT_EMULATOR;
import static com.zzh.android_work.simulator.CheckResult.RESULT_MAYBE_EMULATOR;
import static com.zzh.android_work.simulator.CheckResult.RESULT_UNKNOWN;
import static com.zzh.android_work.simulator.Tools.getInstalledSimulatorPackages;
import static com.zzh.android_work.simulator.Tools.getSimulatorBrand;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


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

        CheckResult flavorResult = checkFeaturesByFlavor();
        suspectCount += scoreIfSuspicious(flavorResult);

        CheckResult modelResult = checkFeaturesByModel();
        suspectCount += scoreIfSuspicious(modelResult);

        CheckResult manufacturerResult = checkFeaturesByManufacturer();
        suspectCount += scoreIfSuspicious(manufacturerResult);

        CheckResult boardResult = checkFeaturesByBoard();
        suspectCount += scoreIfSuspicious(boardResult);

        CheckResult platformResult = checkFeaturesByPlatform();
        suspectCount += scoreIfSuspicious(platformResult);

        CheckResult baseBandResult = checkFeaturesByBaseBand();
        if (baseBandResult.result != RESULT_UNKNOWN) {
            suspectCount += 2;
        }

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
        }

        try {
            if(!TextUtils.isEmpty(checkHasSimulatorMainPackage(context))){
                suspectCount+=3;
            }
        } catch (Exception e) {
            Log.w(TAG, "checkHasSimulatorMainPackage error", e);
        }
        if(checkIsNotRealPhone()){
            ++suspectCount;
        }
        if(checkPipes()){
            ++suspectCount;
        }
        if(isYeShenEmulator()){
            ++suspectCount;
        }

        CheckResult cgroupResult = checkFeaturesByCgroup();
        if (cgroupResult.result == RESULT_MAYBE_EMULATOR){
            ++suspectCount;
        }

        Map<String,Object> map = new HashMap<>();
        map.put("hardware",getProperty("ro.hardware"));
        map.put("hardwareHit", hardwareHit);
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
        return new CheckResult(RESULT_UNKNOWN, flavor);
    }

    private CheckResult checkFeaturesByModel() {
        String model = getProperty("ro.product.model");
        if (null == model) return new CheckResult(RESULT_MAYBE_EMULATOR, null);
        String tempValue = model.toLowerCase();
        if (tempValue.contains("google_sdk")) return new CheckResult(RESULT_EMULATOR, model);
        if (tempValue.contains("emulator")) return new CheckResult(RESULT_EMULATOR, model);
        if (tempValue.contains("android sdk built for x86")) return new CheckResult(RESULT_EMULATOR, model);
        return new CheckResult(RESULT_UNKNOWN, model);
    }

    private CheckResult checkFeaturesByManufacturer() {
        String manufacturer = getProperty("ro.product.manufacturer");
        if (null == manufacturer) return new CheckResult(RESULT_MAYBE_EMULATOR, null);
        String tempValue = manufacturer.toLowerCase();
        if (tempValue.contains("genymotion")) return new CheckResult(RESULT_EMULATOR, manufacturer);
        if (tempValue.contains("netease")) return new CheckResult(RESULT_EMULATOR, manufacturer);
        return new CheckResult(RESULT_UNKNOWN, manufacturer);
    }

    private CheckResult checkFeaturesByBoard() {
        String board = getProperty("ro.product.board");
        if (null == board) return new CheckResult(RESULT_MAYBE_EMULATOR, null);
        String tempValue = board.toLowerCase();
        if (tempValue.contains("android")) return new CheckResult(RESULT_EMULATOR, board);
        if (tempValue.contains("goldfish")) return new CheckResult(RESULT_EMULATOR, board);
        return new CheckResult(RESULT_UNKNOWN, board);
    }

    private CheckResult checkFeaturesByPlatform() {
        String platform = getProperty("ro.board.platform");
        if (null == platform) return new CheckResult(RESULT_MAYBE_EMULATOR, null);
        String tempValue = platform.toLowerCase();
        if (tempValue.contains("android")) return new CheckResult(RESULT_EMULATOR, platform);
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
        return new CheckResult(RESULT_UNKNOWN, filter);
    }

    public static String checkHasSimulatorMainPackage(Context context) {
        List<String> pathList = getInstalledSimulatorPackages(context);
        return getSimulatorBrand(pathList);
    }

    public static String isSimulatorHardware() {
        String hardware = getProperty("ro.hardware");
        if (TextUtils.isEmpty(hardware)) return "";
        String tempValue = hardware.toLowerCase();
        if(tempValue.startsWith("cancro")){
            return "MUMU模拟器";
        }else if(tempValue.contains("nox") || tempValue.contains("x86")){
            return "夜神模拟器";
        }else if(tempValue.contains("android_x86")){
            return "雷电模拟器";
        }
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
        String result = "";
        BufferedReader reader = null;
        Process process = null;
        try {
            String[] args = {"/system/bin/cat", "/proc/cpuinfo"};
            ProcessBuilder cmd = new ProcessBuilder(args);
            process = cmd.start();
            reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            try { reader.close(); } catch (IOException ignored) {}
            reader = null;
            boolean finished;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                finished = process.waitFor(800, TimeUnit.MILLISECONDS);
            } else {
                Thread t = new Thread(() -> { try { process.waitFor(); } catch (InterruptedException ignored) {} });
                t.setDaemon(true); t.start(); t.join(800);
                finished = !t.isAlive();
                if (!finished) t.interrupt();
            }
            if (!finished) { try { process.destroy(); } catch (Exception ignored) {} }
            result = sb.toString().toLowerCase();
        } catch (IOException ex) {
            Log.w(TAG, "readCpuInfo error", ex);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (reader != null) try { reader.close(); } catch (IOException ignored) {}
            if (process != null) try { process.destroy(); } catch (Exception ignored) {}
        }
        return result;
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
}
