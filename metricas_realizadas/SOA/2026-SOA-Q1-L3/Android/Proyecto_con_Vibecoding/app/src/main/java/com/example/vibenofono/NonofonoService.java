package com.example.vibenofono;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.List;

public class NonofonoService extends Service {
    public static final String ACTION_STOP_SERVICE = "com.example.vibenofono.ACTION_STOP_SERVICE";
    public static final String ACTION_MQTT_STATUS = "com.example.vibenofono.ACTION_MQTT_STATUS";
    public static final String ACTION_TELEGRAM_STATUS = "com.example.vibenofono.ACTION_TELEGRAM_STATUS";
    public static final String EXTRA_STATUS_TEXT = "status_text";
    private static final String CHANNEL_ID = "nonofono_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "NonofonoService";
    private MqttClientSingleton mqttClient;
    private ContactosManager contactosManager;
    private boolean serviceStarted;

    @Override
    public void onCreate() {
        super.onCreate();
        contactosManager = new ContactosManager(this);
        MqttClientSingleton.initialize(this);
        mqttClient = MqttClientSingleton.getInstance();
    }

    private void connectMqtt() {
        if (mqttClient == null) {
            return;
        }
        mqttClient.connect(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                sendStatus(ACTION_MQTT_STATUS, getString(R.string.mqtt_status_disconnected));
                Log.e(TAG, "MQTT connection lost", cause);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) {
                processMqttMessage(topic, new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
                // no-op
            }
        }, () -> {
            sendStatus(ACTION_MQTT_STATUS, getString(R.string.mqtt_status_connected));
        }, () -> {
            sendStatus(ACTION_MQTT_STATUS, getString(R.string.mqtt_status_disconnected));
        });
    }

    private void processMqttMessage(String topic, String payload) {
        Log.i(TAG, "Mensaje MQTT recibido en " + topic + ": " + payload);
        if (topic.equals("nonofono/live")) {
            sendStatus(ACTION_TELEGRAM_STATUS, getString(R.string.telegram_status_live));
            new Thread(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException ignored) {
                }
                mqttClient.publish("nonofono/config/contactos", contactosManager.toJsonObjectArray());
            }).start();
            return;
        }

        try {
            JSONObject root = new JSONObject(payload);
            JSONObject contenido = root.optJSONObject("contenido");
            if (contenido == null) {
                return;
            }

            if (topic.equals("nonofono/emergencias")) {
                String mensaje = contenido.optString("mensaje", "Emergencia recibida");
                notifyTelegramStatus(getString(R.string.telegram_status_emergencia));
                enviarEmergencia(mensaje);
            } else if (topic.equals("nonofono/mensajes/predeterminado")) {
                String telefono = contenido.optString("telefono", "");
                String texto = contenido.optString("contenido", "");
                if (!telefono.isEmpty()) {
                    notifyTelegramStatus(getString(R.string.telegram_status_texto));
                    enviarMensajePredeterminado(telefono, texto);
                }
            } else if (topic.equals("nonofono/mensajes/audio")) {
                String ip = contenido.optString("ip", "");
                String endpoint = contenido.optString("endpoint", "");
                String destinatario = contenido.optString("destinatario", "");
                if (!ip.isEmpty() && !endpoint.isEmpty() && !destinatario.isEmpty()) {
                    notifyTelegramStatus(getString(R.string.telegram_status_audio));
                    enviarAudio(ip, endpoint, destinatario);
                }
            }
        } catch (JSONException e) {
            Log.e(TAG, "Error parseando mensaje MQTT", e);
        }
    }

    private void enviarEmergencia(String mensaje) {
        List<String> contactos = contactosManager.obtenerContactos();
        for (String contacto : contactos) {
            TelegramClient.sendMessage(NonofonoConstants.BOT_TOKEN, contacto, mensaje);
        }
    }

    private void enviarMensajePredeterminado(String telefono, String contenido) {
        boolean success = TelegramClient.sendMessage(NonofonoConstants.BOT_TOKEN, telefono, contenido);
        if (success) {
            mqttClient.publish("nonofono/mensajes/predeterminado/ack", "{\"exito\": \"Ok\"}");
        }
    }

    private void enviarAudio(String ip, String endpoint, String destinatario) {
        String audioPath = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
        File audioFile = AudioDownloader.downloadAudio(this, ip, audioPath);
        if (audioFile != null) {
            boolean sent = TelegramClient.sendAudio(NonofonoConstants.BOT_TOKEN, destinatario, audioFile);
            if (sent) {
                mqttClient.publish("nonofono/mensajes/audio/ack", "{\"exito\": \"Ok\"}");
            }
        }
    }

    private void notifyTelegramStatus(String status) {
        sendStatus(ACTION_TELEGRAM_STATUS, status);
    }

    private void sendStatus(String action, String text) {
        Intent broadcast = new Intent(action);
        broadcast.putExtra(EXTRA_STATUS_TEXT, text);
        sendBroadcast(broadcast);
    }

    private Notification buildNotification() {
        createNotificationChannel();
        Intent stopIntent = new Intent(this, NonofonoService.class);
        stopIntent.setAction(ACTION_STOP_SERVICE);
        PendingIntent stopPendingIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_text))
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setOngoing(true)
                .addAction(new NotificationCompat.Action.Builder(
                        0,
                        getString(R.string.action_stop_service),
                        stopPendingIntent)
                        .build())
                .build();
    }

    public static void publishFindMessage(Context context) {
        MqttClientSingleton instance = MqttClientSingleton.getInstance();
        if (instance != null) {
            instance.publish("nonofono/encontrar", "{\"action\":\"localizar\"}");
        }
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW);
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            manager.createNotificationChannel(channel);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP_SERVICE.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        if (!serviceStarted) {
            startForeground(NOTIFICATION_ID, buildNotification());
            connectMqtt();
            serviceStarted = true;
        }

        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mqttClient != null) {
            mqttClient.disconnect();
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
