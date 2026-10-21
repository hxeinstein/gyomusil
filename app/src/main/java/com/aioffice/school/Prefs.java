package com.aioffice.school;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 앱 설정(사무실 이름, 폴더, 층)과 마지막으로 받은 상태를 저장해요. */
final class Prefs {
    static final int MAX_FLOORS = 5;
    static final int REFRESH_MINUTES = 3;

    private static final String[] DEF_NAME = {"", "1F 교과연구부", "2F 학급경영부", "3F 업무지원부", "4F 기획실", "5F 연구소"};
    private static final String[] DEF_TAG  = {"", "수업", "담임", "업무", "기획", "연구"};

    private static SharedPreferences sp(Context c) { return c.getSharedPreferences("gyomusil", Context.MODE_PRIVATE); }

    static String title(Context c)   { return sp(c).getString("title", "AI 교무실"); }
    static String folder(Context c)  { return sp(c).getString("folder", ""); }
    static String userKey(Context c) { return sp(c).getString("apikey", ""); }
    static int count(Context c)      { return Math.max(1, Math.min(MAX_FLOORS, sp(c).getInt("count", 3))); }
    static String name(Context c, int f) { return sp(c).getString("name" + f, DEF_NAME[f]); }
    static String tag(Context c, int f)  { return sp(c).getString("tag" + f, DEF_TAG[f]); }
    static String link(Context c, int f) { return sp(c).getString("link" + f, ""); }

    static String apiKey(Context c) {
        String k = userKey(c).trim();
        return k.isEmpty() ? BuildConfig.DRIVE_API_KEY : k;
    }
    static boolean hasBuiltInKey() { return !BuildConfig.DRIVE_API_KEY.isEmpty(); }

    static String folderId(Context c) {
        String s = folder(c).trim();
        Matcher m = Pattern.compile("/folders/([A-Za-z0-9_-]+)").matcher(s);
        if (m.find()) return m.group(1);
        m = Pattern.compile("[?&]id=([A-Za-z0-9_-]+)").matcher(s);
        if (m.find()) return m.group(1);
        return s.matches("[A-Za-z0-9_-]{10,}") ? s : "";
    }

    static boolean configured(Context c) { return !folderId(c).isEmpty() && !apiKey(c).isEmpty(); }

    static void saveBasics(Context c, String title, String folder, String key, int count) {
        sp(c).edit().putString("title", title.trim().isEmpty() ? "AI 교무실" : title.trim())
                .putString("folder", folder.trim()).putString("apikey", key.trim())
                .putInt("count", count).apply();
    }
    static void saveFloor(Context c, int f, String name, String tag, String link) {
        sp(c).edit().putString("name" + f, name.trim()).putString("tag" + f, tag.trim())
                .putString("link" + f, link.trim()).apply();
    }

    /** 화면 쪽에 넘길 설정: 위층부터 {theme, name}. 1층은 t1, 2층은 t2 … */
    static String configJson(Context c) {
        try {
            JSONArray floors = new JSONArray();
            for (int f = count(c); f >= 1; f--) {
                floors.put(new JSONObject().put("theme", "t" + f).put("name", name(c, f)));
            }
            return new JSONObject().put("title", title(c)).put("floors", floors).toString();
        } catch (Exception e) {
            return "null";
        }
    }

    static String lastJson(Context c) { return sp(c).getString("json", ""); }
    static long lastTime(Context c)   { return sp(c).getLong("time", 0); }
    static String lastError(Context c){ return sp(c).getString("err", ""); }
    static void saveJson(Context c, String json) {
        sp(c).edit().putString("json", json).putLong("time", System.currentTimeMillis()).putString("err", "").apply();
    }
    static void saveError(Context c, String err) { sp(c).edit().putString("err", err).apply(); }

    /** 이 층 Claude Code에 붙여 넣을 훅 설정 요청 */
    static String codeRule(Context c, int f) {
        String t = tag(c, f);
        return "이 프로젝트의 작업 상태를 구글 드라이브의 Claude상태 폴더에 자동으로 기록하도록 Claude Code hooks를 설정해 줘. 이 작업은 " + name(c, f) + " 일이야.\n\n"
             + "- 폴더: 이 PC의 구글 드라이브 '내 드라이브/Claude상태' 폴더 (실제 경로를 찾아서 써 줘)\n"
             + "- 파일: 상태_" + t + ".json (서브 PC라면 상태_" + t + "_서브.json)\n"
             + "- 형식: {\"state\":\"working|waiting|approval|done\",\"activity\":\"read|write|run|search|plan\",\"helpers\":0,\"note\":\"한 줄 설명\"} 값은 영어 그대로\n"
             + "- UserPromptSubmit: state=working, activity=plan\n"
             + "- PreToolUse: Read·Grep·Glob은 read, Edit·Write는 write, Bash는 run, WebSearch·WebFetch는 search (state=working)\n"
             + "- Task(하위 에이전트) 도구를 쓰는 동안 helpers=1\n"
             + "- Notification: 권한 요청이면 approval, 그 밖의 입력 대기면 waiting\n"
             + "- Stop: done\n"
             + "- note는 짧은 한국어로 쓰고, 학생 이름 같은 개인정보는 넣지 않는다\n"
             + "- 파일은 매번 통째로 덮어쓴다\n\n"
             + "프로젝트의 .claude/settings.json에 넣고, 넣은 내용을 보여 준 뒤 테스트로 {\"state\":\"done\",\"activity\":\"plan\",\"helpers\":0,\"note\":\"연결 테스트\"}를 한 번 기록해 줘.";
    }

    /** 이 층 Cowork에 붙여 넣을 상태 보고 규칙 */
    static String rule(Context c, int f) {
        String t = tag(c, f);
        return "이 작업은 " + name(c, f) + " 일을 맡아.\n\n"
             + "[상태 기록]\n"
             + "Claude상태 폴더의 상태_" + t + ".json 파일에 아래 형식만 쓴다. (서브 PC 작업이면 상태_" + t + "_서브.json)\n"
             + "값은 영어 그대로.\n"
             + "{\"state\":\"working|waiting|approval|done\",\n"
             + " \"activity\":\"read|write|run|search|plan\",\"helpers\":0,\"note\":\"한 줄 설명\"}\n"
             + "- 새 지시를 받으면 다른 일보다 먼저 working으로 쓰고 시작한다\n"
             + "- 하는 일 종류가 바뀌면 activity를 바꿔 쓴다\n"
             + "- 하위 에이전트를 쓰는 동안 helpers에 그 수(최대 2)\n"
             + "- 나에게 질문하기 직전 waiting, 내 승인이 필요한 행동 직전 approval\n"
             + "- 답이나 승인을 받으면 바로 working으로 되돌린다\n"
             + "- 작업이 끝나거나 멈출 때는 done\n"
             + "- note는 한국어로 짧게 쓰고, 학생 이름 같은 개인정보는 절대 쓰지 않는다\n"
             + "- 파일은 매번 통째로 덮어쓴다\n\n"
             + "먼저 Claude상태 폴더가 연결됐는지 확인해서 말해 주고,\n"
             + "연결 테스트로 {\"state\":\"done\",\"activity\":\"plan\",\"helpers\":0,\"note\":\"연결 테스트\"}를 한 번 쓴 뒤 썼다고 알려 줘.";
    }
}
