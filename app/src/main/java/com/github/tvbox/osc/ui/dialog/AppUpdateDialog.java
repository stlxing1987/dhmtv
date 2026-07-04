package com.github.tvbox.osc.ui.dialog;

import android.content.Context;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.bean.AppUpdateInfo;
import com.github.tvbox.osc.util.AppUpdateChecker;
import com.lzy.okgo.OkGo;
import com.lzy.okgo.callback.FileCallback;
import com.lzy.okgo.model.Progress;
import com.lzy.okgo.model.Response;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class AppUpdateDialog extends BaseDialog {

    private final AppUpdateInfo updateInfo;
    private TextView btnUpdateLater;
    private TextView btnUpdateNow;
    private ProgressBar progressUpdate;
    private TextView tvUpdateProgress;
    private boolean downloading;
    private final List<TextView> focusButtons = new ArrayList<>();
    private int focusIndex;

    public AppUpdateDialog(@NonNull @NotNull Context context, AppUpdateInfo info) {
        super(context);
        this.updateInfo = info;
        setContentView(R.layout.dialog_app_update);
        setCanceledOnTouchOutside(!info.forceUpdate);
        setCancelable(!info.forceUpdate);
        initViews();
    }

    private void initViews() {
        TextView tvUpdateVersion = findViewById(R.id.tvUpdateVersion);
        TextView tvUpdateChangelog = findViewById(R.id.tvUpdateChangelog);
        btnUpdateLater = findViewById(R.id.btnUpdateLater);
        btnUpdateNow = findViewById(R.id.btnUpdateNow);
        progressUpdate = findViewById(R.id.progressUpdate);
        tvUpdateProgress = findViewById(R.id.tvUpdateProgress);

        tvUpdateVersion.setText(String.format("新版本 %s（%d）  当前 %s（%d）",
                updateInfo.versionName,
                updateInfo.versionCode,
                AppUpdateChecker.getLocalVersionName(getContext()),
                AppUpdateChecker.getLocalVersionCode(getContext())));
        tvUpdateChangelog.setText(updateInfo.changelog == null || updateInfo.changelog.isEmpty()
                ? "暂无更新说明"
                : updateInfo.changelog);

        if (updateInfo.forceUpdate) {
            btnUpdateLater.setVisibility(View.GONE);
        } else {
            btnUpdateLater.setOnClickListener(v -> {
                AppUpdateChecker.ignoreVersion(updateInfo);
                dismiss();
            });
            focusButtons.add(btnUpdateLater);
        }
        btnUpdateNow.setOnClickListener(v -> startDownload());
        focusButtons.add(btnUpdateNow);

        setupTvFocus();
        setOnShowListener(dialog -> focusDefaultButton());
    }

    private void focusDefaultButton() {
        focusIndex = focusButtons.size() - 1;
        TextView target = focusButtons.get(focusIndex);
        target.post(() -> {
            target.requestFocus();
            target.setSelected(true);
        });
    }

    private void moveFocus(int delta) {
        if (focusButtons.size() <= 1) {
            return;
        }
        focusButtons.get(focusIndex).setSelected(false);
        focusIndex = (focusIndex + delta + focusButtons.size()) % focusButtons.size();
        TextView target = focusButtons.get(focusIndex);
        target.requestFocus();
        target.setSelected(true);
    }

    private void setupTvFocus() {
        View.OnFocusChangeListener focusListener = (v, hasFocus) -> v.setSelected(hasFocus);
        View.OnKeyListener keyListener = (v, keyCode, event) -> {
            if (downloading || event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                moveFocus(-1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                moveFocus(1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                v.performClick();
                return true;
            }
            return false;
        };
        for (TextView button : focusButtons) {
            button.setFocusable(true);
            button.setFocusableInTouchMode(true);
            button.setOnFocusChangeListener(focusListener);
            button.setOnKeyListener(keyListener);
        }
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        if (!downloading && event.getAction() == KeyEvent.ACTION_DOWN && !focusButtons.isEmpty()) {
            int keyCode = event.getKeyCode();
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                moveFocus(-1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                moveFocus(1);
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER
                    || keyCode == KeyEvent.KEYCODE_ENTER
                    || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER) {
                focusButtons.get(focusIndex).performClick();
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void startDownload() {
        if (downloading) {
            return;
        }
        downloading = true;
        btnUpdateLater.setEnabled(false);
        btnUpdateNow.setEnabled(false);
        btnUpdateLater.setFocusable(false);
        btnUpdateNow.setFocusable(false);
        progressUpdate.setVisibility(View.VISIBLE);
        tvUpdateProgress.setVisibility(View.VISIBLE);
        progressUpdate.setProgress(0);
        tvUpdateProgress.setText("准备下载…");

        File target = AppUpdateChecker.getApkCacheFile(getContext());
        if (target.exists()) {
            target.delete();
        }
        OkGo.<File>get(updateInfo.apkUrl)
                .tag("app_update_download")
                .execute(new FileCallback(target.getParent(), target.getName()) {
                    @Override
                    public void onSuccess(Response<File> response) {
                        downloading = false;
                        File apk = response.body();
                        if (apk == null || !apk.exists() || apk.length() < 1024 * 1024
                                || getContext().getPackageManager()
                                .getPackageArchiveInfo(apk.getAbsolutePath(), 0) == null) {
                            if (apk != null && apk.exists()) {
                                apk.delete();
                            }
                            btnUpdateLater.setEnabled(!updateInfo.forceUpdate);
                            btnUpdateNow.setEnabled(true);
                            btnUpdateLater.setFocusable(!updateInfo.forceUpdate);
                            btnUpdateNow.setFocusable(true);
                            progressUpdate.setVisibility(View.GONE);
                            tvUpdateProgress.setVisibility(View.GONE);
                            Toast.makeText(getContext(),
                                    "安装包下载不完整，请重试",
                                    Toast.LENGTH_LONG).show();
                            focusIndex = focusButtons.size() - 1;
                            focusDefaultButton();
                            return;
                        }
                        dismiss();
                        AppUpdateChecker.installApk(
                                getContext(),
                                apk,
                                updateInfo.versionCode);
                    }

                    @Override
                    public void onError(Response<File> response) {
                        downloading = false;
                        btnUpdateLater.setEnabled(!updateInfo.forceUpdate);
                        btnUpdateNow.setEnabled(true);
                        btnUpdateLater.setFocusable(!updateInfo.forceUpdate);
                        btnUpdateNow.setFocusable(true);
                        progressUpdate.setVisibility(View.GONE);
                        tvUpdateProgress.setVisibility(View.GONE);
                        Toast.makeText(getContext(),
                                response.getException() != null
                                        ? response.getException().getMessage()
                                        : "下载失败",
                                Toast.LENGTH_LONG).show();
                        focusIndex = focusButtons.size() - 1;
                        focusDefaultButton();
                    }

                    @Override
                    public void downloadProgress(Progress progress) {
                        super.downloadProgress(progress);
                        int percent = (int) (progress.fraction * 100);
                        progressUpdate.setProgress(percent);
                        tvUpdateProgress.setText(String.format("下载中 %d%%", percent));
                    }
                });
    }

    @Override
    public void dismiss() {
        if (downloading) {
            OkGo.getInstance().cancelTag("app_update_download");
            downloading = false;
        }
        super.dismiss();
    }
}
