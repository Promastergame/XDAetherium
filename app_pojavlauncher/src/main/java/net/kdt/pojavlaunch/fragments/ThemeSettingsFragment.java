package net.kdt.pojavlaunch.fragments;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.graphics.drawable.DrawableCompat;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.colorselector.ColorSelector;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

public class ThemeSettingsFragment extends Fragment {
    public static final String TAG = "ThemeSettingsFragment";

    private View mBackgroundColorPreview;

    public ThemeSettingsFragment() {
        super(R.layout.fragment_theme_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mBackgroundColorPreview = view.findViewById(R.id.background_color_preview);
        View btnChooseBgColor = view.findViewById(R.id.btn_choose_background_color);
        View btnChooseTextColor = view.findViewById(R.id.btn_choose_text_color);
        View btnReset = view.findViewById(R.id.btn_reset_theme);

        btnChooseBgColor.setOnClickListener(v -> openBackgroundColorPicker());
        btnChooseTextColor.setOnClickListener(v -> openTextColorPicker());
        btnReset.setOnClickListener(v -> resetTheme());

        updateBackgroundColorPreview();
    }

    private void openBackgroundColorPicker() {
        ViewGroup parent = (ViewGroup) requireActivity().getWindow().getDecorView().findViewById(android.R.id.content);
        int currentColor = LauncherPreferences.PREF_BACKGROUND_COLOR;
        ColorSelector colorSelector = new ColorSelector(requireContext(), parent, null);
        colorSelector.setAlphaEnabled(false);
        colorSelector.setColorSelectionListener(color -> {
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putInt("background_color", color)
                    .apply();
            LauncherPreferences.PREF_BACKGROUND_COLOR = color;
            updateBackgroundColorPreview();
            Activity activity = getActivity();
            if (activity instanceof LauncherActivity) {
                ((LauncherActivity) activity).updateBackgroundColor();
            }
        });
        colorSelector.show(false, currentColor);
    }

    private void openTextColorPicker() {
        ViewGroup parent = (ViewGroup) requireActivity().getWindow().getDecorView().findViewById(android.R.id.content);
        int currentColor = LauncherPreferences.PREF_BUTTON_TEXT_COLOR;
        ColorSelector colorSelector = new ColorSelector(requireContext(), parent, null);
        colorSelector.setAlphaEnabled(false);
        colorSelector.setColorSelectionListener(color -> {
            LauncherPreferences.DEFAULT_PREF.edit()
                    .putInt("button_text_color", color)
                    .apply();
            LauncherPreferences.PREF_BUTTON_TEXT_COLOR = color;
            refreshAllButtons();
            getView().post(() -> refreshAllTextColor());
        });
        colorSelector.show(false, currentColor);
    }

    private void resetTheme() {
        LauncherPreferences.DEFAULT_PREF.edit()
                .remove("background_color")
                .remove("button_text_color")
                .apply();
        LauncherPreferences.PREF_BACKGROUND_COLOR = 0xFF181818;
        LauncherPreferences.PREF_BUTTON_TEXT_COLOR = 0xFFFFFFFF;
        updateBackgroundColorPreview();
        Activity activity = getActivity();
        if (activity instanceof LauncherActivity) {
            ((LauncherActivity) activity).updateBackgroundColor();
        }
        refreshAllButtons();
        getView().post(() -> refreshAllTextColor());
    }

    private void refreshAllButtons() {
        Activity activity = getActivity();
        if (activity == null) return;
        refreshButtonGroup(activity.getWindow().getDecorView());
    }

    private void refreshButtonGroup(View v) {
        if (v instanceof com.kdt.mcgui.MineButton) {
            ((com.kdt.mcgui.MineButton) v).applyCustomColor();
        } else if (v instanceof com.kdt.mcgui.LauncherMenuButton) {
            ((com.kdt.mcgui.LauncherMenuButton) v).applyTextColor();
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                refreshButtonGroup(g.getChildAt(i));
            }
        }
    }

    private void refreshAllTextColor() {
        Activity activity = getActivity();
        if (activity == null) return;
        refreshTextGroup(activity.getWindow().getDecorView());
    }

    private void refreshTextGroup(View v) {
        if (v instanceof TextView && !(v instanceof com.kdt.mcgui.MineButton) && !(v instanceof com.kdt.mcgui.LauncherMenuButton)) {
            ((TextView) v).setTextColor(LauncherPreferences.PREF_BUTTON_TEXT_COLOR);
        } else if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                refreshTextGroup(g.getChildAt(i));
            }
        }
    }

    private void updateBackgroundColorPreview() {
        if (mBackgroundColorPreview == null) return;
        Drawable bg = mBackgroundColorPreview.getBackground();
        if (bg != null) {
            bg = DrawableCompat.wrap(bg.mutate());
            DrawableCompat.setTintList(bg, ColorStateList.valueOf(LauncherPreferences.PREF_BACKGROUND_COLOR));
            mBackgroundColorPreview.invalidate();
        }
    }
}
