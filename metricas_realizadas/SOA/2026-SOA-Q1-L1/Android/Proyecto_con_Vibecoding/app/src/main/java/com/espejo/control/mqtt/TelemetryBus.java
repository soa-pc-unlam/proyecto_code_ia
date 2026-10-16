package com.espejo.control.mqtt;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Hub central de telemetria en memoria. Las pantallas y el servicio de alerta
 * se suscriben aca en vez de leer directamente del cliente MQTT o del
 * generador simulado. Todas las notificaciones se entregan en el hilo
 * principal para que los listeners puedan actualizar UI sin preocuparse por
 * threading.
 */
public class TelemetryBus {

    private static final TelemetryBus INSTANCE = new TelemetryBus();

    private final CopyOnWriteArrayList<TelemetryListener> listeners = new CopyOnWriteArrayList<>();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private TelemetryBus() {
    }

    public static TelemetryBus getInstance() {
        return INSTANCE;
    }

    public void addListener(TelemetryListener listener) {
        listeners.add(listener);
    }

    public void removeListener(TelemetryListener listener) {
        listeners.remove(listener);
    }

    public void publishDistancia1(String valor) {
        mainHandler.post(() -> {
            for (TelemetryListener l : listeners) {
                l.onDistancia1(valor);
            }
        });
    }

    public void publishDistancia2(String valor) {
        mainHandler.post(() -> {
            for (TelemetryListener l : listeners) {
                l.onDistancia2(valor);
            }
        });
    }

    public void publishLuz(String valor) {
        mainHandler.post(() -> {
            for (TelemetryListener l : listeners) {
                l.onLuz(valor);
            }
        });
    }

    public void publishConnectionLost() {
        mainHandler.post(() -> {
            for (TelemetryListener l : listeners) {
                l.onConnectionLost();
            }
        });
    }
}
