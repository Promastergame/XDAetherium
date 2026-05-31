package net.kdt.pojavlaunch.utils;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.EditText;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

public class LanguageSelectorDialog extends DialogFragment {

    private LanguageAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        // Set rounded background if needed, but the layout has background
        if (getDialog() != null && getDialog().getWindow() != null) {
            getDialog().getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        }
        return inflater.inflate(R.layout.dialog_language_selector, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        EditText searchInput = view.findViewById(R.id.search_language_input);
        RecyclerView recyclerView = view.findViewById(R.id.language_recycler_view);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        
        // Localize the default entry based on current locale
        java.util.List<MCLanguage> localizedLanguages = new java.util.ArrayList<>(LanguageDatabase.LANGUAGES);
        if (!localizedLanguages.isEmpty()) {
            localizedLanguages.set(0, new MCLanguage(
                getString(R.string.mc_language_default), "default"));
        }
        adapter = new LanguageAdapter(localizedLanguages, language -> {
            // Save preference
            LauncherPreferences.DEFAULT_PREF.edit().putString("force_minecraft_language", language.code).apply();
            LauncherPreferences.PREF_FORCE_MINECRAFT_LANGUAGE = language.code;
            
            // Optionally, we could show a toast: "Выбран язык: " + language.name
            dismiss();
        });
        
        recyclerView.setAdapter(adapter);

        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.filter(s.toString());
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        // Scroll to selected position
        String currentLang = LauncherPreferences.PREF_FORCE_MINECRAFT_LANGUAGE;
        if (currentLang == null) currentLang = "default";
        
        int selectedIndex = -1;
        for (int i = 0; i < localizedLanguages.size(); i++) {
            if (localizedLanguages.get(i).code.equals(currentLang)) {
                selectedIndex = i;
                break;
            }
        }
        if (selectedIndex != -1) {
            recyclerView.scrollToPosition(selectedIndex);
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        Dialog dialog = getDialog();
        if (dialog != null && dialog.getWindow() != null) {
            // Make the dialog fill up most of the screen
            int width = ViewGroup.LayoutParams.MATCH_PARENT;
            int height = (int) (getResources().getDisplayMetrics().heightPixels * 0.8);
            dialog.getWindow().setLayout(width, height);
        }
    }
}
