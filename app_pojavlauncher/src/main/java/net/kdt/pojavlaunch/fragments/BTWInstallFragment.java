package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.BTWDownloadTask;
import net.kdt.pojavlaunch.modloaders.ModloaderDownloadListener;
import net.kdt.pojavlaunch.modloaders.ModloaderListenerProxy;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;

import java.io.File;
import java.util.concurrent.CancellationException;

public class BTWInstallFragment extends Fragment implements ModloaderDownloadListener {
    public static final String TAG = "BTWInstallFragment";
    private static final String BTW_MODRINTH_URL = "https://cdn.modrinth.com/data/PiC4CKoa/versions/Pbz5N4Ul/btwce-3.1.0.jar?mr_download_reason=standalone";
    private static final String BTW_WIKI_URL = "https://wiki.btwce.com";
    private static final String EXTRA_TAG = "BTWInstallFragment_proxy";

    private CheckBox mCheckboxFlatcore;
    private CheckBox mCheckboxAdventure;
    private TextView mStatusText;
    private ProgressBar mProgressBar;
    private View mInstallButton;

    public BTWInstallFragment() {
        super(R.layout.fragment_btw_install);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mCheckboxFlatcore = view.findViewById(R.id.checkbox_map_flatcore);
        mCheckboxAdventure = view.findViewById(R.id.checkbox_map_adventure);
        mStatusText = view.findViewById(R.id.btw_status_text);
        mProgressBar = view.findViewById(R.id.btw_progress_bar);
        mInstallButton = view.findViewById(R.id.btw_install_button);

        view.findViewById(R.id.btw_modrinth_download_button).setOnClickListener(v -> Tools.openURL(requireActivity(), BTW_MODRINTH_URL));
        view.findViewById(R.id.btw_wiki_button).setOnClickListener(v -> Tools.openURL(requireActivity(), BTW_WIKI_URL));
        mInstallButton.setOnClickListener(this::onClickInstall);

        ModloaderListenerProxy proxy = getListenerProxy();
        if (proxy != null) {
            mInstallButton.setEnabled(false);
            proxy.attachListener(this);
            mStatusText.setVisibility(View.VISIBLE);
            mProgressBar.setVisibility(View.VISIBLE);
            mStatusText.setText(getString(R.string.btw_install_running));
        }
    }

    @Override
    public void onStop() {
        ModloaderListenerProxy proxy = getListenerProxy();
        if (proxy != null) {
            proxy.detachListener();
        }
        super.onStop();
    }

    private void onClickInstall(View v) {
        if (ProgressKeeper.hasOngoingTasks()) {
            Toast.makeText(v.getContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
            return;
        }

        mStatusText.setVisibility(View.VISIBLE);
        mProgressBar.setVisibility(View.VISIBLE);
        mStatusText.setText(getString(R.string.btw_install_preparing));
        mInstallButton.setEnabled(false);

        ModloaderListenerProxy proxy = new ModloaderListenerProxy();
        BTWDownloadTask downloadTask = new BTWDownloadTask(
                proxy,
                mCheckboxFlatcore.isChecked(),
                mCheckboxAdventure.isChecked()
        );

        proxy.attachListener(this);
        setListenerProxy(proxy);

        new Thread(downloadTask).start();
    }

    @Override
    public void onDownloadFinished(File downloadedFile) {
        Tools.runOnUiThread(() -> {
            mStatusText.setText(getString(R.string.btw_install_success));
            mProgressBar.setVisibility(View.GONE);
            mInstallButton.setEnabled(true);
            
            ModloaderListenerProxy proxy = getListenerProxy();
            if (proxy != null) {
                proxy.detachListener();
            }
            setListenerProxy(null);

            Toast.makeText(requireContext(), getString(R.string.btw_install_profile_created), Toast.LENGTH_SHORT).show();
            getParentFragmentManager().popBackStackImmediate();
        });
    }

    @Override
    public void onDataNotAvailable() {
        Tools.runOnUiThread(() -> {
            mStatusText.setText(getString(R.string.btw_install_data_not_available));
            mProgressBar.setVisibility(View.GONE);
            mInstallButton.setEnabled(true);

            ModloaderListenerProxy proxy = getListenerProxy();
            if (proxy != null) {
                proxy.detachListener();
            }
            setListenerProxy(null);
        });
    }

    @Override
    public void onDownloadError(Exception e) {
        Tools.runOnUiThread(() -> {
            mStatusText.setText(getString(R.string.btw_install_error));
            mProgressBar.setVisibility(View.GONE);
            mInstallButton.setEnabled(true);

            ModloaderListenerProxy proxy = getListenerProxy();
            if (proxy != null) {
                proxy.detachListener();
            }
            setListenerProxy(null);

            if (!(e instanceof CancellationException)) {
                Tools.showError(requireContext(), e);
            }
        });
    }

    private ModloaderListenerProxy getListenerProxy() {
        return (ModloaderListenerProxy) net.kdt.pojavlaunch.extra.ExtraCore.getValue(EXTRA_TAG);
    }

    private void setListenerProxy(ModloaderListenerProxy listenerProxy) {
        net.kdt.pojavlaunch.extra.ExtraCore.setValue(EXTRA_TAG, listenerProxy);
    }
}
