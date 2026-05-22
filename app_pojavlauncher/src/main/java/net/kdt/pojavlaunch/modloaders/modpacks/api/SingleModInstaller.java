package net.kdt.pojavlaunch.modloaders.modpacks.api;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.ArrayList;

public class SingleModInstaller {

    public static void installModWithDependencies(ResolveResult resolveResult, String profileId, Runnable onComplete, Runnable onError) {
        new Thread(() -> {
            try {
                MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(profileId);
                if (profile == null) throw new Exception("Профиль не найден");

                if (profile.gameDir == null || profile.gameDir.isEmpty()) {
                    profile.gameDir = "custom_profiles/" + profileId;
                }
                
                File profileDir = Tools.getGameDirPath(profile);
                File modsDir = new File(profileDir, "mods");
                if (!modsDir.exists()) {
                    modsDir.mkdirs();
                }

                if (profile.installedMods == null) {
                    profile.installedMods = new ArrayList<>();
                }

                for (ModItem mod : resolveResult.modsToInstall) {
                    downloadMod(mod, modsDir);
                    boolean exists = false;
                    for (ModItem installed : profile.installedMods) {
                        if (installed.projectId.equals(mod.projectId)) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        profile.installedMods.add(mod);
                    }
                }

                for (ModItem dep : resolveResult.dependenciesToInstall) {
                    downloadMod(dep, modsDir);
                    boolean exists = false;
                    for (ModItem installed : profile.installedMods) {
                        if (installed.projectId.equals(dep.projectId)) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        profile.installedMods.add(dep);
                    }
                }

                LauncherProfiles.write();

                if (onComplete != null) {
                    onComplete.run();
                }
            } catch (Exception e) {
                e.printStackTrace();
                if (onError != null) {
                    onError.run();
                }
            }
        }).start();
    }

    private static void downloadMod(ModItem mod, File modsDir) throws Exception {
        if (mod.downloadUrl == null) throw new Exception("Нет ссылки для скачивания мода");
        File targetFile = new File(modsDir, mod.fileName);
        Tools.downloadFile(mod.downloadUrl, targetFile.getAbsolutePath());
    }
}
