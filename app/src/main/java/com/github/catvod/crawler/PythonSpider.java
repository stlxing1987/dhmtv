package com.github.catvod.crawler;

import android.content.Context;

import com.github.catvod.python.Loader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PythonSpider extends Spider {

    private final Loader.Bridge bridge;

    public PythonSpider(Loader.Bridge bridge) {
        this.bridge = bridge;
    }

    @Override
    public void init(Context context, String extend) {
        bridge.init(context, extend);
    }

    @Override
    public String homeContent(boolean filter) {
        return bridge.homeContent(filter);
    }

    @Override
    public String homeVideoContent() {
        return bridge.homeVideoContent();
    }

    @Override
    public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
        return bridge.categoryContent(tid, pg, filter, extend);
    }

    @Override
    public String detailContent(List<String> ids) {
        return bridge.detailContent(ids);
    }

    @Override
    public String searchContent(String key, boolean quick) {
        return bridge.searchContent(key, quick);
    }

    @Override
    public String searchContent(String key, boolean quick, String pg) {
        return bridge.searchContent(key, quick, pg);
    }

    @Override
    public String playerContent(String flag, String id, List<String> vipFlags) {
        return bridge.playerContent(flag, id, vipFlags);
    }

    @Override
    public String liveContent(String url) {
        return bridge.liveContent(url);
    }

    @Override
    public boolean manualVideoCheck() {
        return bridge.manualVideoCheck();
    }

    @Override
    public boolean isVideoFormat(String url) {
        return bridge.isVideoFormat(url);
    }

    @Override
    public Object[] proxy(Map<String, String> params) throws Exception {
        return bridge.proxy(params);
    }

    @Override
    public String action(String action) {
        return bridge.action(action);
    }

    @Override
    public void destroy() {
        bridge.destroy();
    }
}
