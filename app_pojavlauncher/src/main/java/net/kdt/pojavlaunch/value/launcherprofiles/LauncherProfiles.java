package net.kdt.pojavlaunch.value.launcherprofiles;

import android.util.Log;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LauncherProfiles {
    public static MinecraftLauncherProfiles mainProfileJson;
    private static final File launcherProfilesFile = new File(Tools.GAME_PROFILES_FILE);

    /** Reload the profile from the file, creating a default one if necessary */
    public static void load(){
        if (launcherProfilesFile.exists()) {
            try {
                mainProfileJson = Tools.GLOBAL_GSON.fromJson(Tools.read(launcherProfilesFile.getAbsolutePath()), MinecraftLauncherProfiles.class);
            } catch (IOException e) {
                Log.e(LauncherProfiles.class.toString(), "Failed to load file: ", e);
                throw new RuntimeException(e);
            }
        }

        // Fill with default
        if (mainProfileJson == null) mainProfileJson = new MinecraftLauncherProfiles();
        if (mainProfileJson.profiles == null) mainProfileJson.profiles = new HashMap<>();
        if (mainProfileJson.profiles.size() == 0)
            mainProfileJson.profiles.put(UUID.randomUUID().toString(), MinecraftProfile.getDefaultProfile());

        // Normalize profile names from mod installers
        if(normalizeProfileIds(mainProfileJson)){
            write();
        }

        // When profile isolation is disabled, revert all auto-generated gameDirs
        if(LauncherPreferences.PREF_DISABLE_PROFILE_ISOLATION) {
            boolean reverted = false;
            for (MinecraftProfile profile : mainProfileJson.profiles.values()) {
                if (profile.gameDir != null && profile.gameDir.startsWith("profiles/")) {
                    profile.gameDir = null;
                    reverted = true;
                }
            }
            if(reverted) write();
        }

        // Ensure profiles with existing gameDir have their data migrated from .minecraft
        ensureProfilesHaveData();
    }

    /** Apply the current configuration into a file */
    public static void write() {
        try {
            Tools.write(launcherProfilesFile.getAbsolutePath(), mainProfileJson.toJson());
        } catch (IOException e) {
            Log.e(LauncherProfiles.class.toString(), "Failed to write profile file", e);
            throw new RuntimeException(e);
        }
    }

    public static @NonNull MinecraftProfile getCurrentProfile() {
        if(mainProfileJson == null) LauncherProfiles.load();
        String defaultProfileName = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
        MinecraftProfile profile = mainProfileJson.profiles.get(defaultProfileName);
        if(profile == null) throw new RuntimeException("The current profile stopped existing :(");
        return profile;
    }

    /**
     * Insert a new profile into the profile map
     * @param minecraftProfile the profile to insert
     */
    public static void insertMinecraftProfile(MinecraftProfile minecraftProfile) {
        // If the installer just named it "OptiFine", "Forge" or "Fabric", use the specific version ID instead
        if (minecraftProfile.name != null && (minecraftProfile.name.equals("OptiFine") || minecraftProfile.name.equals("Forge") || minecraftProfile.name.equals("fabric") || minecraftProfile.name.equals("Fabric"))) {
            if (minecraftProfile.lastVersionId != null && !minecraftProfile.lastVersionId.isEmpty()) {
                minecraftProfile.name = minecraftProfile.lastVersionId;
            }
        }

        if (!LauncherPreferences.PREF_DISABLE_PROFILE_ISOLATION && (minecraftProfile.gameDir == null || minecraftProfile.gameDir.isEmpty())) {
            String baseName = minecraftProfile.name == null ? "" : minecraftProfile.name;
            if (baseName.isEmpty() && minecraftProfile.lastVersionId != null) {
                baseName = minecraftProfile.lastVersionId;
            }
            if (baseName.isEmpty()) baseName = "Profile_" + System.currentTimeMillis();
            
            String sanitized = baseName.replaceAll("[^a-zA-Z0-9.\\-]", "_");
            minecraftProfile.gameDir = getUniqueGameDir("profiles/" + sanitized);
            
            // Copy existing data from .minecraft to the new profile directory (like Modrinth does with copy_dotminecraft)
            copyExistingDataToProfile(minecraftProfile);
        }
        mainProfileJson.profiles.put(getFreeProfileKey(), minecraftProfile);
    }

    /**
     * Copy existing .minecraft data (options.txt, saves, mods, etc.)
     * to the new profile directory so settings and worlds are preserved.
     * Like Modrinth's copy_dotminecraft() approach.
     */
    /**
     * For profiles that already have an isolated gameDir but are missing essential
     * files (like options.txt), copy them from the shared .minecraft directory.
     * This handles profiles that were migrated by the buggy 3.0.0 version.
     */
    private static void ensureProfilesHaveData() {
        File sourceDir = new File(Tools.DIR_GAME_NEW);
        if (!sourceDir.isDirectory()) return;

        for (MinecraftProfile profile : mainProfileJson.profiles.values()) {
            if (profile.gameDir == null || !profile.gameDir.startsWith("profiles/")) continue;
            File destDir = new File(Tools.DIR_GAME_HOME, profile.gameDir);

            // Always copy options.txt from .minecraft so old versions get proper rendering settings
            copyFileIfSourceExists(sourceDir, destDir, "options.txt");

            // For everything else, only copy once (tracked by .initialized)
            if (!new File(destDir, ".initialized").exists()) {
                copyExistingDataToProfile(profile);
            }
        }
    }

    private static void copyFileIfSourceExists(File sourceDir, File destDir, String name) {
        File src = new File(sourceDir, name);
        if (!src.exists()) return;
        File dst = new File(destDir, name);
        try {
            if (src.isDirectory()) {
                copyRecursive(src, dst);
            } else {
                Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            }
        } catch (Exception e) {
            Log.w("LauncherProfiles", "Failed to copy " + name, e);
        }
    }

    private static void copyExistingDataToProfile(MinecraftProfile profile) {
        if (profile.gameDir == null) return;
        File sourceDir = new File(Tools.DIR_GAME_NEW);
        if (!sourceDir.isDirectory()) return;

        File destDir = new File(Tools.DIR_GAME_HOME, profile.gameDir);
        if (!destDir.exists()) destDir.mkdirs();

        String[] itemsToCopy = {"saves", "mods", "resourcepacks", "servers.dat", "config"};
        for (String name : itemsToCopy) {
            copyFileIfSourceExists(sourceDir, destDir, name);
        }

        // Write sentinel file so we don't re-copy bulk data on every launch
        try {
            new File(destDir, ".initialized").createNewFile();
        } catch (IOException e) {
            Log.w("LauncherProfiles", "Failed to write sentinel file", e);
        }
    }

    private static void copyRecursive(File src, File dst) throws IOException {
        if (src.isDirectory()) {
            if (!dst.mkdirs()) return;
            String[] children = src.list();
            if (children != null) {
                for (String child : children) {
                    copyRecursive(new File(src, child), new File(dst, child));
                }
            }
        } else {
            Files.copy(src.toPath(), dst.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        }
    }

    public static String getUniqueGameDir(String baseDir) {
        String dir = baseDir;
        int i = 1;
        while (true) {
            boolean conflict = false;
            for (MinecraftProfile p : mainProfileJson.profiles.values()) {
                if (dir.equals(p.gameDir)) {
                    conflict = true;
                    break;
                }
            }
            if (!conflict) return dir;
            dir = baseDir + "_" + i;
            i++;
        }
    }

    public static @NonNull String cloneProfile(@NonNull String profileKey) {
        MinecraftProfile originalProfile = mainProfileJson.profiles.get(profileKey);
        MinecraftProfile clonedProfile = originalProfile == null
                ? MinecraftProfile.getDefaultProfile()
                : new MinecraftProfile(originalProfile);
        if(Tools.isValidString(clonedProfile.name)) clonedProfile.name = clonedProfile.name + " Copy";
        else if(Tools.isValidString(clonedProfile.lastVersionId)) clonedProfile.name = clonedProfile.lastVersionId + " Copy";
        else clonedProfile.name = "Copy";
        String nextProfile = getFreeProfileKey();
        mainProfileJson.profiles.put(nextProfile, clonedProfile);
        write();
        return nextProfile;
    }

    public static @NonNull String deleteProfile(@NonNull String profileKey, @NonNull String selectedProfile) {
        mainProfileJson.profiles.remove(profileKey);

        String nextProfile = selectedProfile;
        if(mainProfileJson.profiles.isEmpty()) {
            nextProfile = LauncherProfiles.getFreeProfileKey();
            mainProfileJson.profiles.put(nextProfile, MinecraftProfile.getDefaultProfile());
        } else if(profileKey.equals(selectedProfile) || !mainProfileJson.profiles.containsKey(selectedProfile)) {
            nextProfile = mainProfileJson.profiles.keySet().iterator().next();
        }

        write();
        return nextProfile;
    }

    /**
     * Pick an unused normalized key to store a new profile with
     * @return an unused key
     */
    public static String getFreeProfileKey() {
        Map<String, MinecraftProfile> profileMap = mainProfileJson.profiles;
        String freeKey = UUID.randomUUID().toString();
        while(profileMap.get(freeKey) != null) freeKey = UUID.randomUUID().toString();
        return freeKey;
    }

    /**
     * For all keys to be UUIDs, effectively isolating profile created by installers
     * This avoids certain profiles to be erased by the installer
     * @return Whether some profiles have been normalized
     */
    private static boolean normalizeProfileIds(MinecraftLauncherProfiles launcherProfiles){
        boolean hasNormalized = false;
        ArrayList<String> keys = new ArrayList<>();

        // Detect denormalized keys
        for(String profileKey : launcherProfiles.profiles.keySet()){
            try{
                if(!UUID.fromString(profileKey).toString().equals(profileKey)) keys.add(profileKey);
            }catch (IllegalArgumentException exception){
                keys.add(profileKey);
                Log.w(LauncherProfiles.class.toString(), "Illegal profile uuid: " + profileKey);
            }
        }

        // Swap the new keys
        for(String profileKey : keys){
            MinecraftProfile currentProfile = launcherProfiles.profiles.get(profileKey);
            insertMinecraftProfile(currentProfile);
            launcherProfiles.profiles.remove(profileKey);
            hasNormalized = true;
        }

        return hasNormalized;
    }
}
