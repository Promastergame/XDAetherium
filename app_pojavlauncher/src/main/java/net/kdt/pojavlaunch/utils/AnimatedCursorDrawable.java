package net.kdt.pojavlaunch.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class AnimatedCursorDrawable extends Drawable implements Runnable {
    private final List<Bitmap> mFrames = new ArrayList<>();
    private final List<Integer> mDurations = new ArrayList<>(); // in milliseconds
    private int mCurrentFrameIndex = 0;
    private boolean mRunning = false;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    public AnimatedCursorDrawable(Context context, String pathOrAsset, boolean isAsset) {
        try {
            byte[] fileBytes;
            if (isAsset) {
                try (InputStream in = context.getAssets().open(pathOrAsset)) {
                    fileBytes = CursorParser.readAllBytes(in);
                }
            } else {
                try (InputStream in = new FileInputStream(new File(pathOrAsset))) {
                    fileBytes = CursorParser.readAllBytes(in);
                }
            }
            loadFromBytes(fileBytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadFromBytes(byte[] bytes) {
        if (bytes == null) return;
        List<CursorParser.AniFrame> frames = CursorParser.decodeAniAllFrames(bytes);
        if (frames != null && !frames.isEmpty()) {
            for (CursorParser.AniFrame frame : frames) {
                if (frame.bitmap != null) {
                    mFrames.add(frame.bitmap);
                    mDurations.add(frame.durationMs);
                }
            }
        }
        if (!mFrames.isEmpty()) {
            start();
        }
    }

    public void start() {
        if (mFrames.size() <= 1) return;
        mRunning = true;
        mHandler.removeCallbacks(this);
        scheduleNextFrame();
    }

    public void stop() {
        mRunning = false;
        mHandler.removeCallbacks(this);
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        if (visible) {
            start();
        } else {
            stop();
        }
        return super.setVisible(visible, restart);
    }

    private void scheduleNextFrame() {
        if (!mRunning) return;
        int delay = mDurations.get(mCurrentFrameIndex);
        mHandler.postAtTime(this, SystemClock.uptimeMillis() + delay);
    }

    @Override
    public void run() {
        if (!mRunning) return;
        mCurrentFrameIndex = (mCurrentFrameIndex + 1) % mFrames.size();
        invalidateSelf();
        scheduleNextFrame();
    }

    @Override
    public void draw(Canvas canvas) {
        if (mFrames.isEmpty()) return;
        Bitmap current = mFrames.get(mCurrentFrameIndex);
        if (current != null && !current.isRecycled()) {
            canvas.drawBitmap(current, null, getBounds(), mPaint);
        }
    }

    @Override
    public int getIntrinsicWidth() {
        if (!mFrames.isEmpty()) {
            return mFrames.get(0).getWidth();
        }
        return -1;
    }

    @Override
    public int getIntrinsicHeight() {
        if (!mFrames.isEmpty()) {
            return mFrames.get(0).getHeight();
        }
        return -1;
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mPaint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }

    public void recycle() {
        stop();
        for (Bitmap bmp : mFrames) {
            if (bmp != null && !bmp.isRecycled()) {
                bmp.recycle();
            }
        }
        mFrames.clear();
    }
}
