package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.StoreBean;
import com.github.tvbox.osc.event.RefreshEvent;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.adapter.StoreDialogAdapter;
import com.github.tvbox.osc.ui.tv.QRCodeGen;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.StoreConfigHelper;
import com.hjq.permissions.OnPermissionCallback;
import com.hjq.permissions.Permission;
import com.hjq.permissions.XXPermissions;
import com.orhanobut.hawk.Hawk;
import com.owen.tvrecyclerview.widget.TvRecyclerView;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import me.jessyan.autosize.utils.AutoSizeUtils;

public class ApiDialog extends BaseDialog {
    private ImageView ivQRCode;
    private TextView tvAddress;
    private EditText inputName;
    private EditText inputUrl;
    private TvRecyclerView storeList;
    private StoreDialogAdapter storeAdapter;
    private String selectedUrl = "";

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void refresh(RefreshEvent event) {
        if (event.type == RefreshEvent.TYPE_API_URL_CHANGE) {
            if (event.obj instanceof StoreBean) {
                StoreBean store = (StoreBean) event.obj;
                inputName.setText(store.name);
                inputUrl.setText(store.url);
                refreshStoreList();
            } else if (event.obj instanceof String) {
                inputUrl.setText((String) event.obj);
            }
        }
    }

    public ApiDialog(@NonNull @NotNull Context context) {
        super(context, R.style.CustomDialogStyleDim);
        setContentView(R.layout.dialog_api);
        setupOutsideDismiss(findViewById(R.id.dialogRoot), findViewById(R.id.dialogContent));
        ivQRCode = findViewById(R.id.ivQRCode);
        tvAddress = findViewById(R.id.tvAddress);
        inputName = findViewById(R.id.inputName);
        inputUrl = findViewById(R.id.inputUrl);
        storeList = findViewById(R.id.storeList);
        selectedUrl = getSelectedStoreUrl();
        storeAdapter = new StoreDialogAdapter(new StoreDialogAdapter.StoreDialogInterface() {
            @Override
            public void click(StoreBean store) {
                applyStore(store);
            }

            @Override
            public void del(StoreBean store) {
                StoreConfigHelper.removeStore(store.url);
                if (TextUtils.isEmpty(Hawk.get(HawkConfig.API_URL, ""))) {
                    inputName.setText("");
                    inputUrl.setText("");
                }
                refreshStoreList();
            }
        });
        storeList.setAdapter(storeAdapter);
        refreshStoreList();
        initCurrentInput();
        initTvFocus();
        findViewById(R.id.inputSubmit).setOnClickListener(v -> submitInput());
        findViewById(R.id.storagePermission).setOnClickListener(v -> requestStoragePermission());
        refreshQRCode();
    }

    private void initCurrentInput() {
        StoreBean current = StoreConfigHelper.getCurrentStore();
        if (current != null) {
            inputName.setText(current.name);
            inputUrl.setText(current.url);
        }
    }

