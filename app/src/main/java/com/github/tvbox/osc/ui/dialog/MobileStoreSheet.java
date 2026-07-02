package com.github.tvbox.osc.ui.dialog;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.bean.StoreBean;
import com.github.tvbox.osc.ui.adapter.MobileLineAdapter;
import com.github.tvbox.osc.ui.adapter.MobileStoreAdapter;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.StoreConfigHelper;
import com.orhanobut.hawk.Hawk;

import java.util.ArrayList;
import java.util.List;

public class MobileStoreSheet extends Dialog {

    public interface Listener {
        void onLineApplied();
    }

    private final Listener listener;
    private MobileStoreAdapter storeAdapter;
    private MobileLineAdapter lineAdapter;
    private StoreBean selectedStore;
    private List<ApiConfig.UrlIndexItem> lineItems = new ArrayList<>();

    public MobileStoreSheet(@NonNull Context context, Listener listener) {
        super(context, R.style.CustomDialogStyleDim);
        this.listener = listener;
        initView();
    }

    private void initView() {
        View content = LayoutInflater.from(getContext()).inflate(R.layout.bottom_sheet_store_manager, null, false);
        setContentView(content);
        Window window = getWindow();
        if (window != null) {
            window.setGravity(Gravity.BOTTOM);
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.width = WindowManager.LayoutParams.MATCH_PARENT;
            lp.height = (int) (getContext().getResources().getDisplayMetrics().heightPixels * 0.62f);
            window.setAttributes(lp);
        }

        RecyclerView rvStores = content.findViewById(R.id.rvStores);
        RecyclerView rvLines = content.findViewById(R.id.rvLines);
        storeAdapter = new MobileStoreAdapter();
        lineAdapter = new MobileLineAdapter();
        rvStores.setLayoutManager(new LinearLayoutManager(getContext()));
        rvLines.setLayoutManager(new LinearLayoutManager(getContext()));
        rvStores.setAdapter(storeAdapter);
        rvLines.setAdapter(lineAdapter);

        content.findViewById(R.id.btnRefresh).setOnClickListener(v -> loadLinesForSelectedStore());
        content.findViewById(R.id.btnAdd).setOnClickListener(v -> showAddDialog());
        storeAdapter.setOnItemClickListener((adapter, view, position) -> previewStore(storeAdapter.getItem(position), position));
        lineAdapter.setOnItemClickListener((adapter, view, position) -> applyLine(position));

        refreshStores();
    }

    private void refreshStores() {
        List<StoreBean> stores = StoreConfigHelper.getStoreList();
        storeAdapter.setNewData(stores);
        if (stores.isEmpty()) {
            lineAdapter.setNewData(new ArrayList<>());
            return;
        }
        String currentUrl = Hawk.get(HawkConfig.API_URL, "");
        String indexUrl = Hawk.get(HawkConfig.API_INDEX_URL, "");
        int selected = 0;
        for (int i = 0; i < stores.size(); i++) {
            String url = stores.get(i).url;
            if (TextUtils.equals(url, currentUrl) || TextUtils.equals(url, indexUrl)) {
                selected = i;
                break;
            }
        }
        previewStore(stores.get(selected), selected);
    }

    private void previewStore(StoreBean store, int position) {
        selectedStore = store;
        storeAdapter.setSelected(position);
        loadLinesForSelectedStore();
    }

    private void loadLinesForSelectedStore() {
        if (selectedStore == null || TextUtils.isEmpty(selectedStore.url)) {
            lineAdapter.setNewData(new ArrayList<>());
            return;
        }
        lineAdapter.setNewData(new ArrayList<>());
        ApiConfig.get().fetchUrlIndexList(selectedStore.url, new ApiConfig.UrlIndexCallback() {
            @Override
            public void onSuccess(List<ApiConfig.UrlIndexItem> items) {
                lineItems = items == null ? new ArrayList<>() : items;
                List<String> names = new ArrayList<>();
                for (ApiConfig.UrlIndexItem item : lineItems) {
                    names.add(item.name);
                }
                lineAdapter.setNewData(names);
                int selected = 0;
                String currentApi = Hawk.get(HawkConfig.API_URL, "");
                String currentLine = ApiConfig.get().getCurrentLineName();
                for (int i = 0; i < lineItems.size(); i++) {
                    ApiConfig.UrlIndexItem item = lineItems.get(i);
                    if (TextUtils.equals(item.url, currentApi)
                            || (!TextUtils.isEmpty(currentLine) && TextUtils.equals(item.name, currentLine))) {
                        selected = i;
                        break;
                    }
                }
                lineAdapter.setSelected(selected);
            }

            @Override
            public void onError(String msg) {
                lineItems = new ArrayList<>();
                lineAdapter.setNewData(new ArrayList<>());
            }
        });
    }

    private void applyLine(int position) {
        if (selectedStore == null || lineItems == null || position < 0 || position >= lineItems.size()) {
            return;
        }
        lineAdapter.setSelected(position);
        StoreConfigHelper.selectStore(selectedStore);
        Hawk.put(HawkConfig.API_INDEX_URL, selectedStore.url);
        Hawk.put(HawkConfig.API_LINE_LIST, StoreConfigHelper.serializeLines(lineItems));
        ApiConfig.get().switchLine(lineItems.get(position));
        dismiss();
        if (listener != null) {
            listener.onLineApplied();
        }
    }

    private void showAddDialog() {
        LinearLayout layout = new LinearLayout(getContext());
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getContext().getResources().getDisplayMetrics().density);
        layout.setPadding(pad, pad, pad, pad);
        EditText inputName = new EditText(getContext());
        inputName.setHint("仓库名称（可选）");
        EditText inputUrl = new EditText(getContext());
        inputUrl.setHint("配置地址 http://...");
        layout.addView(inputName);
        layout.addView(inputUrl);
        new AlertDialog.Builder(getContext())
                .setTitle("添加仓库")
                .setView(layout)
                .setPositiveButton("确定", (d, w) -> {
                    String name = inputName.getText().toString().trim();
                    String url = inputUrl.getText().toString().trim();
                    if (TextUtils.isEmpty(url) || !(url.startsWith("http") || url.startsWith("clan"))) {
                        Toast.makeText(getContext(), "请输入有效的配置地址", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    StoreConfigHelper.addOrUpdateStore(name, url);
                    StoreBean store = StoreConfigHelper.findByUrl(StoreConfigHelper.getStoreList(), url);
                    refreshStores();
                    if (store != null) {
                        int idx = StoreConfigHelper.getStoreList().indexOf(store);
                        previewStore(store, Math.max(idx, 0));
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }
}
