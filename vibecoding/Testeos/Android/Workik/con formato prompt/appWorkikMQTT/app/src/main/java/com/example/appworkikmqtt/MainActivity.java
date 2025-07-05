package com.example.appworkikmqtt;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;


import android.content.Intent;
import android.widget.Button;
import android.widget.EditText;
import androidx.annotation.Nullable;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

public class MainActivity extends AppCompatActivity {

    private EditText textBox;
    private Button btnPublish, btnSecondActivity;

    private MqttClient mqttClient;
    private final String brokerUrl = "tcp://test.mosquitto.org:1883";
    private final String topicPublish = "/casa/luz";
    private final String topicSubscribe = "/casa/cmd";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textBox = findViewById(R.id.textbox);
        btnPublish = findViewById(R.id.btnPublish);
        btnSecondActivity = findViewById(R.id.btnSecondActivity);

        setupMqttClient();

        btnPublish.setOnClickListener(v -> {
            try {
                // Publicar un mensaje fijo, se puede cambiar para enviar el texto del textbox
                String message = "Mensaje desde app";
                mqttClient.publish(topicPublish, new MqttMessage(message.getBytes()));
            } catch (MqttException e) {
                e.printStackTrace();
            }
        });

        btnSecondActivity.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SecondActivity.class);
            startActivity(intent);
        });
    }

    private void setupMqttClient() {
        try {
            mqttClient = new MqttClient(brokerUrl, MqttClient.generateClientId(), null);
            MqttConnectOptions options = new MqttConnectOptions();
            options.setAutomaticReconnect(true);
            options.setCleanSession(true);
            mqttClient.setCallback(new MqttCallbackExtended() {
                @Override
                public void connectComplete(boolean reconnect, String serverURI) {
                    try {
                        mqttClient.subscribe(topicSubscribe);
                    } catch (MqttException e) {
                        e.printStackTrace();
                    }
                }
                @Override
                public void connectionLost(Throwable cause) {
                }
                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    if (topic.equals(topicSubscribe)) {
                        runOnUiThread(() -> textBox.setText(new String(message.getPayload())));
                    }
                }
                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });

            mqttClient.connect(options);

        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
            } catch (MqttException e) {
                e.printStackTrace();
            }
        }
    }
}