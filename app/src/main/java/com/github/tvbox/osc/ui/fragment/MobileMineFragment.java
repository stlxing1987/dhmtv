package com.github.tvbox.osc.ui.fragment;

import android.widget.TextView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.util.AppUpdateHelper;
import com.github.tvbox.osc.util.ConfigDialogHelper;
import com.github.tvbox.osc.util.DeviceHelper;
import com.github.tvbox.osc.util.HawkConfig;
import com.github.tvbox.osc.util.SettingUiHelper;
import com.github.tvbox.osc.util.UiModeSwitcher;
import com.orhanobut.hawk.Hawk;

public class MobileMineFragment extends BaseLazyFragment {
    private TextView tvUiModeValue;

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_mobile_mine;
    }

    @Override
    protected void init() {
        tvUiModeValue = findViewById(R.id.tvUiModeValue);
        refreshUiMode();
        findViewById(R.id.itemSetting).setOnClickListener(v -> jumpActivity(SettingActivity.class));
        findViewById(R.id.itemConfig).setOnClickListener(v ->
                ConfigDialogHelper.showApiDialog((BaseActivity) mActivity, null));
        findViewById(R.id.itemLine).setOnClickListener(v ->
                ConfigDialogHelper.showLineSwitchDialog((BaseActivity) mActivity, null));
        findViewById(R.id.itemUiMode).setOnClickListener(v -> cycleUiMode());
        findViewById(R.id.itemCheckUpdate).setOnClickListener(v ->
                AppUpdateHelper.check((BaseActivity) mActivity, true));
    }

    @Override
    protected void onFragmentResume() {
        super.onFragmentResume();
        refreshUiMode();
    }

    private void refreshUiMode() {
        int mode = Hawk.get(HawkConfig.UI_MODE, DeviceHelper.MODE_TV);
        tvUiModeValue.setText("当前：" + SettingUiHelper.getUiModeName(mode) + "（点击切换）");
    }

    private void cycleUiMode() {
        int mode = Hawk.get(HawkConfig.UI_MODE, DeviceHelper.MODE_TV);
        int next = (mode + 1) % 3;
        UiModeSwitcher.apply(mActivity, next);
    }
}
