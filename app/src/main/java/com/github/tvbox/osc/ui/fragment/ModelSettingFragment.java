package com.github.tvbox.osc.ui.fragment;

import android.app.AlertDialog;
import android.text.TextUtils;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;
import androidx.recyclerview.widget.DiffUtil;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.api.ApiConfig;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.bean.IJKCode;
import com.github.tvbox.osc.bean.LiveSourceBean;
import com.github.tvbox.osc.bean.SourceBean;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.ui.adapter.SelectDialogAdapter;
import com.github.tvbox.osc.ui.dialog.AboutDialog;
import com.github.tvbox.osc.ui.dialog.BackupDialog;
import com.github.tvbox.osc.ui.dialog.DriveAuthDialog;
import com.github.tvbox.osc.ui.dialog.SelectDialog;
import com.github.tvbox.osc.ui.dialog.XWalkInitDialog;
import com.github.tvbox.osc.util.AppUpdateChecker;
import com.github.tvbox.osc.util.AppUpdateHelper;
import com.github.tvbox.osc.util.ConfigDialogHelper;
import com.github.tvbox.osc.util.FastClickCheckUtil;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.MobileUiHelper;
import com.github.tvbox.osc.util.OkGoHelper;
import com.github.tvbox.osc.util.PlayerHelper;
import com.github.tvbox.osc.util.SettingUiHelper;
import com.github.tvbox.osc.util.StoreConfigHelper;
import com.github.tvbox.osc.util.UiModeSwitcher;
import com.orhanobut.hawk.Hawk;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import okhttp3.HttpUrl;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;

public class ModelSettingFragment extends BaseLazyFragment {
    private static final float SPLIT_WIDE = 0.28f;
    private static final float SPLIT_NORMAL = 0.46f;
    private static final float SPLIT_ACTION = 0.88f;

    private TextView tvDebugOpen;

    public static ModelSettingFragment newInstance() {
        return new ModelSettingFragment().setArguments();
    }

    public ModelSettingFragment setArguments() {
        return this;
    }

    @Override
    protected int getLayoutResID() {
        return MobileUiHelper.useMobileUi(mContext)
                ? R.layout.fragment_model_mobile
                : R.layout.fragment_model;
    }

    @Override
    protected void init() {
        tvDebugOpen = findViewById(R.id.tvDebugOpen);
        setupCellLabels();
        refreshValues();
        setupClickListeners();
        SettingActivity.callback = new SettingActivity.DevModeCallback() {
            @Override
            public void onChange() {
                findViewById(R.id.llDebug).setVisibility(View.VISIBLE);
            }
        };
    }

    @Override
    protected void onFragmentResume() {
        super.onFragmentResume();
        refreshValues();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        SettingActivity.callback = null;
    }

