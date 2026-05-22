package net.kdt.pojavlaunch.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.CursorParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Stack;

public class CursorStoreFragment extends Fragment {
    public static final String TAG = "CursorStoreFragment";

    // 4 Preset Cards
    private View mCardStandard, mCardScifi, mCardV3, mCardAni;
    private ImageView mPreviewStandard, mPreviewScifi, mPreviewV3, mPreviewAni;

    // Custom Card
    private View mCardCustom;
    private ImageView mPreviewCustom;
    private com.kdt.mcgui.MineButton mBtnCustomUpload, mBtnCustomEditCurrent;

    // Generated bitmaps
    private Bitmap mScifiBitmap;
    private Bitmap mV3Bitmap;
    private Bitmap mAniBitmap;

    // Test zone elements
    private SeekBar mSeekBarSize;
    private TextView mTxtSizeVal;
    private FrameLayout mCursorTestPad;
    private ImageView mTestPointer;

    // Editor elements
    private View mPanelEditor;
    private ImageView mEditorCheckboard;
    private FrameLayout mEditorCheckboardContainer;

    private com.kdt.mcgui.MineButton mBtnFlood, mBtnManualEraser, mBtnAutoCut;
    private TextView mTxtSliderTitle, mTxtSliderValue;
    private SeekBar mSeekBrushOrThreshold;
    private com.kdt.mcgui.MineButton mBtnUndo;

    // Editor State
    private Bitmap mEditingBitmap;
    private static final int MODE_NONE = 0;
    private static final int MODE_MANUAL_ERASER = 1;
    private static final int MODE_FLOOD_ERASE = 2;
    private int mCurrentEditorMode = MODE_FLOOD_ERASE;

    private int mEraserRadius = 15;
    private int mThreshold = 45;

    // Undo History Stack
    private final Stack<Bitmap> mUndoStack = new Stack<>();

    private final ActivityResultLauncher<String> mPickCustomCursor = registerForActivityResult(
            new ActivityResultContracts.GetContent(),
            this::onCustomCursorPicked
    );

