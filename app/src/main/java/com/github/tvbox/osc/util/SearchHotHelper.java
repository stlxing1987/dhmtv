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
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 腾讯视频热搜榜：电视 / 电影 / 综艺
 */
public class SearchHotHelper {

    public static class Section {
        public String title;
        public List<String> words = new ArrayList<>();
    }

    public interface Callback {
        void onResult(List<Section> sections);
    }

    private static final String[][] CHANNELS = {
            {"电视榜", "1"},
            {"电影榜", "4"},
            {"综艺榜", "2"},
    };

    public static void fetchSections(Callback callback) {
        List<Section> sections = new ArrayList<>();
        for (String[] ch : CHANNELS) {
            Section section = new Section();
            section.title = ch[0];
            sections.add(section);
        }
        AtomicInteger pending = new AtomicInteger(CHANNELS.length);
        for (int i = 0; i < CHANNELS.length; i++) {
            final int index = i;
            OkGo.<String>get("https://node.video.qq.com/x/api/hot_mobilesearch")
                    .params("channdlId", CHANNELS[i][1])
                    .params("_", System.currentTimeMillis())
                    .execute(new AbsCallback<String>() {
                        @Override
                        public void onSuccess(Response<String> response) {
                            sections.get(index).words = parseTitles(response.body());
                            if (pending.decrementAndGet() == 0) {
                                callback.onResult(sections);
                            }
                        }

                        @Override
                        public void onError(Response<String> response) {
                            if (pending.decrementAndGet() == 0) {
                                callback.onResult(sections);
                            }
                        }

                        @Override
                        public String convertResponse(okhttp3.Response response) throws Throwable {
                            return response.body().string();
                        }
                    });
        }
    }

    private static List<String> parseTitles(String body) {
        LinkedHashSet<String> titles = new LinkedHashSet<>();
        try {
            JsonArray itemList = JsonParser.parseString(body)
                    .getAsJsonObject().get("data").getAsJsonObject()
                    .get("itemList").getAsJsonArray();
            for (JsonElement ele : itemList) {
                JsonObject obj = ele.getAsJsonObject();
                String title = obj.get("title").getAsString().trim()
                        .replaceAll("<|>|《|》|-", "")
                        .split(" ")[0];
                if (!TextUtils.isEmpty(title)) {
                    titles.add(title);
                }
            }
        } catch (Throwable ignored) {
        }
        return new ArrayList<>(titles);
    }
}
