package com.example.espejo;

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

    private static final String CLIENT_ID  = "mi_espejo";
    public static final String ACTION_DATA_RECEIVE = "com.example.espejo.action.DATA_RECEIVE";
    public static final String ACTION_CONNECTION_LOST = "com.example.espejo.intent.action.CONNECTION_LOST";

    private MqttClient client;
    private Context mContext;

    private static MqttHandler instance;

    private MqttHandler(Context mContext){

        this.mContext = mContext;

    }

    public static MqttHandler getInstance(Context context) {
        if (instance == null) {
            instance = new MqttHandler(context.getApplicationContext());
        }
        return instance;
    }

    public boolean connect( String brokerUrl) {
        try {


            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);

            MemoryPersistence persistence = new MemoryPersistence();

            client = new MqttClient(brokerUrl,CLIENT_ID, persistence);
            client.connect(options);

            client.setCallback(this);

            return true;
        } catch (MqttException e) {
            Log.d("Aplicacion",e.getMessage()+ "  "+e.getCause());
            return false;
        }
    }

    public void disconnect() {
        try {
            client.disconnect();
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
    public void publish(String topic, String message){
        try{
            MqttMessage mqttMessage = new MqttMessage(message.getBytes());
            mqttMessage.setQos(2);
            client.publish(topic, mqttMessage);
        }catch (MqttException e){
            e.printStackTrace();
        }
    }

    public void suscribe(String topic){
        try{
            client.subscribe(topic);
        }catch (MqttException e){
            e.printStackTrace();
        }
    }

    @Override
    public void connectionLost(Throwable cause) {
        Log.d("MAIN ACTIVITY","conexion perdida"+ cause.getMessage().toString());

        Intent i = new Intent(ACTION_CONNECTION_LOST);
        mContext.sendBroadcast(i);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
        String payload = message.toString();

        Intent i = new Intent(ACTION_DATA_RECEIVE);
        i.putExtra("topic", topic);
        i.putExtra("payload", payload);

        mContext.sendBroadcast(i);
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {

    }

}
