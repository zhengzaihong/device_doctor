package com.zzh.android_work.simulator;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Tools {

    private static final String TAG = "Tools";
    private static final long PKG_CACHE_TTL_MS = 5 * 60 * 1000L;
    private static final Object PKG_LOCK = new Object();
    private static volatile List<String> sPkgCache = null;
    private static volatile long sPkgCacheAt = 0;

    private static final Object EXTRA_LOCK = new Object();
    private static final Set<String> sExtraPackages = new HashSet<>();

    private static volatile Set<String> sAssetPackages = null;
    private static volatile Set<String> sAssetFiles = null;
    private static final Object ASSET_LOCK = new Object();

    private static final String[] PKG_NAMES = {
            "com.mumu.launcher", "com.ami.duosupdater.ui", "com.ami.launchmetro", "com.ami.syncduosservices",
            "com.bluestacks.home", "com.bluestacks.windowsfilemanager", "com.bluestacks.settings",
            "com.bluestacks.bluestackslocationprovider", "com.bluestacks.appsettings", "com.bluestacks.bstfolder",
            "com.bluestacks.BstCommandProcessor", "com.bluestacks.s2p", "com.bluestacks.setup", "com.bluestacks.appmart",
            "com.kaopu001.tiantianserver", "com.kpzs.helpercenter", "com.kaopu001.tiantianime",
            "com.android.development_settings", "com.android.development", "com.android.customlocale2",
            "com.genymotion.superuser", "com.genymotion.clipboardproxy",
            "com.uc.xxzs.keyboard", "com.uc.xxzs",
            "com.blue.huang17.agent", "com.blue.huang17.launcher", "com.blue.huang17.ime",
            "com.microvirt.guide", "com.microvirt.market", "com.microvirt.memuime",
            "cn.itools.vm.launcher", "cn.itools.vm.proxy", "cn.itools.vm.softkeyboard", "cn.itools.avdmarket",
            "com.syd.IME", "com.bignox.app.store.hd", "com.bignox.launcher", "com.bignox.app.phone",
            "com.bignox.app.noxservice", "com.android.noxpush", "com.haimawan.push", "me.haima.helpcenter",
            "com.windroy.launcher", "com.windroy.superuser", "com.windroy.ime",
            "com.android.flysilkworm", "com.android.emu.inputservice", "com.tiantian.ime",
            "com.microvirt.launcher", "me.le8.androidassist", "com.vphone.helper", "com.vphone.launcher",
            "com.duoyi.giftcenter.giftcenter",
            "com.ldmnq.launcher", "com.ld.ldplayer", "com.netease.mumu.cloner", "com.bluestacks.nxt"
    };
    private static final String[] EMULATOR_PATHS = {
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq", "/system/lib/libc_malloc_debug_qemu.so",
            "/sys/qemu_trace", "/system/bin/qemu-props", "/dev/socket/qemud", "/dev/qemu_pipe",
            "/dev/socket/baseband_genyd", "/dev/socket/genyd"
    };
    private static final String[] EMULATOR_FILES = {"/data/data/com.android.flysilkworm", "/data/data/com.bluestacks.filemanager"};

    public static void addCustomPackages(List<String> packages) {
        if (packages == null || packages.isEmpty()) return;
        synchronized (EXTRA_LOCK) {
            for (String p : packages) {
                if (!TextUtils.isEmpty(p)) sExtraPackages.add(p.trim());
            }
        }
        clearCache();
    }

    public static void clearCache() {
        synchronized (PKG_LOCK) {
            sPkgCache = null;
            sPkgCacheAt = 0;
        }
    }

    static Set<String> getAllPackageNames(Context context) {
        Set<String> all = new HashSet<>();
        Collections.addAll(all, PKG_NAMES);
        synchronized (EXTRA_LOCK) { all.addAll(sExtraPackages); }
        Set<String> assetPkgs = getAssetPackages(context);
        if (assetPkgs != null) all.addAll(assetPkgs);
        return all;
    }

    private static Set<String> getAssetPackages(Context context) {
        if (sAssetPackages != null) return sAssetPackages;
        synchronized (ASSET_LOCK) {
            if (sAssetPackages != null) return sAssetPackages;
            loadAssetConfig(context);
            return sAssetPackages != null ? sAssetPackages : Collections.emptySet();
        }
    }

    private static Set<String> getAssetFiles(Context context) {
        if (sAssetFiles != null) return sAssetFiles;
        synchronized (ASSET_LOCK) {
            if (sAssetFiles != null) return sAssetFiles;
            loadAssetConfig(context);
            return sAssetFiles != null ? sAssetFiles : Collections.emptySet();
        }
    }

    private static void loadAssetConfig(Context context) {
        Set<String> pkgs = new HashSet<>();
        Set<String> files = new HashSet<>();
        if (context == null) { sAssetPackages = pkgs; sAssetFiles = files; return; }
        InputStream is = null;
        try {
            is = context.getAssets().open("emulator_config.json");
            byte[] bytes = new byte[is.available()];
            int read = is.read(bytes);
            String json = new String(bytes, 0, read, StandardCharsets.UTF_8);
            JSONObject obj = new JSONObject(json);
            JSONArray ja = obj.optJSONArray("packages");
            if (ja != null) for (int i = 0; i < ja.length(); i++) {
                String s = ja.optString(i, null);
                if (!TextUtils.isEmpty(s)) pkgs.add(s.trim());
            }
            JSONArray jf = obj.optJSONArray("files");
            if (jf != null) for (int i = 0; i < jf.length(); i++) {
                String s = jf.optString(i, null);
                if (!TextUtils.isEmpty(s)) files.add(s.trim());
            }
        } catch (Exception e) {
            Log.w(TAG, "load emulator_config.json failed, using built-in", e);
        } finally {
            if (is != null) try { is.close(); } catch (Exception ignored) {}
        }
        sAssetPackages = pkgs;
        sAssetFiles = files;
    }

    public static List<String> getSimulatorInfo(Context context) {
        List<String> simulatorMaps = new ArrayList<>();
        try {
            List<String> pathList = getInstalledSimulatorPackages(context);
            String brand = getSimulatorBrand(pathList);
            if (TextUtils.isEmpty(brand)) {
                List<String> list = loadApps(context);
                if (!list.isEmpty()) simulatorMaps.add(list.get(0));
            } else {
                simulatorMaps.add(brand);
            }
        } catch (Exception e) {
            Log.w(TAG, "getSimulatorInfo error", e);
        }
        return simulatorMaps;
    }

    static List<String> getInstalledSimulatorPackages(Context context) {
        if (context == null) return new ArrayList<>();
        List<String> cached = sPkgCache;
        long at = sPkgCacheAt;
        if (cached != null && System.currentTimeMillis() - at < PKG_CACHE_TTL_MS) {
            return new ArrayList<>(cached);
        }
        synchronized (PKG_LOCK) {
            cached = sPkgCache;
            at = sPkgCacheAt;
            if (cached != null && System.currentTimeMillis() - at < PKG_CACHE_TTL_MS) {
                return new ArrayList<>(cached);
            }
            ArrayList<String> hits = new ArrayList<>();
            try {
                PackageManager pm = context.getPackageManager();
                Set<String> allPkgs = getAllPackageNames(context);
                List<PackageInfo> installed = null;
                try {
                    installed = pm.getInstalledPackages(0);
                } catch (Exception e) {
                    Log.w(TAG, "getInstalledPackages error, fallback to per-package query", e);
                }
                if (installed != null && !installed.isEmpty()) {
                    Set<String> installedSet = new HashSet<>(installed.size());
                    for (PackageInfo p : installed) {
                        if (p.packageName != null) installedSet.add(p.packageName);
                    }
                    for (String pkg : allPkgs) {
                        if (installedSet.contains(pkg)) hits.add(pkg);
                    }
                } else {
                    for (String pkg : allPkgs) {
                        try {
                            pm.getPackageInfo(pkg, 0);
                            hits.add(pkg);
                        } catch (PackageManager.NameNotFoundException ignored) {
                        }
                    }
                }
                if (hits.isEmpty()) {
                    Set<String> files = new HashSet<>();
                    Collections.addAll(files, EMULATOR_FILES);
                    Collections.addAll(files, EMULATOR_PATHS);
                    files.addAll(getAssetFiles(context));
                    synchronized (EXTRA_LOCK) {
                        for (String e : sExtraPackages) if (e.startsWith("/")) files.add(e);
                    }
                    for (String f : files) {
                        try { if (new File(f).exists()) hits.add(f); } catch (Exception ignored) {}
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "getInstalledSimulatorPackages error", e);
            }
            sPkgCache = new ArrayList<>(hits);
            sPkgCacheAt = System.currentTimeMillis();
            return new ArrayList<>(hits);
        }
    }

    public static List<String> loadApps(Context context) {
        List<String> list = new ArrayList<>();
        if (context == null) return list;
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> apps = context.getPackageManager().queryIntentActivities(intent, 0);
            for (ResolveInfo info : apps) {
                if (info.activityInfo == null) continue;
                String packageName = info.activityInfo.packageName;
                if (!TextUtils.isEmpty(packageName) && packageName.contains("bluestacks")) {
                    list.add("蓝叠");
                    return list;
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "loadApps error", e);
        }
        return list;
    }

    public static String getSimulatorBrand(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        String pkgName = list.get(0).toLowerCase();
        if (pkgName.contains("mumu")) return "mumu";
        if (pkgName.contains("ami")) return "AMIDuOS";
        if (pkgName.contains("bluestacks")) return "蓝叠";
        if (pkgName.contains("kaopu001") || pkgName.contains("tiantian")) return "天天";
        if (pkgName.contains("kpzs")) return "靠谱助手";
        if (pkgName.contains("genymotion")) {
            if (Build.MODEL.contains("iTools")) return "iTools";
            if (Build.MODEL.contains("ChangWan")) return "畅玩";
            return "genymotion";
        }
        if (pkgName.contains("ldmnq") || pkgName.contains("ldplayer") || pkgName.contains("ld.")) return "雷电";
        if (pkgName.contains("uc")) return "uc";
        if (pkgName.contains("blue")) return "blue";
        if (pkgName.contains("microvirt")) return "逍遥";
        if (pkgName.contains("itools")) return "itools";
        if (pkgName.contains("syd")) return "手游岛";
        if (pkgName.contains("bignox")) return "夜神";
        if (pkgName.contains("haimawan") || pkgName.contains("haima")) return "海马玩";
        if (pkgName.contains("windroy")) return "windroy";
        if (pkgName.contains("flysilkworm")) return "雷电";
        if (pkgName.contains("emu")) return "emu";
        if (pkgName.contains("le8")) return "le8";
        if (pkgName.contains("vphone")) return "vphone";
        if (pkgName.contains("duoyi")) return "多益";
        if (pkgName.contains("redfinger")) return "红手指";
        return "";
    }

    public static boolean isSimulator(Context context) {
        try {
            if (context == null) return false;
            boolean canResolveIntent = false;
            try {
                Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:123456"));
                canResolveIntent = intent.resolveActivity(context.getPackageManager()) != null;
            } catch (Exception e) {
                Log.w(TAG, "resolveActivity error", e);
            }
            String fingerprint = Build.FINGERPRINT != null ? Build.FINGERPRINT : "";
            String model = Build.MODEL != null ? Build.MODEL : "";
            String manufacturer = Build.MANUFACTURER != null ? Build.MANUFACTURER : "";
            String brand = Build.BRAND != null ? Build.BRAND : "";
            String device = Build.DEVICE != null ? Build.DEVICE : "";
            String product = Build.PRODUCT != null ? Build.PRODUCT : "";
            String serial = "";
            try { serial = Build.SERIAL != null ? Build.SERIAL : ""; } catch (Exception e) { Log.w(TAG, "Build.SERIAL error", e); }
            String operatorName = "";
            try {
                TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
                if (tm != null && tm.getNetworkOperatorName() != null) operatorName = tm.getNetworkOperatorName();
            } catch (SecurityException e) {
                Log.w(TAG, "getNetworkOperatorName permission", e);
            } catch (Exception e) {
                Log.w(TAG, "getNetworkOperatorName error", e);
            }
            return fingerprint.startsWith("generic")
                    || fingerprint.toLowerCase().contains("vbox")
                    || fingerprint.toLowerCase().contains("test-keys")
                    || model.contains("google_sdk")
                    || model.contains("Emulator")
                    || serial.equalsIgnoreCase("unknown")
                    || serial.equalsIgnoreCase("android")
                    || model.contains("Android SDK built for x86")
                    || manufacturer.contains("Genymotion")
                    || (brand.startsWith("generic") && device.startsWith("generic"))
                    || "google_sdk".equals(product)
                    || operatorName.toLowerCase().equals("android")
                    || !canResolveIntent;
        } catch (Exception e) {
            Log.w(TAG, "isSimulator error", e);
            return false;
        }
    }
}
