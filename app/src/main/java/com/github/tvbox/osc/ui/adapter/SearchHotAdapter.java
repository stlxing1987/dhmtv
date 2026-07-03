package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseMultiItemQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.chad.library.adapter.base.entity.MultiItemEntity;
import com.github.tvbox.osc.R;

import java.util.ArrayList;
import java.util.List;

public class SearchHotAdapter extends BaseMultiItemQuickAdapter<SearchHotAdapter.HotItem, BaseViewHolder> {

    public static final int TYPE_HEADER = 0;
    public static final int TYPE_WORD = 1;

    public SearchHotAdapter() {
        super(new ArrayList<>());
        addItemType(TYPE_HEADER, R.layout.item_search_hot_header);
        addItemType(TYPE_WORD, R.layout.item_search_word_split);
    }

    public void setSections(List<com.github.tvbox.osc.util.SearchHotHelper.Section> sections) {
        List<HotItem> items = new ArrayList<>();
        if (sections != null) {
            for (com.github.tvbox.osc.util.SearchHotHelper.Section section : sections) {
                if (section == null || section.words == null || section.words.isEmpty()) {
                    continue;
                }
                HotItem header = new HotItem(TYPE_HEADER, section.title);
                items.add(header);
                for (String word : section.words) {
                    items.add(new HotItem(TYPE_WORD, word));
                }
            }
        }
        setNewData(items);
    }

    @Override
    protected void convert(BaseViewHolder helper, HotItem item) {
        if (item.getItemType() == TYPE_HEADER) {
            helper.setText(R.id.tvHotHeader, item.text);
        } else {
            helper.setText(R.id.tvSearchWord, item.text);
        }
    }

    public static class HotItem implements MultiItemEntity {
        private final int type;
        public final String text;

        public HotItem(int type, String text) {
            this.type = type;
            this.text = text;
        }

        @Override
        public int getItemType() {
            return type;
        }
    }
}
