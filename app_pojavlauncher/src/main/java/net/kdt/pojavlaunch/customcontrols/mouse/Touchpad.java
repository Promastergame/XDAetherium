package net.kdt.pojavlaunch.customcontrols.mouse;

import static net.kdt.pojavlaunch.Tools.currentDisplayMetrics;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;

import net.kdt.pojavlaunch.GrabListener;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import org.lwjgl.glfw.CallbackBridge;

/**
 * Class dealing with the virtual mouse
 */
public class Touchpad extends View implements GrabListener, AbstractTouchpad {
    /* Whether the Touchpad should be displayed */
    private boolean mDisplayState;
    /* Mouse pointer icon used by the touchpad */
    private Drawable mMousePointerDrawable;
    private float mMouseX, mMouseY;
    public Touchpad(@NonNull Context context) {
        this(context, null);
    }

    public Touchpad(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    /** Enable the touchpad */
    private void _enable(){
        setVisibility(VISIBLE);
        if (mMousePointerDrawable != null) {
            mMousePointerDrawable.setVisible(true, false);
        }
        placeMouseAt(currentDisplayMetrics.widthPixels / 2f, currentDisplayMetrics.heightPixels / 2f);
    }

    /** Disable the touchpad and hides the mouse */
    private void _disable(){
        setVisibility(GONE);
        if (mMousePointerDrawable != null) {
            mMousePointerDrawable.setVisible(false, false);
        }
    }

    /** @return The new state, enabled or disabled */
    public boolean switchState(){
        mDisplayState = !mDisplayState;
        if(!CallbackBridge.isGrabbing()) {
            if(mDisplayState) _enable();
            else _disable();
        }
        return mDisplayState;
    }

    public void placeMouseAt(float x, float y) {
        mMouseX = x;
        mMouseY = y;
        updateMousePosition();
    }

    private void sendMousePosition() {
        CallbackBridge.sendCursorPos((mMouseX * LauncherPreferences.PREF_SCALE_FACTOR), (mMouseY * LauncherPreferences.PREF_SCALE_FACTOR));
    }

    private void updateMousePosition() {
        sendMousePosition();
        // I wanted to implement a dirty rect for this, but it is ignored since API level 21
        // (which is our min API)
        // Let's hope the "internally calculated area" is good enough.
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.translate(mMouseX, mMouseY);
        mMousePointerDrawable.draw(canvas);
    }

    private void init(){
        // Setup mouse pointer
        String customCursorPath = null;
        String selectedKey = "standard";
        if (LauncherPreferences.DEFAULT_PREF != null) {
            customCursorPath = LauncherPreferences.DEFAULT_PREF.getString("custom_cursor_path", null);
            selectedKey = LauncherPreferences.DEFAULT_PREF.getString("custom_cursor_style_key", "standard");
        }
        if ("standard".equals(selectedKey)) {
            mMousePointerDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_mouse_pointer, getContext().getTheme());
        } else if ("ani".equals(selectedKey)) {
            mMousePointerDrawable = new net.kdt.pojavlaunch.utils.AnimatedCursorDrawable(getContext(), "01-normal-select.ani", true);
        } else if ("custom_ani".equals(selectedKey) && customCursorPath != null && new java.io.File(customCursorPath).exists()) {
            mMousePointerDrawable = new net.kdt.pojavlaunch.utils.AnimatedCursorDrawable(getContext(), customCursorPath, false);
        } else if (customCursorPath != null && new java.io.File(customCursorPath).exists()) {
            mMousePointerDrawable = Drawable.createFromPath(customCursorPath);
        }
        if (mMousePointerDrawable == null) {
            mMousePointerDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_mouse_pointer, getContext().getTheme());
        }
        // For some reason it's annotated as Nullable even though it doesn't seem to actually
        // ever return null
        assert mMousePointerDrawable != null;
        
        mMousePointerDrawable.setCallback(this);
        mMousePointerDrawable.setVisible(getVisibility() == VISIBLE, false);

        int intrinsicWidth = mMousePointerDrawable.getIntrinsicWidth();
        int intrinsicHeight = mMousePointerDrawable.getIntrinsicHeight();
        float scale = LauncherPreferences.PREF_MOUSESCALE;
        int width;
        int height;
        
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
            width = (int) (36 * scale);
            height = (int) (54 * scale);
        } else {
            float aspect = (float) intrinsicHeight / intrinsicWidth;
            width = (int) (36 * scale);
            height = (int) (36 * aspect * scale);
        }
        
        mMousePointerDrawable.setBounds(0, 0, width, height);
        setFocusable(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            setDefaultFocusHighlightEnabled(false);
        }

        // When the game is grabbing, we should not display the mouse
        disable();
        mDisplayState = false;
    }

    @Override
    public void onGrabState(boolean isGrabbing) {
        post(()->updateGrabState(isGrabbing));
    }
    private void updateGrabState(boolean isGrabbing) {
        if(!isGrabbing) {
            if(mDisplayState && getVisibility() != VISIBLE) _enable();
            if(!mDisplayState && getVisibility() == VISIBLE) _disable();
        }else{
            if(getVisibility() != View.GONE) _disable();
        }
    }

    @Override
    public boolean getDisplayState() {
        return mDisplayState;
    }

    @Override
    public void applyMotionVector(float x, float y) {
        mMouseX = Math.max(0, Math.min(currentDisplayMetrics.widthPixels, mMouseX + x * LauncherPreferences.PREF_MOUSESPEED));
        mMouseY = Math.max(0, Math.min(currentDisplayMetrics.heightPixels, mMouseY + y * LauncherPreferences.PREF_MOUSESPEED));
        updateMousePosition();
    }

    @Override
    public void enable(boolean supposed) {
        if(mDisplayState) return;
        mDisplayState = true;
        if(supposed && CallbackBridge.isGrabbing() && LauncherPreferences.PREF_MOUSE_GRAB_FORCE) return;
        _enable();
    }

    @Override
    public void disable() {
        if(!mDisplayState) return;
        mDisplayState = false;
        _disable();
    }

    @Override
    protected boolean verifyDrawable(@NonNull Drawable who) {
        return who == mMousePointerDrawable || super.verifyDrawable(who);
    }

    @Override
    public void invalidateDrawable(@NonNull Drawable who) {
        if (who == mMousePointerDrawable) {
            invalidate();
        } else {
            super.invalidateDrawable(who);
        }
    }
}
