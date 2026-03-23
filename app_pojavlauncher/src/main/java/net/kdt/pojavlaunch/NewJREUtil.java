package net.kdt.pojavlaunch;

import static net.kdt.pojavlaunch.Architecture.archAsString;

import android.app.Activity;
import android.content.res.AssetManager;
import android.util.Log;

import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.multirt.Runtime;
import net.kdt.pojavlaunch.utils.MathUtils;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.io.IOException;
import java.io.InputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.URL;
import java.util.Arrays;
import java.util.List;

public class NewJREUtil {
    private static volatile Throwable sLastError;
    private static volatile String sLastDiagnostic;

    private static boolean checkInternalRuntime(AssetManager assetManager, InternalRuntime internalRuntime) {
        sLastError = null;
        sLastDiagnostic = "Checking runtime " + internalRuntime.name;
        String launcher_runtime_version;
        String installed_runtime_version = MultiRTUtils.readInternalRuntimeVersion(internalRuntime.name);
        try {
            launcher_runtime_version = Tools.read(assetManager.open(internalRuntime.path+"/version"));
        }catch (IOException exc) {
            // Try downloading a known-good runtime when the APK does not bundle one.
            if (installed_runtime_version != null) return true;
            sLastDiagnostic = "Bundled runtime missing for " + internalRuntime.name;
            return downloadInternalRuntime(internalRuntime);
        }
        // this implicitly checks for null, so it will unpack the runtime even if we don't have one installed
        if(!launcher_runtime_version.equals(installed_runtime_version))
            return unpackInternalRuntime(assetManager, internalRuntime, launcher_runtime_version);
        else return true;
    }

    private static boolean unpackInternalRuntime(AssetManager assetManager, InternalRuntime internalRuntime, String version) {
        try {
            sLastDiagnostic = "Installing bundled runtime " + internalRuntime.name + " version " + version;
            MultiRTUtils.installRuntimeNamedBinpack(
                    assetManager.open(internalRuntime.path+"/universal.tar.xz"),
                    assetManager.open(internalRuntime.path+"/bin-" + archAsString(Tools.DEVICE_ARCHITECTURE) + ".tar.xz"),
                    internalRuntime.name, version);
            MultiRTUtils.postPrepare(internalRuntime.name);
            sLastError = null;
            return true;
        }catch (IOException e) {
            Log.e("NewJREAuto", "Internal JRE unpack failed", e);
            sLastError = e;
            sLastDiagnostic = "Bundled runtime install failed for " + internalRuntime.name + ": " + e.getClass().getSimpleName();
            return false;
        }
    }

    private static boolean downloadInternalRuntime(InternalRuntime internalRuntime) {
        String runtimeUrl = internalRuntime.getDownloadUrl();
        if (runtimeUrl == null) {
            sLastDiagnostic = "No fallback runtime URL for " + internalRuntime.name + " on arch " + archAsString(Tools.DEVICE_ARCHITECTURE);
            return false;
        }

        try (InputStream runtimeInputStream = new URL(runtimeUrl).openStream()) {
            sLastDiagnostic = "Downloading runtime " + internalRuntime.name + " from " + runtimeUrl;
            MultiRTUtils.installRuntimeNamed(
                    Tools.NATIVE_LIB_DIR,
                    runtimeInputStream,
                    internalRuntime.name
            );
            MultiRTUtils.postPrepare(internalRuntime.name);
            writeInstalledRuntimeVersion(internalRuntime.name, internalRuntime.fallbackVersion);
            sLastError = null;
            return true;
        } catch (IOException e) {
            Log.e("NewJREAuto", "Fallback JRE download failed", e);
            sLastError = e;
            sLastDiagnostic = "Fallback runtime download failed for " + internalRuntime.name + " from " + runtimeUrl + ": " + e.getClass().getSimpleName();
            return false;
        }
    }

    private static void writeInstalledRuntimeVersion(String runtimeName, String version) throws IOException {
        File versionFile = new File(Tools.MULTIRT_HOME, runtimeName + "/pojav_version");
        try (FileOutputStream outputStream = new FileOutputStream(versionFile)) {
            outputStream.write(version.getBytes());
        }
    }

    private static InternalRuntime getInternalRuntime(Runtime runtime) {
        for(InternalRuntime internalRuntime : InternalRuntime.values()) {
            if(internalRuntime.name.equals(runtime.name)) return internalRuntime;
        }
        return null;
    }

    private static MathUtils.RankedValue<Runtime> getNearestInstalledRuntime(int targetVersion) {
        List<Runtime> runtimes = MultiRTUtils.getRuntimes();
        return MathUtils.findNearestPositive(targetVersion, runtimes, (runtime)->runtime.javaVersion);
    }

    private static MathUtils.RankedValue<InternalRuntime> getNearestInternalRuntime(int targetVersion) {
        List<InternalRuntime> runtimeList = Arrays.asList(InternalRuntime.values());
        return MathUtils.findNearestPositive(targetVersion, runtimeList, (runtime)->runtime.majorVersion);
    }


