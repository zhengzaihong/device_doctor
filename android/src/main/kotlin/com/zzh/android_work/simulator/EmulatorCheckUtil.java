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
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
* create_user: zhengzaihong
* email:1096877329@qq.com
* create_date: 2024/3/18
* create_time: 16:42
* describe: 检查是否是模拟器
*/
public class EmulatorCheckUtil {
    private EmulatorCheckUtil() {

    }

    private static class SingletonHolder {
        private static final EmulatorCheckUtil INSTANCE = new EmulatorCheckUtil();
    }

    public static final EmulatorCheckUtil getSingleInstance() {
        return SingletonHolder.INSTANCE;
    }

    public void readSysProperty(Context context, EmulatorCheckCallback callback) {
        if (context == null)
            throw new IllegalArgumentException("context must not be null");

        int suspectCount = 0;


        //检测渠道
        CheckResult flavorResult = checkFeaturesByFlavor();
        switch (flavorResult.result) {
            case RESULT_MAYBE_EMULATOR:
                ++suspectCount;
                break;
            case RESULT_EMULATOR:
                if (callback != null) callback.findEmulator("flavor = " + flavorResult.value);
                return ;
        }

        //检测设备型号
        CheckResult modelResult = checkFeaturesByModel();
        switch (modelResult.result) {
            case RESULT_MAYBE_EMULATOR:
                ++suspectCount;
                break;
            case RESULT_EMULATOR:
                if (callback != null) callback.findEmulator("model = " + modelResult.value);
                return ;
        }

        //检测硬件制造商
        CheckResult manufacturerResult = checkFeaturesByManufacturer();
        switch (manufacturerResult.result) {
            case RESULT_MAYBE_EMULATOR:
                ++suspectCount;
                break;
            case RESULT_EMULATOR:
                if (callback != null)
                    callback.findEmulator("manufacturer = " + manufacturerResult.value);
                return ;
        }

        //检测主板名称
        CheckResult boardResult = checkFeaturesByBoard();
        switch (boardResult.result) {
            case RESULT_MAYBE_EMULATOR:
                ++suspectCount;
                break;
            case RESULT_EMULATOR:
                if (callback != null) callback.findEmulator("board = " + boardResult.value);
                return ;
        }

        //检测主板平台
        CheckResult platformResult = checkFeaturesByPlatform();
        switch (platformResult.result) {
            case RESULT_MAYBE_EMULATOR:
                ++suspectCount;
                break;
            case RESULT_EMULATOR:
                if (callback != null) callback.findEmulator("platform = " + platformResult.value);
                return ;
        }

        //检测基带信息
        CheckResult baseBandResult = checkFeaturesByBaseBand();
        switch (baseBandResult.result) {
            case RESULT_MAYBE_EMULATOR:
                suspectCount += 2;//模拟器基带信息为null的情况概率相当大
                break;
            case RESULT_EMULATOR:
                if (callback != null) callback.findEmulator("baseBand = " + baseBandResult.value);
                return ;
        }

        //检测传感器数量
        int sensorNumber = getSensorNumber(context);
        if (sensorNumber <= 7) ++suspectCount;

        //检测是否支持闪光灯
        boolean supportCameraFlash = supportCameraFlash(context);
        if (!supportCameraFlash) ++suspectCount;
        //检测是否支持相机
        boolean supportCamera = supportCamera(context);
        if (!supportCamera) ++suspectCount;
        //检测是否支持蓝牙
        boolean supportBluetooth = supportBluetooth(context);
        if (!supportBluetooth) ++suspectCount;

        //检测光线传感器
        boolean hasLightSensor = hasLightSensor(context);
        if (!hasLightSensor) ++suspectCount;

        boolean hasGPS = supportGPS(context);
        if (!hasGPS) ++suspectCount;

        boolean hasTemperature = supportTemperature(context);
        if (!hasTemperature) ++suspectCount;

        boolean hasSensorLight = supportSensorLight(context);
        if (!hasSensorLight) ++suspectCount;

        if(Tools.isSimulator(context)){
            ++suspectCount;
        }
        /// 直接检查出是主流，模拟器
        if(!TextUtils.isEmpty(isSimulatorHardware())){
            suspectCount+=100;
        }

        if(!TextUtils.isEmpty(checkHasSimulatorMainPackage(context))){
            suspectCount+=3;
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

        //检测进程组信息
        CheckResult cgroupResult = checkFeaturesByCgroup();
        if (cgroupResult.result == RESULT_MAYBE_EMULATOR) ++suspectCount;
        if (callback != null) {
            Map<Object,Object> map = new HashMap<>();
            map.put("hardware",getProperty("ro.hardware"));
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
            callback.findEmulator(map);
        }
    }

    private static String getProperty(String propName) {
        String property = CommandUtil.getSingleInstance().getProperty(propName);
        return TextUtils.isEmpty(property) ? null : property;
    }

    /**
     * 特征参数-渠道
     *
     * @return 0表示可能是模拟器，1表示模拟器，2表示可能是真机
     */
    private CheckResult checkFeaturesByFlavor() {
        String flavor = getProperty("ro.build.flavor");
        if (null == flavor) return new CheckResult(RESULT_MAYBE_EMULATOR, null);
        int result;
        String tempValue = flavor.toLowerCase();
        if (tempValue.contains("vbox")) result = RESULT_EMULATOR;
        else if (tempValue.contains("sdk_gphone")) result = RESULT_EMULATOR;
        else result = RESULT_UNKNOWN;
        return new CheckResult(result, flavor);
    }

    /**
     * 特征参数-设备型号
     *
     * @return 0表示可能是模拟器，1表示模拟器，2表示可能是真机
     */
    private CheckResult checkFeaturesByModel() {
        String model = getProperty("ro.product.model");
        if (null == model) return new CheckResult(RESULT_MAYBE_EMULATOR, null);
        int result;
        String tempValue = model.toLowerCase();
        if (tempValue.contains("google_sdk")) result = RESULT_EMULATOR;
        else if (tempValue.contains("emulator")) result = RESULT_EMULATOR;
        else if (tempValue.contains("android sdk built for x86")) result = RESULT_EMULATOR;
        else result = RESULT_UNKNOWN;
        return new CheckResult(result, model);
    }

    /**
     * 特征参数-硬件制造商
     *
     * @return 0表示可能是模拟器，1表示模拟器，2表示可能是真机
     */
    private CheckResult checkFeaturesByManufacturer() {
        String manufacturer = getProperty("ro.product.manufacturer");
        if (null == manufacturer) return new CheckResult(RESULT_MAYBE_EMULATOR, null);
        int result;
        String tempValue = manufacturer.toLowerCase();
        if (tempValue.contains("genymotion")) result = RESULT_EMULATOR;
        else if (tempValue.contains("netease")) result = RESULT_EMULATOR;//网易MUMU模拟器
        else result = RESULT_UNKNOWN;
        return new CheckResult(result, manufacturer);
    }

    /**
     * 特征参数-主板名称
     *
     * @return 0表示可能是模拟器，1表示模拟器，2表示可能是真机
     */
    private CheckResult checkFeaturesByBoard() {
        String board = getProperty("ro.product.board");
        if (null == board) return new CheckResult(RESULT_MAYBE_EMULATOR, null);
        int result;
        String tempValue = board.toLowerCase();
        if (tempValue.contains("android")) result = RESULT_EMULATOR;
        else if (tempValue.contains("goldfish")) result = RESULT_EMULATOR;
        else result = RESULT_UNKNOWN;
        return new CheckResult(result, board);
    }

    /**
     * 特征参数-主板平台
     *
     * @return 0表示可能是模拟器，1表示模拟器，2表示可能是真机
     */
    private CheckResult checkFeaturesByPlatform() {
        String platform = getProperty("ro.board.platform");
        if (null == platform) return new CheckResult(RESULT_MAYBE_EMULATOR, null);
        int result;
        String tempValue = platform.toLowerCase();
        if (tempValue.contains("android")) result = RESULT_EMULATOR;
        else result = RESULT_UNKNOWN;
        return new CheckResult(result, platform);
    }

    /**
     * 特征参数-基带信息
     *
     * @return 0表示可能是模拟器，1表示模拟器，2表示可能是真机
     */
    private CheckResult checkFeaturesByBaseBand() {
        String baseBandVersion = getProperty("gsm.version.baseband");
        if (null == baseBandVersion) return new CheckResult(RESULT_MAYBE_EMULATOR, null);
        int result;
        if (baseBandVersion.contains("1.0.0.0")) result = RESULT_EMULATOR;
        else result = RESULT_UNKNOWN;
        return new CheckResult(result, baseBandVersion);
    }

    /**
     * 获取传感器数量
     */
    private int getSensorNumber(Context context) {
        SensorManager sm = (SensorManager) context.getSystemService(SENSOR_SERVICE);
        return sm.getSensorList(Sensor.TYPE_ALL).size();
    }


    /**
     * 是否支持相机
     */
    private boolean supportCamera(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    /**
     * 是否支持闪光灯
     */
    private boolean supportCameraFlash(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_FLASH);
    }

    /**
     * 是否支持蓝牙
     */
    private boolean supportBluetooth(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH);
    }

