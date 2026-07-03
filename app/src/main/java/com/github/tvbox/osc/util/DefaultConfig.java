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

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
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
                        if (sortData.name.equals(cate)
                                || sortData.name.contains(cate)
                                || cate.contains(sortData.name)) {
                            if (sortData.filters == null)
                                sortData.filters = new ArrayList<>();
                            if (!data.contains(sortData)) {
                                data.add(sortData);
                            }
                        }
                    }
                }
                if (data.isEmpty()) {
                    for (MovieSort.SortData sortData : list) {
                        if (sortData.filters == null)
                            sortData.filters = new ArrayList<>();
                        data.add(sortData);
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
        if (TextUtils.isEmpty(urlOri) || !urlOri.startsWith("proxy://")) {
            return urlOri;
        }
        ControlManager.get().startServer();
        String address = ControlManager.get().getAddress(true);
        if (TextUtils.isEmpty(address)) {
            return urlOri;
        }
        return urlOri.replace("proxy://", address + "proxy?");
    }

    public static void normalizeSpiderPlayResult(JSONObject result) throws org.json.JSONException {
        if (result == null) {
            return;
        }
        if (TextUtils.isEmpty(result.optString("url", ""))) {
            String playUrl = result.optString("playUrl", "");
            if (!TextUtils.isEmpty(playUrl)) {
                result.put("url", playUrl);
            }
        }
        Object urlObj = result.opt("url");
        if (urlObj instanceof JSONObject) {
            JSONObject urlJson = (JSONObject) urlObj;
            String nested = urlJson.optString("url", urlJson.optString("src", ""));
            if (!TextUtils.isEmpty(nested)) {
                result.put("url", nested);
            }
        }
        if (!TextUtils.isEmpty(result.optString("url", ""))) {
            return;
        }
        String[] urlKeys = {"mediaUrl", "videoUrl", "link", "src"};
        for (String key : urlKeys) {
            String value = result.optString(key, "");
            if (!TextUtils.isEmpty(value)) {
                result.put("url", value);
                return;
            }
        }
        String[] nestedKeys = {"data", "play", "result"};
        for (String nestedKey : nestedKeys) {
            JSONObject nested = result.optJSONObject(nestedKey);
            if (nested == null) {
                continue;
            }
            mergePlayField(result, nested, "url");
            mergePlayField(result, nested, "playUrl");
            mergePlayField(result, nested, "parse");
            mergePlayField(result, nested, "jx");
            mergePlayField(result, nested, "flag");
            mergePlayField(result, nested, "header");
            if (!TextUtils.isEmpty(result.optString("url", ""))) {
                return;
            }
        }
    }

    private static void mergePlayField(JSONObject target, JSONObject source, String key) throws org.json.JSONException {
        if (target.has(key) || !source.has(key)) {
            return;
        }
        target.put(key, source.get(key));
    }

    public static boolean isPlayParseEnabled(JSONObject info) {
        if (info == null) {
            return true;
        }
        if (!info.has("parse")) {
            String url = info.optString("url", "");
            if (isVideoFormat(url) || url.startsWith("proxy://") || url.startsWith("http")) {
                return false;
            }
            return true;
        }
        Object value = info.opt("parse");
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        String text = String.valueOf(value);
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    public static boolean isPlayJxEnabled(JSONObject info) {
        if (info == null || !info.has("jx")) {
            return false;
        }
        Object value = info.opt("jx");
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof Number) {
            return ((Number) value).intValue() != 0;
        }
        String text = String.valueOf(value);
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    public static HashMap<String, String> parsePlayHeaders(JSONObject info) {
        if (info == null || !info.has("header")) {
            return null;
        }
        HashMap<String, String> headers = null;
        try {
            JSONObject hds = info.optJSONObject("header");
            if (hds == null) {
                String raw = info.optString("header", "");
                if (!TextUtils.isEmpty(raw)) {
                    hds = new JSONObject(raw);
                }
            }
            if (hds != null) {
                Iterator<String> keys = hds.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    if (headers == null) {
                        headers = new HashMap<>();
                    }
                    headers.put(key, hds.optString(key, ""));
                }
            }
        } catch (Throwable ignored) {
        }
        return headers;
    }

    /** 弹幕等非关键服务失败时不应阻断视频播放 */
    public static boolean isNonFatalPlayMessage(String msg) {
        if (TextUtils.isEmpty(msg)) {
            return false;
        }
        String lower = msg.toLowerCase();
        return msg.contains("弹幕") || lower.contains("danmu") || lower.contains("danmaku");
    }

    public static boolean hasPlayableUrlInJson(String json) {
        if (TextUtils.isEmpty(json)) {
            return false;
        }
        String trimmed = json.trim();
        if (!trimmed.startsWith("{") && !trimmed.startsWith("[")) {
            return trimmed.startsWith("http") || trimmed.startsWith("proxy://")
                    || isVideoFormat(trimmed);
        }
        try {
            if (trimmed.startsWith("[")) {
                org.json.JSONArray arr = new org.json.JSONArray(trimmed);
                for (int i = 0; i < arr.length(); i++) {
                    if (hasPlayableUrlInJson(arr.getJSONObject(i).toString())) {
                        return true;
                    }
                }
                return false;
            }
            JSONObject obj = new JSONObject(trimmed);
            normalizeSpiderPlayResult(obj);
            return !TextUtils.isEmpty(obj.optString("url", ""));
        } catch (Throwable ignored) {
            return !TextUtils.isEmpty(extractPlayUrlFromJson(trimmed));
        }
    }

    public static String extractPlayUrlFromJson(String json) {
        if (TextUtils.isEmpty(json)) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("(proxy://[^\"'\\s\\\\]+|https?://[^\"'\\s\\\\]+)")
                .matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    public static JSONObject buildPlayResultFromExtractedUrl(String rawUrl, String playFlag, String progressKey)
            throws org.json.JSONException {
        String mediaUrl = extractPlayUrlFromJson(rawUrl);
        if (TextUtils.isEmpty(mediaUrl)) {
            return null;
        }
        return buildPlayResultFromRawUrl(mediaUrl, playFlag, progressKey);
    }

    public static JSONObject buildDirectPlayResult(String episodeUrl, String playFlag, String progressKey)
            throws org.json.JSONException {
        JSONObject result = new JSONObject();
        String mediaUrl = checkReplaceProxy(episodeUrl);
        result.put("url", mediaUrl);
        result.put("key", episodeUrl);
        result.put("proKey", progressKey);
        result.put("parse", 0);
        result.put("flag", playFlag);
        return result;
    }

    public static JSONObject buildParsePlayResult(String episodeUrl, String playFlag, String progressKey)
            throws org.json.JSONException {
        JSONObject result = new JSONObject();
        String mediaUrl = checkReplaceProxy(episodeUrl);
        result.put("url", mediaUrl);
        result.put("key", episodeUrl);
        result.put("proKey", progressKey);
        result.put("parse", 1);
        result.put("playUrl", "");
        result.put("flag", playFlag);
        return result;
    }

    public static JSONObject buildPlayResultFromRawUrl(String rawUrl, String playFlag, String progressKey)
            throws org.json.JSONException {
        String mediaUrl = checkReplaceProxy(rawUrl.trim());
        if (isVideoFormat(mediaUrl) || mediaUrl.startsWith("proxy://")) {
            return buildDirectPlayResult(mediaUrl, playFlag, progressKey);
        }
        if (mediaUrl.startsWith("http")) {
            return buildParsePlayResult(mediaUrl, playFlag, progressKey);
        }
        return null;
    }

    public static boolean isDriveAuthMessage(String msg) {
        if (TextUtils.isEmpty(msg)) {
            return false;
        }
        String text = msg.toLowerCase();
        return msg.contains("扫码") || msg.contains("登录") || msg.contains("授权")
                || msg.contains("网盘") || text.contains("token") || text.contains("cookie")
                || msg.contains("QrCode") || msg.contains("二维码");
    }

    public static boolean isDriveAuthUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String lower = url.toLowerCase();
        if (!lower.contains("/proxy") && !lower.contains("proxy?")) {
            return false;
        }
        return lower.contains("qrcode") || lower.contains("login") || lower.contains("oauth")
                || lower.contains("type=ali") || lower.contains("type=quark") || lower.contains("type=uc")
                || lower.contains("type=115") || lower.contains("type=thunder")
                || lower.contains("do=ali") || lower.contains("do=quark") || lower.contains("do=pan");
    }

    public static String buildDriveAuthUrl(String driveType) {
        ControlManager.get().startServer();
        return DriveAuthHelper.buildProxyWebUrl(driveType, null);
    }
}