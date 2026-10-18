package com.cprp.launcher;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Process;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

public class GameRuntimeManager {

    public static final String ASSETS_SUBDIR = "game_runtime";
    public static final String[] RUNTIME_FILES = {
            "bass.dll",
            "d3d9.dll",
            "d3dx9_25.dll",
            "samp.dll",
            "samp.saa"
    };

    public enum VerificationStatus {
        SUCCESS,
        EMPTY_PATH,
        NOT_FOUND,
        NOT_A_DIRECTORY,
        PERMISSION_DENIED,
        MISSING_EXE
    }

    public interface InstallCallback {
        void onProgress(String message);
        void onFileCompleted(String filename, int current, int total);
        void onSuccess(String message);
        void onError(String errorMessage);
    }

    public static VerificationStatus verifyGameDirectory(String gameDirPath) {
        if (gameDirPath == null || gameDirPath.trim().isEmpty()) {
            return VerificationStatus.EMPTY_PATH;
        }

        File dir = new File(gameDirPath.trim());
        if (!dir.exists()) {
            return VerificationStatus.NOT_FOUND;
        }

        if (!dir.isDirectory()) {
            return VerificationStatus.NOT_A_DIRECTORY;
        }

        if (!dir.canRead()) {
            return VerificationStatus.PERMISSION_DENIED;
        }

        File exeFile = new File(dir, GameConfig.EXE_NAME);
        if (!exeFile.exists() || !exeFile.isFile()) {
            return VerificationStatus.MISSING_EXE;
        }

        return VerificationStatus.SUCCESS;
    }

    public static void installRuntimeAssets(Context context, File targetDir, InstallCallback callback) {
        if (targetDir == null || !targetDir.exists()) {
            if (callback != null) {
                callback.onError("Direktori target tidak valid.");
            }
            return;
        }

        AssetManager assetManager = context.getAssets();
        byte[] buffer = new byte[8192];
        int totalFiles = RUNTIME_FILES.length;

        for (int i = 0; i < totalFiles; i++) {
            String filename = RUNTIME_FILES[i];
            String assetPath = ASSETS_SUBDIR + "/" + filename;
            File destinationFile = new File(targetDir, filename);

            if (callback != null) {
                callback.onProgress("Mengekstrak " + filename + " (" + (i + 1) + "/" + totalFiles + ")...");
            }

            InputStream in = null;
            OutputStream out = null;
            try {
                in = assetManager.open(assetPath);
                out = new FileOutputStream(destinationFile);
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                out.flush();

                if (callback != null) {
                    callback.onFileCompleted(filename, i + 1, totalFiles);
                }
            } catch (IOException e) {
                if (callback != null) {
                    callback.onError("Gagal menyalin " + filename + ": " + e.getMessage());
                }
                return;
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignored) {}
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) {}
                }
            }
        }

        if (callback != null) {
            callback.onSuccess("Semua komponen runtime berhasil disalin.");
        }
    }

    public static String[] buildLaunchArguments(GameConfig config) {
        String coreBinary = Process.is64Bit() ? "box64" : "box86";
        File gameExe = new File(config.getGameDir(), GameConfig.EXE_NAME);

        List<String> argsList = new ArrayList<>();
        argsList.add(coreBinary);
        argsList.add(gameExe.getAbsolutePath());
        argsList.add("-c");
        argsList.add("-h");
        argsList.add(config.getServerIp());
        argsList.add("-p");
        argsList.add(String.valueOf(config.getServerPort()));
        argsList.add("-n");
        argsList.add(config.getNickname());

        return argsList.toArray(new String[0]);
    }
}
