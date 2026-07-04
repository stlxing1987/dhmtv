package com.github.tvbox.osc.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.text.TextUtils;
import android.widget.Toast;

import com.github.tvbox.osc.player.thirdparty.MXPlayer;
import com.github.tvbox.osc.player.thirdparty.ReexPlayer;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.FileCallback;
import com.lzy.okgo.model.Progress;
import com.lzy.okgo.model.Response;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ExternalPlayerHelper {

    public static final int TYPE_MX = 10;
    public static final int TYPE_REEX = 11;

    public interface DownloadCallback {
        void onProgress(int percent);

        void onSuccess();

        void onError(String message);
    }

    public static boolean isInstalled(int playerType) {
        if (playerType == TYPE_MX) {
            return MXPlayer.getPackageInfo() != null;
        }
        if (playerType == TYPE_REEX) {
            return ReexPlayer.getPackageInfo() != null;
        }
        return false;
    }

    public static String getDisplayName(int playerType) {
        if (playerType == TYPE_MX) {
            return "MX Player";
        }
        if (playerType == TYPE_REEX) {
            return "Reex Player";
        }
        return "外部播放器";
    }

    public static String getExpectedPackage(int playerType) {
        if (playerType == TYPE_MX) {
            return "com.mxtech.videoplayer.ad";
        }
        if (playerType == TYPE_REEX) {
            return "xyz.re.player.ex";
        }
        return null;
    }

    /**
     * 国内优先：Reex 走 Gitee 直链；MX 走 gh-proxy / gitmirror 等镜像。
     */
    public static List<String> getDownloadUrls(int playerType) {
        ArrayList<String> urls = new ArrayList<>();
        String abi = getPreferredAbi();
        if (playerType == TYPE_REEX) {
            String version = "1.8.9";
            String file = abi + "-release-v" + version + ".apk";
            String gitee = "https://gitee.com/lntls/reex/releases/download/v" + version + "/" + file;
            urls.add(gitee);
            urls.add(wrapGhProxy(gitee));
            if (!"arm64-v8a".equals(abi)) {
                String arm64 = "https://gitee.com/lntls/reex/releases/download/v" + version + "/arm64-v8a-release-v" + version + ".apk";
                urls.add(arm64);
            }
            if (!"armeabi-v7a".equals(abi)) {
                String v7a = "https://gitee.com/lntls/reex/releases/download/v" + version + "/armeabi-v7a-release-v" + version + ".apk";
                urls.add(v7a);
            }
        } else if (playerType == TYPE_MX) {
            if (abi.contains("64")) {
                String raw = "https://github.com/youhunwl/TVAPP/raw/refs/heads/MXPlayer-1.94.0-v8a-CN-Mod.apk";
                urls.add(wrapGhProxy(raw));
                urls.add("https://mirror.ghproxy.com/" + raw);
                urls.add("https://gitmirror.com/github.com/youhunwl/TVAPP/raw/refs/heads/MXPlayer-1.94.0-v8a-CN-Mod.apk");
            } else {
                String raw = "https://github.com/youhunwl/TVAPP/raw/refs/heads/main/MXPlayer-Pro-1.86.0-v7a-Mod-Balatan.apk";
                urls.add(wrapGhProxy(raw));
                urls.add("https://mirror.ghproxy.com/" + raw);
                urls.add("https://gitmirror.com/github.com/youhunwl/TVAPP/raw/refs/heads/main/MXPlayer-Pro-1.86.0-v7a-Mod-Balatan.apk");
            }
        }
        return urls;
    }

    public static void downloadAndInstall(Context context, int playerType, DownloadCallback callback) {
        List<String> urls = getDownloadUrls(playerType);
        if (urls.isEmpty()) {
            if (callback != null) {
                callback.onError("暂无可用下载地址");
            }
            return;
        }
        OkGo.getInstance().cancelTag(downloadTag(playerType));
        tryDownload(context, playerType, urls, 0, callback);
    }

    public static void cancelDownload(int playerType) {
        OkGo.getInstance().cancelTag(downloadTag(playerType));
    }

    private static void tryDownload(Context context, int playerType, List<String> urls, int index, DownloadCallback callback) {
        if (index >= urls.size()) {
            if (callback != null) {
                callback.onError("所有下载线路均失败，请检查网络后重试");
            }
            return;
        }
        String url = urls.get(index);
        File target = getApkCacheFile(context, playerType);
        if (target.exists()) {
            target.delete();
        }
        if (callback != null) {
            callback.onProgress(0);
        }
        OkGo.<File>get(url)
                .tag(downloadTag(playerType))
                .execute(new FileCallback(target.getParent(), target.getName()) {
                    @Override
                    public void onSuccess(Response<File> response) {
                        File apk = response.body();
                        if (!isValidApk(context, apk, playerType)) {
                            if (apk != null && apk.exists()) {
                                apk.delete();
                            }
                            tryDownload(context, playerType, urls, index + 1, callback);
                            return;
                        }
                        try {
                            AppUpdateChecker.installApk(context, apk);
                            if (callback != null) {
                                callback.onSuccess();
                            }
                        } catch (Throwable th) {
                            if (callback != null) {
                                callback.onError("安装失败：" + th.getMessage());
                            }
                        }
                    }

                    @Override
                    public void onError(Response<File> response) {
                        tryDownload(context, playerType, urls, index + 1, callback);
                    }

                    @Override
                    public void downloadProgress(Progress progress) {
                        super.downloadProgress(progress);
                        if (callback != null) {
                            callback.onProgress(progress == null ? 0 : (int) (progress.fraction * 100));
                        }
                    }
                });
    }

    private static boolean isValidApk(Context context, File apk, int playerType) {
        if (apk == null || !apk.exists() || apk.length() < 1024 * 512) {
            return false;
        }
        PackageInfo info = context.getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
        if (info == null || TextUtils.isEmpty(info.packageName)) {
            return false;
        }
        String expected = getExpectedPackage(playerType);
        if (playerType == TYPE_MX) {
            return info.packageName.startsWith("com.mxtech.videoplayer");
        }
        return expected != null && expected.equals(info.packageName);
    }

    public static File getApkCacheFile(Context context, int playerType) {
        String name = playerType == TYPE_MX ? "mx_player_install.apk" : "reex_player_install.apk";
        return new File(context.getCacheDir(), name);
    }

    private static String downloadTag(int playerType) {
        return "external_player_download_" + playerType;
    }

    private static String wrapGhProxy(String url) {
        if (url.startsWith("https://gh-proxy.com/")) {
            return url;
        }
        return "https://gh-proxy.com/" + url;
    }

    private static String getPreferredAbi() {
        if (Build.SUPPORTED_ABIS != null) {
            for (String abi : Build.SUPPORTED_ABIS) {
                if ("arm64-v8a".equals(abi)) {
                    return "arm64-v8a";
                }
            }
            for (String abi : Build.SUPPORTED_ABIS) {
                if ("armeabi-v7a".equals(abi)) {
                    return "armeabi-v7a";
                }
            }
            if (Build.SUPPORTED_ABIS.length > 0) {
                return Build.SUPPORTED_ABIS[0];
            }
        }
        return "arm64-v8a";
    }

    public static void showDownloadToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}
