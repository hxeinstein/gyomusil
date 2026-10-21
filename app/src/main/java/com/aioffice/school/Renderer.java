package com.aioffice.school;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;
import android.widget.RemoteViews;

import org.json.JSONObject;

import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.Date;
import java.util.Locale;

/** 층별 상태에 맞는 그림 조각을 골라 한 장으로 합쳐 위젯에 올려요. */
final class Renderer {
    private static final int LW = 236;
    private static final long MIN = 60_000L;

    static void update(Context c) {
        int[] ids = AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, OfficeWidget.class));
        if (ids == null || ids.length == 0) return;

        JSONObject floors = null;
        try {
            String js = Prefs.lastJson(c);
            if (!js.isEmpty()) floors = new JSONObject(js).optJSONObject("floors");
        } catch (Exception ignored) { }

        int n = Prefs.count(c);
        int lh = 39 + 126 * (n - 1) + 129;
        float out = n <= 3 ? 2.25f : 1.75f;                    // 층이 많으면 해상도를 조금 낮춰요
        long now = System.currentTimeMillis();
        Bitmap bmp = Bitmap.createBitmap(Math.round(LW * out), Math.round(lh * out), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint();
        p.setFilterBitmap(false);

        // 위층부터 i = 0, 1, … (맨 위가 n층)
        String[] variants = new String[n];
        for (int i = 0; i < n; i++) {
            int floor = n - i;
            JSONObject d = floors == null ? null : floors.optJSONObject("t" + floor);
            variants[i] = variant(d, now);
        }
        for (int i = n - 1; i >= 0; i--) {
            int oy = 39 + 126 * i;
            draw(c, cv, p, "floors/t" + (n - i) + "_" + variants[i] + ".png", Math.max(0, oy - 40), out);
        }
        // 말풍선은 글자로 또렷하게 (그림 위에)
        JSONObject spots = bubbleSpots(c), texts = WidgetText.load(c);
        for (int i = 0; i < n; i++) {
            org.json.JSONArray list = spots == null ? null : spots.optJSONArray("t" + (n - i) + "_" + variants[i]);
            if (list == null) continue;
            int top = Math.max(0, 39 + 126 * i - 40);
            for (int j = 0; j < list.length(); j++) {
                JSONObject b = list.optJSONObject(j);
                if (b == null) continue;
                String text = texts.optString(b.optString("key"), "");
                if (!text.isEmpty()) drawBubble(cv, text, (float) b.optDouble("x"), top + (float) b.optDouble("y"), b.optString("kind"), out);
            }
        }
        for (int i = 0; i < n; i++) drawLabel(cv, Prefs.name(c, n - i), variants[i], 39 + 126 * i - 27, out);

        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget_office);
        v.setImageViewBitmap(R.id.office, bmp);

        long t = Prefs.lastTime(c);
        String time = t == 0 ? "--:--" : new SimpleDateFormat("HH:mm", Locale.KOREA).format(new Date(t));
        boolean err = !Prefs.lastError(c).isEmpty();
        v.setTextViewText(R.id.refresh, (err ? "⚠ 연결 확인 · " : "↻ ") + time);
        Intent refresh = new Intent(c, OfficeWidget.class).setAction(OfficeWidget.ACTION_REFRESH);
        v.setOnClickPendingIntent(R.id.refresh, PendingIntent.getBroadcast(c, 10, refresh,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));

        int[] zones = {R.id.zone1, R.id.zone2, R.id.zone3, R.id.zone4, R.id.zone5};
        int max = zones.length;
        for (int z = 0; z < max; z++) v.setViewVisibility(zones[z], z < max - n ? View.GONE : View.VISIBLE);
        for (int i = 0; i < n; i++) {
            v.setOnClickPendingIntent(zones[max - n + i], PendingIntent.getActivity(c, 20 + i,
                    Opener.forFloor(c, n - i), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        }
        AppWidgetManager.getInstance(c).updateAppWidget(new ComponentName(c, OfficeWidget.class), v);
    }

    static String variant(JSONObject d, long now) {
        if (d == null) return "resting";
        String st = d.optString("state", "working");
        String act = d.optString("activity", "write");
        long age = 0;
        try { age = now - Instant.parse(d.optString("updatedAt")).toEpochMilli(); } catch (Exception ignored) { }
        if (st.equals("working") && age > 30 * MIN) return "unknown";
        if (st.equals("done") && age > 10 * MIN) return "resting";
        switch (st) {
            case "waiting": case "approval": case "done": return st;
            default:
                if (!act.matches("read|write|run|search|plan")) act = "write";
                return "working_" + act;
        }
    }

    private static String say(String v) {
        switch (v) {
            case "working_read": return "자료 읽는 중";
            case "working_write": return "작성 중";
            case "working_run": return "실행 중";
            case "working_search": return "검색 중";
            case "working_plan": return "계획 세우는 중";
            case "waiting": return "질문 대기";
            case "approval": return "결재 대기";
            case "done": return "작업 끝";
            case "unknown": return "확인 중";
            default: return "자유 시간";
        }
    }

    private static int lamp(String v) {
        if (v.startsWith("working")) return Color.parseColor("#5BD69A");
        switch (v) {
            case "waiting": return Color.parseColor("#FFC93C");
            case "approval": return Color.parseColor("#FF9A55");
            case "done": return Color.parseColor("#9AA5B4");
            case "unknown": return Color.parseColor("#8D8799");
            default: return Color.parseColor("#C3CAD4");
        }
    }

    private static void drawLabel(Canvas cv, String name, String v, int cyLogical, float k) {
        boolean hot = v.equals("waiting") || v.equals("approval");
        float cy = cyLogical * k;
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextSize(8 * k);
        tp.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        String sub = " · " + say(v);
        float x0 = 3 * k, tw = tp.measureText(name), sw = tp.measureText(sub);
        RectF box = new RectF(x0, cy - 7 * k, x0 + tw + sw + 16 * k, cy + 6 * k);
        Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        bg.setColor(hot ? Color.parseColor("#FFE3CC") : Color.WHITE);
        cv.drawRoundRect(box, 6 * k, 6 * k, bg);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth((hot ? 1.4f : 1f) * k);
        line.setColor(hot ? Color.parseColor("#E8433A") : Color.parseColor("#9AA5B4"));
        cv.drawRoundRect(box, 6 * k, 6 * k, line);
        Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
        dot.setColor(lamp(v));
        cv.drawCircle(x0 + 6 * k, cy, 3 * k, dot);
        float base = cy - (tp.descent() + tp.ascent()) / 2;
        tp.setColor(Color.parseColor("#2B3340"));
        cv.drawText(name, x0 + 12 * k, base, tp);
        tp.setColor(hot ? Color.parseColor("#D9581F") : Color.parseColor("#5F6B7A"));
        cv.drawText(sub, x0 + 12 * k + tw, base, tp);
    }

    private static JSONObject spotsCache;
    private static JSONObject bubbleSpots(Context c) {
        if (spotsCache != null) return spotsCache;
        try (InputStream in = c.getAssets().open("floors/bubbles.json")) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            spotsCache = new JSONObject(out.toString("UTF-8"));
        } catch (Exception e) { spotsCache = new JSONObject(); }
        return spotsCache;
    }

    /** 말풍선: kind work=노랑(일하는 중), hot=빨강 테두리(선생님 호출), plain=흰색 */
    private static void drawBubble(Canvas cv, String text, float x, float bottom, String kind, float k) {
        Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
        tp.setTextSize(7 * k);
        tp.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        float w = tp.measureText(text) + 10 * k, h = 12 * k;
        float cx = x * k, by = bottom * k;
        float left = Math.max(2 * k, Math.min(LW * k - 2 * k - w, cx - w / 2));
        int bg = kind.equals("work") ? Color.parseColor("#FFF3B8") : kind.equals("hot") ? Color.parseColor("#FFE3CC") : Color.WHITE;
        int line = kind.equals("work") ? Color.parseColor("#D9A300") : kind.equals("hot") ? Color.parseColor("#E8433A") : Color.parseColor("#9AA5B4");
        int ink = kind.equals("hot") ? Color.parseColor("#C2361E") : kind.equals("work") ? Color.parseColor("#5A4300") : Color.parseColor("#2B3340");

        RectF box = new RectF(left, by - h, left + w, by);
        android.graphics.Path tail = new android.graphics.Path();
        tail.moveTo(cx - 2.5f * k, by - 0.5f * k);
        tail.lineTo(cx, by + 3 * k);
        tail.lineTo(cx + 2.5f * k, by - 0.5f * k);
        tail.close();

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(bg);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth((kind.equals("plain") ? 1f : 1.4f) * k);
        stroke.setColor(line);
        cv.drawRoundRect(box, 6 * k, 6 * k, fill);
        cv.drawRoundRect(box, 6 * k, 6 * k, stroke);
        cv.drawPath(tail, fill);
        cv.drawLine(cx - 2.5f * k, by, cx, by + 3 * k, stroke);
        cv.drawLine(cx, by + 3 * k, cx + 2.5f * k, by, stroke);
        tp.setColor(ink);
        tp.setTextAlign(Paint.Align.CENTER);
        cv.drawText(text, left + w / 2, by - h / 2 - (tp.descent() + tp.ascent()) / 2, tp);
    }

    /** 그림 조각을 폭에 맞춰 그려요(조각의 폭 = 사무실 전체 폭). */
    private static void draw(Context c, Canvas cv, Paint p, String asset, int topLogical, float k) {
        try (InputStream in = c.getAssets().open(asset)) {
            Bitmap b = BitmapFactory.decodeStream(in);
            if (b == null) return;
            float scale = (LW * k) / b.getWidth();
            int top = Math.round(topLogical * k);
            Rect dst = new Rect(0, top, Math.round(LW * k), top + Math.round(b.getHeight() * scale));
            cv.drawBitmap(b, null, dst, p);
            b.recycle();
        } catch (Exception ignored) { }
    }
}
