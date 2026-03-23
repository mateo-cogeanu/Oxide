package net.kdt.pojavlaunch.modloaders.modpacks;

import net.kdt.pojavlaunch.Tools;

import java.util.Locale;

public final class ModCompatibility {
    private ModCompatibility() {}

    public static String extractMinecraftVersion(String versionId) {
        if (!Tools.isValidString(versionId)) return null;
        String lower = versionId.toLowerCase(Locale.ROOT);
        if (lower.startsWith("neoforge-")) {
            String[] parts = lower.substring("neoforge-".length()).split("[^0-9]+");
            if (parts.length >= 2 && Tools.isValidString(parts[0]) && Tools.isValidString(parts[1])) {
                return "1." + parts[0] + "." + parts[1];
            }
        }
        if (lower.contains("-forge-")) {
            return lower.substring(0, lower.indexOf("-forge-"));
        }
        if (lower.startsWith("fabric-loader-") || lower.startsWith("quilt-loader-")) {
            int lastDash = lower.lastIndexOf('-');
            if (lastDash != -1 && lastDash + 1 < lower.length()) {
                return lower.substring(lastDash + 1);
            }
        }
        if (lower.matches("\\d+\\.\\d+(\\.\\d+)?")) {
            return lower;
        }
        return null;
    }

    public static String extractModLoader(String versionId) {
        if (!Tools.isValidString(versionId)) return null;
        String lower = versionId.toLowerCase(Locale.ROOT);
        if (lower.contains("fabric")) return "fabric";
        if (lower.contains("quilt")) return "quilt";
        if (lower.contains("neoforge")) return "neoforge";
        if (lower.contains("forge")) return "forge";
        if (lower.contains("optifine")) return "optifine";
        return null;
    }

    public static boolean matchesRequestedLoader(String candidate, String requestedLoader) {
        if (!Tools.isValidString(requestedLoader)) return true;
        if (!Tools.isValidString(candidate)) return false;
        String lowerCandidate = candidate.toLowerCase(Locale.ROOT);
        String lowerRequested = requestedLoader.toLowerCase(Locale.ROOT);
        if ("forge".equals(lowerRequested)) {
            return lowerCandidate.contains("forge") && !lowerCandidate.contains("neoforge");
        }
        return lowerCandidate.contains(lowerRequested);
    }

    public static boolean matchesRequestedLoader(String[] candidates, String requestedLoader) {
        if (!Tools.isValidString(requestedLoader)) return true;
        if (candidates == null || candidates.length == 0) return false;
        for (String candidate : candidates) {
            if (matchesRequestedLoader(candidate, requestedLoader)) return true;
        }
        return false;
    }

    public static int findCompatibleVersionIndex(String[] mcVersions, String[] versionLabels, String targetMcVersion, String targetLoader) {
        return findCompatibleVersionIndex(mcVersions, null, versionLabels, targetMcVersion, targetLoader);
    }

    public static int findCompatibleVersionIndex(String[] mcVersions, String[][] versionLoaders, String[] versionLabels,
                                                 String targetMcVersion, String targetLoader) {
        if (versionLabels == null) return -1;

        for (int i = 0; i < versionLabels.length; i++) {
            String mcVersion = mcVersions != null && i < mcVersions.length ? mcVersions[i] : null;
            if (Tools.isValidString(targetMcVersion) && !targetMcVersion.equalsIgnoreCase(mcVersion)) continue;
            if (!matchesLoader(versionLoaders, versionLabels, i, targetLoader)) continue;
            return i;
        }

        for (int i = 0; i < versionLabels.length; i++) {
            if (!matchesLoader(versionLoaders, versionLabels, i, targetLoader)) continue;
            return i;
        }

        return versionLabels.length == 0 ? -1 : 0;
    }

    private static boolean matchesLoader(String[][] versionLoaders, String[] versionLabels, int index, String targetLoader) {
        if (versionLoaders != null && index < versionLoaders.length && versionLoaders[index] != null && versionLoaders[index].length > 0) {
            return matchesRequestedLoader(versionLoaders[index], targetLoader);
        }
        return matchesRequestedLoader(versionLabels[index], targetLoader);
    }
}
