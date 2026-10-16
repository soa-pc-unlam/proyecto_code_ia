package com.example.vibenofono;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class LocalizarActivity extends AppCompatActivity implements SensorEventListener {
    private static final float SHAKE_THRESHOLD = 15.0f;
    private static final int SHAKE_COOLDOWN_MS = 6000;

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastShakeTimestamp;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_localizar);

        statusText = findViewById(R.id.text_localizar_status);
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
            statusText.setText(R.string.localizar_esperando_agitar);
        } else {
            statusText.setText(R.string.localizar_sin_sensor);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
        if (magnitude > SHAKE_THRESHOLD) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTimestamp > SHAKE_COOLDOWN_MS) {
                lastShakeTimestamp = now;
                statusText.setText(R.string.localizar_agitado);
                NonofonoService.publishFindMessage(this);
                Toast.makeText(this, R.string.localizar_enviando_evento, Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No-op
    }
}
