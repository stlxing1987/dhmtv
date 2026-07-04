package com.github.tvbox.osc.util;

import android.content.Context;
import android.content.pm.ActivityInfo;

import com.orhanobut.hawk.Hawk;

/**
 * 平板专版：固定平板 UI 模式（横屏 TV 界面 + 平板设计尺寸）。
 */
public class DeviceHelper {

    public static final int MODE_TABLET = 1;

    private DeviceHelper() {
    }

    public static void initDeviceMode(Context context) {
        Hawk.put(HawkConfig.UI_MODE, MODE_TABLET);
        if (!Hawk.contains(HawkConfig.HOME_GRID_COLS)) {
            Hawk.put(HawkConfig.HOME_GRID_COLS, 5);
        }
    }

    public static int getMode(Context context) {
        return MODE_TABLET;
    }

    public static boolean isTv(Context context) {
        return false;
    }

    public static boolean isTablet(Context context) {
        return true;
    }

    public static boolean isPhone(Context context) {
        return false;
    }

    public static int getScreenOrientation(Context context) {
        return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    }

    public static float getDesignWidthDp(Context context) {
        return 960f;
    }

    public static float getDesignHeightDp(Context context) {
        return 540f;
    }

    public static int getDefaultGridCols() {
        return 5;
    }
}
