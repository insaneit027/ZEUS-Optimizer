package com.zeus.optimizer;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

/** Lembrete semanal de otimização (e reagendamento após reiniciar). */
public class ReminderReceiver extends BroadcastReceiver {

    static final String ACTION = "com.zeus.optimizer.LEMBRETE";
    private static final String CHANNEL = "zeus_lembrete";
    private static final long WEEK = 7L * 24 * 60 * 60 * 1000;

    @Override
    public void onReceive(Context c, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (enabled(c)) schedule(c);
            return;
        }
        if (ACTION.equals(intent.getAction()) && enabled(c)) notifyNow(c);
    }

    static boolean enabled(Context c) {
        return c.getSharedPreferences("zeus", Context.MODE_PRIVATE).getBoolean("lembrete", false);
    }

    static void setEnabled(Context c, boolean on) {
        c.getSharedPreferences("zeus", Context.MODE_PRIVATE).edit().putBoolean("lembrete", on).apply();
        if (on) schedule(c);
        else cancel(c);
    }

    private static PendingIntent pending(Context c) {
        Intent i = new Intent(c, ReminderReceiver.class).setAction(ACTION);
        return PendingIntent.getBroadcast(c, 1, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static void schedule(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        am.setInexactRepeating(AlarmManager.RTC, System.currentTimeMillis() + WEEK, WEEK, pending(c));
    }

    static void cancel(Context c) {
        AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
        if (am != null) am.cancel(pending(c));
    }

    private static void notifyNow(Context c) {
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Lembrete de otimização",
                NotificationManager.IMPORTANCE_DEFAULT));
        PendingIntent open = PendingIntent.getActivity(c, 0, new Intent(c, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(R.drawable.ic_bolt)
                .setContentTitle("⚡ Hora de otimizar")
                .setContentText("Faz uma semana desde a última limpeza. Toque para deixar o celular mais rápido.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        nm.notify(7, n);
    }
}
