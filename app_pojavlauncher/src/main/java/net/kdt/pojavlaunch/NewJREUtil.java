package net.kdt.pojavlaunch;

import static net.kdt.pojavlaunch.Architecture.archAsString;
import static net.kdt.pojavlaunch.Architecture.getDeviceArchitecture;
import static net.kdt.pojavlaunch.Tools.NATIVE_LIB_DIR;
import static net.kdt.pojavlaunch.Tools.isOnline;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper;
import net.kdt.pojavlaunch.utils.DownloadUtils;
import net.kdt.pojavlaunch.utils.MathUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class NewJREUtil {
    private static boolean checkInternalRuntime(AssetManager assetManager, InternalRuntime internalRuntime) {
        String launcher_runtime_version;
        String installed_runtime_version = MultiRTUtils.readInternalRuntimeVersion(internalRuntime.name);
        try {
            launcher_runtime_version = Tools.read(assetManager.open(internalRuntime.path+"/version"));
        }catch (IOException exc) {
            //we don't have a runtime included!
            //if we have one installed -> return true -> proceed (no updates but the current one should be functional)
            //if we don't -> return false -> Cannot find compatible Java runtime
            return installed_runtime_version != null;
        }
        // this implicitly checks for null, so it will unpack the runtime even if we don't have one installed
        if(!launcher_runtime_version.equals(installed_runtime_version))
            return unpackInternalRuntime(assetManager, internalRuntime, launcher_runtime_version);
        else return true;
    }

    private static boolean unpackInternalRuntime(AssetManager assetManager, InternalRuntime internalRuntime, String version) {
        try {
            MultiRTUtils.installRuntimeNamedBinpack(
                    assetManager.open(internalRuntime.path+"/universal.tar.xz"),
                    assetManager.open(internalRuntime.path+"/bin-" + archAsString(Tools.DEVICE_ARCHITECTURE) + ".tar.xz"),
                    internalRuntime.name, version);
            MultiRTUtils.postPrepare(internalRuntime.name);
            return true;
        }catch (IOException e) {
            Log.e("NewJREAuto", "Internal JRE unpack failed", e);
            return false;
        }
    }

    private static InternalRuntime getInternalRuntime(Runtime runtime) {
        for(InternalRuntime internalRuntime : InternalRuntime.values()) {
            if(internalRuntime.name.equals(runtime.name)) return internalRuntime;
        }
        return null;
    }

    private static MathUtils.RankedValue<Runtime> getNearestInstalledRuntime(int targetVersion) {
        List<Runtime> runtimes = MultiRTUtils.getInstalledRuntimes();
        return MathUtils.findNearestPositive(targetVersion, runtimes, (runtime)->runtime.javaVersion);
    }

    private static MathUtils.RankedValue<InternalRuntime> getNearestInternalRuntime(int targetVersion) {
        List<InternalRuntime> runtimeList = Arrays.asList(InternalRuntime.values());
        return MathUtils.findNearestPositive(targetVersion, runtimeList, (runtime)->runtime.majorVersion);
    }

    private static Runtime getInstalledRuntime(int javaVersion) {
        for (Runtime runtime : MultiRTUtils.getInstalledRuntimes()) {
            if (runtime.javaVersion == javaVersion) return runtime;
        }
        return null;
    }

    private static InternalRuntime getInternalRuntime(int javaVersion) {
        for(InternalRuntime internalRuntime : InternalRuntime.values()) {
            if(internalRuntime.majorVersion == javaVersion) return internalRuntime;
        }
        return null;
    }

    private static String getExactRuntimeNameIfAvailable(Activity activity, AssetManager assetManager, int javaVersion) {
        Runtime installedRuntime = getInstalledRuntime(javaVersion);
        if (installedRuntime != null) return installedRuntime.name;

        InternalRuntime internalRuntime = getInternalRuntime(javaVersion);
        if (internalRuntime != null && checkInternalRuntime(assetManager, internalRuntime)) return internalRuntime.name;

        if (isJavaVersionAvailableForDownload(javaVersion)) {
            tryDownloadRuntime(activity, javaVersion);
            Runtime downloadedRuntime = getInstalledRuntime(javaVersion);
            if (downloadedRuntime != null) return downloadedRuntime.name;
        }
        return null;
    }


    public static boolean isLegacyVersion(String id, String inheritsFrom) {
        if (id == null) return false;
        String idLower = id.toLowerCase();
        
        if (idLower.contains("btw-") || idLower.contains("btw")) {
            return true;
        }
        
        if (idLower.startsWith("b1.") || idLower.startsWith("a1.") || idLower.startsWith("c0.") || idLower.startsWith("rd-") || idLower.startsWith("inf-")) {
            return true;
        }
        
        if (idLower.startsWith("1.")) {
            try {
                String[] parts = idLower.split("\\.");
                if (parts.length > 1) {
                    int subVersion = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
                    if (subVersion < 13) {
                        return true;
                    }
                }
            } catch (Exception e) {
                // Ignore
            }
        }
        
        if (inheritsFrom != null) {
            String inheritsLower = inheritsFrom.toLowerCase();
            if (inheritsLower.contains("btw-") || inheritsLower.contains("btw")) {
                return true;
            }
            if (inheritsLower.startsWith("b1.") || inheritsLower.startsWith("a1.") || inheritsLower.startsWith("c0.") || inheritsLower.startsWith("rd-") || inheritsLower.startsWith("inf-")) {
                return true;
            }
            if (inheritsLower.startsWith("1.")) {
                try {
                    String[] parts = inheritsLower.split("\\.");
                    if (parts.length > 1) {
                        int subVersion = Integer.parseInt(parts[1].replaceAll("[^0-9]", ""));
                        if (subVersion < 13) {
                            return true;
                        }
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }
        }
        
        return false;
    }

    /** @return true if everything is good, false otherwise.  */
    public static boolean installNewJreIfNeeded(Activity activity, JMinecraftVersionList.Version versionInfo) {
        //Now we have the reliable information to check if our runtime settings are good enough
        int gameRequiredVersion = 8;
        if (versionInfo.javaVersion != null && versionInfo.javaVersion.majorVersion > 0)
            gameRequiredVersion = versionInfo.javaVersion.majorVersion;

        boolean legacy = isLegacyVersion(versionInfo.id, versionInfo.inheritsFrom);
        if (legacy) {
            gameRequiredVersion = 8;
        }

        LauncherProfiles.load();
        AssetManager assetManager = activity.getAssets();
        MinecraftProfile minecraftProfile = LauncherProfiles.getCurrentProfile();
        String profileRuntime = Tools.getSelectedRuntime(minecraftProfile);
        Runtime runtime = MultiRTUtils.read(profileRuntime);
        
        boolean isRuntimeAcceptable = runtime != null && (legacy ? (runtime.javaVersion == 8) : (runtime.javaVersion == gameRequiredVersion));
        
        // Partly trust the user with his own exact-version selection, if the game can even try to run in this case
        if (isRuntimeAcceptable) {
            // Check whether the selection is an internal runtime
            InternalRuntime internalRuntime = getInternalRuntime(runtime);
            // If it is, check if updates are available from the APK file
            if (internalRuntime != null) {
                // Not calling showRuntimeFail on failure here because we did, technically, find the compatible runtime
                return checkInternalRuntime(assetManager, internalRuntime);
            }
            return true;
        }

        try {
            String exactRuntimeName = getExactRuntimeNameIfAvailable(activity, assetManager, gameRequiredVersion);
            if (exactRuntimeName != null) {
                minecraftProfile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + exactRuntimeName;
                LauncherProfiles.write();
                return true;
            }
        } catch (Exception e) {
            Tools.showError(activity, e);
            return false;
        }

        if (legacy || gameRequiredVersion == 8) {
            showRuntimeFail(activity, 8);
            return false;
        }

        // If the runtime version selected by the user is not appropriate for this version (which means the game won't run at all)
        // automatically pick from either an already installed runtime, or a runtime packed with the launcher
        MathUtils.RankedValue<?> nearestInstalledRuntime = getNearestInstalledRuntime(gameRequiredVersion);
        MathUtils.RankedValue<?> nearestInternalRuntime = getNearestInternalRuntime(gameRequiredVersion);

        MathUtils.RankedValue<?> selectedRankedRuntime = MathUtils.objectMin(
                nearestInternalRuntime, nearestInstalledRuntime, (value)->value.rank
        );

        // Check if the selected runtime actually exists in the APK, else download it
        // If it isn't InternalRuntime then it wasn't in the apk in the first place!
        if (selectedRankedRuntime != null && selectedRankedRuntime.value instanceof InternalRuntime)
            if (!checkInternalRuntime(assetManager, (InternalRuntime) selectedRankedRuntime.value)) {
                if (nearestInstalledRuntime == null) // If this was non-null then it would be a valid runtime and we can leave it be
                    tryDownloadRuntime(activity, gameRequiredVersion);
                // This means the internal runtime didn't extract so let's use installed instead
                // This also refreshes it so after the runtime download, it can find the new runtime
                selectedRankedRuntime = getNearestInstalledRuntime(gameRequiredVersion);
            }


        // No possible selections
        if(selectedRankedRuntime == null) {
            showRuntimeFail(activity, gameRequiredVersion);
            return false;
        }

        Object selected = selectedRankedRuntime.value;
        String appropriateRuntime;
        InternalRuntime internalRuntime;

        // Perform checks on the picked runtime
        if(selected instanceof Runtime) {
            // If it's an already installed runtime, save its name and check if
            // it's actually an internal one (just in case)
            Runtime selectedRuntime = (Runtime) selected;
            appropriateRuntime = selectedRuntime.name;
            internalRuntime = getInternalRuntime(selectedRuntime);
        } else if (selected instanceof InternalRuntime) {
            // If it's an internal runtime, set it's name as the appropriate one.
            internalRuntime = (InternalRuntime) selected;
            appropriateRuntime = internalRuntime.name;
        } else {
            throw new RuntimeException("Unexpected type of selected: "+selected.getClass().getName());
        }

        // If it turns out the selected runtime is actually an internal one, attempt automatic installation or update
        if(internalRuntime != null && !checkInternalRuntime(assetManager, internalRuntime)) {
            // Not calling showRuntimeFail here because we did, technically, find the compatible runtime
            return false;
        }

        minecraftProfile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + appropriateRuntime;
        LauncherProfiles.write();
        return true;
    }

    private static void showRuntimeFail(Activity activity, int javaVersion) {
        Tools.dialogOnUiThread(activity, activity.getString(R.string.global_error),
                activity.getString(R.string.multirt_nocompatiblert, javaVersion));
    }

    public static boolean isJavaVersionAvailableForDownload(int version) {
        for (ExternalRuntime javaVersion : ExternalRuntime.values()) {
            if (javaVersion.majorVersion == version) {
                return true;
            }
        }
        return false;
    }

    private static String getJreSource(int javaVersion, String arch){
        return String.format("https://github.com/AngelAuraMC/angelauramc-openjdk-build/releases/download/download_jre%1$s/jre%1$s-android-%2$s.tar.xz", javaVersion, arch);
    }
    /**
     * @return whether installation was successful or not
     */
    private static void tryDownloadRuntime(Context activity, int javaVersion){
        if (!isOnline(activity)) throw new RuntimeException(activity.getString(R.string.multirt_no_internet));
        String arch = archAsString(getDeviceArchitecture());
        // Checks for using this method
        if (!isJavaVersionAvailableForDownload(javaVersion)) throw new RuntimeException("This is not an available JRE version");
        if ((getDeviceArchitecture() == Architecture.ARCH_X86 && javaVersion >= 21)) throw new RuntimeException("x86 is not supported on Java"+javaVersion);
        File outputFile = new File(Tools.DIR_CACHE, String.format("jre%s-android-%s.tar.xz", javaVersion, arch));
        try {
            DownloaderProgressWrapper monitor = new DownloaderProgressWrapper(R.string.newdl_downloading_jre_runtime,
                    ProgressLayout.UNPACK_RUNTIME + "_" + javaVersion);
            monitor.extraString = Integer.toString(javaVersion);
            DownloadUtils.downloadFileMonitored(
                    getJreSource(javaVersion, arch),
                    outputFile,
                    null,
                    monitor
            );
            String jreName = "External-" + javaVersion;
            MultiRTUtils.installRuntimeNamed(NATIVE_LIB_DIR, new FileInputStream(outputFile), jreName);
            MultiRTUtils.postPrepare(jreName);
            outputFile.delete();
        } catch (IOException e) {
            if(outputFile.exists()) outputFile.delete();
            throw new RuntimeException("Failed to download Java "+javaVersion+" for "+arch, e);
        } finally {
            ProgressLayout.clearProgress(ProgressLayout.UNPACK_RUNTIME + "_" + javaVersion);
        }
    }

    private enum InternalRuntime {
        JRE_17(17, "Internal-17", "components/jre-new"),
        JRE_21(21, "Internal-21", "components/jre-21"),
        JRE_25(25, "Internal-25", "components/jre-25");
        public final int majorVersion;
        public final String name;
        public final String path;
        InternalRuntime(int majorVersion, String name, String path) {
            this.majorVersion = majorVersion;
            this.name = name;
            this.path = path;
        }
    }

    public enum ExternalRuntime {
        JRE_8(8, "External-8"),
        JRE_17(17, "External-17"),
        JRE_21(21, "External-21"),
        JRE_25(25, "External-25");
        public final int majorVersion;
        public final String name;
        public final String downloadLink;
        public boolean isDownloading = false;

        ExternalRuntime(int majorVersion, String name) {
            this.majorVersion = majorVersion;
            this.name = name;
            this.downloadLink = getJreSource(majorVersion, archAsString(getDeviceArchitecture()));
        }
        public void downloadRuntime(Context activity){
            tryDownloadRuntime(activity, majorVersion);
        }
    }

}