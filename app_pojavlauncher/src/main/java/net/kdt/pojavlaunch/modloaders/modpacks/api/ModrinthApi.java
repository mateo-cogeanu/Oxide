package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.app.Activity;
import android.net.Uri;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.InstanceManager;
import net.kdt.pojavlaunch.modloaders.modpacks.ModCompatibility;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModrinthIndex;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;

import org.apache.commons.io.FilenameUtils;
import net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper;
import net.kdt.pojavlaunch.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

public class ModrinthApi implements ModpackApi{
    private final ApiHandler mApiHandler;
    private static final String PROJECT_MODPACK = "modpack";
    private static final String PROJECT_MOD = "mod";
    private static final String PROJECT_RESOURCEPACK = "resourcepack";
    private static final String PROJECT_SHADER = "shader";

    public ModrinthApi(){
        mApiHandler = new ApiHandler("https://api.modrinth.com/v2");
    }

    public ModUpdate findCompatibleUpdate(String sha1, String targetMcVersion, String targetLoader) {
        if (!Tools.isValidString(sha1)) return null;

        HashMap<String, Object> query = new HashMap<>();
        query.put("algorithm", "sha1");
        JsonObject currentVersion = mApiHandler.get("version_file/" + sha1, query, JsonObject.class);
        if (currentVersion == null || !currentVersion.has("project_id") || !currentVersion.has("id")) return null;

        String projectId = currentVersion.get("project_id").getAsString();
        String currentVersionId = currentVersion.get("id").getAsString();
        String currentVersionName = readVersionLabel(currentVersion);
        JsonArray versions = mApiHandler.get("project/" + projectId + "/version", JsonArray.class);
        if (versions == null) return null;

        for (JsonElement element : versions) {
            JsonObject version = element == null || !element.isJsonObject() ? null : element.getAsJsonObject();
            if (version == null) continue;
            if (!matchesTargetVersion(version.getAsJsonArray("game_versions"), targetMcVersion)) continue;
            if (!matchesTargetLoader(version.getAsJsonArray("loaders"), targetLoader)) continue;

            String candidateId = getJsonString(version, "id");
            String candidateName = readVersionLabel(version);
            if (!Tools.isValidString(candidateId) || candidateId.equals(currentVersionId)) {
                return new ModUpdate(projectId, currentVersionId, currentVersionName, currentVersionName, false);
            }
            return new ModUpdate(projectId, currentVersionId, currentVersionName, candidateName, true);
        }

        return new ModUpdate(projectId, currentVersionId, currentVersionName, currentVersionName, false);
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
        String projectType = searchFilters.isModpack ? PROJECT_MODPACK : (Tools.isValidString(searchFilters.projectType) ? searchFilters.projectType : PROJECT_MOD);
        facetString.append("[");
        facetString.append(String.format("[\"project_type:%s\"]", projectType));
        if(searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            facetString.append(String.format(",[\"versions:%s\"]", searchFilters.mcVersion));
        if(PROJECT_MOD.equals(projectType) && searchFilters.modLoader != null && !searchFilters.modLoader.isEmpty())
            facetString.append(String.format(",[\"categories:%s\"]", searchFilters.modLoader));
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
            String hitProjectType = hit.get("project_type").getAsString();
            items[i] = new ModItem(
                    Constants.SOURCE_MODRINTH,
                    hitProjectType.equals(PROJECT_MODPACK),
                    hitProjectType,
                    resolveInstallSubdirectory(hitProjectType),
                    hit.get("project_id").getAsString(),
                    hit.get("title").getAsString(),
                    hit.get("description").getAsString(),
                    hit.get("icon_url").getAsString()
            );
        }
        if(modrinthSearchResult == null) modrinthSearchResult = new ModrinthSearchResult();
        modrinthSearchResult.previousOffset += responseHits.size();
        modrinthSearchResult.results = items;
        modrinthSearchResult.totalResultCount = response.get("total_hits").getAsInt();
        return modrinthSearchResult;
    }

