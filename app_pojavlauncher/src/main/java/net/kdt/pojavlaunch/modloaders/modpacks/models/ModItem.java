package net.kdt.pojavlaunch.modloaders.modpacks.models;

import androidx.annotation.NonNull;

public class ModItem extends ModSource {

    public String id;
    public String title;
    public String description;
    public String imageUrl;

    public String projectId;
    public String versionId;

    public String version;
    public String fileName;
    public String downloadUrl;
    public String sha1;
    public long fileSize;

    public String mcVersion;
    public String modLoader;

    public boolean isDependency;
    public java.util.List<String> requiredDependencyIds;
    public java.util.List<String> optionalDependencyIds;

    public ModItem(int apiSource, boolean isModpack, String id, String title, String description, String imageUrl) {
        this.apiSource = apiSource;
        this.isModpack = isModpack;
        this.id = id;
        this.title = title;
        this.description = description;
        this.imageUrl = imageUrl;
    }

    public ModItem() {
        // No-args constructor for Gson serialization
    }

    @NonNull
    @Override
    public String toString() {
        return "ModItem{" +
                "id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", apiSource=" + apiSource +
                ", isModpack=" + isModpack +
                '}';
    }

    public String getIconCacheTag() {
        return apiSource+"_"+id;
    }
}
