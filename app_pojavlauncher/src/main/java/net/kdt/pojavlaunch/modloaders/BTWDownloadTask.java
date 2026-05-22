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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class BTWDownloadTask implements Runnable {
    private static final String BASE_JSON = "{\"inheritsFrom\":\"1.6.4\",\"mainClass\":\"net.fabricmc.loader.impl.launch.knot.KnotClient\",\"libraries\":[{\"name\":\"net.fabricmc:fabric-loader:0.15.11\",\"url\":\"https://maven.fabricmc.net/\"},{\"name\":\"net.fabricmc:tiny-mappings-parser:0.3.0+build.17\",\"url\":\"https://maven.fabricmc.net/\"},{\"name\":\"net.fabricmc:sponge-mixin:0.12.5+mixin.0.8.5\",\"url\":\"https://maven.fabricmc.net/\"},{\"name\":\"net.fabricmc:tiny-remapper:0.8.6\",\"url\":\"https://maven.fabricmc.net/\"},{\"name\":\"net.fabricmc:access-widener:2.1.0\",\"url\":\"https://maven.fabricmc.net/\"},{\"name\":\"org.ow2.asm:asm:9.6\",\"url\":\"https://maven.fabricmc.net/\"},{\"name\":\"org.ow2.asm:asm-analysis:9.6\",\"url\":\"https://maven.fabricmc.net/\"},{\"name\":\"org.ow2.asm:asm-commons:9.6\",\"url\":\"https://maven.fabricmc.net/\"},{\"name\":\"org.ow2.asm:asm-tree:9.6\",\"url\":\"https://maven.fabricmc.net/\"},{\"name\":\"org.ow2.asm:asm-util:9.6\",\"url\":\"https://maven.fabricmc.net/\"}],\"id\":\"%1$s\"}";
    
    private static final String BTW_JAR_URL = "https://github.com/BTW-Community/BTW-Public/releases/download/v2.1.4/Better-Than-Wolves-v2.1.4.jar";
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

    public void runCatching() throws IOException {
        String versionId = "btw-1.6.4";
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 10, R.string.fabric_dl_progress, "Создание профиля");
        createJson(versionId);
        createProfile(versionId);

        // Ensure directories
        File btwGameDir = new File(Tools.DIR_GAME_NEW, "custom_instances/better_than_wolves");
        File modsDir = new File(btwGameDir, "mods");
        File savesDir = new File(btwGameDir, "saves");
        FileUtils.ensureDirectory(modsDir);
        FileUtils.ensureDirectory(savesDir);

        // Download BTW Mod JAR
        File btwJar = new File(modsDir, "Better-Than-Wolves-v2.1.4.jar");
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 20, R.string.fabric_dl_progress, "Скачивание мода");
        downloadFileWithProgress(BTW_JAR_URL, btwJar, "Скачивание Better Than Wolves");

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
