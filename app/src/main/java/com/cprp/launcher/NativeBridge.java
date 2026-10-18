package com.cprp.launcher;

import android.util.Log;

public final class NativeBridge {
    private static final String TAG = "CPRP_BRIDGE";
    private static boolean isLoaded = false;
    private static String loadError = null;

    static {
        try {
            System.loadLibrary("box86");
            isLoaded = true;
            Log.i(TAG, "libbox86.so loaded successfully via JNI.");
        } catch (Throwable t) {
            isLoaded = false;
            loadError = t.getMessage();
            Log.e(TAG, "Failed to load libbox86.so: " + loadError, t);
        }
    }

    private NativeBridge() {}

    public static boolean isLoaded() {
        return isLoaded;
    }

    public static String getLoadError() {
        return loadError;
    }

    public static native int startCore(String[] args);
}
