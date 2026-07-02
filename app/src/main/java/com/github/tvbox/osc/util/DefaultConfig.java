package com.github.tvbox.osc.util;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.text.TextUtils;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.MovieSort;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.server.ControlManager;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author pj567
 * @date :2020/12/21
 * @description:
 */
public class DefaultConfig {

    public static List<MovieSort.SortData> adjustSort(String sourceKey, List<MovieSort.SortData> list, boolean withMy) {
        List<MovieSort.SortData> data = new ArrayList<>();
        if (sourceKey != null) {
            SourceBean sb = ApiConfig.get().getSource(sourceKey);
            ArrayList<String> categories = sb.getCategories();
            if (!categories.isEmpty()) {
                for (String cate : categories) {
                    for (MovieSort.SortData sortData : list) {
                        if (sortData.name.equals(cate)) {
                            if (sortData.filters == null)
                                sortData.filters = new ArrayList<>();
                            data.add(sortData);
                        }
                    }
                }
            } else {
                for (MovieSort.SortData sortData : list) {
                    if (sortData.filters == null)
                        sortData.filters = new ArrayList<>();
                    data.add(sortData);
                }
            }
        }
        if (withMy) {
            MovieSort.SortData my = new MovieSort.SortData("my0", "我的");
            my.sort = Integer.MIN_VALUE;
            data.add(my);
        }
        Collections.sort(data);
        return data;
    }

    public static int getAppVersionCode(Context mContext) {
        //包管理操作管理类
        PackageManager pm = mContext.getPackageManager();
        try {
            PackageInfo packageInfo = pm.getPackageInfo(mContext.getPackageName(), 0);
            return packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return -1;
    }

    public static String getAppVersionName(Context mContext) {
        //包管理操作管理类
        PackageManager pm = mContext.getPackageManager();
        try {
            PackageInfo packageInfo = pm.getPackageInfo(mContext.getPackageName(), 0);
            return packageInfo.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return "";
    }

    /**
     * 后缀
     *
     * @param name
     * @return
     */
    public static String getFileSuffix(String name) {
        if (TextUtils.isEmpty(name)) {
            return "";
        }
        int endP = name.lastIndexOf(".");
        return endP > -1 ? name.substring(endP) : "";
    }

    /**
     * 获取文件的前缀
     *
     * @param fileName
     * @return
     */
    public static String getFilePrefixName(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return "";
        }
        int start = fileName.lastIndexOf(".");
        return start > -1 ? fileName.substring(0, start) : fileName;
    }

    /** CMS 采集源：0=XML，1=JSON；根据 api 地址自动纠正 type */
    public static int resolveSourceType(int configuredType, String api) {
        if (TextUtils.isEmpty(api)) {
            return configuredType;
        }
        String lower = api.toLowerCase();
        if (lower.contains("/xml") || lower.endsWith(".xml") || lower.contains("at/xml")) {
            return 0;
        }
        if (lower.contains("/json") || lower.endsWith(".json") || lower.contains("at/json")) {
            return 1;
        }
        return configuredType;
    }

    public static boolean looksLikeXml(String body) {
        if (TextUtils.isEmpty(body)) {
            return false;
        }
        String trimmed = body.trim();
        return trimmed.startsWith("<") || trimmed.contains("<?xml");
    }

    public static boolean isWafBlocked(String body) {
        if (TextUtils.isEmpty(body)) {
            return false;
        }
        return body.contains("GOEDGE_WAF") || body.contains("Verify Yourself")
                || body.contains("WAF/VERIFY") || body.contains("ui-captcha");
    }

    /** 修正已知失效/被 WAF 拦截的 CMS 采集地址 */
    public static String normalizeCmsApi(String api) {
        if (TextUtils.isEmpty(api)) {
            return api;
        }
        String normalized = api.trim();
        if (normalized.contains("caiji.dyttzyapi.com")) {
            normalized = normalized.replace("http://caiji.dyttzyapi.com", "https://www.dyttzy.tv");
            normalized = normalized.replace("https://caiji.dyttzyapi.com", "https://www.dyttzy.tv");
        }
        if (normalized.startsWith("http://") && normalized.contains("api.php/provide/vod")) {
            normalized = "https://" + normalized.substring(7);
        }
        return normalized;
    }

    private static final Pattern dyttShareM3u8Pattern = Pattern.compile("const url\\s*=\\s*\"([^\"]+)\"");

    public static boolean isDyttShareUrl(String url) {
        return !TextUtils.isEmpty(url) && url.contains("/share/");
    }

    public static boolean isDyttCdnUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.contains("dytt-see.com") || lower.contains("dytt-tvs.com")
                || lower.contains("dytt-cine.com") || lower.contains("vip.dytt");
    }

    /** 电影天堂 m3u8 直链通常缺少 sign，需从 share 页解析 */
    public static boolean needsDyttSign(String url) {
        return isDyttCdnUrl(url) && url.contains(".m3u8") && !url.contains("sign=");
    }

