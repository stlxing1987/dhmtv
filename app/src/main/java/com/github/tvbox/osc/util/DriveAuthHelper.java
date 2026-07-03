package com.github.tvbox.osc.util;

import android.text.TextUtils;

import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.server.RemoteServer;
import com.github.tvbox.osc.viewmodel.SourceViewModel;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class DriveAuthHelper {

    public static final String[] DRIVE_NAMES = {"阿里云盘", "夸克网盘", "UC网盘", "115网盘", "迅雷网盘"};
    private static final String[] DRIVE_DO = {"ali", "quark", "uc", "115", "xunlei"};
    private static final String[][] DRIVE_KEYWORDS = {
            {"阿里", "ali", "aliyun"},
            {"夸克", "quark"},
            {"uc", "UC"},
            {"115"},
            {"迅雷", "xunlei", "thunder"}
    };
    private static final String[] PROXY_TYPES = {null, "qrcode", "login", "qr", "scan", "token", "code"};

    public static class AuthPage {
        public String html;
        public String mime = "text/html";
        public String baseUrl;
        public String loadUrl;
        public String error;
        public byte[] imageBytes;
        public String imageMime;

        public boolean hasContent() {
            return !TextUtils.isEmpty(html) || !TextUtils.isEmpty(loadUrl)
                    || (imageBytes != null && imageBytes.length > 0);
        }
    }

    public interface Callback {
        void onResult(AuthPage page);
    }

    public static void resolve(int driveIndex, Callback callback) {
        SourceViewModel.spThreadPool.execute(() -> {
            AuthPage page;
            try {
                page = resolveSync(driveIndex);
            } catch (Throwable th) {
                th.printStackTrace();
                page = new AuthPage();
                page.error = "获取授权页失败：" + th.getMessage();
            }
            if (callback != null) {
                callback.onResult(page);
            }
        });
    }

    public static AuthPage resolveSync(int driveIndex) {
        if (driveIndex < 0 || driveIndex >= DRIVE_DO.length) {
            AuthPage page = new AuthPage();
            page.error = "无效的网盘类型";
            return page;
        }
        ControlManager.get().startServer();
        if (!isJarProxyReady()) {
            AuthPage page = nullSafePage(resolveFromSpider(driveIndex));
            if (page.hasContent()) {
                return page;
            }
            page.error = buildJarNotReadyMessage();
            return page;
        }
        String driveDo = DRIVE_DO[driveIndex];
        List<String> doList = new ArrayList<>();
        doList.add(driveDo);
        if ("xunlei".equals(driveDo)) {
            doList.add("thunder");
        }
        if ("115".equals(driveDo)) {
            doList.add("115pan");
        }
        for (String doParam : doList) {
            for (String type : PROXY_TYPES) {
                AuthPage page = nullSafePage(fetchProxy(doParam, type));
                if (page.hasContent()) {
                    page.baseUrl = ControlManager.get().getAddress(false);
                    return page;
                }
            }
        }
        AuthPage spiderPage = nullSafePage(resolveFromSpider(driveIndex));
        if (spiderPage.hasContent()) {
            return spiderPage;
        }
        spiderPage.error = "未能获取「" + DRIVE_NAMES[driveIndex] + "」授权页。\n"
                + "请确认已切换饭太硬线路且 JAR 加载成功；\n"
                + "也可在首页切换「我的网盘」源进行扫码授权。";
        return spiderPage;
    }

    public static boolean isJarProxyReady() {
        Map<String, String> params = new HashMap<>();
        params.put("do", "ck");
        Object[] rs = ApiConfig.get().proxyLocal(params);
        if (rs == null || rs.length < 3) {
            return false;
        }
        try {
            int code = (int) rs[0];
            if (code != 200) {
                closeQuietly(rs[2]);
                return false;
            }
            byte[] body = readBytes((InputStream) rs[2]);
            String text = body == null ? "" : new String(body, StandardCharsets.UTF_8);
            return "ok".equalsIgnoreCase(text.trim());
        } catch (Throwable th) {
            return false;
        }
    }

    public static String getLanAddress() {
        ControlManager.get().startServer();
        return normalizeBaseUrl(ControlManager.get().getAddress(false));
    }

    public static String getLocalProxyAddress() {
        ControlManager.get().startServer();
        return normalizeBaseUrl(ControlManager.get().getAddress(true));
    }

    public static String buildProxyWebUrl(int driveIndex) {
        if (driveIndex < 0 || driveIndex >= DRIVE_DO.length) {
            return "";
        }
        return buildProxyWebUrl(DRIVE_DO[driveIndex], null);
    }

    public static String buildProxyWebUrl(String driveDo, String type) {
        String local = getLocalProxyAddress();
        if (TextUtils.isEmpty(local)) {
            return "";
        }
        StringBuilder url = new StringBuilder(local);
        if (!local.endsWith("/")) {
            url.append("/");
        }
        url.append("proxy?do=").append(driveDo);
        appendHostQuery(url);
        if (!TextUtils.isEmpty(type)) {
            url.append("&type=").append(type);
        }
        return url.toString();
    }

    public static String buildAuthTip(int driveIndex) {
        String lan = getLanAddress();
        String driveName = driveIndex >= 0 && driveIndex < DRIVE_NAMES.length
                ? DRIVE_NAMES[driveIndex] : "网盘";
        String appHint = driveIndex == 1 ? "请使用「夸克App」扫描，勿用微信/浏览器"
                : "请使用对应网盘App扫描，勿用微信/浏览器";
        if (TextUtils.isEmpty(lan) || "http://0.0.0.0".equals(lan.replaceAll(":\\d+/?$", ""))) {
            return appHint + "；请确认电视已连接 WiFi/有线网络";
        }
        return appHint + "；手机需与电视同一局域网\n电视地址：" + lan;
    }

    private static void appendHostParams(Map<String, String> params) {
        String lan = getLanAddress();
        if (TextUtils.isEmpty(lan)) {
            return;
        }
        params.put("host", lan);
        params.put("url", lan);
        try {
            String noScheme = lan.replace("http://", "").replace("https://", "");
            int slash = noScheme.indexOf('/');
            if (slash >= 0) {
                noScheme = noScheme.substring(0, slash);
            }
            int colon = noScheme.indexOf(':');
            if (colon > 0) {
                params.put("ip", noScheme.substring(0, colon));
                params.put("port", noScheme.substring(colon + 1));
            } else if (!noScheme.isEmpty()) {
                params.put("ip", noScheme);
            }
        } catch (Throwable ignored) {
        }
    }

    private static void appendHostQuery(StringBuilder url) {
        String lan = getLanAddress();
        if (TextUtils.isEmpty(lan)) {
            return;
        }
        try {
            url.append("&host=").append(URLEncoder.encode(lan, "UTF-8"));
            url.append("&url=").append(URLEncoder.encode(lan, "UTF-8"));
        } catch (UnsupportedEncodingException ignored) {
            url.append("&host=").append(lan);
        }
    }

    private static String normalizeBaseUrl(String address) {
        if (TextUtils.isEmpty(address)) {
            return "";
        }
        return address.endsWith("/") ? address.substring(0, address.length() - 1) : address;
    }

    private static String replaceLocalHost(String content) {
        if (TextUtils.isEmpty(content)) {
            return content;
        }
        String lan = getLanAddress();
        if (TextUtils.isEmpty(lan)) {
            return content;
        }
        String port = "";
        try {
            String noScheme = lan.replace("http://", "").replace("https://", "");
            int colon = noScheme.indexOf(':');
            if (colon > 0) {
                port = noScheme.substring(colon + 1);
            }
        } catch (Throwable ignored) {
        }
        String replaced = content
                .replace("http://127.0.0.1:" + RemoteServer.serverPort, lan)
                .replace("http://127.0.0.1", lan)
                .replace("127.0.0.1:" + RemoteServer.serverPort, lan.replace("http://", "").replace("https://", ""))
                .replace("127.0.0.1", lan.replace("http://", "").replace("https://", ""));
        if (!TextUtils.isEmpty(port)) {
            replaced = replaced.replace("localhost:" + port, lan.replace("http://", "").replace("https://", ""));
        }
        replaced = replaced.replace("localhost", lan.replace("http://", "").replace("https://", ""));
        return replaced;
    }

    private static AuthPage fetchProxy(String driveDo, String type) {
        Map<String, String> params = new HashMap<>();
        params.put("do", driveDo);
        if (!TextUtils.isEmpty(type)) {
            params.put("type", type);
        }
        appendHostParams(params);
        return nullSafePage(toAuthPage(ApiConfig.get().proxyLocal(params)));
    }

    public static AuthPage resolveUrl(String url) {
        return fetchProxyUrl(url);
    }

    private static AuthPage fetchProxyUrl(String url) {
        if (TextUtils.isEmpty(url)) {
            return null;
        }
        String resolved = DefaultConfig.checkReplaceProxy(url);
        if (resolved.contains("/proxy?")) {
            int q = resolved.indexOf('?');
            if (q >= 0) {
                Map<String, String> params = parseQuery(resolved.substring(q + 1));
                if (params.containsKey("do")) {
                    appendHostParams(params);
                    return toAuthPage(ApiConfig.get().proxyLocal(params));
                }
            }
            AuthPage page = new AuthPage();
            page.loadUrl = appendHostToProxyUrl(resolved);
            page.baseUrl = getLocalProxyAddress();
            return page;
        }
        AuthPage page = new AuthPage();
        page.loadUrl = resolved;
        page.baseUrl = resolved;
        return page;
    }

    private static String appendHostToProxyUrl(String proxyUrl) {
        if (TextUtils.isEmpty(proxyUrl) || proxyUrl.contains("host=")) {
            return proxyUrl;
        }
        StringBuilder url = new StringBuilder(proxyUrl);
        appendHostQuery(url);
        return url.toString();
    }

    private static AuthPage toAuthPage(Object[] rs) {
        if (rs == null || rs.length < 3) {
            return null;
        }
        InputStream stream = null;
        try {
            int code = (int) rs[0];
            String mime = rs[1] == null ? "" : String.valueOf(rs[1]);
            stream = (InputStream) rs[2];
            if (code != 200 || stream == null) {
                return null;
            }
            byte[] body = readBytes(stream);
            if (body == null || body.length == 0) {
                return null;
            }
            AuthPage page = new AuthPage();
            page.mime = mime;
            page.baseUrl = ControlManager.get().getAddress(false);
            if (mime.contains("html") || looksLikeHtml(body)) {
                page.html = replaceLocalHost(new String(body, StandardCharsets.UTF_8));
                return page;
            }
            if (mime.contains("json")) {
                String json = new String(body, StandardCharsets.UTF_8);
                String url = extractUrlFromJson(json);
                if (!TextUtils.isEmpty(url)) {
                    return fetchProxyUrl(url);
                }
                page.html = wrapMessageHtml(json);
                return page;
            }
            if (mime.contains("image") || mime.contains("png") || mime.contains("jpeg")) {
                page.imageBytes = body;
                page.imageMime = mime.contains("/") ? mime.split(";")[0].trim() : "image/png";
                return page;
            }
            String text = replaceLocalHost(new String(body, StandardCharsets.UTF_8));
            if (text.contains("<html") || text.contains("<!DOCTYPE")) {
                page.html = text;
                return page;
            }
            if ("ok".equalsIgnoreCase(text.trim())) {
                return null;
            }
            page.html = wrapMessageHtml(text);
            return page;
        } catch (Throwable th) {
            return null;
        } finally {
            closeQuietly(stream);
        }
    }

    private static AuthPage nullSafePage(AuthPage page) {
        return page != null ? page : new AuthPage();
    }

    private static AuthPage resolveFromSpider(int driveIndex) {
        SourceBean source = findDriveConfigSource();
        if (source == null) {
            return null;
        }
        try {
            Spider sp = ApiConfig.get().getCSP(source);
            String authTarget = findAuthTarget(sp.homeContent(true), driveIndex);
            if (TextUtils.isEmpty(authTarget)) {
                authTarget = findAuthTarget(sp.homeContent(false), driveIndex);
            }
            if (TextUtils.isEmpty(authTarget)) {
                authTarget = findAuthFromCategories(sp, driveIndex);
            }
            if (TextUtils.isEmpty(authTarget)) {
                return null;
            }
            if (authTarget.startsWith("proxy://") || authTarget.contains("/proxy?") || authTarget.startsWith("http")) {
                return fetchProxyUrl(authTarget);
            }
            List<String> ids = new ArrayList<>();
            ids.add(authTarget);
            String detail = sp.detailContent(ids);
            String url = findFirstUrl(detail);
            if (!TextUtils.isEmpty(url)) {
                return fetchProxyUrl(url);
            }
            String play = sp.playerContent("", authTarget, ApiConfig.get().getVipParseFlags());
            url = findPlayUrl(play);
            if (!TextUtils.isEmpty(url)) {
                return fetchProxyUrl(url);
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return null;
    }

    private static String findAuthFromCategories(Spider sp, int driveIndex) {
        try {
            String home = sp.homeContent(true);
            JSONArray classes = extractClasses(home);
            if (classes == null) {
                return null;
            }
            for (int i = 0; i < classes.length(); i++) {
                JSONObject cls = classes.optJSONObject(i);
                if (cls == null) {
                    continue;
                }
                String typeName = cls.optString("type_name", cls.optString("name", ""));
                if (!matchesDrive(typeName, driveIndex)) {
                    continue;
                }
                String typeId = cls.optString("type_id", cls.optString("id", String.valueOf(i)));
                String category = sp.categoryContent(typeId, "1", true, new HashMap<>());
                String target = findAuthTarget(category, driveIndex);
                if (!TextUtils.isEmpty(target)) {
                    return target;
                }
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        return null;
    }

    private static JSONArray extractClasses(String json) {
        if (TextUtils.isEmpty(json)) {
            return null;
        }
        try {
            JSONObject obj = new JSONObject(json);
            JSONArray classes = obj.optJSONArray("class");
            if (classes == null) {
                classes = obj.optJSONArray("categories");
            }
            return classes;
        } catch (Throwable th) {
            return null;
        }
    }

    private static String findAuthTarget(String json, int driveIndex) {
        if (TextUtils.isEmpty(json)) {
            return null;
        }
        try {
            JSONObject obj = new JSONObject(json);
            JSONArray list = obj.optJSONArray("list");
            if (list == null) {
                return null;
            }
            String fallback = null;
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String name = item.optString("name", item.optString("vod_name", ""));
                String url = item.optString("url", item.optString("vod_url", ""));
                String id = item.optString("id", item.optString("vod_id", ""));
                if (isAuthEntry(name)) {
                    if (matchesDrive(name, driveIndex)) {
                        return !TextUtils.isEmpty(url) ? url : id;
                    }
                    if (fallback == null && matchesDrive(name, driveIndex)) {
                        fallback = !TextUtils.isEmpty(url) ? url : id;
                    }
                }
                if (matchesDrive(name, driveIndex) && (isAuthEntry(name) || name.contains("Token") || name.contains("token"))) {
                    return !TextUtils.isEmpty(url) ? url : id;
                }
            }
            for (int i = 0; i < list.length(); i++) {
                JSONObject item = list.optJSONObject(i);
                if (item == null) {
                    continue;
                }
                String name = item.optString("name", item.optString("vod_name", ""));
                if (!matchesDrive(name, driveIndex)) {
                    continue;
                }
                String url = item.optString("url", item.optString("vod_url", ""));
                String id = item.optString("id", item.optString("vod_id", ""));
                return !TextUtils.isEmpty(url) ? url : id;
            }
            return fallback;
        } catch (Throwable th) {
            return findFirstUrl(json);
        }
    }

    private static boolean isAuthEntry(String name) {
        if (TextUtils.isEmpty(name)) {
            return false;
        }
        return name.contains("扫码") || name.contains("登录") || name.contains("授权")
                || name.contains("Qr") || name.contains("QR") || name.contains("Token")
                || name.contains("token") || name.contains("Open");
    }

    private static boolean matchesDrive(String text, int driveIndex) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        for (String keyword : DRIVE_KEYWORDS[driveIndex]) {
            if (text.contains(keyword) || lower.contains(keyword.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static SourceBean findDriveConfigSource() {
        SourceBean fallback = null;
        for (SourceBean sb : ApiConfig.get().getSourceBeanList()) {
            if (sb.getType() != 3) {
                continue;
            }
            String name = sb.getName() == null ? "" : sb.getName();
            String api = sb.getApi() == null ? "" : sb.getApi();
            String key = sb.getKey() == null ? "" : sb.getKey();
            if (name.contains("网盘及弹幕") || name.contains("配置中心") || name.contains("Token")
                    || api.contains("Token") || api.contains("PanConfig")) {
                return sb;
            }
            if (name.contains("网盘") || name.contains("我的网盘") || key.equalsIgnoreCase("MDrive")
                    || api.contains("MyDrive")) {
                fallback = sb;
            }
        }
        return fallback;
    }

    private static String findFirstUrl(String json) {
        if (TextUtils.isEmpty(json)) {
            return null;
        }
        if (json.contains("proxy://") || json.contains("/proxy?")) {
            int start = json.indexOf("proxy://");
            if (start < 0) {
                start = json.indexOf("http");
            }
            if (start >= 0) {
                int end = start;
                while (end < json.length()) {
                    char c = json.charAt(end);
                    if (c == '"' || c == '\'' || c == ' ' || c == '\n' || c == '\r') {
                        break;
                    }
                    end++;
                }
                return json.substring(start, end);
            }
        }
        try {
            JSONObject obj = new JSONObject(json);
            String url = obj.optString("url", "");
            if (!TextUtils.isEmpty(url)) {
                return url;
            }
            JSONArray list = obj.optJSONArray("list");
            if (list != null && list.length() > 0) {
                JSONObject item = list.optJSONObject(0);
                if (item != null) {
                    url = item.optString("url", item.optString("vod_url", ""));
                    if (!TextUtils.isEmpty(url)) {
                        return url;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static String findPlayUrl(String playJson) {
        if (TextUtils.isEmpty(playJson)) {
            return null;
        }
        try {
            String json = playJson.trim();
            if (json.startsWith("[")) {
                JSONArray arr = new JSONArray(json);
                if (arr.length() > 0) {
                    json = arr.getJSONObject(0).toString();
                }
            }
            JSONObject obj = new JSONObject(json);
            String url = obj.optString("url", "");
            if (!TextUtils.isEmpty(url)) {
                return url;
            }
        } catch (Throwable ignored) {
        }
        return findFirstUrl(playJson);
    }

    private static String extractUrlFromJson(String json) {
        return findPlayUrl(json);
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> params = new HashMap<>();
        if (TextUtils.isEmpty(query)) {
            return params;
        }
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                params.put(pair.substring(0, eq), pair.substring(eq + 1));
            } else if (!pair.isEmpty()) {
                params.put(pair, "");
            }
        }
        return params;
    }

    private static boolean looksLikeHtml(byte[] body) {
        if (body == null || body.length < 6) {
            return false;
        }
        String prefix = new String(body, 0, Math.min(body.length, 32), StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
        return prefix.contains("<html") || prefix.contains("<!doctype") || prefix.contains("<body");
    }

    private static String wrapImageHtml(String base64, String mime, String title) {
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/>"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"/>"
                + "<style>body{margin:0;background:#fff;color:#222;font-family:sans-serif;text-align:center;}"
                + "img{max-width:90vw;max-height:70vh;margin:24px auto;display:block;}"
                + "p{font-size:18px;line-height:1.6;padding:0 16px;}</style></head><body>"
                + "<img src=\"data:" + mime + ";base64," + base64 + "\" alt=\"qrcode\"/>"
                + "<p>请使用「" + title + "」App 扫描上方二维码完成授权</p>"
                + "</body></html>";
    }

    private static String wrapMessageHtml(String message) {
        String safe = TextUtils.htmlEncode(message);
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\"/>"
                + "<style>body{background:#fff;color:#222;font-family:sans-serif;padding:24px;line-height:1.6;}"
                + "pre{white-space:pre-wrap;word-break:break-all;}</style></head><body><pre>"
                + safe + "</pre></body></html>";
    }

    private static String buildJarNotReadyMessage() {
        return "本地爬虫 JAR 未就绪，无法打开网盘授权页。\n"
                + "请先在设置中切换饭太硬线路，等待首页加载完成后再试；\n"
                + "或在首页切换「我的网盘」源进行扫码。";
    }

    private static byte[] readBytes(InputStream stream) {
        if (stream == null) {
            return null;
        }
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        try {
            int len;
            while ((len = stream.read(buffer)) != -1) {
                bos.write(buffer, 0, len);
            }
            return bos.toByteArray();
        } catch (Throwable th) {
            return null;
        } finally {
            closeQuietly(stream);
        }
    }

    private static void closeQuietly(Object closeable) {
        if (closeable instanceof InputStream) {
            try {
                ((InputStream) closeable).close();
            } catch (Throwable ignored) {
            }
        }
    }
}
