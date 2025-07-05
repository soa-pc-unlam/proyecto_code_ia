package com.example.appcursormqtt;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

public class MainActivity extends AppCompatActivity {

    private Button btnPublish;
    private Button btnOpenShake;
    private EditText messageTextBox;
    
    private MqttClient mqttClient;
    private static final String BROKER_URL = "tcp://test.mosquitto.org:1883";
    private static final String CLIENT_ID = "AndroidApp_" + System.currentTimeMillis();
    private static final String PUBLISH_TOPIC = "/casa/luz";
    private static final String SUBSCRIBE_TOPIC = "/casa/cmd";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Inicializar vistas
        btnPublish = findViewById(R.id.btnPublish);
        btnOpenShake = findViewById(R.id.btnOpenShake);
        messageTextBox = findViewById(R.id.messageTextBox);
        
        // Configurar listeners
        btnPublish.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                publishMessage();
            }
        });
        
        btnOpenShake.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openShakeActivity();
            }
        });
        
        // Conectar a MQTT
        connectToMqtt();
    }
    
    private void connectToMqtt() {
        try {
            mqttClient = new MqttClient(BROKER_URL, CLIENT_ID, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(30);
            options.setKeepAliveInterval(60);
            
            mqttClient.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Conexión MQTT perdida", Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) throws Exception {
                    final String receivedMessage = new String(message.getPayload());
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Agregar el mensaje recibido al textbox
                            String currentText = messageTextBox.getText().toString();
                            String newText = currentText + "\n" + receivedMessage;
                            messageTextBox.setText(newText);
                            
                            // Hacer scroll al final
                            messageTextBox.setSelection(messageTextBox.getText().length());
                        }
                    });
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                    // No necesitamos hacer nada aquí
                }
            });
            
            mqttClient.connect(options);
            
            // Suscribirse al tópico
            mqttClient.subscribe(SUBSCRIBE_TOPIC, 0);
            
            Toast.makeText(this, "Conectado a MQTT", Toast.LENGTH_SHORT).show();
            
        } catch (MqttException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error al conectar MQTT: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
    
    private void publishMessage() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                String message = "Mensaje desde Android - " + System.currentTimeMillis();
                MqttMessage mqttMessage = new MqttMessage(message.getBytes());
                mqttClient.publish(PUBLISH_TOPIC, mqttMessage);
                Toast.makeText(this, "Mensaje publicado: " + message, Toast.LENGTH_SHORT).show();
            } catch (MqttException e) {
                e.printStackTrace();
                Toast.makeText(this, "Error al publicar: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(this, "No conectado a MQTT", Toast.LENGTH_SHORT).show();
        }
    }
    
    private void openShakeActivity() {
        Intent intent = new Intent(this, ShakeActivity.class);
        startActivity(intent);
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