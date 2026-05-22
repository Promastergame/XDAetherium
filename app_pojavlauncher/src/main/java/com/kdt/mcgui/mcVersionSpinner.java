package com.kdt.mcgui;

import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static net.kdt.pojavlaunch.fragments.ProfileEditorFragment.DELETED_PROFILE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.transition.Slide;
import android.transition.Transition;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ListView;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.res.ResourcesCompat;
import androidx.fragment.app.FragmentActivity;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.fragments.ProfileEditorFragment;
import net.kdt.pojavlaunch.fragments.ProfileTypeSelectFragment;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.profiles.ProfileAdapter;
import net.kdt.pojavlaunch.profiles.ProfileAdapterExtra;
import net.kdt.pojavlaunch.profiles.ProfileIconCache;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;

import fr.spse.extended_view.ExtendedTextView;

/**
 * A class implementing custom spinner like behavior, notably:
 * dropdown popup view with a custom direction.
 */
public class mcVersionSpinner extends ExtendedTextView {
    private static final int VERSION_SPINNER_PROFILE_CREATE = 0;
    public mcVersionSpinner(@NonNull Context context) {
        super(context);
        init();
    }
    public mcVersionSpinner(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }
    public mcVersionSpinner(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    /* The class is in charge of displaying its own list with adapter content being known in advance */
    private ListView mListView = null;
    private PopupWindow mPopupWindow = null;
    private Object mPopupAnimation;
    private int mSelectedIndex;

    private final ProfileAdapter mProfileAdapter = new ProfileAdapter(new ProfileAdapterExtra[]{
            new ProfileAdapterExtra(VERSION_SPINNER_PROFILE_CREATE,
                    R.string.create_profile,
                    ResourcesCompat.getDrawable(getResources(), R.drawable.ic_add, null)),
    });


    /** Set the selection AND saves it as a shared preference */
    public void setProfileSelection(int position){
        setSelection(position);
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE,
                        mProfileAdapter.getItem(position).toString())
                .apply();
    }

    public void setSelection(int position){
        if(mListView != null) mListView.setSelection(position);
        mProfileAdapter.setView(this, mProfileAdapter.getItem(position), false);
        mSelectedIndex = position;
    }

    public void openProfileEditor(FragmentActivity fragmentActivity) {
        Object currentSelection = mProfileAdapter.getItem(mSelectedIndex);
        if(currentSelection instanceof ProfileAdapterExtra) {
            performExtraAction((ProfileAdapterExtra) currentSelection);
        }else{
            String profileKey = (String) currentSelection;
            Tools.swapFragment(fragmentActivity, ProfileEditorFragment.class, ProfileEditorFragment.TAG, null);
        }
    }

    /** Reload profiles from the file, forcing the spinner to consider the new data */
    public void reloadProfiles(){
        mProfileAdapter.reloadProfiles();
    }

