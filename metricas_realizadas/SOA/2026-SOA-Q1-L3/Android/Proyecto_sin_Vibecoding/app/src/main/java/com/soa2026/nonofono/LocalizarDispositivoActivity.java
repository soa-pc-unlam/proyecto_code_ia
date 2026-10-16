package com.soa2026.nonofono;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LocalizarDispositivoActivity extends AppCompatActivity
        implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private MQTTManager mqttManager;
    private final ExecutorService executor =
            Executors.newSingleThreadExecutor();

    private TextView txtEstado;
    private TextView txtAcelerometroX;
    private TextView txtAcelerometroY;
    private TextView txtAcelerometroZ;

    private static final float SHAKE_THRESHOLD = 15.0f;

    private long ultimoShake = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_localizar_dispositivo);

        txtEstado = findViewById(R.id.txtEstado);

        sensorManager =
                (SensorManager) getSystemService(SENSOR_SERVICE);

        accelerometer =
                sensorManager.getDefaultSensor(
                        Sensor.TYPE_ACCELEROMETER
                );

        txtAcelerometroX = findViewById(R.id.txtAcelerometroX);
        txtAcelerometroY = findViewById(R.id.txtAcelerometroY);
        txtAcelerometroZ = findViewById(R.id.txtAcelerometroZ);

        mqttManager = MQTTManager.getInstance(this);
    }

    @Override
    protected void onResume() {
        super.onResume();

        sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_NORMAL
        );
    }

    @Override
    protected void onPause() {
        super.onPause();

        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        txtAcelerometroX.setText(String.format(Locale.getDefault(), "X: %.2f", x));
        txtAcelerometroY.setText(String.format(Locale.getDefault(), "Y: %.2f", y));
        txtAcelerometroZ.setText(String.format(Locale.getDefault(), "Z: %.2f", z));

        float magnitud =
                (float) Math.sqrt(x*x + y*y + z*z);

        if (magnitud > SHAKE_THRESHOLD) {

            long tiempoActual =
                    System.currentTimeMillis();

            if (tiempoActual - ultimoShake > 6000) {

                ultimoShake = tiempoActual;

                txtEstado.setText(
                        R.string.localizar_dispositivo_movimiento_detectado
                );

                txtEstado.postDelayed(() -> txtEstado.setText(R.string.localizar_dispositivo_descripcion), 6000);

                enviarComandoESP32();

            }
        }
    }

    @Override
    public void onAccuracyChanged(
            Sensor sensor,
            int accuracy) {
    }

    private void enviarComandoESP32() {

        executor.execute(() -> {
            try {
                mqttManager.publicar("nonofono/encontrar", "{\"action\":\"localizar\"}");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}