package com.kdt.mcgui;

import android.content.Context;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;

import androidx.core.content.res.ResourcesCompat;

import net.kdt.pojavlaunch.R;

public class LauncherPlayButton extends MineButton {

    public LauncherPlayButton(Context ctx) {
        super(ctx);
    }

    public LauncherPlayButton(Context ctx, AttributeSet attrs) {
        super(ctx, attrs);
    }

    @Override
    public void init() {
        setTypeface(ResourcesCompat.getFont(getContext(), R.font.minecraft_ten));
        setBackground(ResourcesCompat.getDrawable(getResources(), R.drawable.play_button_background, null));
        setTextSize(TypedValue.COMPLEX_UNIT_PX, getResources().getDimensionPixelSize(R.dimen._22ssp));
        setAllCaps(true);
        setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.animate().cancel();
                    v.animate()
                            .scaleX(0.985f)
                            .scaleY(0.965f)
                            .translationY(getResources().getDimension(R.dimen._1sdp))
                            .alpha(0.97f)
                            .setDuration(85)
                            .start();
                    break;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    v.animate().cancel();
                    v.animate()
                            .scaleX(1f)
                            .scaleY(1f)
                            .translationY(0f)
                            .alpha(1f)
                            .setDuration(140)
                            .start();
                    break;
            }
            return false;
        });
    }
}
