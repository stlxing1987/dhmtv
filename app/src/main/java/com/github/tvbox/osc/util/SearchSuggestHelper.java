package com.github.tvbox.osc.util;

import android.text.TextUtils;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.AbsCallback;
import com.lzy.okgo.model.Response;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 搜索联想：B站（简拼友好）+ 腾讯（备用）
 */
public class SearchSuggestHelper {

    public interface Callback {
        void onResult(List<String> titles);

        void onError();
    }

    public static void fetch(String query, Callback callback) {
        OkGo.<String>get("https://s.search.bilibili.com/main/suggest")
                .tag("search_suggest")
                .params("term", query)
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        List<String> list = parseBilibili(response.body());
                        if (!list.isEmpty()) {
                            callback.onResult(list);
                        } else {
                            fetchFromTencent(query, callback);
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        fetchFromTencent(query, callback);
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }
                });
    }

    private static void fetchFromTencent(String query, Callback callback) {
        OkGo.<String>get("https://s.video.qq.com/smartbox")
                .tag("search_suggest")
                .params("plat", 2)
                .params("ver", 0)
                .params("num", 12)
                .params("otype", "json")
                .params("query", query)
                .execute(new AbsCallback<String>() {
                    @Override
                    public void onSuccess(Response<String> response) {
                        List<String> list = parseTencent(response.body());
                        if (!list.isEmpty()) {
                            callback.onResult(list);
                        } else {
                            callback.onError();
                        }
                    }

                    @Override
                    public void onError(Response<String> response) {
                        callback.onError();
                    }

                    @Override
                    public String convertResponse(okhttp3.Response response) throws Throwable {
                        return response.body().string();
                    }
                });
    }

    public static List<String> parseBilibili(String json) {
        LinkedHashSet<String> titles = new LinkedHashSet<>();
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            if (!root.has("result")) {
                return new ArrayList<>();
            }
            JsonObject result = root.getAsJsonObject("result");
            if (!result.has("tag")) {
                return new ArrayList<>();
            }
            JsonArray tags = result.getAsJsonArray("tag");
            for (JsonElement ele : tags) {
                JsonObject tag = ele.getAsJsonObject();
                String term = tag.has("term") ? tag.get("term").getAsString() : tag.get("value").getAsString();
                String core = PinyinSearchHelper.extractCoreTitle(term);
                if (!TextUtils.isEmpty(core)) {
                    titles.add(core);
                }
            }
        } catch (Throwable ignored) {
        }
        return new ArrayList<>(titles);
    }

    public static List<String> parseTencent(String raw) {
        LinkedHashSet<String> titles = new LinkedHashSet<>();
        try {
            String jsonStr = raw.substring(raw.indexOf("{"), raw.lastIndexOf("}") + 1);
            JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
            JsonArray itemList = json.getAsJsonArray("item");
            for (JsonElement ele : itemList) {
                String word = ele.getAsJsonObject().get("word").getAsString().trim();
                String core = PinyinSearchHelper.extractCoreTitle(word);
                if (!TextUtils.isEmpty(core)) {
                    titles.add(core);
                }
            }
        } catch (Throwable ignored) {
        }
        return new ArrayList<>(titles);
    }
}
