package com.github.tvbox.osc.ui.activity;

import android.graphics.Color;
import android.os.Handler;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.ui.dialog.TipDialog;
import com.github.tvbox.osc.ui.fragment.MobileHistoryCollectFragment;
import com.github.tvbox.osc.ui.fragment.MobileHomeFragment;
import com.github.tvbox.osc.ui.fragment.MobileMineFragment;
import com.github.tvbox.osc.util.AppManager;
import com.github.tvbox.osc.util.AppUpdateHelper;
import com.github.tvbox.osc.util.SettingUiHelper;
import com.github.tvbox.osc.viewmodel.SourceViewModel;

import java.util.ArrayList;
import java.util.List;

public class MobileHomeActivity extends BaseActivity {
    private FrameLayout mobileContainer;
    private LinearLayout navHome;
    private LinearLayout navLive;
    private LinearLayout navHistory;
    private LinearLayout navMine;
    private final List<LinearLayout> navItems = new ArrayList<>();
    private final List<ImageView> navIcons = new ArrayList<>();
    private final List<TextView> navLabels = new ArrayList<>();
    private final List<Fragment> fragments = new ArrayList<>();
    private int currentNav = 0;
    private boolean dataInitOk = false;
    private boolean jarInitOk = false;
    private boolean useCacheConfig = false;
    private final Handler mHandler = new Handler();
    private final List<Runnable> configReadyCallbacks = new ArrayList<>();
    private SourceViewModel sourceViewModel;

    @Override
    protected int getLayoutResID() {
        return R.layout.activity_mobile_home;
    }

    @Override
    protected void init() {
        getWindow().setBackgroundDrawableResource(SettingUiHelper.WALLPAPER_RES[SettingUiHelper.getWallpaperIndex()]);
        ControlManager.get().startServer();
        sourceViewModel = new ViewModelProvider(this).get(SourceViewModel.class);
        mobileContainer = findViewById(R.id.mobileContainer);
        navHome = findViewById(R.id.navHome);
        navLive = findViewById(R.id.navLive);
        navHistory = findViewById(R.id.navHistory);
        navMine = findViewById(R.id.navMine);
        navItems.add(navHome);
        navItems.add(navLive);
        navItems.add(navHistory);
        navItems.add(navMine);
        for (LinearLayout nav : navItems) {
            navIcons.add((ImageView) nav.getChildAt(0));
            navLabels.add((TextView) nav.getChildAt(1));
        }
        navHome.setOnClickListener(v -> switchNav(0));
        navLive.setOnClickListener(v -> jumpActivity(LivePlayActivity.class));
        navHistory.setOnClickListener(v -> switchNav(2));
        navMine.setOnClickListener(v -> switchNav(3));

        fragments.add(new MobileHomeFragment());
        fragments.add(new MobileHomeFragment());
        fragments.add(new MobileHistoryCollectFragment());
        fragments.add(new MobileMineFragment());

        switchNav(0);
        initData();
    }

    public boolean isConfigReady() {
        return dataInitOk && jarInitOk;
    }

    public void runWhenConfigReady(Runnable task) {
        if (isConfigReady()) {
            task.run();
        } else {
            configReadyCallbacks.add(task);
        }
    }

    public void reloadConfig(boolean useCache) {
        reloadConfig(useCache, false);
    }

    public void reloadConfig(boolean useCache, boolean suppressLineDialog) {
        useCacheConfig = useCache;
        dataInitOk = false;
        jarInitOk = false;
        ApiConfig.get().setSuppressUrlIndexDialog(suppressLineDialog);
        ApiConfig.get().cancelPendingLoad();
        configReadyCallbacks.clear();
        mHandler.post(this::initData);
    }

    private void notifyConfigReady() {
        for (Runnable task : configReadyCallbacks) {
            task.run();
        }
        configReadyCallbacks.clear();
        Fragment fragment = getSupportFragmentManager().findFragmentById(R.id.mobileContainer);
        if (fragment instanceof MobileHomeFragment) {
            ((MobileHomeFragment) fragment).reloadContent();
        }
        AppUpdateHelper.checkOnStartup(this);
    }

    private void switchNav(int index) {
        currentNav = index;
        int activeColor = Color.parseColor("#0CADE2");
        int normalColor = Color.parseColor("#BBFFFFFF");
        for (int i = 0; i < navLabels.size(); i++) {
            boolean active = i == index;
            navLabels.get(i).setTextColor(active ? activeColor : normalColor);
            navIcons.get(i).setColorFilter(active ? activeColor : normalColor);
        }
        if (index == 1) {
            return;
        }
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.mobileContainer, fragments.get(index))
                .commitAllowingStateLoss();
    }

    private void initData() {
        if (dataInitOk && jarInitOk) {
            SourceBean home = ApiConfig.get().getHomeSourceBean();
            if (home != null && home.getKey() != null) {
                sourceViewModel.getSort(home.getKey());
            }
            notifyConfigReady();
            return;
        }
        if (dataInitOk && !jarInitOk) {
            if (!ApiConfig.get().getSpider().isEmpty()) {
                ApiConfig.get().loadJar(useCacheConfig, ApiConfig.get().getSpider(), new ApiConfig.LoadConfigCallback() {
                    @Override
                    public void success() {
                        jarInitOk = true;
                        mHandler.post(MobileHomeActivity.this::initData);
                    }

                    @Override
                    public void retry() {
                    }

                    @Override
                    public void error(String msg) {
                        jarInitOk = true;
                        mHandler.post(MobileHomeActivity.this::initData);
                    }
                });
            }
            return;
        }
        ApiConfig.get().loadConfig(useCacheConfig, new ApiConfig.LoadConfigCallback() {
            TipDialog dialog = null;

            @Override
            public void retry() {
                mHandler.post(MobileHomeActivity.this::initData);
            }

            @Override
            public void success() {
                dataInitOk = true;
                jarInitOk = ApiConfig.get().getSpider().isEmpty();
                mHandler.postDelayed(MobileHomeActivity.this::initData, 50);
            }

            @Override
            public void error(String msg) {
                if ("-1".equalsIgnoreCase(msg)) {
                    dataInitOk = true;
                    jarInitOk = true;
                    mHandler.post(MobileHomeActivity.this::initData);
                    return;
                }
                mHandler.post(() -> {
                    if (dialog == null) {
                        dialog = new TipDialog(MobileHomeActivity.this, msg, "重试", "取消", new TipDialog.OnListener() {
                            @Override
                            public void left() {
                                dialog.hide();
                                initData();
                            }

                            @Override
                            public void right() {
                                dataInitOk = true;
                                jarInitOk = true;
                                dialog.hide();
                                initData();
                            }

                            @Override
                            public void cancel() {
                                dataInitOk = true;
                                jarInitOk = true;
                                dialog.hide();
                                initData();
                            }
                        });
                    }
                    if (!dialog.isShowing()) {
                        dialog.show();
                    }
                });
            }
        }, this);
    }

    @Override
    public void onBackPressed() {
        if (currentNav != 0) {
            switchNav(0);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mHandler.removeCallbacksAndMessages(null);
        ControlManager.get().stopServer();
        if (isFinishing()) {
            AppManager.getInstance().appExit(0);
        }
    }
}
