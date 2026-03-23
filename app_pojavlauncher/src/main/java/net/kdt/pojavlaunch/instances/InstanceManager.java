package net.kdt.pojavlaunch.instances;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftLauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class InstanceManager {
    private static final String INSTANCES_DIR = "instances";
    private static final String INSTANCE_METADATA_FILE = "instance.json";

    private InstanceManager() {}

    public static boolean migrateProfiles(@NonNull MinecraftLauncherProfiles launcherProfiles) {
        if (launcherProfiles.profiles == null) return false;

        boolean changed = false;
        Set<String> usedDirectoryNames = new LinkedHashSet<>();

        for (Map.Entry<String, MinecraftProfile> entry : launcherProfiles.profiles.entrySet()) {
            MinecraftProfile profile = entry.getValue();
            if (profile == null || !Tools.isValidString(profile.gameDir)) continue;
            String prefix = "./" + INSTANCES_DIR + "/";
            if (profile.gameDir.startsWith(prefix)) {
                usedDirectoryNames.add(profile.gameDir.substring(prefix.length()));
            }
        }

        for (Map.Entry<String, MinecraftProfile> entry : launcherProfiles.profiles.entrySet()) {
            String profileId = entry.getKey();
            MinecraftProfile profile = entry.getValue();
            if (profile == null) continue;

            if (!Tools.isValidString(profile.gameDir)) {
                String directoryName = buildUniqueDirectoryName(profileId, profile, usedDirectoryNames);
                profile.gameDir = "./" + INSTANCES_DIR + "/" + directoryName;
                changed = true;
            }
        }

        return changed;
    }

    public static File getInstancesRoot() {
        return new File(Tools.DIR_GAME_HOME, INSTANCES_DIR);
    }

    public static File getInstanceRoot(@NonNull MinecraftProfile profile) {
        return Tools.getGameDirPath(profile);
    }

    public static File getModsDirectory(@NonNull MinecraftProfile profile) {
        return new File(getInstanceRoot(profile), "mods");
    }

    public static File getResourcePacksDirectory(@NonNull MinecraftProfile profile) {
        return new File(getInstanceRoot(profile), "resourcepacks");
    }

    public static File getShaderPacksDirectory(@NonNull MinecraftProfile profile) {
        return new File(getInstanceRoot(profile), "shaderpacks");
    }

    public static boolean ensureInstanceLayout(@NonNull String profileId, @NonNull MinecraftProfile profile) throws IOException {
        File instanceRoot = getInstanceRoot(profile);
        boolean changed = false;
        changed |= FileUtils.ensureDirectorySilently(getInstancesRoot());
        changed |= FileUtils.ensureDirectorySilently(instanceRoot);
        changed |= FileUtils.ensureDirectorySilently(new File(instanceRoot, "mods"));
        changed |= FileUtils.ensureDirectorySilently(new File(instanceRoot, "resourcepacks"));
        changed |= FileUtils.ensureDirectorySilently(new File(instanceRoot, "shaderpacks"));
        changed |= FileUtils.ensureDirectorySilently(new File(instanceRoot, "saves"));

        InstanceMetadata metadata = new InstanceMetadata();
        metadata.id = profileId;
        metadata.name = Tools.isValidString(profile.name) ? profile.name : "Unnamed Instance";
        metadata.version = profile.lastVersionId;
        metadata.gameDir = profile.gameDir;

        File metadataFile = new File(instanceRoot, INSTANCE_METADATA_FILE);
        String nextJson = Tools.GLOBAL_GSON.toJson(metadata);
        if (!metadataFile.exists()) {
            Tools.write(metadataFile.getAbsolutePath(), nextJson);
            return true;
        }

        String currentJson = Tools.read(metadataFile.getAbsolutePath());
        if (!nextJson.equals(currentJson)) {
            Tools.write(metadataFile.getAbsolutePath(), nextJson);
            return true;
        }
        return changed;
    }

    public static String buildUniqueDirectoryName(@NonNull String profileId, @NonNull MinecraftProfile profile, @NonNull Set<String> usedDirectoryNames) {
        String preferredName = sanitizeDirectoryName(Tools.isValidString(profile.name) ? profile.name : profileId);
        String candidate = preferredName;
        int suffix = 2;
        while (usedDirectoryNames.contains(candidate)) {
            candidate = preferredName + "_" + suffix;
            suffix++;
        }
        usedDirectoryNames.add(candidate);
        return candidate;
    }

    public static String sanitizeDirectoryName(@NonNull String rawName) {
        String sanitized = rawName
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("[^a-z0-9]+", "_")
                .replaceAll("_{2,}", "_")
                .replaceAll("^_+|_+$", "");
        if (!Tools.isValidString(sanitized)) sanitized = "instance";
        return sanitized;
    }

    private static class InstanceMetadata {
        String id;
        String name;
        String version;
        String gameDir;
    }
}
