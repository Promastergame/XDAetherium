package net.kdt.pojavlaunch.value;


import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.Log;

import net.kdt.pojavlaunch.*;
import net.kdt.pojavlaunch.utils.FileUtils;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import com.google.gson.*;
import android.graphics.Bitmap;
import android.util.Base64;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;

import org.apache.commons.io.IOUtils;

@SuppressWarnings("IOStreamConstructor")
@Keep
public class MinecraftAccount {
    public String accessToken = "0"; // access token
    public String clientToken = "0"; // clientID: refresh and invalidate
    public String profileId = "00000000-0000-0000-0000-000000000000"; // profile UUID, for obtaining skin
    public String username = "Steve";
    public String selectedVersion = "1.7.10";
    public boolean isMicrosoft = false;
    public boolean isElyBy = false;
    public String msaRefreshToken = "0";
    public String xuid;
    public long expiresAt;
    public String skinFaceBase64;
    private Bitmap mFaceCache;
    
    void updateSkinFace(String uuid) {
        try {
            File skinFile = getSkinFaceFile(username);
            if(isElyBy) {
                updateElyBySkinFace(skinFile);
            } else {
                Tools.downloadFile("https://mc-heads.net/head/" + uuid + "/100", skinFile.getAbsolutePath());
            }
            mFaceCache = null;
            
            Log.i("SkinLoader", "Update skin face success");
        } catch (IOException e) {
            // Skin refresh limit, no internet connection, etc...
            // Simply ignore updating skin face
            Log.w("SkinLoader", "Could not update skin face", e);
        }
    }

    private void updateElyBySkinFace(File skinFile) throws IOException {
        File skinTextureFile = new File(Tools.DIR_CACHE, username + "_elyby_skin.png");
        downloadFileFollowingRedirects("https://skinsystem.ely.by/skins/" + URLEncoder.encode(username, "UTF-8") + ".png", skinTextureFile);
        Bitmap skin = BitmapFactory.decodeFile(skinTextureFile.getAbsolutePath());
        if(skin == null) throw new IOException("Could not decode Ely.by skin");
        Bitmap face = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(face);
        Rect dst = new Rect(0, 0, 100, 100);
        canvas.drawBitmap(skin, new Rect(8, 8, 16, 16), dst, null);
        if(skin.getWidth() >= 48 && skin.getHeight() >= 16) {
            canvas.drawBitmap(skin, new Rect(40, 8, 48, 16), dst, null);
        }
        try(FileOutputStream outputStream = new FileOutputStream(skinFile)) {
            if(!face.compress(Bitmap.CompressFormat.PNG, 100, outputStream)) {
                throw new IOException("Could not save Ely.by skin face");
            }
        }
    }

    private void downloadFileFollowingRedirects(String url, File out) throws IOException {
        FileUtils.ensureParentDirectory(out);
        String currentUrl = url;
        for(int redirectCount = 0; redirectCount < 5; redirectCount++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(currentUrl).openConnection();
            connection.setConnectTimeout(60000);
            connection.setReadTimeout(60000);
            connection.setRequestProperty("User-Agent", Tools.APP_NAME);
            connection.setInstanceFollowRedirects(false);
            int responseCode = connection.getResponseCode();
            if(responseCode >= 300 && responseCode < 400) {
                String location = connection.getHeaderField("Location");
                connection.disconnect();
                if(location == null) throw new IOException("Redirect without location from " + currentUrl);
                currentUrl = new URL(new URL(currentUrl), location).toString();
                if(currentUrl.startsWith("http://")) {
                    currentUrl = "https://" + currentUrl.substring("http://".length());
                }
                continue;
            }
            if(responseCode != HttpURLConnection.HTTP_OK) {
                String responseMessage = connection.getResponseMessage();
                connection.disconnect();
                throw new IOException("Server returned HTTP " + responseCode + ": " + responseMessage);
            }
            try(InputStream inputStream = connection.getInputStream();
                FileOutputStream outputStream = new FileOutputStream(out)) {
                IOUtils.copy(inputStream, outputStream);
            } finally {
                connection.disconnect();
            }
            return;
        }
        throw new IOException("Too many redirects from " + url);
    }

    public boolean isLocal(){
        return accessToken.equals("0") && !username.startsWith("Demo.");
    }

    public boolean isDemo(){
        return username.startsWith("Demo.");
    }
    
    public void updateSkinFace() {
        updateSkinFace(profileId);
    }
    
    public String save(String outPath) throws IOException {
        Tools.write(outPath, Tools.GLOBAL_GSON.toJson(this));
        return username;
    }
    
    public String save() throws IOException {
        return save(Tools.DIR_ACCOUNT_NEW + "/" + username + ".json");
    }
    
    public static MinecraftAccount parse(String content) throws JsonSyntaxException {
        return Tools.GLOBAL_GSON.fromJson(content, MinecraftAccount.class);
    }
    @Nullable
    public static MinecraftAccount load(String name) {
        if(!accountExists(name)) return null;
        try {
            MinecraftAccount acc = parse(Tools.read(Tools.DIR_ACCOUNT_NEW + "/" + name + ".json"));
            if (acc.accessToken == null) {
                acc.accessToken = "0";
            }
            if (acc.clientToken == null) {
                acc.clientToken = "0";
            }
            if (acc.profileId == null) {
                acc.profileId = "00000000-0000-0000-0000-000000000000";
            }
            if (acc.username == null) {
                acc.username = "0";
            }
            if (acc.selectedVersion == null) {
                acc.selectedVersion = "1.7.10";
            }
            if (acc.msaRefreshToken == null) {
                acc.msaRefreshToken = "0";
            }
            return acc;
        } catch(NullPointerException | IOException | JsonSyntaxException e) {
            Log.e(MinecraftAccount.class.getName(), "Caught an exception while loading the profile",e);
            return null;
        }
    }

    public Bitmap getSkinFace(){
        if(isLocal()) return null;

        File skinFaceFile = getSkinFaceFile(username);
        if (!skinFaceFile.exists()) {
            // Legacy version, storing the head inside the json as base 64
            if(skinFaceBase64 == null) return null;
            byte[] faceIconBytes = Base64.decode(skinFaceBase64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(faceIconBytes, 0, faceIconBytes.length);
        } else {
            if(mFaceCache == null) {
                mFaceCache = BitmapFactory.decodeFile(skinFaceFile.getAbsolutePath());
            }
        }

        return mFaceCache;
    }

    public static Bitmap getSkinFace(String username) {
        return BitmapFactory.decodeFile(getSkinFaceFile(username).getAbsolutePath());
    }

    private static File getSkinFaceFile(String username) {
        return new File(Tools.DIR_CACHE, username + ".png");
    }

    private static boolean accountExists(String username){
        return new File(Tools.DIR_ACCOUNT_NEW + "/" + username + ".json").exists();
    }
}
