package net.kdt.pojavlaunch.fragments;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import net.kdt.pojavlaunch.BaseActivity;
import net.kdt.pojavlaunch.LauncherActivity;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;

public class ModpackCreateFragment extends Fragment {
    public static final String TAG = "ModpackCreateFragment";
    public ModpackCreateFragment() {
        super(R.layout.fragment_create_modpack_profile);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.button_browse_modpacks).setOnClickListener(v -> {
            tryInstall(SearchModFragment.class, SearchModFragment.TAG);
        });
        view.findViewById(R.id.button_import_modpack).setOnClickListener(v -> {
            Activity launcheractivity = requireActivity();
            if (!(launcheractivity instanceof LauncherActivity))
                    throw new IllegalStateException("Cannot import modpack without LauncherActivity");
            ((LauncherActivity) launcheractivity).modpackImportLauncher.launch(null);
        });;

        animateIn(
                view.findViewById(R.id.create_modpack_profile_title),
                view.findViewById(R.id.create_modpack_profile_subtitle),
                view.findViewById(R.id.create_modpack_profile_menu)
        );
    }

    private void tryInstall(Class<? extends Fragment> fragmentClass, String tag){
        Tools.swapFragment(requireActivity(), fragmentClass, tag, null);
    }

    private void animateIn(View... views) {
        for (int i = 0; i < views.length; i++) {
            View target = views[i];
            if (target == null) continue;
            target.setAlpha(0f);
            target.setTranslationY(28f);
            target.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(i * 50L)
                    .setDuration(220L)
                    .start();
        }
    }
}
