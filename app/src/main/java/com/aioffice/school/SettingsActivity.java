package com.aioffice.school;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/** 설정 화면: 사무실 이름, 폴더 링크, API 키, 층 수, 층마다 이름·파일 이름표·Claude 주소 */
public class SettingsActivity extends Activity {
    private static final int INK = Color.parseColor("#2B3340"), SUB = Color.parseColor("#5F6B7A");
    private EditText title, folder, key;
    private RadioGroup countGroup;
    private final EditText[] names = new EditText[Prefs.MAX_FLOORS + 1];
    private final EditText[] tags = new EditText[Prefs.MAX_FLOORS + 1];
    private final EditText[] links = new EditText[Prefs.MAX_FLOORS + 1];
    private final View[] blocks = new View[Prefs.MAX_FLOORS + 1];

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(28), dp(20), dp(40));
        box.setBackgroundColor(Color.parseColor("#F1F2F4"));
        scroll.addView(box);

        box.addView(text("AI 교무실 설정", 22, INK, true));
        box.addView(text("Claude(Cowork·Claude Code)가 Claude상태 폴더에 남긴 기록을 읽어 층마다 지금 무슨 일을 하는지 보여줘요.", 13, SUB, false));
        Button guide = new Button(this);
        guide.setText("📖 설명서 보기");
        guide.setOnClickListener(v -> startActivity(new Intent(this, GuideActivity.class)));
        box.addView(guide);

        box.addView(label("사무실 이름"));
        title = field("예: 도덕후 AI 교무실", Prefs.title(this));
        box.addView(title);

        box.addView(label("Claude상태 폴더 공유 링크"));
        folder = field("드라이브에서 '링크가 있는 모든 사용자'로 공유한 링크", Prefs.folder(this));
        folder.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        box.addView(folder);

        box.addView(label(Prefs.hasBuiltInKey() ? "구글 API 키 (비워 두면 기본 키 사용)" : "구글 API 키 (필수)"));
        key = field("Google Drive API 키", Prefs.userKey(this));
        box.addView(key);

        box.addView(label("층 수"));
        countGroup = new RadioGroup(this);
        countGroup.setOrientation(RadioGroup.HORIZONTAL);
        for (int i = 1; i <= Prefs.MAX_FLOORS; i++) {
            RadioButton r = new RadioButton(this);
            r.setText(i + "층");
            r.setId(1000 + i);
            r.setTextColor(INK);
            countGroup.addView(r);
        }
        countGroup.check(1000 + Prefs.count(this));
        countGroup.setOnCheckedChangeListener((g, id) -> showBlocks(id - 1000));
        box.addView(countGroup);

        for (int f = Prefs.MAX_FLOORS; f >= 1; f--) box.addView(floorBlock(f));
        showBlocks(Prefs.count(this));

        TextView privacy = text("상태 폴더는 링크를 아는 사람이 볼 수 있어요. 복사되는 규칙에 '학생 이름 같은 개인정보는 쓰지 않는다'가 들어 있으니 그대로 쓰세요.", 12, Color.parseColor("#8A5A2B"), false);
        privacy.setPadding(0, dp(16), 0, dp(8));
        box.addView(privacy);

        Button save = new Button(this);
        save.setText("저장하고 시작");
        save.setOnClickListener(v -> save());
        box.addView(save, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        setContentView(scroll);
    }

    private View floorBlock(int f) {
        LinearLayout b = new LinearLayout(this);
        b.setOrientation(LinearLayout.VERTICAL);
        b.setPadding(dp(14), dp(12), dp(14), dp(12));
        b.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        b.setLayoutParams(lp);

        b.addView(text(f + "층", 16, INK, true));
        b.addView(label("층 이름"));
        names[f] = field("예: " + f + "F 교과연구부", Prefs.name(this, f));
        b.addView(names[f]);
        b.addView(label("파일 이름표 (상태_이름표.json)"));
        tags[f] = field("예: 수업", Prefs.tag(this, f));
        b.addView(tags[f]);
        b.addView(label("Claude 작업 주소 (선택, 비우면 Claude 앱이 열려요)"));
        links[f] = field("https://claude.ai/…", Prefs.link(this, f));
        links[f].setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        b.addView(links[f]);

        Button copy = new Button(this);
        copy.setText("Cowork용 규칙 복사");
        copy.setOnClickListener(v -> {
            saveFloors();
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Cowork 규칙", Prefs.rule(this, f)));
            Toast.makeText(this, f + "층 Cowork 규칙을 복사했어요. Cowork 메시지로 붙여 넣으세요.", Toast.LENGTH_LONG).show();
        });
        b.addView(copy);

        Button code = new Button(this);
        code.setText("Claude Code용 설정 복사");
        code.setOnClickListener(v -> {
            saveFloors();
            ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            cm.setPrimaryClip(ClipData.newPlainText("Claude Code 설정", Prefs.codeRule(this, f)));
            Toast.makeText(this, f + "층 Claude Code 설정 문구를 복사했어요. Claude Code에 붙여 넣으세요.", Toast.LENGTH_LONG).show();
        });
        b.addView(code);
        blocks[f] = b;
        return b;
    }

    private void showBlocks(int n) {
        for (int f = 1; f <= Prefs.MAX_FLOORS; f++) blocks[f].setVisibility(f <= n ? View.VISIBLE : View.GONE);
    }

    private void saveFloors() {
        for (int f = 1; f <= Prefs.MAX_FLOORS; f++) {
            Prefs.saveFloor(this, f, names[f].getText().toString(), tags[f].getText().toString(), links[f].getText().toString());
        }
    }

    private void save() {
        int n = countGroup.getCheckedRadioButtonId() - 1000;
        Prefs.saveBasics(this, title.getText().toString(), folder.getText().toString(), key.getText().toString(), n);
        saveFloors();
        if (Prefs.folderId(this).isEmpty()) { toast("폴더 링크를 확인해 주세요"); return; }
        if (Prefs.apiKey(this).isEmpty()) { toast("API 키를 넣어 주세요"); return; }
        for (int f = 1; f <= n; f++) if (Prefs.tag(this, f).isEmpty()) { toast(f + "층 파일 이름표를 넣어 주세요"); return; }
        Updater.start(this);
        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
        finish();
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(0, dp(4), 0, dp(4));
        return t;
    }
    private TextView label(String s) {
        TextView t = text(s, 13, SUB, false);
        t.setPadding(0, dp(12), 0, 0);
        return t;
    }
    private EditText field(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        e.setTextColor(INK);
        return e;
    }
    private void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
