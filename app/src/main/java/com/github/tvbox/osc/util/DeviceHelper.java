package com.github.tvbox.osc.util;

import android.content.Context;
import android.content.pm.ActivityInfo;

import com.orhanobut.hawk.Hawk;

/**
 * TV 专版：固定电视 UI 模式，无运行时切换。
 */
public class DeviceHelper {

    public static final int MODE_TV = 0;

    private DeviceHelper() {
    }

    public static void initDeviceMode(Context context) {
        Hawk.put(HawkConfig.UI_MODE, MODE_TV);
        if (!Hawk.contains(HawkConfig.HOME_GRID_COLS)) {
            Hawk.put(HawkConfig.HOME_GRID_COLS, 5);
        }
    }

    public static int getMode(Context context) {
        return MODE_TV;
    }

    public static boolean isTv(Context context) {
        return true;
    }

    public static boolean isTablet(Context context) {
        return false;
    }

    public static boolean isPhone(Context context) {
        return false;
    }

    public static int getScreenOrientation(Context context) {
        return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    }

    public static float getDesignWidthDp(Context context) {
        return 1280f;
    }

    public static float getDesignHeightDp(Context context) {
        return 720f;
    }

    public static int getDefaultGridCols() {
        return 5;
    }
}
