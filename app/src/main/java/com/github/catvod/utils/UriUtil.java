package com.github.catvod.utils;

import android.text.TextUtils;

public final class UriUtil {

    private UriUtil() {
    }

    public static String resolve(String baseUri, String referenceUri) {
        if (TextUtils.isEmpty(referenceUri)) return baseUri;
        if (referenceUri.startsWith("http://") || referenceUri.startsWith("https://")) return referenceUri;
        if (TextUtils.isEmpty(baseUri)) return referenceUri;
        if (baseUri.endsWith("/")) return baseUri + referenceUri;
        int slash = baseUri.lastIndexOf('/');
        if (slash >= 0) return baseUri.substring(0, slash + 1) + referenceUri;
        return baseUri + "/" + referenceUri;
    }
}
