package com.github.tvbox.osc.util;

import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.ImageView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.ui.activity.SearchActivity;
import com.github.tvbox.osc.picasso.RoundTransformation;
import com.github.tvbox.osc.util.DefaultConfig;
import com.github.tvbox.osc.util.MD5;
import com.orhanobut.hawk.Hawk;
import com.squareup.picasso.Picasso;

import me.jessyan.autosize.utils.AutoSizeUtils;

/**
 * 手机竖屏 UI 辅助：布局选择与封面加载。
 */
public final class MobileUiHelper {

    private MobileUiHelper() {
    }

    public static boolean useMobileUi(Context context) {
        return true;
    }

    public static int dp(Context context, float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    public static int getGridItemLayout(Context context) {
        return useMobileUi(context) ? R.layout.item_grid_mobile : R.layout.item_grid;
    }

    public static int getSearchItemLayout(Context context) {
        return useMobileUi(context) ? R.layout.item_search_mobile : R.layout.item_search;
    }

    public static int getHotWordItemLayout(Context context) {
        return useMobileUi(context) ? R.layout.item_search_word_hot_mobile : R.layout.item_search_word_hot;
    }

    public static int getHomeGridColumns(Context context) {
        return Hawk.get(HawkConfig.HOME_GRID_COLS, useMobileUi(context) ? 3 : 5);
    }

    public static void openSearch(BaseActivity activity, String keyword) {
        if (activity == null || TextUtils.isEmpty(keyword)) {
            return;
        }
        Bundle bundle = new Bundle();
        bundle.putString("title", keyword.trim());
        activity.jumpActivity(SearchActivity.class, bundle);
    }

    public static int getSeriesItemLayout(Context context) {
        return useMobileUi(context) ? R.layout.item_series_mobile : R.layout.item_series;
    }

    public static void loadPoster(Context context, ImageView imageView, String pic, String cacheKey) {
        if (TextUtils.isEmpty(pic)) {
            imageView.setImageResource(R.drawable.img_loading_placeholder);
            return;
        }
        int width;
        int height;
        int radius;
        if (useMobileUi(context)) {
            width = dp(context, 110);
            height = dp(context, 148);
            radius = dp(context, 6);
        } else {
            width = AutoSizeUtils.mm2px(context, 300);
            height = AutoSizeUtils.mm2px(context, 400);
            radius = AutoSizeUtils.mm2px(context, 10);
        }
        Picasso.get()
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
