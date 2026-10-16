package com.espejo.control.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

/**
 * Unica clase que conoce los detalles de la comunicacion MQTT (o del modo
 * simulado). El resto de la app solo llama a connect/subscribeToSensors/
 * publish/disconnect y escucha el TelemetryBus.
 */
public class MqttManager {

    private static final MqttManager INSTANCE = new MqttManager();

    private final TelemetryBus bus = TelemetryBus.getInstance();
    private final SimulatedDataGenerator simulator = new SimulatedDataGenerator();

    private MqttClient client;
    private boolean simulatedMode = false;

    private MqttManager() {
    }

    public static MqttManager getInstance() {
        return INSTANCE;
    }

    public void setSimulatedMode(boolean simulatedMode) {
        this.simulatedMode = simulatedMode;
    }

    public boolean isSimulatedMode() {
        return simulatedMode;
    }

    /**
     * Bloqueante: debe llamarse desde un hilo de background. Devuelve true
     * si la conexion (o el modo simulado) esta lista.
     */
    public boolean connect(String host, int port) {
        if (simulatedMode) {
            return true;
        }
        try {
            String uri = "tcp://" + host + ":" + port;
            client = new MqttClient(uri, MqttClient.generateClientId(), new MemoryPersistence());
            client.setCallback(new MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    bus.publishConnectionLost();
                }

                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    String payload = new String(message.getPayload());
                    routeMessage(topic, payload);
                }

                @Override
                public void deliveryComplete(IMqttDeliveryToken token) {
                }
            });

            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setAutomaticReconnect(false);
            client.connect(options);
            return true;
        } catch (MqttException e) {
            client = null;
            return false;
        }
    }

    /**
     * Bloqueante: debe llamarse desde un hilo de background. En modo
     * simulado, arranca el generador de datos ficticios.
     */
    public void subscribeToSensors() {
        if (simulatedMode) {
            simulator.start();
            return;
        }
        if (client == null) {
            return;
        }
        try {
            client.subscribe(MqttTopics.SUB_DISTANCIA1, 0);
            client.subscribe(MqttTopics.SUB_DISTANCIA2, 0);
            client.subscribe(MqttTopics.SUB_LUZ, 0);
        } catch (MqttException e) {
            bus.publishConnectionLost();
        }
    }

    /**
     * No bloqueante: publica en un hilo propio para no afectar al llamador,
     * sin importar desde donde se invoque.
     */
    public void publish(String topic, String payload) {
        if (simulatedMode) {
            return;
        }
        new Thread(() -> {
            try {
                if (client != null && client.isConnected()) {
                    client.publish(topic, payload.getBytes(), 0, false);
                }
            } catch (MqttException ignored) {
            }
        }).start();
    }

    /**
     * Bloqueante: debe llamarse desde un hilo de background.
     */
    public void disconnect() {
        if (simulatedMode) {
            simulator.stop();
            return;
        }
        try {
            if (client != null) {
                if (client.isConnected()) {
                    client.disconnect();
                }
                client.close();
            }
        } catch (MqttException ignored) {
        } finally {
            client = null;
        }
    }

    private void routeMessage(String topic, String payload) {
        if (MqttTopics.SUB_DISTANCIA1.equals(topic)) {
            bus.publishDistancia1(payload);
        } else if (MqttTopics.SUB_DISTANCIA2.equals(topic)) {
            bus.publishDistancia2(payload);
        } else if (MqttTopics.SUB_LUZ.equals(topic)) {
            bus.publishLuz(payload);
        }
    }
}
