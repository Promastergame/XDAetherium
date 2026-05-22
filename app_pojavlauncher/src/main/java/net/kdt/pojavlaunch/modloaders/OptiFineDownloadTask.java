package net.kdt.pojavlaunch.modloaders;

import android.app.Activity;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.tasks.AsyncMinecraftDownloader;
import net.kdt.pojavlaunch.tasks.MinecraftDownloader;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.util.concurrent.CancellationException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OptiFineDownloadTask implements Runnable, Tools.DownloaderFeedback, AsyncMinecraftDownloader.DoneListener {
    private static final Pattern sMcVersionPattern = Pattern.compile("([0-9]+)\\.([0-9]+)\\.?([0-9]+)?");
    private static final int OPTIFINE_DOWNLOAD_ATTEMPTS = 3;
    private final OptiFineUtils.OptiFineVersion mOptiFineVersion;
    private final File mDestinationFile;
    private final ModloaderDownloadListener mListener;
    private final Object mMinecraftDownloadLock = new Object();
    private Throwable mDownloaderThrowable;
    private final Activity activity;
    private volatile boolean mCancelled;
    private volatile Thread mRunningThread;
    private volatile HttpURLConnection mActiveConnection;

    public OptiFineDownloadTask(OptiFineUtils.OptiFineVersion mOptiFineVersion, ModloaderDownloadListener mListener, Activity activity) {
        this.mOptiFineVersion = mOptiFineVersion;
        this.mDestinationFile = new File(Tools.DIR_CACHE, "mod_installers/" + getOptiFineFileName(mOptiFineVersion));
        this.mListener = mListener;
        this.activity = activity;
    }

    @Override
    public void run() {
        mCancelled = false;
        mRunningThread = Thread.currentThread();
        ProgressKeeper.setCancellationHandler(ProgressLayout.INSTALL_MODPACK, this::cancelDownload);
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.of_dl_progress, mOptiFineVersion.versionName);
        try {
            if(runCatching()) mListener.onDownloadFinished(mDestinationFile);
        }catch (CancellationException e) {
            mListener.onDownloadError(e);
        }catch (IOException e) {
            mListener.onDownloadError(e);
        } finally {
            ProgressKeeper.setCancellationHandler(ProgressLayout.INSTALL_MODPACK, null);
            mRunningThread = null;
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
            Thread.interrupted();
        }
    }

    public boolean runCatching() throws IOException {
        throwIfCancelled();
        String minecraftVersion = determineMinecraftVersion();
        if(minecraftVersion == null) return false;
        if(!downloadMinecraft(minecraftVersion)) {
            mListener.onDownloadError(getMinecraftDownloadException(minecraftVersion));
            return false;
        }
        throwIfCancelled();
        if(isCachedInstallerUsable()) return true;
        downloadOptiFineInstaller(minecraftVersion);
        return true;
    }

    private boolean isCachedInstallerUsable() {
        if(!mDestinationFile.isFile() || mDestinationFile.length() < 1024) return false;
        try (FileInputStream inputStream = new FileInputStream(mDestinationFile)) {
            return inputStream.read() == 'P' && inputStream.read() == 'K';
        }catch (IOException e) {
            return false;
        }
    }

    private void downloadOptiFineInstaller(String minecraftVersion) throws IOException {
        IOException lastException = null;
        String bmclapiDownloadUrl = getBmclapiDownloadUrl(minecraftVersion);
        if(bmclapiDownloadUrl != null) {
            try {
                DownloadUtils.downloadFileMonitored(bmclapiDownloadUrl, mDestinationFile, new byte[8192], this);
                return;
            }catch (FileNotFoundException e) {
                lastException = e;
            }catch (CancellationException e) {
                throw e;
            }catch (IOException e) {
                lastException = e;
            }
            if(mDestinationFile.exists()) mDestinationFile.delete();
        }
        try {
            DownloadUtils.downloadFileMonitored(getFastMinecraftMirrorDownloadUrl(), mDestinationFile, new byte[8192], this);
            return;
        }catch (FileNotFoundException e) {
            lastException = e;
        }catch (CancellationException e) {
            throw e;
        }catch (IOException e) {
            lastException = e;
        }
        if(mDestinationFile.exists()) mDestinationFile.delete();

        for(int attempt = 0; attempt < OPTIFINE_DOWNLOAD_ATTEMPTS; attempt++) {
            String downloadUrl = scrapeDownloadsPage();
            if(downloadUrl == null) break;
            try {
                DownloadUtils.downloadFileMonitored(downloadUrl, mDestinationFile, new byte[8192], this);
                return;
            }catch (CancellationException e) {
                throw e;
            }catch (IOException e) {
                lastException = e;
                if(mDestinationFile.exists()) mDestinationFile.delete();
            }
        }
        if(lastException != null) throw lastException;
    }

    private String getBmclapiDownloadUrl(String minecraftVersion) {
        String fileName = getOptiFineFileName(mOptiFineVersion);
        Matcher matcher = Pattern.compile("OptiFine_[^_]+_([^_]+_[^_]+)_(.+)\\.jar").matcher(fileName);
        if(!matcher.matches()) return null;
        return "https://bmclapi2.bangbang93.com/optifine/" + minecraftVersion + "/" + matcher.group(1) + "/" + matcher.group(2);
    }

    private String getFastMinecraftMirrorDownloadUrl() {
        return "https://optifine.fastmcmirror.org/" + getOptiFineFileName(mOptiFineVersion);
    }

    private static String getOptiFineFileName(OptiFineUtils.OptiFineVersion optiFineVersion) {
        Matcher urlMatcher = Pattern.compile("[?&]f=([^&]+)").matcher(optiFineVersion.downloadUrl);
        if(urlMatcher.find()) {
            try {
                return URLDecoder.decode(urlMatcher.group(1), "UTF-8");
            }catch (Exception ignored) {
                return urlMatcher.group(1);
            }
        }
        return "OptiFine_" + optiFineVersion.minecraftVersion.replace("Minecraft ", "")
                + "_" + optiFineVersion.versionName.replace("OptiFine ", "").replace(' ', '_')
                + ".jar";
    }

    public String scrapeDownloadsPage() throws IOException{
        String scrapeResult = OFDownloadPageScraper.run(mOptiFineVersion.downloadUrl);
        if(scrapeResult == null) mListener.onDataNotAvailable();
        return scrapeResult;
    }

    public String determineMinecraftVersion() {
        Matcher matcher = sMcVersionPattern.matcher(mOptiFineVersion.minecraftVersion);
        if(matcher.find()) {
            StringBuilder mcVersionBuilder = new StringBuilder();
            mcVersionBuilder.append(matcher.group(1));
            mcVersionBuilder.append('.');
            mcVersionBuilder.append(matcher.group(2));
            String thirdGroup = matcher.group(3);
            if(thirdGroup != null && !thirdGroup.isEmpty() && !"0".equals(thirdGroup)) {
                mcVersionBuilder.append('.');
                mcVersionBuilder.append(thirdGroup);
            }
            return mcVersionBuilder.toString();
        }else{
            mListener.onDataNotAvailable();
            return null;
        }
    }

    public boolean downloadMinecraft(String minecraftVersion) {
        throwIfCancelled();
        // the string is always normalized
        JMinecraftVersionList.Version minecraftJsonVersion = getListedVersion(minecraftVersion);
        if(minecraftJsonVersion == null) {
            mDownloaderThrowable = new FileNotFoundException("Minecraft " + minecraftVersion + " was not found in the version manifest");
            return false;
        }
        try {
            synchronized (mMinecraftDownloadLock) {
                new MinecraftDownloader().start(activity, minecraftJsonVersion, minecraftVersion, this);
                mMinecraftDownloadLock.wait();
            }
        }catch (InterruptedException e) {
            throw new CancellationException("OptiFine download cancelled");
        }
        throwIfCancelled();
        return mDownloaderThrowable == null;
    }

    private JMinecraftVersionList.Version getListedVersion(String minecraftVersion) {
        JMinecraftVersionList.Version minecraftJsonVersion = AsyncMinecraftDownloader.getListedVersion(minecraftVersion);
        if(minecraftJsonVersion != null) return minecraftJsonVersion;
        JMinecraftVersionList versionList = refreshVersionManifest();
        if(versionList == null || versionList.versions == null) return null;
        for(JMinecraftVersionList.Version version : versionList.versions) {
            if(minecraftVersion.equals(version.id)) return version;
        }
        return null;
    }

    private JMinecraftVersionList refreshVersionManifest() {
        try {
            String jsonString = DownloadUtils.downloadString(LauncherPreferences.PREF_VERSION_REPOS);
            JMinecraftVersionList versionList = Tools.GLOBAL_GSON.fromJson(jsonString, JMinecraftVersionList.class);
            if(versionList != null && versionList.versions != null) {
                ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versionList);
                Tools.write(new File(Tools.DIR_CACHE, "version_list.json").getAbsolutePath(), jsonString);
                return versionList;
            }
        }catch (Exception e) {
            mDownloaderThrowable = e;
        }
        try {
            File versionFile = new File(Tools.DIR_CACHE, "version_list.json");
            if(versionFile.isFile()) {
                JMinecraftVersionList versionList = Tools.GLOBAL_GSON.fromJson(Tools.read(versionFile), JMinecraftVersionList.class);
                if(versionList != null && versionList.versions != null) {
                    ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versionList);
                    return versionList;
                }
            }
        }catch (IOException e) {
            if(mDownloaderThrowable == null) mDownloaderThrowable = e;
        }
        return null;
    }

    private Exception getMinecraftDownloadException(String minecraftVersion) {
        if(mDownloaderThrowable instanceof Exception) return (Exception) mDownloaderThrowable;
        if(mDownloaderThrowable != null) return new Exception(mDownloaderThrowable);
        return new FileNotFoundException("Unable to resolve Minecraft " + minecraftVersion + " metadata for OptiFine");
    }

    @Override
    public void updateProgress(int curr, int max) {
        throwIfCancelled();
        int progress100 = (int)(((float)curr / (float)max)*100f);
        ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, progress100, R.string.of_dl_progress, mOptiFineVersion.versionName);
    }

    private void cancelDownload() {
        mCancelled = true;
        ProgressKeeper.setCancellationHandler(ProgressLayout.INSTALL_MODPACK, null);
        ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, "Cancelling...");
        HttpURLConnection activeConnection = mActiveConnection;
        if(activeConnection != null) activeConnection.disconnect();
        MinecraftDownloader.cancelCurrentDownload();
        Thread runningThread = mRunningThread;
        if(runningThread != null) runningThread.interrupt();
        synchronized (mMinecraftDownloadLock) {
            mMinecraftDownloadLock.notifyAll();
        }
    }

    private void throwIfCancelled() {
        if(isCancelled()) throw new CancellationException("OptiFine download cancelled");
    }

    @Override
    public boolean isCancelled() {
        return mCancelled || Thread.currentThread().isInterrupted();
    }

    @Override
    public void onDownloadConnectionOpened(HttpURLConnection connection) {
        mActiveConnection = connection;
        if(isCancelled()) connection.disconnect();
    }

    @Override
    public void onDownloadConnectionClosed(HttpURLConnection connection) {
        if(mActiveConnection == connection) mActiveConnection = null;
    }

    @Override
    public void onDownloadDone() {
        synchronized (mMinecraftDownloadLock) {
            mDownloaderThrowable = null;
            mMinecraftDownloadLock.notifyAll();
        }
    }

    @Override
    public void onDownloadFailed(Throwable throwable) {
        synchronized (mMinecraftDownloadLock) {
            mDownloaderThrowable = throwable;
            mMinecraftDownloadLock.notifyAll();
        }
    }
}
