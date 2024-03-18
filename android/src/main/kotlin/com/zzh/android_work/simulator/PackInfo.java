package com.zzh.android_work.simulator;


public class PackInfo {
    private static final String JSON_PACKAGENAME = "packageName";
    private static final String JSON_APPNAME = "appName";
    private String packageName;

    private String appName;

    public PackInfo() {
    }


    @Override
    public String toString() {
        return "{\"packageName\":\"" + packageName + '\"' +
                ",\"appName\":\"" + appName + "\"}";
    }

    public String getPackageName() {
        return packageName;
    }

    public void setPackageName(String packageName) {
        this.packageName = packageName;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }
}
