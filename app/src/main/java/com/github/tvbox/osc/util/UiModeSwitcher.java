package com.github.tvbox.osc.util;

import android.app.Activity;
import android.content.Intent;

import com.github.tvbox.osc.ui.activity.HomeActivity;
import com.github.tvbox.osc.ui.activity.MobileHomeActivity;
import com.orhanobut.hawk.Hawk;

/**
 * 切换电视 / 平板 / 手机 UI 模式并立即重启到对应首页。
 */
public final class UiModeSwitcher {

    public static volatile boolean switching = false;

    private UiModeSwitcher() {
    }

    public static void apply(Activity activity, int newMode) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        int current = Hawk.get(HawkConfig.UI_MODE, DeviceHelper.MODE_TV);
        if (current == newMode) {
            return;
        }
        switching = true;
        Hawk.put(HawkConfig.UI_MODE, newMode);
        DeviceHelper.applyGridColsForMode(newMode);

        Class<?> target = newMode == DeviceHelper.MODE_PHONE
                ? MobileHomeActivity.class
                : HomeActivity.class;
        Intent intent = new Intent(activity, target);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finish();
    }
}
