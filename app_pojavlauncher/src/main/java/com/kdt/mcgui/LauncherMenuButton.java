package com.kdt.mcgui;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import fr.spse.extended_view.ExtendedButton;

public class LauncherMenuButton extends ExtendedButton {

    public LauncherMenuButton(@NonNull Context context) {
        super(context);
        setSettings();
    }
    public LauncherMenuButton(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setSettings();
    }


    /** Set style stuff */
    private void setSettings(){
        applyTextColor();
        Resources resources = getContext().getResources();

        int padding = resources.getDimensionPixelSize(R.dimen._22sdp);
        setCompoundDrawablePadding(padding);
        setPaddingRelative(padding, 0, 0, 0);
        setGravity(Gravity.CENTER_VERTICAL);

        setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen._12ssp));

        // Set drawable size
        int[] sizes = getExtendedViewData().getSizeCompounds();
        sizes[0] = resources.getDimensionPixelSize(R.dimen._30sdp);
        getExtendedViewData().setSizeCompounds(sizes);
        postProcessDrawables();
    }

    public void applyTextColor() {
        if (LauncherPreferences.DEFAULT_PREF == null) return;
        setTextColor(LauncherPreferences.PREF_BUTTON_TEXT_COLOR);
    }
}
