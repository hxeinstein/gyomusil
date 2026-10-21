package com.aioffice.school;

import android.content.Context;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** 위젯 말풍선 글자. 인터넷의 widget.json을 받아 두면 APK 없이도 대사를 바꿀 수 있어요. */
final class WidgetText {
    static final String REMOTE = "https://hxeinstein.github.io/gyomusil/widget.json";

    private static JSONObject defaults() {
        try {
            return new JSONObject()
                    .put("working_read", "자료 읽는 중").put("working_write", "작성 중")
                    .put("working_run", "실행 중").put("working_search", "검색 중")
                    .put("working_plan", "계획 세우는 중")
                    .put("waiting", "선생님, 질문 있습니다!").put("approval", "선생님, 결재 부탁드립니다!")
                    .put("done", "완료했습니다!").put("unknown", "확인 중");
        } catch (Exception e) { return new JSONObject(); }
    }

    /** 기본 글자 위에 받아 둔 글자를 덮어써요. */
    static JSONObject load(Context c) {
        JSONObject t = defaults();
        try {
            String saved = c.getSharedPreferences("gyomusil", Context.MODE_PRIVATE).getString("wtext", "");
            if (!saved.isEmpty()) {
                JSONObject o = new JSONObject(saved);
                java.util.Iterator<String> it = o.keys();
                while (it.hasNext()) { String k = it.next(); t.put(k, o.optString(k)); }
            }
        } catch (Exception ignored) { }
        return t;
    }

    /** 하루에 몇 번만 확인해요. 실패하면 그냥 넘어가요. */
    static void refresh(Context c) {
        android.content.SharedPreferences sp = c.getSharedPreferences("gyomusil", Context.MODE_PRIVATE);
        if (System.currentTimeMillis() - sp.getLong("wtextTime", 0) < 3 * 60 * 60 * 1000L) return;
        try {
            HttpURLConnection con = (HttpURLConnection) new URL(REMOTE + "?v=" + (System.currentTimeMillis() / 600000)).openConnection();
            con.setConnectTimeout(6000);
            con.setReadTimeout(8000);
            if (con.getResponseCode() != 200) return;
            try (InputStream in = con.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                String js = out.toString("UTF-8");
                new JSONObject(js);                                   // 형식 확인
                sp.edit().putString("wtext", js).putLong("wtextTime", System.currentTimeMillis()).apply();
            }
        } catch (Exception ignored) { }
    }
}