    public CursorStoreFragment() {
        super(R.layout.fragment_cursor_store);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Grid cards
        mCardStandard = view.findViewById(R.id.card_cursor_standard);
        mCardScifi = view.findViewById(R.id.card_cursor_scifi);
        mCardV3 = view.findViewById(R.id.card_cursor_v3);
        mCardAni = view.findViewById(R.id.card_cursor_ani);
        mCardCustom = view.findViewById(R.id.card_cursor_custom);

        // Previews
        mPreviewStandard = view.findViewById(R.id.preview_standard);
        mPreviewScifi = view.findViewById(R.id.preview_scifi);
        mPreviewV3 = view.findViewById(R.id.preview_v3);
        mPreviewAni = view.findViewById(R.id.preview_ani);
        mPreviewCustom = view.findViewById(R.id.preview_custom);

        // Buttons
        mBtnCustomUpload = view.findViewById(R.id.btn_custom_upload);
        mBtnCustomEditCurrent = view.findViewById(R.id.btn_custom_edit_current);

        // Test zone
        mSeekBarSize = view.findViewById(R.id.seek_cursor_size);
        mTxtSizeVal = view.findViewById(R.id.txt_cursor_size_val);
        mCursorTestPad = view.findViewById(R.id.cursor_test_pad);
        mTestPointer = view.findViewById(R.id.test_pointer);

        // Editor Panel
        mPanelEditor = view.findViewById(R.id.panel_cursor_editor);
        mEditorCheckboard = view.findViewById(R.id.editor_checkboard);
        mEditorCheckboardContainer = view.findViewById(R.id.editor_checkboard_container);

        mBtnFlood = view.findViewById(R.id.btn_editor_flood);
        mBtnManualEraser = view.findViewById(R.id.btn_editor_manual_eraser);
        mBtnAutoCut = view.findViewById(R.id.btn_editor_autocut);

        mTxtSliderTitle = view.findViewById(R.id.txt_slider_title);
        mTxtSliderValue = view.findViewById(R.id.txt_slider_value);
        mSeekBrushOrThreshold = view.findViewById(R.id.seek_brush_or_threshold);
        mBtnUndo = view.findViewById(R.id.btn_editor_undo);

        // Setup Photoshop Checkered Background
        setCheckboardBackground(mEditorCheckboardContainer);

        // Load Preset Bitmaps and initialize
        generateBitmaps();
        setupPreviews();

        // Preset Clicks
        mCardStandard.setOnClickListener(v -> selectCursor("standard"));
        mCardScifi.setOnClickListener(v -> selectCursor("scifi"));
        mCardV3.setOnClickListener(v -> selectPreset("v3", "Normal Select v3.0.cur"));
        mCardAni.setOnClickListener(v -> selectPreset("ani", "01-normal-select.ani"));

        // Custom Clicks
        mBtnCustomUpload.setOnClickListener(v -> mPickCustomCursor.launch("*/*"));
        mBtnCustomEditCurrent.setOnClickListener(v -> openEditorForCurrentCustom());

        // Size slider logic
        int currentSizePercent = (int) (LauncherPreferences.PREF_MOUSESCALE * 100);
        mSeekBarSize.setProgress(Math.max(0, currentSizePercent - 20));
        mTxtSizeVal.setText(currentSizePercent + "%");

        mSeekBarSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int scalePercent = progress + 20;
                mTxtSizeVal.setText(scalePercent + "%");
                float scaleVal = scalePercent / 100f;
                LauncherPreferences.PREF_MOUSESCALE = scaleVal;
                if (LauncherPreferences.DEFAULT_PREF != null) {
                    LauncherPreferences.DEFAULT_PREF.edit()
                            .putInt("mousescale", scalePercent)
                            .apply();
                }
                updateTestPointer();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // Test pad touchscreen drags
        mCursorTestPad.setOnTouchListener((v, event) -> {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                mTestPointer.setVisibility(View.VISIBLE);
                float x = event.getX() - (mTestPointer.getWidth() / 2f);
                float y = event.getY() - (mTestPointer.getHeight() / 2f);
                x = Math.max(0, Math.min(mCursorTestPad.getWidth() - mTestPointer.getWidth(), x));
                y = Math.max(0, Math.min(mCursorTestPad.getHeight() - mTestPointer.getHeight(), y));
                mTestPointer.setX(x);
                mTestPointer.setY(y);
                return true;
            }
            return false;
        });

        // Editor Painting / Flood-Fill Erasing Touch Interception
        mEditorCheckboard.setOnTouchListener((v, event) -> {
            if (mEditingBitmap == null || mCurrentEditorMode == MODE_NONE) return false;
            int action = event.getAction();

            float touchX = event.getX();
            float touchY = event.getY();

            float intrinsicWidth = mEditorCheckboard.getDrawable().getIntrinsicWidth();
            float intrinsicHeight = mEditorCheckboard.getDrawable().getIntrinsicHeight();
            float viewWidth = mEditorCheckboard.getWidth();
            float viewHeight = mEditorCheckboard.getHeight();

            float scale = Math.min(viewWidth / intrinsicWidth, viewHeight / intrinsicHeight);
            float dx = (viewWidth - intrinsicWidth * scale) / 2f;
            float dy = (viewHeight - intrinsicHeight * scale) / 2f;

            float bitmapX = (touchX - dx) / scale;
            float bitmapY = (touchY - dy) / scale;

            if (mCurrentEditorMode == MODE_MANUAL_ERASER) {
                if (action == MotionEvent.ACTION_DOWN) {
                    saveToHistory();
                    return true;
                }
                if (action == MotionEvent.ACTION_MOVE) {
                    boolean changed = false;
                    for (int x = (int) (bitmapX - mEraserRadius); x <= (int) (bitmapX + mEraserRadius); x++) {
                        for (int y = (int) (bitmapY - mEraserRadius); y <= (int) (bitmapY + mEraserRadius); y++) {
                            if (x >= 0 && x < mEditingBitmap.getWidth() && y >= 0 && y < mEditingBitmap.getHeight()) {
                                double dist = Math.sqrt((x - bitmapX) * (x - bitmapX) + (y - bitmapY) * (y - bitmapY));
                                if (dist <= mEraserRadius) {
                                    if (mEditingBitmap.getPixel(x, y) != Color.TRANSPARENT) {
                                        mEditingBitmap.setPixel(x, y, Color.TRANSPARENT);
                                        changed = true;
                                    }
                                }
                            }
                        }
                    }
                    if (changed) {
                        mEditorCheckboard.setImageBitmap(mEditingBitmap);
                        mEditorCheckboard.invalidate();
                    }
                    return true;
                }
            } else if (mCurrentEditorMode == MODE_FLOOD_ERASE) {
                if (action == MotionEvent.ACTION_DOWN) {
                    int bx = (int) bitmapX;
                    int by = (int) bitmapY;
                    if (bx >= 0 && bx < mEditingBitmap.getWidth() && by >= 0 && by < mEditingBitmap.getHeight()) {
                        saveToHistory();
                        performFloodErase(bx, by, mThreshold);
                    }
                    return true;
                }
            }
            return false;
        });