    private void setupCellLabels() {
        setCellLabel(R.id.llApi, "配置地址");
        setCellLabel(R.id.llLineSwitch, "线路切换");
        setCellLabel(R.id.llLiveApi, "直播地址");
        setCellLabel(R.id.llHomePref, "首选项");
        setCellLabel(R.id.llHomeRec, "首页推荐");
        setCellLabel(R.id.llHomeTab, "首页Tab");
        setCellLabel(R.id.llSearchView, "搜索展示");
        setCellLabel(R.id.llCacheDays, "缓存时长");
        setCellLabel(R.id.llPlay, "播放器");
        setCellLabel(R.id.llMediaCodec, "解码方式");
        setCellLabel(R.id.llWindowPreview, "窗口预览");
        setCellLabel(R.id.llScale, "画面缩放");
        setCellLabel(R.id.llChangeWallpaper, "换张壁纸");
        setCellLabel(R.id.llResetWallpaper, "重置壁纸");
        setCellLabel(R.id.llDns, "安全DNS");
        setCellLabel(R.id.llHistoryCount, "历史记录");
        setCellLabel(R.id.llSearchThread, "搜索线程");
        setCellLabel(R.id.llRender, "渲染方式");
        setCellLabel(R.id.llExoBuffer, "EXO缓冲");
        setCellLabel(R.id.llThunderCache, "荐片缓存");
        setCellLabel(R.id.llBackup, "备份还原");
        setCellLabel(R.id.llClearCache, "清空缓存");
        setCellLabel(R.id.llExoCache, "EXO缓存");
        setCellLabel(R.id.llResetApp, "重置App");
        setCellLabel(R.id.llParseWebVew, "嗅探Web");
        setCellLabel(R.id.llDriveAuth, "网盘授权");
        setCellLabel(R.id.llHomeApi, "首页源");
        setCellLabel(R.id.llHomeGridCols, "首页列数");
        setCellLabel(R.id.llAbout, "关于");
        setCellLabel(R.id.llUiMode, "操作偏好");
        setCellLabel(R.id.llCheckUpdate, "检查更新");
        setCellLabel(R.id.llUpdateUrl, "更新地址");
        if (!MobileUiHelper.useMobileUi(mContext)) {
            setWideCell(R.id.llApi);
            setWideCell(R.id.llLineSwitch);
            setWideCell(R.id.llLiveApi);
            setWideCell(R.id.llHomePref);
            setWideCell(R.id.llUiMode);
            setWideCell(R.id.llUpdateUrl);
        }
        hideCellValue(R.id.llChangeWallpaper);
        hideCellValue(R.id.llResetWallpaper);
        hideCellValue(R.id.llBackup);
        hideCellValue(R.id.llClearCache);
        hideCellValue(R.id.llThunderCache);
        hideCellValue(R.id.llResetApp);
        hideCellValue(R.id.llCheckUpdate);
        hideCellValue(R.id.llDriveAuth);
    }

    private void refreshValues() {
        tvDebugOpen.setText(Hawk.get(HawkConfig.DEBUG_OPEN, false) ? "已打开" : "已关闭");
        setCellValue(R.id.llApi, getStoreDisplayName());
        setCellValue(R.id.llLineSwitch, getLineDisplayName());
        setCellValue(R.id.llLiveApi, ApiConfig.get().getCurrentLiveSourceName());
        setCellValue(R.id.llHomePref, SettingUiHelper.getHomePrefName(Hawk.get(HawkConfig.HOME_PREF, 0)));
        setCellValue(R.id.llHomeRec, getHomeRecName(Hawk.get(HawkConfig.HOME_REC, 0)));
        setCellValue(R.id.llHomeTab, SettingUiHelper.getHomeTabName(Hawk.get(HawkConfig.HOME_TAB, 0)));
        setCellValue(R.id.llSearchView, getSearchView(Hawk.get(HawkConfig.SEARCH_VIEW, 0)));
        setCellValue(R.id.llCacheDays, SettingUiHelper.getCacheDaysName(Hawk.get(HawkConfig.CONFIG_CACHE_DAYS, 1)));
        setCellValue(R.id.llPlay, PlayerHelper.getPlayerName(Hawk.get(HawkConfig.PLAY_TYPE, 0)));
        setCellValue(R.id.llMediaCodec, SettingUiHelper.getDecodeName(Hawk.get(HawkConfig.IJK_CODEC, "")));
        setCellValue(R.id.llWindowPreview, Hawk.get(HawkConfig.WINDOW_PREVIEW, true) ? "开启" : "关闭");
        setCellValue(R.id.llScale, PlayerHelper.getScaleName(Hawk.get(HawkConfig.PLAY_SCALE, 0)));
        setCellValue(R.id.llDns, OkGoHelper.dnsHttpsList.get(Hawk.get(HawkConfig.DOH_URL, 0)));
        setCellValue(R.id.llHistoryCount, SettingUiHelper.getHistoryCountName(Hawk.get(HawkConfig.HISTORY_COUNT, 30)));
        setCellValue(R.id.llSearchThread, String.valueOf(Hawk.get(HawkConfig.SEARCH_THREAD, 16)));
        setCellValue(R.id.llRender, PlayerHelper.getRenderName(Hawk.get(HawkConfig.PLAY_RENDER, 0)));
        setCellValue(R.id.llExoBuffer, Hawk.get(HawkConfig.EXO_BUFFER, 50) + "s");
        setCellValue(R.id.llExoCache, Hawk.get(HawkConfig.EXO_CACHE, false) ? "启用" : "禁用");
        setCellValue(R.id.llParseWebVew, Hawk.get(HawkConfig.PARSE_WEBVIEW, true) ? "系统自带" : "XWalkView");
        SourceBean home = ApiConfig.get().getHomeSourceBean();
        setCellValue(R.id.llHomeApi, home != null ? home.getName() : "");
        setCellValue(R.id.llHomeGridCols, SettingUiHelper.getHomeGridColsName(Hawk.get(HawkConfig.HOME_GRID_COLS, 5)));
        setCellValue(R.id.llAbout, "V" + getAppVersionName());
        setCellValue(R.id.llUiMode, SettingUiHelper.getUiModeName(Hawk.get(HawkConfig.UI_MODE, 0)));
        setCellValue(R.id.llUpdateUrl, getUpdateUrlDisplay());
    }

