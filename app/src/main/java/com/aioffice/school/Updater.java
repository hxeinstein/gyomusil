package com.aioffice.school;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** 위젯 새로 고침 예약. 몇 분마다 이어서 돌고, 15분 주기 예약을 보험으로 둬요. */
final class Updater {
    private static Constraints net() {
        return new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    }

    static void start(Context c) {
        WorkManager.getInstance(c).enqueueUniquePeriodicWork("periodic", ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(UpdateWorker.class, 15, TimeUnit.MINUTES).setConstraints(net()).build());
        refreshNow(c);
    }

    static void stop(Context c) {
        WorkManager wm = WorkManager.getInstance(c);
        wm.cancelUniqueWork("periodic");
        wm.cancelUniqueWork("chain");
        wm.cancelUniqueWork("now");
    }

    static void refreshNow(Context c) {
        WorkManager.getInstance(c).enqueueUniqueWork("now", ExistingWorkPolicy.REPLACE,
                new OneTimeWorkRequest.Builder(UpdateWorker.class).setConstraints(net()).build());
    }

    static void scheduleNext(Context c, boolean fromChain) {
        WorkManager wm = WorkManager.getInstance(c);
        if (!fromChain) {
            try {
                List<WorkInfo> infos = wm.getWorkInfosForUniqueWork("chain").get();
                for (WorkInfo i : infos) if (!i.getState().isFinished() && i.getState() != WorkInfo.State.RUNNING) return;
            } catch (Exception ignored) { }
        }
        OneTimeWorkRequest next = new OneTimeWorkRequest.Builder(UpdateWorker.class)
                .setInitialDelay(Prefs.REFRESH_MINUTES, TimeUnit.MINUTES)
                .setConstraints(net())
                .setInputData(new Data.Builder().putBoolean("chain", true).build())
                .build();
        wm.enqueueUniqueWork("chain", fromChain ? ExistingWorkPolicy.APPEND_OR_REPLACE : ExistingWorkPolicy.REPLACE, next);
    }
}