    @Override
    public ModDetail getModDetails(ModItem item) {

        JsonArray response = mApiHandler.get(String.format("project/%s/version", item.id), JsonArray.class);
        if(response == null) return null;
        System.out.println(response);
        String[] names = new String[response.size()];
        String[] mcNames = new String[response.size()];
        String[][] versionLoaders = new String[response.size()][];
        String[] versionIds = new String[response.size()];
        String[] urls = new String[response.size()];
        String[] hashes = new String[response.size()];
        String[][] dependencyIds = new String[response.size()][];

        for (int i=0; i<response.size(); ++i) {
            JsonObject version = response.get(i).getAsJsonObject();
            versionIds[i] = version.get("id").getAsString();
            names[i] = version.get("name").getAsString();
            mcNames[i] = version.get("game_versions").getAsJsonArray().get(0).getAsString();
            JsonArray loaders = version.getAsJsonArray("loaders");
            if (loaders != null) {
                versionLoaders[i] = new String[loaders.size()];
                for (int j = 0; j < loaders.size(); j++) {
                    versionLoaders[i][j] = loaders.get(j).getAsString();
                }
            }

            JsonArray files = version.getAsJsonArray("files");
            JsonObject selectedFile = files.get(0).getAsJsonObject();
            for (int fileIndex = 0; fileIndex < files.size(); fileIndex++) {
                JsonObject file = files.get(fileIndex).getAsJsonObject();
                if (file.has("primary") && file.get("primary").getAsBoolean()) {
                    selectedFile = file;
                    break;
                }
            }
            urls[i] = selectedFile.get("url").getAsString();
            // Assume there may not be hashes, in case the API changes
            JsonObject hashesMap = selectedFile.get("hashes").getAsJsonObject();
            if(hashesMap == null || hashesMap.get("sha1") == null){
                hashes[i] = null;
                continue;
            }

            hashes[i] = hashesMap.get("sha1").getAsString();

            JsonArray dependencies = version.getAsJsonArray("dependencies");
            ArrayList<String> requiredDependencies = new ArrayList<>();
            if (dependencies != null) {
                for (int j = 0; j < dependencies.size(); j++) {
                    JsonObject dependency = dependencies.get(j).getAsJsonObject();
                    if (dependency == null || dependency.get("dependency_type") == null) continue;
                    String dependencyType = dependency.get("dependency_type").getAsString();
                    if (!"required".equalsIgnoreCase(dependencyType)) continue;
                    if (dependency.get("project_id") != null && !dependency.get("project_id").isJsonNull()) {
                        requiredDependencies.add(dependency.get("project_id").getAsString());
                    }
                }
            }
            dependencyIds[i] = requiredDependencies.toArray(new String[0]);
        }

        return new ModDetail(item, names, mcNames, versionLoaders, versionIds, urls, hashes, dependencyIds);
    }

