package com.buswake.app;

import android.app.Application;

public final class BusWakeApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();
        AppLog.init(this);
        AppLog.i("App", "Application started");
        final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            AppLog.e("Crash", "Uncaught on thread " + thread.getName(), throwable);
            if (previous != null) {
                previous.uncaughtException(thread, throwable);
            }
        });
    }
}
