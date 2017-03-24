package net.maxsmr.permissionchecker.gui.activities;

import android.annotation.TargetApi;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.view.View;


public abstract class BaseSplashActivity extends AppCompatActivity {

    private static final String ARG_EXPIRED_TIME = BaseSplashActivity.class.getName() + ".ARG_EXPIRED_TIME";

    private final Handler navigateHandler = new Handler(Looper.getMainLooper());
    private final Runnable navigateRunnable = new Runnable() {
        @Override
        public void run() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && !isDestroyed() || Build.VERSION.SDK_INT < Build.VERSION_CODES.JELLY_BEAN_MR1) {
                onSplashTimeout();
            }
            isNavigateRunnableScheduled = false;
        }
    };

    private boolean isNavigateRunnableScheduled = false;

    protected long expiredTime = 0;
    protected long startTime = 0;

    /** @return 0 if splash not needed */
    protected abstract long getSplashTimeout();

    protected abstract void onSplashTimeout();

    @Nullable
    protected abstract View getClickableView();

    protected abstract boolean allowRemoveCallbackWhenScreenIsOff();

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        expiredTime = savedInstanceState != null ? savedInstanceState.getLong(ARG_EXPIRED_TIME) : expiredTime;
    }

    @Override
    public void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        init();
    }

    private void init() {
        View clickableView = getClickableView();
        if (clickableView != null && clickableView.isClickable()) {
            clickableView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    navigateHandler.removeCallbacks(navigateRunnable);
                    onSplashTimeout();
                }
            });
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        scheduleNavigateRunnable();
    }

    protected void scheduleNavigateRunnable() {

        if (isNavigateRunnableScheduled) {
            return;
        }

        long timeout = getSplashTimeout();

        if (timeout < 0) {
            throw new IllegalArgumentException("incorrect splash timeout");
        }

        startTime = System.currentTimeMillis() - expiredTime;
        navigateHandler.postDelayed(navigateRunnable, expiredTime <= timeout ? timeout - expiredTime : 0);
        isNavigateRunnableScheduled = true;
    }

    @TargetApi(Build.VERSION_CODES.KITKAT_WATCH)
    @Override
    public void onStop() {
        super.onStop();
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        boolean isScreenOn = Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT_WATCH ? pm.isScreenOn() : pm.isInteractive();
        if (isScreenOn || allowRemoveCallbackWhenScreenIsOff()) {
            navigateHandler.removeCallbacks(navigateRunnable);
            isNavigateRunnableScheduled = false;
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(ARG_EXPIRED_TIME, expiredTime = (startTime > 0 ? System.currentTimeMillis() - startTime : 0));
    }
}
