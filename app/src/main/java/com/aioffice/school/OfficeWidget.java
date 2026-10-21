package com.aioffice.school;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;

/** 바탕화면 위젯 */
public class OfficeWidget extends AppWidgetProvider {
    static final String ACTION_REFRESH = "com.aioffice.school.REFRESH";

    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        Renderer.update(c);
        if (Prefs.configured(c)) Updater.start(c);
    }

    @Override
    public void onEnabled(Context c) { if (Prefs.configured(c)) Updater.start(c); }

    @Override
    public void onDisabled(Context c) { Updater.stop(c); }

    @Override
    public void onReceive(Context c, Intent intent) {
        super.onReceive(c, intent);
        if (ACTION_REFRESH.equals(intent.getAction())) Updater.refreshNow(c);
    }
}
