package net.kdt.pojavlaunch.mirrors;

import android.util.Log;

import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.DownloadUtils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;

public class DownloadMirror {
    public static final int DOWNLOAD_CLASS_LIBRARIES = 0;
    public static final int DOWNLOAD_CLASS_METADATA = 1;
    public static final int DOWNLOAD_CLASS_ASSETS = 2;

    private static final String URL_PROTOCOL_TAIL = "://";
    private static final String[] MIRROR_BMCLAPI = {
            "https://bmclapi2.bangbang93.com/maven",
            "https://bmclapi2.bangbang93.com",
            "https://bmclapi2.bangbang93.com/assets"
    };

    /**
     * Download a file with the current mirror. If the file is missing on the mirror,
     * fall back to the official source.
     * @param downloadClass Class of the download. Can either be DOWNLOAD_CLASS_LIBRARIES,
     *                      DOWNLOAD_CLASS_METADATA or DOWNLOAD_CLASS_ASSETS
     * @param urlInput The original (Mojang) URL for the download
     * @param outputFile The output file for the download
     * @param buffer The shared buffer
     * @param monitor The download monitor.
     */
    public static void downloadFileMirrored(int downloadClass, String urlInput, File outputFile,
                                            @Nullable byte[] buffer, Tools.DownloaderFeedback monitor) throws IOException {
        String mappedUrl = getMirrorMapping(downloadClass, urlInput);
        try {
            DownloadUtils.downloadFileMonitored(mappedUrl, outputFile, buffer, monitor);
            return;
        }catch (IOException e) {
            if(tryDownloadFallback(downloadClass, urlInput, mappedUrl, outputFile, buffer, monitor, e)) return;
            throw e;
        }
    }

    /**
     * Download a file with the current mirror. If the file is missing on the mirror,
     * fall back to the official source.
     * @param downloadClass Class of the download. Can either be DOWNLOAD_CLASS_LIBRARIES,
     *                      DOWNLOAD_CLASS_METADATA or DOWNLOAD_CLASS_ASSETS
     * @param urlInput The original (Mojang) URL for the download
     * @param outputFile The output file for the download
     */
    public static void downloadFileMirrored(int downloadClass, String urlInput, File outputFile) throws IOException {
        String mappedUrl = getMirrorMapping(downloadClass, urlInput);
        try {
            DownloadUtils.downloadFile(mappedUrl, outputFile);
            return;
        }catch (IOException e) {
            if(tryDownloadFallback(downloadClass, urlInput, mappedUrl, outputFile, e)) return;
            throw e;
        }
    }

    /**
     * Get the content length of a file on the current mirror. If the file is missing on the mirror,
     * or the mirror does not give out the length, request the length from the original source
     * @param downloadClass Class of the download. Can either be DOWNLOAD_CLASS_LIBRARIES,
     *                      DOWNLOAD_CLASS_METADATA or DOWNLOAD_CLASS_ASSETS
     * @param urlInput The original (Mojang) URL for the download
     * @return the length of the file denoted by the URL in bytes, or -1 if not available
     */
    public static long getContentLengthMirrored(int downloadClass, String urlInput){
        String mappedUrl;
        try {
            mappedUrl = getMirrorMapping(downloadClass, urlInput);
            long length = DownloadUtils.getContentLength(mappedUrl);
            if (length < 1) {
                Log.w("DownloadMirror", "Unable to get content length from " + mappedUrl);
            }else {
                return length;
            }
        } catch (IOException ignored) {
            mappedUrl = urlInput;
        }
        try {
            if(!mappedUrl.equals(urlInput)) {
                long length = DownloadUtils.getContentLength(urlInput);
                if(length > 0) return length;
            }
            String fallbackUrl = getAutomaticFallbackMapping(downloadClass, urlInput, mappedUrl);
            if(fallbackUrl != null) return DownloadUtils.getContentLength(fallbackUrl);
        } catch (IOException ignored) { // If error happens, fallback to old file counter instead of size. This shouldn't really happen unless offline though.
            return -1L;
        }
        return -1L;
    }

    /**
     * Download a file as a string from the current mirror. If the file does not exist on the mirror
     * or the mirror returns an invalid string, request the file from the original source
     * @param downloadClass Class of the download. Can either be DOWNLOAD_CLASS_LIBRARIES,
     *                      DOWNLOAD_CLASS_METADATA or DOWNLOAD_CLASS_ASSETS
     * @param urlInput The original (Mojang) URL for the download
     * @return the contents of the downloaded file as a String.
     */
    public static String downloadStringMirrored(int downloadClass, String urlInput) throws IOException{
        String resultString = null;
        String mappedUrl = getMirrorMapping(downloadClass,urlInput);
        IOException thrownException = null;
        try {
            resultString = DownloadUtils.downloadString(mappedUrl);
        }catch (IOException e) {
            Log.w("DownloadMirror", "Failed to download string from " + mappedUrl, e);
            thrownException = e;
        }
        if(Tools.isValidString(resultString)) {
            return resultString;
        }
        if(!mappedUrl.equals(urlInput)) {
            try {
                resultString = DownloadUtils.downloadString(urlInput);
            }catch (IOException e) {
                if(thrownException != null) thrownException.addSuppressed(e);
                else thrownException = e;
            }
            if(Tools.isValidString(resultString)) return resultString;
        }
        String fallbackUrl = getAutomaticFallbackMapping(downloadClass, urlInput, mappedUrl);
        if(fallbackUrl != null) {
            try {
                resultString = DownloadUtils.downloadString(fallbackUrl);
            }catch (IOException e) {
                if(thrownException != null) thrownException.addSuppressed(e);
                else thrownException = e;
            }
            if(Tools.isValidString(resultString)) return resultString;
        }
        if(thrownException != null) throw thrownException;
        throw new IOException("Unable to download valid string from " + urlInput);
    }

