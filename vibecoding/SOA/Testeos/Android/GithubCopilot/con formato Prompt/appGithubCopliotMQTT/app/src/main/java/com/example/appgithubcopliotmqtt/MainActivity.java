package com.example.appgithubcopliotmqtt;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import org.eclipse.paho.client.mqttv3.*;

import info.mqtt.android.service.MqttAndroidClient;

public class MainActivity extends AppCompatActivity {
    private MqttAndroidClient mqttClient;
    private EditText editText;
    private boolean mqttConnected = false;

    private final String brokerUrl = "tcp://test.mosquitto.org:1883";
    private final String topicPub = "/casa/luz";
    private final String topicSub = "/casa/cmd";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button btnPublicar = findViewById(R.id.btnPublicar);
        Button btnSegunda = findViewById(R.id.btnSegunda);
        editText = findViewById(R.id.editText);
        btnPublicar.setEnabled(false);


        mqttClient = new MqttAndroidClient(this, brokerUrl, MqttClient.generateClientId());
        mqttClient.connect(null, new IMqttActionListener() {
            @Override
            public void onSuccess(IMqttToken asyncActionToken) {
                mqttConnected = true;
                runOnUiThread(() -> btnPublicar.setEnabled(true)); // Habilita el botón en el hilo principal
                mqttClient.subscribe(topicSub, 0);
            }

            @Override
            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                mqttConnected = false;
                runOnUiThread(() -> btnPublicar.setEnabled(false)); // Deshabilita el botón en el hilo principal
                Log.e("MQTT", "Error al conectar: " + exception.getMessage());
            }
        });

        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {}
            @Override
            public void messageArrived(String topic, MqttMessage message) {
                runOnUiThread(() -> editText.setText(new String(message.getPayload())));
            }
            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {}
        });

        // En el onClick:
        btnPublicar.setOnClickListener(v -> {
            if (mqttConnected) {
                String msg = "Mensaje desde Android";
                mqttClient.publish(topicPub, new MqttMessage(msg.getBytes()));
            }
        });

        btnSegunda.setOnClickListener(v -> {
            startActivity(new Intent(this, activity_second.class));
        });
    }
}
