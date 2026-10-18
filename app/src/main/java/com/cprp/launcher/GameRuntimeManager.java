package com.cprp.launcher;

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Process;
import android.system.Os;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.tukaani.xz.XZInputStream;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class GameRuntimeManager {

    public static final String ASSETS_SUBDIR = "game_runtime";
    public static final String ASSET_IMAGEFS = "imagefs.txz";
    public static final String DIR_IMAGEFS = "imagefs";
    public static final String DIR_WINEPREFIX = "wineprefix";
    public static final String REL_WINE_BIN = "opt/wine/bin/wine";

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

    public enum BinaryType {
        WINDOWS_PE,
        LINUX_ELF,
        UNKNOWN,
        NOT_FOUND
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

    public static BinaryType inspectBinaryType(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return BinaryType.NOT_FOUND;
        }

        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] header = new byte[4];
            int read = fis.read(header);
            if (read >= 2 && header[0] == 'M' && header[1] == 'Z') {
                return BinaryType.WINDOWS_PE;
            } else if (read >= 4 && (header[0] & 0xFF) == 0x7F && header[1] == 'E' && header[2] == 'L' && header[3] == 'F') {
                return BinaryType.LINUX_ELF;
            }
        } catch (IOException ignored) {}

        return BinaryType.UNKNOWN;
    }

    public static boolean isSystemImageInstalled(Context context) {
        File wineBin = new File(context.getFilesDir(), DIR_IMAGEFS + "/" + REL_WINE_BIN);
        return wineBin.exists() && wineBin.isFile();
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

    public static void installSystemImage(Context context, InstallCallback callback) {
        File destDir = new File(context.getFilesDir(), DIR_IMAGEFS);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        if (callback != null) {
            callback.onProgress("Membuka paket subsistem Wine WOW64 (imagefs.txz)...");
        }

        try (InputStream rawIn = context.getAssets().open(ASSET_IMAGEFS);
             BufferedInputStream bufIn = new BufferedInputStream(rawIn);
             XZInputStream xzIn = new XZInputStream(bufIn);
             TarArchiveInputStream tarIn = new TarArchiveInputStream(xzIn)) {

            byte[] buffer = new byte[16384];
            TarArchiveEntry entry;
            int fileCount = 0;

            while ((entry = tarIn.getNextEntry()) != null) {
                String entryName = entry.getName();
                if (entryName.startsWith("./")) {
                    entryName = entryName.substring(2);
                }
                if (entryName.isEmpty()) continue;

                File targetFile = new File(destDir, entryName);

                if (entry.isDirectory()) {
                    targetFile.mkdirs();
                } else {
                    File parent = targetFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }

                    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                        int read;
                        while ((read = tarIn.read(buffer)) != -1) {
                            fos.write(buffer, 0, read);
                        }
                        fos.flush();
                    }

                    if ((entry.getMode() & 0111) != 0 ||
                            entryName.contains("bin/") ||
                            entryName.endsWith(".so") ||
                            entryName.contains(".so.")) {
                        targetFile.setExecutable(true, false);
                        targetFile.setReadable(true, false);
                    }

                    fileCount++;
                    if (fileCount % 250 == 0 && callback != null) {
                        callback.onProgress("Mengekstrak berkas subsistem (" + fileCount + " berkas)...");
                    }
                }
            }

            File wineBin = new File(destDir, REL_WINE_BIN);
            if (wineBin.exists()) {
                wineBin.setExecutable(true, false);
            }

            patchWineBinaries(context);

            if (callback != null) {
                callback.onSuccess("Subsistem Wine WOW64 berhasil dipasang (" + fileCount + " berkas).");
            }

        } catch (IOException e) {
            if (callback != null) {
                callback.onError("Gagal mengekstrak imagefs: " + e.getMessage());
            }
        }
    }

    public static void patchFileBinaryString(File file, byte[] oldBytes, byte[] newBytes) {
        if (file == null || !file.exists() || !file.canWrite()) return;
        try {
            byte[] fileData = new byte[(int) file.length()];
            try (FileInputStream fis = new FileInputStream(file)) {
                int read = 0;
                while (read < fileData.length) {
                    int r = fis.read(fileData, read, fileData.length - read);
                    if (r == -1) break;
                    read += r;
                }
            }
            boolean modified = false;
            int limit = fileData.length - oldBytes.length;
            for (int i = 0; i <= limit; i++) {
                boolean match = true;
                for (int j = 0; j < oldBytes.length; j++) {
                    if (fileData[i + j] != oldBytes[j]) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    System.arraycopy(newBytes, 0, fileData, i, newBytes.length);
                    modified = true;
                    i += oldBytes.length - 1;
                }
            }
            if (modified) {
                try (FileOutputStream fos = new FileOutputStream(file)) {
                    fos.write(fileData);
                    fos.flush();
                }
            }
        } catch (Exception ignored) {}
    }

    public static void patchWineBinaries(Context context) {
        File filesDir = context.getFilesDir();
        File tmpDir = new File(filesDir, "tmp");
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }
        tmpDir.setReadable(true, true);
        tmpDir.setWritable(true, true);
        tmpDir.setExecutable(true, true);

        File wineserverBin = new File(filesDir, DIR_IMAGEFS + "/opt/wine/bin/wineserver");
        File ntdllBin = new File(filesDir, DIR_IMAGEFS + "/opt/wine/lib/wine/x86_64-unix/ntdll.so");

        String appTmpWine = filesDir.getAbsolutePath() + "/tmp/.wine-%u";
        byte[] oldWs = "/data/data/com.winlator/files/rootfs/tmp/.wine-%u\0".getBytes(StandardCharsets.US_ASCII);
        byte[] newWs = new byte[oldWs.length];
        byte[] appTmpBytes = (appTmpWine + "\0").getBytes(StandardCharsets.US_ASCII);
        if (appTmpBytes.length <= oldWs.length) {
            System.arraycopy(appTmpBytes, 0, newWs, 0, appTmpBytes.length);
            patchFileBinaryString(wineserverBin, oldWs, newWs);
        }

        String appTmpServer = filesDir.getAbsolutePath() + "/tmp/.wine-%u/server-%s";
        byte[] oldNt = "/data/data/com.winlator/files/rootfs/tmp/.wine-%u/server-%s\0".getBytes(StandardCharsets.US_ASCII);
        byte[] newNt = new byte[oldNt.length];
        byte[] appNtBytes = (appTmpServer + "\0").getBytes(StandardCharsets.US_ASCII);
        if (appNtBytes.length <= oldNt.length) {
            System.arraycopy(appNtBytes, 0, newNt, 0, appNtBytes.length);
            patchFileBinaryString(ntdllBin, oldNt, newNt);
        }

        byte[] oldRoot = "/data/data/com.winlator/files/rootfs\0".getBytes(StandardCharsets.US_ASCII);
        byte[] newRoot = new byte[oldRoot.length];
        byte[] appFilesBytes = (filesDir.getAbsolutePath() + "\0").getBytes(StandardCharsets.US_ASCII);
        if (appFilesBytes.length <= oldRoot.length) {
            System.arraycopy(appFilesBytes, 0, newRoot, 0, appFilesBytes.length);
            patchFileBinaryString(ntdllBin, oldRoot, newRoot);
        }
    }

    public static void configureWineEnvironment(Context context, GameConfig config) {
        File filesDir = context.getFilesDir();
        File imageFsDir = new File(filesDir, DIR_IMAGEFS);
        File winePrefix = new File(filesDir, DIR_WINEPREFIX);
        if (!winePrefix.exists()) {
            winePrefix.mkdirs();
        }
        File tmpDir = new File(filesDir, "tmp");
        if (!tmpDir.exists()) {
            tmpDir.mkdirs();
        }
        tmpDir.setReadable(true, true);
        tmpDir.setWritable(true, true);
        tmpDir.setExecutable(true, true);

        patchWineBinaries(context);

        try {
            Os.setenv("WINEPREFIX", winePrefix.getAbsolutePath(), true);
            Os.setenv("WINEDLLPATH", new File(imageFsDir, "opt/wine/lib/wine").getAbsolutePath(), true);
            Os.setenv("WINEDATADIR", new File(imageFsDir, "opt/wine/share/wine").getAbsolutePath(), true);
            Os.setenv("WINESERVER", new File(imageFsDir, "opt/wine/bin/wineserver").getAbsolutePath(), true);
            Os.setenv("TMPDIR", tmpDir.getAbsolutePath(), true);
            Os.setenv("WINEDEBUG", "+err", true);
            Os.setenv("BOX64_PATH", new File(imageFsDir, "opt/wine/bin").getAbsolutePath(), true);
            Os.setenv("BOX64_LD_LIBRARY_PATH", new File(imageFsDir, "opt/wine/lib/wine/x86_64-unix").getAbsolutePath(), true);
            Os.setenv("BOX64_LOG", "1", true);
            Os.setenv("BOX64_SHOWSEGV", "1", true);
            Os.setenv("BOX64_MMAP32", "1", true);
            Os.setenv("BOX64_DYNAREC_SAFEFLAGS", "2", true);
        } catch (Exception ignored) {}
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

    public static String[] buildWineLaunchArguments(Context context, GameConfig config) {
        String coreBinary = Process.is64Bit() ? "box64" : "box86";
        File wineBin = new File(context.getFilesDir(), DIR_IMAGEFS + "/" + REL_WINE_BIN);
        File gameExe = new File(config.getGameDir(), GameConfig.EXE_NAME);

        List<String> argsList = new ArrayList<>();
        argsList.add(coreBinary);
        argsList.add(wineBin.getAbsolutePath());
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
