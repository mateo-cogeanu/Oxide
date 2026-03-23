package net.kdt.pojavlaunch.fragments;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.math.MathUtils;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.modloaders.modpacks.ModCompatibility;
import net.kdt.pojavlaunch.modloaders.modpacks.ModItemAdapter;
import net.kdt.pojavlaunch.modloaders.modpacks.api.CommonApi;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.profiles.VersionSelectorDialog;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

public class SearchModFragment extends Fragment implements ModItemAdapter.SearchResultCallback {

    public static final String TAG = "SearchModFragment";
    public static final String ARG_IS_MODPACK = "is_modpack";
    public static final String ARG_PROJECT_TYPE = "project_type";
    public static final String ARG_INSTALL_SUBDIRECTORY = "install_subdirectory";
    private View mOverlay;
    private float mOverlayTopCache; // Padding cache reduce resource lookup

    private final RecyclerView.OnScrollListener mOverlayPositionListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            mOverlay.setY(MathUtils.clamp(mOverlay.getY() - dy, -mOverlay.getHeight(), mOverlayTopCache));
        }
    };

    private EditText mSearchEditText;
    private ImageButton mFilterButton;
    private RecyclerView mRecyclerview;
    private ModItemAdapter mModItemAdapter;
    private ProgressBar mSearchProgressBar;
    private TextView mStatusTextView;
    private TextView mCompatibilityTextView;
    private Button mDownloadSelectedButton;
    private ColorStateList mDefaultTextColor;

    private ModpackApi modpackApi;

    private final SearchFilters mSearchFilters;
    private boolean mIsModpack = true;
    private String mProjectType = "mod";
    private String mInstallSubdirectory = "mods";

    public SearchModFragment(){
        super(R.layout.fragment_mod_search);
        mSearchFilters = new SearchFilters();
        mSearchFilters.isModpack = true;
    }

    public static Bundle buildArgs(boolean isModpack) {
        Bundle bundle = new Bundle(1);
        bundle.putBoolean(ARG_IS_MODPACK, isModpack);
        return bundle;
    }

    public static Bundle buildArgs(String projectType, String installSubdirectory) {
        Bundle bundle = new Bundle(3);
        bundle.putBoolean(ARG_IS_MODPACK, false);
        bundle.putString(ARG_PROJECT_TYPE, projectType);
        bundle.putString(ARG_INSTALL_SUBDIRECTORY, installSubdirectory);
        return bundle;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Bundle args = getArguments();
        mIsModpack = args == null || args.getBoolean(ARG_IS_MODPACK, true);
        mSearchFilters.isModpack = mIsModpack;
        if (!mIsModpack) {
            if (args != null) {
                mProjectType = args.getString(ARG_PROJECT_TYPE, "mod");
                mInstallSubdirectory = args.getString(ARG_INSTALL_SUBDIRECTORY, "mods");
            }
            mSearchFilters.projectType = mProjectType;
            mSearchFilters.installSubdirectory = mInstallSubdirectory;
        }
        if (!mIsModpack) {
            LauncherProfiles.load();
            MinecraftProfile currentProfile = LauncherProfiles.getCurrentProfile();
            if (currentProfile != null) {
                mSearchFilters.mcVersion = ModCompatibility.extractMinecraftVersion(currentProfile.lastVersionId);
                if ("mod".equals(mProjectType)) {
                    mSearchFilters.modLoader = ModCompatibility.extractModLoader(currentProfile.lastVersionId);
                }
            }
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        modpackApi = new CommonApi();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        // You can only access resources after attaching to current context
        mModItemAdapter = new ModItemAdapter(getResources(), modpackApi, this);
        ProgressKeeper.addTaskCountListener(mModItemAdapter);
        mOverlayTopCache = getResources().getDimension(R.dimen.fragment_padding_medium);

        mOverlay = view.findViewById(R.id.search_mod_overlay);
        mSearchEditText = view.findViewById(R.id.search_mod_edittext);
        mSearchProgressBar = view.findViewById(R.id.search_mod_progressbar);
        mRecyclerview = view.findViewById(R.id.search_mod_list);
        mStatusTextView = view.findViewById(R.id.search_mod_status_text);
        mFilterButton = view.findViewById(R.id.search_mod_filter);
        mCompatibilityTextView = view.findViewById(R.id.search_mod_compatibility_text);
        mDownloadSelectedButton = view.findViewById(R.id.search_mod_download_selected);
        mSearchEditText.setHint(getSearchHintResId());
        updateCompatibilityLabel();
        updateDownloadButtonState(0);

        mDefaultTextColor = mStatusTextView.getTextColors();

        mRecyclerview.setLayoutManager(new LinearLayoutManager(getContext()));
        DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
        itemAnimator.setAddDuration(180);
        itemAnimator.setMoveDuration(160);
        itemAnimator.setChangeDuration(120);
        mRecyclerview.setItemAnimator(itemAnimator);
        mRecyclerview.setAdapter(mModItemAdapter);

        mRecyclerview.addOnScrollListener(mOverlayPositionListener);

        mSearchEditText.setOnEditorActionListener((v, actionId, event) -> {
            searchMods(mSearchEditText.getText().toString());
            mSearchEditText.clearFocus();
            return false;
        });

        mOverlay.post(()->{
           int overlayHeight = mOverlay.getHeight();
           mRecyclerview.setPadding(mRecyclerview.getPaddingLeft(),
                   mRecyclerview.getPaddingTop() + overlayHeight,
                   mRecyclerview.getPaddingRight(),
                   mRecyclerview.getPaddingBottom());
        });
        mFilterButton.setOnClickListener(v -> displayFilterDialog());
        mDownloadSelectedButton.setOnClickListener(v -> mModItemAdapter.installQueued(requireContext().getApplicationContext()));

        searchMods(null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        ProgressKeeper.removeTaskCountListener(mModItemAdapter);
        mRecyclerview.removeOnScrollListener(mOverlayPositionListener);
    }

    @Override
    public void onSearchFinished() {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setVisibility(View.GONE);
    }

    @Override
    public void onSearchError(int error) {
        mSearchProgressBar.setVisibility(View.GONE);
        mStatusTextView.setVisibility(View.VISIBLE);
        switch (error) {
            case ERROR_INTERNAL:
                mStatusTextView.setTextColor(Color.RED);
                mStatusTextView.setText(mIsModpack ? R.string.search_modpack_error : R.string.search_mod_error);
                break;
            case ERROR_NO_RESULTS:
                mStatusTextView.setTextColor(mDefaultTextColor);
                mStatusTextView.setText(mIsModpack ? R.string.search_modpack_no_result : R.string.search_mod_no_result);
                break;
        }
    }

    @Override
    public void onSelectionChanged(int count) {
        updateDownloadButtonState(count);
    }

    private void searchMods(String name) {
        mSearchProgressBar.setVisibility(View.VISIBLE);
        mSearchFilters.name = name == null ? "" : name;
        mModItemAdapter.performSearchQuery(mSearchFilters);
    }

    private void displayFilterDialog() {
        AlertDialog dialog = new AlertDialog.Builder(requireContext())
                .setView(R.layout.dialog_mod_filters)
                .create();

        // setup the view behavior
        dialog.setOnShowListener(dialogInterface -> {
            TextView mSelectedVersion = dialog.findViewById(R.id.search_mod_selected_mc_version_textview);
            Button mSelectVersionButton = dialog.findViewById(R.id.search_mod_mc_version_button);
            Button mApplyButton = dialog.findViewById(R.id.search_mod_apply_filters);

            assert mSelectVersionButton != null;
            assert mSelectedVersion != null;
            assert mApplyButton != null;

            // Setup the expendable list behavior
            mSelectVersionButton.setOnClickListener(v -> VersionSelectorDialog.open(v.getContext(), true, (id, snapshot)-> mSelectedVersion.setText(id)));

            // Apply visually all the current settings
            mSelectedVersion.setText(mSearchFilters.mcVersion);

            // Apply the new settings
            mApplyButton.setOnClickListener(v -> {
                mSearchFilters.mcVersion = mSelectedVersion.getText().toString();
                updateCompatibilityLabel();
                searchMods(mSearchEditText.getText().toString());
                dialogInterface.dismiss();
            });
        });


        dialog.show();
    }

    private void updateCompatibilityLabel() {
        if (mCompatibilityTextView == null) return;
        if (mIsModpack) {
            mCompatibilityTextView.setText(R.string.search_mod_all_sources);
            return;
        }
        String version = mSearchFilters.mcVersion == null ? getString(R.string.global_default) : mSearchFilters.mcVersion;
        if ("mod".equals(mProjectType)) {
            String loader = mSearchFilters.modLoader == null ? getString(R.string.search_mod_loader_any) : mSearchFilters.modLoader;
            mCompatibilityTextView.setText(getString(R.string.search_mod_compatibility_format, version, loader));
        } else {
            mCompatibilityTextView.setText(getString(R.string.search_mod_content_format, version, getContentTypeLabel()));
        }
    }

    private void updateDownloadButtonState(int count) {
        if (mDownloadSelectedButton == null) return;
        mDownloadSelectedButton.setEnabled(count > 0);
        mDownloadSelectedButton.setText(getString(R.string.search_mod_download_selected_count, count));
    }

    private int getSearchHintResId() {
        if (mIsModpack) return R.string.hint_search_modpack;
        if ("resourcepack".equals(mProjectType)) return R.string.hint_search_resourcepack;
        if ("shader".equals(mProjectType)) return R.string.hint_search_shaderpack;
        return R.string.hint_search_mod;
    }

    private String getContentTypeLabel() {
        if ("resourcepack".equals(mProjectType)) return getString(R.string.instance_mods_resourcepacks);
        if ("shader".equals(mProjectType)) return getString(R.string.instance_mods_shaderpacks);
        return getString(R.string.instance_mods_mods);
    }
}
