package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.app.Activity;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.instances.InstanceManager;
import net.kdt.pojavlaunch.modloaders.modpacks.ModCompatibility;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.CurseManifest;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.GsonJsonUtils;
import net.kdt.pojavlaunch.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

public class CurseforgeApi implements ModpackApi{
    private static final Pattern sMcVersionPattern = Pattern.compile("([0-9]+)\\.([0-9]+)\\.?([0-9]+)?");
    private static final int ALGO_SHA_1 = 1;
    // Stolen from
    // https://github.com/AnzhiZhang/CurseForgeModpackDownloader/blob/6cb3f428459f0cc8f444d16e54aea4cd1186fd7b/utils/requester.py#L93
    private static final int CURSEFORGE_MINECRAFT_GAME_ID = 432;
    private static final int CURSEFORGE_MODPACK_CLASS_ID = 4471;
    // https://api.curseforge.com/v1/categories?gameId=432 and search for "Mods" (case-sensitive)
    private static final int CURSEFORGE_MOD_CLASS_ID = 6;
    private static final int CURSEFORGE_SORT_RELEVANCY = 1;
    private static final int CURSEFORGE_PAGINATION_SIZE = 50;
    private static final int CURSEFORGE_PAGINATION_END_REACHED = -1;
    private static final int CURSEFORGE_PAGINATION_ERROR = -2;

    private final ApiHandler mApiHandler;
    public CurseforgeApi(String apiKey) {
        mApiHandler = new ApiHandler("https://api.curseforge.com/v1", apiKey);
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        CurseforgeSearchResult curseforgeSearchResult = (CurseforgeSearchResult) previousPageResult;

        HashMap<String, Object> params = new HashMap<>();
        params.put("gameId", CURSEFORGE_MINECRAFT_GAME_ID);
        params.put("classId", searchFilters.isModpack ? CURSEFORGE_MODPACK_CLASS_ID : CURSEFORGE_MOD_CLASS_ID);
        params.put("searchFilter", searchFilters.name);
        params.put("sortField", CURSEFORGE_SORT_RELEVANCY);
        params.put("sortOrder", "desc");
        if(searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            params.put("gameVersion", searchFilters.mcVersion);
        if(previousPageResult != null)
            params.put("index", curseforgeSearchResult.previousOffset);

        JsonObject response = mApiHandler.get("mods/search", params, JsonObject.class);
        if(response == null) return null;
        JsonArray dataArray = response.getAsJsonArray("data");
        if(dataArray == null) return null;
        JsonObject paginationInfo = response.getAsJsonObject("pagination");
        ArrayList<ModItem> modItemList = new ArrayList<>(dataArray.size());
        for(int i = 0; i < dataArray.size(); i++) {
            JsonObject dataElement = dataArray.get(i).getAsJsonObject();
            JsonElement allowModDistribution = dataElement.get("allowModDistribution");
            // Gson automatically casts null to false, which leans to issues
            // So, only check the distribution flag if it is non-null
            if(!allowModDistribution.isJsonNull() && !allowModDistribution.getAsBoolean()) {
                Log.i("CurseforgeApi", "Skipping modpack "+dataElement.get("name").getAsString() + " because curseforge sucks");
                continue;
            }
            if (!matchesSearchFilters(dataElement, searchFilters)) continue;
            ModItem modItem = new ModItem(Constants.SOURCE_CURSEFORGE,
                    searchFilters.isModpack,
                    dataElement.get("id").getAsString(),
                    dataElement.get("name").getAsString(),
                    dataElement.get("summary").getAsString(),
                    dataElement.getAsJsonObject("logo").get("thumbnailUrl").getAsString());
            modItemList.add(modItem);
        }
        if(curseforgeSearchResult == null) curseforgeSearchResult = new CurseforgeSearchResult();
        curseforgeSearchResult.results = modItemList.toArray(new ModItem[0]);
        curseforgeSearchResult.totalResultCount = paginationInfo.get("totalCount").getAsInt();
        curseforgeSearchResult.previousOffset += dataArray.size();
        return curseforgeSearchResult;

    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        ArrayList<JsonObject> allModDetails = new ArrayList<>();
        int index = 0;
        while(index != CURSEFORGE_PAGINATION_END_REACHED &&
                index != CURSEFORGE_PAGINATION_ERROR) {
            index = getPaginatedDetails(allModDetails, index, item.id);
        }
        if(index == CURSEFORGE_PAGINATION_ERROR) return null;
        int length = allModDetails.size();
        String[] versionNames = new String[length];
        String[] mcVersionNames = new String[length];
        String[] versionIds = new String[length];
        String[] versionUrls = new String[length];
        String[] hashes = new String[length];
        String[][] dependencyIds = new String[length][];
        for(int i = 0; i < allModDetails.size(); i++) {
            JsonObject modDetail = allModDetails.get(i);
            versionIds[i] = modDetail.get("id").getAsString();
            versionNames[i] = modDetail.get("displayName").getAsString();

            JsonElement downloadUrl = modDetail.get("downloadUrl");
            versionUrls[i] = downloadUrl.getAsString();

            JsonArray gameVersions = modDetail.getAsJsonArray("gameVersions");
            for(JsonElement jsonElement : gameVersions) {
                String gameVersion = jsonElement.getAsString();
                if(!sMcVersionPattern.matcher(gameVersion).matches()) {
                    continue;
                }
                mcVersionNames[i] = gameVersion;
                break;
            }

            hashes[i] = getSha1FromModData(modDetail);
            dependencyIds[i] = getRequiredDependencyIds(modDetail);
        }
        return new ModDetail(item, versionNames, mcVersionNames, versionIds, versionUrls, hashes, dependencyIds);
    }

    @Override
    public ModLoader installMod(ModDetail modDetail, int selectedVersion) throws IOException{
        if (!modDetail.isModpack) {
            installSingleMod(modDetail, selectedVersion);
            return null;
        }
        return ModpackInstaller.installModpack(modDetail, selectedVersion, this::installCurseforgeZip);
    }

    @Override
    public ModLoader importModpack(Activity activity, Uri zipUri) throws IOException, NoSuchAlgorithmException {
        return ModpackInstaller.importModpack(activity, zipUri, this::installCurseforgeZip);
    }


    private int getPaginatedDetails(ArrayList<JsonObject> objectList, int index, String modId) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("index", index);
        params.put("pageSize", CURSEFORGE_PAGINATION_SIZE);

        JsonObject response = mApiHandler.get("mods/"+modId+"/files", params, JsonObject.class);
        JsonArray data = GsonJsonUtils.getJsonArraySafe(response, "data");
        if(data == null) return CURSEFORGE_PAGINATION_ERROR;

        for(int i = 0; i < data.size(); i++) {
            JsonObject fileInfo = data.get(i).getAsJsonObject();
            if(fileInfo.get("isServerPack").getAsBoolean()) continue;
            objectList.add(fileInfo);
        }
        if(data.size() < CURSEFORGE_PAGINATION_SIZE) {
            return CURSEFORGE_PAGINATION_END_REACHED; // we read the remainder! yay!
        }
        return index + data.size();
    }

