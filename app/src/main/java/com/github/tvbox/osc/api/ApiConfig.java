package com.github.tvbox.osc.api;

import android.app.Activity;
import android.os.Build;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Base64;

import com.github.catvod.crawler.JarLoader;
import com.github.catvod.crawler.Spider;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.LiveChannelGroup;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.LiveChannelItem;
import com.github.tvbox.osc.bean.LiveSourceBean;
import com.github.tvbox.osc.bean.ParseBean;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.ApiHistoryDialogAdapter;
import com.github.tvbox.osc.ui.dialog.ApiHistoryDialog;
import com.github.tvbox.osc.util.AdBlocker;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.MD5;
import com.github.tvbox.osc.util.SettingUiHelper;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.ui.activity.HomeActivity;
import com.github.tvbox.osc.util.AppManager;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;
import com.orhanobut.hawk.Hawk;

import org.json.JSONObject;

import com.github.tvbox.osc.util.ConfigTextExtractor;
import com.github.tvbox.osc.util.StoreConfigHelper;
import com.github.tvbox.osc.util.UrlHelper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author pj567
 * @date :2020/12/18
 * @description:
 */
public class ApiConfig {
    private static ApiConfig instance;
    private LinkedHashMap<String, SourceBean> sourceBeanList;
    private SourceBean mHomeSource;
    private ParseBean mDefaultParse;
    private List<LiveChannelGroup> liveChannelGroupList;
    private List<LiveSourceBean> liveSourceList;
    private List<LiveChannelGroup> inlineLiveCache;
    private List<ParseBean> parseBeanList;
    private List<String> vipParseFlags;
    private List<IJKCode> ijkCodes;
    private String spider = null;
    private List<UrlIndexItem> urlIndexList = new ArrayList<>();

    private SourceBean emptyHome = new SourceBean();

    private JarLoader jarLoader = new JarLoader();
    private int loadGeneration = 0;
    private boolean suppressUrlIndexDialog = false;

    public interface UrlIndexCallback {
        void onSuccess(List<UrlIndexItem> items);

        void onError(String msg);
    }

    public void setSuppressUrlIndexDialog(boolean suppress) {
        suppressUrlIndexDialog = suppress;
    }

    public void cancelPendingLoad() {
        loadGeneration++;
    }

    private boolean isLoadStale(int generation) {
        return generation != loadGeneration;
    }

    private ApiConfig() {
        sourceBeanList = new LinkedHashMap<>();
        liveChannelGroupList = new ArrayList<>();
        liveSourceList = new ArrayList<>();
        inlineLiveCache = new ArrayList<>();
        parseBeanList = new ArrayList<>();
    }

    public static ApiConfig get() {
        if (instance == null) {
            synchronized (ApiConfig.class) {
                if (instance == null) {
                    instance = new ApiConfig();
                }
            }
        }
        return instance;
    }

    public void loadConfig(boolean useCache, LoadConfigCallback callback) {
        loadConfig(useCache, callback, null);
    }

