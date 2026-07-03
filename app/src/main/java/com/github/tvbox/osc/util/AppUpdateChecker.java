package com.github.tvbox.osc.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.text.TextUtils;

import androidx.core.content.FileProvider;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.AppUpdateInfo;
import com.github.tvbox.osc.util.HawkConfig;
import com.google.gson.Gson;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import java.io.File;

/**
 * App APK 热更新：拉取 update.json → 下载 APK → 调起安装。
 */
public final class AppUpdateChecker {

    public interface CheckCallback {
        void onUpdateAvailable(AppUpdateInfo info);

        void onNoUpdate();

        void onError(String message);
    }

    private AppUpdateChecker() {
    }

    public static String getUpdateUrl(Context context) {
        String custom = Hawk.get(HawkConfig.APP_UPDATE_URL, "");
        if (!TextUtils.isEmpty(custom)) {
            return custom.trim();
        }
        String fallback = context.getString(R.string.app_update_url);
        return TextUtils.isEmpty(fallback) ? "" : fallback.trim();
    }

    public static int getLocalVersionCode(Context context) {
        try {
            PackageInfo info = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return (int) info.getLongVersionCode();
            }
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            return 0;
        }
    }

    public static String getLocalVersionName(Context context) {
        try {
            return context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            return "1.0.0";
        }
    }

    public static boolean isIgnored(AppUpdateInfo info) {
        if (info == null) {
            return true;
        }
        int ignored = Hawk.get(HawkConfig.APP_UPDATE_IGNORE_CODE, 0);
        return ignored >= info.versionCode;
    }

    public static void ignoreVersion(AppUpdateInfo info) {
        if (info != null) {
            Hawk.put(HawkConfig.APP_UPDATE_IGNORE_CODE, info.versionCode);
        }
    }

    public static void check(Context context, CheckCallback callback) {
        check(context, false, callback);
    }

    public static void check(Context context, boolean manual, CheckCallback callback) {
        String url = getUpdateUrl(context);
        if (TextUtils.isEmpty(url)) {
            if (callback != null) {
                callback.onError("未配置更新地址，请在设置中填写或在 strings.xml 配置 app_update_url");
            }
            return;
        }
        OkGo.<String>get(url)
                .tag("app_update_check")
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        try {
                            AppUpdateInfo info = new Gson().fromJson(response.body(), AppUpdateInfo.class);
                            if (info == null || !info.isValid()) {
                                if (callback != null) {
                                    callback.onError("更新配置格式无效");
                                }
                                return;
                            }
                            int localCode = getLocalVersionCode(context);
                            if (!info.isNewerThan(localCode)) {
                                if (callback != null) {
                                    callback.onNoUpdate();
                                }
                                return;
                            }
                            if (!manual && isIgnored(info)) {
                                if (callback != null) {
                                    callback.onNoUpdate();
                                }
                                return;
                            }
                            if (callback != null) {
                                callback.onUpdateAvailable(info);
                            }
                        } catch (Throwable th) {
                            if (callback != null) {
                                callback.onError("解析更新配置失败");
                            }
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        if (callback != null) {
                            callback.onError(formatCheckError(url, response));
                        }
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        if (response.body() == null) {
                            return "";
                        }
                        return response.body().string();
                    }
                });
    }

    public static void installApk(Context context, File apkFile) {
        installApk(context, apkFile, 0);
    }

    public static void installApk(Context context, File apkFile, int pendingVersionCode) {
        if (apkFile == null || !apkFile.exists()) {
            return;
        }
        if (pendingVersionCode > 0) {
            Hawk.put(HawkConfig.APP_UPDATE_PENDING_CODE, pendingVersionCode);
        }
        Uri uri = FileProvider.getUriForFile(
                context,
                context.getPackageName() + ".fileprovider",
                apkFile);
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        context.startActivity(intent);
    }

    /** 新版本启动后清理已安装 APK，释放电视存储空间 */
    public static void cleanupInstalledApk(Context context) {
        int pendingCode = Hawk.get(HawkConfig.APP_UPDATE_PENDING_CODE, 0);
        if (pendingCode <= 0) {
            return;
        }
        int currentCode = getLocalVersionCode(context);
        if (currentCode < pendingCode) {
            return;
        }
        File apk = getApkCacheFile(context);
        if (apk.exists()) {
            apk.delete();
        }
        Hawk.delete(HawkConfig.APP_UPDATE_PENDING_CODE);
    }

    public static File getApkCacheFile(Context context) {
        return new File(context.getCacheDir(), "dhmtv_update.apk");
    }

    public static void cancelTasks() {
        OkGo.getInstance().cancelTag("app_update_check");
        OkGo.getInstance().cancelTag("app_update_download");
    }

    private static String formatCheckError(String url, Response<String> response) {
        if (response != null && response.getRawResponse() != null) {
            int code = response.getRawResponse().code();
            if (code == 404) {
                return "更新配置不存在(404)，请上传 update.json 到：\n" + url;
            }
            if (code >= 500) {
                return "更新服务器错误(" + code + ")，请稍后重试";
            }
        }
        String msg = response != null && response.getException() != null
                ? response.getException().getMessage()
                : null;
        if (msg != null && msg.contains("404")) {
            return "更新配置不存在(404)，请上传 update.json 到：\n" + url;
        }
        return TextUtils.isEmpty(msg) ? "检查更新失败，请确认网络与更新地址" : msg;
    }
}
