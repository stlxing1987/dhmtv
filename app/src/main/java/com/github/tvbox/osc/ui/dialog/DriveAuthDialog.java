package com.github.tvbox.osc.ui.dialog;

import android.app.Activity;
import android.content.Context;
import android.net.http.SslError;
import android.os.Build;
import android.text.TextUtils;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.graphics.BitmapFactory;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.github.tvbox.osc.R;
import com.github.tvbox.osc.server.ControlManager;
import com.github.tvbox.osc.util.DriveAuthHelper;
import com.github.tvbox.osc.viewmodel.SourceViewModel;

import org.jetbrains.annotations.NotNull;

public class DriveAuthDialog extends BaseDialog {

    private FrameLayout webContainer;
    private ImageView ivQr;
    private WebView webView;
    private TextView tvTitle;
    private TextView tvTip;
    private TextView tvClose;
    private final String driveName;
    private final int driveIndex;

    public DriveAuthDialog(@NonNull @NotNull Context context, String driveName, int driveIndex) {
        super(context, R.style.CustomDialogStyleDim);
        this.driveName = driveName;
        this.driveIndex = driveIndex;
        setContentView(R.layout.dialog_drive_auth);
        setCancelable(true);
        setCanceledOnTouchOutside(false);
        if (getWindow() != null) {
            getWindow().setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
        }
        tvTitle = findViewById(R.id.tvDriveAuthTitle);
        tvTip = findViewById(R.id.tvDriveAuthTip);
        webContainer = findViewById(R.id.flDriveAuthContainer);
        ivQr = findViewById(R.id.ivDriveAuthQr);
        tvClose = findViewById(R.id.tvDriveAuthClose);
        if (!TextUtils.isEmpty(driveName)) {
            tvTitle.setText(driveName + " 扫码授权");
        }
        tvClose.setOnClickListener(v -> dismiss());
        tvClose.requestFocus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (driveIndex >= 0) {
            loadAuthPage();
        }
    }

