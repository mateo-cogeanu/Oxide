package net.kdt.pojavlaunch.instances;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.NonNull;

import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public final class InstanceArchiveUtils {
    private static final String PROFILE_ENTRY = "oxide-profile.json";
    private static final String MANIFEST_ENTRY = "oxide-export.json";

    private InstanceArchiveUtils() {}

    public static void exportInstance(@NonNull Context context, @NonNull MinecraftProfile profile, @NonNull Uri destinationUri) throws IOException {
        File instanceRoot = InstanceManager.getInstanceRoot(profile);
        if (!instanceRoot.exists()) throw new IOException("Instance folder does not exist: " + instanceRoot);

        ContentResolver resolver = context.getContentResolver();
        try (OutputStream outputStream = resolver.openOutputStream(destinationUri, "wt")) {
            if (outputStream == null) throw new IOException("Unable to open export destination");
            try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
                writeJsonEntry(zipOutputStream, PROFILE_ENTRY, Tools.GLOBAL_GSON.toJson(profile));
                writeJsonEntry(zipOutputStream, MANIFEST_ENTRY, buildManifestJson(profile));
                zipDirectory(zipOutputStream, instanceRoot, instanceRoot);
            }
        }
    }

    public static @NonNull ImportedInstance importInstance(@NonNull Context context, @NonNull Uri archiveUri) throws IOException {
        File tempArchive = File.createTempFile("oxide-instance-import", ".zip", context.getCacheDir());
        try {
            copyUriToFile(context, archiveUri, tempArchive);
            return importFromZip(tempArchive);
        } finally {
            tempArchive.delete();
        }
    }

    private static @NonNull ImportedInstance importFromZip(@NonNull File archiveFile) throws IOException {
        LauncherProfiles.load();

        MinecraftProfile importedProfile = null;
        try (ZipFile zipFile = new ZipFile(archiveFile)) {
            ZipEntry profileEntry = zipFile.getEntry(PROFILE_ENTRY);
            if (profileEntry != null) {
                importedProfile = Tools.GLOBAL_GSON.fromJson(Tools.read(zipFile.getInputStream(profileEntry)), MinecraftProfile.class);
            }

            if (importedProfile == null) {
                importedProfile = MinecraftProfile.createTemplate();
                importedProfile.name = archiveFile.getName().replace(".zip", "");
            }
            if (!Tools.isValidString(importedProfile.name)) importedProfile.name = "Imported Instance";

            String profileKey = LauncherProfiles.getFreeProfileKey();
            importedProfile.gameDir = "./instances/" + buildImportedDirectoryName(importedProfile);
            InstanceManager.ensureInstanceLayout(profileKey, importedProfile);
            extractInstanceContents(zipFile, InstanceManager.getInstanceRoot(importedProfile));

            LauncherProfiles.mainProfileJson.profiles.put(profileKey, importedProfile);
            LauncherProfiles.write();
            return new ImportedInstance(profileKey, importedProfile);
        }
    }

    private static String buildImportedDirectoryName(MinecraftProfile importedProfile) {
        Set<String> usedNames = new LinkedHashSet<>();
        for (Map.Entry<String, MinecraftProfile> entry : LauncherProfiles.mainProfileJson.profiles.entrySet()) {
            MinecraftProfile profile = entry.getValue();
            if (profile == null || !Tools.isValidString(profile.gameDir)) continue;
            String prefix = "./instances/";
            if (profile.gameDir.startsWith(prefix)) {
                usedNames.add(profile.gameDir.substring(prefix.length()));
            }
        }
        return InstanceManager.buildUniqueDirectoryName(LauncherProfiles.getFreeProfileKey(), importedProfile, usedNames);
    }

    private static void copyUriToFile(Context context, Uri sourceUri, File destination) throws IOException {
        try (InputStream inputStream = context.getContentResolver().openInputStream(sourceUri);
             OutputStream outputStream = new FileOutputStream(destination)) {
            if (inputStream == null) throw new IOException("Unable to open archive");
            IOUtils.copy(inputStream, outputStream);
        }
    }

    private static void extractInstanceContents(ZipFile zipFile, File destinationRoot) throws IOException {
        String destinationPath = destinationRoot.getCanonicalPath() + File.separator;
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            if (entry.isDirectory()) continue;
            String entryName = entry.getName();
            if (PROFILE_ENTRY.equals(entryName) || MANIFEST_ENTRY.equals(entryName)) continue;

            File destination = new File(destinationRoot, entryName);
            String candidatePath = destination.getCanonicalPath();
            if (!candidatePath.startsWith(destinationPath)) {
                throw new IOException("Blocked unsafe archive path: " + entryName);
            }

            FileUtils.ensureParentDirectory(destination);
            try (InputStream inputStream = zipFile.getInputStream(entry);
                 OutputStream outputStream = new FileOutputStream(destination)) {
                IOUtils.copy(inputStream, outputStream);
            }
        }
    }

    private static void zipDirectory(ZipOutputStream zipOutputStream, File rootDirectory, File currentDirectory) throws IOException {
        File[] children = currentDirectory.listFiles();
        if (children == null) return;
        for (File child : children) {
            String relativePath = rootDirectory.toURI().relativize(child.toURI()).getPath();
            if (child.isDirectory()) {
                zipDirectory(zipOutputStream, rootDirectory, child);
                continue;
            }
            zipOutputStream.putNextEntry(new ZipEntry(relativePath));
            try (InputStream inputStream = new FileInputStream(child)) {
                IOUtils.copy(inputStream, zipOutputStream);
            }
            zipOutputStream.closeEntry();
        }
    }

    private static void writeJsonEntry(ZipOutputStream zipOutputStream, String entryName, String contents) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(entryName));
        zipOutputStream.write(contents.getBytes());
        zipOutputStream.closeEntry();
    }

    private static String buildManifestJson(MinecraftProfile profile) {
        ExportManifest exportManifest = new ExportManifest();
        exportManifest.format = "oxide-instance";
        exportManifest.name = profile.name;
        exportManifest.version = profile.lastVersionId;
        exportManifest.gameDir = profile.gameDir;
        return Tools.GLOBAL_GSON.toJson(exportManifest);
    }

    public static final class ImportedInstance {
        public final String profileKey;
        public final MinecraftProfile profile;

        public ImportedInstance(String profileKey, MinecraftProfile profile) {
            this.profileKey = profileKey;
            this.profile = profile;
        }
    }

    private static final class ExportManifest {
        String format;
        String name;
        String version;
        String gameDir;
    }
}
