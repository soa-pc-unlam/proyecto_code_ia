package com.ashencostha.mqtt;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MqttHandler implements MqttCallback {

    // Estos ya no los usás porque ahora configurás todo desde ConfigMQTT,
    // pero los dejo por si algo los referencia en otro lado.
    public static final String BROKER_URL = "tcp://industrial.api.ubidots.com:1883";
    public static final String CLIENT_ID  = "mqttx_f9bfd3ww";
    public static final String USER       = "BBUS-Z1MN1sGYdyPI8Mut0NahTzna5JVrBn";
    public static final String PASS       = "BBUS-Z1MN1sGYdyPI8Mut0NahTzna5JVrBn";

    public static final String TOPIC_STATUS = "/v1.6/devices/simulator/status";
    public static final String TOPIC_STATE  = "/v1.6/devices/simulator/state";
    public static final String TOPIC_TEMPO  = "/v1.6/devices/simulator/tempo";
    public static final String TOPIC_EDIT   = "/v1.6/devices/simulator/edit";

    public static final String ACTION_DATA_RECEIVE   = "com.example.intentservice.intent.action.DATA_RECEIVE";
    public static final String ACTION_CONNECTION_LOST = "com.example.intentservice.intent.action.CONNECTION_LOST";

    private MqttClient client;
    private final Context mContext;

    public MqttHandler(Context mContext){
        this.mContext = mContext;
    }

    public void connect(String brokerUrl, String clientId, String username, String password) {
        try {
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setUserName(username);
            options.setPassword(password.toCharArray());

            MemoryPersistence persistence = new MemoryPersistence();

            client = new MqttClient(brokerUrl, clientId, persistence);
            client.setCallback(this);       // Primero el callback
            client.connect(options);        // Luego conectamos

            Log.d("MqttHandler", "Conectado a broker: " + brokerUrl + " con clientId=" + clientId);

        } catch (MqttException e) {
            Log.d("MqttHandler", "Error al conectar: " + e.getMessage(), e);
        }
    }

    public void disconnect() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();
                Log.d("MqttHandler", "Desconectado de MQTT");
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void publish(String topic, String message) {
        try {
            if (client == null || !client.isConnected()) {
                Log.w("MqttHandler", "publish: cliente no conectado");
                return;
            }
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            mqttMessage.setQos(2);
            client.publish(topic, mqttMessage);
            Log.d("MqttHandler", "Publicado en " + topic + ": " + message);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void subscribe(String topic) {
        try {
            if (client == null || !client.isConnected()) {
                Log.w("MqttHandler", "subscribe: cliente no conectado");
                return;
            }
            client.subscribe(topic);
            Log.d("MqttHandler", "Suscripto a topic: " + topic);
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    public void unsubscribe(final String topic) {
        try {
            if (client == null || !client.isConnected()) {
                Log.w("MqttHandler", "unsubscribe: cliente no conectado");
                return;
            }
            client.unsubscribe(topic);
            Log.d("MqttHandler", "Unsubscribed from topic: " + topic);
        } catch (MqttException e) {
            e.printStackTrace();
            Log.e("MqttHandler", "Exception while unsubscribing from topic: " + topic, e);
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        Log.d("MqttHandler","Conexión perdida: " + (cause != null ? cause.getMessage() : "desconocida"));

        Intent i = new Intent(ACTION_CONNECTION_LOST);
        mContext.sendBroadcast(i);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        // 🔴 IMPORTANTE: ya NO parseamos JSON, usamos el payload tal cual
        String msgJson = new String(message.getPayload());
        Log.d("MqttHandler", "Mensaje recibido. Topic=" + topic + " payload=" + msgJson);

        // Enviar ambos: topic + msgJson, porque MainActivity usa los dos
        Intent i = new Intent(ACTION_DATA_RECEIVE);
        i.putExtra("topic", topic);
        i.putExtra("msgJson", msgJson);

        mContext.sendBroadcast(i);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Podés loguear si querés
        // Log.d("MqttHandler", "deliveryComplete: " + token.toString());
    }
}
