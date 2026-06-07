package net.kdt.pojavlaunch.modloaders;

import android.util.Log;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class BTWDownloadTask implements Runnable {
    private static final String BASE_JSON = "{\"+traits\":[\"noapplet\"],\"inheritsFrom\":\"1.5.2\",\"mainClass\":\"net.fabricmc.loader.launch.knot.KnotClient\",\"libraries\":[{\"name\":\"org.ow2.asm:asm:9.0\",\"url\":\"https://maven.fabricmc.net/\"},{\"name\":\"org.ow2.asm:asm-analysis:9.0\",\"url\":\"https://maven.fabricmc.net/\"},{\"name\":\"org.ow2.asm:asm-commons:9.0\",\"url\":\"https://maven.fabricmc.net/\"},{\"name\":\"org.ow2.asm:asm-tree:9.0\",\"url\":\"https://maven.fabricmc.net/\"},{\"name\":\"org.ow2.asm:asm-util:9.0\",\"url\":\"https://maven.fabricmc.net/\"},{\"name\":\"net.fabricmc:sponge-mixin:0.8+build.18\",\"url\":\"https://maven.fabricmc.net/\"},{\"name\":\"net.fabricmc:tiny-mappings-parser:0.2.2.14\",\"url\":\"https://maven.fabricmc.net/\"},{\"name\":\"net.fabricmc:tiny-remapper:0.3.0.70\",\"url\":\"https://maven.fabricmc.net/\"},{\"name\":\"com.google.jimfs:jimfs:1.1\",\"url\":\"https://maven.fabricmc.net/\"},{\"name\":\"net.fabricmc:fabric-loader-sat4j:2.3.5.4\",\"url\":\"https://maven.fabricmc.net/\"},{\"name\":\"com.google.guava:guava:21.0\",\"url\":\"https://maven.fabricmc.net/\"},{\"name\":\"net.fabricmc:access-widener:1.0.0\",\"url\":\"https://maven.fabricmc.net/\"},{\"name\":\"org.apache.logging.log4j:log4j-api:2.19.0\",\"url\":\"https://libraries.minecraft.net/\"},{\"name\":\"org.apache.logging.log4j:log4j-core:2.19.0\",\"url\":\"https://libraries.minecraft.net/\"},{\"name\":\"com.google.code.gson:gson:2.8.6\",\"url\":\"https://repo.maven.apache.org/maven2/\"},{\"name\":\"commons-io:commons-io:2.5\",\"url\":\"https://libraries.minecraft.net/\"},{\"name\":\"org.apache.commons:commons-lang3:3.5\",\"url\":\"https://libraries.minecraft.net/\"},{\"name\":\"net.sf.jopt-simple:jopt-simple:5.0.4\",\"url\":\"https://repo.maven.apache.org/maven2/\"},{\"name\":\"com.github.minecraft-cursed-legacy:cursed-fabric-loader-btw:1.1.0\"},{\"name\":\"net.fabricmc:intermediary-btw:1.5.2\"}],\"arguments\":{\"jvm\":[\"-Dfabric.gameVersion=1.5.2\",\"-Dfabric.launch.version=%1$s\"]},\"minecraftArguments\":\"${auth_player_name} ${auth_session} --gameDir ${game_directory} --assetsDir ${game_assets} --version ${version_name}\",\"id\":\"%1$s\"}";
    
    private static final String BTW_JAR_URL = "https://github.com/BTW-Community/Cursed-BTW/releases/download/v0.5-beta-v2.1.1/btw-fabric-0.5.0-CE.2.1.4.jar";
    private static final String FLATCORE_MAP_URL = "https://github.com/BTW-Community/BTW-Public/raw/main/maps/flatcore.zip";
    private static final String ADVENTURE_MAP_URL = "https://github.com/BTW-Community/BTW-Public/raw/main/maps/adventure.zip";

    private final ModloaderDownloadListener mListener;
    private final boolean mDownloadFlatcore;
    private final boolean mDownloadAdventure;

    public BTWDownloadTask(ModloaderDownloadListener listener, boolean downloadFlatcore, boolean downloadAdventure) {
        this.mListener = listener;
        this.mDownloadFlatcore = downloadFlatcore;
        this.mDownloadAdventure = downloadAdventure;
    }

    @Override
    public void run() {
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.fabric_dl_progress, "Better Than Wolves");
        try {
            runCatching();
            mListener.onDownloadFinished(null);
        } catch (Exception e) {
            Log.e("BTWDownloadTask", "Failed to install Better Than Wolves", e);
            mListener.onDownloadError(e);
        }
        ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
    }

    private void createJson(String versionId) throws IOException {
        String btwJson = String.format(BASE_JSON, versionId);
        File jsonDir = new File(Tools.DIR_HOME_VERSION, versionId);
        File jsonFile = new File(jsonDir, versionId + ".json");
        FileUtils.ensureDirectory(jsonDir);
        Tools.write(jsonFile.getAbsolutePath(), btwJson);
    }

    private void createProfile(String versionId) throws IOException {
        LauncherProfiles.load();
        MinecraftProfile btwProfile = new MinecraftProfile();
        btwProfile.lastVersionId = versionId;
        btwProfile.name = "Better Than Wolves";
        btwProfile.gameDir = "./custom_instances/better_than_wolves";
        btwProfile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + "External-8";
        btwProfile.icon = "fabric";
        LauncherProfiles.insertMinecraftProfile(btwProfile);
        LauncherProfiles.write();
    }

    private void downloadFileWithProgress(String urlString, File destFile, String taskName) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.connect();

        int fileLength = connection.getContentLength();
        try (InputStream input = new BufferedInputStream(url.openStream(), 8192);
             FileOutputStream output = new FileOutputStream(destFile)) {

            byte[] data = new byte[1024];
            long total = 0;
            int count;
            while ((count = input.read(data)) != -1) {
                total += count;
                int progress = fileLength > 0 ? (int) (total * 100 / fileLength) : 0;
                ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, progress, R.string.fabric_dl_progress, taskName);
                output.write(data, 0, count);
            }
            output.flush();
        }
    }

    private void downloadAndExtractZip(String urlString, File destDir, String taskName) throws IOException {
        URL url = new URL(urlString);
        FileUtils.ensureDirectory(destDir);
        try (ZipInputStream zipInput = new ZipInputStream(new BufferedInputStream(url.openStream()))) {
            ZipEntry entry;
            while ((entry = zipInput.getNextEntry()) != null) {
                File file = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    FileUtils.ensureDirectory(file);
                } else {
                    FileUtils.ensureDirectory(file.getParentFile());
                    try (FileOutputStream output = new FileOutputStream(file)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = zipInput.read(buffer)) > 0) {
                            output.write(buffer, 0, len);
                        }
                    }
                }
                zipInput.closeEntry();
            }
        }
    }

    private File downloadAndExtractLoaderJars() throws IOException {
        String zipUrl = "https://github.com/BTW-Community/cursed-fabric-loader/releases/download/1.1.0-btw/Cursed-Fabric-BTW-MultiMC.zip";
        File tempZipFile = new File(Tools.DIR_CACHE, "Cursed-Fabric-BTW-MultiMC.zip");
        
        // Download Zip with progress
        downloadFileWithProgress(zipUrl, tempZipFile, "Скачивание Cursed Fabric Loader");
        
        File loaderDestFile = new File(Tools.DIR_HOME_LIBRARY, "com/github/minecraft-cursed-legacy/cursed-fabric-loader-btw/1.1.0/cursed-fabric-loader-btw-1.1.0.jar");
        File intermediaryDestFile = new File(Tools.DIR_HOME_LIBRARY, "net/fabricmc/intermediary-btw/1.5.2/intermediary-btw-1.5.2.jar");
        File btwCeDestFile = new File(Tools.DIR_CACHE, "btw-ce-2.1.4.jar.tmp");
        
        FileUtils.ensureDirectory(loaderDestFile.getParentFile());
        FileUtils.ensureDirectory(intermediaryDestFile.getParentFile());
        FileUtils.ensureDirectory(btwCeDestFile.getParentFile());
        
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 95, R.string.fabric_dl_progress, "Распаковка загрузчика");
        try (ZipFile zipFile = new ZipFile(tempZipFile)) {
            java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
            byte[] data = new byte[8192];
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                String name = entry.getName();
                boolean isLoader = name.endsWith("cursed-fabric-loader-btw-1.1.0.jar");
                boolean isIntermediary = name.endsWith("intermediary-btw-1.5.2.jar");
                boolean isBtwCe = name.endsWith("6857a551-f685-4bac-b42c-b7d6d9256c92.jar");
                
                if (isLoader || isIntermediary || isBtwCe) {
                    File destFile;
                    if (isLoader) destFile = loaderDestFile;
                    else if (isIntermediary) destFile = intermediaryDestFile;
                    else destFile = btwCeDestFile;
                    
                    try (InputStream input = zipFile.getInputStream(entry);
                         FileOutputStream output = new FileOutputStream(destFile)) {
                        int count;
                        while ((count = input.read(data)) != -1) {
                            output.write(data, 0, count);
                        }
                    }
                }
            }
        } finally {
            if (tempZipFile.exists()) {
                tempZipFile.delete();
            }
        }
        return btwCeDestFile;
    }

    private void copyFile(File src, File dest) throws IOException {
        try (InputStream in = new java.io.FileInputStream(src);
             OutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
        }
    }

    private void mergeJar(File baseJar, File modJar) throws IOException {
        File tempMergedJar = new File(baseJar.getParentFile(), baseJar.getName() + ".tmp");
        byte[] buffer = new byte[8192];
        
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(tempMergedJar))) {
            java.util.Set<String> modEntryNames = new java.util.HashSet<>();
            try (ZipFile modZip = new ZipFile(modJar)) {
                java.util.Enumeration<? extends ZipEntry> entries = modZip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (!entry.isDirectory()) {
                        modEntryNames.add(entry.getName());
                    }
                }
            }
            
            // Copy non-duplicate entries from baseJar
            try (ZipFile baseZip = new ZipFile(baseJar)) {
                java.util.Enumeration<? extends ZipEntry> entries = baseZip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!entry.isDirectory() && !modEntryNames.contains(name)) {
                        ZipEntry newEntry = new ZipEntry(name);
                        out.putNextEntry(newEntry);
                        try (InputStream in = baseZip.getInputStream(entry)) {
                            int len;
                            while ((len = in.read(buffer)) > 0) {
                                out.write(buffer, 0, len);
                            }
                        }
                        out.closeEntry();
                    }
                }
            }
            
            // Copy all entries from modJar
            try (ZipFile modZip = new ZipFile(modJar)) {
                java.util.Enumeration<? extends ZipEntry> entries = modZip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName();
                    if (!entry.isDirectory()) {
                        ZipEntry newEntry = new ZipEntry(name);
                        out.putNextEntry(newEntry);
                        try (InputStream in = modZip.getInputStream(entry)) {
                            int len;
                            while ((len = in.read(buffer)) > 0) {
                                out.write(buffer, 0, len);
                            }
                        }
                        out.closeEntry();
                    }
                }
            }
        }
        
        if (baseJar.exists() && !baseJar.delete()) {
            throw new IOException("Failed to delete base jar");
        }
        if (!tempMergedJar.renameTo(baseJar)) {
            throw new IOException("Failed to rename merged jar");
        }
    }

    public void runCatching() throws IOException {
        String versionId = "btw-1.5.2";
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 10, R.string.fabric_dl_progress, "Создание профиля");
        createJson(versionId);
        createProfile(versionId);

        // Download and extract the Cursed Fabric loader & intermediary Jars, returning the btw-ce-2.1.4.jar.tmp file
        File tempBtwCeJar = downloadAndExtractLoaderJars();

        // Download/copy base Minecraft 1.5.2 jar to btw-1.5.2.jar and merge with btw-ce-2.1.4.jar.tmp
        File vanillaJarFile = new File(Tools.DIR_HOME_VERSION, "1.5.2/1.5.2.jar");
        File btwJarFile = new File(Tools.DIR_HOME_VERSION, "btw-1.5.2/btw-1.5.2.jar");
        FileUtils.ensureDirectory(btwJarFile.getParentFile());
        
        if (!vanillaJarFile.exists()) {
            FileUtils.ensureDirectory(vanillaJarFile.getParentFile());
            String vanillaJarUrl = "https://launcher.mojang.com/v1/objects/465378c9dc2f779ae1d6e8046ebc46fb53a57968/client.jar";
            downloadFileWithProgress(vanillaJarUrl, vanillaJarFile, "Скачивание Minecraft 1.5.2");
        }
        
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 98, R.string.fabric_dl_progress, "Модификация игрового архива");
        copyFile(vanillaJarFile, btwJarFile);
        mergeJar(btwJarFile, tempBtwCeJar);
        
        if (tempBtwCeJar.exists()) {
            tempBtwCeJar.delete();
        }

        // Ensure directories
        File btwGameDir = new File(Tools.DIR_GAME_HOME, "custom_instances/better_than_wolves");
        File modsDir = new File(btwGameDir, "mods");
        File savesDir = new File(btwGameDir, "saves");
        FileUtils.ensureDirectory(modsDir);
        FileUtils.ensureDirectory(savesDir);

        // No need to download BTW Mod JAR into mods/ folder because it is already merged into btw-1.5.2.jar as a jarmod, mirroring the official MultiMC instance.

        // Download Maps
        if (mDownloadFlatcore) {
            ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 60, R.string.fabric_dl_progress, "Скачивание Flatcore Map");
            try {
                downloadAndExtractZip(FLATCORE_MAP_URL, savesDir, "Распаковка Flatcore Map");
            } catch (IOException e) {
                Log.w("BTWDownloadTask", "Could not download Flatcore Map", e);
            }
        }

        if (mDownloadAdventure) {
            ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 80, R.string.fabric_dl_progress, "Скачивание Adventure Map");
            try {
                downloadAndExtractZip(ADVENTURE_MAP_URL, savesDir, "Распаковка Adventure Map");
            } catch (IOException e) {
                Log.w("BTWDownloadTask", "Could not download Adventure Map", e);
            }
        }

        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 100, R.string.fabric_dl_progress, "Установка завершена");
    }
}
