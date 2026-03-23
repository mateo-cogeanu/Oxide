package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.dialogOnUiThread;
import static net.kdt.pojavlaunch.Tools.hasNoOnlineProfileDialog;
import static net.kdt.pojavlaunch.Tools.hasOnlineProfile;
import static net.kdt.pojavlaunch.Tools.openPath;
import static net.kdt.pojavlaunch.Tools.runOnUiThread;
import static net.kdt.pojavlaunch.Tools.shareLog;

import android.app.Dialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;

import com.kdt.mcgui.mcVersionSpinner;

import net.kdt.pojavlaunch.CustomControlsActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.extra.ExtraConstants;
import net.kdt.pojavlaunch.extra.ExtraCore;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.progresskeeper.ProgressKeeper;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainMenuFragment extends Fragment {
    public static final String TAG = "MainMenuFragment";

    private mcVersionSpinner mVersionSpinner;
    private Button mManageModsButton;

    public MainMenuFragment(){
        super(R.layout.fragment_launcher);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        Button mCustomControlButton = view.findViewById(R.id.custom_control_button);
        Button mInstallJarButton = view.findViewById(R.id.install_jar_button);
        mManageModsButton = view.findViewById(R.id.manage_mods_button);
        Button mShareLogsButton = view.findViewById(R.id.share_logs_button);
        Button mOpenDirectoryButton = view.findViewById(R.id.open_files_button);

        ImageButton mEditProfileButton = view.findViewById(R.id.edit_profile_button);
        Button mPlayButton = view.findViewById(R.id.play_button);
        mVersionSpinner = view.findViewById(R.id.mc_version_spinner);
        mVersionSpinner.setOnProfileSelectedListener(this::updateManageModsVisibility);

        mCustomControlButton.setOnClickListener(v -> startActivity(new Intent(requireContext(), CustomControlsActivity.class)));
        if (hasOnlineProfile()) {
            mInstallJarButton.setOnClickListener(v -> runInstallerWithConfirmation(false));
            mInstallJarButton.setOnLongClickListener(v -> {
                runInstallerWithConfirmation(true);
                return true;
            });
        } else mInstallJarButton.setOnClickListener(v -> hasNoOnlineProfileDialog(requireActivity()));
        mManageModsButton.setOnClickListener(v -> Tools.swapFragment(requireActivity(), InstanceModsFragment.class, InstanceModsFragment.TAG, null));
        mEditProfileButton.setOnClickListener(v -> mVersionSpinner.openProfileEditor(requireActivity()));
        updateManageModsVisibility();

        mPlayButton.setOnClickListener(v -> ExtraCore.setValue(ExtraConstants.LAUNCH_GAME, true));

        mShareLogsButton.setOnClickListener((v) -> shareLog(requireContext()));

        mOpenDirectoryButton.setOnClickListener((v)-> {
            if (Tools.isDemoProfile(v.getContext())){ // Say a different message when on demo profile since they might see the hidden demo folder
                hasNoOnlineProfileDialog(getActivity(), getString(R.string.demo_unsupported), getString(R.string.change_account));
            } else if (!hasOnlineProfile()) { // Otherwise display the generic pop-up to log in
                hasNoOnlineProfileDialog(requireActivity());
        } else openPath(v.getContext(), getCurrentProfileDirectory(), false);

        });
        animateMenuEntrance(mCustomControlButton, mInstallJarButton, mManageModsButton, mShareLogsButton,
                mOpenDirectoryButton, mVersionSpinner, mEditProfileButton, mPlayButton);
    }

    private File getCurrentProfileDirectory() {
        String currentProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        if(!Tools.isValidString(currentProfile)) return new File(Tools.DIR_GAME_NEW);
        LauncherProfiles.load();
        MinecraftProfile profileObject = LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
        if(profileObject == null) return new File(Tools.DIR_GAME_NEW);
        return Tools.getGameDirPath(profileObject);
    }

    @Override
    public void onResume() {
        super.onResume();
        mVersionSpinner.reloadProfiles();
        updateManageModsVisibility();
    }

    private void runInstallerWithConfirmation(boolean isCustomArgs) {
        if (ProgressKeeper.getTaskCount() == 0)
            Tools.installMod(requireActivity(), isCustomArgs);
        else
            Toast.makeText(requireContext(), R.string.tasks_ongoing, Toast.LENGTH_LONG).show();
    }

    private void updateManageModsVisibility() {
        if (mManageModsButton == null) return;
        boolean shouldShow = isCurrentProfileModded();
        boolean isVisible = mManageModsButton.getVisibility() == View.VISIBLE;
        if (shouldShow == isVisible) return;

        mManageModsButton.animate().cancel();
        if (shouldShow) {
            mManageModsButton.setAlpha(0f);
            mManageModsButton.setTranslationY(18f);
            mManageModsButton.setVisibility(View.VISIBLE);
            mManageModsButton.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(180)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        } else {
            mManageModsButton.animate()
                    .alpha(0f)
                    .translationY(-12f)
                    .setDuration(140)
                    .setInterpolator(new DecelerateInterpolator())
                    .withEndAction(() -> {
                        mManageModsButton.setVisibility(View.GONE);
                        mManageModsButton.setAlpha(1f);
                        mManageModsButton.setTranslationY(0f);
                    })
                    .start();
        }
    }

    private boolean isCurrentProfileModded() {
        LauncherProfiles.load();
        String currentProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE, null);
        if (!Tools.isValidString(currentProfile)) return false;

        MinecraftProfile profile = LauncherProfiles.mainProfileJson.profiles.get(currentProfile);
        if (profile == null || !Tools.isValidString(profile.lastVersionId)) return false;

        String versionId = profile.lastVersionId.toLowerCase(Locale.ROOT);
        return versionId.contains("forge")
                || versionId.contains("fabric")
                || versionId.contains("quilt")
                || versionId.contains("neoforge")
                || versionId.contains("optifine")
                || versionId.contains("bta");
    }

    private void animateMenuEntrance(View... views) {
        List<View> visibleViews = new ArrayList<>();
        for (View item : views) {
            if (item != null && item.getVisibility() == View.VISIBLE) {
                visibleViews.add(item);
            }
        }

        for (int i = 0; i < visibleViews.size(); i++) {
            View item = visibleViews.get(i);
            item.setAlpha(0f);
            item.setTranslationY(24f);
            item.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * 28L)
                    .setDuration(220)
                    .setInterpolator(new DecelerateInterpolator())
                    .start();
        }
    }
}
