package com.aioffice.school;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

/** 움직이는 교무실 화면. 그림은 앱 안의 office.html이 그리고, 상태는 앱이 드라이브에서 받아 넘겨줘요. */
public class MainActivity extends Activity {
    // 화면 파일을 인터넷에서 받아 써요. 이 파일만 고치면 모든 사람의 화면이 바뀌어요.
    static final String REMOTE_OFFICE = "https://hxeinstein.github.io/gyomusil/office.html";
    private static final String MARK = "/*__CONFIG__*/null";

    private WebView web;
    private String shown = "";
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile boolean fetching = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Prefs.configured(this)) {
            startActivity(new Intent(this, GuideActivity.class).putExtra(GuideActivity.EXTRA_FIRST, true));
            finish();
            return;
        }

        FrameLayout root = new FrameLayout(this);
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        web.addJavascriptInterface(new Bridge(), "Android");
        root.addView(web);

        TextView gear = new TextView(this);
        gear.setText("⚙ 설정");
        gear.setTextColor(Color.WHITE);
        gear.setTextSize(13);
        gear.setBackgroundColor(Color.parseColor("#CC34495E"));
        gear.setPadding(24, 12, 24, 12);
        gear.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        lp.setMargins(0, 0, 32, 48);
        root.addView(gear, lp);

        TextView help = new TextView(this);
        help.setText("📖 설명서");
        help.setTextColor(Color.WHITE);
        help.setTextSize(13);
        help.setBackgroundColor(Color.parseColor("#CC5F6B7A"));
        help.setPadding(24, 12, 24, 12);
        help.setOnClickListener(v -> startActivity(new Intent(this, GuideActivity.class)));
        FrameLayout.LayoutParams lp2 = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.END);
        lp2.setMargins(0, 0, 32, 150);
        root.addView(help, lp2);
        setContentView(root);

        show(localOffice());
        new Thread(this::checkRemote).start();
    }

    /** 받아 둔 최신 화면이 있으면 그걸, 없으면 앱에 들어 있는 화면을 써요. */
    private String localOffice() {
        try (InputStream in = openFileInput("office.html")) {
            String s = read(in);
            if (s.contains(MARK)) return s;
        } catch (Exception ignored) { }
        return asset("office.html");
    }

    private void show(String page) {
        shown = page;
        String html = page.replace(MARK, Prefs.configJson(this));
        web.loadDataWithBaseURL("https://gyomusil.local/", html, "text/html", "UTF-8", null);
    }

    /** 인터넷의 화면 파일이 바뀌었으면 저장하고 바로 다시 그려요. 안 되면 그냥 지금 화면을 써요. */
    private void checkRemote() {
        try {
            java.net.HttpURLConnection con = (java.net.HttpURLConnection)
                    new java.net.URL(REMOTE_OFFICE + "?v=" + (System.currentTimeMillis() / 60000)).openConnection();
            con.setConnectTimeout(6000);
            con.setReadTimeout(8000);
            if (con.getResponseCode() != 200) return;
            String page;
            try (InputStream in = con.getInputStream()) { page = read(in); }
            if (!page.contains(MARK) || page.equals(shown)) return;
            try (java.io.FileOutputStream out = openFileOutput("office.html", MODE_PRIVATE)) {
                out.write(page.getBytes("UTF-8"));
            }
            ui.post(() -> { if (web != null) show(page); });
        } catch (Exception ignored) { }
    }

    private static String read(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) Updater.start(this);
    }

    /** 화면이 20초마다 부르면, 저장된 상태를 넘겨주고 필요하면 새로 받아요. */
    private class Bridge {
        @JavascriptInterface
        public void ready() {
            push();
            if (!fetching && System.currentTimeMillis() - Prefs.lastTime(MainActivity.this) > 15_000) {
                fetching = true;
                new Thread(() -> {
                    DriveClient.refresh(MainActivity.this);
                    Renderer.update(MainActivity.this);
                    fetching = false;
                    push();
                }).start();
            }
        }

        @JavascriptInterface
        public void openFloor(String theme) {
            int f = 0;
            try { f = Integer.parseInt(theme.replace("t", "")); } catch (Exception ignored) { }
            final int floor = f;
            ui.post(() -> startActivity(Opener.forFloor(MainActivity.this, floor)));
        }
    }

    private void push() {
        String js = Prefs.lastJson(this);
        String err = Prefs.lastError(this);
        ui.post(() -> {
            if (web == null) return;
            if (!js.isEmpty()) web.evaluateJavascript("try{applyStatus((" + js + ").floors)}catch(e){}", null);
            if (!err.isEmpty()) web.evaluateJavascript("document.getElementById('mode').textContent='연결 오류';"
                    + "document.getElementById('msg').textContent=" + org.json.JSONObject.quote(err) + ";", null);
        });
    }

    private String asset(String name) {
        try (InputStream in = getAssets().open(name)) {
            return read(in);
        } catch (Exception e) {
            return "<p>화면을 불러오지 못했어요</p>";
        }
    }

    @Override
    public void onBackPressed() { finish(); }
}
