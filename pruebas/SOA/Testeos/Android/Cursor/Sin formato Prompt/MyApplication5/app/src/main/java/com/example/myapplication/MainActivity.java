package com.example.myapplication;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private MqttAsyncClient mqttClient;
    private Button btnPublicarLuz;
    private Button btnShakeDetector;
    private TextView tvStatus;
    private TextView tvMensajesRecibidos;
    private ExecutorService executorService;
    
    // Configuración MQTT
    private static final String BROKER_URL = "tcp://test.mosquitto.org:1883";
    private static final String CLIENT_ID = "AndroidClient_" + System.currentTimeMillis();
    private static final String TOPIC_PUBLICAR = "/casa/luz";
    private static final String TOPIC_SUSCRIBIR = "/casa/boton";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        
        // Configurar vistas
        btnPublicarLuz = findViewById(R.id.btnPublicarLuz);
        btnShakeDetector = findViewById(R.id.btnShakeDetector);
        tvStatus = findViewById(R.id.tvStatus);
        tvMensajesRecibidos = findViewById(R.id.tvMensajesRecibidos);
        
        // Inicializar executor para operaciones en background
        executorService = Executors.newSingleThreadExecutor();
        
        // Verificar conectividad antes de configurar MQTT
        if (isNetworkAvailable()) {
            setupMqtt();
        } else {
            tvStatus.setText("Estado: Sin conexión a Internet");
            tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
            btnPublicarLuz.setEnabled(false);
            Toast.makeText(this, "No hay conexión a Internet", Toast.LENGTH_LONG).show();
        }
        
        // Configurar click listener del botón MQTT
        btnPublicarLuz.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                publicarMensaje();
            }
        });
        
        // Configurar click listener del botón Shake Detector
        btnShakeDetector.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Navegar a la pantalla de shake detector
                Intent intent = new Intent(MainActivity.this, ShakeDetectorActivity.class);
                startActivity(intent);
            }
        });
        
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    private void setupMqtt() {
        Log.d(TAG, "Iniciando configuración MQTT...");
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.d(TAG, "Creando cliente MQTT con URL: " + BROKER_URL);
                    // Usar persistencia en memoria en lugar de archivos
                    MemoryPersistence persistence = new MemoryPersistence();
                    mqttClient = new MqttAsyncClient(BROKER_URL, CLIENT_ID, persistence);
                    
                    // Configurar callback para mensajes recibidos
                    mqttClient.setCallback(new MqttCallback() {
                        @Override
                        public void connectionLost(Throwable cause) {
                            Log.w(TAG, "Conexión MQTT perdida", cause);
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    tvStatus.setText("Estado: Conexión perdida");
                                    tvStatus.setTextColor(getResources().getColor(android.R.color.holo_orange_dark));
                                    btnPublicarLuz.setEnabled(false);
                                }
                            });
                        }

                        @Override
                        public void messageArrived(String topic, MqttMessage message) throws Exception {
                            Log.d(TAG, "Mensaje recibido en tópico: " + topic + " - Contenido: " + new String(message.getPayload()));
                            final String mensajeRecibido = new String(message.getPayload());
                            final String topicRecibido = topic;
                            
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    // Actualizar el TextView con el mensaje recibido
                                    String mensajeActual = tvMensajesRecibidos.getText().toString();
                                    String nuevoMensaje = "Tópico: " + topicRecibido + "\nMensaje: " + mensajeRecibido + "\n\n" + mensajeActual;
                                    
                                    // Limitar el número de mensajes mostrados (últimos 10)
                                    String[] mensajes = nuevoMensaje.split("\n\n");
                                    if (mensajes.length > 20) { // 10 mensajes * 2 líneas cada uno
                                        StringBuilder sb = new StringBuilder();
                                        for (int i = 0; i < 20; i++) {
                                            sb.append(mensajes[i]).append("\n\n");
                                        }
                                        nuevoMensaje = sb.toString();
                                    }
                                    
                                    tvMensajesRecibidos.setText(nuevoMensaje);
                                    
                                    // Mostrar Toast de confirmación
                                    Toast.makeText(MainActivity.this, "Mensaje recibido: " + mensajeRecibido, Toast.LENGTH_SHORT).show();
                                }
                            });
                        }

                        @Override
                        public void deliveryComplete(IMqttDeliveryToken token) {
                            Log.d(TAG, "Entrega de mensaje completada");
                        }
                    });
                    
                    MqttConnectOptions connectOptions = new MqttConnectOptions();
                    connectOptions.setAutomaticReconnect(true);
                    connectOptions.setCleanSession(true);
                    connectOptions.setConnectionTimeout(30);
                    connectOptions.setKeepAliveInterval(60);
                    connectOptions.setMaxInflight(1000);
                    
                    Log.d(TAG, "Intentando conectar al broker...");
                    mqttClient.connect(connectOptions, null, new IMqttActionListener() {
                        @Override
                        public void onSuccess(IMqttToken asyncActionToken) {
                            Log.d(TAG, "Conexión MQTT exitosa");
                            // Suscribirse al tópico después de conectar exitosamente
                            suscribirseAlTopico();
                            
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    tvStatus.setText("Estado: Conectado");
                                    tvStatus.setTextColor(getResources().getColor(android.R.color.holo_green_dark));
                                    btnPublicarLuz.setEnabled(true);
                                    Toast.makeText(MainActivity.this, "Conectado al broker MQTT", Toast.LENGTH_SHORT).show();
                                }
                            });
                        }

                        @Override
                        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                            Log.e(TAG, "Error en conexión MQTT", exception);
                            String errorMsg = "Error de conexión";
                            if (exception instanceof MqttException) {
                                MqttException mqttEx = (MqttException) exception;
                                errorMsg += " (Código: " + mqttEx.getReasonCode() + ")";
                                Log.e(TAG, "Código de error MQTT: " + mqttEx.getReasonCode());
                            }
                            errorMsg += ": " + exception.getMessage();
                            
                            final String finalErrorMsg = errorMsg;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    tvStatus.setText("Estado: Error de conexión");
                                    tvStatus.setTextColor(getResources().getColor(android.R.color.holo_red_dark));
                                    btnPublicarLuz.setEnabled(false);
                                    Toast.makeText(MainActivity.this, finalErrorMsg, Toast.LENGTH_LONG).show();
                                }
                            });
                        }
                    });
                } catch (MqttException e) {
                    Log.e(TAG, "Error al crear cliente MQTT", e);
                    final String errorMsg = "Error al configurar MQTT (Código: " + e.getReasonCode() + "): " + e.getMessage();
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                        }
                    });
                } catch (Exception e) {
                    Log.e(TAG, "Error inesperado en setupMqtt", e);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Error inesperado: " + e.getMessage(), Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        });
    }

    private void suscribirseAlTopico() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                Log.d(TAG, "Suscribiéndose al tópico: " + TOPIC_SUSCRIBIR);
                mqttClient.subscribe(TOPIC_SUSCRIBIR, 1, null, new IMqttActionListener() {
                    @Override
                    public void onSuccess(IMqttToken asyncActionToken) {
                        Log.d(TAG, "Suscripción exitosa al tópico: " + TOPIC_SUSCRIBIR);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Suscrito al tópico: " + TOPIC_SUSCRIBIR, Toast.LENGTH_SHORT).show();
                            }
                        });
                    }

                    @Override
                    public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                        Log.e(TAG, "Error al suscribirse al tópico: " + TOPIC_SUSCRIBIR, exception);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Error al suscribirse: " + exception.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                });
            } catch (MqttException e) {
                Log.e(TAG, "Error MQTT al suscribirse", e);
            }
        }
    }

    private void publicarMensaje() {
        if (mqttClient != null && mqttClient.isConnected()) {
            executorService.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        String mensaje = "ON"; // Mensaje a publicar
                        MqttMessage mqttMessage = new MqttMessage(mensaje.getBytes());
                        mqttMessage.setQos(1);
                        
                        Log.d(TAG, "Publicando mensaje en tópico: " + TOPIC_PUBLICAR);
                        mqttClient.publish(TOPIC_PUBLICAR, mqttMessage, null, new IMqttActionListener() {
                            @Override
                            public void onSuccess(IMqttToken asyncActionToken) {
                                Log.d(TAG, "Mensaje publicado exitosamente");
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this, "Mensaje publicado exitosamente en " + TOPIC_PUBLICAR, Toast.LENGTH_SHORT).show();
                                    }
                                });
                            }

                            @Override
                            public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                                Log.e(TAG, "Error al publicar mensaje", exception);
                                String errorMsg = "Error al publicar";
                                if (exception instanceof MqttException) {
                                    MqttException mqttEx = (MqttException) exception;
                                    errorMsg += " (Código: " + mqttEx.getReasonCode() + ")";
                                }
                                errorMsg += ": " + exception.getMessage();
                                
                                final String finalErrorMsg = errorMsg;
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this, finalErrorMsg, Toast.LENGTH_LONG).show();
                                    }
                                });
                            }
                        });
                    } catch (MqttException e) {
                        Log.e(TAG, "Error MQTT al publicar", e);
                        final String errorMsg = "Error MQTT (Código: " + e.getReasonCode() + "): " + e.getMessage();
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                            }
                        });
                    } catch (Exception e) {
                        Log.e(TAG, "Error inesperado al publicar", e);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(MainActivity.this, "Error inesperado: " + e.getMessage(), Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            });
        } else {
            Log.w(TAG, "Cliente MQTT no conectado");
            Toast.makeText(this, "No conectado al broker MQTT", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                Log.d(TAG, "Cliente MQTT desconectado");
            } catch (MqttException e) {
                Log.e(TAG, "Error al desconectar MQTT", e);
            }
        }
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}