package com.github.tvbox.osc.util;

import android.app.Activity;
import android.widget.Toast;

import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.AppUpdateInfo;
import com.github.tvbox.osc.ui.dialog.AppUpdateDialog;
import com.orhanobut.hawk.Hawk;

/**
 * 启动时 / 手动触发 App 更新检查。
 */
public final class AppUpdateHelper {

    private static boolean startupChecked;

    private AppUpdateHelper() {
    }

    public static void checkOnStartup(BaseActivity activity) {
        if (startupChecked || activity == null || activity.isFinishing()) {
            return;
        }
        if (!Hawk.get(HawkConfig.APP_UPDATE_CHECK_ON_START, true)) {
            return;
        }
        if (AppUpdateChecker.getUpdateUrl(activity).isEmpty()) {
            return;
        }
        startupChecked = true;
        check(activity, false);
    }

    public static void check(BaseActivity activity, boolean manual) {
        if (activity == null || activity.isFinishing()) {
            return;
        }
        AppUpdateChecker.check(activity, manual, new AppUpdateChecker.CheckCallback() {
            @Override
            public void onUpdateAvailable(AppUpdateInfo info) {
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing()) {
                        return;
                    }
                    new AppUpdateDialog(activity, info).show();
                });
            }

            @Override
            public void onNoUpdate() {
                if (manual) {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity, "当前已是最新版本", Toast.LENGTH_SHORT).show());
                }
            }

            @Override
            public void onError(String message) {
                if (manual) {
                    activity.runOnUiThread(() ->
                            Toast.makeText(activity, message, Toast.LENGTH_SHORT).show());
                }
            }
        });
    }
}
