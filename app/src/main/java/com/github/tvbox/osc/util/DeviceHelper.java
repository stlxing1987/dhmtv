package com.github.tvbox.osc.util;

import android.content.Context;
import android.content.pm.ActivityInfo;

import com.orhanobut.hawk.Hawk;

/**
 * 手机专版：固定手机 UI 模式，无运行时切换。
 */
public class DeviceHelper {

    public static final int MODE_PHONE = 2;

    private DeviceHelper() {
    }

    public static void initDeviceMode(Context context) {
        Hawk.put(HawkConfig.UI_MODE, MODE_PHONE);
        if (!Hawk.contains(HawkConfig.HOME_GRID_COLS)) {
            Hawk.put(HawkConfig.HOME_GRID_COLS, 3);
        }
    }

    public static int getMode(Context context) {
        return MODE_PHONE;
    }

    public static boolean isTv(Context context) {
        return false;
    }

    public static boolean isTablet(Context context) {
        return false;
    }

    public static boolean isPhone(Context context) {
        return true;
    }

    public static int getScreenOrientation(Context context) {
        return ActivityInfo.SCREEN_ORIENTATION_USER;
    }

    public static float getDesignWidthDp(Context context) {
        return 360f;
    }

    public static float getDesignHeightDp(Context context) {
        return 640f;
    }

    public static int getDefaultGridCols() {
        return 3;
    }
}
