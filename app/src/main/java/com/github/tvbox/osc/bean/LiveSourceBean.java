package com.github.tvbox.osc.bean;

public class LiveSourceBean {
    public static final String INLINE = "__inline__";

    private String name;
    private String url;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public boolean isInline() {
        return INLINE.equals(url);
    }
}
