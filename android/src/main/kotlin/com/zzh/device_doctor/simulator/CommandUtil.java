package com.zzh.device_doctor.simulator;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class CommandUtil {
    private static final String TAG = "CommandUtil";
    private static final long EXEC_TIMEOUT_MS = 1200;
    private static final int BUFFER_SIZE = 4096;

    private CommandUtil() {}

    private static class SingletonHolder {
        private static final CommandUtil INSTANCE = new CommandUtil();
    }

    public static final CommandUtil getSingleInstance() {
        return SingletonHolder.INSTANCE;
    }

    public String getProperty(String propName) {
        try {
            Object v = Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class)
                    .invoke(null, propName);
            if (v instanceof String) return (String) v;
            return null;
        } catch (Exception e) {
            Log.w(TAG, "getProperty error: " + propName, e);
            return null;
        }
    }

    public String exec(String command) {
        if (command == null || command.isEmpty()) return null;
        Process process = null;
        InputStream err = null;
        Thread errDrainer = null;
        ReaderThread outReader = null;
        try {
            String[] argv = {"/system/bin/sh", "-c", command};
            process = Runtime.getRuntime().exec(argv);
            err = process.getErrorStream();
            outReader = new ReaderThread(new BufferedInputStream(process.getInputStream()));
            outReader.setDaemon(true);
            outReader.start();
            errDrainer = drainAsync(err);

            boolean finished;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                finished = process.waitFor(EXEC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } else {
                finished = waitForLegacy(process, EXEC_TIMEOUT_MS);
            }
            if (!finished) {
                Log.w(TAG, "exec timeout: " + command);
                try { process.destroy(); } catch (Exception ignored) {}
                outReader.interrupt();
                return null;
            }
            outReader.join(400);
            if (errDrainer != null) errDrainer.join(300);
            return outReader.text();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.w(TAG, "exec interrupted: " + command, e);
            return null;
        } catch (Exception e) {
            Log.w(TAG, "exec error: " + command, e);
            return null;
        } finally {
            if (outReader != null) { try { outReader.interrupt(); } catch (Exception ignored) {} }
            if (err != null) try { err.close(); } catch (IOException ignored) {}
            if (process != null) { try { process.destroy(); } catch (Exception ignored) {} }
            if (errDrainer != null) { try { errDrainer.interrupt(); } catch (Exception ignored) {} }
        }
    }

    private static Thread drainAsync(InputStream err) {
        Thread t = new Thread(() -> {
            byte[] buf = new byte[BUFFER_SIZE];
            try { while (err.read(buf) != -1) { /* discard */ } } catch (IOException ignored) {}
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static boolean waitForLegacy(Process process, long timeoutMs) throws InterruptedException {
        Thread waiter = new Thread(() -> {
            try { process.waitFor(); } catch (InterruptedException ignored) {}
        });
        waiter.setDaemon(true);
        waiter.start();
        waiter.join(timeoutMs);
        if (waiter.isAlive()) {
            waiter.interrupt();
            try { process.destroy(); } catch (Exception ignored) {}
            return false;
        }
        return true;
    }

    private static class ReaderThread extends Thread {
        private final InputStream src;
        private final ByteArrayOutputStream buf = new ByteArrayOutputStream();
        ReaderThread(InputStream src) { this.src = src; }
        @Override public void run() {
            byte[] tmp = new byte[BUFFER_SIZE];
            try {
                int n;
                while ((n = src.read(tmp)) != -1) {
                    if (n > 0) buf.write(tmp, 0, n);
                }
            } catch (IOException ignored) {}
        }
        String text() {
            try { return buf.toString(StandardCharsets.UTF_8.name()); }
            catch (Exception e) { return new String(buf.toByteArray(), StandardCharsets.UTF_8); }
        }
    }

    /** One-shot `getprop` dump parsed into a map; null when shell unavailable. */
    public Map<String, String> getAllProperties() {
        String out = exec("getprop");
        if (out == null || out.isEmpty()) return null;
        Map<String, String> map = new HashMap<>();
        for (String line : out.split("\n")) {
            line = line.trim();
            // Format: [key]: [value]
            if (line.length() < 5 || line.charAt(0) != '[') continue;
            int keyEnd = line.indexOf("]:");
            if (keyEnd <= 1) continue;
            String key = line.substring(1, keyEnd);
            int valStart = line.indexOf('[', keyEnd);
            int valEnd = line.lastIndexOf(']');
            if (valStart < 0 || valEnd <= valStart) continue;
            map.put(key, line.substring(valStart + 1, valEnd));
        }
        return map;
    }
}
