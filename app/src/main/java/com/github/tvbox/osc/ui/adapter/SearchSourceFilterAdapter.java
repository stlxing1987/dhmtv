package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;

import java.util.ArrayList;

public class SearchSourceFilterAdapter extends BaseQuickAdapter<SearchSourceFilterAdapter.FilterItem, BaseViewHolder> {

    private int selectedPosition = 0;

    public SearchSourceFilterAdapter() {
        super(R.layout.item_search_source_filter, new ArrayList<>());
    }

    public void setSelectedPosition(int position) {
        selectedPosition = position;
        notifyDataSetChanged();
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    @Override
    protected void convert(BaseViewHolder helper, FilterItem item) {
        String label = item.count > 0 ? item.name + "  " + item.count : item.name;
        helper.setText(R.id.tvName, label);
        helper.itemView.setSelected(helper.getLayoutPosition() == selectedPosition);
    }

    public static class FilterItem {
        public String sourceKey;
        public String name;
        public int count;

        public FilterItem(String sourceKey, String name, int count) {
            this.sourceKey = sourceKey;
            this.name = name;
            this.count = count;
        }
    }
}
