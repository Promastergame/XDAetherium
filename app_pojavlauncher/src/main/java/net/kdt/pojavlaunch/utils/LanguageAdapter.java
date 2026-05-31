package net.kdt.pojavlaunch.utils;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;

import java.util.ArrayList;
import java.util.List;

public class LanguageAdapter extends RecyclerView.Adapter<LanguageAdapter.LanguageViewHolder> {

    private final List<MCLanguage> allLanguages;
    private final List<MCLanguage> filteredLanguages;
    private final OnLanguageSelectedListener listener;
    private String selectedCode;

    public interface OnLanguageSelectedListener {
        void onLanguageSelected(MCLanguage language);
    }

    public LanguageAdapter(List<MCLanguage> languages, OnLanguageSelectedListener listener) {
        this.allLanguages = new ArrayList<>(languages);
        this.filteredLanguages = new ArrayList<>(languages);
        this.listener = listener;
        this.selectedCode = LauncherPreferences.PREF_FORCE_MINECRAFT_LANGUAGE;
        if (this.selectedCode == null) this.selectedCode = "default";
    }

    @NonNull
    @Override
    public LanguageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_language, parent, false);
        return new LanguageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull LanguageViewHolder holder, int position) {
        MCLanguage lang = filteredLanguages.get(position);
        holder.nameView.setText(lang.name);
        
        boolean isSelected = lang.code.equals(selectedCode);
        holder.checkView.setVisibility(isSelected ? View.VISIBLE : View.GONE);

        holder.itemView.setOnClickListener(v -> {
            String oldCode = selectedCode;
            selectedCode = lang.code;
            notifyDataSetChanged(); // Simple way to update checks
            if (listener != null) {
                listener.onLanguageSelected(lang);
            }
        });
    }

    @Override
    public int getItemCount() {
        return filteredLanguages.size();
    }

    public void filter(String query) {
        filteredLanguages.clear();
        if (query == null || query.isEmpty()) {
            filteredLanguages.addAll(allLanguages);
        } else {
            String lowerQuery = query.toLowerCase();
            for (MCLanguage lang : allLanguages) {
                if (lang.name.toLowerCase().contains(lowerQuery) || lang.code.toLowerCase().contains(lowerQuery)) {
                    filteredLanguages.add(lang);
                }
            }
        }
        notifyDataSetChanged();
    }

    static class LanguageViewHolder extends RecyclerView.ViewHolder {
        final TextView nameView;
        final ImageView checkView;

        LanguageViewHolder(@NonNull View itemView) {
            super(itemView);
            nameView = itemView.findViewById(R.id.language_name);
            checkView = itemView.findViewById(R.id.language_check);
        }
    }
}
