package net.kdt.pojavlaunch.fragments;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.util.ArrayList;

public class CustomModpackConfigFragment extends Fragment {
    public static final String TAG = "CustomModpackConfigFragment";
    
    private String mProfileId;
    private MinecraftProfile mProfile;
    
    private EditText mNameEdit;
    private Spinner mVersionSpinner;
    private Spinner mLoaderSpinner;
    
    public CustomModpackConfigFragment() {
        super(R.layout.fragment_custom_modpack_config);
    }
    
    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mProfileId = getArguments().getString("profile_id");
        }
        
        if (mProfileId == null || !LauncherProfiles.mainProfileJson.profiles.containsKey(mProfileId)) {
            mProfileId = LauncherProfiles.getFreeProfileKey();
            mProfile = MinecraftProfile.createTemplate();
            mProfile.isCustomModpack = true;
            mProfile.installedMods = new ArrayList<>();
            LauncherProfiles.mainProfileJson.profiles.put(mProfileId, mProfile);
        } else {
            mProfile = LauncherProfiles.mainProfileJson.profiles.get(mProfileId);
        }
    }
    
    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        
        mNameEdit = view.findViewById(R.id.profile_name_edit);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);
        mLoaderSpinner = view.findViewById(R.id.loader_spinner);
        
        if (mProfile.name != null && !mProfile.name.isEmpty()) {
            mNameEdit.setText(mProfile.name);
        } else {
            mNameEdit.setText("Моя кастомная сборка");
        }
        
        String[] versions = new String[]{"1.20.1", "1.19.4", "1.19.2", "1.18.2", "1.16.5"};
        ArrayAdapter<String> versionAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, versions);
        versionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mVersionSpinner.setAdapter(versionAdapter);
        
        String[] loaders = new String[]{"Fabric", "Forge", "Quilt", "NeoForge"};
        ArrayAdapter<String> loaderAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, loaders);
        loaderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mLoaderSpinner.setAdapter(loaderAdapter);
        
        if (mProfile.lastVersionId != null) {
            String cleanMcVer = Tools.getCleanMinecraftVersion(mProfile.lastVersionId, mProfile.modLoader);
            for (int i=0; i<versions.length; i++) {
                if (versions[i].equals(cleanMcVer)) {
                    mVersionSpinner.setSelection(i);
                    break;
                }
            }
        }
        if (mProfile.modLoader != null) {
            for (int i=0; i<loaders.length; i++) {
                if (loaders[i].equalsIgnoreCase(mProfile.modLoader)) {
                    mLoaderSpinner.setSelection(i);
                    break;
                }
            }
        }
        
        RecyclerView recycler = view.findViewById(R.id.installed_mods_recycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        InstalledModsAdapter adapter = new InstalledModsAdapter();
        recycler.setAdapter(adapter);
        
        view.findViewById(R.id.manage_mods_button).setOnClickListener(v -> {
            String targetVer = mProfile.lastVersionId;
            if (targetVer == null || targetVer.equals("latest-release") || targetVer.equals("latest-snapshot") || targetVer.equals("release")) {
                targetVer = mVersionSpinner.getSelectedItem().toString();
            }
            saveCurrentState(targetVer);
            Bundle bundle = new Bundle();
            bundle.putString("profile_id", mProfileId);
            bundle.putString("mc_version", mVersionSpinner.getSelectedItem().toString());
            bundle.putString("mod_loader", mLoaderSpinner.getSelectedItem().toString());
            bundle.putBoolean("is_custom_modpack", true); // Tell SearchModFragment that we are picking single mods
            Tools.swapFragment(requireActivity(), SearchModFragment.class, SearchModFragment.TAG, bundle);
        });
        
        view.findViewById(R.id.save_profile_button).setOnClickListener(v -> {
            saveAndInstallLoader(view);
        });
    }
    
    private void saveAndInstallLoader(View view) {
        String mcVersion = mVersionSpinner.getSelectedItem().toString();
        String loaderName = mLoaderSpinner.getSelectedItem().toString();

        if (loaderName.equals("Vanilla")) {
            finalizeSave(mcVersion);
            return;
        }

        view.findViewById(R.id.save_profile_button).setEnabled(false);
        Toast.makeText(requireContext(), "Установка " + loaderName + "...", Toast.LENGTH_SHORT).show();

        net.kdt.pojavlaunch.PojavApplication.sExecutorService.execute(() -> {
            try {
                String loaderVersionId = mcVersion;
                if (loaderName.equals("Fabric")) {
                    net.kdt.pojavlaunch.modloaders.FabricVersion[] versions = net.kdt.pojavlaunch.modloaders.FabriclikeUtils.FABRIC_UTILS.downloadLoaderVersions(mcVersion);
                    if (versions != null && versions.length > 0) {
                        String loaderVer = versions[0].version;
                        net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader ml = new net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader(net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader.MOD_LOADER_FABRIC, loaderVer, mcVersion);
                        loaderVersionId = ml.getVersionId();
                        new net.kdt.pojavlaunch.modloaders.FabriclikeDownloadTask(new DummyListener(), net.kdt.pojavlaunch.modloaders.FabriclikeUtils.FABRIC_UTILS, mcVersion, loaderVer, true).run();
                    }
                } else if (loaderName.equals("Quilt")) {
                    net.kdt.pojavlaunch.modloaders.FabricVersion[] versions = net.kdt.pojavlaunch.modloaders.FabriclikeUtils.QUILT_UTILS.downloadLoaderVersions(mcVersion);
                    if (versions != null && versions.length > 0) {
                        String loaderVer = versions[0].version;
                        net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader ml = new net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader(net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader.MOD_LOADER_QUILT, loaderVer, mcVersion);
                        loaderVersionId = ml.getVersionId();
                        new net.kdt.pojavlaunch.modloaders.FabriclikeDownloadTask(new DummyListener(), net.kdt.pojavlaunch.modloaders.FabriclikeUtils.QUILT_UTILS, mcVersion, loaderVer, true).run();
                    }
                } else if (loaderName.equals("Forge")) {
                    java.util.List<String> forgeVersions = net.kdt.pojavlaunch.modloaders.ForgeUtils.downloadForgeVersions();
                    String targetForge = null;
                    if(forgeVersions != null) {
                        for(String v : forgeVersions) {
                            if(v.startsWith(mcVersion + "-")) { targetForge = v.substring(mcVersion.length() + 1); break; }
                        }
                    }
                    if(targetForge != null) {
                        net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader ml = new net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader(net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader.MOD_LOADER_FORGE, targetForge, mcVersion);
                        loaderVersionId = ml.getVersionId();
                        ml.getDownloadTask(new net.kdt.pojavlaunch.modloaders.modpacks.api.NotificationDownloadListener(requireContext(), ml)).run();
                    }
                } else if (loaderName.equals("NeoForge")) {
                    java.util.List<String> neoforgeVersions = net.kdt.pojavlaunch.fragments.NeoForgeInstallFragment.downloadNeoForgeVersions();
                    String targetNeoForge = null;
                    if (neoforgeVersions != null) {
                        for(String version : neoforgeVersions) {
                            String[] parts = version.split("\\.");
                            String gameVersion;
                            try {
                                if (Integer.parseInt(parts[1]) < 25) { 
                                    gameVersion = "1." + parts[0] + "." + parts[1];
                                } else gameVersion = parts[0] + "." + parts[1];
                            } catch (NumberFormatException ignored) {
                                gameVersion = parts[0] + "." + parts[1];
                            }
                            if (gameVersion.equals(mcVersion)) {
                                targetNeoForge = version;
                                break;
                            }
                        }
                    }
                    if(targetNeoForge != null) {
                        net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader ml = new net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader(net.kdt.pojavlaunch.modloaders.modpacks.api.ModLoader.MOD_LOADER_NEOFORGE, targetNeoForge, mcVersion);
                        loaderVersionId = ml.getVersionId();
                        ml.getDownloadTask(new net.kdt.pojavlaunch.modloaders.modpacks.api.NotificationDownloadListener(requireContext(), ml)).run();
                    }
                }

                String finalLoaderVersionId = loaderVersionId;
                Tools.runOnUiThread(() -> finalizeSave(finalLoaderVersionId));

            } catch (Exception e) {
                e.printStackTrace();
                Tools.runOnUiThread(() -> {
                    Toast.makeText(requireContext(), "Ошибка установки: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    view.findViewById(R.id.save_profile_button).setEnabled(true);
                });
            }
        });
    }

    private class DummyListener implements net.kdt.pojavlaunch.modloaders.ModloaderDownloadListener {
        @Override public void onDownloadFinished(java.io.File f) {}
        @Override public void onDataNotAvailable() {}
        @Override public void onDownloadError(Exception e) {}
    }

    private void finalizeSave(String versionId) {
        saveCurrentState(versionId);
        Toast.makeText(requireContext(), "Сборка сохранена!", Toast.LENGTH_SHORT).show();
        requireActivity().getSupportFragmentManager().popBackStack();
    }
    
    private void saveCurrentState(String versionId) {
        mProfile.name = mNameEdit.getText().toString();
        if (versionId == null || versionId.equals("latest-release") || versionId.equals("latest-snapshot") || versionId.equals("release")) {
            versionId = mVersionSpinner.getSelectedItem().toString();
        }
        mProfile.lastVersionId = versionId;
        mProfile.modLoader = mLoaderSpinner.getSelectedItem().toString();
        if (mProfile.gameDir == null || mProfile.gameDir.isEmpty()) {
            mProfile.gameDir = "custom_profiles/" + mProfileId;
        }
        LauncherProfiles.write();
    }

    private class InstalledModsAdapter extends RecyclerView.Adapter<InstalledModsAdapter.ModViewHolder> {
        @NonNull
        @Override
        public ModViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            android.widget.LinearLayout layout = new android.widget.LinearLayout(parent.getContext());
            layout.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            layout.setPadding(16, 16, 16, 16);
            layout.setLayoutParams(new RecyclerView.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT));

            android.widget.TextView textView = new android.widget.TextView(parent.getContext());
            textView.setId(android.view.View.generateViewId());
            textView.setTextColor(android.graphics.Color.WHITE);
            textView.setTextSize(16);
            android.widget.LinearLayout.LayoutParams textParams = new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            layout.addView(textView, textParams);

            android.widget.Button deleteButton = new android.widget.Button(parent.getContext());
            deleteButton.setId(android.view.View.generateViewId());
            deleteButton.setText("Удалить");
            deleteButton.setTextColor(android.graphics.Color.WHITE);
            deleteButton.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.RED));
            layout.addView(deleteButton);

            return new ModViewHolder(layout, textView, deleteButton);
        }

        @Override
        public void onBindViewHolder(@NonNull ModViewHolder holder, int position) {
            ModItem mod = mProfile.installedMods.get(position);
            holder.textView.setText(mod.fileName != null ? mod.fileName : mod.projectId);
            holder.deleteButton.setOnClickListener(v -> {
                // Delete jar
                if (mod.fileName != null) {
                    java.io.File modFile = new java.io.File(Tools.getGameDirPath(mProfile), "mods/" + mod.fileName);
                    if (modFile.exists()) modFile.delete();
                }
                mProfile.installedMods.remove(position);
                LauncherProfiles.write();
                notifyItemRemoved(position);
                notifyItemRangeChanged(position, mProfile.installedMods.size());
            });
        }

        @Override
        public int getItemCount() {
            return mProfile.installedMods == null ? 0 : mProfile.installedMods.size();
        }

        class ModViewHolder extends RecyclerView.ViewHolder {
            android.widget.TextView textView;
            android.widget.Button deleteButton;
            ModViewHolder(View v, android.widget.TextView tv, android.widget.Button btn) {
                super(v);
                textView = tv;
                deleteButton = btn;
            }
        }
    }
}
