package net.kdt.pojavlaunch.prefs.screens;

import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.BackupUtils;
import net.kdt.pojavlaunch.utils.GLInfoUtils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LauncherPreferenceMiscellaneousFragment extends LauncherPreferenceFragment {

    private ActivityResultLauncher<String> mExportLauncher;
    private ActivityResultLauncher<String[]> mImportLauncher;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        mExportLauncher = registerForActivityResult(new ActivityResultContracts.CreateDocument(), uri -> {
            if (uri != null) {
                runExport(uri);
            }
        });
        
        mImportLauncher = registerForActivityResult(new ActivityResultContracts.OpenDocument(), uri -> {
            if (uri != null) {
                checkAndRunImport(uri);
            }
        });
    }

    @Override
    public void onCreatePreferences(Bundle b, String str) {
        addPreferencesFromResource(R.xml.pref_misc);
        Preference driverPreference = requirePreference("zinkPreferSystemDriver");
        PackageManager packageManager = driverPreference.getContext().getPackageManager();
        boolean supportsTurnip = Tools.checkVulkanSupport(packageManager) && GLInfoUtils.getGlInfo().isAdreno();
        driverPreference.setVisible(supportsTurnip);

        Preference exportPref = findPreference("export_backup");
        if (exportPref != null) {
            exportPref.setOnPreferenceClickListener(preference -> {
                mExportLauncher.launch("PojavBackup.zip");
                return true;
            });
        }

        Preference importPref = findPreference("import_backup");
        if (importPref != null) {
            importPref.setOnPreferenceClickListener(preference -> {
                mImportLauncher.launch(new String[]{"application/zip", "application/x-zip-compressed", "application/octet-stream"});
                return true;
            });
        }
    }

    private void runExport(Uri targetUri) {
        Toast.makeText(getContext(), "Начинаем экспорт данных...", Toast.LENGTH_SHORT).show();
        mExecutor.execute(() -> {
            try {
                BackupUtils.createBackup(getContext(), targetUri, currentFile -> {
                    // Could update progress dialog here
                });
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> 
                        Toast.makeText(getContext(), "Бэкап успешно сохранён!", Toast.LENGTH_LONG).show()
                    );
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> 
                        Toast.makeText(getContext(), "Ошибка экспорта: " + e.getMessage(), Toast.LENGTH_LONG)
                    );
                }
            }
        });
    }

    private void checkAndRunImport(Uri sourceUri) {
        Toast.makeText(getContext(), "Проверка архива...", Toast.LENGTH_SHORT);
        mExecutor.execute(() -> {
            try {
                boolean hasCollisions = BackupUtils.checkCollisions(getContext(), sourceUri);
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        if (hasCollisions) {
                            new AlertDialog.Builder(requireContext())
                                .setTitle("Внимание")
                                .setMessage("Перезаписать ли существующие файлы?")
                                .setPositiveButton("Да", (dialog, which) -> runImport(sourceUri))
                                .setNegativeButton("Отмена", null)
                                .show();
                        } else {
                            runImport(sourceUri);
                        }
                    });
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> 
                        Toast.makeText(getContext(), "Ошибка проверки: " + e.getMessage(), Toast.LENGTH_LONG)
                    );
                }
            }
        });
    }

    private void runImport(Uri sourceUri) {
        Toast.makeText(getContext(), "Восстановление бэкапа...", Toast.LENGTH_SHORT).show();
        mExecutor.execute(() -> {
            try {
                BackupUtils.restoreBackup(getContext(), sourceUri, currentFile -> {
                    // Could update progress dialog here
                });
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> 
                        Toast.makeText(getContext(), "Бэкап успешно восстановлен!", Toast.LENGTH_LONG).show()
                    );
                }
            } catch (Exception e) {
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> 
                        Toast.makeText(getContext(), "Ошибка восстановления: " + e.getMessage(), Toast.LENGTH_LONG).show()
                    );
                }
            }
        });
    }
}
