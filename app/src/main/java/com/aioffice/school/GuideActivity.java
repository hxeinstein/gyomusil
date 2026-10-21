package com.aioffice.school;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

/** 앱 안 설명서. 처음 실행하면 먼저 보여주고, 설정·메인 화면의 '설명서' 버튼으로 언제든 열 수 있어요. */
public class GuideActivity extends Activity {
    static final String EXTRA_FIRST = "first";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        boolean first = getIntent().getBooleanExtra(EXTRA_FIRST, false);
        WebView web = new WebView(this);
        web.getSettings().setJavaScriptEnabled(true);
        web.addJavascriptInterface(new Object() {
            @JavascriptInterface
            public void done() {
                runOnUiThread(() -> {
                    if (first) startActivity(new Intent(GuideActivity.this, SettingsActivity.class));
                    finish();
                });
            }
        }, "Android");
        web.loadUrl("file:///android_asset/guide.html" + (first ? "#first" : ""));
        setContentView(web);
    }
}
