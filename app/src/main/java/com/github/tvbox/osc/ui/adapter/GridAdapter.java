package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.util.UiHelper;

import java.util.ArrayList;

/**
 * @author pj567
 * @date :2020/12/21
 * @description:
 */
public class GridAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    private int previewWidth;

    public GridAdapter() {
        super(UiHelper.getGridItemLayout(), new ArrayList<>());
    }

    public void setPreviewWidth(int previewWidth) {
        this.previewWidth = previewWidth;
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        TextView tvYear = helper.getView(R.id.tvYear);
        if (item.year <= 0) {
            tvYear.setVisibility(View.GONE);
        } else {
            tvYear.setText(String.valueOf(item.year));
            tvYear.setVisibility(View.VISIBLE);
        }
        TextView tvLang = helper.getView(R.id.tvLang);
        tvLang.setVisibility(View.GONE);
        TextView tvArea = helper.getView(R.id.tvArea);
        tvArea.setVisibility(View.GONE);
        if (TextUtils.isEmpty(item.note)) {
            helper.setVisible(R.id.tvNote, false);
        } else {
            helper.setVisible(R.id.tvNote, true);
            helper.setText(R.id.tvNote, item.note);
        }
        helper.setText(R.id.tvName, item.name);
        helper.setText(R.id.tvActor, item.actor);
        ImageView ivThumb = helper.getView(R.id.ivThumb);
        String cacheKey = item.pic + "position=" + helper.getLayoutPosition();
        UiHelper.loadPoster(mContext, ivThumb, item.pic, cacheKey);
        if (previewWidth > 0) {
            ViewGroup.LayoutParams lp = helper.itemView.getLayoutParams();
            if (lp == null) {
                lp = new ViewGroup.LayoutParams(previewWidth, ViewGroup.LayoutParams.WRAP_CONTENT);
            } else {
                lp.width = previewWidth;
            }
            helper.itemView.setLayoutParams(lp);
        }
    }
}
