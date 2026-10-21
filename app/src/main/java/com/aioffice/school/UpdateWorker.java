package com.aioffice.school;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

/** 드라이브에서 상태를 받아 위젯 그림을 다시 그려요. */
public class UpdateWorker extends Worker {
    public UpdateWorker(@NonNull Context c, @NonNull WorkerParameters p) { super(c, p); }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        if (!Prefs.configured(c)) return Result.success();
        DriveClient.refresh(c);
        WidgetText.refresh(c);
        Renderer.update(c);
        int[] ids = AppWidgetManager.getInstance(c).getAppWidgetIds(new ComponentName(c, OfficeWidget.class));
        if (ids != null && ids.length > 0) Updater.scheduleNext(c, getInputData().getBoolean("chain", false));
        return Result.success();
    }
}