    /** @return true if everything is good, false otherwise.  */
    public static boolean installNewJreIfNeeded(Activity activity, JMinecraftVersionList.Version versionInfo) {
        //Now we have the reliable information to check if our runtime settings are good enough
        if (versionInfo.javaVersion == null || versionInfo.javaVersion.component.equalsIgnoreCase("jre-legacy"))
            return true;

        int gameRequiredVersion = versionInfo.javaVersion.majorVersion;

        LauncherProfiles.load();
        AssetManager assetManager = activity.getAssets();
        MinecraftProfile minecraftProfile = LauncherProfiles.getCurrentProfile();
        String profileRuntime = Tools.getSelectedRuntime(minecraftProfile);
        Runtime runtime = MultiRTUtils.read(profileRuntime);
        // Partly trust the user with his own selection, if the game can even try to run in this case
        if (runtime.javaVersion >= gameRequiredVersion) {
            // Check whether the selection is an internal runtime
            InternalRuntime internalRuntime = getInternalRuntime(runtime);
            // If it is, check if updates are available from the APK file
            if(internalRuntime != null) {
                // Not calling showRuntimeFail on failure here because we did, technically, find the compatible runtime
                return checkInternalRuntime(assetManager, internalRuntime);
            }
            return true;
        }

        // If the runtime version selected by the user is not appropriate for this version (which means the game won't run at all)
        // automatically pick from either an already installed runtime, or a runtime packed with the launcher
        MathUtils.RankedValue<?> nearestInstalledRuntime = getNearestInstalledRuntime(gameRequiredVersion);
        MathUtils.RankedValue<?> nearestInternalRuntime = getNearestInternalRuntime(gameRequiredVersion);

        MathUtils.RankedValue<?> selectedRankedRuntime = MathUtils.objectMin(
                nearestInternalRuntime, nearestInstalledRuntime, (value)->value.rank
        );

        // No possible selections
        if(selectedRankedRuntime == null) {
            showRuntimeFail(activity, versionInfo);
            return false;
        }

        Object selected = selectedRankedRuntime.value;
        String appropriateRuntime;
        InternalRuntime internalRuntime;

        // Perform checks on the picked runtime
        if(selected instanceof Runtime) {
            // If it's an already installed runtime, save its name and check if
            // it's actually an internal one (just in case)
            Runtime selectedRuntime = (Runtime) selected;
            appropriateRuntime = selectedRuntime.name;
            internalRuntime = getInternalRuntime(selectedRuntime);
        } else if (selected instanceof InternalRuntime) {
            // If it's an internal runtime, set it's name as the appropriate one.
            internalRuntime = (InternalRuntime) selected;
            appropriateRuntime = internalRuntime.name;
        } else {
            throw new RuntimeException("Unexpected type of selected: "+selected.getClass().getName());
        }

        // If it turns out the selected runtime is actually an internal one, attempt automatic installation or update
        if(internalRuntime != null && !checkInternalRuntime(assetManager, internalRuntime)) {
            // Not calling showRuntimeFail here because we did, technically, find the compatible runtime
            return false;
        }

        minecraftProfile.javaDir = Tools.LAUNCHERPROFILES_RTPREFIX + appropriateRuntime;
        LauncherProfiles.write();
        return true;
    }

    private static void showRuntimeFail(Activity activity, JMinecraftVersionList.Version verInfo) {
        Tools.dialogOnUiThread(activity, activity.getString(R.string.global_error),
                activity.getString(R.string.multirt_nocompatiblert, verInfo.javaVersion.majorVersion));
    }

    public static Throwable getLastError() {
        return sLastError;
    }

    public static String getLastDiagnostic() {
        return sLastDiagnostic;
    }

    private enum InternalRuntime {
        JRE_17(17, "Internal-17", "components/jre-new", "jre17-ec28559"),
        JRE_21(21, "Internal-21", "components/jre-21", null),
        JRE_25(25, "Internal-25", "components/jre-25", null);
        public final int majorVersion;
        public final String name;
        public final String path;
        public final String fallbackVersion;
        InternalRuntime(int majorVersion, String name, String path, String fallbackVersion) {
            this.majorVersion = majorVersion;
            this.name = name;
            this.path = path;
            this.fallbackVersion = fallbackVersion;
        }

        public String getDownloadUrl() {
            if (fallbackVersion == null) return null;
            switch (Tools.DEVICE_ARCHITECTURE) {
                case Architecture.ARCH_ARM:
                    return "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/"
                            + fallbackVersion + "/jre17-arm-20210914-release.tar.xz";
                case Architecture.ARCH_ARM64:
                    return "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/"
                            + fallbackVersion + "/jre17-arm64-20210825-release.tar.xz";
                case Architecture.ARCH_X86:
                    return "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/"
                            + fallbackVersion + "/jre17-x86-20220225-release.tar.xz";
                case Architecture.ARCH_X86_64:
                    return "https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch/releases/download/"
                            + fallbackVersion + "/jre17-x86_64-20210825-release.tar.xz";
                default:
                    return null;
            }
        }
    }

}
