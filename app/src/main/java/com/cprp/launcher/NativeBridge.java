package com.cprp.launcher;

import android.os.Process;
import android.util.Log;

public final class NativeBridge {
    private static final String TAG = "CPRP_BRIDGE";
    private static boolean isLoaded = false;
    private static String loadedCore = null;
    private static String loadError = null;

    static {
        try {
            if (Process.is64Bit()) {
                System.loadLibrary("box64");
                loadedCore = "Box64 (arm64-v8a Native)";
                isLoaded = true;
                Log.i(TAG, "libbox64.so loaded successfully via JNI on 64-bit device.");
            } else {
                System.loadLibrary("box86");
                loadedCore = "Box86 (armeabi-v7a Native)";
                isLoaded = true;
                Log.i(TAG, "libbox86.so loaded successfully via JNI on 32-bit device.");
            }
        } catch (Throwable t) {
            isLoaded = false;
            loadError = t.getMessage();
            Log.e(TAG, "Failed to load native core: " + loadError, t);
        }
    }

    private NativeBridge() {}

    public static boolean isLoaded() {
        return isLoaded;
    }

    public static String getLoadedCore() {
        return loadedCore != null ? loadedCore : "Tidak diketahui";
    }

    public static String getLoadError() {
        return loadError;
    }

    public static native int startCore(String[] args);
    public static native int startCore64(String[] args);
    public static native String pollLogOutput();
}
