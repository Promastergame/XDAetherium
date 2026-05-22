package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;

public class ElyByLoginFragment extends Fragment {
    public static final String TAG = "ELYBY_LOGIN_FRAGMENT";

    private EditText mLoginEditText;
    private EditText mPasswordEditText;
    private EditText mTotpEditText;

    public ElyByLoginFragment() {
        super(R.layout.fragment_elyby_login);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        mLoginEditText = view.findViewById(R.id.elyby_login_edit);
        mPasswordEditText = view.findViewById(R.id.elyby_password_edit);
        mTotpEditText = view.findViewById(R.id.elyby_totp_edit);

        view.findViewById(R.id.elyby_login_button).setOnClickListener(v -> {
            if(!checkEditText()) {
                Context context = v.getContext();
                Tools.dialog(context, context.getString(R.string.global_error),
                        context.getString(R.string.login_elyby_bad_credentials));
                return;
            }

            ExtraCore.setValue(ExtraConstants.ELYBY_LOGIN_TODO, new String[]{
                    mLoginEditText.getText().toString().trim(),
                    mPasswordEditText.getText().toString(),
                    mTotpEditText.getText().toString().trim()
            });
            Tools.backToMainMenu(requireActivity());
        });

        view.findViewById(R.id.elyby_register_button).setOnClickListener(v ->
                Tools.openURL(requireActivity(), "https://account.ely.by/"));
    }

    private boolean checkEditText() {
        return !mLoginEditText.getText().toString().trim().isEmpty()
                && !mPasswordEditText.getText().toString().isEmpty();
    }
}
