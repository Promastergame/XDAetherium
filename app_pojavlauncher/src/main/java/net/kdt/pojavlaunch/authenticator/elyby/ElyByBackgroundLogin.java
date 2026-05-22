package net.kdt.pojavlaunch.authenticator.elyby;

import static net.kdt.pojavlaunch.PojavApplication.sExecutorService;

import android.util.Base64;
import android.util.Log;

import androidx.annotation.Nullable;

import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.authenticator.listener.DoneListener;
import net.kdt.pojavlaunch.authenticator.listener.ErrorListener;
import net.kdt.pojavlaunch.authenticator.listener.ProgressListener;
import net.kdt.pojavlaunch.value.MinecraftAccount;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class ElyByBackgroundLogin {
    private static final String ELYBY_AUTH_URL = "https://authserver.ely.by";
    private static final int CONNECTION_TIMEOUT = 30000;
    private final boolean mIsRefresh;
    private final String mLogin;
    private final String mPassword;
    private final String mTotp;
    private MinecraftAccount mAccount;

    public ElyByBackgroundLogin(String login, String password, String totp) {
        mIsRefresh = false;
        mLogin = login;
        mPassword = password;
        mTotp = totp;
    }

    public ElyByBackgroundLogin(MinecraftAccount account) {
        mIsRefresh = true;
        mAccount = account;
        mLogin = null;
        mPassword = null;
        mTotp = null;
    }

    public void performLogin(@Nullable final ProgressListener progressListener,
                             @Nullable final DoneListener doneListener,
                             @Nullable final ErrorListener errorListener) {
        sExecutorService.execute(() -> {
            try {
                notifyProgress(progressListener, 1);
                JSONObject response = mIsRefresh ? refresh() : authenticate();
                notifyProgress(progressListener, 4);
                MinecraftAccount account = buildAccount(response);
                account.save();
                notifyProgress(progressListener, 5);
                if(doneListener != null) {
                    Tools.runOnUiThread(() -> doneListener.onLoginDone(account));
                }
            }catch (Exception e) {
                Log.e("ElyByAuth", "Ely.by authentication failed", e);
                if(errorListener != null) {
                    Tools.runOnUiThread(() -> errorListener.onLoginError(e));
                }
            }
            ProgressLayout.clearProgress(ProgressLayout.AUTHENTICATE_MICROSOFT);
        });
    }

    private JSONObject authenticate() throws IOException, JSONException {
        JSONObject request = new JSONObject();
        request.put("username", mLogin);
        request.put("password", getPasswordWithTotp());
        request.put("clientToken", UUID.randomUUID().toString());
        request.put("requestUser", true);
        JSONObject agent = new JSONObject();
        agent.put("name", "Minecraft");
        agent.put("version", 1);
        request.put("agent", agent);
        return postJson("/auth/authenticate", request);
    }

    private JSONObject refresh() throws IOException, JSONException {
        JSONObject request = new JSONObject();
        request.put("accessToken", mAccount.accessToken);
        request.put("clientToken", mAccount.clientToken);
        request.put("requestUser", true);
        return postJson("/auth/refresh", request);
    }

    private String getPasswordWithTotp() {
        if(mTotp == null || mTotp.isEmpty()) return mPassword;
        return mPassword + ":" + mTotp;
    }

    private MinecraftAccount buildAccount(JSONObject response) throws JSONException {
        JSONObject selectedProfile = response.getJSONObject("selectedProfile");
        String username = selectedProfile.getString("name");
        MinecraftAccount account = MinecraftAccount.load(username);
        if(account == null) account = new MinecraftAccount();
        account.username = username;
        account.profileId = normalizeUuid(selectedProfile.getString("id"));
        account.accessToken = response.getString("accessToken");
        account.clientToken = response.optString("clientToken", account.clientToken);
        account.isMicrosoft = false;
        account.isElyBy = true;
        account.msaRefreshToken = "0";
        account.xuid = null;
        account.expiresAt = getJwtExpiresAt(account.accessToken);
        account.updateSkinFace();
        return account;
    }

    private String normalizeUuid(String uuid) {
        String rawUuid = uuid.replace("-", "");
        if(rawUuid.length() != 32) return uuid;
        return rawUuid.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)", "$1-$2-$3-$4-$5"
        );
    }

    private long getJwtExpiresAt(String jwt) {
        try {
            String[] parts = jwt.split("\\.");
            if(parts.length < 2) return 0;
            byte[] payloadBytes = Base64.decode(parts[1], Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
            JSONObject payload = new JSONObject(new String(payloadBytes, StandardCharsets.UTF_8));
            long exp = payload.optLong("exp", 0);
            return exp > 0 ? exp * 1000L : 0;
        }catch (Exception e) {
            return 0;
        }
    }

    private JSONObject postJson(String endpoint, JSONObject request) throws IOException, JSONException {
        String requestString = request.toString();
        HttpURLConnection connection = (HttpURLConnection) new URL(ELYBY_AUTH_URL + endpoint).openConnection();
        setCommonProperties(connection, requestString);
        connection.connect();
        try(OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write(requestString.getBytes(StandardCharsets.UTF_8));
        }
        try {
            if(connection.getResponseCode() >= 200 && connection.getResponseCode() < 300) {
                return new JSONObject(Tools.read(connection.getInputStream()));
            }
            throw getResponseThrowable(connection);
        } finally {
            connection.disconnect();
        }
    }

    private static void setCommonProperties(HttpURLConnection connection, String requestString) {
        connection.setConnectTimeout(CONNECTION_TIMEOUT);
        connection.setReadTimeout(CONNECTION_TIMEOUT);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("charset", "utf-8");
        connection.setRequestProperty("Content-Length", Integer.toString(requestString.getBytes(StandardCharsets.UTF_8).length));
        try {
            connection.setRequestMethod("POST");
        }catch (ProtocolException e) {
            Log.e("ElyByAuth", e.toString());
        }
        connection.setUseCaches(false);
        connection.setDoInput(true);
        connection.setDoOutput(true);
    }

    private RuntimeException getResponseThrowable(HttpURLConnection connection) throws IOException {
        String responseMessage = connection.getResponseMessage();
        String responseContents = connection.getErrorStream() == null ? null : Tools.read(connection.getErrorStream());
        if(Tools.isValidString(responseContents)) {
            try {
                JSONObject responseJson = new JSONObject(responseContents);
                responseMessage = responseJson.optString("errorMessage", responseMessage);
                String cause = responseJson.optString("cause", null);
                if(Tools.isValidString(cause)) responseMessage += " (" + cause + ")";
            }catch (JSONException ignored) {
                responseMessage = responseContents;
            }
        }
        return new RuntimeException(responseMessage);
    }

    private void notifyProgress(@Nullable ProgressListener listener, int step) {
        if(listener != null) {
            Tools.runOnUiThread(() -> listener.onLoginProgress(step));
        }
        ProgressLayout.setProgress(ProgressLayout.AUTHENTICATE_MICROSOFT, step * 20);
    }
}
