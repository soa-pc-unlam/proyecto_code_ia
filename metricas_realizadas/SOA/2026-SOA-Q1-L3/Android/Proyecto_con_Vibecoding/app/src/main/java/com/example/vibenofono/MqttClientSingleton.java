package com.example.vibenofono;

import android.content.Context;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import javax.net.ssl.SSLSocketFactory;

public class MqttClientSingleton {
    private static final String TAG = "MqttClientSingleton";
    private static final String BROKER_URL = "ssl://e32525b034cd4ef5a01f6e9715295f64.s1.eu.hivemq.cloud:8883";
    private static final String CLIENT_ID = "NonofonoAppClient";
    private static final String USERNAME = "admin_prueba";
    private static final String PASSWORD = "Nonofono8";
    private static MqttClientSingleton INSTANCE;

    private final Context context;
    private MqttAndroidClient client;

    private MqttClientSingleton(Context context) {
        this.context = context.getApplicationContext();
    }

    public static synchronized void initialize(Context context) {
        if (INSTANCE == null) {
            INSTANCE = new MqttClientSingleton(context);
        }
    }

    public static MqttClientSingleton getInstance() {
        return INSTANCE;
    }

    public void connect(MqttCallback callback, Runnable onConnected, Runnable onDisconnected) {
        try {
            if (client == null || !client.isConnected()) {
                client = new MqttAndroidClient(context, BROKER_URL, CLIENT_ID);
                MqttConnectOptions options = new MqttConnectOptions();
                options.setSocketFactory(SSLSocketFactory.getDefault());
                options.setUserName(USERNAME);
                options.setPassword(PASSWORD.toCharArray());
                options.setAutomaticReconnect(true);
                options.setCleanSession(true);
                options.setConnectionTimeout(10);
                options.setKeepAliveInterval(30);

                client.setCallback(callback);
                client.connect(options, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        Log.i(TAG, "MQTT conectado");
                        subscribeDefaultTopics();
                        onConnected.run();
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Log.e(TAG, "Error conectando MQTT", exception);
                        onDisconnected.run();
                    }
                });
            } else {
                onConnected.run();
            }
        } catch (MqttException e) {
            Log.e(TAG, "Error iniciando conexión MQTT", e);
            onDisconnected.run();
        }
    }

    private void subscribeDefaultTopics() {
        try {
            if (client != null && client.isConnected()) {
                client.subscribe("nonofono/emergencias", 1);
                client.subscribe("nonofono/mensajes/audio", 1);
                client.subscribe("nonofono/mensajes/predeterminado", 1);
                client.subscribe("nonofono/live", 1);
            }
        } catch (MqttException e) {
            Log.e(TAG, "Error suscribiendo temas MQTT", e);
        }
    }

    public void publish(String topic, String payload) {
        try {
            if (client != null && client.isConnected()) {
                MqttMessage message = new MqttMessage(payload.getBytes());
                message.setQos(1);
                client.publish(topic, message);
            }
        } catch (MqttException e) {
            Log.e(TAG, "Error publicando MQTT", e);
        }
    }

    public void disconnect() {
        if (client != null) {
            try {
                client.disconnect();
                client.close();
                client = null;
                Log.i(TAG, "MQTT desconectado y cerrado");
            } catch (MqttException e) {
                Log.e(TAG, "Error desconectando MQTT", e);
            }
        }
    }
}