    @Override
    public ModLoader installMod(ModDetail modDetail, int selectedVersion) throws IOException{
        if (!modDetail.isModpack) {
            installSingleMod(modDetail, selectedVersion);
            return null;
        }
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

    private void installSingleMod(ModDetail modDetail, int selectedVersion) throws IOException {
        LauncherProfiles.load();
        File destinationDirectory = getInstallDirectory(modDetail);
        if (!destinationDirectory.exists() && !destinationDirectory.mkdirs()) {
            throw new IOException("Failed to create destination directory: " + destinationDirectory);
        }
        String targetMcVersion = modDetail.mcVersionNames[selectedVersion];
        String targetLoader = ModCompatibility.extractModLoader(LauncherProfiles.getCurrentProfile().lastVersionId);
        Set<String> visitedProjects = new HashSet<>();
        ModDownloader modDownloader = new ModDownloader(destinationDirectory, true);
        enqueueModAndDependencies(modDownloader, modDetail, selectedVersion, targetMcVersion, targetLoader, visitedProjects);
        modDownloader.awaitFinish(new DownloaderProgressWrapper(R.string.mod_download_progress, ProgressLayout.INSTALL_MODPACK));
    }

    private void enqueueModAndDependencies(ModDownloader modDownloader, ModDetail modDetail, int selectedVersion,
                                           String targetMcVersion, String targetLoader, Set<String> visitedProjects) throws IOException {
        if (modDetail == null || selectedVersion < 0 || selectedVersion >= modDetail.versionUrls.length) return;
        if (!visitedProjects.add(modDetail.id)) return;

        String[] dependencies = modDetail.dependencyIds != null && selectedVersion < modDetail.dependencyIds.length
                ? modDetail.dependencyIds[selectedVersion] : null;
        if (dependencies != null) {
            for (String dependencyId : dependencies) {
                ModDetail dependencyDetail = getModDetails(new ModItem(Constants.SOURCE_MODRINTH, false, PROJECT_MOD, "mods", dependencyId, dependencyId, "", ""));
                int dependencyVersionIndex = dependencyDetail == null ? -1
                        : ModCompatibility.findCompatibleVersionIndex(
                                dependencyDetail.mcVersionNames,
                                dependencyDetail.versionLoaders,
                                dependencyDetail.versionNames,
                                targetMcVersion,
                                targetLoader
                        );
                if (dependencyVersionIndex >= 0) {
                    enqueueModAndDependencies(modDownloader, dependencyDetail, dependencyVersionIndex, targetMcVersion, targetLoader, visitedProjects);
                }
            }
        }

        String downloadUrl = modDetail.versionUrls[selectedVersion];
        String fileName = FilenameUtils.getName(downloadUrl);
        if (!Tools.isValidString(fileName)) {
            throw new IOException("Failed to resolve file name from " + downloadUrl);
        }

        modDownloader.submitDownload(() -> new ModDownloader.FileInfo(
                downloadUrl,
                fileName,
                modDetail.versionHashes[selectedVersion]
        ));
    }

    class ModrinthSearchResult extends SearchResult {
        int previousOffset;
    }

    private static String resolveInstallSubdirectory(String projectType) {
        if (PROJECT_RESOURCEPACK.equals(projectType)) return "resourcepacks";
        if (PROJECT_SHADER.equals(projectType)) return "shaderpacks";
        return "mods";
    }

    private static File getInstallDirectory(ModDetail modDetail) {
        String subdirectory = Tools.isValidString(modDetail.installSubdirectory) ? modDetail.installSubdirectory : "mods";
        if ("resourcepacks".equals(subdirectory)) {
            return InstanceManager.getResourcePacksDirectory(LauncherProfiles.getCurrentProfile());
        }
        if ("shaderpacks".equals(subdirectory)) {
            return InstanceManager.getShaderPacksDirectory(LauncherProfiles.getCurrentProfile());
        }
        return InstanceManager.getModsDirectory(LauncherProfiles.getCurrentProfile());
    }

    private boolean matchesTargetVersion(JsonArray gameVersions, String targetMcVersion) {
        if (!Tools.isValidString(targetMcVersion)) return true;
        if (gameVersions == null) return false;
        for (JsonElement element : gameVersions) {
            if (element != null && element.isJsonPrimitive() && targetMcVersion.equalsIgnoreCase(element.getAsString())) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesTargetLoader(JsonArray loaders, String targetLoader) {
        if (!Tools.isValidString(targetLoader)) return true;
        if (loaders == null) return false;
        String[] candidates = new String[loaders.size()];
        for (int i = 0; i < loaders.size(); i++) {
            candidates[i] = loaders.get(i).getAsString();
        }
        return ModCompatibility.matchesRequestedLoader(candidates, targetLoader);
    }

    private String readVersionLabel(JsonObject version) {
        String versionNumber = getJsonString(version, "version_number");
        if (Tools.isValidString(versionNumber)) return versionNumber;
        String name = getJsonString(version, "name");
        if (Tools.isValidString(name)) return name;
        return getJsonString(version, "id");
    }

    private String getJsonString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return null;
        return object.get(key).getAsString();
    }

    public static class ModUpdate {
        public final String projectId;
        public final String currentVersionId;
        public final String currentVersionName;
        public final String latestVersionName;
        public final boolean hasUpdate;

        public ModUpdate(String projectId, String currentVersionId, String currentVersionName, String latestVersionName, boolean hasUpdate) {
            this.projectId = projectId;
            this.currentVersionId = currentVersionId;
            this.currentVersionName = currentVersionName;
            this.latestVersionName = latestVersionName;
            this.hasUpdate = hasUpdate;
        }
    }
}
