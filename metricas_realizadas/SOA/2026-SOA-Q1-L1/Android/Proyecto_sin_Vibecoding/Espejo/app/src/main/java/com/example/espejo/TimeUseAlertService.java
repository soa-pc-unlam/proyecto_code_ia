package com.example.espejo;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

public class TimeUseAlertService extends Service {

    private static final String TAG = "TimeUseAlertService";
    private static final String DISTANCE_KEYWORD = "distancia";
    private static final int DISTANCE_THRESHOLD_MM = 390;
    private static final long ALERT_THRESHOLD_MS = 5000;
    private long lastNoPresenceTimestamp;
    private BroadcastReceiver mqttDataReceiver;
    private static final String CHANNEL_ID = "time_use_alert_channel";
    private static final int FOREGROUND_NOTIFICATION_ID = 1;
    private static final int ALERT_NOTIFICATION_ID = 2;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service onCreate");
        
        createNotificationChannel();
        
        startForeground(FOREGROUND_NOTIFICATION_ID, createForegroundNotification());

        lastNoPresenceTimestamp = System.currentTimeMillis();
        
        mqttDataReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String topic = intent.getStringExtra("topic");
                String payload = intent.getStringExtra("payload");
                handleMqttData(topic, payload);
            }
        };

        IntentFilter filter = new IntentFilter(MqttHandler.ACTION_DATA_RECEIVE);

        registerReceiver(mqttDataReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service onStartCommand");
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Service onDestroy");
        if (mqttDataReceiver != null) {
            unregisterReceiver(mqttDataReceiver);
        }
        super.onDestroy();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Alerta de Uso de Espejo",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Canal para notificaciones de monitoreo de tiempo de uso");
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createForegroundNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Monitoreo de Espejo")
                .setContentText("El servicio de alerta de uso está activo en segundo plano.")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .build();
    }

    private void handleMqttData(String topic, String payload) {
        if (topic != null && topic.contains(DISTANCE_KEYWORD)) {
            try {
                float distance = Float.parseFloat(payload);
                long currentTime = System.currentTimeMillis();

                if (distance >= DISTANCE_THRESHOLD_MM) {
                    lastNoPresenceTimestamp = currentTime;
                } else {
                    long timeInUse = currentTime - lastNoPresenceTimestamp;
                    if (timeInUse > ALERT_THRESHOLD_MS) {
                        sendTimeUseAlert();
                        lastNoPresenceTimestamp = currentTime;
                    }
                }
            } catch (NumberFormatException e) {
                Log.e(TAG, "Error al parsear distancia: " + payload);
            }
        }
    }

    private void sendTimeUseAlert() {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("¡Cuidado!")
                .setContentText("Llevas mucho tiempo frente al espejo.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        try {
            notificationManager.notify(ALERT_NOTIFICATION_ID, builder.build());
        } catch (SecurityException e) {
            Log.e(TAG, "No se tienen permisos para enviar notificaciones: " + e.getMessage());
        }
    }
}
