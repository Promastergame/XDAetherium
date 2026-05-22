package net.kdt.pojavlaunch.fragments;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class ThemeSettingsFragment extends Fragment {
    public static final String TAG = "ThemeSettingsFragment";

    private ImageView mWallpaperPreview;
    private final ActivityResultLauncher<String> mGetContent = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::onImagePicked
    );

    public ThemeSettingsFragment() {
        super(R.layout.fragment_theme_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mWallpaperPreview = view.findViewById(R.id.wallpaper_preview);
        View btnChoose = view.findViewById(R.id.btn_choose_wallpaper);
        View btnClear = view.findViewById(R.id.btn_clear_wallpaper);

        btnChoose.setOnClickListener(v -> mGetContent.launch("image/*"));
        btnClear.setOnClickListener(v -> clearWallpaper());

        updatePreview();
    }

    private void onImagePicked(Uri uri) {
        if (uri == null) return;
        Context context = getContext();
        if (context == null) return;

        try {
            File destFile = new File(context.getFilesDir(), "custom_wallpaper.png");
            try (InputStream in = context.getContentResolver().openInputStream(uri);
                 OutputStream out = new FileOutputStream(destFile)) {
                if (in == null) {
                    throw new Exception("Failed to open input stream");
                }
                byte[] buffer = new byte[4096];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }

            LauncherPreferences.DEFAULT_PREF.edit()
                    .putString("custom_background_path", destFile.getAbsolutePath())
                    .apply();

            updatePreview();

            Activity activity = getActivity();
            if (activity instanceof LauncherActivity) {
                ((LauncherActivity) activity).updateBackground();
            }

            Toast.makeText(context, getString(R.string.theme_wallpaper_success), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, getString(R.string.theme_wallpaper_error_prefix) + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void clearWallpaper() {
        Context context = getContext();
        if (context == null) return;

        File destFile = new File(context.getFilesDir(), "custom_wallpaper.png");
        if (destFile.exists()) {
            destFile.delete();
        }

        LauncherPreferences.DEFAULT_PREF.edit()
                .remove("custom_background_path")
                .apply();

        updatePreview();

        Activity activity = getActivity();
        if (activity instanceof LauncherActivity) {
            ((LauncherActivity) activity).updateBackground();
        }

        Toast.makeText(context, getString(R.string.theme_wallpaper_reset), Toast.LENGTH_SHORT).show();
    }

    private void updatePreview() {
        if (mWallpaperPreview == null) return;
        String path = LauncherPreferences.DEFAULT_PREF.getString("custom_background_path", null);
        if (path != null && new File(path).exists()) {
            mWallpaperPreview.setImageDrawable(Drawable.createFromPath(path));
        } else {
            mWallpaperPreview.setImageDrawable(null);
        }
    }
}
