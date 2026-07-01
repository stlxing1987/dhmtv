package com.github.tvbox.osc.util;

import android.net.Uri;
import android.text.TextUtils;

import java.net.IDN;
import java.net.URI;

public class UrlHelper {

    /**
     * 将含中文等国际域名的 URL 转为 Punycode，便于 OkHttp 正常请求。
     */
    public static String normalizeRequestUrl(String url) {
        if (TextUtils.isEmpty(url) || url.startsWith("clan://")) {
            return url;
        }
        try {
            Uri uri = Uri.parse(url.trim());
            String host = uri.getHost();
            if (TextUtils.isEmpty(host)) {
                return url;
            }
            String asciiHost = IDN.toASCII(host, IDN.ALLOW_UNASSIGNED);
            if (asciiHost.equals(host)) {
                return url;
            }
            URI parsed = new URI(url.trim());
            URI asciiUri = new URI(
                    parsed.getScheme(),
                    parsed.getUserInfo(),
                    asciiHost,
                    parsed.getPort(),
                    parsed.getPath(),
                    parsed.getQuery(),
                    parsed.getFragment()
            );
            return asciiUri.toASCIIString();
        } catch (Throwable ignored) {
            return url;
        }
    }

    public static boolean looksLikeHtml(String body) {
        if (TextUtils.isEmpty(body)) {
            return false;
        }
        String trimmed = body.trim();
        return trimmed.startsWith("<!DOCTYPE") || trimmed.startsWith("<html") || trimmed.startsWith("<HTML");
    }

}