    /**
     * Check if the current download source is a mirror and not an official source.
     * @return true if the source is a mirror, false otherwise
     */
    public static boolean isMirrored() {
        return !LauncherPreferences.PREF_DOWNLOAD_SOURCE.equals("default");
    }

    private static String[] getMirrorSettings() {
        switch (LauncherPreferences.PREF_DOWNLOAD_SOURCE) {
            case "bmclapi": return MIRROR_BMCLAPI;
            case "default":
            default:
                return null;
        }
    }

    private static boolean tryDownloadFallback(int downloadClass, String urlInput, String mappedUrl,
                                               File outputFile, @Nullable byte[] buffer,
                                               Tools.DownloaderFeedback monitor, IOException firstException) throws IOException {
        if(!mappedUrl.equals(urlInput)) {
            Log.w("DownloadMirror", "Failed to download from " + mappedUrl, firstException);
            Log.i("DownloadMirror", "Falling back to default source");
            try {
                DownloadUtils.downloadFileMonitored(urlInput, outputFile, buffer, monitor);
                return true;
            }catch (IOException e) {
                firstException.addSuppressed(e);
            }
        }
        String fallbackUrl = getAutomaticFallbackMapping(downloadClass, urlInput, mappedUrl);
        if(fallbackUrl != null) {
            Log.i("DownloadMirror", "Falling back to BMCLAPI mirror");
            try {
                DownloadUtils.downloadFileMonitored(fallbackUrl, outputFile, buffer, monitor);
                return true;
            }catch (IOException e) {
                firstException.addSuppressed(e);
            }
        }
        return false;
    }

    private static boolean tryDownloadFallback(int downloadClass, String urlInput, String mappedUrl,
                                               File outputFile, IOException firstException) throws IOException {
        if(!mappedUrl.equals(urlInput)) {
            Log.w("DownloadMirror", "Failed to download from " + mappedUrl, firstException);
            Log.i("DownloadMirror", "Falling back to default source");
            try {
                DownloadUtils.downloadFile(urlInput, outputFile);
                return true;
            }catch (IOException e) {
                firstException.addSuppressed(e);
            }
        }
        String fallbackUrl = getAutomaticFallbackMapping(downloadClass, urlInput, mappedUrl);
        if(fallbackUrl != null) {
            Log.i("DownloadMirror", "Falling back to BMCLAPI mirror");
            try {
                DownloadUtils.downloadFile(fallbackUrl, outputFile);
                return true;
            }catch (IOException e) {
                firstException.addSuppressed(e);
            }
        }
        return false;
    }

    private static String getAutomaticFallbackMapping(int downloadClass, String mojangUrl, String attemptedUrl) throws MalformedURLException {
        String fallbackUrl = getMirrorMapping(downloadClass, mojangUrl, MIRROR_BMCLAPI);
        if(fallbackUrl.equals(mojangUrl) || fallbackUrl.equals(attemptedUrl)) return null;
        return fallbackUrl;
    }

    private static String getMirrorMapping(int downloadClass, String mojangUrl) throws MalformedURLException{
        return getMirrorMapping(downloadClass, mojangUrl, getMirrorSettings());
    }

    private static String getMirrorMapping(int downloadClass, String mojangUrl, String[] mirrorSettings) throws MalformedURLException{
        if(mirrorSettings == null) return mojangUrl;
        int urlTail = getBaseUrlTail(mojangUrl);
        String baseUrl = mojangUrl.substring(0, urlTail);
        String path = mojangUrl.substring(urlTail);
        switch(downloadClass) {
            case DOWNLOAD_CLASS_ASSETS:
            case DOWNLOAD_CLASS_METADATA:
                baseUrl = mirrorSettings[downloadClass];
                break;
            case DOWNLOAD_CLASS_LIBRARIES:
                if(baseUrl.endsWith("libraries.minecraft.net")) {
                    baseUrl = mirrorSettings[downloadClass];
                }else if(baseUrl.endsWith("piston-data.mojang.com")) {
                    baseUrl = mirrorSettings[DOWNLOAD_CLASS_METADATA];
                }
                break;
        }
        return baseUrl + path;
    }

    private static int getBaseUrlTail(String wholeUrl) throws MalformedURLException{
        int protocolNameEnd = wholeUrl.indexOf(URL_PROTOCOL_TAIL);
        if(protocolNameEnd == -1)
            throw new MalformedURLException("No protocol, or non path-based URL");
        protocolNameEnd += URL_PROTOCOL_TAIL.length();
        int hostnameEnd = wholeUrl.indexOf('/', protocolNameEnd);
        if(protocolNameEnd >= wholeUrl.length() || hostnameEnd == protocolNameEnd)
            throw new MalformedURLException("No hostname");
        if(hostnameEnd == -1) hostnameEnd = wholeUrl.length();
        return hostnameEnd;
    }
}