    private void initTvFocus() {
        TextView storagePermission = findViewById(R.id.storagePermission);
        TextView inputSubmit = findViewById(R.id.inputSubmit);
        storeList.setOnInBorderKeyEventListener((direction, focused) -> {
            if (direction == View.FOCUS_DOWN) {
                inputName.requestFocus();
                return true;
            }
            return false;
        });
        storeList.setOnItemListener(new TvRecyclerView.OnItemListener() {
            @Override
            public void onItemPreSelected(TvRecyclerView parent, View itemView, int position) {
            }

            @Override
            public void onItemSelected(TvRecyclerView parent, View itemView, int position) {
            }

            @Override
            public void onItemClick(TvRecyclerView parent, View itemView, int position) {
                View focus = itemView.findFocus();
                if (focus != null && focus.getId() == R.id.tvDel) {
                    focus.performClick();
                    return;
                }
                View nameView = itemView.findViewById(R.id.tvName);
                if (nameView != null) {
                    nameView.performClick();
                }
            }
        });
        inputName.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                if (storeAdapter.getItemCount() > 0) {
                    storeList.requestFocus();
                    return true;
                }
            }
            return false;
        });
        inputUrl.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                inputSubmit.requestFocus();
                return true;
            }
            return false;
        });
        storagePermission.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                inputName.requestFocus();
                return true;
            }
            return false;
        });
        inputSubmit.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_UP) {
                inputUrl.requestFocus();
                return true;
            }
            return false;
        });
        storeList.post(() -> {
            if (storeAdapter.getItemCount() > 0) {
                storeList.setSelectedPosition(0);
                storeList.requestFocus();
            } else {
                inputName.requestFocus();
            }
        });
    }

    @Override
    public void show() {
        super.show();
        storeList.post(() -> {
            if (storeAdapter.getItemCount() > 0) {
                storeList.setSelectedPosition(0);
                storeList.requestFocus();
            } else {
                inputName.requestFocus();
            }
        });
    }

    private String getSelectedStoreUrl() {
        String indexUrl = Hawk.get(HawkConfig.API_INDEX_URL, "");
        if (!TextUtils.isEmpty(indexUrl)) {
            return indexUrl;
        }
        return Hawk.get(HawkConfig.API_URL, "");
    }

    private void refreshStoreList() {
        List<StoreBean> stores = StoreConfigHelper.getStoreList();
        selectedUrl = getSelectedStoreUrl();
        storeAdapter.setData(stores, selectedUrl);
    }

    private void submitInput() {
        String name = inputName.getText().toString().trim();
        String url = inputUrl.getText().toString().trim();
        if (TextUtils.isEmpty(url) || !(url.startsWith("http") || url.startsWith("clan"))) {
            Toast.makeText(getContext(), "请输入有效的配置地址", Toast.LENGTH_SHORT).show();
            return;
        }
        StoreConfigHelper.addOrUpdateStore(name, url);
        StoreBean store = StoreConfigHelper.findByUrl(StoreConfigHelper.getStoreList(), url);
        if (store == null) {
            store = new StoreBean(TextUtils.isEmpty(name) ? StoreConfigHelper.buildDefaultName(url) : name, url);
        }
        applyStore(store);
    }

    private void applyStore(StoreBean store) {
        if (store == null || TextUtils.isEmpty(store.url)) {
            return;
        }
        selectedUrl = store.url;
        StoreConfigHelper.selectStore(store);
        refreshStoreList();
        final String storeUrl = store.url;
        final OnListener changeListener = listener;
        dismiss();
        if (changeListener != null) {
            new Handler(Looper.getMainLooper()).post(() -> changeListener.onchange(storeUrl));
        }
    }

    private void requestStoragePermission() {
        if (XXPermissions.isGranted(getContext(), Permission.Group.STORAGE)) {
            Toast.makeText(getContext(), "已获得存储权限", Toast.LENGTH_SHORT).show();
        } else {
            XXPermissions.with(getContext())
                    .permission(Permission.Group.STORAGE)
                    .request(new OnPermissionCallback() {
                        @Override
                        public void onGranted(List<String> permissions, boolean all) {
                            if (all) {
                                Toast.makeText(getContext(), "已获得存储权限", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onDenied(List<String> permissions, boolean never) {
                            if (never) {
                                Toast.makeText(getContext(), "获取存储权限失败,请在系统设置中开启", Toast.LENGTH_SHORT).show();
                                XXPermissions.startPermissionActivity((Activity) getContext(), permissions);
                            } else {
                                Toast.makeText(getContext(), "获取存储权限失败", Toast.LENGTH_SHORT).show();
                            }
                        }
                    });
        }
    }

    private void refreshQRCode() {
        String address = ControlManager.get().getAddress(false);
        tvAddress.setText(address);
        ivQRCode.setImageBitmap(QRCodeGen.generateBitmap(address, AutoSizeUtils.mm2px(getContext(), 260), AutoSizeUtils.mm2px(getContext(), 260)));
    }

    public void setOnListener(OnListener listener) {
        this.listener = listener;
    }

    private OnListener listener = null;

    public interface OnListener {
        void onchange(String api);
    }
}
