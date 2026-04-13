package com.example.appworkikmqtt;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.widget.RelativeLayout;

public class SecondActivity extends AppCompatActivity implements SensorEventListener {

    private RelativeLayout layout;
    private SensorManager sensorManager;
    private Sensor accelerometer;

    private static final int SHAKE_THRESHOLD_GRAVITY = 2; // Ajustado para detectar agitación
    private float lastX, lastY, lastZ;
    private long lastShakeTime = 0;

    private int colorIndex = 0;
    private final int[] colors = {
            Color.RED, Color.BLACK, Color.BLUE, Color.GREEN, Color.YELLOW
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.second_activity_layout);

        layout = findViewById(R.id.second_activity_layout);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if(sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if(accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            long now = System.currentTimeMillis();

            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            float gX = x / SensorManager.GRAVITY_EARTH;
            float gY = y / SensorManager.GRAVITY_EARTH;
            float gZ = z / SensorManager.GRAVITY_EARTH;

            // Fuerza total en escala G
            float gForce = (float) Math.sqrt(gX * gX + gY * gY + gZ * gZ);

            if (gForce > SHAKE_THRESHOLD_GRAVITY) {
                // Evitar múltiples shakes en muy poco tiempo
                if (now - lastShakeTime > 500) {
                    lastShakeTime = now;
                    changeBackgroundColor();
                }
            }
        }
    }

    private void changeBackgroundColor() {
        colorIndex = (colorIndex + 1) % colors.length;
        layout.setBackgroundColor(colors[colorIndex]);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No requerido
    }
}