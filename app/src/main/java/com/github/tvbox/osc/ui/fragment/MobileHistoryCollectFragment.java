package com.github.tvbox.osc.ui.fragment;

import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.VodInfo;
import com.github.tvbox.osc.cache.RoomDataManger;
import com.github.tvbox.osc.cache.VodCollect;
import com.github.tvbox.osc.ui.activity.DetailActivity;
import com.github.tvbox.osc.ui.adapter.CollectAdapter;
import com.github.tvbox.osc.ui.adapter.HistoryAdapter;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.MobileUiHelper;

import java.util.List;

public class MobileHistoryCollectFragment extends BaseLazyFragment {
    private TextView tabHistory;
    private TextView tabCollect;
    private RecyclerView rvList;
    private HistoryAdapter historyAdapter;
    private CollectAdapter collectAdapter;
    private boolean showHistory = true;

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_mobile_history_collect;
    }

    @Override
    protected void init() {
        tabHistory = findViewById(R.id.tabHistory);
        tabCollect = findViewById(R.id.tabCollect);
        rvList = findViewById(R.id.rvList);
        historyAdapter = new HistoryAdapter();
        collectAdapter = new CollectAdapter();
        rvList.setLayoutManager(new GridLayoutManager(mContext, MobileUiHelper.getHomeGridColumns(mContext)));
        rvList.setAdapter(historyAdapter);

        tabHistory.setOnClickListener(v -> switchTab(true));
        tabCollect.setOnClickListener(v -> switchTab(false));
        historyAdapter.setOnItemClickListener(this::openHistoryItem);
        collectAdapter.setOnItemClickListener(this::openCollectItem);
        switchTab(true);
    }

    @Override
    protected void onFragmentResume() {
        super.onFragmentResume();
        refreshData();
    }

    private void switchTab(boolean history) {
        showHistory = history;
        tabHistory.setTextColor(history ? Color.parseColor("#0CADE2") : Color.parseColor("#BBFFFFFF"));
        tabCollect.setTextColor(history ? Color.parseColor("#BBFFFFFF") : Color.parseColor("#0CADE2"));
        rvList.setAdapter(history ? historyAdapter : collectAdapter);
        refreshData();
    }

    private void refreshData() {
        if (showHistory) {
            List<VodInfo> records = RoomDataManger.getAllVodRecord(100);
            historyAdapter.setNewData(records);
        } else {
            List<VodCollect> collects = RoomDataManger.getAllVodCollect();
            collectAdapter.setNewData(collects);
        }
    }

    private void openHistoryItem(BaseQuickAdapter adapter, android.view.View view, int position) {
        FastClickCheckUtil.check(view);
        VodInfo item = historyAdapter.getItem(position);
        if (item != null) {
            Bundle bundle = new Bundle();
            bundle.putString("id", item.id);
            bundle.putString("sourceKey", item.sourceKey);
            jumpActivity(DetailActivity.class, bundle);
        }
    }

    private void openCollectItem(BaseQuickAdapter adapter, android.view.View view, int position) {
        FastClickCheckUtil.check(view);
        VodCollect item = collectAdapter.getItem(position);
        if (item != null) {
            Bundle bundle = new Bundle();
            bundle.putString("id", item.vodId);
            bundle.putString("sourceKey", item.sourceKey);
            jumpActivity(DetailActivity.class, bundle);
        }
    }
}
