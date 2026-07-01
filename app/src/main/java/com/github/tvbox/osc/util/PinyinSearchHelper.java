package com.github.tvbox.osc.util;

import android.text.TextUtils;

import com.github.promeg.pinyinhelper.Pinyin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * 简拼/全拼搜索辅助：将字母输入匹配为中文关键词
 */
public class PinyinSearchHelper {

    private static final String[] BUILTIN_TITLES = {
            "完美世界", "镖人", "爱情有烟火", "庆余年", "斗罗大陆",
            "凡人修仙传", "诛仙", "深潜", "白夜破晓", "西北岁月",
            "大奉打更人", "九重紫", "猎罪图鉴", "冬至", "我是刑警",
            "永夜星河", "小巷人家", "黑白森林", "蜀锦人家", "太阳星辰",
            "婚内婚外", "白夜追凶", "唐朝诡事录", "狂飙", "三体",
            "漫长的季节", "莲花楼", "长相思", "与凤行", "墨雨云间",
            "狐妖小红娘", "哪吒之魔童闹海", "封神第二部", "射雕英雄传",
            "流浪地球", "热辣滚烫", "飞驰人生", "第二十条", "熊出没",
            "海贼王", "名侦探柯南", "进击的巨人", "鬼灭之刃",
            "吞噬星空", "仙逆", "斗破苍穹", "武动乾坤", "沧元图", "神印王座",
            "一念永恒", "全职法师", "遮天", "秦时明月", "画江湖之不良人",
            "少年歌行", "紫川", "元龙", "灵笼", "凡人修仙传",
            "择天记", "雪鹰领主", "斗战神", "墓王之王", "不良人"
    };

    public static void init() {
        try {
            Pinyin.init(Pinyin.newConfig());
        } catch (Throwable ignored) {
        }
    }

    public static boolean isLatinInput(String text) {
        return !TextUtils.isEmpty(text) && text.matches("^[A-Za-z0-9\\s]+$");
    }

    public static String toJianpin(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (Pinyin.isChinese(c)) {
                String py = Pinyin.toPinyin(c);
                if (!TextUtils.isEmpty(py)) {
                    sb.append(py.charAt(0));
                }
            }
        }
        return sb.toString().toUpperCase(Locale.ROOT);
    }

    public static String toFullPinyin(String text) {
        if (TextUtils.isEmpty(text)) {
            return "";
        }
        return Pinyin.toPinyin(text, "").toLowerCase(Locale.ROOT);
    }

    /**
     * 根据简拼/全拼前缀匹配语料中的中文标题
     */
    public static List<String> matchLatin(String latin, List<String> corpus) {
        if (TextUtils.isEmpty(latin)) {
            return Collections.emptyList();
        }
        String upper = latin.trim().toUpperCase(Locale.ROOT);
        String lower = latin.trim().toLowerCase(Locale.ROOT);
        LinkedHashSet<String> exact = new LinkedHashSet<>();
        LinkedHashSet<String> prefix = new LinkedHashSet<>();
        LinkedHashSet<String> fullPy = new LinkedHashSet<>();

        List<String> all = new ArrayList<>();
        Collections.addAll(all, BUILTIN_TITLES);
        if (corpus != null) {
            for (String item : corpus) {
                if (!TextUtils.isEmpty(item) && !all.contains(item)) {
                    all.add(item);
                }
            }
        }

        for (String title : all) {
            if (TextUtils.isEmpty(title)) {
                continue;
            }
            String jp = toJianpin(title);
            String fp = toFullPinyin(title);
            if (jp.equals(upper)) {
                exact.add(title);
            } else if (jp.startsWith(upper)) {
                prefix.add(title);
            } else if (fp.startsWith(lower)) {
                fullPy.add(title);
            }
        }

        List<String> result = new ArrayList<>();
        result.addAll(exact);
        result.addAll(prefix);
        result.addAll(fullPy);
        if (result.size() > 12) {
            return new ArrayList<>(result.subList(0, 12));
        }
        return result;
    }

    /**
     * 将搜索输入解析为可提交给 CMS 的中文关键词列表
     */
    public static List<String> resolveSearchKeywords(String input, List<String> corpus) {
        if (TextUtils.isEmpty(input)) {
            return Collections.emptyList();
        }
        String trimmed = input.trim();
        if (!isLatinInput(trimmed)) {
            return Collections.singletonList(trimmed);
        }
        List<String> matched = matchLatin(trimmed, corpus);
        if (!matched.isEmpty()) {
            return matched;
        }
        return Collections.emptyList();
    }

    /** 从联想词/带集数标题中提取核心片名，如「吞噬星空229」→「吞噬星空」 */
    public static String extractCoreTitle(String title) {
        if (TextUtils.isEmpty(title)) {
            return title;
        }
        String t = title.trim().split(" ")[0];
        StringBuilder cn = new StringBuilder();
        for (char c : t.toCharArray()) {
            if (Pinyin.isChinese(c)) {
                cn.append(c);
            } else if (cn.length() > 0) {
                break;
            }
        }
        String core = cn.toString();
        if (core.length() >= 2) {
            return core;
        }
        return t.replaceAll("<|>|《|》", "").trim();
    }
}
