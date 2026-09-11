package com.zzh.android_work.simulator;

import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public class CommandUtil {
    private static final String TAG = "CommandUtil";
    private static final long EXEC_TIMEOUT_MS = 1200;
    private static final int BUFFER_SIZE = 512;

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
        BufferedOutputStream out = null;
        BufferedInputStream in = null;
        InputStream err = null;
        Thread errDrainer = null;
        try {
            process = Runtime.getRuntime().exec("sh");
            out = new BufferedOutputStream(process.getOutputStream());
            in = new BufferedInputStream(process.getInputStream());
            err = process.getErrorStream();
            errDrainer = drainAsync(err);

            out.write(command.getBytes(StandardCharsets.UTF_8));
            out.write('\n');
            out.flush();
            try { out.close(); } catch (IOException ignored) {}
            out = null;

            boolean finished;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                finished = process.waitFor(EXEC_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } else {
                finished = waitForLegacy(process, EXEC_TIMEOUT_MS);
            }
            if (!finished) {
                Log.w(TAG, "exec timeout: " + command);
                try { process.destroy(); } catch (Exception ignored) {}
                return null;
            }
            try { errDrainer.join(300); } catch (InterruptedException ignored) {}

            return readFully(in);
        } catch (Exception e) {
            Log.w(TAG, "exec error: " + command, e);
            return null;
        } finally {
            if (out != null) try { out.close(); } catch (IOException ignored) {}
            if (in != null) try { in.close(); } catch (IOException ignored) {}
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

    private static String readFully(BufferedInputStream in) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        StringBuilder sb = new StringBuilder();
        while (true) {
            int read = in.read(buffer);
            if (read == -1) break;
            if (read > 0) sb.append(new String(buffer, 0, read, StandardCharsets.UTF_8));
            if (in.available() == 0 && read < BUFFER_SIZE) break;
        }
        return sb.toString();
    }
}
