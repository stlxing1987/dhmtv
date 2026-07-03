package com.github.catvod.crawler;

import android.text.TextUtils;

import com.github.catvod.python.Loader;
import com.github.tvbox.osc.base.App;
import com.github.tvbox.osc.bean.SourceBean;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PyLoader {

    private final ConcurrentHashMap<String, Spider> spiders = new ConcurrentHashMap<>();
    private Loader loader;
    private volatile String recent;

    public static boolean isPySource(SourceBean sourceBean) {
        if (sourceBean == null || sourceBean.getType() != 3) return false;
        String api = sourceBean.getApi();
        String ext = sourceBean.getExt();
        if (!TextUtils.isEmpty(api) && api.contains(".py")) return true;
        if (!TextUtils.isEmpty(ext) && ext.contains(".py")) return true;
        if (!TextUtils.isEmpty(api) && api.startsWith("py_")) return true;
        if (!TextUtils.isEmpty(api) && api.startsWith("spider_")) return true;
        if (!TextUtils.isEmpty(api) && !api.startsWith("csp_") && !looksLikeUrl(api) && !TextUtils.isEmpty(ext) && ext.startsWith("http")) {
            return true;
        }
        return false;
    }

    public static String resolveScriptUrl(String api, String ext) {
        if (!TextUtils.isEmpty(ext) && ext.contains(".py") && (ext.startsWith("http") || ext.startsWith("/"))) {
            return ext.trim();
        }
        if (!TextUtils.isEmpty(api) && api.contains(".py")) {
            return api.trim();
        }
        if (!TextUtils.isEmpty(ext) && ext.startsWith("http")) {
            return ext.trim();
        }
        return TextUtils.isEmpty(ext) ? api : ext;
    }

    /** ext 为脚本地址时，init 传空字符串，避免把 .py URL 当成站点参数 */
    public static String resolveInitExtend(String ext) {
        if (TextUtils.isEmpty(ext)) return "";
        String value = ext.trim();
        if (value.contains(".py") && value.startsWith("http")) return "";
        return value;
    }

    private static boolean looksLikeUrl(String api) {
        return api.startsWith("http://") || api.startsWith("https://");
    }

    private Loader getLoader() {
        if (loader == null) {
            loader = new Loader();
        }
        return loader;
    }

    public void setRecent(String key) {
        this.recent = key;
    }

    public void clear() {
        for (Spider spider : spiders.values()) spider.destroy();
        spiders.clear();
        recent = null;
    }

    public Spider getSpider(String key, String api, String ext) {
        return spiders.computeIfAbsent(key, k -> {
            try {
                String scriptUrl = resolveScriptUrl(api, ext);
                Loader.Bridge bridge = getLoader().spider(scriptUrl);
                bridge.siteKey = key;
                PythonSpider spider = new PythonSpider(bridge);
                spider.init(App.getInstance(), resolveInitExtend(ext));
                return spider;
            } catch (Throwable e) {
                e.printStackTrace();
                return new SpiderNull();
            }
        });
    }

    public Object[] proxyInvoke(Map<String, String> params) {
        try {
            if (recent == null) return null;
            Spider spider = spiders.get(recent);
            if (spider == null) return null;
            return spider.proxy(params);
        } catch (Throwable e) {
            e.printStackTrace();
            return null;
        }
    }
}
