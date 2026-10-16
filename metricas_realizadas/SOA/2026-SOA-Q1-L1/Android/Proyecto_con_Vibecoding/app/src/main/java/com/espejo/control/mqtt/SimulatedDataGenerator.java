package com.espejo.control.mqtt;

import android.os.Handler;
import android.os.Looper;

import java.util.Random;

/**
 * Genera telemetria ficticia y la publica en el TelemetryBus, exactamente
 * como si hubiera llegado por MQTT. Alterna cada 8s entre un rango "lejos"
 * y uno "cerca" para las distancias, de forma que se pueda probar la alerta
 * de tiempo de uso sin depender del azar.
 */
public class SimulatedDataGenerator {

    private static final long TICK_MS = 1000L;
    private static final long PHASE_MS = 8000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final TelemetryBus bus = TelemetryBus.getInstance();

    private boolean running = false;
    private long elapsedInPhase = 0L;
    private boolean nearPhase = false;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!running) {
                return;
            }
            elapsedInPhase += TICK_MS;
            if (elapsedInPhase >= PHASE_MS) {
                elapsedInPhase = 0L;
                nearPhase = !nearPhase;
            }

            int distancia1 = nearPhase ? randomInRange(100, 350) : randomInRange(400, 800);
            int distancia2 = nearPhase ? randomInRange(100, 350) : randomInRange(400, 800);
            int luz = randomInRange(0, 100);

            bus.publishDistancia1(String.valueOf(distancia1));
            bus.publishDistancia2(String.valueOf(distancia2));
            bus.publishLuz(String.valueOf(luz));

            handler.postDelayed(this, TICK_MS);
        }
    };

    public void start() {
        if (running) {
            return;
        }
        running = true;
        elapsedInPhase = 0L;
        nearPhase = false;
        handler.postDelayed(tick, TICK_MS);
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(tick);
    }

    private int randomInRange(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }
}
