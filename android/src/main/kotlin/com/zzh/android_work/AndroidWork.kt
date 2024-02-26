package com.zzh.android_work

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log
import com.zzh.android_work.utils.DeviceCheckUtils

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

    open fun getRunningAppProcesses(): MutableList<String> {
        if (!checkContext()) {
            return mutableListOf()
        }
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.KITKAT_WATCH) {
            return getActivePackages()
        }
        return getActivePackagesCompat()
    }

    private fun getActivePackagesCompat(): MutableList<String> {
        val activityManager: ActivityManager =
            applicationContext!!.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val taskInfo: List<ActivityManager.RunningTaskInfo> = activityManager.getRunningTasks(100)
        val activePackages: MutableList<String> = mutableListOf()
        taskInfo.forEach {
            activePackages.add((if (it.topActivity?.packageName == null) "" else it.topActivity!!.packageName))
        }
        return activePackages
    }

    private fun getActivePackages(): MutableList<String> {
        val activityManager: ActivityManager =
            applicationContext!!.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val activePackages: MutableList<String> = mutableListOf()
        val processInfos: List<ActivityManager.RunningAppProcessInfo> =
            activityManager.runningAppProcesses
        for (processInfo in processInfos) {
            if (processInfo.importance === ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                activePackages.addAll(processInfo.pkgList)
            }
        }
        return activePackages
    }

    open fun checkDeviceIsEmulator(): Int {
        return  DeviceCheckUtils.checkDeviceIsEmulator(applicationContext)
    }
}