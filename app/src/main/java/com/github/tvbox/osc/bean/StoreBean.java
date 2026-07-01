package com.github.tvbox.osc.bean;

/**
 * 多仓配置项：名称 + 推送/配置地址
 */
public class StoreBean {
    public String name;
    public String url;

    public StoreBean() {
    }

    public StoreBean(String name, String url) {
        this.name = name;
        this.url = url;
    }
}
