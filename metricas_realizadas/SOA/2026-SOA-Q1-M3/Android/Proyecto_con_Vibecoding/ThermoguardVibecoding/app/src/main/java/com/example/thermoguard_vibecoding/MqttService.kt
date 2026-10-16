package com.example.thermoguard_vibecoding

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttMessage

class MqttService : Service() {

    private lateinit var mqttManager: MqttManager

    override fun onCreate() {
        super.onCreate()
        mqttManager = MqttManager.getInstance(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        val broker = intent?.getStringExtra("broker") ?: prefs.getString(Constants.KEY_BROKER, Constants.DEFAULT_BROKER)
        val port = intent?.getStringExtra("port") ?: prefs.getString(Constants.KEY_PORT, Constants.DEFAULT_PORT)
        val serverUri = "tcp://$broker:$port"

        startForeground(Constants.NOTIFICATION_ID, createNotification("Conectando a ThermoGuard...", ""))

        mqttManager.connect(serverUri, "ThermoGuardService_${System.currentTimeMillis()}", {
            mqttManager.subscribe(Constants.TOPIC_SENSOR_ESTADO)
            mqttManager.subscribe(Constants.TOPIC_SENSOR_TEMP)
            updateNotification("ThermoGuard Conectado", "Monitoreando estado...")
        }, {
            updateNotification("Error de Conexión", "No se pudo conectar al broker.")
        })

        mqttManager.addListener(mqttCallback)

        return START_STICKY
    }

    private val mqttCallback = object : MqttCallbackExtended {
        override fun connectComplete(reconnect: Boolean, serverURI: String?) {
            mqttManager.subscribe(Constants.TOPIC_SENSOR_ESTADO)
            mqttManager.subscribe(Constants.TOPIC_SENSOR_TEMP)
        }

        override fun connectionLost(cause: Throwable?) {}

        override fun messageArrived(topic: String?, message: MqttMessage?) {
            val rawPayload = message?.toString() ?: ""
            val payload = rawPayload.trim().replace("\"", "").uppercase()
            
            if (topic == Constants.TOPIC_SENSOR_ESTADO) {
                if (payload == Constants.STATE_ALERT) {
                    triggerAlertNotification(payload)
                }
            }
        }

        override fun deliveryComplete(token: IMqttDeliveryToken?) {}
    }

    private fun triggerAlertNotification(state: String) {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("Estado Crítico Detectado: $state")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(0, 500, 200, 500))
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(Constants.NOTIFICATION_ID + 1, notification)
    }

    private fun createNotification(title: String, text: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(Constants.NOTIFICATION_ID, createNotification(title, text))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.NOTIFICATION_CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones de alertas críticas de temperatura"
                enableLights(true)
                lightColor = Color.RED
                enableVibration(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        mqttManager.removeListener(mqttCallback)
        super.onDestroy()
    }
}