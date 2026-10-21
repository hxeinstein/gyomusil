package com.aioffice.school;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/** 구글 드라이브 공유 폴더의 상태 파일을 읽어 층별 상태로 합쳐요. */
final class DriveClient {

    static void refresh(Context c) {
        try {
            JSONObject data = fetch(c);
            Prefs.saveJson(c, data.toString());
        } catch (Exception e) {
            Prefs.saveError(c, e.getMessage() == null ? "연결 오류" : e.getMessage());
        }
    }

    static JSONObject fetch(Context c) throws Exception {
        String folder = Prefs.folderId(c), key = Prefs.apiKey(c);
        if (folder.isEmpty()) throw new Exception("폴더 링크를 확인해 주세요");
        if (key.isEmpty()) throw new Exception("API 키를 넣어 주세요");

        String q = URLEncoder.encode("'" + folder + "' in parents and trashed=false", "UTF-8");
        String listUrl = "https://www.googleapis.com/drive/v3/files?q=" + q
                + "&fields=files(id,name,modifiedTime)&pageSize=200&key=" + URLEncoder.encode(key, "UTF-8");
        JSONArray files = new JSONObject(get(listUrl)).optJSONArray("files");
        if (files == null) files = new JSONArray();

        JSONObject floors = new JSONObject();
        for (int f = 1; f <= Prefs.count(c); f++) {
            String tag = Prefs.tag(c, f);
            List<JSONObject> list = new ArrayList<>();
            for (int i = 0; i < files.length(); i++) {
                JSONObject fi = files.getJSONObject(i);
                String name = fi.optString("name");
                boolean match = name.equals("상태_" + tag + ".json")
                        || (name.startsWith("상태_" + tag + "_") && name.endsWith(".json"));
                if (!match || tag.isEmpty()) continue;
                try {
                    String body = get("https://www.googleapis.com/drive/v3/files/" + fi.getString("id")
                            + "?alt=media&key=" + URLEncoder.encode(key, "UTF-8"));
                    JSONObject d = normalize(new JSONObject(body));
                    d.put("updatedAt", fi.optString("modifiedTime"));
                    d.put("source", name);
                    list.add(d);
                } catch (Exception ignored) { }    // 형식이 깨진 파일은 건너뛰어요
            }
            JSONObject top = merge(list);
            if (top != null) {
                top.put("link", Prefs.link(c, f));
                floors.put("t" + f, top);
            } else {
                floors.put("t" + f, JSONObject.NULL);
            }
        }
        return new JSONObject().put("floors", floors).put("now", System.currentTimeMillis());
    }

    /** Cowork가 한글로 써도 알아듣게 맞춰요 */
    static JSONObject normalize(JSONObject raw) throws Exception {
        String st = pick(raw, "state", "상태");
        String state = "working";
        if (st.matches("(?is).*(approval|결재|승인).*")) state = "approval";
        else if (st.matches("(?is).*(waiting|질문|막힘|물어).*")) state = "waiting";
        else if (st.matches("(?is).*(done|끝|완료|쉬|휴식|대기).*")) state = "done";

        String ac = pick(raw, "activity", "활동", "하는 일");
        String activity = "write";
        if (ac.matches("(?is).*(read|자료|읽).*")) activity = "read";
        else if (ac.matches("(?is).*(run|실행|업로드|예약|명령).*")) activity = "run";
        else if (ac.matches("(?is).*(search|검색|조사).*")) activity = "search";
        else if (ac.matches("(?is).*(plan|계획|기획|분석).*")) activity = "plan";

        int helpers = 0;
        try { helpers = Integer.parseInt(pick(raw, "helpers", "도우미").replaceAll("[^0-9]", "")); } catch (Exception ignored) { }
        String note = pick(raw, "note", "말풍선", "진행");
        if (note.length() > 40) note = note.substring(0, 40);
        return new JSONObject().put("state", state).put("activity", activity)
                .put("helpers", Math.max(0, Math.min(2, helpers))).put("note", note);
    }

    private static String pick(JSONObject o, String... keys) {
        for (String k : keys) { String v = o.optString(k, ""); if (!v.isEmpty()) return v; }
        return "";
    }

    /** 더 급한 쪽이 먼저: 결재 > 질문 > 작업 중 > 끝 */
    static JSONObject merge(List<JSONObject> list) throws Exception {
        if (list.isEmpty()) return null;
        JSONObject top = null;
        int helpers = 0;
        for (JSONObject d : list) {
            if (d.optString("state").equals("working")) helpers += d.optInt("helpers", 0);
            if (top == null || rank(d) > rank(top)
                    || (rank(d) == rank(top) && d.optString("updatedAt").compareTo(top.optString("updatedAt")) > 0)) top = d;
        }
        return new JSONObject(top.toString()).put("helpers", Math.min(2, helpers));
    }

    private static int rank(JSONObject d) {
        switch (d.optString("state")) {
            case "approval": return 4;
            case "waiting": return 3;
            case "working": return 2;
            default: return 1;
        }
    }

    private static String get(String address) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(address).openConnection();
        con.setConnectTimeout(15000);
        con.setReadTimeout(15000);
        int code = con.getResponseCode();
        if (code == 403) throw new Exception("API 키나 폴더 공유 설정을 확인해 주세요");
        if (code == 404) throw new Exception("폴더를 찾을 수 없어요. 링크 공유를 켰는지 확인해 주세요");
        if (code >= 400) throw new Exception("드라이브 오류 " + code);
        try (InputStream in = con.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        } finally {
            con.disconnect();
        }
    }
}
