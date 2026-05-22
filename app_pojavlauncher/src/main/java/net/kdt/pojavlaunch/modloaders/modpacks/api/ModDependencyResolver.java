package net.kdt.pojavlaunch.modloaders.modpacks.api;

import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ModDependencyResolver {

    private ModrinthApi api;

    public ModDependencyResolver() {
        api = new ModrinthApi();
    }

    public ResolveResult resolve(String projectId, MinecraftProfile profile) {
        return resolve(projectId, profile, null, null);
    }

    public ResolveResult resolve(String projectId, MinecraftProfile profile, String mcVersion, String loader) {
        return resolve(projectId, profile, mcVersion, loader, null);
    }

    public ResolveResult resolve(String projectId, MinecraftProfile profile, String mcVersion, String loader, String targetVersionId) {
        ResolveResult result = new ResolveResult();
        Set<String> processedProjectIds = new HashSet<>();
        
        if (profile.installedMods != null) {
            for (ModItem installed : profile.installedMods) {
                processedProjectIds.add(installed.projectId);
            }
        }

        try {
            // Use passed parameters first, fallback to profile values if null/empty/invalid
            String targetVersion = mcVersion;
            if (targetVersion == null || targetVersion.isEmpty() || targetVersion.equals("release") || targetVersion.equals("latest-release") || targetVersion.equals("latest-snapshot")) {
                targetVersion = profile.lastVersionId;
            }
            String targetLoader = loader != null && !loader.isEmpty() ? loader : profile.modLoader;

            String cleanMcVersion = net.kdt.pojavlaunch.Tools.getCleanMinecraftVersion(targetVersion, targetLoader);
            if (targetVersionId != null && !targetVersionId.isEmpty()) {
                resolveSpecificVersion(projectId, targetVersionId, cleanMcVersion, targetLoader, result, processedProjectIds, true);
            } else {
                resolveRecursive(projectId, cleanMcVersion, targetLoader, result, processedProjectIds, true);
            }
            result.success = true;
        } catch (Exception e) {
            e.printStackTrace();
            result.success = false;
            result.errorMessage = e.getMessage();
        }

        return result;
    }

    private void resolveSpecificVersion(String projectId, String versionId, String mcVersion, String loader, ResolveResult result, Set<String> processed, boolean isRoot) throws Exception {
        if (processed.contains(projectId)) {
            return;
        }
        
        ModItem compatibleMod = api.getVersionDetails(versionId, mcVersion, loader);
        if (compatibleMod == null) {
            throw new Exception("Не найдена совместимая версия мода или зависимости для проекта: " + projectId + " (" + mcVersion + ", " + loader + ")");
        }
        
        processed.add(projectId);
        
        if (isRoot) {
            result.modsToInstall.add(compatibleMod);
        } else {
            result.dependenciesToInstall.add(compatibleMod);
        }
        
        if (compatibleMod.requiredDependencyIds != null) {
            for (String depId : compatibleMod.requiredDependencyIds) {
                resolveRecursive(depId, mcVersion, loader, result, processed, false);
            }
        }
    }

    private void resolveRecursive(String projectId, String mcVersion, String loader, ResolveResult result, Set<String> processed, boolean isRoot) throws Exception {
        if (processed.contains(projectId)) {
            return;
        }
        
        ModItem compatibleMod = api.getCompatibleVersion(projectId, mcVersion, loader);
        if (compatibleMod == null) {
            throw new Exception("Не найдена совместимая версия мода или зависимости для проекта: " + projectId + " (" + mcVersion + ", " + loader + ")");
        }
        
        processed.add(projectId);
        
        if (isRoot) {
            result.modsToInstall.add(compatibleMod);
        } else {
            result.dependenciesToInstall.add(compatibleMod);
        }
        
        if (compatibleMod.requiredDependencyIds != null) {
            for (String depId : compatibleMod.requiredDependencyIds) {
                resolveRecursive(depId, mcVersion, loader, result, processed, false);
            }
        }
    }
}
