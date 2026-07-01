package com.github.tvbox.osc.util;

import android.text.TextUtils;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * 从 TVBox 配置响应中提取 JSON 文本。
 * 支持纯 JSON、JPEG 尾部 Base64（饭太硬等接口）等格式。
 */
public class ConfigTextExtractor {
    private static final Pattern JSONC_LINE = Pattern.compile("(?m)^\\s*//.*$\\R?");

    public static String extract(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        int start = firstNonWhitespace(data);
        if (start >= 0 && start < data.length) {
            byte b = data[start];
            if (b == '{' || b == '[') {
                return stripJsonComments(new String(data, start, data.length - start, StandardCharsets.UTF_8));
            }
        }
        int eoi = findJpegEoi(data);
        byte[] payload = eoi >= 0 && eoi + 2 < data.length
                ? java.util.Arrays.copyOfRange(data, eoi + 2, data.length)
                : data;
        String payloadText = new String(payload, StandardCharsets.ISO_8859_1);
        int sep = payloadText.indexOf("**");
        if (sep >= 0 && sep + 2 < payloadText.length()) {
            String encoded = payloadText.substring(sep + 2).trim();
            try {
                byte[] decoded = Base64.decode(encoded, Base64.DEFAULT);
                return stripJsonComments(new String(decoded, StandardCharsets.UTF_8));
            } catch (Throwable ignored) {
            }
        }
        return stripJsonComments(new String(data, StandardCharsets.UTF_8));
    }

    public static String extract(String raw) {
        if (TextUtils.isEmpty(raw)) {
            return "";
        }
        return extract(raw.getBytes(StandardCharsets.ISO_8859_1));
    }

    private static int firstNonWhitespace(byte[] data) {
        for (int i = 0; i < data.length; i++) {
            byte b = data[i];
            if (b != ' ' && b != '\n' && b != '\r' && b != '\t') {
                return i;
            }
        }
        return -1;
    }

    private static int findJpegEoi(byte[] data) {
        for (int i = data.length - 2; i >= 0; i--) {
            if ((data[i] & 0xFF) == 0xFF && (data[i + 1] & 0xFF) == 0xD9) {
                return i;
            }
        }
        return -1;
    }

    private static String stripJsonComments(String json) {
        if (TextUtils.isEmpty(json)) {
            return json;
        }
        return JSONC_LINE.matcher(json).replaceAll("").trim();
    }
}