        // Tool Mode Selection Clickers
        mBtnFlood.setOnClickListener(v -> selectToolMode(MODE_FLOOD_ERASE));
        mBtnManualEraser.setOnClickListener(v -> selectToolMode(MODE_MANUAL_ERASER));
        mBtnAutoCut.setOnClickListener(v -> {
            saveToHistory();
            performAutoCutBackground();
        });

        // Adaptive slider listener
        mSeekBrushOrThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (mCurrentEditorMode == MODE_FLOOD_ERASE) {
                    mThreshold = Math.max(1, progress);
                    mTxtSliderValue.setText(String.valueOf(mThreshold));
                } else if (mCurrentEditorMode == MODE_MANUAL_ERASER) {
                    mEraserRadius = Math.max(1, progress);
                    mTxtSliderValue.setText(mEraserRadius + " px");
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        // History Actions
        mBtnUndo.setOnClickListener(v -> performUndo());

        // Cancel / Save
        view.findViewById(R.id.btn_editor_cancel).setOnClickListener(v -> closeEditorPanel(false));
        view.findViewById(R.id.btn_editor_save).setOnClickListener(v -> closeEditorPanel(true));

        highlightActiveCard();
        updateTestPointer();
    }

    private void generateBitmaps() {
        Paint paint = new Paint();
        mScifiBitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Canvas cScifi = new Canvas(mScifiBitmap);
        drawSciFiGlow(cScifi, paint);

        // Preload V3 & Ani assets for UI Previews
        Context context = getContext();
        if (context == null) return;
        try {
            try (InputStream in = context.getAssets().open("Normal Select v3.0.cur")) {
                mV3Bitmap = CursorParser.decodeCursor(in);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            try (InputStream in = context.getAssets().open("01-normal-select.ani")) {
                mAniBitmap = CursorParser.decodeCursor(in);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void setupPreviews() {
        Context context = getContext();
        if (context == null) return;

        Drawable standardDrawable = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_mouse_pointer, context.getTheme());
        mPreviewStandard.setImageDrawable(standardDrawable);
        mPreviewScifi.setImageBitmap(mScifiBitmap);

        if (mV3Bitmap != null) {
            mPreviewV3.setImageBitmap(mV3Bitmap);
        }
        if (mAniBitmap != null) {
            mPreviewAni.setImageDrawable(new net.kdt.pojavlaunch.utils.AnimatedCursorDrawable(context, "01-normal-select.ani", true));
        }

        updateCustomPreview();
    }

    private void updateCustomPreview() {
        Context context = getContext();
        if (context == null) return;

        String path = LauncherPreferences.DEFAULT_PREF.getString("custom_cursor_path", null);
        String selectedKey = LauncherPreferences.DEFAULT_PREF.getString("custom_cursor_style_key", "standard");
        if (path != null && new File(path).exists() && !"standard".equals(selectedKey) && !"scifi".equals(selectedKey) && !"v3".equals(selectedKey) && !"ani".equals(selectedKey)) {
            if ("custom_ani".equals(selectedKey)) {
                mPreviewCustom.setImageDrawable(new net.kdt.pojavlaunch.utils.AnimatedCursorDrawable(context, path, false));
            } else {
                mPreviewCustom.setImageDrawable(Drawable.createFromPath(path));
            }
        } else {
            mPreviewCustom.setImageDrawable(ResourcesCompat.getDrawable(getResources(), R.drawable.ic_mouse_pointer, context.getTheme()));
        }
    }

    private void updateTestPointer() {
        Context context = getContext();
        if (context == null || mTestPointer == null) return;

        Drawable d = null;
        String path = LauncherPreferences.DEFAULT_PREF.getString("custom_cursor_path", null);
        String selectedKey = LauncherPreferences.DEFAULT_PREF.getString("custom_cursor_style_key", "standard");

        if ("standard".equals(selectedKey)) {
            d = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_mouse_pointer, context.getTheme());
        } else if ("scifi".equals(selectedKey)) {
            d = new BitmapDrawable(getResources(), mScifiBitmap);
        } else if ("v3".equals(selectedKey) && mV3Bitmap != null) {
            d = new BitmapDrawable(getResources(), mV3Bitmap);
        } else if ("ani".equals(selectedKey)) {
            d = new net.kdt.pojavlaunch.utils.AnimatedCursorDrawable(context, "01-normal-select.ani", true);
        } else if ("custom_ani".equals(selectedKey) && path != null && new File(path).exists()) {
            d = new net.kdt.pojavlaunch.utils.AnimatedCursorDrawable(context, path, false);
        } else if (path != null && new File(path).exists()) {
            d = Drawable.createFromPath(path);
        }

        if (d == null) {
            d = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_mouse_pointer, context.getTheme());
        }

        mTestPointer.setImageDrawable(d);

        float scale = LauncherPreferences.PREF_MOUSESCALE;
        int intrinsicWidth = d.getIntrinsicWidth();
        int intrinsicHeight = d.getIntrinsicHeight();
        int width, height;
        if (intrinsicWidth <= 0 || intrinsicHeight <= 0) {
            width = (int) (36 * scale);
            height = (int) (54 * scale);
        } else {
            float aspect = (float) intrinsicHeight / intrinsicWidth;
            width = (int) (36 * scale);
            height = (int) (36 * aspect * scale);
        }

        android.view.ViewGroup.LayoutParams params = mTestPointer.getLayoutParams();
        params.width = width;
        params.height = height;
        mTestPointer.setLayoutParams(params);
    }

    private void selectCursor(String type) {
        Context context = getContext();
        if (context == null) return;

        File cursorFile = new File(context.getFilesDir(), "custom_cursor.png");
        try {
            if ("standard".equals(type)) {
                if (cursorFile.exists()) {
                    cursorFile.delete();
                }
                LauncherPreferences.DEFAULT_PREF.edit()
                        .remove("custom_cursor_path")
                        .putString("custom_cursor_style_key", "standard")
                        .apply();
                Toast.makeText(context, getString(R.string.cursor_store_selected_standard), Toast.LENGTH_SHORT).show();
            } else if ("scifi".equals(type) && mScifiBitmap != null) {
                try (OutputStream out = new FileOutputStream(cursorFile)) {
                    mScifiBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }
                LauncherPreferences.DEFAULT_PREF.edit()
                        .putString("custom_cursor_path", cursorFile.getAbsolutePath())
                        .putString("custom_cursor_style_key", "scifi")
                        .apply();
                Toast.makeText(context, getString(R.string.cursor_store_success), Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(context, getString(R.string.cursor_store_error) + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        highlightActiveCard();
        updateTestPointer();
    }

    private void selectPreset(String key, String assetName) {
        Context context = getContext();
        if (context == null) return;

        try {
            if ("ani".equals(key)) {
                LauncherPreferences.DEFAULT_PREF.edit()
                        .remove("custom_cursor_path")
                        .putString("custom_cursor_style_key", "ani")
                        .apply();
                Toast.makeText(context, getString(R.string.cursor_store_success), Toast.LENGTH_SHORT).show();
            } else {
                File cursorFile = new File(context.getFilesDir(), "custom_cursor_" + key + ".png");
                Bitmap bmp = null;
                if ("v3".equals(key)) bmp = mV3Bitmap;

                if (bmp == null) {
                    try (InputStream in = context.getAssets().open(assetName)) {
                        bmp = CursorParser.decodeCursor(in);
                    }
                }

                if (bmp != null) {
                    try (OutputStream out = new FileOutputStream(cursorFile)) {
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
                    }
                    LauncherPreferences.DEFAULT_PREF.edit()
                            .putString("custom_cursor_path", cursorFile.getAbsolutePath())
                            .putString("custom_cursor_style_key", key)
                            .apply();
                    Toast.makeText(context, getString(R.string.cursor_store_success), Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(context, getString(R.string.cursor_store_fail_preset), Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Toast.makeText(context, getString(R.string.cursor_store_error) + e.getMessage(), Toast.LENGTH_LONG).show();
        }

        highlightActiveCard();
        updateTestPointer();
    }

    private void onCustomCursorPicked(Uri uri) {
        if (uri == null) return;
        Context context = getContext();
        if (context == null) return;

        try {
            byte[] fileBytes;
            try (InputStream in = context.getContentResolver().openInputStream(uri)) {
                if (in == null) throw new Exception("Failed to open stream");
                fileBytes = CursorParser.readAllBytes(in);
            }

            if (fileBytes == null || fileBytes.length < 4) {
                throw new Exception(getString(R.string.cursor_store_file_empty));
            }

            boolean isAnimated = false;
            Bitmap decodedBitmap = null;

            if (fileBytes.length >= 12 &&
                fileBytes[0] == 'R' && fileBytes[1] == 'I' && fileBytes[2] == 'F' && fileBytes[3] == 'F' &&
                fileBytes[8] == 'A' && fileBytes[9] == 'C' && fileBytes[10] == 'O' && fileBytes[11] == 'N') {
                isAnimated = true;
                List<CursorParser.AniFrame> frames = CursorParser.decodeAniAllFrames(fileBytes);
                if (frames != null && !frames.isEmpty()) {
                    decodedBitmap = frames.get(0).bitmap;
                }
            } else {
                try (java.io.ByteArrayInputStream bais = new java.io.ByteArrayInputStream(fileBytes)) {
                    decodedBitmap = CursorParser.decodeCursor(bais);
                }
            }

            if (decodedBitmap == null) {
                Toast.makeText(context, getString(R.string.cursor_store_invalid_format), Toast.LENGTH_LONG).show();
                return;
            }

            if (isAnimated) {
                File destFile = new File(context.getFilesDir(), "custom_cursor_user.ani");
                try (FileOutputStream fos = new FileOutputStream(destFile)) {
                    fos.write(fileBytes);
                }
                LauncherPreferences.DEFAULT_PREF.edit()
                        .putString("custom_cursor_path", destFile.getAbsolutePath())
                        .putString("custom_cursor_style_key", "custom_ani")
                        .apply();
            } else {
                File destFile = new File(context.getFilesDir(), "custom_cursor_user.png");
                try (FileOutputStream fos = new FileOutputStream(destFile)) {
                    decodedBitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                }
                LauncherPreferences.DEFAULT_PREF.edit()
                        .putString("custom_cursor_path", destFile.getAbsolutePath())
                        .putString("custom_cursor_style_key", "custom")
                        .apply();
            }

            mEditingBitmap = decodedBitmap.copy(Bitmap.Config.ARGB_8888, true);
            mEditorCheckboard.setImageBitmap(mEditingBitmap);
            mPanelEditor.setVisibility(View.VISIBLE);

            if (getActivity() != null) {
                getActivity().setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED);
            }

            mUndoStack.clear();
            updateUndoButtonState();
            selectToolMode(MODE_FLOOD_ERASE);

            Toast.makeText(context, getString(R.string.cursor_store_file_loaded), Toast.LENGTH_SHORT).show();
            highlightActiveCard();
            updateCustomPreview();
            updateTestPointer();
        } catch (Exception e) {
            Toast.makeText(context, getString(R.string.cursor_store_load_error_prefix) + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openEditorForCurrentCustom() {
        Context context = getContext();
        if (context == null) return;

        Bitmap activeBmp = null;
        String path = LauncherPreferences.DEFAULT_PREF.getString("custom_cursor_path", null);
        String selectedKey = LauncherPreferences.DEFAULT_PREF.getString("custom_cursor_style_key", "standard");

        if ("standard".equals(selectedKey)) {
            Drawable d = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_mouse_pointer, context.getTheme());
            activeBmp = drawableToBitmap(d);
        } else if ("scifi".equals(selectedKey)) {
            activeBmp = mScifiBitmap;
        } else if ("v3".equals(selectedKey)) {
            activeBmp = mV3Bitmap;
        } else if ("ani".equals(selectedKey)) {
            activeBmp = mAniBitmap;
        } else if ("custom_ani".equals(selectedKey) && path != null && new File(path).exists()) {
            try {
                byte[] bytes = CursorParser.readAllBytes(new java.io.FileInputStream(new File(path)));
                List<CursorParser.AniFrame> frames = CursorParser.decodeAniAllFrames(bytes);
                if (frames != null && !frames.isEmpty()) {
                    activeBmp = frames.get(0).bitmap;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else if (path != null && new File(path).exists()) {
            activeBmp = BitmapFactory.decodeFile(path);
        }

        if (activeBmp == null) {
            Drawable d = ResourcesCompat.getDrawable(getResources(), R.drawable.ic_mouse_pointer, context.getTheme());
            activeBmp = drawableToBitmap(d);
        }

        try {
            mEditingBitmap = activeBmp.copy(Bitmap.Config.ARGB_8888, true);
            mEditorCheckboard.setImageBitmap(mEditingBitmap);
            mPanelEditor.setVisibility(View.VISIBLE);

            if (getActivity() != null) {
                getActivity().setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED);
            }

            mUndoStack.clear();
            updateUndoButtonState();
            selectToolMode(MODE_FLOOD_ERASE);

            Toast.makeText(context, getString(R.string.cursor_store_loaded_into_editor), Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(context, getString(R.string.cursor_store_open_error_prefix) + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private Bitmap drawableToBitmap(Drawable d) {
        if (d instanceof BitmapDrawable) {
            return ((BitmapDrawable) d).getBitmap();
        }
        Bitmap bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        d.setBounds(0, 0, 64, 64);
        d.draw(canvas);
        return bitmap;
    }

    private void selectToolMode(int mode) {
        mCurrentEditorMode = mode;
        highlightActiveToolButton();

        if (mode == MODE_FLOOD_ERASE) {
            mTxtSliderTitle.setText(getString(R.string.cursor_editor_flood_threshold_title));
            mTxtSliderValue.setText(String.valueOf(mThreshold));
            mSeekBrushOrThreshold.setMax(150);
            mSeekBrushOrThreshold.setProgress(mThreshold);
        } else if (mode == MODE_MANUAL_ERASER) {
            mTxtSliderTitle.setText(getString(R.string.cursor_editor_eraser_size_title));
            mTxtSliderValue.setText(mEraserRadius + " px");
            mSeekBrushOrThreshold.setMax(100);
            mSeekBrushOrThreshold.setProgress(mEraserRadius);
        }
    }

    private void highlightActiveToolButton() {
        mBtnFlood.setAlpha(0.6f);
        mBtnManualEraser.setAlpha(0.6f);

        if (mCurrentEditorMode == MODE_FLOOD_ERASE) {
            mBtnFlood.setAlpha(1.0f);
        } else if (mCurrentEditorMode == MODE_MANUAL_ERASER) {
            mBtnManualEraser.setAlpha(1.0f);
        }
    }

    private void performAutoCutBackground() {
        if (mEditingBitmap == null) return;

        int width = mEditingBitmap.getWidth();
        int height = mEditingBitmap.getHeight();
        if (width <= 0 || height <= 0) return;

        int keyColor = mEditingBitmap.getPixel(0, 0);
        int kr = Color.red(keyColor);
        int kg = Color.green(keyColor);
        int kb = Color.blue(keyColor);
        int ka = Color.alpha(keyColor);

        // If corners are already transparent, we should find first solid pixel or default to white
        if (ka == 0) {
            kr = 255;
            kg = 255;
            kb = 255;
        }

        int cutCount = 0;
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                int px = mEditingBitmap.getPixel(x, y);
                int pr = Color.red(px);
                int pg = Color.green(px);
                int pb = Color.blue(px);
                int pa = Color.alpha(px);

                if (pa > 0) {
                    double dist = Math.sqrt((pr - kr) * (pr - kr) + (pg - kg) * (pg - kg) + (pb - kb) * (pb - kb));
                    if (dist <= mThreshold) {
                        mEditingBitmap.setPixel(x, y, Color.TRANSPARENT);
                        cutCount++;
                    }
                }
            }
        }

        mEditorCheckboard.setImageBitmap(mEditingBitmap);
        mEditorCheckboard.invalidate();
        Toast.makeText(getContext(), getString(R.string.cursor_editor_pixels_cut, cutCount), Toast.LENGTH_SHORT).show();
    }

    private void performFloodErase(int startX, int startY, double threshold) {
        if (mEditingBitmap == null) return;
        int width = mEditingBitmap.getWidth();
        int height = mEditingBitmap.getHeight();
        if (startX < 0 || startX >= width || startY < 0 || startY >= height) return;

        int targetColor = mEditingBitmap.getPixel(startX, startY);
        if (Color.alpha(targetColor) == 0) return;

        int tr = Color.red(targetColor);
        int tg = Color.green(targetColor);
        int tb = Color.blue(targetColor);

        boolean[][] visited = new boolean[width][height];
        java.util.Queue<android.graphics.Point> queue = new java.util.LinkedList<>();
        queue.add(new android.graphics.Point(startX, startY));
        visited[startX][startY] = true;

        int filledCount = 0;
        while (!queue.isEmpty()) {
            android.graphics.Point p = queue.poll();
            int x = p.x;
            int y = p.y;

            mEditingBitmap.setPixel(x, y, Color.TRANSPARENT);
            filledCount++;

            int[] dx = {0, 0, 1, -1};
            int[] dy = {1, -1, 0, 0};
            for (int i = 0; i < 4; i++) {
                int nx = x + dx[i];
                int ny = y + dy[i];

                if (nx >= 0 && nx < width && ny >= 0 && ny < height) {
                    if (!visited[nx][ny]) {
                        int px = mEditingBitmap.getPixel(nx, ny);
                        int pr = Color.red(px);
                        int pg = Color.green(px);
                        int pb = Color.blue(px);
                        int pa = Color.alpha(px);

                        if (pa > 0) {
                            double dist = Math.sqrt((pr - tr)*(pr - tr) + (pg - tg)*(pg - tg) + (pb - tb)*(pb - tb));
                            if (dist <= threshold) {
                                visited[nx][ny] = true;
                                queue.add(new android.graphics.Point(nx, ny));
                            }
                        }
                    }
                }
            }
        }

        mEditorCheckboard.setImageBitmap(mEditingBitmap);
        mEditorCheckboard.invalidate();
        Toast.makeText(getContext(), getString(R.string.cursor_store_flood_erased, filledCount), Toast.LENGTH_SHORT).show();
    }

    private void saveToHistory() {
        if (mEditingBitmap != null) {
            if (mUndoStack.size() >= 10) {
                Bitmap oldest = mUndoStack.remove(0);
                if (oldest != null && !oldest.isRecycled()) {
                    oldest.recycle();
                }
            }
            mUndoStack.push(mEditingBitmap.copy(mEditingBitmap.getConfig(), true));
            updateUndoButtonState();
        }
    }

    private void performUndo() {
        if (!mUndoStack.isEmpty()) {
            if (mEditingBitmap != null && !mEditingBitmap.isRecycled()) {
                mEditingBitmap.recycle();
            }
            mEditingBitmap = mUndoStack.pop();
            mEditorCheckboard.setImageBitmap(mEditingBitmap);
            mEditorCheckboard.invalidate();
            updateUndoButtonState();
            Toast.makeText(getContext(), getString(R.string.cursor_store_action_undone), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateUndoButtonState() {
        mBtnUndo.setEnabled(!mUndoStack.isEmpty());
        mBtnUndo.setAlpha(mUndoStack.isEmpty() ? 0.5f : 1.0f);
    }

    private void closeEditorPanel(boolean saveChanges) {
        Context context = getContext();
        if (context == null) return;

        if (saveChanges && mEditingBitmap != null) {
            File cursorFile = new File(context.getFilesDir(), "custom_cursor_user.png");
            try {
                try (OutputStream out = new FileOutputStream(cursorFile)) {
                    mEditingBitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
                }

                LauncherPreferences.DEFAULT_PREF.edit()
                        .putString("custom_cursor_path", cursorFile.getAbsolutePath())
                        .putString("custom_cursor_style_key", "custom")
                        .apply();

                updateCustomPreview();
                highlightActiveCard();
                updateTestPointer();

                Toast.makeText(context, getString(R.string.cursor_store_save_success), Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(context, getString(R.string.cursor_store_save_fail) + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }

        // Clean up editing bitmaps
        if (mEditingBitmap != null && !mEditingBitmap.isRecycled()) {
            mEditingBitmap.recycle();
        }
        mEditingBitmap = null;

        // Clear history stack
        while (!mUndoStack.isEmpty()) {
            Bitmap b = mUndoStack.pop();
            if (b != null && !b.isRecycled()) {
                b.recycle();
            }
        }

        mPanelEditor.setVisibility(View.GONE);

        // Restore screen auto rotation
        if (getActivity() != null) {
            getActivity().setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }
    }

    private void highlightActiveCard() {
        String path = LauncherPreferences.DEFAULT_PREF.getString("custom_cursor_path", null);
        String selectedKey = LauncherPreferences.DEFAULT_PREF.getString("custom_cursor_style_key", "standard");

        mCardStandard.setAlpha(0.6f);
        mCardScifi.setAlpha(0.6f);
        mCardV3.setAlpha(0.6f);
        mCardAni.setAlpha(0.6f);
        mCardCustom.setAlpha(0.6f);

        if (path == null || "standard".equals(selectedKey)) {
            mCardStandard.setAlpha(1.0f);
        } else if ("scifi".equals(selectedKey)) {
            mCardScifi.setAlpha(1.0f);
        } else if ("v3".equals(selectedKey)) {
            mCardV3.setAlpha(1.0f);
        } else if ("ani".equals(selectedKey)) {
            mCardAni.setAlpha(1.0f);
        } else {
            mCardCustom.setAlpha(1.0f);
        }
    }

    private void setCheckboardBackground(View view) {
        int size = 16;
        Bitmap bmp = Bitmap.createBitmap(size * 2, size * 2, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint paint = new Paint();

        paint.setColor(0xFFE5E5E5);
        canvas.drawRect(0, 0, size * 2, size * 2, paint);

        paint.setColor(0xFFCCCCCC);
        canvas.drawRect(0, 0, size, size, paint);
        canvas.drawRect(size, size, size * 2, size * 2, paint);

        BitmapDrawable tiledDrawable = new BitmapDrawable(getResources(), bmp);
        tiledDrawable.setTileModeXY(android.graphics.Shader.TileMode.REPEAT, android.graphics.Shader.TileMode.REPEAT);
        view.setBackground(tiledDrawable);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (getActivity() != null) {
            getActivity().setRequestedOrientation(android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        }

        // Recycle generated bitmaps
        if (mScifiBitmap != null && !mScifiBitmap.isRecycled()) {
            mScifiBitmap.recycle();
            mScifiBitmap = null;
        }
        if (mV3Bitmap != null && !mV3Bitmap.isRecycled()) {
            mV3Bitmap.recycle();
            mV3Bitmap = null;
        }
        if (mAniBitmap != null && !mAniBitmap.isRecycled()) {
            mAniBitmap.recycle();
            mAniBitmap = null;
        }
    }

    private static void drawSciFiGlow(Canvas canvas, Paint paint) {
        paint.setAntiAlias(true);
        paint.setColor(0x3300FFFF);
        paint.setStyle(Paint.Style.FILL);
        android.graphics.Path glowPath = new android.graphics.Path();
        glowPath.moveTo(4, 4);
        glowPath.lineTo(48, 24);
        glowPath.lineTo(24, 48);
        glowPath.close();

        paint.setShadowLayer(8f, 0f, 0f, 0xFF00FFFF);
        canvas.drawPath(glowPath, paint);

        paint.clearShadowLayer();
        paint.setColor(0xFF00FFFF);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        canvas.drawPath(glowPath, paint);

        paint.setColor(0xFFFFFFFF);
        paint.setStyle(Paint.Style.FILL);
        android.graphics.Path corePath = new android.graphics.Path();
        corePath.moveTo(8, 8);
        corePath.lineTo(36, 22);
        corePath.lineTo(22, 36);
        corePath.close();
        canvas.drawPath(corePath, paint);
    }
}
