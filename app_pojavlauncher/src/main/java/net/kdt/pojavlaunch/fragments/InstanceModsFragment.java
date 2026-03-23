package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.openPath;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.kdt.pojavlaunch.PojavApplication;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.contracts.OpenDocumentWithExtension;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.instances.InstanceArchiveUtils;
import net.kdt.pojavlaunch.instances.InstanceManager;
import net.kdt.pojavlaunch.modloaders.modpacks.ModCompatibility;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModrinthApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import org.apache.commons.codec.binary.Hex;
import org.apache.commons.io.FileUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class InstanceModsFragment extends Fragment {
    public static final String TAG = "InstanceModsFragment";
    private static final Pattern TOML_STRING_PATTERN = Pattern.compile("%s\\s*=\\s*\"([^\"]+)\"");

    private final ActivityResultLauncher<Object> mImportContentLauncher =
            registerForActivityResult(new OpenDocumentWithExtension(new String[]{"jar", "zip"}), this::importSelectedContent);
    private final ActivityResultLauncher<Object> mImportInstanceLauncher =
            registerForActivityResult(new OpenDocumentWithExtension("zip"), this::importInstanceArchive);
    private final ActivityResultLauncher<String> mExportInstanceLauncher =
            registerForActivityResult(new ActivityResultContracts.CreateDocument(), this::exportSelectedInstance);

    private final List<File> mEntries = new ArrayList<>();
    private final Map<String, EntryDisplayInfo> mDisplayInfoCache = new HashMap<>();
    private final Map<String, ModrinthApi.ModUpdate> mUpdateCache = new HashMap<>();
    private final ModrinthApi mModrinthApi = new ModrinthApi();

    private ContentSection mCurrentSection = ContentSection.MODS;
    private MinecraftProfile mCurrentProfile;
    private ModListAdapter mAdapter;
    private ListView mListView;
    private TextView mTitleTextView;
    private TextView mSubtitleTextView;
    private TextView mEmptyTextView;
    private Button mImportButton;
    private Button mBrowseButton;
    private Button mOpenFolderButton;
    private Button mCheckUpdatesButton;
    private Button mToolsButton;
    private Button mShowModsButton;
    private Button mShowResourcePacksButton;
    private Button mShowShaderPacksButton;
    private Button mShowWorldsButton;

    private boolean mCheckingUpdates;
    private boolean mHasCheckedUpdates;
    private boolean mUpdatingMods;

    public InstanceModsFragment() {
        super(R.layout.fragment_instance_mods);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        LauncherProfiles.load();
        mCurrentProfile = LauncherProfiles.getCurrentProfile();

        mTitleTextView = view.findViewById(R.id.instance_mods_title);
        mSubtitleTextView = view.findViewById(R.id.instance_mods_subtitle);
        mEmptyTextView = view.findViewById(R.id.instance_mods_empty);
        mListView = view.findViewById(R.id.instance_mods_list);

        mAdapter = new ModListAdapter();
        mListView.setAdapter(mAdapter);
        mListView.setLayoutAnimation(AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_item_stagger_smooth));

        mImportButton = view.findViewById(R.id.instance_mods_import_button);
        mBrowseButton = view.findViewById(R.id.instance_mods_browse_button);
        mOpenFolderButton = view.findViewById(R.id.instance_mods_open_folder_button);
        mCheckUpdatesButton = view.findViewById(R.id.instance_mods_check_updates_button);
        mToolsButton = view.findViewById(R.id.instance_mods_tools_button);
        mShowModsButton = view.findViewById(R.id.instance_mods_show_mods_button);
        mShowResourcePacksButton = view.findViewById(R.id.instance_mods_show_resourcepacks_button);
        mShowShaderPacksButton = view.findViewById(R.id.instance_mods_show_shaderpacks_button);
        mShowWorldsButton = view.findViewById(R.id.instance_mods_show_worlds_button);

        mImportButton.setOnClickListener(v -> mImportContentLauncher.launch(null));
        mBrowseButton.setOnClickListener(v -> Tools.swapFragment(
                requireActivity(),
                SearchModFragment.class,
                SearchModFragment.TAG,
                SearchModFragment.buildArgs(getCurrentProjectType(), getCurrentInstallSubdirectory())
        ));
        mOpenFolderButton.setOnClickListener(v -> openPath(requireContext(), getCurrentDirectory(), false));
        mCheckUpdatesButton.setOnClickListener(v -> {
            if (mHasCheckedUpdates && getAvailableUpdateCount() > 0) {
                applyAllUpdates();
            } else {
                runUpdateCheck();
            }
        });
        mToolsButton.setOnClickListener(v -> showToolsDialog());
        mShowModsButton.setOnClickListener(v -> switchSection(ContentSection.MODS));
        mShowResourcePacksButton.setOnClickListener(v -> switchSection(ContentSection.RESOURCEPACKS));
        mShowShaderPacksButton.setOnClickListener(v -> switchSection(ContentSection.SHADERPACKS));
        mShowWorldsButton.setOnClickListener(v -> switchSection(ContentSection.WORLDS));

        updateHeader();
        updateControls();
        reloadEntries();
    }

    @Override
    public void onResume() {
        super.onResume();
        LauncherProfiles.load();
        mCurrentProfile = LauncherProfiles.getCurrentProfile();
        updateHeader();
        updateControls();
        reloadEntries();
    }

    private void updateHeader() {
        String instanceName = Tools.isValidString(mCurrentProfile.name) ? mCurrentProfile.name : getString(R.string.unnamed);
        mTitleTextView.setText(getString(R.string.instance_manage_title_format, instanceName));
        mSubtitleTextView.setText(getString(R.string.instance_manage_subtitle_format, getSectionTitle(), getCurrentDirectory().getAbsolutePath()));
    }

    private void reloadEntries() {
        File sectionDirectory = getCurrentDirectory();
        if (!sectionDirectory.exists()) sectionDirectory.mkdirs();

        File[] files = sectionDirectory.listFiles(file -> isManagedEntry(file));
        mEntries.clear();
        mDisplayInfoCache.clear();
        if (files != null) {
            Arrays.sort(files, Comparator
                    .comparing(File::isFile)
                    .thenComparing((File file) -> file.getName().endsWith(".disabled"))
                    .thenComparing(file -> file.getName().toLowerCase()));
            mEntries.addAll(Arrays.asList(files));
        }
        mEmptyTextView.setVisibility(mEntries.isEmpty() ? View.VISIBLE : View.GONE);
        mEmptyTextView.setText(getString(R.string.instance_mods_empty_format, getSectionTitle().toLowerCase()));
        mAdapter.notifyDataSetChanged();
        if (mListView != null) mListView.scheduleLayoutAnimation();
    }

    private boolean isManagedEntry(File dir, String name) {
        return isManagedEntry(new File(dir, name));
    }

    private boolean isManagedEntry(File file) {
        if (file == null) return false;
        if (mCurrentSection == ContentSection.WORLDS) return file.isDirectory();
        if (!file.isFile()) return false;
        String name = file.getName().toLowerCase();
        if (mCurrentSection == ContentSection.MODS) {
            return name.endsWith(".jar") || name.endsWith(".zip") || name.endsWith(".jar.disabled") || name.endsWith(".zip.disabled");
        }
        return name.endsWith(".zip") || name.endsWith(".zip.disabled");
    }

    private void importSelectedContent(@Nullable Uri uri) {
        if (uri == null || mCurrentSection == ContentSection.WORLDS) return;
        try (InputStream inputStream = requireContext().getContentResolver().openInputStream(uri)) {
            if (inputStream == null) throw new IOException("Unable to open selected file");
            String fileName = queryDisplayName(requireContext(), uri);
            if (!Tools.isValidString(fileName)) {
                fileName = mCurrentSection == ContentSection.MODS ? "imported_mod.jar" : "imported_pack.zip";
            }
            File destination = new File(getCurrentDirectory(), fileName);
            FileUtils.copyInputStreamToFile(inputStream, destination);
            Toast.makeText(requireContext(), getImportToastResId(), Toast.LENGTH_LONG).show();
            reloadEntries();
        } catch (IOException e) {
            Tools.showErrorRemote(requireContext(), R.string.modpack_install_download_failed, e);
        }
    }

    private void exportSelectedInstance(@Nullable Uri uri) {
        if (uri == null) return;
        Context appContext = requireContext().getApplicationContext();
        MinecraftProfile profile = mCurrentProfile;
        PojavApplication.sExecutorService.execute(() -> {
            try {
                InstanceArchiveUtils.exportInstance(appContext, profile, uri);
                Tools.runOnUiThread(() -> Toast.makeText(appContext, R.string.instance_export_success, Toast.LENGTH_LONG).show());
            } catch (IOException e) {
                Tools.showErrorRemote(appContext, R.string.instance_export_failed, e);
            }
        });
    }

    private void importInstanceArchive(@Nullable Uri uri) {
        if (uri == null) return;
        Context appContext = requireContext().getApplicationContext();
        PojavApplication.sExecutorService.execute(() -> {
            try {
                InstanceArchiveUtils.ImportedInstance importedInstance = InstanceArchiveUtils.importInstance(appContext, uri);
                ExtraCore.setValue(ExtraConstants.REFRESH_VERSION_SPINNER, importedInstance.profileKey);
                LauncherPreferences.DEFAULT_PREF.edit()
                        .putString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, importedInstance.profileKey)
                        .apply();
                Tools.runOnUiThread(() -> Toast.makeText(appContext, R.string.instance_import_success, Toast.LENGTH_LONG).show());
            } catch (IOException e) {
                Tools.showErrorRemote(appContext, R.string.instance_import_failed, e);
            }
        });
    }

    private String buildExportFileName() {
        String profileName = Tools.isValidString(mCurrentProfile.name) ? mCurrentProfile.name : "oxide-instance";
        return InstanceManager.sanitizeDirectoryName(profileName) + ".zip";
    }

    private String queryDisplayName(Context context, Uri uri) {
        String[] projection = new String[]{OpenableColumns.DISPLAY_NAME};
        try (android.database.Cursor cursor = context.getContentResolver().query(uri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (columnIndex >= 0) return cursor.getString(columnIndex);
            }
        }
        return null;
    }

    private void toggleEntry(File entryFile) {
        if (mCurrentSection == ContentSection.WORLDS) {
            openPath(requireContext(), entryFile, false);
            return;
        }
        String sourceName = entryFile.getName();
        String targetName = sourceName.endsWith(".disabled")
                ? sourceName.substring(0, sourceName.length() - ".disabled".length())
                : sourceName + ".disabled";
        File targetFile = new File(entryFile.getParentFile(), targetName);
        if (!entryFile.renameTo(targetFile)) {
            Toast.makeText(requireContext(), R.string.instance_mod_toggle_failed, Toast.LENGTH_LONG).show();
            return;
        }
        reloadEntries();
    }

    private void deleteEntry(File entryFile) {
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.global_delete)
                .setMessage(getString(R.string.instance_mod_delete_confirm, entryFile.getName()))
                .setPositiveButton(R.string.global_delete, (dialog, which) -> {
                    try {
                        if (entryFile.isDirectory()) FileUtils.deleteDirectory(entryFile);
                        else if (!entryFile.delete()) throw new IOException("Failed to delete " + entryFile);
                        reloadEntries();
                    } catch (IOException e) {
                        Tools.showErrorRemote(requireContext(), R.string.instance_mod_delete_failed, e);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void runUpdateCheck() {
        if (mCurrentSection != ContentSection.MODS || mCheckingUpdates) return;
        mCheckingUpdates = true;
        updateControls();

        List<File> filesToCheck = new ArrayList<>(mEntries);
        String targetMcVersion = ModCompatibility.extractMinecraftVersion(mCurrentProfile.lastVersionId);
        String targetLoader = ModCompatibility.extractModLoader(mCurrentProfile.lastVersionId);
        Context appContext = requireContext().getApplicationContext();

        PojavApplication.sExecutorService.execute(() -> {
            Map<String, ModrinthApi.ModUpdate> nextUpdates = new HashMap<>();
            List<String> availableUpdates = new ArrayList<>();
            int checkedMods = 0;

            for (File file : filesToCheck) {
                try {
                    File sourceFile = getSourceFile(file);
                    if (sourceFile == null || !sourceFile.isFile()) continue;
                    String sha1 = computeSha1(sourceFile);
                    ModrinthApi.ModUpdate update = mModrinthApi.findCompatibleUpdate(sha1, targetMcVersion, targetLoader);
                    if (update == null) continue;
                    checkedMods++;
                    nextUpdates.put(buildCacheKey(file), update);
                    if (update.hasUpdate) {
                        EntryDisplayInfo info = resolveDisplayInfo(file);
                        availableUpdates.add(info.displayName + " -> " + update.latestVersionName);
                    }
                } catch (Exception ignored) {
                }
            }

            mHasCheckedUpdates = true;
            mUpdateCache.clear();
            mUpdateCache.putAll(nextUpdates);
            mCheckingUpdates = false;

            final int checkedModsFinal = checkedMods;
            final List<String> availableUpdatesFinal = new ArrayList<>(availableUpdates);
            Tools.runOnUiThread(() -> {
                updateControls();
                mAdapter.notifyDataSetChanged();
                showUpdateSummaryDialog(checkedModsFinal, availableUpdatesFinal);
                Toast.makeText(appContext, R.string.instance_updates_finished, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void applyAllUpdates() {
        applyUpdates(new ArrayList<>(mEntries));
    }

    private void applyUpdates(List<File> filesToUpdate) {
        if (mUpdatingMods || mCheckingUpdates || mCurrentSection != ContentSection.MODS) return;
        mUpdatingMods = true;
        updateControls();

        String targetMcVersion = ModCompatibility.extractMinecraftVersion(mCurrentProfile.lastVersionId);
        String targetLoader = ModCompatibility.extractModLoader(mCurrentProfile.lastVersionId);
        Context appContext = requireContext().getApplicationContext();

        PojavApplication.sExecutorService.execute(() -> {
            int updatedCount = 0;
            List<String> failedUpdates = new ArrayList<>();

            for (File entryFile : filesToUpdate) {
                try {
                    ModrinthApi.ModUpdate update = mUpdateCache.get(buildCacheKey(entryFile));
                    if (update == null || !update.hasUpdate) continue;

                    EntryDisplayInfo displayInfo = resolveDisplayInfo(entryFile);
                    ModItem modItem = new ModItem(Constants.SOURCE_MODRINTH, false, "mod", "mods",
                            update.projectId, displayInfo.displayName, "", "");
                    ModDetail modDetail = mModrinthApi.getModDetails(modItem);
                    if (modDetail == null) throw new IOException("Missing details for " + displayInfo.displayName);

                    int versionIndex = ModCompatibility.findCompatibleVersionIndex(
                            modDetail.mcVersionNames,
                            modDetail.versionLoaders,
                            modDetail.versionNames,
                            targetMcVersion,
                            targetLoader
                    );
                    if (versionIndex < 0) throw new IOException("No compatible update for " + displayInfo.displayName);

                    boolean wasDisabled = entryFile.getName().endsWith(".disabled");
                    File oldFile = entryFile;
                    File oldSource = getSourceFile(entryFile);
                    String expectedFileName = FilenameUtils.getName(modDetail.versionUrls[versionIndex]);
                    File destinationDirectory = InstanceManager.getModsDirectory(mCurrentProfile);
                    File downloadedFile = new File(destinationDirectory, expectedFileName);

                    mModrinthApi.installMod(modDetail, versionIndex);
                    finalizeUpdatedFile(oldFile, oldSource, downloadedFile, wasDisabled);
                    updatedCount++;
                } catch (Exception e) {
                    failedUpdates.add(entryFile.getName());
                }
            }

            mUpdatingMods = false;
            mHasCheckedUpdates = false;
            mUpdateCache.clear();
            final int updatedCountFinal = updatedCount;
            Tools.runOnUiThread(() -> {
                updateControls();
                reloadEntries();
                if (!failedUpdates.isEmpty()) {
                    Toast.makeText(appContext, R.string.instance_updates_failed, Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(appContext, getString(R.string.instance_updates_updated, updatedCountFinal), Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private void finalizeUpdatedFile(File oldFile, File oldSource, File downloadedFile, boolean wasDisabled) throws IOException {
        if (!wasDisabled) {
            if (oldSource != null && downloadedFile.exists() && !sameFile(downloadedFile, oldSource) && oldSource.exists()) {
                oldSource.delete();
            }
            return;
        }

        if (oldFile.exists()) oldFile.delete();
        if (oldSource != null && oldSource.exists() && !sameFile(oldSource, oldFile)) oldSource.delete();
        if (!downloadedFile.exists()) return;

        File disabledTarget = new File(downloadedFile.getParentFile(), downloadedFile.getName() + ".disabled");
        if (disabledTarget.exists()) disabledTarget.delete();
        if (!downloadedFile.renameTo(disabledTarget)) {
            throw new IOException("Failed to disable updated mod " + downloadedFile.getName());
        }
    }

    private boolean sameFile(File first, File second) throws IOException {
        return first != null && second != null && first.getCanonicalPath().equals(second.getCanonicalPath());
    }

    private void showToolsDialog() {
        List<String> labels = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        labels.add(getString(R.string.instance_export));
        actions.add(() -> mExportInstanceLauncher.launch(buildExportFileName()));
        labels.add(getString(R.string.instance_import));
        actions.add(() -> mImportInstanceLauncher.launch(null));

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.instance_options)
                .setItems(labels.toArray(new String[0]), (dialog, which) -> actions.get(which).run())
                .show();
    }

    private void showUpdateSummaryDialog(int checkedMods, List<String> availableUpdates) {
        String message;
        if (availableUpdates.isEmpty()) {
            message = getString(R.string.instance_updates_none, checkedMods);
        } else {
            StringBuilder builder = new StringBuilder();
            builder.append(getString(R.string.instance_updates_found_count, availableUpdates.size(), checkedMods));
            builder.append("\n\n");
            for (String update : availableUpdates) {
                builder.append("• ").append(update).append('\n');
            }
            message = builder.toString().trim();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.instance_updates_title)
                .setMessage(message);
        if (!availableUpdates.isEmpty()) {
            builder.setPositiveButton(R.string.instance_updates_install_all, (dialog, which) -> applyAllUpdates());
            builder.setNegativeButton(android.R.string.cancel, null);
        } else {
            builder.setPositiveButton(android.R.string.ok, null);
        }
        builder.show();
    }

    private String computeSha1(File sourceFile) throws IOException {
        try (InputStream inputStream = FileUtils.openInputStream(sourceFile)) {
            return new String(Hex.encodeHex(DigestUtils.sha1(inputStream)));
        }
    }

    private File getSourceFile(File entryFile) {
        if (entryFile == null) return null;
        if (!entryFile.getName().endsWith(".disabled")) return entryFile;
        File sourceFile = new File(entryFile.getParentFile(), entryFile.getName().replace(".disabled", ""));
        return sourceFile.exists() ? sourceFile : entryFile;
    }

    private class ModListAdapter extends BaseAdapter {
        private int mLastAnimatedPosition = -1;

        @Override
        public int getCount() {
            return mEntries.size();
        }

        @Override
        public Object getItem(int position) {
            return mEntries.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_instance_mod, parent, false);
            }

            File entryFile = mEntries.get(position);
            boolean enabled = !entryFile.getName().endsWith(".disabled");
            EntryDisplayInfo displayInfo = resolveDisplayInfo(entryFile);

            TextView nameTextView = view.findViewById(R.id.instance_mod_name);
            TextView stateTextView = view.findViewById(R.id.instance_mod_state);
            android.widget.ImageView iconView = view.findViewById(R.id.instance_mod_icon);
            Button toggleButton = view.findViewById(R.id.instance_mod_toggle_button);
            Button deleteButton = view.findViewById(R.id.instance_mod_delete_button);

            nameTextView.setText(displayInfo.displayName);
            stateTextView.setText(buildStateText(entryFile, enabled, displayInfo));
            if (displayInfo.iconBitmap != null) iconView.setImageBitmap(displayInfo.iconBitmap);
            else iconView.setImageResource(displayInfo.iconResId);

            ModrinthApi.ModUpdate update = mUpdateCache.get(buildCacheKey(entryFile));
            boolean hasUpdate = mCurrentSection == ContentSection.MODS && update != null && update.hasUpdate;
            toggleButton.setText(mCurrentSection == ContentSection.WORLDS
                    ? R.string.instance_world_open
                    : (hasUpdate ? R.string.instance_updates_install_all : (enabled ? R.string.instance_mod_disable : R.string.instance_mod_enable)));
            toggleButton.setOnClickListener(v -> {
                if (hasUpdate) {
                    applySingleUpdate(entryFile);
                } else {
                    toggleEntry(entryFile);
                }
            });
            deleteButton.setOnClickListener(v -> deleteEntry(entryFile));

            if (position > mLastAnimatedPosition) {
                view.setAlpha(0f);
                view.setTranslationY(20f);
                view.animate().alpha(1f).translationY(0f).setDuration(180).start();
                mLastAnimatedPosition = position;
            }
            return view;
        }
    }

    private String buildStateText(File entryFile, boolean enabled, EntryDisplayInfo displayInfo) {
        if (mCurrentSection == ContentSection.WORLDS) {
            return displayInfo.detailLine;
        }

        StringBuilder builder = new StringBuilder();
        builder.append(enabled ? getString(R.string.instance_mod_enabled) : getString(R.string.instance_mod_disabled));
        builder.append(" - ").append(entryFile.getName().replace(".disabled", ""));

        if (mCurrentSection == ContentSection.MODS && mHasCheckedUpdates) {
            ModrinthApi.ModUpdate update = mUpdateCache.get(buildCacheKey(entryFile));
            if (update != null && update.hasUpdate) {
                builder.append(" - ").append(getString(R.string.instance_updates_available_short, update.latestVersionName));
            }
        }
        return builder.toString();
    }

    private void applySingleUpdate(File entryFile) {
        List<File> singleUpdate = new ArrayList<>();
        singleUpdate.add(entryFile);
        applyUpdates(singleUpdate);
    }

    private EntryDisplayInfo resolveDisplayInfo(File entryFile) {
        String cacheKey = buildCacheKey(entryFile);
        EntryDisplayInfo cached = mDisplayInfoCache.get(cacheKey);
        if (cached != null) return cached;

        EntryDisplayInfo info = new EntryDisplayInfo();
        info.displayName = entryFile.getName().replace(".disabled", "");
        info.iconResId = mCurrentSection == ContentSection.WORLDS ? R.drawable.ic_instance_worlds : R.drawable.ic_file;
        info.detailLine = mCurrentSection == ContentSection.WORLDS
                ? getString(R.string.instance_world_state)
                : entryFile.getName().replace(".disabled", "");

        if (mCurrentSection == ContentSection.WORLDS) {
            info.displayName = entryFile.getName();
            mDisplayInfoCache.put(cacheKey, info);
            return info;
        }

        File sourceFile = getSourceFile(entryFile);
        if (sourceFile != null && sourceFile.exists()) {
            try (ZipFile zipFile = new ZipFile(sourceFile)) {
                applyFabricMetadata(zipFile, info);
                if (!Tools.isValidString(info.displayName) || info.iconBitmap == null) applyQuiltMetadata(zipFile, info);
                if (!Tools.isValidString(info.displayName) || info.iconBitmap == null) applyForgeMetadata(zipFile, info);
                if (!Tools.isValidString(info.displayName)) applyLegacyMetadata(zipFile, info);
            } catch (IOException ignored) {
            }
        }

        if (!Tools.isValidString(info.displayName)) {
            info.displayName = entryFile.getName().replace(".disabled", "");
        }
        mDisplayInfoCache.put(cacheKey, info);
        return info;
    }

    private void applyFabricMetadata(ZipFile zipFile, EntryDisplayInfo info) throws IOException {
        ZipEntry zipEntry = zipFile.getEntry("fabric.mod.json");
        if (zipEntry == null) return;
        JsonObject object = Tools.GLOBAL_GSON.fromJson(Tools.read(zipFile.getInputStream(zipEntry)), JsonObject.class);
        if (object == null) return;
        if (!Tools.isValidString(info.displayName)) {
            info.displayName = getJsonString(object, "name");
            if (!Tools.isValidString(info.displayName)) info.displayName = getJsonString(object, "id");
        }
        if (info.iconBitmap == null) info.iconBitmap = loadIcon(zipFile, pickJsonIconPath(object.get("icon")));
    }

    private void applyQuiltMetadata(ZipFile zipFile, EntryDisplayInfo info) throws IOException {
        ZipEntry zipEntry = zipFile.getEntry("quilt.mod.json");
        if (zipEntry == null) return;
        JsonObject object = Tools.GLOBAL_GSON.fromJson(Tools.read(zipFile.getInputStream(zipEntry)), JsonObject.class);
        if (object == null || !object.has("quilt_loader")) return;
        JsonObject quiltLoader = object.getAsJsonObject("quilt_loader");
        JsonObject metadata = quiltLoader == null ? null : quiltLoader.getAsJsonObject("metadata");
        if (metadata == null) return;
        if (!Tools.isValidString(info.displayName)) {
            info.displayName = getJsonString(metadata, "name");
            if (!Tools.isValidString(info.displayName)) info.displayName = getJsonString(quiltLoader, "id");
        }
        if (info.iconBitmap == null) info.iconBitmap = loadIcon(zipFile, pickJsonIconPath(metadata.get("icon")));
    }

    private void applyForgeMetadata(ZipFile zipFile, EntryDisplayInfo info) throws IOException {
        String modsToml = readEntry(zipFile, "META-INF/mods.toml");
        if (modsToml == null) modsToml = readEntry(zipFile, "META-INF/neoforge.mods.toml");
        if (modsToml == null) return;
        if (!Tools.isValidString(info.displayName)) {
            info.displayName = findTomlString(modsToml, "displayName");
            if (!Tools.isValidString(info.displayName)) info.displayName = findTomlString(modsToml, "modId");
        }
        if (info.iconBitmap == null) info.iconBitmap = loadIcon(zipFile, findTomlString(modsToml, "logoFile"));
    }

    private void applyLegacyMetadata(ZipFile zipFile, EntryDisplayInfo info) throws IOException {
        ZipEntry zipEntry = zipFile.getEntry("mcmod.info");
        if (zipEntry == null) return;
        JsonElement element = Tools.GLOBAL_GSON.fromJson(Tools.read(zipFile.getInputStream(zipEntry)), JsonElement.class);
        if (element == null) return;
        JsonObject object = null;
        if (element.isJsonArray()) {
            JsonArray array = element.getAsJsonArray();
            if (array.size() > 0 && array.get(0).isJsonObject()) object = array.get(0).getAsJsonObject();
        } else if (element.isJsonObject()) {
            object = element.getAsJsonObject();
        }
        if (object == null) return;
        if (!Tools.isValidString(info.displayName)) {
            info.displayName = getJsonString(object, "name");
            if (!Tools.isValidString(info.displayName)) info.displayName = getJsonString(object, "modid");
        }
        if (info.iconBitmap == null) info.iconBitmap = loadIcon(zipFile, getJsonString(object, "logoFile"));
    }

    private String readEntry(ZipFile zipFile, String entryName) throws IOException {
        ZipEntry zipEntry = zipFile.getEntry(entryName);
        if (zipEntry == null) return null;
        return Tools.read(zipFile.getInputStream(zipEntry));
    }

    private String findTomlString(String contents, String key) {
        Matcher matcher = Pattern.compile(String.format(TOML_STRING_PATTERN.pattern(), Pattern.quote(key))).matcher(contents);
        return matcher.find() ? matcher.group(1) : null;
    }

    private String getJsonString(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) return null;
        return object.get(key).getAsString();
    }

    private String pickJsonIconPath(JsonElement iconElement) {
        if (iconElement == null || iconElement.isJsonNull()) return null;
        if (iconElement.isJsonPrimitive()) return iconElement.getAsString();
        if (!iconElement.isJsonObject()) return null;
        JsonObject iconObject = iconElement.getAsJsonObject();
        if (iconObject.has("512")) return getJsonString(iconObject, "512");
        if (iconObject.has("256")) return getJsonString(iconObject, "256");
        if (iconObject.has("128")) return getJsonString(iconObject, "128");
        if (iconObject.has("64")) return getJsonString(iconObject, "64");
        for (Map.Entry<String, JsonElement> entry : iconObject.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isJsonNull()) return entry.getValue().getAsString();
        }
        return null;
    }

    private Bitmap loadIcon(ZipFile zipFile, String iconPath) throws IOException {
        if (!Tools.isValidString(iconPath)) return null;
        String normalized = iconPath.startsWith("/") ? iconPath.substring(1) : iconPath;
        ZipEntry entry = zipFile.getEntry(normalized);
        if (entry == null) {
            String lowerPath = normalized.toLowerCase();
            java.util.Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry candidate = entries.nextElement();
                if (candidate.isDirectory()) continue;
                String candidateName = candidate.getName();
                if (candidateName.equalsIgnoreCase(normalized) || candidateName.toLowerCase().endsWith("/" + lowerPath)) {
                    entry = candidate;
                    break;
                }
            }
        }
        if (entry == null) return null;
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            return BitmapFactory.decodeStream(inputStream);
        }
    }

    private void switchSection(ContentSection contentSection) {
        if (mCurrentSection == contentSection) return;
        mCurrentSection = contentSection;
        updateHeader();
        updateControls();
        reloadEntries();
    }

    private void updateControls() {
        if (mImportButton != null) {
            mImportButton.setText(getImportLabelResId());
            mImportButton.setVisibility(mCurrentSection == ContentSection.WORLDS ? View.GONE : View.VISIBLE);
        }
        if (mBrowseButton != null) {
            mBrowseButton.setText(getBrowseLabelResId());
            mBrowseButton.setVisibility(mCurrentSection == ContentSection.WORLDS ? View.GONE : View.VISIBLE);
        }
        if (mOpenFolderButton != null) {
            mOpenFolderButton.setText(mCurrentSection == ContentSection.WORLDS
                    ? R.string.instance_worlds_folder
                    : R.string.instance_mods_folder);
        }
        if (mCheckUpdatesButton != null) {
            mCheckUpdatesButton.setVisibility(mCurrentSection == ContentSection.MODS ? View.VISIBLE : View.GONE);
            mCheckUpdatesButton.setEnabled(!mCheckingUpdates && !mUpdatingMods);
            if (mCheckingUpdates) {
                mCheckUpdatesButton.setText(R.string.instance_updates_checking);
            } else {
                int updates = getAvailableUpdateCount();
                if (mHasCheckedUpdates && updates > 0) {
                    mCheckUpdatesButton.setText(getString(R.string.instance_updates_install_all) + " (" + updates + ")");
                } else {
                    mCheckUpdatesButton.setText(R.string.instance_updates_check);
                }
            }
        }
        if (mToolsButton != null) {
            mToolsButton.setEnabled(!mCheckingUpdates && !mUpdatingMods);
            mToolsButton.setText(R.string.instance_options);
        }
        if (mShowModsButton != null) mShowModsButton.setSelected(mCurrentSection == ContentSection.MODS);
        if (mShowResourcePacksButton != null) mShowResourcePacksButton.setSelected(mCurrentSection == ContentSection.RESOURCEPACKS);
        if (mShowShaderPacksButton != null) mShowShaderPacksButton.setSelected(mCurrentSection == ContentSection.SHADERPACKS);
        if (mShowWorldsButton != null) mShowWorldsButton.setSelected(mCurrentSection == ContentSection.WORLDS);
    }

    private File getCurrentDirectory() {
        if (mCurrentSection == ContentSection.RESOURCEPACKS) return InstanceManager.getResourcePacksDirectory(mCurrentProfile);
        if (mCurrentSection == ContentSection.SHADERPACKS) return InstanceManager.getShaderPacksDirectory(mCurrentProfile);
        if (mCurrentSection == ContentSection.WORLDS) return new File(InstanceManager.getInstanceRoot(mCurrentProfile), "saves");
        return InstanceManager.getModsDirectory(mCurrentProfile);
    }

    private String getSectionTitle() {
        if (mCurrentSection == ContentSection.RESOURCEPACKS) return getString(R.string.instance_mods_resourcepacks);
        if (mCurrentSection == ContentSection.SHADERPACKS) return getString(R.string.instance_mods_shaderpacks);
        if (mCurrentSection == ContentSection.WORLDS) return getString(R.string.instance_mods_worlds);
        return getString(R.string.instance_mods_mods);
    }

    private int getImportToastResId() {
        if (mCurrentSection == ContentSection.RESOURCEPACKS) return R.string.instance_resourcepack_imported;
        if (mCurrentSection == ContentSection.SHADERPACKS) return R.string.instance_shaderpack_imported;
        return R.string.instance_mod_imported;
    }

    private int getImportLabelResId() {
        if (mCurrentSection == ContentSection.RESOURCEPACKS) return R.string.instance_resourcepacks_import;
        if (mCurrentSection == ContentSection.SHADERPACKS) return R.string.instance_shaderpacks_import;
        return R.string.instance_mods_import;
    }

    private int getBrowseLabelResId() {
        if (mCurrentSection == ContentSection.RESOURCEPACKS) return R.string.instance_resourcepacks_browse;
        if (mCurrentSection == ContentSection.SHADERPACKS) return R.string.instance_shaderpacks_browse;
        return R.string.instance_mods_browse_mods;
    }

    private String getCurrentProjectType() {
        if (mCurrentSection == ContentSection.RESOURCEPACKS) return "resourcepack";
        if (mCurrentSection == ContentSection.SHADERPACKS) return "shader";
        return "mod";
    }

    private String getCurrentInstallSubdirectory() {
        if (mCurrentSection == ContentSection.RESOURCEPACKS) return "resourcepacks";
        if (mCurrentSection == ContentSection.SHADERPACKS) return "shaderpacks";
        return "mods";
    }

    private String buildCacheKey(File file) {
        return file.getAbsolutePath() + ":" + file.lastModified() + ":" + file.length();
    }

    private int getAvailableUpdateCount() {
        int updates = 0;
        for (ModrinthApi.ModUpdate update : mUpdateCache.values()) {
            if (update != null && update.hasUpdate) updates++;
        }
        return updates;
    }

    private static class EntryDisplayInfo {
        String displayName;
        String detailLine;
        Bitmap iconBitmap;
        int iconResId = R.drawable.ic_file;
    }

    private enum ContentSection {
        MODS,
        RESOURCEPACKS,
        SHADERPACKS,
        WORLDS
    }
}
