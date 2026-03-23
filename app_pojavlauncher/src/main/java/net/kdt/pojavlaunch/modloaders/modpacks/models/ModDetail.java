package net.kdt.pojavlaunch.modloaders.modpacks.models;


import androidx.annotation.NonNull;

import java.util.Arrays;

public class ModDetail extends ModItem {
    /* A cheap way to map from the front facing name to the underlying id */
    public String[] versionNames;
    public String [] mcVersionNames;
    public String[][] versionLoaders;
    public String[] versionIds;
    public String[] versionUrls;
    /* SHA 1 hashes, null if a hash is unavailable */
    public String[] versionHashes;
    public String[][] dependencyIds;

    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames, String[] versionIds,
                     String[] versionUrls, String[] hashes, String[][] dependencyIds) {
        this(item, versionNames, mcVersionNames, null, versionIds, versionUrls, hashes, dependencyIds);
    }

    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames, String[][] versionLoaders,
                     String[] versionIds, String[] versionUrls, String[] hashes, String[][] dependencyIds) {
        super(item.apiSource, item.isModpack, item.projectType, item.installSubdirectory, item.id, item.title, item.description, item.imageUrl);
        this.versionNames = versionNames;
        this.mcVersionNames = mcVersionNames;
        this.versionLoaders = versionLoaders;
        this.versionIds = versionIds;
        this.versionUrls = versionUrls;
        this.versionHashes = hashes;
        this.dependencyIds = dependencyIds;

        // Add the mc version to the version model
        for (int i=0; i<versionNames.length; i++){
            if (!versionNames[i].contains(mcVersionNames[i]))
                versionNames[i] += " - " + mcVersionNames[i];
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "ModDetail{" +
                "versionNames=" + Arrays.toString(versionNames) +
                ", mcVersionNames=" + Arrays.toString(mcVersionNames) +
                ", versionIds=" + Arrays.toString(versionUrls) +
                ", id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", apiSource=" + apiSource +
                ", isModpack=" + isModpack +
                '}';
    }
}
