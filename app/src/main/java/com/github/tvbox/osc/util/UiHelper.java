package com.github.tvbox.osc.util;

import android.content.Context;
import android.text.TextUtils;
import android.widget.ImageView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.picasso.RoundTransformation;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.orhanobut.hawk.Hawk;

import android.os.Bundle;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * TV 专版 UI 辅助：布局选择与封面加载。
 */
public final class UiHelper {

    private UiHelper() {
    }

    public static int getGridItemLayout() {
        return R.layout.item_grid;
    }

    public static int getSearchItemLayout() {
        return R.layout.item_search;
    }

    public static int getHotWordItemLayout() {
        return R.layout.item_search_word_hot;
    }

    public static int getHomeGridColumns(Context context) {
        return Hawk.get(HawkConfig.HOME_GRID_COLS, 5);
    }

    public static void openSearch(BaseActivity activity, String keyword) {
        if (activity == null || TextUtils.isEmpty(keyword)) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putString("title", keyword.trim());
        activity.jumpActivity(SearchActivity.class, bundle);
    }

    public static int getSeriesItemLayout() {
        return R.layout.item_series;
    }

    public static void loadPoster(Context context, ImageView imageView, String pic, String cacheKey) {
        if (TextUtils.isEmpty(pic)) {
            imageView.setImageResource(R.drawable.img_loading_placeholder);
            return;
        }
        int width = AutoSizeUtils.mm2px(context, 300);
        int height = AutoSizeUtils.mm2px(context, 400);
        int radius = AutoSizeUtils.mm2px(context, 10);
        com.squareup.picasso.Picasso.get()
                .load(DefaultConfig.checkReplaceProxy(pic))
                .transform(new RoundTransformation(MD5.string2MD5(cacheKey))
                        .centerCorp(true)
                        .override(width, height)
                        .roundRadius(radius, RoundTransformation.RoundType.ALL))
                .placeholder(R.drawable.img_loading_placeholder)
                .error(R.drawable.img_loading_placeholder)
                .into(imageView);
    }
}