    /**
     * 是否支GPS
     */
    private boolean supportGPS(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS);
    }

    /**
     * 是否支温度传感器
     */
    private boolean supportTemperature(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_AMBIENT_TEMPERATURE);
    }


    /**
     * 是否支持光感
     */
    private boolean supportSensorLight(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_LIGHT);
    }

    /**
     * 判断是否存在光传感器来判断是否为模拟器
     * 部分真机也不存在温度和压力传感器。其余传感器模拟器也存在。
     *
     * @return false为模拟器
     */
    private boolean hasLightSensor(Context context) {
        SensorManager sensorManager = (SensorManager) context.getSystemService(SENSOR_SERVICE);
        Sensor sensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT); //光线传感器
        if (null == sensor) return false;
        else return true;
    }

    /**
     * 特征参数-进程组信息
     */
    private CheckResult checkFeaturesByCgroup() {
        String filter = CommandUtil.getSingleInstance().exec("cat /proc/self/cgroup");
        if (null == filter) return new CheckResult(RESULT_MAYBE_EMULATOR, null);
        return new CheckResult(RESULT_UNKNOWN, filter);
    }







    //检查是否包含模拟器进程包名
    public static String checkHasSimulatorMainPackage(Context context) {
        List pathList = getInstalledSimulatorPackages(context);
        return getSimulatorBrand(pathList);
    }

    public static String isSimulatorHardware() {
        String result = "";
        String hardware = getProperty("ro.hardware");
        String tempValue = hardware.toLowerCase();
        if(tempValue.startsWith("cancro")){
            result = "MUMU模拟器";
        }else if(tempValue.contains("nox") || tempValue.contains("x86")){
            result = "夜神模拟器";
        }else if(tempValue.contains("android_x86") || tempValue.contains("qcom")){
            result= "雷电模拟器";
        }
        return result;
    }



    /*
     *用途:根据CPU是否为电脑来判断是否为模拟器
     *返回:true 为模拟器
     */
    private static boolean checkIsNotRealPhone() {
        String cpuInfo = readCpuInfo();
        if ((cpuInfo.contains("intel") || cpuInfo.contains("amd"))) {
            return true;
        }
        return false;
    }

    /*
     *用途:根据CPU是否为电脑来判断是否为模拟器(子方法)
     *返回:String
     */
    private static String readCpuInfo() {
        String result = "";
        try {
            String[] args = {"/system/bin/cat", "/proc/cpuinfo"};
            ProcessBuilder cmd = new ProcessBuilder(args);

            Process process = cmd.start();
            StringBuffer sb = new StringBuffer();
            String readLine = "";
            BufferedReader responseReader = new BufferedReader(new InputStreamReader(process.getInputStream(), "utf-8"));
            while ((readLine = responseReader.readLine()) != null) {
                sb.append(readLine);
            }
            responseReader.close();
            result = sb.toString().toLowerCase();
        } catch (IOException ex) {
        }
        return result;
    }

    /*
     *用途:检测模拟器的特有文件
     *返回:true 为模拟器
     */
    private static String[] known_pipes = {"/dev/socket/qemud", "/dev/qemu_pipe"};
    private static boolean checkPipes() {
        for (int i = 0; i < known_pipes.length; i++) {
            String pipes = known_pipes[i];
            File qemu_socket = new File(pipes);
            if (qemu_socket.exists()) {
                Log.v("Result:", "Find pipes!");
                return true;
            }
        }
        Log.i("Result:", "Not Find pipes!");
        return false;
    }


    //****************适配夜神模拟器*******************
    //获取 cpu 信息
    private static String getCpuInfo() {
        String[] abis;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            abis = Build.SUPPORTED_ABIS;
        } else {
            abis = new String[]{Build.CPU_ABI, Build.CPU_ABI2};
        }
        StringBuilder abiStr = new StringBuilder();
        for (String abi : abis) {
            abiStr.append(abi);
            abiStr.append(',');
        }

        return abiStr.toString();
    }





    // 通过cpu判断是否模拟器 ,适配夜神
    private static boolean isYeShenEmulator() {
        String abiStr = getCpuInfo();
        if (abiStr != null && abiStr.length() > 0) {
            boolean isSupportX86 = false;
            boolean isSupportArm = false;

            if (abiStr.contains("x86_64") || abiStr.contains("x86")) {
                isSupportX86 = true;
            }
            if (abiStr.contains("armeabi") || abiStr.contains("armeabi-v7a") || abiStr.contains("arm64-v8a")) {
                isSupportArm = true;
            }
            if (isSupportX86 && isSupportArm) {
                //同时拥有X86和arm的判断为模拟器。
                return true;
            }
        }
        return false;
    }

}
