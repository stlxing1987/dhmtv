package com.github.tvbox.osc.util;

import android.app.Activity;
import android.content.Context;
import android.widget.Toast;

import com.github.tvbox.osc.R;
import com.orhanobut.hawk.Hawk;

import java.io.File;

public class SettingUiHelper {

    public static final int[] WALLPAPER_RES = {
            R.drawable.app_bg,
            R.drawable.wallpaper_bg_1,
            R.drawable.wallpaper_bg_2,
            R.drawable.wallpaper_bg_3
    };

    public static String getHomePrefName(int type) {
        return type == 1 ? "直播" : "点播";
    }

    public static String getHomeTabName(int type) {
        return type == 1 ? "简洁" : "默认";
    }

    public static String getCacheDaysName(int days) {
        return days <= 0 ? "不缓存" : days + "天";
    }

    public static String getHistoryCountName(int count) {
        return count + "条";
    }

    public static String getHomeGridColsName(int cols) {
        return cols + "列";
    }

    public static String getUiModeName(int mode) {
        return "电视";
    }

    public static final int MODE_TV = DeviceHelper.MODE_TV;

    public static String getDecodeName(String codec) {
        if (codec == null || codec.isEmpty()) {
            return "硬解码";
        }
        if (codec.contains("硬") || codec.toLowerCase().contains("hw")) {
            return "硬解码";
        }
        if (codec.contains("软") || codec.toLowerCase().contains("sw")) {
            return "软解码";
        }
        return codec;
    }

    public static boolean isConfigCacheValid(File cache) {
        if (cache == null || !cache.exists()) {
            return false;
        }
        int days = Hawk.get(HawkConfig.CONFIG_CACHE_DAYS, 1);
        if (days <= 0) {
            return false;
        }
        return System.currentTimeMillis() - cache.lastModified() <= days * 24L * 60L * 60L * 1000L;
    }

    public static int getWallpaperIndex() {
        return Hawk.get(HawkConfig.HOME_WALLPAPER, 0);
    }

    public static void nextWallpaper(Activity activity) {
        int next = (getWallpaperIndex() + 1) % WALLPAPER_RES.length;
        Hawk.put(HawkConfig.HOME_WALLPAPER, next);
        activity.getWindow().setBackgroundDrawableResource(WALLPAPER_RES[next]);
    }

    public static void resetWallpaper(Activity activity) {
        Hawk.put(HawkConfig.HOME_WALLPAPER, 0);
        activity.getWindow().setBackgroundDrawableResource(WALLPAPER_RES[0]);
    }

    public static void clearAppCache(Context context) {
        try {
            deleteDir(context.getCacheDir());
            deleteDir(context.getExternalCacheDir());
            Toast.makeText(context, "缓存已清空", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(context, "清空缓存失败", Toast.LENGTH_SHORT).show();
        }
    }

    public static void clearThunderCache(Context context) {
        try {
            deleteDir(new File(context.getFilesDir(), "thunder"));
            Toast.makeText(context, "迅雷/荐片缓存已清空", Toast.LENGTH_SHORT).show();
        } catch (Throwable t) {
            Toast.makeText(context, "清空失败", Toast.LENGTH_SHORT).show();
        }
    }

    public static void resetApp(Activity activity) {
        try {
            Hawk.deleteAll();
            activity.deleteDatabase("tvbox");
        } catch (Throwable ignored) {
        }
        ConfigDialogHelper.restartApp(activity, false);
    }

    private static void deleteDir(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        if (dir.isDirectory()) {
            File[] children = dir.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteDir(child);
                }
            }
        }
        dir.delete();
    }
}
