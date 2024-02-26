package com.zzh.android_work.utils

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.zzh.android_work.MethodCallHandlerImpl

/**
* create_user: zhengzaihong
* email:1096877329@qq.com
* create_date: 2024/2/26
* create_time: 16:31
* describe: 检查设备是否是模拟器
*/
object DeviceCheckUtils {
    fun checkDeviceIsEmulator(context: Context?,bt:Int = 0): Int {
       var currentDoubt = bt
        if (context == null) {
            Log.e(MethodCallHandlerImpl.TAG, "check(), context is null!")
            return 5
        }

        //1.检查设备特征
        if(Build.FINGERPRINT.contains("generic")
            || Build.FINGERPRINT.contains("unknown")
            || Build.MODEL.contains("google_sdk")
            || Build.MODEL.contains("Emulator")
            || Build.MODEL.contains("Android SDK built for x86")
            || Build.MANUFACTURER.contains("Genymotion")
            || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
            || "google_sdk".equals(Build.PRODUCT)) {
            // 运行在模拟器上
            currentDoubt++
        }


        //2.
        val deviceModel = Build.MODEL
        if (deviceModel.contains("Emulator") || deviceModel.contains("VirtualDevice")) {
            // 运行在模拟器上
            currentDoubt++
        }

        //3.检查设备名称
        if(Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic")){
            // 运行在模拟器上
            currentDoubt++
            // running in emulator
        }

        //4.检查CPU架构
        if(Build.CPU_ABI.contains("google_sdk") || Build.CPU_ABI2.contains("google_sdk")){
            // 运行在模拟器上
            currentDoubt++
        }



        //5.检查设备厂商
        val deviceManufacturer = Build.MANUFACTURER
        if (deviceManufacturer.contains("Genymotion") || // Genymotion
            deviceManufacturer.contains("Google") ||  // Google
            deviceManufacturer.contains("ttvm") ||   //天天模拟器
            deviceManufacturer.contains("nox") ||    //夜神
            deviceManufacturer.contains("cancro") ||  //网易
            deviceManufacturer.contains("intel") ||   //逍遥
            deviceManufacturer.contains("vbox") ||    //腾讯
            deviceManufacturer.contains("android_x86")  //雷电
            ) {
            // 运行在模拟器上
            currentDoubt++
        }


        //6.检查序列号
        val deviceSerialNumber = Build.SERIAL
        if (deviceSerialNumber.contains("emulator") || deviceSerialNumber.contains("android_sdk") || Build.SERIAL.contains("unknown")) {
            // 运行在模拟器上
            currentDoubt++
        }


        //7.检查硬件功能
        val hasAccelerometer = PackageManager.FEATURE_SENSOR_ACCELEROMETER
        val packageManager = context.packageManager
        if (!packageManager.hasSystemFeature(hasAccelerometer)) {
            // 运行在模拟器上
            currentDoubt++
        }
        return  currentDoubt

    }
}