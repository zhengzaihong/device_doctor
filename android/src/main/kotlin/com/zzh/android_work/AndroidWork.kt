package com.zzh.android_work

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaDrm
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import java.util.UUID


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


    fun getIMEINo(): String? {
        var imeiNumber: String? = ""
        val telephonyManager =
            activity!!.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        if (ContextCompat.checkSelfPermission(
                activity!!,
                Manifest.permission.READ_PHONE_STATE
            ) !== PackageManager.PERMISSION_GRANTED
        ) {
            return Manifest.permission.READ_PHONE_STATE
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                imeiNumber = getDeviceUniqueID()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (telephonyManager?.imei != null) {
                    imeiNumber = telephonyManager.imei
                }
            } else {
                if (telephonyManager?.deviceId != null) {
                    imeiNumber = telephonyManager.deviceId
                }
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