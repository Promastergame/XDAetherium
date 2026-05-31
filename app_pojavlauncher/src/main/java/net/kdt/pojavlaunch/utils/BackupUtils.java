package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import net.kdt.pojavlaunch.Tools;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class BackupUtils {

    public interface ProgressCallback {
        void onProgress(String currentFile);
    }

    /**
     * Zip a list of predefined paths into the target URI.
     */
    public static void createBackup(Context context, Uri targetUri, ProgressCallback callback) throws IOException {
        try (OutputStream os = context.getContentResolver().openOutputStream(targetUri);
             ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(os))) {

            // 1. Launcher Settings (config.json) -> launcher_config.json
            zipFile(new File(Tools.DIR_DATA, "config.json"), "launcher_config.json", zos, callback);
            
            // 2. Profiles (launcher_profiles.json)
            zipFile(new File(Tools.DIR_GAME_NEW, "launcher_profiles.json"), "launcher_profiles.json", zos, callback);
            
            // 3. Custom Controls
            zipFile(new File(Tools.DIR_GAME_HOME, "controlmap"), "controlmap", zos, callback);

            // 4. Custom cursors (*.png, *.ani, *.cur in GAME_HOME)
            File gameHome = new File(Tools.DIR_GAME_HOME);
            if (gameHome.exists() && gameHome.isDirectory()) {
                File[] files = gameHome.listFiles();
                if (files != null) {
                    for (File f : files) {
                        if (f.isFile()) {
                            String name = f.getName().toLowerCase();
                            if (name.endsWith(".png") || name.endsWith(".ani") || name.endsWith(".cur")) {
                                zipFile(f, f.getName(), zos, callback);
                            }
                        }
                    }
                }
            }

            // 5. Shared/Vanilla Saves and Options
            zipFile(new File(Tools.DIR_GAME_NEW, "saves"), "vanilla_saves", zos, callback);
            zipFile(new File(Tools.DIR_GAME_NEW, "options.txt"), "vanilla_options.txt", zos, callback);

            // 6. Isolated Profiles (options.txt and saves/)
            File profilesDir = new File(Tools.DIR_GAME_HOME, "profiles");
            if (profilesDir.exists() && profilesDir.isDirectory()) {
                File[] profiles = profilesDir.listFiles();
                if (profiles != null) {
                    for (File profile : profiles) {
                        if (profile.isDirectory()) {
                            String profileName = profile.getName();
                            zipFile(new File(profile, "saves"), "profiles/" + profileName + "/saves", zos, callback);
                            zipFile(new File(profile, "options.txt"), "profiles/" + profileName + "/options.txt", zos, callback);
                        }
                    }
                }
            }
        }
    }

    private static void zipFile(File fileToZip, String fileName, ZipOutputStream zos, ProgressCallback callback) throws IOException {
        if (!fileToZip.exists()) return;

        if (fileToZip.isHidden()) return;

        if (fileToZip.isDirectory()) {
            if (fileName.endsWith("/")) {
                zos.putNextEntry(new ZipEntry(fileName));
                zos.closeEntry();
            } else {
                zos.putNextEntry(new ZipEntry(fileName + "/"));
                zos.closeEntry();
            }
            File[] children = fileToZip.listFiles();
            if (children != null) {
                for (File childFile : children) {
                    zipFile(childFile, fileName + "/" + childFile.getName(), zos, callback);
                }
            }
            return;
        }

        if (callback != null) callback.onProgress(fileName);
        
        try (FileInputStream fis = new FileInputStream(fileToZip)) {
            ZipEntry zipEntry = new ZipEntry(fileName);
            zos.putNextEntry(zipEntry);
            byte[] bytes = new byte[8192];
            int length;
            while ((length = fis.read(bytes)) >= 0) {
                zos.write(bytes, 0, length);
            }
        }
    }

    /**
     * Unzip a backup from the source URI.
     */
    public static void restoreBackup(Context context, Uri sourceUri, ProgressCallback callback) throws IOException {
        try (InputStream is = context.getContentResolver().openInputStream(sourceUri);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is))) {
             
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String fileName = entry.getName();
                if (callback != null) callback.onProgress(fileName);
                
                File targetFile = getTargetFileForEntry(fileName);
                if (targetFile == null) continue; // Skip unknown files
                
                if (entry.isDirectory()) {
                    targetFile.mkdirs();
                } else {
                    targetFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(targetFile)) {
                        byte[] bytes = new byte[8192];
                        int length;
                        while ((length = zis.read(bytes)) >= 0) {
                            fos.write(bytes, 0, length);
                        }
                    }
                }
                zis.closeEntry();
            }
        }
    }
    
    /**
     * Checks if a backup contains any files that would overwrite existing local files.
     * @return true if there is at least one collision
     */
    public static boolean checkCollisions(Context context, Uri sourceUri) throws IOException {
        try (InputStream is = context.getContentResolver().openInputStream(sourceUri);
             ZipInputStream zis = new ZipInputStream(new BufferedInputStream(is))) {
             
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String fileName = entry.getName();
                File targetFile = getTargetFileForEntry(fileName);
                if (targetFile != null && targetFile.exists() && !entry.isDirectory()) {
                    return true;
                }
                zis.closeEntry();
            }
        }
        return false;
    }

    private static File getTargetFileForEntry(String fileName) {
        if (fileName.equals("launcher_config.json")) {
            return new File(Tools.DIR_DATA, "config.json");
        } else if (fileName.equals("launcher_profiles.json")) {
            return new File(Tools.DIR_GAME_NEW, "launcher_profiles.json");
        } else if (fileName.startsWith("controlmap/")) {
            return new File(Tools.DIR_GAME_HOME, fileName);
        } else if (fileName.startsWith("vanilla_saves/")) {
            return new File(Tools.DIR_GAME_NEW, fileName.replaceFirst("vanilla_saves", "saves"));
        } else if (fileName.equals("vanilla_options.txt")) {
            return new File(Tools.DIR_GAME_NEW, "options.txt");
        } else if (fileName.startsWith("profiles/")) {
            return new File(Tools.DIR_GAME_HOME, fileName);
        } else if (fileName.endsWith(".png") || fileName.endsWith(".ani") || fileName.endsWith(".cur")) {
            // Must be a cursor at the root
            if (!fileName.contains("/")) {
                return new File(Tools.DIR_GAME_HOME, fileName);
            }
        }
        return null;
    }
}
