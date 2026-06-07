package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

public class MITEInstallFragment extends Fragment {
    public static final String TAG = "MITEInstallFragment";

    private static final String MITE_R196_URL = "https://avernite.ca/MITE/MITE%201.6.4%20R196.zip";
    private static final String MITE_RENEWED_URL = "https://github.com/MinecraftIsTooEasy/mite-renewed/releases/download/0.2.7.2/MiTE-Renewed.0.2.7.2.jar";
    private static final String MITE_WIKI_URL = "https://www.mcmod.cn/class/226.html";

    public MITEInstallFragment() {
        super(R.layout.fragment_hard_mod_info);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        ((TextView) view.findViewById(R.id.hard_mod_title)).setText(R.string.mite_install_title);
        ((TextView) view.findViewById(R.id.hard_mod_subtitle)).setText(R.string.mite_install_subtitle);
        ((TextView) view.findViewById(R.id.hard_mod_description)).setText(R.string.mite_install_desc);

        TextView primaryDownload = view.findViewById(R.id.hard_mod_primary_download);
        TextView secondaryDownload = view.findViewById(R.id.hard_mod_secondary_download);

        primaryDownload.setText(R.string.mite_install_official_r196);
        secondaryDownload.setText(R.string.mite_install_renewed_0272);

        primaryDownload.setOnClickListener(v -> Tools.openURL(requireActivity(), MITE_R196_URL));
        secondaryDownload.setOnClickListener(v -> Tools.openURL(requireActivity(), MITE_RENEWED_URL));
        view.findViewById(R.id.hard_mod_wiki).setOnClickListener(v -> Tools.openURL(requireActivity(), MITE_WIKI_URL));
    }
}
