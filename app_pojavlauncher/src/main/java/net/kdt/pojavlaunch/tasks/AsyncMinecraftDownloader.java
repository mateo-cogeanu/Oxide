package net.kdt.pojavlaunch.tasks;

import com.google.gson.stream.JsonReader;

import net.kdt.pojavlaunch.JMinecraftVersionList;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.FileReader;

public class AsyncMinecraftDownloader {
    public static String normalizeVersionId(String versionString) {
        JMinecraftVersionList versionList = getVersionList();
        if(versionList == null || versionList.versions == null) return versionString;
        if(MinecraftProfile.LATEST_RELEASE.equals(versionString)) {
            versionString = versionList.latest.get("release");
        }
        if(MinecraftProfile.LATEST_SNAPSHOT.equals(versionString)) versionString = versionList.latest.get("snapshot");
        return versionString;
    }

    public static JMinecraftVersionList.Version getListedVersion(String normalizedVersionString) {
        JMinecraftVersionList versionList = getVersionList();
        if(versionList == null || versionList.versions == null) return null; // can't have listed versions if there's no list
        for(JMinecraftVersionList.Version version : versionList.versions) {
            if(version.id.equals(normalizedVersionString)) return version;
        }
        return null;
    }

    public interface DoneListener{
        void onDownloadDone();
        void onDownloadFailed(Throwable throwable);
    }

    private static JMinecraftVersionList getVersionList() {
        JMinecraftVersionList versionList = (JMinecraftVersionList) ExtraCore.getValue(ExtraConstants.RELEASE_TABLE);
        if (versionList != null && versionList.versions != null) return versionList;

        File versionFile = new File(Tools.DIR_CACHE, "version_list.json");
        if (!versionFile.exists()) return null;

        try (JsonReader jsonReader = new JsonReader(new FileReader(versionFile))) {
            versionList = Tools.GLOBAL_GSON.fromJson(jsonReader, JMinecraftVersionList.class);
            if (versionList != null) {
                ExtraCore.setValue(ExtraConstants.RELEASE_TABLE, versionList);
            }
            return versionList;
        } catch (Exception ignored) {
            return null;
        }
    }
}
