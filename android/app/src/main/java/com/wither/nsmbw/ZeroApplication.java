package com.wither.nsmbw;

import android.app.Application;

/** Process-wide Java crash capture, including the separate :game process. */
public final class ZeroApplication extends Application {
    @Override public void onCreate() {
        super.onCreate();
        ZeroDiagnostics.event(this, "PROCESS_START", getPackageName());
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            ZeroDiagnostics.failure(this, "UNCAUGHT_JAVA " + thread.getName(), error);
            if (previous != null) previous.uncaughtException(thread, error);
        });
    }
}
