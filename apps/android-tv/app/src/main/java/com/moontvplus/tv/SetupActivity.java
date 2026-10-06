package com.moontvplus.tv;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Switch;
import android.widget.TextView;

public class SetupActivity extends Activity {
    private EditText inputUrl;
    private Switch switchSelfSigned;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF070816);
        int padPx = (int) (24 * getResources().getDisplayMetrics().density);

        TextView title = new TextView(this);
        title.setText("MoonTVPlus 服务地址");
        title.setTextColor(0xFFFFFFFF);
        title.setTextSize(28);
        title.setPadding(padPx, padPx, padPx, padPx / 2);
        root.addView(title, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        inputUrl = new EditText(this);
        inputUrl.setHint("https://ltv.860527.xyz:88");
        inputUrl.setHintTextColor(0xFF64748B);
        inputUrl.setTextColor(0xFFF8FAFC);
        inputUrl.setTextSize(18);
        inputUrl.setText(Settings.getBaseUrl(this));
        FrameLayout.LayoutParams inputLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        inputLp.leftMargin = padPx;
        inputLp.rightMargin = padPx;
        inputLp.topMargin = padPx;
        root.addView(inputUrl, inputLp);

        switchSelfSigned = new Switch(this);
        switchSelfSigned.setText("信任自签名证书（私有 CA 环境用，勿在公网开启）");
        switchSelfSigned.setTextColor(0xFFCBD5E1);
        switchSelfSigned.setChecked(Settings.isSslTrustSelfSigned(this));
        FrameLayout.LayoutParams switchLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        switchLp.leftMargin = padPx;
        switchLp.topMargin = padPx;
        root.addView(switchSelfSigned, switchLp);

        status = new TextView(this);
        status.setTextColor(0xFFA5B4FC);
        status.setTextSize(14);
        status.setPadding(padPx, 0, padPx, 0);
        FrameLayout.LayoutParams statusLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        statusLp.topMargin = padPx / 2;
        root.addView(status, statusLp);

        TextView hint = new TextView(this);
        hint.setText("OK 保存并进入 · 返回取消 · 输入框聚焦后直接键入（遥控器数字键有效）");
        hint.setTextColor(0xFF64748B);
        hint.setTextSize(12);
        hint.setPadding(padPx, 0, padPx, padPx);
        FrameLayout.LayoutParams hintLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        hintLp.bottomMargin = padPx;
        root.addView(hint, hintLp);

        setContentView(root);
        inputUrl.requestFocus();
        inputUrl.postDelayed(() -> inputUrl.selectAll(), 300);

        switchSelfSigned.setOnCheckedChangeListener((v, checked) ->
                Settings.setSslTrustSelfSigned(this, checked));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            saveAndContinue();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void saveAndContinue() {
        String url = Settings.normalize(inputUrl.getText().toString());
        if (url.isEmpty()) {
            status.setText("地址不能为空");
            return;
        }
        status.setText("正在连接 " + url + " ...");
        new Thread(() -> {
            boolean ok = Settings.checkReachable(url);
            runOnUiThread(() -> {
                if (ok) {
                    Settings.setBaseUrl(this, url);
                    startActivity(new android.content.Intent(this, MainActivity.class));
                    finish();
                } else {
                    status.setText("连接失败，请检查地址后按 OK 重试（也可直接按返回保存退出）");
                }
            });
        }).start();
    }

    @Override
    public void onBackPressed() {
        // 允许"先保存当前输入再退出"的情况很少，保持简单：直接退出
        super.onBackPressed();
    }
}
