package com.kdt.mcgui;

import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.*;

import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

public class MineButton extends androidx.appcompat.widget.AppCompatButton {
	
	public MineButton(Context ctx) {
		this(ctx, null);
	}
	
	public MineButton(Context ctx, AttributeSet attrs) {
		super(ctx, attrs);
		init();
	}

	public void init() {
		setTypeface(ResourcesCompat.getFont(getContext(), R.font.noto_sans_bold));
		setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.mine_button_background, null));
		setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen._13ssp));
		applyCustomColor();
	}

	public void applyCustomColor() {
		if (LauncherPreferences.DEFAULT_PREF == null) return;
		int color = LauncherPreferences.PREF_BUTTON_COLOR;
		Drawable bg = getBackground();
		if (bg != null) {
			bg = DrawableCompat.wrap(bg.mutate());
			DrawableCompat.setTintList(bg, ColorStateList.valueOf(color));
		}
		setTextColor(LauncherPreferences.PREF_BUTTON_TEXT_COLOR);
	}

}
