package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.widget.ImageView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.util.UiHelper;

import java.util.ArrayList;

public class SearchAdapter extends BaseQuickAdapter<Movie.Video, BaseViewHolder> {
    public SearchAdapter() {
        super(UiHelper.getSearchItemLayout(), new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, Movie.Video item) {
        helper.setText(R.id.tvName, item.name);
        SourceBean source = ApiConfig.get().getSource(item.sourceKey);
        String siteName = source != null ? source.getName() : item.sourceKey;
        helper.setText(R.id.tvSite, siteName);
        helper.setVisible(R.id.tvSite, !TextUtils.isEmpty(siteName));
        helper.setVisible(R.id.tvNote, item.note != null && !item.note.isEmpty());
        if (item.note != null && !item.note.isEmpty()) {
            helper.setText(R.id.tvNote, item.note);
        }
        ImageView ivThumb = helper.getView(R.id.ivThumb);
        String cacheKey = item.pic + "position=" + helper.getLayoutPosition();
        UiHelper.loadPoster(mContext, ivThumb, item.pic, cacheKey);
    }
}
