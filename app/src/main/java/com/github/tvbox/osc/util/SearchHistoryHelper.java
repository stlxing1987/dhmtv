package com.github.tvbox.osc.util;

import android.text.TextUtils;

import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;

public class SearchHistoryHelper {
    private static final int MAX = 20;

    public static List<String> getHistory() {
        List<String> list = Hawk.get(HawkConfig.SEARCH_HISTORY, new ArrayList<String>());
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    public static void add(String keyword) {
        if (TextUtils.isEmpty(keyword)) {
            return;
        }
        String word = keyword.trim();
        List<String> list = getHistory();
        list.remove(word);
        list.add(0, word);
        while (list.size() > MAX) {
            list.remove(list.size() - 1);
        }
        Hawk.put(HawkConfig.SEARCH_HISTORY, list);
    }

    public static void clear() {
        Hawk.put(HawkConfig.SEARCH_HISTORY, new ArrayList<String>());
    }
}
