package com.github.tvbox.osc.util;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.ui.activity.HomeActivity;
import com.github.tvbox.osc.ui.adapter.ApiHistoryDialogAdapter;
import com.github.tvbox.osc.ui.dialog.ApiDialog;
import com.github.tvbox.osc.ui.dialog.ApiHistoryDialog;
import com.orhanobut.hawk.Hawk;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;
import java.util.List;

public class ConfigDialogHelper {

    public interface UiCallback {
        void onStoreChanged();

        void onLineChanged();
    }

    public static void showApiDialog(BaseActivity activity, UiCallback callback) {
        ApiDialog dialog = new ApiDialog(activity);
        EventBus.getDefault().register(dialog);
        String oldUrl = Hawk.get(HawkConfig.API_URL, "");
        String oldIndex = Hawk.get(HawkConfig.API_INDEX_URL, "");
        dialog.setOnListener(api -> {
            String newUrl = Hawk.get(HawkConfig.API_URL, "");
            String newIndex = Hawk.get(HawkConfig.API_INDEX_URL, "");
            if (callback != null) {
                callback.onStoreChanged();
            }
            if (!TextUtils.equals(oldUrl, newUrl) || !TextUtils.equals(oldIndex, newIndex)) {
                restartApp(activity, false);
            }
        });
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialogInterface) {
                activity.hideSysBar();
                EventBus.getDefault().unregister(dialog);
            }
        });
        dialog.show();
    }

    public static void showLineSwitchDialog(BaseActivity activity, UiCallback callback) {
        List<ApiConfig.UrlIndexItem> lines = ApiConfig.get().getUrlIndexList();
        if (lines.size() <= 1) {
            Toast.makeText(activity, "当前配置无可切换线路", Toast.LENGTH_SHORT).show();
            return;
        }
        ArrayList<String> names = new ArrayList<>();
        for (ApiConfig.UrlIndexItem item : lines) {
            names.add(item.name);
        }
        String currentLine = ApiConfig.get().getCurrentLineName();
        int defaultPos = 0;
        if (!TextUtils.isEmpty(currentLine) && names.contains(currentLine)) {
            defaultPos = names.indexOf(currentLine);
        }
        ApiHistoryDialog dialog = new ApiHistoryDialog(activity);
        dialog.setTip("请选择配置线路");
        ApiHistoryDialogAdapter adapter = new ApiHistoryDialogAdapter(new ApiHistoryDialogAdapter.SelectDialogInterface() {
            @Override
            public void click(String value) {
                int idx = names.indexOf(value);
                if (idx >= 0) {
                    ApiConfig.get().switchLine(lines.get(idx));
                    dialog.dismiss();
                    if (callback != null) {
                        callback.onLineChanged();
                    }
                    restartApp(activity, true);
                }
            }

            @Override
            public void del(String value, ArrayList<String> data) {
            }
        });
        adapter.setAllowReselect(true);
        dialog.setAdapter(adapter, names, defaultPos);
        dialog.setOnDismissListener(dialogInterface -> activity.hideSysBar());
        dialog.show();
    }

    public static void restartApp(Activity activity, boolean useCache) {
        Intent intent = new Intent(App.getInstance(), HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        if (useCache) {
            Bundle bundle = new Bundle();
            bundle.putBoolean("useCache", true);
            intent.putExtras(bundle);
        }
        new Handler(Looper.getMainLooper()).postDelayed(() -> App.getInstance().startActivity(intent), 200);
    }
}
