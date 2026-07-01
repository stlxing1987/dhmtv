package com.github.tvbox.osc.util;

import android.text.TextUtils;

import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.StoreBean;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;

public class StoreConfigHelper {
    private static final Gson GSON = new Gson();

    public static List<StoreBean> getStoreList() {
        migrateHistoryIfNeeded();
        List<StoreBean> list = Hawk.get(HawkConfig.API_STORE_LIST, new ArrayList<StoreBean>());
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    public static void saveStoreList(List<StoreBean> list) {
        Hawk.put(HawkConfig.API_STORE_LIST, list == null ? new ArrayList<>() : list);
    }

    private static void migrateHistoryIfNeeded() {
        if (Hawk.contains(HawkConfig.API_STORE_LIST)) {
            return;
        }
        ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());
        if (history == null || history.isEmpty()) {
            return;
        }
        List<StoreBean> stores = new ArrayList<>();
        for (String url : history) {
            if (TextUtils.isEmpty(url)) {
                continue;
            }
            StoreBean bean = new StoreBean(buildDefaultName(url), url);
            stores.add(bean);
        }
        saveStoreList(stores);
    }

    public static String buildDefaultName(String url) {
        if (TextUtils.isEmpty(url)) {
            return "未命名";
        }
        if (url.length() <= 24) {
            return url;
        }
        return url.substring(0, 21) + "...";
    }

    public static StoreBean findByUrl(List<StoreBean> list, String url) {
        if (TextUtils.isEmpty(url) || list == null) {
            return null;
        }
        for (StoreBean bean : list) {
            if (url.equals(bean.url)) {
                return bean;
            }
        }
        return null;
    }

    public static StoreBean getCurrentStore() {
        String url = Hawk.get(HawkConfig.API_URL, "");
        StoreBean store = findByUrl(getStoreList(), url);
        if (store != null) {
            return store;
        }
        String indexUrl = Hawk.get(HawkConfig.API_INDEX_URL, "");
        store = findByUrl(getStoreList(), indexUrl);
        if (store != null) {
            return store;
        }
        if (!TextUtils.isEmpty(url)) {
            return new StoreBean(buildDefaultName(url), url);
        }
        return null;
    }

    public static String getCurrentStoreName() {
        StoreBean store = getCurrentStore();
        return store == null ? "" : store.name;
    }

    public static void addOrUpdateStore(String name, String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        List<StoreBean> list = getStoreList();
        StoreBean existing = findByUrl(list, url);
        if (existing != null) {
            if (!TextUtils.isEmpty(name)) {
                existing.name = name;
            }
        } else {
            list.add(0, new StoreBean(TextUtils.isEmpty(name) ? buildDefaultName(url) : name, url));
        }
        saveStoreList(list);
        ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());
        if (!history.contains(url)) {
            history.add(0, url);
        }
        if (history.size() > 20) {
            history.remove(history.size() - 1);
        }
        Hawk.put(HawkConfig.API_HISTORY, history);
    }

    public static void removeStore(String url) {
        List<StoreBean> list = getStoreList();
        StoreBean target = findByUrl(list, url);
        if (target != null) {
            list.remove(target);
            saveStoreList(list);
        }
        ArrayList<String> history = Hawk.get(HawkConfig.API_HISTORY, new ArrayList<String>());
        history.remove(url);
        Hawk.put(HawkConfig.API_HISTORY, history);
    }

    public static void selectStore(StoreBean store) {
        if (store == null || TextUtils.isEmpty(store.url)) {
            return;
        }
        Hawk.put(HawkConfig.API_URL, store.url);
        Hawk.put(HawkConfig.API_INDEX_URL, "");
        Hawk.put(HawkConfig.API_LINE_NAME, "");
        Hawk.put(HawkConfig.API_LINE_LIST, "");
    }

    public static void selectStore(String url) {
        selectStore(findByUrl(getStoreList(), url));
        if (TextUtils.isEmpty(Hawk.get(HawkConfig.API_URL, ""))) {
            Hawk.put(HawkConfig.API_URL, url);
            Hawk.put(HawkConfig.API_INDEX_URL, "");
            Hawk.put(HawkConfig.API_LINE_NAME, "");
            Hawk.put(HawkConfig.API_LINE_LIST, "");
        }
    }

    public static String serializeLines(List<ApiConfig.UrlIndexItem> lines) {
        return GSON.toJson(lines);
    }

    public static List<ApiConfig.UrlIndexItem> parseLines(String json) {
        if (TextUtils.isEmpty(json)) {
            return new ArrayList<>();
        }
        try {
            List<ApiConfig.UrlIndexItem> list = GSON.fromJson(json, new TypeToken<ArrayList<ApiConfig.UrlIndexItem>>() {
            }.getType());
            return list == null ? new ArrayList<>() : list;
        } catch (Throwable ignored) {
            return new ArrayList<>();
        }
    }
}
