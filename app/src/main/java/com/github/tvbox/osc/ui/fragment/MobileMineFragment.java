package com.github.tvbox.osc.ui.fragment;

import android.view.View;
import android.widget.TextView;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.base.BaseActivity;
import com.github.tvbox.osc.base.BaseLazyFragment;
import com.github.tvbox.osc.ui.activity.SettingActivity;
import com.github.tvbox.osc.util.AppUpdateHelper;
import com.github.tvbox.osc.util.ConfigDialogHelper;

public class MobileMineFragment extends BaseLazyFragment {

    @Override
    protected int getLayoutResID() {
        return R.layout.fragment_mobile_mine;
    }

    @Override
    protected void init() {
        View itemUiMode = findViewById(R.id.itemUiMode);
        if (itemUiMode != null) {
            itemUiMode.setVisibility(View.GONE);
        }
        findViewById(R.id.itemSetting).setOnClickListener(v -> jumpActivity(SettingActivity.class));
        findViewById(R.id.itemConfig).setOnClickListener(v ->
                ConfigDialogHelper.showApiDialog((BaseActivity) mActivity, null));
        findViewById(R.id.itemLine).setOnClickListener(v ->
                ConfigDialogHelper.showLineSwitchDialog((BaseActivity) mActivity, null));
        findViewById(R.id.itemCheckUpdate).setOnClickListener(v ->
                AppUpdateHelper.check((BaseActivity) mActivity, true));
    }
}