    /** Initialize various behaviors */
    private void init(){
        // Setup various attributes
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen._12ssp));
        setGravity(Gravity.CENTER_VERTICAL);
        int startPadding = getContext().getResources().getDimensionPixelOffset(R.dimen._17sdp);
        int endPadding = getContext().getResources().getDimensionPixelOffset(R.dimen._5sdp);
        setPaddingRelative(startPadding, 0, endPadding, 0);
        setCompoundDrawablePadding(startPadding);

        int profileIndex;
        String extra_value = (String) ExtraCore.consumeValue(ExtraConstants.REFRESH_VERSION_SPINNER);
        if(extra_value != null){
            profileIndex = extra_value.equals(DELETED_PROFILE) ? 0
                    : getProfileAdapter().resolveProfileIndex(extra_value);
        }else
            profileIndex = mProfileAdapter.resolveProfileIndex(
                    LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE,""));

        setProfileSelection(Math.max(0,profileIndex));

        // Popup window behavior
        setOnClickListener(new OnClickListener() {
            final int offset = -getContext().getResources().getDimensionPixelOffset(R.dimen._4sdp);
            @Override
            public void onClick(View v) {
                if(mPopupWindow == null) getPopupWindow();

                if(mPopupWindow.isShowing()){
                    mPopupWindow.dismiss();
                    return;
                }
                mPopupWindow.showAsDropDown(mcVersionSpinner.this, 0, offset);
                // Post() is required for the layout inflation phase
                post(() -> mListView.setSelection(mSelectedIndex));
            }
        });
    }

    private void performExtraAction(ProfileAdapterExtra extra) {
        //Replace with switch-case if you want to add more extra actions
        if (extra.id == VERSION_SPINNER_PROFILE_CREATE) {
            Tools.swapFragment((FragmentActivity) getContext(), ProfileTypeSelectFragment.class,
                    ProfileTypeSelectFragment.TAG, null);
        }
    }


    /** Create the listView and popup window for the interface, and set up the click behavior */
    @SuppressLint("ClickableViewAccessibility")
    private void getPopupWindow(){
        mListView = (ListView) inflate(getContext(), R.layout.spinner_mc_version, null);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            mListView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        }
        mListView.setAdapter(mProfileAdapter);
        mListView.setOnItemClickListener((parent, view, position, id) -> {
            Object item = mProfileAdapter.getItem(position);
            if(item instanceof String) {
                hidePopup(true);
                setProfileSelection(position);
            }else if(item instanceof ProfileAdapterExtra) {
                hidePopup(false);
                performExtraAction((ProfileAdapterExtra) item);
            }
        });
        mListView.setOnItemLongClickListener((parent, view, position, id) -> {
            Object item = mProfileAdapter.getItem(position);
            if(item instanceof String) {
                showProfileActions((String) item, position);
                return true;
            }
            return false;
        });

        mPopupWindow = new PopupWindow(mListView, MATCH_PARENT, getContext().getResources().getDimensionPixelOffset(R.dimen._184sdp));
        mPopupWindow.setElevation(5);
        mPopupWindow.setClippingEnabled(false);

        // Block clicking outside of the popup window
        mPopupWindow.setOutsideTouchable(true);
        mPopupWindow.setFocusable(true);
        mPopupWindow.setTouchInterceptor((v, event) -> {
            if(event.getAction() == MotionEvent.ACTION_OUTSIDE){
                mPopupWindow.dismiss();
                return true;
            }
            return false;
        });


        // Custom animation, nice hardware-accelerated slide in
        mPopupWindow.setAnimationStyle(android.R.style.Animation_InputMethod);
    }

    private void showProfileActions(String profileKey, int position) {
        hidePopup(false);
        String[] actions = new String[]{
                getContext().getString(R.string.global_edit),
                getContext().getString(R.string.mcl_version_clone),
                getContext().getString(R.string.global_delete)
        };
        new AlertDialog.Builder(getContext())
                .setItems(actions, (dialog, which) -> {
                    if(which == 0) editProfile(position);
                    else if(which == 1) cloneProfile(profileKey);
                    else if(which == 2) confirmDeleteProfile(profileKey);
                })
                .show();
    }

    private void editProfile(int position) {
        setProfileSelection(position);
        Object currentSelection = mProfileAdapter.getItem(position);
        if (currentSelection instanceof String) {
            String profileKey = (String) currentSelection;
            // Fall through to standard profile editor
        }
        Tools.swapFragment((FragmentActivity) getContext(), ProfileEditorFragment.class,
                ProfileEditorFragment.TAG, null);
    }

    private void cloneProfile(String profileKey) {
        String nextProfile = LauncherProfiles.cloneProfile(profileKey);
        refreshProfileSelection(nextProfile);
    }

    private void confirmDeleteProfile(String profileKey) {
        new AlertDialog.Builder(getContext())
                .setTitle(R.string.profile_delete_title)
                .setMessage(R.string.profile_delete_message)
                .setPositiveButton(R.string.global_delete, (dialog, which) -> {
                    ProfileIconCache.dropIcon(profileKey);
                    String selectedProfile = LauncherPreferences.DEFAULT_PREF.getString(
                            LauncherPreferences.PREF_KEY_CURRENT_PROFILE, "");
                    String nextProfile = LauncherProfiles.deleteProfile(profileKey, selectedProfile);
                    refreshProfileSelection(nextProfile);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void refreshProfileSelection(String nextProfile) {
        LauncherPreferences.DEFAULT_PREF.edit()
                .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, nextProfile)
                .apply();
        ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, nextProfile);
        reloadProfiles();
        setSelection(Math.max(0, mProfileAdapter.resolveProfileIndex(nextProfile)));
    }

    private void hidePopup(boolean animate) {
        if(mPopupWindow == null) return;
        if(!animate) {
            mPopupWindow.setAnimationStyle(0);
            mPopupWindow.dismiss();
            mPopupWindow.setAnimationStyle(android.R.style.Animation_InputMethod);
        }else {
            mPopupWindow.dismiss();
        }
    }

    public ProfileAdapter getProfileAdapter() {
        return mProfileAdapter;
    }
}