    public static void showPicker(Context context) {
        Activity activity = context instanceof Activity ? (Activity) context : null;
        if (activity == null) {
            Toast.makeText(context, "无法打开网盘授权", Toast.LENGTH_SHORT).show();
            return;
        }
        ControlManager.get().startServer();
        SelectDialog<String> dialog = new SelectDialog<>(activity);
        dialog.setTip("请选择需要授权的网盘");
        dialog.setAdapter(new com.github.tvbox.osc.ui.adapter.SelectDialogAdapter.SelectDialogInterface<String>() {
            @Override
            public void click(String value, int pos) {
                dialog.dismiss();
                activity.getWindow().getDecorView().post(() -> {
                    try {
                        new DriveAuthDialog(activity, DriveAuthHelper.DRIVE_NAMES[pos], pos).show();
                    } catch (Throwable th) {
                        th.printStackTrace();
                        Toast.makeText(activity, "打开授权页失败：" + th.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public String getDisplay(String val) {
                return val;
            }
        }, new androidx.recyclerview.widget.DiffUtil.ItemCallback<String>() {
            @Override
            public boolean areItemsTheSame(@NonNull String oldItem, @NonNull String newItem) {
                return oldItem.equals(newItem);
            }

            @Override
            public boolean areContentsTheSame(@NonNull String oldItem, @NonNull String newItem) {
                return oldItem.equals(newItem);
            }
        }, java.util.Arrays.asList(DriveAuthHelper.DRIVE_NAMES), 0);
        dialog.show();
    }

    public static void showForUrl(Context context, String authUrl) {
        Activity activity = context instanceof Activity ? (Activity) context : null;
        if (activity == null) {
            Toast.makeText(context, "无法打开网盘授权", Toast.LENGTH_SHORT).show();
            return;
        }
        if (TextUtils.isEmpty(authUrl)) {
            showPicker(activity);
            return;
        }
        DriveAuthDialog authDialog;
        try {
            authDialog = new DriveAuthDialog(activity, "网盘", -1);
        } catch (Throwable th) {
            th.printStackTrace();
            Toast.makeText(activity, "打开授权页失败", Toast.LENGTH_SHORT).show();
            return;
        }
        authDialog.show();
        authDialog.showLoading("正在加载授权页…");
        SourceViewModel.spThreadPool.execute(() -> {
            DriveAuthHelper.AuthPage page = DriveAuthHelper.resolveUrl(authUrl);
            new android.os.Handler(android.os.Looper.getMainLooper())
                    .post(() -> authDialog.showPage(page));
        });
    }

    private WebView ensureWebView() {
        if (webView != null) {
            return webView;
        }
        if (webContainer == null) {
            return null;
        }
        try {
            webView = new WebView(getContext());
            webView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            webView.setVisibility(android.view.View.INVISIBLE);
            if (ivQr != null) {
                ivQr.setVisibility(android.view.View.GONE);
            }
            webContainer.removeAllViews();
            webContainer.addView(webView);
            if (ivQr != null) {
                webContainer.addView(ivQr);
            }
            setupWebView(webView);
            return webView;
        } catch (Throwable th) {
            th.printStackTrace();
            return null;
        }
    }

    private void setupWebView(WebView view) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }
        view.setWebViewClient(new WebViewClient() {
            @Override
            public void onReceivedSslError(WebView wv, SslErrorHandler handler, SslError error) {
                handler.proceed();
            }

            @Override
            public void onPageFinished(WebView wv, String url) {
                super.onPageFinished(wv, url);
                wv.setVisibility(android.view.View.VISIBLE);
                if (driveIndex >= 0) {
                    tvTip.setText(DriveAuthHelper.buildAuthTip(driveIndex));
                } else {
                    tvTip.setText("请使用手机网盘App扫描二维码，完成后点击「完成授权」");
                }
            }

            @Override
            public void onReceivedError(WebView wv, int errorCode, String description, String failingUrl) {
                super.onReceivedError(wv, errorCode, description, failingUrl);
                showError("页面加载失败：" + description);
            }
        });
    }

    private void loadAuthPage() {
        if (driveIndex < 0) {
            return;
        }
        showLoading("正在获取授权页面…");
        if (DriveAuthHelper.isJarProxyReady()) {
            String proxyUrl = DriveAuthHelper.buildProxyWebUrl(driveIndex);
            if (!TextUtils.isEmpty(proxyUrl)) {
                showProxyWebPage(proxyUrl);
                return;
            }
        }
        DriveAuthHelper.resolve(driveIndex, page -> new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
            if (!isShowing()) {
                return;
            }
            showPage(page);
        }));
    }

    private void showProxyWebPage(String proxyUrl) {
        WebView view = ensureWebView();
        if (view == null) {
            showError("WebView 初始化失败，请检查系统 WebView 组件");
            return;
        }
        view.setVisibility(android.view.View.VISIBLE);
        if (ivQr != null) {
            ivQr.setVisibility(android.view.View.GONE);
        }
        tvTip.setText(DriveAuthHelper.buildAuthTip(driveIndex));
        view.loadUrl(proxyUrl);
    }

    private void showPage(DriveAuthHelper.AuthPage page) {
        if (page == null) {
            showError("获取授权页失败");
            return;
        }
        if (!TextUtils.isEmpty(page.error)) {
            showError(page.error);
            return;
        }
        if (!TextUtils.isEmpty(page.loadUrl)) {
            showProxyWebPage(page.loadUrl);
            return;
        }
        if (!TextUtils.isEmpty(page.html)) {
            WebView view = ensureWebView();
            if (view == null) {
                showError("WebView 初始化失败，请检查系统 WebView 组件");
                return;
            }
            view.setVisibility(android.view.View.VISIBLE);
            String base = TextUtils.isEmpty(page.baseUrl) ? DriveAuthHelper.getLocalProxyAddress() : page.baseUrl;
            view.loadDataWithBaseURL(base, page.html, page.mime, "utf-8", null);
            tvTip.setText(DriveAuthHelper.buildAuthTip(driveIndex));
            return;
        }
        if (showQrImage(page)) {
            return;
        }
        WebView view = ensureWebView();
        if (view == null) {
            showError("WebView 初始化失败，请检查系统 WebView 组件");
            return;
        }
        showError("未能获取「" + driveName + "」授权页，请稍后重试");
    }

    private boolean showQrImage(DriveAuthHelper.AuthPage page) {
        if (page == null || page.imageBytes == null || page.imageBytes.length == 0) {
            return false;
        }
        hideWebView();
        if (ivQr != null) {
            android.graphics.Bitmap bmp = BitmapFactory.decodeByteArray(page.imageBytes, 0, page.imageBytes.length);
            if (bmp != null) {
                ivQr.setImageBitmap(bmp);
                ivQr.setVisibility(android.view.View.VISIBLE);
                tvTip.setText(DriveAuthHelper.buildAuthTip(driveIndex));
                return true;
            }
        }
        return false;
    }

    private void hideWebView() {
        if (webView != null) {
            webView.setVisibility(android.view.View.GONE);
        }
        if (ivQr != null) {
            ivQr.setVisibility(android.view.View.GONE);
        }
    }

    private void showLoading(String message) {
        hideWebView();
        tvTip.setText(message);
    }

    private void showError(String message) {
        WebView view = ensureWebView();
        tvTip.setText(message);
        if (view == null) {
            Toast.makeText(getContext(), message.split("\n")[0], Toast.LENGTH_LONG).show();
            return;
        }
        view.setVisibility(android.view.View.VISIBLE);
        view.loadDataWithBaseURL(null,
                "<html><body style='background:#fff;color:#333;font-size:16px;padding:24px;line-height:1.7;'>"
                        + TextUtils.htmlEncode(message).replace("\n", "<br/>")
                        + "</body></html>",
                "text/html", "utf-8", null);
        Toast.makeText(getContext(), message.split("\n")[0], Toast.LENGTH_LONG).show();
    }

    @Override
    public void dismiss() {
        if (webView != null) {
            try {
                webView.stopLoading();
                webView.loadUrl("about:blank");
                webView.destroy();
            } catch (Throwable th) {
                th.printStackTrace();
            }
            webView = null;
        }
        if (webContainer != null) {
            webContainer.removeAllViews();
        }
        super.dismiss();
    }
}
