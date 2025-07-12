package com.example.appwindsurfmqttjava;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import androidx.appcompat.app.AppCompatActivity;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MainActivity extends AppCompatActivity {
    private MqttClient mqttClient;
    private EditText editText;
    private Button btnPublicar, btnAbrir;
    private final String broker = "tcp://test.mosquitto.org:1883";
    private final String topicPub = "/casa/luz";
    private final String topicSub = "/casa/cmd";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        editText = findViewById(R.id.editTextMqtt);
        btnPublicar = findViewById(R.id.btnPublicar);
        btnAbrir = findViewById(R.id.btnAbrir);
        setupMqtt();
        btnPublicar.setOnClickListener(v -> publishMessage());
        btnAbrir.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, SecondActivity.class);
            startActivity(intent);
        });
    }

    private void setupMqtt() {
        try {
            mqttClient = new MqttClient(broker, MqttClient.generateClientId(), new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            mqttClient.connect(options);
            mqttClient.subscribe(topicSub);
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
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }

    private void publishMessage() {
        try {
            String msg = "Mensaje desde Android";
            mqttClient.publish(topicPub, new MqttMessage(msg.getBytes()));
        } catch (MqttException e) {
            e.printStackTrace();
        }
    }
}