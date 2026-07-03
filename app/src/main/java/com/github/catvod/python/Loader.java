package com.github.catvod.python;

import android.content.Context;

import com.chaquo.python.PyObject;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import com.github.catvod.Init;
import com.github.catvod.utils.Path;
import com.github.catvod.utils.UriUtil;
import com.github.catvod.utils.Util;
import com.github.tvbox.osc.base.App;
import com.google.gson.Gson;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Loader {

    private final PyObject app;

    public Loader() {
        android.content.Context context = Init.context();
        if (context == null) context = App.getInstance();
        if (!Python.isStarted()) Python.start(new AndroidPlatform(context));
        app = Python.getInstance().getModule("app");
    }

    public Bridge spider(String api) {
        PyObject obj = app.callAttr("spider", Path.py().getAbsolutePath(), api);
        return new Bridge(app, obj, api);
    }

    public static class Bridge {
        private final PyObject app;
        private final PyObject obj;
        private final String api;
        private final Gson gson = new Gson();
        public String siteKey;

        public Bridge(PyObject app, PyObject obj, String api) {
            this.app = app;
            this.obj = obj;
            this.api = api;
        }

        public void init(Context context, String extend) {
            PyObject dependence = app.callAttr("getDependence", obj);
            if (dependence != null) {
                for (PyObject item : dependence.asList()) download(item.toString() + ".py");
            }
            obj.put("siteKey", siteKey);
            app.callAttr("init", obj, extend == null ? "" : extend);
        }

        public String homeContent(boolean filter) {
            return app.callAttr("homeContent", obj, filter).toString();
        }

        public String homeVideoContent() {
            return app.callAttr("homeVideoContent", obj).toString();
        }

        public String categoryContent(String tid, String pg, boolean filter, HashMap<String, String> extend) {
            return app.callAttr("categoryContent", obj, tid, pg, filter, gson.toJson(extend)).toString();
        }

        public String detailContent(List<String> ids) {
            return app.callAttr("detailContent", obj, gson.toJson(ids)).toString();
        }

        public String searchContent(String key, boolean quick) {
            return app.callAttr("searchContent", obj, key, quick).toString();
        }

        public String searchContent(String key, boolean quick, String pg) {
            return app.callAttr("searchContent", obj, key, quick, pg).toString();
        }

        public String playerContent(String flag, String id, List<String> vipFlags) {
            return app.callAttr("playerContent", obj, flag, id, gson.toJson(vipFlags)).toString();
        }

        public String liveContent(String url) {
            PyObject result = app.callAttr("liveContent", obj, url);
            return result == null ? "" : result.toString();
        }

        public boolean manualVideoCheck() {
            return app.callAttr("manualVideoCheck", obj).toBoolean();
        }

        public boolean isVideoFormat(String url) {
            return app.callAttr("isVideoFormat", obj, url).toBoolean();
        }

        public Object[] proxy(Map<String, String> params) throws Exception {
            List<PyObject> list = app.callAttr("localProxy", obj, gson.toJson(params)).asList();
            if (list == null || list.isEmpty()) return null;
            boolean base64 = list.size() > 4 && list.get(4).toInt() == 1;
            boolean header = list.size() > 3 && list.get(3) != null;
            Object[] result = new Object[4];
            result[0] = list.get(0).toInt();
            result[1] = list.get(1).toString();
            result[2] = getStream(list.get(2), base64);
            result[3] = header ? getHeader(list.get(3)) : null;
            return result;
        }

        public String action(String action) {
            return app.callAttr("action", obj, action).toString();
        }

        public void destroy() {
            try {
                app.callAttr("destroy", obj);
            } catch (Exception ignored) {
            }
        }

        private Map<String, String> getHeader(PyObject headerObj) {
            try {
                Map<String, String> header = new HashMap<>();
                for (Map.Entry<PyObject, PyObject> entry : headerObj.asMap().entrySet()) {
                    header.put(entry.getKey().toString(), entry.getValue().toString());
                }
                return header;
            } catch (Exception e) {
                return null;
            }
        }

        private ByteArrayInputStream getStream(PyObject o, boolean base64) {
            if (o == null) return null;
            if (o.type().toString().contains("bytes")) return new ByteArrayInputStream(o.toJava(byte[].class));
            String content = o.toString();
            if (base64 && content.contains("base64,")) content = content.split("base64,")[1];
            return new ByteArrayInputStream(base64 ? Util.decode(content) : content.getBytes());
        }

        private void download(String name) {
            String path = Path.py(name).getAbsolutePath();
            String url = UriUtil.resolve(api, name);
            app.callAttr("download", path, url);
        }
    }
}