    private String getStoreDisplayName() {
        String name = StoreConfigHelper.getCurrentStoreName();
        if (!TextUtils.isEmpty(name)) {
            return name;
        }
        String url = Hawk.get(HawkConfig.API_URL, "");
        return TextUtils.isEmpty(url) ? "未设置" : StoreConfigHelper.buildDefaultName(url);
    }

    private String getLineDisplayName() {
        String line = ApiConfig.get().getCurrentLineName();
        return TextUtils.isEmpty(line) ? "默认" : line;
    }

    private void setupClickListeners() {
        findViewById(R.id.llDebug).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            Hawk.put(HawkConfig.DEBUG_OPEN, !Hawk.get(HawkConfig.DEBUG_OPEN, false));
            refreshValues();
        });
        findViewById(R.id.llApi).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            ConfigDialogHelper.showApiDialog((BaseActivity) mActivity, new ConfigDialogHelper.UiCallback() {
                @Override
                public void onStoreChanged() {
                    refreshValues();
                }

                @Override
                public void onLineChanged() {
                    refreshValues();
                }
            });
        });
        findViewById(R.id.llLineSwitch).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            ConfigDialogHelper.showLineSwitchDialog((BaseActivity) mActivity, new ConfigDialogHelper.UiCallback() {
                @Override
                public void onStoreChanged() {
                }

                @Override
                public void onLineChanged() {
                    refreshValues();
                }
            });
        });
        findViewById(R.id.llLiveApi).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            showLiveSourceDialog();
        });
        findViewById(R.id.llHomePref).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            showIntSelect("请选择首选项", intArray(0, 1), Hawk.get(HawkConfig.HOME_PREF, 0),
                    val -> SettingUiHelper.getHomePrefName(val), val -> {
                        Hawk.put(HawkConfig.HOME_PREF, val);
                        refreshValues();
                    });
        });
        findViewById(R.id.llHomeRec).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            showIntSelect("请选择首页列表数据", intArray(0, 1, 2), Hawk.get(HawkConfig.HOME_REC, 0),
                    this::getHomeRecName, val -> {
                        Hawk.put(HawkConfig.HOME_REC, val);
                        refreshValues();
                    });
        });
        findViewById(R.id.llHomeTab).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            showIntSelect("请选择首页Tab", intArray(0, 1), Hawk.get(HawkConfig.HOME_TAB, 0),
                    val -> SettingUiHelper.getHomeTabName(val), val -> {
                        Hawk.put(HawkConfig.HOME_TAB, val);
                        refreshValues();
                    });
        });
        findViewById(R.id.llSearchView).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            showIntSelect("请选择搜索视图", intArray(0, 1), Hawk.get(HawkConfig.SEARCH_VIEW, 0),
                    this::getSearchView, val -> {
                        Hawk.put(HawkConfig.SEARCH_VIEW, val);
                        refreshValues();
                    });
        });
        findViewById(R.id.llCacheDays).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            showIntSelect("请选择配置缓存时长", intArray(0, 1, 3, 7), Hawk.get(HawkConfig.CONFIG_CACHE_DAYS, 1),
                    SettingUiHelper::getCacheDaysName, val -> {
                        Hawk.put(HawkConfig.CONFIG_CACHE_DAYS, val);
                        refreshValues();
                    });
        });
        findViewById(R.id.llPlay).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            showIntSelect("请选择默认播放器", intArray(0, 1, 2), Hawk.get(HawkConfig.PLAY_TYPE, 0),
                    PlayerHelper::getPlayerName, val -> {
                        Hawk.put(HawkConfig.PLAY_TYPE, val);
                        PlayerHelper.init();
                        refreshValues();
                    });
        });
        findViewById(R.id.llMediaCodec).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            showIjkCodecDialog();
        });
        findViewById(R.id.llWindowPreview).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            Hawk.put(HawkConfig.WINDOW_PREVIEW, !Hawk.get(HawkConfig.WINDOW_PREVIEW, true));
            refreshValues();
        });
        findViewById(R.id.llScale).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            showIntSelect("请选择默认画面缩放", intArray(0, 1, 2, 3, 4, 5), Hawk.get(HawkConfig.PLAY_SCALE, 0),
                    PlayerHelper::getScaleName, val -> {
                        Hawk.put(HawkConfig.PLAY_SCALE, val);
                        refreshValues();
                    });
        });
        findViewById(R.id.llChangeWallpaper).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            SettingUiHelper.nextWallpaper(mActivity);
        });
        findViewById(R.id.llResetWallpaper).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            SettingUiHelper.resetWallpaper(mActivity);
        });
        findViewById(R.id.llDns).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            int dohUrl = Hawk.get(HawkConfig.DOH_URL, 0);
            SelectDialog<String> dialog = new SelectDialog<>(mActivity);
            dialog.setTip("请选择安全DNS");
            dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<String>() {
                @Override
                public void click(String value, int pos) {
                    Hawk.put(HawkConfig.DOH_URL, pos);
                    String url = OkGoHelper.getDohUrl(pos);
                    OkGoHelper.dnsOverHttps.setUrl(url.isEmpty() ? null : HttpUrl.get(url));
                    IjkMediaPlayer.toggleDotPort(pos > 0);
                    refreshValues();
                }

                @Override
                public String getDisplay(String val) {
                    return val;
                }
            }, new DiffUtil.ItemCallback<String>() {
                @Override
                public boolean areItemsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                    return oldItem.equals(newItem);
                }

                @Override
                public boolean areContentsTheSame(@NonNull @NotNull String oldItem, @NonNull @NotNull String newItem) {
                    return oldItem.equals(newItem);
                }
            }, OkGoHelper.dnsHttpsList, dohUrl);
            dialog.show();
        });
        findViewById(R.id.llHistoryCount).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            showIntSelect("请选择历史记录条数", intArray(10, 20, 30, 50, 100), Hawk.get(HawkConfig.HISTORY_COUNT, 30),
                    SettingUiHelper::getHistoryCountName, val -> {
                        Hawk.put(HawkConfig.HISTORY_COUNT, val);
                        refreshValues();
                    });
        });
        findViewById(R.id.llSearchThread).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            showIntSelect("请选择搜索线程数", intArray(4, 8, 16, 32), Hawk.get(HawkConfig.SEARCH_THREAD, 16),
                    val -> val + "", val -> {
                        Hawk.put(HawkConfig.SEARCH_THREAD, val);
                        refreshValues();
                    });
        });
        findViewById(R.id.llRender).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            showIntSelect("请选择默认渲染方式", intArray(0, 1), Hawk.get(HawkConfig.PLAY_RENDER, 0),
                    PlayerHelper::getRenderName, val -> {
                        Hawk.put(HawkConfig.PLAY_RENDER, val);
                        PlayerHelper.init();
                        refreshValues();
                    });
        });
        findViewById(R.id.llExoBuffer).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            showIntSelect("请选择EXO缓冲时长", intArray(10, 20, 30, 50), Hawk.get(HawkConfig.EXO_BUFFER, 50),
                    val -> val + "s", val -> {
                        Hawk.put(HawkConfig.EXO_BUFFER, val);
                        refreshValues();
                    });
        });
        findViewById(R.id.llThunderCache).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            SettingUiHelper.clearThunderCache(mContext);
        });
        findViewById(R.id.llBackup).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            new BackupDialog(mActivity).show();
        });
        findViewById(R.id.llClearCache).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            SettingUiHelper.clearAppCache(mContext);
        });
        findViewById(R.id.llExoCache).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            Hawk.put(HawkConfig.EXO_CACHE, !Hawk.get(HawkConfig.EXO_CACHE, false));
            refreshValues();
        });
        findViewById(R.id.llResetApp).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            new AlertDialog.Builder(mActivity)
                    .setTitle("重置App")
                    .setMessage("将清空所有本地数据并重启，是否继续？")
                    .setPositiveButton("确定", (d, w) -> SettingUiHelper.resetApp(mActivity))
                    .setNegativeButton("取消", null)
                    .show();
        });
        findViewById(R.id.llParseWebVew).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            boolean useSystem = !Hawk.get(HawkConfig.PARSE_WEBVIEW, true);
            Hawk.put(HawkConfig.PARSE_WEBVIEW, useSystem);
            refreshValues();
            if (!useSystem) {
                Toast.makeText(mContext, "注意: XWalkView只适用于部分低Android版本，Android5.0以上推荐使用系统自带", Toast.LENGTH_LONG).show();
                XWalkInitDialog dialog = new XWalkInitDialog(mContext);
                dialog.setOnListener(new XWalkInitDialog.OnListener() {
                    @Override
                    public void onchange() {
                    }
                });
                dialog.show();
            }
        });
        View llDriveAuth = findViewById(R.id.llDriveAuth);
        if (llDriveAuth != null) {
            llDriveAuth.setOnClickListener(v -> {
                FastClickCheckUtil.check(v);
                DriveAuthDialog.showPicker(mActivity);
            });
        }
        findViewById(R.id.llHomeApi).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            List<SourceBean> sites = ApiConfig.get().getSourceBeanList();
            if (sites.isEmpty()) {
                return;
            }
            SelectDialog<SourceBean> dialog = new SelectDialog<>(mActivity);
            dialog.setTip("请选择首页数据源");
            dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<SourceBean>() {
                @Override
                public void click(SourceBean value, int pos) {
                    ApiConfig.get().setSourceBean(value);
                    refreshValues();
                }

                @Override
                public String getDisplay(SourceBean val) {
                    return val.getName();
                }
            }, new DiffUtil.ItemCallback<SourceBean>() {
                @Override
                public boolean areItemsTheSame(@NonNull @NotNull SourceBean oldItem, @NonNull @NotNull SourceBean newItem) {
                    return oldItem == newItem;
                }

                @Override
                public boolean areContentsTheSame(@NonNull @NotNull SourceBean oldItem, @NonNull @NotNull SourceBean newItem) {
                    return oldItem.getKey().equals(newItem.getKey());
                }
            }, sites, sites.indexOf(ApiConfig.get().getHomeSourceBean()));
            dialog.show();
        });
        findViewById(R.id.llHomeGridCols).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            showIntSelect("请选择首页数据源列数", intArray(3, 4, 5, 6, 7, 8), Hawk.get(HawkConfig.HOME_GRID_COLS, 5),
                    SettingUiHelper::getHomeGridColsName, val -> {
                        Hawk.put(HawkConfig.HOME_GRID_COLS, val);
                        Toast.makeText(mContext, "重新进入首页后生效", Toast.LENGTH_SHORT).show();
                        refreshValues();
                    });
        });
        findViewById(R.id.llAbout).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            new AboutDialog(mActivity).show();
        });
        findViewById(R.id.llCheckUpdate).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            AppUpdateHelper.check((BaseActivity) mActivity, true);
        });
        findViewById(R.id.llUpdateUrl).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            showUpdateUrlDialog();
        });
        findViewById(R.id.llUiMode).setOnClickListener(v -> {
            FastClickCheckUtil.check(v);
            showIntSelect("请选择操作偏好", intArray(0, 1, 2), Hawk.get(HawkConfig.UI_MODE, 0),
                    SettingUiHelper::getUiModeName, val -> {
                        refreshValues();
                        UiModeSwitcher.apply(mActivity, val);
                    });
        });
    }

    private void showLiveSourceDialog() {
        List<LiveSourceBean> sources = ApiConfig.get().getLiveSourceList();
        if (sources.isEmpty()) {
            Toast.makeText(mContext, "当前配置无直播源", Toast.LENGTH_SHORT).show();
            return;
        }
        LiveSourceBean current = ApiConfig.get().getCurrentLiveSource();
        int defaultPos = current != null ? sources.indexOf(current) : 0;
        if (defaultPos < 0) {
            defaultPos = 0;
        }
        SelectDialog<LiveSourceBean> dialog = new SelectDialog<>(mActivity);
        dialog.setTip("请选择直播地址");
        int finalDefaultPos = defaultPos;
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<LiveSourceBean>() {
            @Override
            public void click(LiveSourceBean value, int pos) {
                ApiConfig.get().setLiveSource(value);
                refreshValues();
            }

            @Override
            public String getDisplay(LiveSourceBean val) {
                return val.getName();
            }
        }, new DiffUtil.ItemCallback<LiveSourceBean>() {
            @Override
            public boolean areItemsTheSame(@NonNull @NotNull LiveSourceBean oldItem, @NonNull @NotNull LiveSourceBean newItem) {
                return oldItem.getUrl().equals(newItem.getUrl());
            }

            @Override
            public boolean areContentsTheSame(@NonNull @NotNull LiveSourceBean oldItem, @NonNull @NotNull LiveSourceBean newItem) {
                return oldItem.getUrl().equals(newItem.getUrl());
            }
        }, sources, finalDefaultPos);
        dialog.show();
    }

    private void showIjkCodecDialog() {
        List<IJKCode> ijkCodes = ApiConfig.get().getIjkCodes();
        if (ijkCodes == null || ijkCodes.isEmpty()) {
            return;
        }
        int defaultPos = 0;
        String ijkSel = Hawk.get(HawkConfig.IJK_CODEC, "");
        for (int j = 0; j < ijkCodes.size(); j++) {
            if (ijkSel.equals(ijkCodes.get(j).getName())) {
                defaultPos = j;
                break;
            }
        }
        SelectDialog<IJKCode> dialog = new SelectDialog<>(mActivity);
        dialog.setTip("请选择解码方式");
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<IJKCode>() {
            @Override
            public void click(IJKCode value, int pos) {
                value.selected(true);
                refreshValues();
            }

            @Override
            public String getDisplay(IJKCode val) {
                return val.getName();
            }
        }, new DiffUtil.ItemCallback<IJKCode>() {
            @Override
            public boolean areItemsTheSame(@NonNull @NotNull IJKCode oldItem, @NonNull @NotNull IJKCode newItem) {
                return oldItem == newItem;
            }

            @Override
            public boolean areContentsTheSame(@NonNull @NotNull IJKCode oldItem, @NonNull @NotNull IJKCode newItem) {
                return oldItem.getName().equals(newItem.getName());
            }
        }, ijkCodes, defaultPos);
        dialog.show();
    }

    private interface IntSelectCallback {
        void onSelect(int value);
    }

    private interface IntDisplay {
        String display(int value);
    }

    private void showIntSelect(String tip, int[] options, int current, IntDisplay display, IntSelectCallback callback) {
        ArrayList<Integer> list = new ArrayList<>();
        for (int option : options) {
            list.add(option);
        }
        int defaultPos = list.indexOf(current);
        if (defaultPos < 0) {
            defaultPos = 0;
        }
        SelectDialog<Integer> dialog = new SelectDialog<>(mActivity);
        dialog.setTip(tip);
        dialog.setAdapter(new SelectDialogAdapter.SelectDialogInterface<Integer>() {
            @Override
            public void click(Integer value, int pos) {
                callback.onSelect(value);
            }

            @Override
            public String getDisplay(Integer val) {
                return display.display(val);
            }
        }, new DiffUtil.ItemCallback<Integer>() {
            @Override
            public boolean areItemsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                return oldItem.intValue() == newItem.intValue();
            }

            @Override
            public boolean areContentsTheSame(@NonNull @NotNull Integer oldItem, @NonNull @NotNull Integer newItem) {
                return oldItem.intValue() == newItem.intValue();
            }
        }, list, defaultPos);
        dialog.show();
    }

    private int[] intArray(int... values) {
        return values;
    }

    private void setCellLabel(int includeId, String label) {
        TextView tv = cellLabel(includeId);
        if (tv != null) {
            tv.setText(label);
        }
    }

    private void setCellValue(int includeId, String value) {
        TextView tv = cellValue(includeId);
        if (tv == null) {
            return;
        }
        setSplitGuide(includeId, isWideCell(includeId) ? SPLIT_WIDE : SPLIT_NORMAL);
        tv.setText(TextUtils.isEmpty(value) ? "" : value);
        tv.setVisibility(View.VISIBLE);
    }

    private void hideCellValue(int includeId) {
        TextView tv = cellValue(includeId);
        if (tv != null) {
            tv.setText("");
            tv.setVisibility(View.GONE);
        }
        setSplitGuide(includeId, SPLIT_ACTION);
    }

    private void setWideCell(int includeId) {
        setSplitGuide(includeId, SPLIT_WIDE);
    }

    private boolean isWideCell(int includeId) {
        return includeId == R.id.llApi
                || includeId == R.id.llLineSwitch
                || includeId == R.id.llLiveApi
                || includeId == R.id.llHomePref
                || includeId == R.id.llUiMode;
    }

    private void setSplitGuide(int includeId, float percent) {
        View root = findViewById(includeId);
        if (!(root instanceof ConstraintLayout)) {
            return;
        }
        ConstraintSet set = new ConstraintSet();
        set.clone((ConstraintLayout) root);
        set.setGuidelinePercent(R.id.guidelineSplit, percent);
        set.applyTo((ConstraintLayout) root);
    }

    private TextView cellLabel(int includeId) {
        View root = findViewById(includeId);
        return root == null ? null : root.findViewById(R.id.tvLabel);
    }

    private TextView cellValue(int includeId) {
        View root = findViewById(includeId);
        return root == null ? null : root.findViewById(R.id.tvValue);
    }

    String getHomeRecName(int type) {
        if (type == 1) {
            return "站点推荐";
        } else if (type == 2) {
            return "观看历史";
        } else {
            return "豆瓣热播";
        }
    }

    String getSearchView(int type) {
        if (type == 0) {
            return "文字列表";
        } else {
            return "缩略图";
        }
    }

    private String getUpdateUrlDisplay() {
        String url = AppUpdateChecker.getUpdateUrl(mContext);
        if (TextUtils.isEmpty(url)) {
            return "未设置";
        }
        return url.length() > 28 ? url.substring(0, 28) + "…" : url;
    }

    private void showUpdateUrlDialog() {
        android.widget.EditText input = new android.widget.EditText(mActivity);
        input.setSingleLine(true);
        input.setText(Hawk.get(HawkConfig.APP_UPDATE_URL, ""));
        input.setHint(mContext.getString(R.string.app_update_url));
        new AlertDialog.Builder(mActivity)
                .setTitle("App 更新地址")
                .setMessage("填写 update.json 的完整 URL")
                .setView(input)
                .setPositiveButton("保存", (d, w) -> {
                    String url = input.getText().toString().trim();
                    Hawk.put(HawkConfig.APP_UPDATE_URL, url);
                    refreshValues();
                    Toast.makeText(mContext, "已保存", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private String getAppVersionName() {
        try {
            return mContext.getPackageManager().getPackageInfo(mContext.getPackageName(), 0).versionName;
        } catch (Exception e) {
            return "1.0.0";
        }
    }
}
