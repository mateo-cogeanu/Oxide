package net.kdt.pojavlaunch.profiles;

import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import androidx.core.graphics.ColorUtils;

import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.value.launcherprofiles.LauncherProfiles;
import net.kdt.pojavlaunch.value.launcherprofiles.MinecraftProfile;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import fr.spse.extended_view.ExtendedTextView;

import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;

/*
 * Adapter for listing launcher profiles in a Spinner
 */
public class ProfileAdapter extends BaseAdapter {
    private Map<String, MinecraftProfile> mProfiles;
    private final MinecraftProfile dummy = new MinecraftProfile();
    private List<String> mProfileList;
    private ProfileAdapterExtra[] mExtraEntires;

    public ProfileAdapter(ProfileAdapterExtra[] extraEntries) {
        reloadProfiles(extraEntries);
    }
    /*
     * Gets how much profiles are loaded in the adapter right now
     * @returns loaded profile count
     */
    @Override
    public int getCount() {
        return mProfileList.size() + mExtraEntires.length;
    }
    /*
     * Gets the profile at a given index
     * @param position index to retreive
     * @returns MinecraftProfile name or null
     */
    @Override
    public Object getItem(int position) {
        int profileListSize = mProfileList.size();
        int extraPosition = position - profileListSize;
        if(position < profileListSize){
            String profileName = mProfileList.get(position);
            if(mProfiles.containsKey(profileName)) return profileName;
        }else if(extraPosition >= 0 && extraPosition < mExtraEntires.length) {
            return mExtraEntires[extraPosition];
        }
        return null;
    }



    public int resolveProfileIndex(String name) {
        return mProfileList.indexOf(name);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public void notifyDataSetChanged() {
        mProfiles = new HashMap<>(LauncherProfiles.mainProfileJson.profiles);
        mProfileList = new ArrayList<>(Arrays.asList(mProfiles.keySet().toArray(new String[0])));
        super.notifyDataSetChanged();
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_version_profile_layout,parent,false);
        setView(v, getItem(position), true);
        return v;
    }

    public void setViewProfile(View v, String nm, boolean displaySelection) {
        ExtendedTextView extendedTextView = (ExtendedTextView) v;
        boolean isLandscape = v.getResources().getConfiguration().orientation == ORIENTATION_LANDSCAPE;
        extendedTextView.setTextSize(TypedValue.COMPLEX_UNIT_PX,
                v.getResources().getDimensionPixelSize(isLandscape ? R.dimen._16ssp : R.dimen._14ssp));

        MinecraftProfile minecraftProfile = mProfiles.get(nm);
        if(minecraftProfile == null) minecraftProfile = dummy;
        Drawable cachedIcon = ProfileIconCache.fetchIcon(v.getResources(), nm, minecraftProfile.icon);
        extendedTextView.setCompoundDrawablesRelative(cachedIcon, null, extendedTextView.getCompoundsDrawables()[2], null);

        // Historically, the profile name "New" was hardcoded as the default profile name
        // We consider "New" the same as putting no name at all
        String profileName = (Tools.isValidString(minecraftProfile.name) && !"New".equalsIgnoreCase(minecraftProfile.name)) ? minecraftProfile.name : null;
        String versionName = minecraftProfile.lastVersionId;

        if (MinecraftProfile.LATEST_RELEASE.equalsIgnoreCase(versionName))
            versionName = v.getContext().getString(R.string.profiles_latest_release);
        else if (MinecraftProfile.LATEST_SNAPSHOT.equalsIgnoreCase(versionName))
            versionName = v.getContext().getString(R.string.profiles_latest_snapshot);

        String[] versionParts = splitVersionDisplay(versionName);

        if (versionName == null && profileName != null) {
            extendedTextView.setText(profileName);
        } else if (versionName != null && profileName == null) {
            if (isLandscape && versionParts[1] != null) {
                extendedTextView.setText(buildLandscapeVersionText(v, versionParts[0], versionParts[1]));
            } else {
                extendedTextView.setText(versionName);
            }
        } else {
            SpannableStringBuilder builder = new SpannableStringBuilder();
            builder.append(profileName);
            int versionStart = builder.length();
            builder.append(isLandscape ? "\n" : "   ");
            builder.append(versionName);
            if (isLandscape) {
                builder.setSpan(new AbsoluteSizeSpan(v.getResources().getDimensionPixelSize(R.dimen._14ssp)),
                        versionStart, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                builder.setSpan(new RelativeSizeSpan(0.76f), versionStart, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            builder.setSpan(new ForegroundColorSpan(ColorUtils.setAlphaComponent(Color.WHITE, 105)), versionStart, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            extendedTextView.setText(builder);
        }

        // Set selected background if needed
        if(displaySelection){
            String selectedProfile = LauncherPreferences.DEFAULT_PREF.getString(LauncherPreferences.PREF_KEY_CURRENT_PROFILE,"");
            extendedTextView.setBackgroundColor(selectedProfile.equals(nm) ? ColorUtils.setAlphaComponent(Color.WHITE,60) : Color.TRANSPARENT);
        }else extendedTextView.setBackgroundColor(Color.TRANSPARENT);
    }

    private SpannableStringBuilder buildLandscapeVersionText(View v, String title, String subtitle) {
        SpannableStringBuilder builder = new SpannableStringBuilder();
        builder.append(title);
        int subtitleStart = builder.length();
        builder.append("\n");
        builder.append(subtitle);
        builder.setSpan(new AbsoluteSizeSpan(v.getResources().getDimensionPixelSize(R.dimen._13ssp)),
                subtitleStart, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        builder.setSpan(new ForegroundColorSpan(ColorUtils.setAlphaComponent(Color.WHITE, 105)),
                subtitleStart, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return builder;
    }

    private String[] splitVersionDisplay(String versionName) {
        if (!Tools.isValidString(versionName)) return new String[]{versionName, null};
        int splitIndex = versionName.indexOf(' ');
        if (splitIndex <= 0 || splitIndex >= versionName.length() - 1) {
            return new String[]{versionName, null};
        }
        return new String[]{versionName.substring(0, splitIndex), versionName.substring(splitIndex + 1)};
    }

    public void setViewExtra(View v, ProfileAdapterExtra extra) {
        ExtendedTextView extendedTextView = (ExtendedTextView) v;
        extendedTextView.setCompoundDrawablesRelative(extra.icon, null, extendedTextView.getCompoundsDrawables()[2], null);
        extendedTextView.setText(extra.name);
        extendedTextView.setBackgroundColor(Color.TRANSPARENT);
    }

    public void setView(View v, Object object, boolean displaySelection) {
        if(object instanceof String) {
            setViewProfile(v, (String) object, displaySelection);
        }else if(object instanceof ProfileAdapterExtra) {
            setViewExtra(v, (ProfileAdapterExtra) object);
        }
    }

    /** Reload profiles from the file */
    public void reloadProfiles(){
        LauncherProfiles.load();
        mProfiles = new HashMap<>(LauncherProfiles.mainProfileJson.profiles);
        mProfileList = new ArrayList<>(Arrays.asList(mProfiles.keySet().toArray(new String[0])));
        notifyDataSetChanged();
    }

    /** Reload profiles from the file, with additional extra entries */
    public void reloadProfiles(ProfileAdapterExtra[] extraEntries) {
        if(extraEntries == null) mExtraEntires = new ProfileAdapterExtra[0];
        else mExtraEntires = extraEntries;
        this.reloadProfiles();
    }
}