    private ModLoader installCurseforgeZip(File zipFile, File instanceDestination) throws IOException {
        try (ZipFile modpackZipFile = new ZipFile(zipFile)){
            CurseManifest curseManifest = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(ZipUtils.getEntryStream(modpackZipFile, "manifest.json")),
                    CurseManifest.class);
            if(!verifyManifest(curseManifest)) {
                Log.i("CurseforgeApi","manifest verification failed");
                return null;
            }
            ModDownloader modDownloader = new ModDownloader(new File(instanceDestination,"mods"), true);
            int fileCount = curseManifest.files.length;
            for(int i = 0; i < fileCount; i++) {
                final CurseManifest.CurseFile curseFile = curseManifest.files[i];
                modDownloader.submitDownload(()->{
                    String url = getDownloadUrl(curseFile.projectID, curseFile.fileID);
                    if(url == null && curseFile.required)
                        throw new IOException("Failed to obtain download URL for "+curseFile.projectID+" "+curseFile.fileID);
                    else if(url == null) return null;
                    return new ModDownloader.FileInfo(url, FileUtils.getFileName(url), getDownloadSha1(curseFile.projectID, curseFile.fileID));
                });
            }
            modDownloader.awaitFinish((c,m)->
                    ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, (int) Math.max((float)c/m*100,0), R.string.modpack_download_downloading_mods_fc, c, m)
            );
            String overridesDir = "overrides";
            if(curseManifest.overrides != null) overridesDir = curseManifest.overrides;
            ZipUtils.zipExtract(modpackZipFile, overridesDir, instanceDestination);
            return createInfo(curseManifest.minecraft);
        }
    }

    private void installSingleMod(ModDetail modDetail, int selectedVersion) throws IOException {
        LauncherProfiles.load();
        File modsDirectory = InstanceManager.getModsDirectory(LauncherProfiles.getCurrentProfile());
        if (!modsDirectory.exists() && !modsDirectory.mkdirs()) {
            throw new IOException("Failed to create mods directory: " + modsDirectory);
        }

        String targetMcVersion = modDetail.mcVersionNames[selectedVersion];
        String targetLoader = ModCompatibility.extractModLoader(LauncherProfiles.getCurrentProfile().lastVersionId);
        Set<String> visitedProjects = new HashSet<>();
        ModDownloader modDownloader = new ModDownloader(modsDirectory, true);
        enqueueModAndDependencies(modDownloader, modDetail, selectedVersion, targetMcVersion, targetLoader, visitedProjects);
        modDownloader.awaitFinish((c, m) ->
                ProgressKeeper.submitProgress(ProgressLayout.INSTALL_MODPACK, (int) Math.max((float) c / m * 100, 0), R.string.mod_download_progress)
        );
    }

    private void enqueueModAndDependencies(ModDownloader modDownloader, ModDetail modDetail, int selectedVersion,
                                           String targetMcVersion, String targetLoader, Set<String> visitedProjects) throws IOException {
        if (modDetail == null || selectedVersion < 0 || selectedVersion >= modDetail.versionUrls.length) return;
        if (!visitedProjects.add(modDetail.id)) return;

        String[] dependencies = modDetail.dependencyIds != null && selectedVersion < modDetail.dependencyIds.length
                ? modDetail.dependencyIds[selectedVersion] : null;
        if (dependencies != null) {
            for (String dependencyId : dependencies) {
                ModDetail dependencyDetail = getModDetails(new ModItem(Constants.SOURCE_CURSEFORGE, false, dependencyId, dependencyId, "", ""));
                int dependencyVersionIndex = dependencyDetail == null ? -1
                        : ModCompatibility.findCompatibleVersionIndex(
                                dependencyDetail.mcVersionNames,
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
        modDownloader.submitDownload(() -> new ModDownloader.FileInfo(
                downloadUrl,
                FileUtils.getFileName(downloadUrl),
                modDetail.versionHashes[selectedVersion]
        ));
    }

    private ModLoader createInfo(CurseManifest.CurseMinecraft minecraft) {
        CurseManifest.CurseModLoader primaryModLoader = null;
        for(CurseManifest.CurseModLoader modLoader : minecraft.modLoaders) {
            if(modLoader.primary) {
                primaryModLoader = modLoader;
                break;
            }
        }
        if(primaryModLoader == null) primaryModLoader = minecraft.modLoaders[0];
        String modLoaderId = primaryModLoader.id;
        int dashIndex = modLoaderId.indexOf('-');
        String modLoaderName = modLoaderId.substring(0, dashIndex);
        String modLoaderVersion = modLoaderId.substring(dashIndex+1);
        Log.i("CurseforgeApi", modLoaderId + " " + modLoaderName + " "+modLoaderVersion);
        int modLoaderTypeInt;
        switch (modLoaderName) {
            case "forge":
                modLoaderTypeInt = ModLoader.MOD_LOADER_FORGE;
                break;
            case "fabric":
                modLoaderTypeInt = ModLoader.MOD_LOADER_FABRIC;
                break;
            case "neoforge":
                modLoaderTypeInt = ModLoader.MOD_LOADER_NEOFORGE;
                break;
            default:
                return null;
            //TODO: Quilt is also Forge? How does that work?
        }
        return new ModLoader(modLoaderTypeInt, modLoaderVersion, minecraft.version);
    }

    private String getDownloadUrl(long projectID, long fileID) {
        // First try the official api endpoint
        JsonObject response = mApiHandler.get("mods/"+projectID+"/files/"+fileID+"/download-url", JsonObject.class);
        if (response != null && !response.get("data").isJsonNull())
            return response.get("data").getAsString();

        // Otherwise, fallback to building an edge link
        JsonObject fallbackResponse = mApiHandler.get(String.format("mods/%s/files/%s", projectID, fileID), JsonObject.class);
        if (fallbackResponse != null && !fallbackResponse.get("data").isJsonNull()){
            JsonObject modData = fallbackResponse.get("data").getAsJsonObject();
            int id = modData.get("id").getAsInt();
            return String.format("https://edge.forgecdn.net/files/%s/%s/%s", id/1000, id % 1000, modData.get("fileName").getAsString());
        }

        return null;
    }

    private @Nullable String getDownloadSha1(long projectID, long fileID) {
        // Try the api endpoint, die in the other case
        JsonObject response = mApiHandler.get("mods/"+projectID+"/files/"+fileID, JsonObject.class);
        JsonObject data = GsonJsonUtils.getJsonObjectSafe(response, "data");
        if(data == null) return null;
        return getSha1FromModData(data);
    }

    private String getSha1FromModData(@NonNull JsonObject object) {
        JsonArray hashes = GsonJsonUtils.getJsonArraySafe(object, "hashes");
        if(hashes == null) return null;
        for (JsonElement jsonElement : hashes) {
            // The sha1 = 1; md5 = 2;
            JsonObject jsonObject = GsonJsonUtils.getJsonObjectSafe(jsonElement);
            if(GsonJsonUtils.getIntSafe(
                    jsonObject,
                    "algo",
                    -1) == ALGO_SHA_1) {
                return GsonJsonUtils.getStringSafe(jsonObject, "value");
            }
        }
        return null;
    }

    private boolean matchesSearchFilters(JsonObject dataElement, SearchFilters searchFilters) {
        if ((searchFilters.mcVersion == null || searchFilters.mcVersion.isEmpty())
                && (searchFilters.modLoader == null || searchFilters.modLoader.isEmpty())) {
            return true;
        }
        JsonArray latestFiles = GsonJsonUtils.getJsonArraySafe(dataElement, "latestFiles");
        if (latestFiles == null) return true;
        for (JsonElement fileElement : latestFiles) {
            JsonObject fileInfo = GsonJsonUtils.getJsonObjectSafe(fileElement);
            if (fileInfo == null) continue;
            JsonArray gameVersions = GsonJsonUtils.getJsonArraySafe(fileInfo, "gameVersions");
            if (gameVersions == null) continue;
            boolean versionMatch = !Tools.isValidString(searchFilters.mcVersion);
            boolean loaderMatch = !Tools.isValidString(searchFilters.modLoader);
            for (JsonElement versionElement : gameVersions) {
                String gameVersion = versionElement.getAsString();
                if (!versionMatch && searchFilters.mcVersion.equalsIgnoreCase(gameVersion)) versionMatch = true;
                if (!loaderMatch && ModCompatibility.matchesRequestedLoader(gameVersion, searchFilters.modLoader)) loaderMatch = true;
            }
            if (versionMatch && loaderMatch) return true;
        }
        return false;
    }

    private String[] getRequiredDependencyIds(JsonObject modDetail) {
        JsonArray dependencies = GsonJsonUtils.getJsonArraySafe(modDetail, "dependencies");
        if (dependencies == null) return new String[0];
        ArrayList<String> ids = new ArrayList<>();
        for (JsonElement dependencyElement : dependencies) {
            JsonObject dependency = GsonJsonUtils.getJsonObjectSafe(dependencyElement);
            if (dependency == null) continue;
            if (GsonJsonUtils.getIntSafe(dependency, "relationType", -1) != 3) continue;
            int modId = GsonJsonUtils.getIntSafe(dependency, "modId", -1);
            if (modId != -1) ids.add(String.valueOf(modId));
        }
        return ids.toArray(new String[0]);
    }

    private boolean verifyManifest(CurseManifest manifest) {
        if(!"minecraftModpack".equals(manifest.manifestType)) return false;
        if(manifest.manifestVersion != 1) return false;
        if(manifest.minecraft == null) return false;
        if(manifest.minecraft.version == null) return false;
        if(manifest.minecraft.modLoaders == null) return false;
        return manifest.minecraft.modLoaders.length >= 1;
    }

    static class CurseforgeSearchResult extends SearchResult {
        int previousOffset;
    }
}
