package com.github.tvbox.osc.ui.adapter;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.util.UiHelper;

import java.util.ArrayList;

public class PinyinAdapter extends BaseQuickAdapter<String, BaseViewHolder> {
    public PinyinAdapter() {
        super(UiHelper.getHotWordItemLayout(), new ArrayList<>());
    }

    @Override
    protected void convert(BaseViewHolder helper, String item) {
        helper.setText(R.id.tvSearchWord, item);
    }
}