    public static String parseDyttShareM3u8(String html, String shareUrl) {
        if (TextUtils.isEmpty(html)) {
            return null;
        }
        Matcher matcher = dyttShareM3u8Pattern.matcher(html);
        if (!matcher.find()) {
            return null;
        }
        String path = matcher.group(1).trim();
        if (path.startsWith("http")) {
            return path;
        }
        try {
            java.net.URL base = new java.net.URL(shareUrl);
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            return base.getProtocol() + "://" + base.getHost() + path;
        } catch (Exception e) {
            return null;
        }
    }

    public static HashMap<String, String> buildDyttPlayHeaders(String refererUrl) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        if (!TextUtils.isEmpty(refererUrl) && refererUrl.contains("/share/")) {
            headers.put("Referer", refererUrl);
        } else if (!TextUtils.isEmpty(refererUrl) && isDyttCdnUrl(refererUrl)) {
            try {
                java.net.URL u = new java.net.URL(refererUrl);
                headers.put("Referer", u.getProtocol() + "://" + u.getHost() + "/");
            } catch (Exception e) {
                headers.put("Referer", "https://www.dyttzy.tv/");
            }
        } else {
            headers.put("Referer", "https://www.dyttzy.tv/");
        }
        return headers;
    }

    public static void mergeDyttPlayHeaders(HashMap<String, String> headers, String playUrl) {
        if (headers == null || TextUtils.isEmpty(playUrl) || !isDyttCdnUrl(playUrl)) {
            return;
        }
        HashMap<String, String> defaults = buildDyttPlayHeaders(playUrl);
        for (String key : defaults.keySet()) {
            if (!headers.containsKey(key)) {
                headers.put(key, defaults.get(key));
            }
        }
    }

    private static final Pattern snifferMatch = Pattern.compile("http((?!http).){26,}?\\.(m3u8|mp4)\\?.*|http((?!http).){26,}\\.(m3u8|mp4)|http((?!http).){26,}?/m3u8\\?pt=m3u8.*|http((?!http).)*?default\\.ixigua\\.com/.*|http((?!http).)*?cdn-tos[^\\?]*|http((?!http).)*?/obj/tos[^\\?]*|http.*?/player/m3u8play\\.php\\?url=.*|http.*?/player/.*?[pP]lay\\.php\\?url=.*|http.*?/playlist/m3u8/\\?vid=.*|http.*?\\.php\\?type=m3u8&.*|http.*?/download.aspx\\?.*|http.*?/api/up_api.php\\?.*|https.*?\\.66yk\\.cn.*|http((?!http).)*?netease\\.com/file/.*");

    public static boolean isVideoFormat(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        if (url.contains("=http") || url.contains("=https") || url.contains("=https%3a%2f") || url.contains("=http%3a%2f")) {
            return false;
        }
        if (isDirectVideoUrl(url)) {
            return true;
        }
        if (snifferMatch.matcher(url).find()) {
            if (url.contains("cdn-tos") && (url.contains(".js") || url.contains(".css"))) {
                return false;
            }
            return true;
        }
        return false;
    }

    public static boolean isDirectVideoUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String lower = url.toLowerCase();
        return lower.contains(".m3u8") || lower.contains(".mp4") || lower.contains(".flv")
                || lower.contains(".mkv") || lower.contains(".mov");
    }


    public static String safeJsonString(JsonObject obj, String key, String defaultVal) {
        try {
            if (obj.has(key))
                return obj.getAsJsonPrimitive(key).getAsString().trim();
            else
                return defaultVal;
        } catch (Throwable th) {
        }
        return defaultVal;
    }

    public static int safeJsonInt(JsonObject obj, String key, int defaultVal) {
        try {
            if (obj.has(key))
                return obj.getAsJsonPrimitive(key).getAsInt();
            else
                return defaultVal;
        } catch (Throwable th) {
        }
        return defaultVal;
    }

    public static ArrayList<String> safeJsonStringList(JsonObject obj, String key) {
        ArrayList<String> result = new ArrayList<>();
        try {
            if (obj.has(key)) {
                if (obj.get(key).isJsonObject()) {
                    result.add(obj.get(key).getAsString());
                } else {
                    for (JsonElement opt : obj.getAsJsonArray(key)) {
                        result.add(opt.getAsString());
                    }
                }
            }
        } catch (Throwable th) {
        }
        return result;
    }

    public static String safeJsonExt(JsonObject obj, String key, String defaultVal) {
        try {
            if (!obj.has(key)) {
                return defaultVal;
            }
            JsonElement el = obj.get(key);
            if (el.isJsonPrimitive()) {
                return el.getAsString().trim();
            }
            if (el.isJsonObject() || el.isJsonArray()) {
                return el.toString();
            }
        } catch (Throwable th) {
        }
        return defaultVal;
    }

    public static String checkReplaceProxy(String urlOri) {
        if (urlOri.startsWith("proxy://"))
            return urlOri.replace("proxy://", ControlManager.get().getAddress(true) + "proxy?");
        return urlOri;
    }
}