    public void loadConfig(boolean useCache, LoadConfigCallback callback, Activity activity) {
        final int generation = ++loadGeneration;
        String apiUrl = Hawk.get(HawkConfig.API_URL, "");
        if (apiUrl.isEmpty()) {
            callback.error("-1");
            return;
        }
        File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/" + MD5.encode(apiUrl));
        if (useCache && cache.exists() && SettingUiHelper.isConfigCacheValid(cache)) {
            try {
                handleConfigJson(apiUrl, ConfigTextExtractor.extract(readCache(cache)), useCache, callback, activity, generation);
                return;
            } catch (Throwable th) {
                th.printStackTrace();
            }
        }
        String apiFix = UrlHelper.normalizeRequestUrl(apiUrl);
        if (apiUrl.startsWith("clan://")) {
            apiFix = clanToAddress(apiUrl);
        }
        OkGo.<String>get(apiFix)
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        if (isLoadStale(generation)) {
                            return;
                        }
                        try {
                            String json = response.body();
                            try {
                                File cacheDir = cache.getParentFile();
                                if (!cacheDir.exists())
                                    cacheDir.mkdirs();
                                if (cache.exists())
                                    cache.delete();
                                FileOutputStream fos = new FileOutputStream(cache);
                                fos.write(json.getBytes("UTF-8"));
                                fos.flush();
                                fos.close();
                            } catch (Throwable th) {
                                th.printStackTrace();
                            }
                            handleConfigJson(apiUrl, json, useCache, callback, activity, generation);
                        } catch (Throwable th) {
                            th.printStackTrace();
                            if (!isLoadStale(generation)) {
                                String body = response.body();
                                if (UrlHelper.looksLikeHtml(body)) {
                                    callback.error("该地址返回网页而非配置文件\n请检查线路地址是否完整（如缺少 /tv 路径）");
                                } else {
                                    callback.error("解析配置失败");
                                }
                            }
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        super.onError(response);
                        if (isLoadStale(generation)) {
                            return;
                        }
                        if (cache.exists()) {
                            try {
                                handleConfigJson(apiUrl, ConfigTextExtractor.extract(readCache(cache)), useCache, callback, activity, generation);
                                return;
                            } catch (Throwable th) {
                                th.printStackTrace();
                            }
                        }
                        callback.error("拉取配置失败\n" + (response.getException() != null ? response.getException().getMessage() : ""));
                    }

                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        if (response.body() == null) {
                            return "";
                        }
                        byte[] bytes = response.body().bytes();
                        if (apiUrl.startsWith("clan")) {
                            String fixed = clanContentFix(clanToAddress(apiUrl), new String(bytes, "UTF-8"));
                            return ConfigTextExtractor.extract(fixed);
                        }
                        return ConfigTextExtractor.extract(bytes);
                    }
                });
    }

    private String readCache(File cache) throws Throwable {
        BufferedReader bReader = new BufferedReader(new InputStreamReader(new FileInputStream(cache), "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String s;
        while ((s = bReader.readLine()) != null) {
            sb.append(s).append("\n");
        }
        bReader.close();
        return sb.toString();
    }

    private String normalizeJson(String jsonStr) {
        if (jsonStr == null) {
            return "";
        }
        String json = jsonStr;
        if (json.startsWith("\uFEFF")) {
            json = json.substring(1);
        }
        return json.replace('\u201c', '"')
                .replace('\u201d', '"')
                .replace('\u2018', '\'')
                .replace('\u2019', '\'');
    }

    private JsonArray safeJsonArray(JsonObject obj, String key) {
        try {
            if (obj.has(key) && obj.get(key).isJsonArray()) {
                return obj.getAsJsonArray(key);
            }
        } catch (Throwable ignored) {
        }
        return new JsonArray();
    }

    private List<UrlIndexItem> parseUrlIndex(JsonObject infoJson) {
        List<UrlIndexItem> list = new ArrayList<>();
        JsonArray arr = null;
        if (infoJson.has("urls")) {
            arr = safeJsonArray(infoJson, "urls");
        } else if (infoJson.has("storeHouse")) {
            arr = safeJsonArray(infoJson, "storeHouse");
        }
        if (arr == null) {
            return list;
        }
        for (JsonElement el : arr) {
            try {
                JsonObject obj = el.getAsJsonObject();
                String url = DefaultConfig.safeJsonString(obj, "url", "");
                if (url.isEmpty()) {
                    url = DefaultConfig.safeJsonString(obj, "uri", "");
                }
                String name = DefaultConfig.safeJsonString(obj, "name", url);
                if (!url.isEmpty()) {
                    UrlIndexItem item = new UrlIndexItem();
                    item.name = name;
                    item.url = UrlHelper.normalizeRequestUrl(url);
                    list.add(item);
                }
            } catch (Throwable ignored) {
            }
        }
        return list;
    }

    private boolean isUrlIndex(JsonObject infoJson) {
        if (safeJsonArray(infoJson, "sites").size() > 0) {
            return false;
        }
        return !parseUrlIndex(infoJson).isEmpty();
    }

    private void handleConfigJson(String apiUrl, String jsonStr, boolean useCache, LoadConfigCallback callback, Activity activity, int generation) {
        if (isLoadStale(generation)) {
            return;
        }
        jsonStr = normalizeJson(jsonStr);
        JsonObject infoJson = new Gson().fromJson(jsonStr, JsonObject.class);
        if (isUrlIndex(infoJson)) {
            List<UrlIndexItem> items = parseUrlIndex(infoJson);
            cacheUrlIndex(apiUrl, items);
            UrlIndexItem selected = resolveUrlIndexItem(items);
            if (selected == null) {
                showUrlIndexDialog(activity, items, useCache, callback, generation);
                return;
            }
            applyUrlIndexSelection(selected);
            loadConfig(useCache, callback, activity);
            return;
        }
        urlIndexList.clear();
        parseJson(apiUrl, infoJson);
        if (!isLoadStale(generation)) {
            callback.success();
        }
    }

    private void cacheUrlIndex(String indexUrl, List<UrlIndexItem> items) {
        urlIndexList = new ArrayList<>(items);
        Hawk.put(HawkConfig.API_INDEX_URL, indexUrl);
        Hawk.put(HawkConfig.API_LINE_LIST, StoreConfigHelper.serializeLines(items));
    }

    private UrlIndexItem resolveUrlIndexItem(List<UrlIndexItem> items) {
        if (items == null || items.isEmpty()) {
            return null;
        }
        if (items.size() == 1) {
            return items.get(0);
        }
        String currentApi = Hawk.get(HawkConfig.API_URL, "");
        for (UrlIndexItem item : items) {
            if (currentApi.equals(item.url)) {
                return item;
            }
        }
        String savedLine = Hawk.get(HawkConfig.API_LINE_NAME, "");
        if (!TextUtils.isEmpty(savedLine)) {
            for (UrlIndexItem item : items) {
                if (savedLine.equals(item.name)) {
                    return item;
                }
            }
        }
        return null;
    }

    private void applyUrlIndexSelection(UrlIndexItem item) {
        Hawk.put(HawkConfig.API_URL, item.url);
        Hawk.put(HawkConfig.API_LINE_NAME, item.name);
    }

    public List<UrlIndexItem> getUrlIndexList() {
        if (urlIndexList != null && !urlIndexList.isEmpty()) {
            return new ArrayList<>(urlIndexList);
        }
        urlIndexList = StoreConfigHelper.parseLines(Hawk.get(HawkConfig.API_LINE_LIST, ""));
        return new ArrayList<>(urlIndexList);
    }

    public boolean hasLineSwitch() {
        return getUrlIndexList().size() > 1;
    }

    public String getCurrentLineName() {
        String name = Hawk.get(HawkConfig.API_LINE_NAME, "");
        if (!TextUtils.isEmpty(name)) {
            return name;
        }
        List<UrlIndexItem> items = getUrlIndexList();
        String currentApi = Hawk.get(HawkConfig.API_URL, "");
        for (UrlIndexItem item : items) {
            if (currentApi.equals(item.url)) {
                return item.name;
            }
        }
        return items.isEmpty() ? "" : items.get(0).name;
    }

    public void switchLine(UrlIndexItem item) {
        if (item == null) {
            return;
        }
        applyUrlIndexSelection(item);
    }

    public void fetchUrlIndexList(String storeUrl, UrlIndexCallback callback) {
        if (callback == null) {
            return;
        }
        if (TextUtils.isEmpty(storeUrl)) {
            postUrlIndexCallback(callback, new ArrayList<>(), null);
            return;
        }
        String indexUrl = Hawk.get(HawkConfig.API_INDEX_URL, "");
        String currentApi = Hawk.get(HawkConfig.API_URL, "");
        if (storeUrl.equals(indexUrl) || storeUrl.equals(currentApi)) {
            List<UrlIndexItem> cached = getUrlIndexList();
            if (!cached.isEmpty()) {
                postUrlIndexCallback(callback, cached, null);
                return;
            }
        }
        String apiFix = UrlHelper.normalizeRequestUrl(storeUrl);
        if (storeUrl.startsWith("clan://")) {
            apiFix = clanToAddress(storeUrl);
        }
        OkGo.<String>get(apiFix).execute(new AbsCallback<String>() {
            @Override
            public void onSuccess(Response<String> response) {
                try {
                    String json = response.body();
                    postUrlIndexCallback(callback, parseUrlIndexResponse(json, storeUrl), null);
                } catch (Throwable th) {
                    th.printStackTrace();
                    postUrlIndexCallback(callback, buildSingleLine(storeUrl), null);
                }
            }

            @Override
            public void onError(Response<String> response) {
                super.onError(response);
                postUrlIndexCallback(callback, buildSingleLine(storeUrl), null);
            }

            @Override
            public String convertResponse(okhttp3.Response response) throws Throwable {
                if (response.body() == null) {
                    return "";
                }
                byte[] bytes = response.body().bytes();
                if (storeUrl.startsWith("clan://")) {
                    String fixed = clanContentFix(clanToAddress(storeUrl), new String(bytes, "UTF-8"));
                    return ConfigTextExtractor.extract(fixed);
                }
                return ConfigTextExtractor.extract(bytes);
            }
        });
    }

    private List<UrlIndexItem> parseUrlIndexResponse(String jsonStr, String storeUrl) {
        jsonStr = normalizeJson(jsonStr);
        if (TextUtils.isEmpty(jsonStr)) {
            return buildSingleLine(storeUrl);
        }
        JsonObject infoJson = new Gson().fromJson(jsonStr, JsonObject.class);
        if (isUrlIndex(infoJson)) {
            return parseUrlIndex(infoJson);
        }
        return buildSingleLine(storeUrl);
    }

    private List<UrlIndexItem> buildSingleLine(String storeUrl) {
        List<UrlIndexItem> list = new ArrayList<>();
        UrlIndexItem item = new UrlIndexItem();
        item.name = StoreConfigHelper.buildDefaultName(storeUrl);
        item.url = UrlHelper.normalizeRequestUrl(storeUrl);
        list.add(item);
        return list;
    }

    private void postUrlIndexCallback(UrlIndexCallback callback, List<UrlIndexItem> items, String error) {
        new Handler(Looper.getMainLooper()).post(() -> {
            if (!TextUtils.isEmpty(error)) {
                callback.onError(error);
            } else {
                callback.onSuccess(items == null ? new ArrayList<>() : items);
            }
        });
    }

    private void showUrlIndexDialog(Activity activity, List<UrlIndexItem> items, boolean useCache, LoadConfigCallback callback, int generation) {
        if (isLoadStale(generation)) {
            return;
        }
        if (items.isEmpty()) {
            postCallbackError(callback, "配置线路为空");
            return;
        }
        if (items.size() == 1) {
            applyUrlIndexSelection(items.get(0));
            loadConfig(useCache, callback, activity);
            return;
        }
        if (suppressUrlIndexDialog) {
            suppressUrlIndexDialog = false;
            UrlIndexItem selected = resolveUrlIndexItem(items);
            if (selected == null) {
                selected = items.get(0);
            }
            applyUrlIndexSelection(selected);
            loadConfig(useCache, callback, activity);
            return;
        }
        Activity host = resolveDialogActivity(activity);
        if (host == null) {
            postCallbackError(callback, "配置线路为空");
            return;
        }
        ArrayList<String> names = new ArrayList<>();
        for (UrlIndexItem item : items) {
            names.add(item.name);
        }
        Runnable showDialogTask = () -> {
            if (isLoadStale(generation) || !isActivityAlive(host)) {
                postCallbackError(callback, "配置线路为空");
                return;
            }
            try {
                if (host instanceof BaseActivity) {
                    ((BaseActivity) host).showSuccess();
                }
                ApiHistoryDialog dialog = new ApiHistoryDialog(host);
                dialog.setTip("请选择配置线路");
                ApiHistoryDialogAdapter adapter = new ApiHistoryDialogAdapter(new ApiHistoryDialogAdapter.SelectDialogInterface() {
                    @Override
                    public void click(String value) {
                        int idx = names.indexOf(value);
                        if (idx >= 0) {
                            applyUrlIndexSelection(items.get(idx));
                            dialog.dismiss();
                            loadConfig(useCache, callback, host);
                        }
                    }

                    @Override
                    public void del(String value, ArrayList<String> data) {
                    }
                });
                adapter.setAllowReselect(true);
                dialog.setAdapter(adapter, names, 0);
                dialog.show();
            } catch (Throwable th) {
                th.printStackTrace();
                postCallbackError(callback, "无法显示线路选择");
            }
        };
        if (host instanceof HomeActivity) {
            ((HomeActivity) host).runAfterLoading(showDialogTask);
        } else {
            host.runOnUiThread(showDialogTask);
        }
    }

    private boolean isActivityAlive(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return false;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed()) {
            return false;
        }
        return true;
    }

    private Activity resolveDialogActivity(Activity activity) {
        if (activity != null && !activity.isFinishing()) {
            return activity;
        }
        Activity home = AppManager.getInstance().getActivity(HomeActivity.class);
        if (home != null && !home.isFinishing()) {
            return home;
        }
        return AppManager.getInstance().isActivity() ? AppManager.getInstance().currentActivity() : null;
    }

    private void postCallbackError(LoadConfigCallback callback, String msg) {
        new Handler(Looper.getMainLooper()).post(() -> callback.error(msg));
    }

    public static class UrlIndexItem {
        public String name;
        public String url;
    }


    public void loadJar(boolean useCache, String spider, LoadConfigCallback callback) {
        String[] urls = spider.split(";md5;");
        String jarUrl = urls[0];
        String md5 = urls.length > 1 ? urls[1].trim() : "";
        File cache = new File(App.getInstance().getFilesDir().getAbsolutePath() + "/csp.jar");

        if (!md5.isEmpty() || useCache) {
            if (cache.exists() && (useCache || MD5.getFileMd5(cache).equalsIgnoreCase(md5))) {
                if (jarLoader.load(cache.getAbsolutePath())) {
                    callback.success();
                } else {
                    callback.error("");
                }
                return;
            }
        }

        OkGo.<File>get(jarUrl).execute(new AbsCallback<File>() {

            @Override
            public File convertResponse(okhttp3.Response response) throws Throwable {
                File cacheDir = cache.getParentFile();
                if (!cacheDir.exists())
                    cacheDir.mkdirs();
                if (cache.exists())
                    cache.delete();
                FileOutputStream fos = new FileOutputStream(cache);
                fos.write(response.body().bytes());
                fos.flush();
                fos.close();
                return cache;
            }

            @Override
            public void onSuccess(Response<File> response) {
                if (response.body().exists()) {
                    if (jarLoader.load(response.body().getAbsolutePath())) {
                        callback.success();
                    } else {
                        callback.error("");
                    }
                } else {
                    callback.error("");
                }
            }

            @Override
            public void onError(Response<File> response) {
                super.onError(response);
                callback.error("");
            }
        });
    }

    private void parseJson(String apiUrl, File f) throws Throwable {
        parseJson(apiUrl, readCache(f));
    }

    private void parseJson(String apiUrl, String jsonStr) {
        parseJson(apiUrl, new Gson().fromJson(normalizeJson(jsonStr), JsonObject.class));
    }

    private void parseJson(String apiUrl, JsonObject infoJson) {
        sourceBeanList.clear();
        parseBeanList.clear();
        mDefaultParse = null;
        vipParseFlags = new ArrayList<>();
        // spider
        spider = DefaultConfig.safeJsonString(infoJson, "spider", "");
        // 远端站点源
        SourceBean firstSite = null;
        JsonArray sitesArray = safeJsonArray(infoJson, "sites");
        if (sitesArray.size() == 0) {
            throw new IllegalStateException("配置中无可用站点");
        }
        for (JsonElement opt : sitesArray) {
            JsonObject obj = (JsonObject) opt;
            SourceBean sb = new SourceBean();
            String siteKey = obj.get("key").getAsString().trim();
            sb.setKey(siteKey);
            sb.setName(obj.get("name").getAsString().trim());
            String api = DefaultConfig.normalizeCmsApi(obj.get("api").getAsString().trim());
            sb.setType(DefaultConfig.resolveSourceType(obj.get("type").getAsInt(), api));
            sb.setApi(api);
            sb.setSearchable(DefaultConfig.safeJsonInt(obj, "searchable", 1));
            sb.setQuickSearch(DefaultConfig.safeJsonInt(obj, "quickSearch", 1));
            sb.setFilterable(DefaultConfig.safeJsonInt(obj, "filterable", 1));
            sb.setPlayerUrl(DefaultConfig.safeJsonString(obj, "playUrl", ""));
            sb.setExt(DefaultConfig.safeJsonExt(obj, "ext", ""));
            sb.setCategories(DefaultConfig.safeJsonStringList(obj, "categories"));
            if (firstSite == null)
                firstSite = sb;
            sourceBeanList.put(siteKey, sb);
        }
        if (sourceBeanList != null && sourceBeanList.size() > 0) {
            String home = Hawk.get(HawkConfig.HOME_API, "");
            SourceBean sh = getSource(home);
            if (sh == null) {
                SourceBean defaultHome = firstSite;
                if (defaultHome != null && defaultHome.getType() == 3) {
                    for (SourceBean sb : sourceBeanList.values()) {
                        if (sb.getType() == 1 || sb.getType() == 0) {
                            defaultHome = sb;
                            break;
                        }
                    }
                }
                setSourceBean(defaultHome);
            } else {
                setSourceBean(sh);
            }
        }
        // 需要使用vip解析的flag
        vipParseFlags = DefaultConfig.safeJsonStringList(infoJson, "flags");
        // 解析地址
        for (JsonElement opt : safeJsonArray(infoJson, "parses")) {
            JsonObject obj = (JsonObject) opt;
            ParseBean pb = new ParseBean();
            pb.setName(obj.get("name").getAsString().trim());
            pb.setUrl(obj.get("url").getAsString().trim());
            pb.setExt(DefaultConfig.safeJsonExt(obj, "ext", ""));
            pb.setType(DefaultConfig.safeJsonInt(obj, "type", 0));
            if (pb.getType() == 0 && !TextUtils.isEmpty(pb.getUrl())
                    && (pb.getUrl().contains("url=") || pb.getUrl().endsWith("="))) {
                pb.setType(1);
            }
            parseBeanList.add(pb);
        }
        // 获取默认解析
        if (parseBeanList != null && parseBeanList.size() > 0) {
            String defaultParse = Hawk.get(HawkConfig.DEFAULT_PARSE, "");
            if (!TextUtils.isEmpty(defaultParse))
                for (ParseBean pb : parseBeanList) {
                    if (pb.getName().equals(defaultParse))
                        setDefaultParse(pb);
                }
            if (mDefaultParse != null && mDefaultParse.getType() == 3 && TextUtils.isEmpty(spider)) {
                mDefaultParse = null;
            }
            if (mDefaultParse == null) {
                ParseBean selected = null;
                for (ParseBean pb : parseBeanList) {
                    if (pb.getType() == 1) {
                        selected = pb;
                        break;
                    }
                }
                if (selected == null) {
                    for (ParseBean pb : parseBeanList) {
                        if (pb.getType() == 0 && !TextUtils.isEmpty(pb.getUrl())
                                && !"Web".equalsIgnoreCase(pb.getUrl())
                                && !"Demo".equalsIgnoreCase(pb.getUrl())) {
                            selected = pb;
                            break;
                        }
                    }
                }
                setDefaultParse(selected != null ? selected : parseBeanList.get(0));
            }
        }
        // 直播源
        liveChannelGroupList.clear();
        liveSourceList.clear();
        inlineLiveCache.clear();
        try {
            JsonArray livesArray = safeJsonArray(infoJson, "lives");
            if (livesArray.size() > 0) {
                String lives = livesArray.toString();
                int index = lives.indexOf("proxy://");
                if (index != -1) {
                    int endIndex = lives.lastIndexOf("\"");
                    String url = lives.substring(index, endIndex);
                    url = DefaultConfig.checkReplaceProxy(url);

                    //clan
                    String extUrl = Uri.parse(url).getQueryParameter("ext");
                    if (extUrl != null && !extUrl.isEmpty()) {
                        String extUrlFix = new String(Base64.decode(extUrl, Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP), "UTF-8");
                        if (extUrlFix.startsWith("clan://")) {
                            extUrlFix = clanContentFix(clanToAddress(apiUrl), extUrlFix);
                            extUrlFix = Base64.encodeToString(extUrlFix.getBytes("UTF-8"), Base64.DEFAULT | Base64.URL_SAFE | Base64.NO_WRAP);
                            url = url.replace(extUrl, extUrlFix);
                        }
                    }
                    LiveSourceBean liveSource = new LiveSourceBean();
                    liveSource.setName("代理直播");
                    liveSource.setUrl(url);
                    liveSourceList.add(liveSource);
                    applySelectedLiveSource();
                } else {
                    JsonObject firstLive = livesArray.get(0).getAsJsonObject();
                    if (firstLive.has("group") && firstLive.has("channels")) {
                        loadLives(livesArray);
                        LiveSourceBean liveSource = new LiveSourceBean();
                        liveSource.setName("配置内置");
                        liveSource.setUrl(LiveSourceBean.INLINE);
                        liveSourceList.add(liveSource);
                        inlineLiveCache = new ArrayList<>(liveChannelGroupList);
                        Hawk.put(HawkConfig.LIVE_URL, LiveSourceBean.INLINE);
                    } else {
                        parseFongMiLiveSources(livesArray);
                        applySelectedLiveSource();
                    }
                }
            }
        } catch (Throwable th) {
            th.printStackTrace();
        }
        // 广告地址
        for (JsonElement host : safeJsonArray(infoJson, "ads")) {
            AdBlocker.addAdHost(host.getAsString());
        }
        // IJK解码配置
        boolean foundOldSelect = false;
        String ijkCodec = Hawk.get(HawkConfig.IJK_CODEC, "");
        ijkCodes = new ArrayList<>();
        for (JsonElement opt : safeJsonArray(infoJson, "ijk")) {
            JsonObject obj = (JsonObject) opt;
            String name = obj.get("group").getAsString();
            LinkedHashMap<String, String> baseOpt = new LinkedHashMap<>();
            for (JsonElement cfg : obj.get("options").getAsJsonArray()) {
                JsonObject cObj = (JsonObject) cfg;
                String key = cObj.get("category").getAsString() + "|" + cObj.get("name").getAsString();
                String val = cObj.get("value").getAsString();
                baseOpt.put(key, val);
            }
            IJKCode codec = new IJKCode();
            codec.setName(name);
            codec.setOption(baseOpt);
            if (name.equals(ijkCodec) || TextUtils.isEmpty(ijkCodec)) {
                codec.selected(true);
                ijkCodec = name;
                foundOldSelect = true;
            } else {
                codec.selected(false);
            }
            ijkCodes.add(codec);
        }
        if (!foundOldSelect && ijkCodes.size() > 0) {
            ijkCodes.get(0).selected(true);
        }
    }

    public void loadLives(JsonArray livesArray) {
        liveChannelGroupList.clear();
        int groupIndex = 0;
        int channelIndex = 0;
        int channelNum = 0;
        for (JsonElement groupElement : livesArray) {
            if (!groupElement.isJsonObject()) {
                continue;
            }
            JsonObject groupObj = groupElement.getAsJsonObject();
            if (!groupObj.has("channels")) {
                continue;
            }
            LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
            liveChannelGroup.setLiveChannels(new ArrayList<LiveChannelItem>());
            liveChannelGroup.setGroupIndex(groupIndex++);
            String groupName = DefaultConfig.safeJsonString(groupObj, "group", "默认");
            String[] splitGroupName = groupName.split("_", 2);
            liveChannelGroup.setGroupName(splitGroupName[0]);
            if (splitGroupName.length > 1)
                liveChannelGroup.setGroupPassword(splitGroupName[1]);
            else
                liveChannelGroup.setGroupPassword("");
            channelIndex = 0;
            JsonArray channels = groupObj.getAsJsonArray("channels");
            for (JsonElement channelElement : channels) {
                if (!channelElement.isJsonObject()) {
                    continue;
                }
                JsonObject obj = channelElement.getAsJsonObject();
                String channelName = DefaultConfig.safeJsonString(obj, "name", "");
                if (TextUtils.isEmpty(channelName)) {
                    continue;
                }
                LiveChannelItem liveChannelItem = new LiveChannelItem();
                liveChannelItem.setChannelName(channelName);
                liveChannelItem.setChannelIndex(channelIndex++);
                liveChannelItem.setChannelNum(++channelNum);
                ArrayList<String> urls = DefaultConfig.safeJsonStringList(obj, "urls");
                ArrayList<String> sourceNames = new ArrayList<>();
                ArrayList<String> sourceUrls = new ArrayList<>();
                int sourceIndex = 1;
                for (String url : urls) {
                    String[] splitText = url.split("\\$", 2);
                    sourceUrls.add(splitText[0]);
                    if (splitText.length > 1)
                        sourceNames.add(splitText[1]);
                    else
                        sourceNames.add("源" + Integer.toString(sourceIndex));
                    sourceIndex++;
                }
                liveChannelItem.setChannelSourceNames(sourceNames);
                liveChannelItem.setChannelUrls(sourceUrls);
                liveChannelGroup.getLiveChannels().add(liveChannelItem);
            }
            if (!liveChannelGroup.getLiveChannels().isEmpty()) {
                liveChannelGroupList.add(liveChannelGroup);
            }
        }
    }

    private void parseFongMiLiveSources(JsonArray livesArray) {
        for (JsonElement element : livesArray) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject obj = element.getAsJsonObject();
            int type = DefaultConfig.safeJsonInt(obj, "type", -1);
            if (type != 0) {
                continue;
            }
            String url = DefaultConfig.safeJsonString(obj, "url", "");
            if (TextUtils.isEmpty(url) || !(url.startsWith("http://") || url.startsWith("https://"))) {
                continue;
            }
            String name = DefaultConfig.safeJsonString(obj, "name", "");
            if (TextUtils.isEmpty(name)) {
                name = url;
            }
            LiveSourceBean liveSource = new LiveSourceBean();
            liveSource.setName(name);
            liveSource.setUrl(url);
            liveSourceList.add(liveSource);
        }
    }

    private void applySelectedLiveSource() {
        if (liveSourceList.isEmpty()) {
            return;
        }
        String selectedUrl = Hawk.get(HawkConfig.LIVE_URL, "");
        LiveSourceBean selected = null;
        if (!TextUtils.isEmpty(selectedUrl)) {
            for (LiveSourceBean source : liveSourceList) {
                if (selectedUrl.equals(source.getUrl())) {
                    selected = source;
                    break;
                }
            }
        }
        if (selected == null) {
            selected = liveSourceList.get(0);
            Hawk.put(HawkConfig.LIVE_URL, selected.getUrl());
        }
        liveChannelGroupList.clear();
        if (selected.isInline()) {
            liveChannelGroupList.addAll(inlineLiveCache);
            return;
        }
        LiveChannelGroup liveChannelGroup = new LiveChannelGroup();
        liveChannelGroup.setGroupName(selected.getUrl());
        liveChannelGroupList.add(liveChannelGroup);
    }

    public List<LiveSourceBean> getLiveSourceList() {
        return new ArrayList<>(liveSourceList);
    }

    public LiveSourceBean getCurrentLiveSource() {
        String selectedUrl = Hawk.get(HawkConfig.LIVE_URL, "");
        if (!TextUtils.isEmpty(selectedUrl)) {
            for (LiveSourceBean source : liveSourceList) {
                if (selectedUrl.equals(source.getUrl())) {
                    return source;
                }
            }
        }
        return liveSourceList.isEmpty() ? null : liveSourceList.get(0);
    }

    public String getCurrentLiveSourceName() {
        LiveSourceBean source = getCurrentLiveSource();
        return source != null ? source.getName() : "未设置";
    }

    public void setLiveSource(LiveSourceBean source) {
        if (source == null) {
            return;
        }
        Hawk.put(HawkConfig.LIVE_URL, source.getUrl());
        Hawk.put(HawkConfig.LIVE_CHANNEL, "");
        applySelectedLiveSource();
    }

    public void loadLiveContent(String content) {
        liveChannelGroupList.clear();
        if (TextUtils.isEmpty(content)) {
            return;
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("[")) {
            try {
                loadLives(new Gson().fromJson(trimmed, JsonArray.class));
            } catch (Throwable ignored) {
            }
            return;
        }
        if (trimmed.startsWith("#EXTM3U")) {
            parseLiveM3u(trimmed);
        } else {
            parseLiveTxt(trimmed);
        }
    }

    private void parseLiveTxt(String content) {
        LiveChannelGroup currentGroup = null;
        int groupIndex = 0;
        int channelIndex = 0;
        int channelNum = 0;
        int[] counters = new int[2];
        String[] lines = content.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            if (line.contains(",#genre#")) {
                String groupName = line.substring(0, line.indexOf(",#genre#")).trim();
                currentGroup = new LiveChannelGroup();
                currentGroup.setLiveChannels(new ArrayList<LiveChannelItem>());
                currentGroup.setGroupIndex(groupIndex++);
                currentGroup.setGroupName(groupName);
                currentGroup.setGroupPassword("");
                liveChannelGroupList.add(currentGroup);
                channelIndex = 0;
                counters[0] = 0;
                continue;
            }
            int comma = line.indexOf(',');
            if (comma <= 0 || comma >= line.length() - 1) {
                continue;
            }
            if (currentGroup == null) {
                currentGroup = new LiveChannelGroup();
                currentGroup.setLiveChannels(new ArrayList<LiveChannelItem>());
                currentGroup.setGroupIndex(groupIndex++);
                currentGroup.setGroupName("默认");
                currentGroup.setGroupPassword("");
                liveChannelGroupList.add(currentGroup);
                counters[0] = 0;
            }
            String channelName = line.substring(0, comma).trim();
            String urlPart = line.substring(comma + 1).trim();
            if (!urlPart.startsWith("http://") && !urlPart.startsWith("https://")) {
                continue;
            }
            counters[0] = channelIndex;
            addLiveChannel(currentGroup, channelName, urlPart, counters);
            channelIndex = counters[0];
            channelNum = counters[1];
        }
    }

    private void parseLiveM3u(String content) {
        LiveChannelGroup currentGroup = null;
        int groupIndex = 0;
        int channelIndex = 0;
        int channelNum = 0;
        int[] counters = new int[2];
        String pendingName = null;
        String pendingGroup = "默认";
        String[] lines = content.split("\\r?\\n");
        for (String rawLine : lines) {
            String line = rawLine.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.startsWith("#EXTINF")) {
                pendingGroup = "默认";
                int groupTitleIndex = line.indexOf("group-title=\"");
                if (groupTitleIndex >= 0) {
                    int end = line.indexOf('"', groupTitleIndex + 13);
                    if (end > groupTitleIndex) {
                        pendingGroup = line.substring(groupTitleIndex + 13, end).trim();
                    }
                }
                int nameIndex = line.lastIndexOf(',');
                pendingName = nameIndex >= 0 && nameIndex < line.length() - 1
                        ? line.substring(nameIndex + 1).trim() : "";
            } else if (!line.startsWith("#") && pendingName != null) {
                if (currentGroup == null || !pendingGroup.equals(currentGroup.getGroupName())) {
                    currentGroup = findLiveGroup(pendingGroup);
                    if (currentGroup == null) {
                        currentGroup = new LiveChannelGroup();
                        currentGroup.setLiveChannels(new ArrayList<LiveChannelItem>());
                        currentGroup.setGroupIndex(groupIndex++);
                        currentGroup.setGroupName(pendingGroup);
                        currentGroup.setGroupPassword("");
                        liveChannelGroupList.add(currentGroup);
                        counters[0] = 0;
                    }
                    channelIndex = counters[0];
                }
                counters[0] = channelIndex;
                addLiveChannel(currentGroup, pendingName, line, counters);
                channelIndex = counters[0];
                channelNum = counters[1];
                pendingName = null;
            }
        }
    }

    private LiveChannelGroup findLiveGroup(String groupName) {
        for (LiveChannelGroup group : liveChannelGroupList) {
            if (groupName.equals(group.getGroupName())) {
                return group;
            }
        }
        return null;
    }

    private void addLiveChannel(LiveChannelGroup group, String channelName, String urlPart, int[] counters) {
        ArrayList<LiveChannelItem> channels = group.getLiveChannels();
        LiveChannelItem existing = null;
        for (LiveChannelItem item : channels) {
            if (channelName.equals(item.getChannelName())) {
                existing = item;
                break;
            }
        }
        String[] splitText = urlPart.split("\\$", 2);
        String sourceUrl = splitText[0].trim();
        String sourceName = splitText.length > 1 ? splitText[1].trim() : null;
        if (existing != null) {
            int sourceIndex = existing.getChannelUrls().size() + 1;
            if (TextUtils.isEmpty(sourceName)) {
                sourceName = "源" + sourceIndex;
            }
            existing.getChannelUrls().add(sourceUrl);
            existing.getChannelSourceNames().add(sourceName);
            return;
        }
        if (TextUtils.isEmpty(sourceName)) {
            sourceName = "源1";
        }
        LiveChannelItem liveChannelItem = new LiveChannelItem();
        liveChannelItem.setChannelName(channelName);
        liveChannelItem.setChannelIndex(counters[0]++);
        liveChannelItem.setChannelNum(++counters[1]);
        ArrayList<String> sourceUrls = new ArrayList<>();
        sourceUrls.add(sourceUrl);
        ArrayList<String> sourceNames = new ArrayList<>();
        sourceNames.add(sourceName);
        liveChannelItem.setChannelUrls(sourceUrls);
        liveChannelItem.setChannelSourceNames(sourceNames);
        channels.add(liveChannelItem);
    }

    public String getSpider() {
        return spider;
    }

    public Spider getCSP(SourceBean sourceBean) {
        return jarLoader.getSpider(sourceBean.getKey(), sourceBean.getApi(), sourceBean.getExt());
    }

    public Object[] proxyLocal(Map param) {
        return jarLoader.proxyInvoke(param);
    }

    public JSONObject jsonExt(String key, LinkedHashMap<String, String> jxs, String url) {
        return jarLoader.jsonExt(key, jxs, url);
    }

    public JSONObject jsonExtMix(String flag, String key, String name, LinkedHashMap<String, HashMap<String, String>> jxs, String url) {
        return jarLoader.jsonExtMix(flag, key, name, jxs, url);
    }

    public interface LoadConfigCallback {
        void success();

        void retry();

        void error(String msg);
    }

    public interface FastParseCallback {
        void success(boolean parse, String url, Map<String, String> header);

        void fail(int code, String msg);
    }

    public SourceBean getSource(String key) {
        if (!sourceBeanList.containsKey(key))
            return null;
        return sourceBeanList.get(key);
    }

    public void setSourceBean(SourceBean sourceBean) {
        this.mHomeSource = sourceBean;
        Hawk.put(HawkConfig.HOME_API, sourceBean.getKey());
    }

    public void setDefaultParse(ParseBean parseBean) {
        if (this.mDefaultParse != null)
            this.mDefaultParse.setDefault(false);
        this.mDefaultParse = parseBean;
        Hawk.put(HawkConfig.DEFAULT_PARSE, parseBean.getName());
        parseBean.setDefault(true);
    }

    public ParseBean getDefaultParse() {
        return mDefaultParse;
    }

    public List<SourceBean> getSourceBeanList() {
        return new ArrayList<>(sourceBeanList.values());
    }

    public List<ParseBean> getParseBeanList() {
        return parseBeanList;
    }

    public List<String> getVipParseFlags() {
        return vipParseFlags;
    }

    public SourceBean getHomeSourceBean() {
        return mHomeSource == null ? emptyHome : mHomeSource;
    }

    public List<LiveChannelGroup> getChannelGroupList() {
        return liveChannelGroupList;
    }

    public List<IJKCode> getIjkCodes() {
        return ijkCodes;
    }

    public IJKCode getCurrentIJKCode() {
        String codeName = Hawk.get(HawkConfig.IJK_CODEC, "");
        return getIJKCodec(codeName);
    }

    public IJKCode getIJKCodec(String name) {
        for (IJKCode code : ijkCodes) {
            if (code.getName().equals(name))
                return code;
        }
        return ijkCodes.get(0);
    }

    String clanToAddress(String lanLink) {
        if (lanLink.startsWith("clan://localhost/")) {
            return lanLink.replace("clan://localhost/", ControlManager.get().getAddress(true) + "file/");
        } else {
            String link = lanLink.substring(7);
            int end = link.indexOf('/');
            return "http://" + link.substring(0, end) + "/file/" + link.substring(end + 1);
        }
    }

    String clanContentFix(String lanLink, String content) {
        String fix = lanLink.substring(0, lanLink.indexOf("/file/") + 6);
        return content.replace("clan://", fix);
    }
}