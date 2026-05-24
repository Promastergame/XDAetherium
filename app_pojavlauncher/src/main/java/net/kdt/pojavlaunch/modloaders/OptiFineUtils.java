package net.kdt.pojavlaunch.modloaders;

import android.content.Intent;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class OptiFineUtils {

    public static OptiFineVersions downloadOptiFineVersions() throws IOException {
        // Check if we have a valid cache first (avoids network requests entirely)
        File cacheDestination = new File(Tools.DIR_CACHE, "string_cache/of_downloads_page");
        boolean isCacheValid = cacheDestination.isFile() &&
                cacheDestination.canRead() &&
                System.currentTimeMillis() < (cacheDestination.lastModified() + 86400000);

        if (isCacheValid) {
            try {
                OptiFineVersions versions = DownloadUtils.downloadStringCached("https://optifine.net/downloads",
                        "of_downloads_page", new OptiFineScraper());
                if (versions != null && versions.minecraftVersions != null && !versions.minecraftVersions.isEmpty()) {
                    return versions;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        // If no cache or cache failed to parse, run a parallel race between official site and mirror
        final OptiFineVersions[] result = new OptiFineVersions[1];
        final Object lock = new Object();

        Thread mirrorThread = new Thread(() -> {
            try {
                OptiFineVersions versions = downloadOptiFineVersionsBmclapi();
                if (versions != null) {
                    synchronized (lock) {
                        if (result[0] == null) {
                            result[0] = versions;
                            lock.notifyAll();
                        }
                    }
                }
            } catch (Exception ignored) {}
        });

        Thread officialThread = new Thread(() -> {
            try {
                OptiFineVersions versions = DownloadUtils.downloadStringCached("https://optifine.net/downloads",
                        "of_downloads_page", new OptiFineScraper());
                if (versions != null && versions.minecraftVersions != null && !versions.minecraftVersions.isEmpty()) {
                    synchronized (lock) {
                        if (result[0] == null) {
                            result[0] = versions;
                            lock.notifyAll();
                        }
                    }
                }
            } catch (Exception ignored) {}
        });

        officialThread.start();
        // Give official thread a tiny 150ms start so if it is working, we prefer it
        try {
            Thread.sleep(150);
        } catch (InterruptedException ignored) {}

        synchronized (lock) {
            if (result[0] == null) {
                mirrorThread.start();
            }
        }

        synchronized (lock) {
            long startTime = System.currentTimeMillis();
            while (result[0] == null && (System.currentTimeMillis() - startTime) < 5000) {
                try {
                    lock.wait(1000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

        if (result[0] == null) {
            throw new IOException("Failed to load OptiFine versions from both official site and mirror");
        }
        return result[0];
    }

    public static OptiFineVersions downloadOptiFineVersionsBmclapi() {
        try {
            String json = DownloadUtils.downloadString("https://bmclapi2.bangbang93.com/optifine/versionList");
            if (json == null || json.isEmpty()) return null;

            BmclapiOptiFine[] bmclapiList = Tools.GLOBAL_GSON.fromJson(json, BmclapiOptiFine[].class);
            if (bmclapiList == null || bmclapiList.length == 0) return null;

            java.util.Map<String, List<OptiFineVersion>> groups = new java.util.LinkedHashMap<>();
            // Loop backwards to get the newest versions first (BMCLAPI lists oldest first)
            for (int i = bmclapiList.length - 1; i >= 0; i--) {
                BmclapiOptiFine item = bmclapiList[i];
                if (item.mcversion == null || item.filename == null) continue;
                String mcVerKey = "Minecraft " + item.mcversion;
                List<OptiFineVersion> versions = groups.get(mcVerKey);
                if (versions == null) {
                    versions = new java.util.ArrayList<>();
                    groups.put(mcVerKey, versions);
                }

                OptiFineVersion optiVersion = new OptiFineVersion();
                optiVersion.minecraftVersion = mcVerKey;

                // Extract a user-friendly version name from filename (e.g., OptiFine_1.16.5_HD_U_G8.jar -> OptiFine 1.16.5 HD U G8)
                String name = item.filename.replace(".jar", "");
                if (name.startsWith("preview_")) {
                    name = name.substring("preview_".length());
                }
                optiVersion.versionName = name.replace('_', ' ');
                optiVersion.downloadUrl = "https://optifine.net/adloadx?f=" + item.filename;

                versions.add(optiVersion);
            }

            OptiFineVersions result = new OptiFineVersions();
            result.minecraftVersions = new java.util.ArrayList<>(groups.keySet());
            result.optifineVersions = new java.util.ArrayList<>(groups.values());
            return result;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static class BmclapiOptiFine {
        public String mcversion;
        public String filename;
    }

    public static void addAutoInstallArgs(Intent intent, File modInstallerJar) {
        intent.putExtra("javaArgs", "-javaagent:"+ Tools.DIR_DATA+"/forge_installer/forge_installer.jar"
                + "=OFNPS" +// No Profile Suppression
                " -jar "+modInstallerJar.getAbsolutePath());
    }

    public static class OptiFineVersions {
        public List<String> minecraftVersions;
        public List<List<OptiFineVersion>> optifineVersions;
    }
    public static class OptiFineVersion {
        public String minecraftVersion;
        public String versionName;
        public String downloadUrl;
    }
}
