package com.zzh.device_doctor.simulator;
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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
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
            "com.mumu.launcher", "com.netease.mumu.cloner", "com.netease.mumu.player",
            "com.mumu.store", "com.mumu.market", "com.mumu.shell", "com.nemu.launcher",
            "com.netease.mumu", "com.mumu.superuser", "com.mumu.ime",
            "com.ami.duosupdater.ui", "com.ami.launchmetro", "com.ami.syncduosservices",
            "com.bluestacks.home", "com.bluestacks.windowsfilemanager", "com.bluestacks.settings",
            "com.bluestacks.bluestackslocationprovider", "com.bluestacks.appsettings", "com.bluestacks.bstfolder",
            "com.bluestacks.BstCommandProcessor", "com.bluestacks.s2p", "com.bluestacks.setup", "com.bluestacks.appmart",
            "com.bluestacks.nxt", "com.bluestacks.filemanager", "com.bluestacks.servicestray",
            "com.kaopu001.tiantianserver", "com.kpzs.helpercenter", "com.kaopu001.tiantianime",
            "com.android.development_settings", "com.android.development", "com.android.customlocale2",
            "com.genymotion.superuser", "com.genymotion.clipboardproxy",
            "com.uc.xxzs.keyboard", "com.uc.xxzs",
            "com.blue.huang17.agent", "com.blue.huang17.launcher", "com.blue.huang17.ime",
            "com.microvirt.guide", "com.microvirt.market", "com.microvirt.memuime",
            "com.microvirt.launcher", "com.memu.launcher",
            "cn.itools.vm.launcher", "cn.itools.vm.proxy", "cn.itools.vm.softkeyboard", "cn.itools.avdmarket",
            "com.syd.IME", "com.bignox.app.store.hd", "com.bignox.launcher", "com.bignox.app.phone",
            "com.bignox.app.noxservice", "com.nox.launcher", "com.android.noxpush", "com.haimawan.push", "me.haima.helpcenter",
            "com.windroy.launcher", "com.windroy.superuser", "com.windroy.ime",
            "com.android.flysilkworm", "com.android.emu.inputservice", "com.tiantian.ime",
            "me.le8.androidassist", "com.vphone.helper", "com.vphone.launcher",
            "com.duoyi.giftcenter.giftcenter",
            "com.ldmnq.launcher", "com.ld.ldplayer", "com.ld.store", "com.ld.market",
            "com.redfinger.cloudphone", "com.kaopu001.tiantiandailyupdate",
            "com.vmos.pro", "com.vmos.helper", "com.x8.sandbox", "com.f1.player",
            "com.tiantian.vm", "com.phonecleanos.device"
    };
    /** Fuzzy substrings matched against the full installed-package list. Catches renamed MuMu12/LD9/BlueStacks5 helpers. */
    private static final String[] FUZZY_PKG_KEYWORDS = {
            "mumu", ".nemu", "nemu.", "bignox", "com.nox", "noxapp", "ldplayer", "ldmnq",
            "com.ld.", "microvirt", "memu", "bluestacks", "bstk", "genymotion", "vphone",
            "redfinger", "tiantian", "kaopu", "kpzs", "windroy", "haimawan", "me.haima",
            "duoyi", "uc.xxzs", "itools.vm", "itools.avd", "me.le8", "vmos", "x8.sandbox",
            "f1.player", "flysilkworm", "emu.inputservice", "amiduos", "duosupdater",
            "launchmetro", "syncduosservices", "hd-service", "noxpush"
    };
    private static final String[] EMULATOR_PATHS = {
            "/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq", "/system/lib/libc_malloc_debug_qemu.so",
            "/system/lib64/libc_malloc_debug_qemu.so", "/sys/qemu_trace", "/system/bin/qemu-props",
            "/system/bin/nox-prop", "/system/bin/ld-prop", "/system/bin/mumu-prop",
            "/dev/socket/qemud", "/dev/qemu_pipe", "/dev/socket/baseband_genyd", "/dev/socket/genyd",
            "/dev/vboxguest", "/dev/vboxuser", "/dev/mumu_pipe", "/dev/nox_pipe", "/dev/ld_pipe",
            "/sys/android_emu", "/sys/mumu", "/proc/mumu", "/dev/socket/mumu",
            "/system/lib/libhoudini.so", "/system/lib64/libhoudini.so", "/system/arm/libc.so",
            "/mnt/nemu", "/data/nemu", "/sdcard/nemu", "/system/etc/nemu.conf"
    };
    private static final String[] EMULATOR_FILES = {
            "/data/data/com.android.flysilkworm", "/data/data/com.bluestacks.filemanager",
            "/data/data/com.mumu.launcher", "/data/data/com.netease.mumu.cloner",
            "/data/data/com.bignox.app.store.hd", "/data/data/com.ldmnq.launcher"
    };

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
            String json = readStreamUtf8(is);
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

    private static String readStreamUtf8(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString(StandardCharsets.UTF_8.name());
    }

    public static List<String> getSimulatorInfo(Context context) {
        List<String> simulatorMaps = new ArrayList<>();
        try {
            for (String hit : getInstalledSimulatorPackages(context)) {
                String brand = brandOf(hit);
                if (!TextUtils.isEmpty(brand) && !simulatorMaps.contains(brand)) simulatorMaps.add(brand);
            }
            if (simulatorMaps.isEmpty()) {
                for (String brand : loadApps(context)) {
                    if (!simulatorMaps.contains(brand)) simulatorMaps.add(brand);
                }
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
            LinkedHashSet<String> hits = new LinkedHashSet<>();
            Set<String> visible;
            try {
                visible = queryVisiblePackages(context.getPackageManager());
            } catch (Exception e) {
                Log.w(TAG, "queryVisiblePackages error", e);
                visible = Collections.emptySet();
            }
            try {
                Set<String> allPkgs = getAllPackageNames(context);
                // 1) 已知指纹包：逐包探测。Android 11+ 的 getInstalledPackages 会被
                //    包可见性规则裁剪，必须靠 Manifest <queries> + getPackageInfo 才能确认。
                for (String pkg : allPkgs) {
                    if (visible.contains(pkg) || canResolvePackage(context, pkg)) hits.add(pkg);
                }
                // 2) 模糊关键字扫描：兼容被改名/新增的宿主辅助包（如 MuMu12 组件）
                for (String pkg : visible) {
                    if (matchesFuzzyKeyword(pkg)) hits.add(pkg);
                }
                // 3) 设备节点/镜像路径探测始终执行：包完全不可见时（云手机、精简镜像）
                //    仍能给出证据，且与包命中互相印证。
                for (String f : collectProbeFiles(context)) {
                    try { if (new File(f).exists()) hits.add(f); } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                Log.w(TAG, "getInstalledSimulatorPackages error", e);
            }
            sPkgCache = new ArrayList<>(hits);
            sPkgCacheAt = System.currentTimeMillis();
            return new ArrayList<>(hits);
        }
    }

    /** 列表中是否存在包名命中（非路径），供 getSimulatorBrand 判定用。 */
    private static String brandOf(List<String> list) {
        for (String raw : list) {
            if (TextUtils.isEmpty(raw) || raw.startsWith("/")) continue;
            String brand = brandOf(raw);
            if (!TextUtils.isEmpty(brand)) return brand;
        }
        for (String raw : list) {
            if (TextUtils.isEmpty(raw) || !raw.startsWith("/")) continue;
            // /data/data/<pkg>、/dev/mumu_pipe 这类路径也携带品牌信息
            String brand = brandOf(raw);
            if (!TextUtils.isEmpty(brand)) return brand;
        }
        return "";
    }

    /** 可见包名集合：bulk 结果 + 老设备兜底（部分 ROM 对 getInstalledPackages 抛异常）。 */
    private static Set<String> queryVisiblePackages(PackageManager pm) {
        Set<String> all = new HashSet<>();
        List<PackageInfo> installed;
        try {
            installed = pm.getInstalledPackages(0);
        } catch (Exception e) {
            Log.w(TAG, "getInstalledPackages error", e);
            return all;
        }
        if (installed != null) {
            for (PackageInfo p : installed) {
                if (p.packageName != null) all.add(p.packageName);
            }
        }
        return all;
    }

    private static boolean canResolvePackage(Context context, String pkg) {
        try {
            context.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        } catch (Exception ignored) {
            return false;
        }
    }

    static boolean matchesFuzzyKeyword(String packageName) {
        if (TextUtils.isEmpty(packageName)) return false;
        String low = packageName.toLowerCase();
        for (String kw : FUZZY_PKG_KEYWORDS) {
            if (low.contains(kw)) return true;
        }
        return false;
    }

    /** 内置 + assets + 自定义 的文件路径探针集合。 */
    static Set<String> collectProbeFiles(Context context) {
        Set<String> files = new LinkedHashSet<>();
        Collections.addAll(files, EMULATOR_FILES);
        Collections.addAll(files, EMULATOR_PATHS);
        files.addAll(getAssetFiles(context));
        synchronized (EXTRA_LOCK) {
            for (String e : sExtraPackages) if (e.startsWith("/")) files.add(e);
        }
        return files;
    }

    public static List<String> loadApps(Context context) {
        List<String> list = new ArrayList<>();
        if (context == null) return list;
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN, null);
            intent.addCategory(Intent.CATEGORY_LAUNCHER);
            List<ResolveInfo> apps = context.getPackageManager().queryIntentActivities(intent, 0);
            for (ResolveInfo info : apps) {
                if (info.activityInfo == null || TextUtils.isEmpty(info.activityInfo.packageName)) continue;
                String brand = brandOf(info.activityInfo.packageName);
                if (!TextUtils.isEmpty(brand) && !list.contains(brand)) list.add(brand);
            }
        } catch (Exception e) {
            Log.w(TAG, "loadApps error", e);
        }
        return list;
    }

    public static String getSimulatorBrand(List<String> list) {
        if (list == null || list.isEmpty()) return "";
        // 精确/可信路径命中优先：/dev/vboxguest、/data/data/com.mumu.launcher 等直接定品牌
        String strong = brandOf(list);
        if (!TextUtils.isEmpty(strong)) return strong;
        for (String raw : list) {
            if (TextUtils.isEmpty(raw)) continue;
            String lowerRaw = raw.toLowerCase();
            if (lowerRaw.startsWith("/")) continue;
            if (matchesFuzzyKeyword(raw)) return fuzzyBrandGuess(raw);
        }
        return "";
    }

    /** 模糊包名 → 品牌，仅用于兜底展示，调用方仍按 +3 计分。 */
    private static String fuzzyBrandGuess(String packageName) {
        String brand = brandOf(packageName);
        return TextUtils.isEmpty(brand) ? packageName : brand;
    }

    /** 单个包名/路径 → 模拟器品牌；未识别返回空串。 */
    static String brandOf(String raw) {
        if (TextUtils.isEmpty(raw)) return "";
        String pkgName = raw.toLowerCase();
        // MuMu 系：netease.mumu 后缀限定，避免 `emu` 误伤（如 com.example.myemu）
        if (pkgName.contains("mumu") || pkgName.contains("netease.mumu")) return "mumu";
        if (pkgName.contains(".nemu") || pkgName.contains("nemu.")) return "mumu";
        if (pkgName.contains("amiduos") || pkgName.contains("duosupdater")
                || pkgName.contains("launchmetro") || pkgName.contains("syncduosservices")) return "AMIDuOS";
        if (pkgName.contains("bluestacks") || pkgName.contains("bstk") || pkgName.contains("hd-service")
                || pkgName.contains("bstfolder") || pkgName.contains("s2p") || pkgName.contains("noxpush")) return "蓝叠";
        if (pkgName.contains("kaopu001") || pkgName.contains("tiantian")) return "天天";
        if (pkgName.contains("kpzs")) return "靠谱助手";
        if (pkgName.contains("genymotion")) {
            if (Build.MODEL != null && Build.MODEL.contains("iTools")) return "iTools";
            if (Build.MODEL != null && Build.MODEL.contains("ChangWan")) return "畅玩";
            return "genymotion";
        }
        if (pkgName.contains("ldmnq") || pkgName.contains("ldplayer")
                || pkgName.contains("flysilkworm") || pkgName.contains("ld.")) return "雷电";
        if (pkgName.contains("uc.xxzs")) return "uc";
        if (pkgName.contains("microvirt") || pkgName.contains("memu")) return "逍遥";
        if (pkgName.contains("itools")) return "itools";
        if (pkgName.contains("syd")) return "手游";
        if (pkgName.contains("bignox") || pkgName.contains("nox")) return "夜神";
        if (pkgName.contains("haimawan") || pkgName.contains("me.haima")) return "海马";
        if (pkgName.contains("windroy")) return "windroy";
        if (pkgName.contains("le8")) return "le8";
        if (pkgName.contains("vphone")) return "vphone";
        if (pkgName.contains("duoyi")) return "多益";
        if (pkgName.contains("redfinger")) return "红手";
        if (pkgName.contains("vmos") || pkgName.contains("x8.sandbox") || pkgName.contains("f1.player")) return "VMOS云手机";
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
