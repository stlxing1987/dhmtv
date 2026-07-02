package com.github.tvbox.osc.ui.adapter;

import android.graphics.Color;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;

import java.util.ArrayList;

public class MobileLineAdapter extends BaseQuickAdapter<String, BaseViewHolder> {
    private int selected = -1;

    public MobileLineAdapter() {
        super(R.layout.item_mobile_line_row, new ArrayList<>());
    }

    public void setSelected(int position) {
        selected = position;
        notifyDataSetChanged();
    }

    @Override
    protected void convert(BaseViewHolder helper, String item) {
        TextView tv = helper.getView(R.id.tvLineName);
        tv.setText(item);
        boolean active = helper.getAdapterPosition() == selected;
        tv.setBackgroundColor(active ? Color.parseColor("#220CADE2") : Color.TRANSPARENT);
        tv.setTextColor(active ? Color.parseColor("#0CADE2") : Color.parseColor("#333333"));
    }
}
