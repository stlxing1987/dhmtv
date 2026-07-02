package com.github.tvbox.osc.ui.adapter;

import android.text.TextUtils;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.MobileCategorySection;
import com.github.tvbox.osc.bean.Movie;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.MobileUiHelper;

import java.util.ArrayList;

public class MobileCategorySectionAdapter extends BaseQuickAdapter<MobileCategorySection, BaseViewHolder> {

    public interface MoreClickListener {
        void onMore(MobileCategorySection section);
    }

    private MoreClickListener moreClickListener;

    public MobileCategorySectionAdapter() {
        super(R.layout.item_mobile_category_section, new ArrayList<>());
    }

    public void setMoreClickListener(MoreClickListener listener) {
        this.moreClickListener = listener;
    }

    @Override
    protected void convert(BaseViewHolder helper, MobileCategorySection item) {
        TextView title = helper.getView(R.id.tvSectionTitle);
        TextView more = helper.getView(R.id.btnSectionMore);
        RecyclerView preview = helper.getView(R.id.rvSectionPreview);
        title.setText(item.sort != null ? item.sort.name : "");
        more.setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            if (moreClickListener != null) {
                moreClickListener.onMore(item);
            }
        });
        GridAdapter adapter = (GridAdapter) preview.getTag();
        if (adapter == null) {
            adapter = new GridAdapter();
            adapter.setPreviewWidth(MobileUiHelper.dp(mContext, 96));
            preview.setLayoutManager(new LinearLayoutManager(mContext, LinearLayoutManager.HORIZONTAL, false));
            preview.setNestedScrollingEnabled(false);
            preview.setAdapter(adapter);
            preview.setTag(adapter);
            adapter.setOnItemClickListener((a, view, position) -> {
                FastClickCheckUtil.check(view);
                Movie.Video video = ((GridAdapter) a).getItem(position);
                if (video == null || TextUtils.isEmpty(video.name)) {
                    return;
                }
                if (mContext instanceof BaseActivity) {
                    MobileUiHelper.openSearch((BaseActivity) mContext, video.name);
                }
            });
        }
        ViewGroup.LayoutParams lp = helper.itemView.getLayoutParams();
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) lp).bottomMargin = MobileUiHelper.dp(mContext, 4);
        }
        adapter.setNewData(item.loading ? new ArrayList<>() : item.videos);
    }
}
