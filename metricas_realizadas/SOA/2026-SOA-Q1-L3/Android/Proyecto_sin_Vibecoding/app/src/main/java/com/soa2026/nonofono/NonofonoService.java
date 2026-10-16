package com.soa2026.nonofono;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

public class NonofonoService extends Service {

    private static final String CHANNEL_ID = "NonofonoChannel";
    private MQTTManager mqttManager;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.i("NonofonoService", "Servicio creado. Inicializando MQTT...");

        mqttManager = MQTTManager.getInstance(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //Verificar si recibimos la orden de apagar desde la notificación
        if (intent != null && "ACCION_APAGAR_SERVICIO".equals(intent.getAction())) {
            Log.w("NonofonoService", "Apagando servicio desde la notificación...");
            // Detiene el modo "Foreground" y borra la notificación
            stopForeground(true);
            // esto dispara el onDestroy
            stopSelf();
            return START_NOT_STICKY;
        }

        // --- Flujo normal de inicio del servicio ---

        crearCanalNotificacion();
        Bitmap fotoGrande = BitmapFactory.decodeResource(getResources(), R.mipmap.ic_launcher);

        // Creamos  la instruccion (PendingIntent) para el botón de apagar
        Intent intentApagar = new Intent(this, NonofonoService.class);
        intentApagar.setAction("ACCION_APAGAR_SERVICIO");

        // FLAG_IMMUTABLE es obligatorio en las versiones nuevas de Android por seguridad
        android.app.PendingIntent pendingIntentApagar = android.app.PendingIntent.getService(
                this,
                0,
                intentApagar,
                android.app.PendingIntent.FLAG_IMMUTABLE
        );

        // Construimos la notificación agregando el botón (Action)
        Notification notificacion = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Nonófono Activo \uD83D\uDCF1")
                .setContentText("El nonófono está esperando tus mensajes.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setLargeIcon(fotoGrande)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(R.mipmap.ic_launcher, "Apagar Nonófono 🛑", pendingIntentApagar)
                .build();

        startForeground(
                1,
                notificacion,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        );

        // Conectamos MQTT
        new Thread(() -> {
            try {
                // Aseguramos que solo conecte si no estaba conectado ya
                if (!mqttManager.isConnected()) {
                    mqttManager.conectar();
                    mqttManager.suscribirse();
                    Log.i("NonofonoService", "MQTT Conectado y suscrito desde el Servicio");
                }
            } catch (Exception e) {
                Log.e("NonofonoService", "Error iniciando MQTT: " + e.getMessage());
            }
        }).start();

        return START_STICKY;
    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.w("NonofonoService", "Servicio destruido");

        // Desconectamos MQTT para no dejar sesiones colgadas
        if (mqttManager != null && mqttManager.isConnected()) {
            try {
                mqttManager.desconectar();
            } catch (Exception e) {
                Log.e("NonofonoService", "Error al desconectar MQTT", e);
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void crearCanalNotificacion() {
        NotificationChannel serviceChannel = new NotificationChannel(
                CHANNEL_ID,
                "Canal de Servicio Nonófono",
                NotificationManager.IMPORTANCE_LOW
        );
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(serviceChannel);
        }
    }
}