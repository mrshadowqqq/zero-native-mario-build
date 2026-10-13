package com.wither.nsmbw;

import android.content.Context;
import android.os.Process;
import android.util.Log;
import java.io.File;

/** Lightweight, optional developer-only sampler for the actual SDL native game thread. */
public final class ZeroBootProbe {
    private static final String TAG = "ZERO-BOOTPROBE";
    private static final boolean AVAILABLE;
    static {
        boolean loaded = false;
        try {
            System.loadLibrary("zero_bootprobe");
            loaded = true;
        } catch (Throwable error) {
            Log.e(TAG, "Unable to load standalone boot probe", error);
        }
        AVAILABLE = loaded;
    }
    private ZeroBootProbe() {}

    private static native void nativeStart(String path);
    private static native void nativeStop();

    public static void start(Context context) {
        if (!AVAILABLE || context == null) return;
        try {
            File root = context.getExternalFilesDir(null);
            if (root == null) root = context.getFilesDir();
            File logDir = new File(root, "Logs");
            if (!logDir.isDirectory() && !logDir.mkdirs()) return;
            File report = new File(logDir, "zero-bootprobe-pid" + Process.myPid() + ".log");
            nativeStart(report.getAbsolutePath());
        } catch (Throwable error) {
            Log.e(TAG, "Native boot probe startup failed", error);
        }
    }

    public static void stop() {
        if (!AVAILABLE) return;
        try { nativeStop(); }
        catch (Throwable error) { Log.e(TAG, "Native boot probe cleanup failed", error); }
    }
}
