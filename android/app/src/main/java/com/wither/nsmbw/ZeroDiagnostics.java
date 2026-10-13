package com.wither.nsmbw;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Local, opt-in-export diagnostics. Does not require READ_LOGS, root, or ADB.
 * Native runtime messages are read from the existing app-owned Logs directory.
 * We do NOT claim to see GPU frames or native stdout when the runtime did not write it.
 */
final class ZeroDiagnostics {
    private static final String TAG = "ZERO-DIAG";
    private static final long JAVA_LOG_LIMIT = 512L * 1024;
    private static final int MAX_REPORT_CHARS = 240000;
    private ZeroDiagnostics() {}

    private static String timestamp(long ms) {
        final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS 'UTC'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(ms));
    }

    private static File javaLog(Context c) {
        final File dir = new File(c.getFilesDir(), "zero-diagnostics");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        return new File(dir, "java-pid" + Process.myPid() + ".log");
    }

    static synchronized void event(Context c, String stage, String message) {
        final String safeMessage = (message == null ? "" : message.replace('\r', ' ').replace('\n', ' '));
        final String line = timestamp(System.currentTimeMillis()) + " pid=" + Process.myPid()
                + " " + stage + " " + safeMessage + "\n";
        Log.i(TAG, line.trim());
        try {
            final File target = javaLog(c);
            if (target.length() > JAVA_LOG_LIMIT) {
                final File previous = new File(target.getParentFile(), target.getName() + ".old");
                //noinspection ResultOfMethodCallIgnored
                previous.delete();
                //noinspection ResultOfMethodCallIgnored
                target.renameTo(previous);
            }
            try (FileOutputStream stream = new FileOutputStream(target, true)) {
                stream.write(line.getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not persist local diagnostics", e);
        }
    }

    static void failure(Context c, String stage, Throwable error) {
        event(c, stage, error == null ? "unknown" : Log.getStackTraceString(error));
    }

    private static void append(StringBuilder out, String text) {
        if (out.length() >= MAX_REPORT_CHARS) return;
        final int remaining = MAX_REPORT_CHARS - out.length();
        out.append(text, 0, Math.min(remaining, text.length()));
    }

    private static String tail(File file, int maxBytes) {
        try (RandomAccessFile in = new RandomAccessFile(file, "r")) {
            final long length = in.length();
            in.seek(Math.max(0, length - maxBytes));
            final byte[] data = new byte[(int) Math.min(length, maxBytes)];
            in.readFully(data);
            return new String(data, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "[Unable to read " + file.getName() + ": " + e.getMessage() + "]\n";
        }
    }

    private static List<File> filesIn(File directory, String suffix, int depth) {
        final List<File> out = new ArrayList<>();
        gather(directory, suffix, depth, out);
        out.sort((a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return out;
    }

    private static void gather(File dir, String suffix, int depth, List<File> output) {
        if (depth < 0 || !dir.isDirectory() || output.size() > 80) return;
        final File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) gather(file, suffix, depth - 1, output);
            else if (file.isFile() && (suffix.isEmpty() || file.getName().endsWith(suffix))) output.add(file);
        }
    }

    static String report(Context c) {
        final StringBuilder out = new StringBuilder();
        append(out, "ZERO Native Mario v2.8.1 diagnostic report\n");
        append(out, "Generated: " + timestamp(System.currentTimeMillis()) + "\n");
        append(out, "Device: " + Build.MANUFACTURER + " " + Build.MODEL
                + " / Android " + Build.VERSION.RELEASE + " / SDK " + Build.VERSION.SDK_INT + "\n");
        append(out, "ABIs: " + Arrays.toString(Build.SUPPORTED_ABIS) + "\n");
        append(out, "Vulkan feature declared: " + c.getPackageManager().hasSystemFeature("android.hardware.vulkan.level") + "\n");
        try {
            final PackageInfo info = c.getPackageManager().getPackageInfo(c.getPackageName(), 0);
            append(out, "Package version: " + info.versionName + "\n");
        } catch (Exception e) {
            append(out, "Package version unavailable: " + e.getMessage() + "\n");
        }
        final File game = AppFiles.gameDir(c);
        final File runtime = AppFiles.runtimeDir(c);
        append(out, "\n=== Resource checks (no file contents) ===\n");
        append(out, "Game files directory: " + new File(game, "files").isDirectory() + "\n");
        append(out, "Game main.dol: " + new File(game, "sys/main.dol").isFile() + "\n");
        append(out, "Game identity check: " + (AppFiles.gameProblem(game) == null ? "PASS SMNE01 Revision 2" : "not ready: " + AppFiles.gameProblem(game)) + "\n");
        for (String item : new String[]{"dsp_coef.bin", "cacert.pem", "initial_pipeline_cache.db", "wii_bootstrap"}) {
            final File path = new File(runtime, item);
            append(out, "Runtime " + item + ": " + (path.exists() ? "present" : "missing") + "\n");
        }
        try (java.util.zip.ZipFile installedApk = new java.util.zip.ZipFile(c.getApplicationInfo().sourceDir)) {
            append(out, "APK contains native main: " + (installedApk.getEntry("lib/arm64-v8a/libmain.so") != null) + "\n");
            append(out, "APK contains native C++: " + (installedApk.getEntry("lib/arm64-v8a/libc++_shared.so") != null) + "\n");
            append(out, "APK contains boot probe: " + (installedApk.getEntry("lib/arm64-v8a/libzero_bootprobe.so") != null) + "\n");
        } catch (Exception error) {
            append(out, "APK native entries inaccessible: " + error.getMessage() + "\n");
        }
        append(out, "\n=== Recent process exits (system-reported) ===\n");
        if (Build.VERSION.SDK_INT >= 30) {
            try {
                final ActivityManager manager = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
                final List<ApplicationExitInfo> exits = manager == null ? null : manager.getHistoricalProcessExitReasons(c.getPackageName(), 0, 8);
                if (exits != null && !exits.isEmpty()) {
                    for (ApplicationExitInfo entry : exits) {
                        append(out, timestamp(entry.getTimestamp()) + " " + entry.getProcessName()
                                + " reason=" + entry.getReason() + " status=" + entry.getStatus()
                                + " description=" + entry.getDescription() + "\n");
                    }
                } else append(out, "No recorded recent exits\n");
            } catch (Exception e) {
                append(out, "Exit history inaccessible: " + e.getMessage() + "\n");
            }
        }
        append(out, "\n=== Java startup and error checkpoints ===\n");
        final List<File> javaLogs = filesIn(new File(c.getFilesDir(), "zero-diagnostics"), ".log", 1);
        for (int i = 0; i < Math.min(javaLogs.size(), 6); i++) {
            append(out, "-- " + javaLogs.get(i).getName() + " --\n" + tail(javaLogs.get(i), 24000) + "\n");
        }
        if (javaLogs.isEmpty()) append(out, "No Java diagnostic logs found\n");
        append(out, "\n=== Native runtime console logs ===\n");
        final File logRoot = new File(AppFiles.dataDir(c), "Logs");
        final List<File> nativeLogs = filesIn(logRoot, "console.log", 3);
        for (int i = 0; i < Math.min(nativeLogs.size(), 4); i++) {
            final File log = nativeLogs.get(i);
            append(out, "-- " + log.getParentFile().getName() + "/console.log --\n" + tail(log, 40000) + "\n");
        }
        if (nativeLogs.isEmpty()) append(out, "No native console.log yet. This DOES NOT prove the native game ran.\n");
        append(out, "\n=== ZERO v2.8.1 native SDL-thread execution samples ===\n");
        final List<File> probeLogs = filesIn(logRoot, ".log", 1);
        int probeCount = 0;
        for (File f : probeLogs) {
            if (!f.getName().startsWith("zero-bootprobe-pid")) continue;
            append(out, "-- " + f.getName() + " --\n" + tail(f, 16000) + "\n");
            if (++probeCount >= 3) break;
        }
        if (probeCount == 0) append(out, "No native thread samples found (probe may not have loaded).\n");
        append(out, "\nGraphics frame submission is not yet instrumented in this build.\n");
        append(out, "Report excludes actual game file contents and native library bytes. Review before sharing.\n");
        return out.toString();
    }
}
