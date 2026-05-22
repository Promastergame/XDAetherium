package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

public class ModloaderSelectFragment extends Fragment {
    public static final String TAG = "ModloaderSelectFragment";

    public ModloaderSelectFragment() {
        super(R.layout.fragment_modloader_select);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        view.findViewById(R.id.btn_loader_fabric).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), FabricInstallFragment.class, FabricInstallFragment.TAG, null));

        view.findViewById(R.id.btn_loader_forge).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), ForgeInstallFragment.class, ForgeInstallFragment.TAG, null));

        view.findViewById(R.id.btn_loader_quilt).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), QuiltInstallFragment.class, QuiltInstallFragment.TAG, null));

        view.findViewById(R.id.btn_loader_neoforge).setOnClickListener(v ->
                Tools.swapFragment(requireActivity(), NeoForgeInstallFragment.class, NeoForgeInstallFragment.TAG, null));

        view.findViewById(R.id.btn_loader_back).setOnClickListener(v ->
                getParentFragmentManager().popBackStackImmediate());
    }
}
