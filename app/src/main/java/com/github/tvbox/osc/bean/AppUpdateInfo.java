package com.github.tvbox.osc.bean;

/**
 * 远程 update.json 描述文件。
 */
public class AppUpdateInfo {
    public int versionCode;
    public String versionName;
    public String apkUrl;
    public boolean forceUpdate;
    public String changelog;

    public boolean isNewerThan(int localVersionCode) {
        return versionCode > localVersionCode;
    }

    public boolean isValid() {
        return versionCode > 0 && apkUrl != null && !apkUrl.trim().isEmpty();
    }
}
