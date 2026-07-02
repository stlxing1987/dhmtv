package com.github.tvbox.osc.ui.adapter;

import android.graphics.Color;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.MovieSort;

import java.util.ArrayList;

public class MobileTabAdapter extends BaseQuickAdapter<MovieSort.SortData, BaseViewHolder> {
    private int selected = 0;

    public MobileTabAdapter() {
        super(R.layout.item_mobile_tab, new ArrayList<>());
    }

    public void setSelected(int position) {
        selected = position;
        notifyDataSetChanged();
    }

    @Override
    protected void convert(BaseViewHolder helper, MovieSort.SortData item) {
        TextView tv = helper.getView(R.id.tvTabTitle);
        tv.setText(item.name);
        boolean active = helper.getAdapterPosition() == selected;
        tv.setBackgroundResource(active ? R.drawable.shape_mobile_tab_selected : R.drawable.shape_mobile_tab_normal);
        tv.setTextColor(active ? Color.parseColor("#0CADE2") : Color.parseColor("#BBFFFFFF"));
    }
}
