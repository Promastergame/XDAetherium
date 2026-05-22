package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.app.Activity;
import android.net.Uri;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModrinthIndex;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper;
import net.kdt.pojavlaunch.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

public class ModrinthApi implements ModpackApi{
    private final ApiHandler mApiHandler;
    public ModrinthApi(){
        mApiHandler = new ApiHandler("https://api.modrinth.com/v2");
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        ModrinthSearchResult modrinthSearchResult = (ModrinthSearchResult) previousPageResult;

        // Fixes an issue where the offset being equal or greater than total_hits is ignored
        if (modrinthSearchResult != null && modrinthSearchResult.previousOffset >= modrinthSearchResult.totalResultCount) {
            ModrinthSearchResult emptyResult = new ModrinthSearchResult();
            emptyResult.results = new ModItem[0];
            emptyResult.totalResultCount = modrinthSearchResult.totalResultCount;
            emptyResult.previousOffset = modrinthSearchResult.previousOffset;
            return emptyResult;
        }


        // Build the facets filters
        HashMap<String, Object> params = new HashMap<>();
        StringBuilder facetString = new StringBuilder();
        facetString.append("[");
        facetString.append(String.format("[\"project_type:%s\"]", searchFilters.isModpack ? "modpack" : "mod"));
        if(searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            facetString.append(String.format(",[\"versions:%s\"]", searchFilters.mcVersion));
        if(!searchFilters.isModpack && searchFilters.modLoader != null && !searchFilters.modLoader.isEmpty()) {
            String loader = searchFilters.modLoader.toLowerCase();
            if (loader.equals("neoforge")) loader = "neo-forge";
            facetString.append(String.format(",[\"categories:%s\"]", loader));
        }
        facetString.append("]");
        params.put("facets", facetString.toString());
        params.put("query", searchFilters.name);
        params.put("limit", 50);
        params.put("index", "relevance");
        if(modrinthSearchResult != null)
            params.put("offset", modrinthSearchResult.previousOffset);

        JsonObject response = mApiHandler.get("search", params, JsonObject.class);
        if(response == null) return null;
        JsonArray responseHits = response.getAsJsonArray("hits");
        if(responseHits == null) return null;

        ModItem[] items = new ModItem[responseHits.size()];
        for(int i=0; i<responseHits.size(); ++i){
            JsonObject hit = responseHits.get(i).getAsJsonObject();
            items[i] = new ModItem(
                    Constants.SOURCE_MODRINTH,
                    hit.get("project_type").getAsString().equals("modpack"),
                    hit.get("project_id").getAsString(),
                    hit.get("title").getAsString(),
                    hit.get("description").getAsString(),
                    hit.get("icon_url").getAsString()
            );
            items[i].mcVersion = searchFilters.mcVersion;
            items[i].modLoader = searchFilters.modLoader;
        }
        if(modrinthSearchResult == null) modrinthSearchResult = new ModrinthSearchResult();
        modrinthSearchResult.previousOffset += responseHits.size();
        modrinthSearchResult.results = items;
        modrinthSearchResult.totalResultCount = response.get("total_hits").getAsInt();
        return modrinthSearchResult;
    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        HashMap<String, Object> params = new HashMap<>();
        if (item.mcVersion != null && !item.mcVersion.isEmpty()) {
            params.put("game_versions", "[\"" + item.mcVersion + "\"]");
        }
        if (item.modLoader != null && !item.modLoader.isEmpty()) {
            String modrinthLoader = item.modLoader.toLowerCase();
            if (modrinthLoader.equals("neoforge")) modrinthLoader = "neo-forge";
            params.put("loaders", "[\"" + modrinthLoader + "\"]");
        }

        JsonArray response = mApiHandler.get(String.format("project/%s/version", item.id), params, JsonArray.class);
        if(response == null) return null;
        System.out.println(response);
        String[] names = new String[response.size()];
        String[] mcNames = new String[response.size()];
        String[] urls = new String[response.size()];
        String[] hashes = new String[response.size()];
        String[] versionIds = new String[response.size()];

        for (int i=0; i<response.size(); ++i) {
            JsonObject version = response.get(i).getAsJsonObject();
            names[i] = version.get("name").getAsString();
            mcNames[i] = version.get("game_versions").getAsJsonArray().get(0).getAsString();
            urls[i] = version.get("files").getAsJsonArray().get(0).getAsJsonObject().get("url").getAsString();
            versionIds[i] = version.get("id").getAsString();
            // Assume there may not be hashes, in case the API changes
            JsonObject hashesMap = version.getAsJsonArray("files").get(0).getAsJsonObject()
                    .get("hashes").getAsJsonObject();
            if(hashesMap == null || hashesMap.get("sha1") == null){
                hashes[i] = null;
                continue;
            }

            hashes[i] = hashesMap.get("sha1").getAsString();
        }

        return new ModDetail(item, names, mcNames, urls, hashes, versionIds);
    }

    @Override
    public ModLoader installMod(ModDetail modDetail, int selectedVersion) throws IOException{
        //TODO considering only modpacks for now
        return ModpackInstaller.installModpack(modDetail, selectedVersion, this::installMrpack);
    }

    @Override
    public ModLoader importModpack(Activity activity, Uri zipUri) throws IOException, NoSuchAlgorithmException {
        return ModpackInstaller.importModpack(activity, zipUri, this::installMrpack);
    }

    private static ModLoader createInfo(ModrinthIndex modrinthIndex) {
        if(modrinthIndex == null) return null;
        Map<String, String> dependencies = modrinthIndex.dependencies;
        String mcVersion = dependencies.get("minecraft");
        if(mcVersion == null) return null;
        String modLoaderVersion;
        if((modLoaderVersion = dependencies.get("forge")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_FORGE, modLoaderVersion, mcVersion);
        }
        if((modLoaderVersion = dependencies.get("fabric-loader")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_FABRIC, modLoaderVersion, mcVersion);
        }
        if((modLoaderVersion = dependencies.get("quilt-loader")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_QUILT, modLoaderVersion, mcVersion);
        }
        if((modLoaderVersion = dependencies.get("neoforge")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_NEOFORGE, modLoaderVersion, mcVersion);
        }
        return null;
    }

    private ModLoader installMrpack(File mrpackFile, File instanceDestination) throws IOException {
        try (ZipFile modpackZipFile = new ZipFile(mrpackFile)){
            ModrinthIndex modrinthIndex = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(ZipUtils.getEntryStream(modpackZipFile, "modrinth.index.json")),
                    ModrinthIndex.class);
            
            ModDownloader modDownloader = new ModDownloader(instanceDestination);
            for(ModrinthIndex.ModrinthIndexFile indexFile : modrinthIndex.files) {
                modDownloader.submitDownload(indexFile.fileSize, indexFile.path, indexFile.hashes.sha1, indexFile.downloads);
            }
            modDownloader.awaitFinish(new DownloaderProgressWrapper(R.string.modpack_download_downloading_mods, ProgressLayout.INSTALL_MODPACK));
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.modpack_download_applying_overrides, 1, 2);
            ZipUtils.zipExtract(modpackZipFile, "overrides/", instanceDestination);
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 50, R.string.modpack_download_applying_overrides, 2, 2);
            ZipUtils.zipExtract(modpackZipFile, "client-overrides/", instanceDestination);
            return createInfo(modrinthIndex);
        }
    }

    public ModItem getCompatibleVersion(String projectId, String mcVersion, String loader) {
        HashMap<String, Object> params = new HashMap<>();
        if (mcVersion != null) params.put("game_versions", "[\"" + mcVersion + "\"]");
        if (loader != null) {
            String modrinthLoader = loader.toLowerCase();
            if (modrinthLoader.equals("neoforge")) modrinthLoader = "neo-forge";
            params.put("loaders", "[\"" + modrinthLoader + "\"]");
        }
        
        JsonArray response = mApiHandler.get(String.format("project/%s/version", projectId), params, JsonArray.class);
        if (response == null || response.size() == 0) return null;
        
        JsonObject version = response.get(0).getAsJsonObject();
        
        ModItem item = new ModItem();
        item.id = projectId;
        item.projectId = projectId;
        item.apiSource = Constants.SOURCE_MODRINTH;
        item.versionId = version.has("id") && !version.get("id").isJsonNull() ? version.get("id").getAsString() : null;
        item.version = version.has("version_number") && !version.get("version_number").isJsonNull() ? version.get("version_number").getAsString() : "";
        
        JsonArray files = version.getAsJsonArray("files");
        if (files == null || files.size() == 0) return null;
        
        JsonObject file = files.get(0).getAsJsonObject();
        for (int i=0; i<files.size(); i++) {
            com.google.gson.JsonElement primaryEl = files.get(i).getAsJsonObject().get("primary");
            if (primaryEl != null && !primaryEl.isJsonNull() && primaryEl.getAsBoolean()) {
                file = files.get(i).getAsJsonObject();
                break;
            }
        }
        
        item.fileName = file.has("filename") && !file.get("filename").isJsonNull() ? file.get("filename").getAsString() : "mod.jar";
        item.title = item.fileName;
        item.downloadUrl = file.has("url") && !file.get("url").isJsonNull() ? file.get("url").getAsString() : null;
        
        item.sha1 = null;
        if (file.has("hashes") && !file.get("hashes").isJsonNull()) {
            JsonObject hashes = file.getAsJsonObject("hashes");
            if (hashes.has("sha1") && !hashes.get("sha1").isJsonNull()) {
                item.sha1 = hashes.get("sha1").getAsString();
            }
        }
        
        item.fileSize = file.has("size") && !file.get("size").isJsonNull() ? file.get("size").getAsLong() : 0;
        item.mcVersion = mcVersion;
        item.modLoader = loader;
        
        JsonArray deps = version.getAsJsonArray("dependencies");
        item.requiredDependencyIds = new java.util.ArrayList<>();
        item.optionalDependencyIds = new java.util.ArrayList<>();
        if (deps != null) {
            for (int i=0; i<deps.size(); i++) {
                JsonObject dep = deps.get(i).getAsJsonObject();
                if (!dep.has("dependency_type")) continue;
                String type = dep.get("dependency_type").getAsString();
                String targetId = null;
                if (dep.has("project_id") && !dep.get("project_id").isJsonNull()) {
                    targetId = dep.get("project_id").getAsString();
                } else if (dep.has("version_id") && !dep.get("version_id").isJsonNull()) {
                    targetId = dep.get("version_id").getAsString();
                }
                
                if (targetId != null) {
                    if (type.equals("required")) item.requiredDependencyIds.add(targetId);
                    else if (type.equals("optional")) item.optionalDependencyIds.add(targetId);
                }
            }
        }
        return item;
    }

    public ModItem getVersionDetails(String versionId, String mcVersion, String loader) {
        JsonObject version = mApiHandler.get(String.format("version/%s", versionId), JsonObject.class);
        if (version == null) return null;
        
        ModItem item = new ModItem();
        String projectId = version.has("project_id") && !version.get("project_id").isJsonNull() ? version.get("project_id").getAsString() : null;
        item.id = projectId;
        item.projectId = projectId;
        item.apiSource = Constants.SOURCE_MODRINTH;
        item.versionId = versionId;
        item.version = version.has("version_number") && !version.get("version_number").isJsonNull() ? version.get("version_number").getAsString() : "";
        
        JsonArray files = version.getAsJsonArray("files");
        if (files == null || files.size() == 0) return null;
        
        JsonObject file = files.get(0).getAsJsonObject();
        for (int i=0; i<files.size(); i++) {
            com.google.gson.JsonElement primaryEl = files.get(i).getAsJsonObject().get("primary");
            if (primaryEl != null && !primaryEl.isJsonNull() && primaryEl.getAsBoolean()) {
                file = files.get(i).getAsJsonObject();
                break;
            }
        }
        
        item.fileName = file.has("filename") && !file.get("filename").isJsonNull() ? file.get("filename").getAsString() : "mod.jar";
        item.title = item.fileName;
        item.downloadUrl = file.has("url") && !file.get("url").isJsonNull() ? file.get("url").getAsString() : null;
        
        item.sha1 = null;
        if (file.has("hashes") && !file.get("hashes").isJsonNull()) {
            JsonObject hashes = file.getAsJsonObject("hashes");
            if (hashes.has("sha1") && !hashes.get("sha1").isJsonNull()) {
                item.sha1 = hashes.get("sha1").getAsString();
            }
        }
        
        item.fileSize = file.has("size") && !file.get("size").isJsonNull() ? file.get("size").getAsLong() : 0;
        item.mcVersion = mcVersion;
        item.modLoader = loader;
        
        JsonArray deps = version.getAsJsonArray("dependencies");
        item.requiredDependencyIds = new java.util.ArrayList<>();
        item.optionalDependencyIds = new java.util.ArrayList<>();
        if (deps != null) {
            for (int i=0; i<deps.size(); i++) {
                JsonObject dep = deps.get(i).getAsJsonObject();
                if (!dep.has("dependency_type")) continue;
                String type = dep.get("dependency_type").getAsString();
                String targetId = null;
                if (dep.has("project_id") && !dep.get("project_id").isJsonNull()) {
                    targetId = dep.get("project_id").getAsString();
                } else if (dep.has("version_id") && !dep.get("version_id").isJsonNull()) {
                    targetId = dep.get("version_id").getAsString();
                }
                
                if (targetId != null) {
                    if (type.equals("required")) item.requiredDependencyIds.add(targetId);
                    else if (type.equals("optional")) item.optionalDependencyIds.add(targetId);
                }
            }
        }
        return item;
    }

    class ModrinthSearchResult extends SearchResult {
        int previousOffset;
    }
}
