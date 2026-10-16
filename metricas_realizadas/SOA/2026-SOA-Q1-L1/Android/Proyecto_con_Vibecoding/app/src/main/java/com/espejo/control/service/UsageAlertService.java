package com.espejo.control.service;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;
import android.os.SystemClock;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.espejo.control.R;
import com.espejo.control.mqtt.TelemetryBus;
import com.espejo.control.mqtt.TelemetryListener;

/**
 * Servicio en primer plano que vigila los sensores de distancia y avisa si
 * el usuario permanece demasiado tiempo parado frente al espejo.
 */
public class UsageAlertService extends Service implements TelemetryListener {

    private static final double DISTANCE_THRESHOLD = 390.0;
    private static final long TOO_CLOSE_DURATION_MS = 5000L;

    private static final String CHANNEL_PERSISTENT = "espejo_monitoring";
    private static final String CHANNEL_HIGH = "espejo_alert_high";
    private static final int NOTIF_ID_PERSISTENT = 1;
    private static final int NOTIF_ID_ALERT = 2;

    private double lastDistancia1 = Double.MAX_VALUE;
    private double lastDistancia2 = Double.MAX_VALUE;
    private long tooCloseSince = 0L;
    private boolean alertAlreadyFired = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannels();
        TelemetryBus.getInstance().addListener(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIF_ID_PERSISTENT, buildPersistentNotification());
        return START_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        TelemetryBus.getInstance().removeListener(this);
        super.onDestroy();
    }

    @Override
    public void onDistancia1(String valor) {
        Double parsed = parse(valor);
        if (parsed != null) {
            lastDistancia1 = parsed;
            checkProximity();
        }
    }

    @Override
    public void onDistancia2(String valor) {
        Double parsed = parse(valor);
        if (parsed != null) {
            lastDistancia2 = parsed;
            checkProximity();
        }
    }

    @Nullable
    private Double parse(String valor) {
        try {
            return Double.parseDouble(valor);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void checkProximity() {
        double closest = Math.min(lastDistancia1, lastDistancia2);
        boolean tooClose = closest < DISTANCE_THRESHOLD;

        if (!tooClose) {
            tooCloseSince = 0L;
            alertAlreadyFired = false;
            return;
        }

        if (tooCloseSince == 0L) {
            tooCloseSince = SystemClock.elapsedRealtime();
            return;
        }

        if (!alertAlreadyFired && SystemClock.elapsedRealtime() - tooCloseSince >= TOO_CLOSE_DURATION_MS) {
            alertAlreadyFired = true;
            fireHighPriorityAlert();
        }
    }

    private void createNotificationChannels() {
        NotificationManager manager = getSystemService(NotificationManager.class);

        NotificationChannel persistentChannel = new NotificationChannel(
                CHANNEL_PERSISTENT, getString(R.string.alert_channel_persistent_name),
                NotificationManager.IMPORTANCE_LOW);
        manager.createNotificationChannel(persistentChannel);

        NotificationChannel highChannel = new NotificationChannel(
                CHANNEL_HIGH, getString(R.string.alert_channel_high_name),
                NotificationManager.IMPORTANCE_HIGH);
        manager.createNotificationChannel(highChannel);
    }

    private Notification buildPersistentNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_PERSISTENT)
                .setContentTitle(getString(R.string.alert_persistent_title))
                .setContentText(getString(R.string.alert_persistent_text))
                .setSmallIcon(R.drawable.ic_sensores)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void fireHighPriorityAlert() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_HIGH)
                .setContentTitle(getString(R.string.alert_high_title))
                .setContentText(getString(R.string.alert_high_text))
                .setSmallIcon(R.drawable.ic_sensores)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build();
        NotificationManagerCompat.from(this).notify(NOTIF_ID_ALERT, notification);
    }
}
