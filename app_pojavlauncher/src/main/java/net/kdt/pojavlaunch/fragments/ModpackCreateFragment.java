package net.kdt.pojavlaunch.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;


import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;

public class ModpackCreateFragment extends Fragment {
    public static final String TAG = "ModpackCreateFragment";
    public ModpackCreateFragment() {
        super(R.layout.fragment_create_modpack_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.button_browse_modrinth_modpacks).setOnClickListener(v ->
                tryInstall(SearchModFragment.class, SearchModFragment.TAG, Constants.SOURCE_MODRINTH));
        view.findViewById(R.id.button_browse_curseforge_modpacks).setOnClickListener(v ->
                tryInstall(SearchModFragment.class, SearchModFragment.TAG, Constants.SOURCE_CURSEFORGE));
        view.findViewById(R.id.button_browse_modpacks).setOnClickListener(v ->
                tryInstall(SearchModFragment.class, SearchModFragment.TAG));
        view.findViewById(R.id.button_import_modpack).setOnClickListener(v -> {
            Activity launcheractivity = requireActivity();
            if (!(launcheractivity instanceof LauncherActivity))
                    throw new IllegalStateException("Cannot import modpack without LauncherActivity");
            ((LauncherActivity) launcheractivity).modpackImportLauncher.launch(null);
        });;
    }

    private void tryInstall(Class<? extends Fragment> fragmentClass, String tag){
        Tools.swapFragment(requireActivity(), fragmentClass, tag, null);
    }

    private void tryInstall(Class<? extends Fragment> fragmentClass, String tag, int modpackSource){
        Bundle args = new Bundle();
        args.putInt(SearchModFragment.ARG_MODPACK_SOURCE, modpackSource);
        Tools.swapFragment(requireActivity(), fragmentClass, tag, args);
    }
}
