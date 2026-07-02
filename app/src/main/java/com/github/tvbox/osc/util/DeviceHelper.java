package com.github.tvbox.osc.util;

import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;

import com.github.tvbox.osc.base.App;
import com.orhanobut.hawk.Hawk;

/**
 * 通用 APK：启动时自动识别电视 / 平板 / 手机，并写入 UI_MODE。
 */
public class DeviceHelper {

    public static final int MODE_TV = 0;
    public static final int MODE_TABLET = 1;
    public static final int MODE_PHONE = 2;

    private DeviceHelper() {
    }

    public static void initDeviceMode(Context context) {
        if (Hawk.contains(HawkConfig.DEVICE_AUTO_DETECTED)) {
            return;
        }
        if (!Hawk.contains(HawkConfig.UI_MODE)) {
            int detected = detectDeviceType(context);
            Hawk.put(HawkConfig.UI_MODE, detected);
            applyDefaultSettingsForMode(detected);
        }
        Hawk.put(HawkConfig.DEVICE_AUTO_DETECTED, true);
    }

    public static int detectDeviceType(Context context) {
        UiModeManager uiModeManager = (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
        PackageManager pm = context.getPackageManager();
        boolean isTv = (uiModeManager != null
                && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION)
                || pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK);
        if (isTv) {
            return MODE_TV;
        }
        int smallestWidthDp = context.getResources().getConfiguration().smallestScreenWidthDp;
        if (smallestWidthDp >= 600) {
            return MODE_TABLET;
        }
        return MODE_PHONE;
    }

    public static int getMode(Context context) {
        return Hawk.get(HawkConfig.UI_MODE, MODE_TV);
    }

    public static boolean isTv(Context context) {
        return getMode(context) == MODE_TV;
    }

    public static boolean isTablet(Context context) {
        return getMode(context) == MODE_TABLET;
    }

    public static boolean isPhone(Context context) {
        return getMode(context) == MODE_PHONE;
    }

    public static int getScreenOrientation(Context context) {
        return isPhone(context)
                ? ActivityInfo.SCREEN_ORIENTATION_USER
                : ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    }

    public static float getDesignWidthDp(Context context) {
        switch (getMode(context)) {
            case MODE_PHONE:
                return 360f;
            case MODE_TABLET:
                return 960f;
            default:
                return 1280f;
        }
    }

    public static float getDesignHeightDp(Context context) {
        switch (getMode(context)) {
            case MODE_PHONE:
                return 640f;
            case MODE_TABLET:
                return 540f;
            default:
                return 720f;
        }
    }

    public static int getDefaultGridCols(int mode) {
        return mode == MODE_PHONE ? 3 : 5;
    }

    public static void applyGridColsForMode(int mode) {
        Hawk.put(HawkConfig.HOME_GRID_COLS, getDefaultGridCols(mode));
    }

    private static void applyDefaultSettingsForMode(int mode) {
        if (!Hawk.contains(HawkConfig.HOME_GRID_COLS)) {
            applyGridColsForMode(mode);
        }
    }
}
