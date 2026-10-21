package com.aioffice.school;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

/** 층을 눌렀을 때 열 화면: 주소가 있으면 그 주소, 없으면 Claude 앱 */
final class Opener {
    static Intent forFloor(Context c, int floor) {
        String link = floor > 0 ? Prefs.link(c, floor) : "";
        Intent i;
        if (link.startsWith("https://")) {
            i = new Intent(Intent.ACTION_VIEW, Uri.parse(link));
        } else {
            i = c.getPackageManager().getLaunchIntentForPackage("com.anthropic.claude");
            if (i == null) i = new Intent(Intent.ACTION_VIEW, Uri.parse("https://claude.ai/"));
        }
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }
}
