package com.example.appcursormqtt;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

public class ShakeActivity extends AppCompatActivity implements SensorEventListener {

    private ConstraintLayout shakeLayout;
    private Button btnBack;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    
    // Variables para detectar shake
    private long lastUpdate = 0;
    private float last_x, last_y, last_z;
    private static final int SHAKE_THRESHOLD = 600;
    
    // Colores para el fondo
    private int[] colors = {
        R.color.red,
        R.color.black,
        R.color.blue,
        R.color.green,
        R.color.yellow
    };
    private int currentColorIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shake);
        
        // Inicializar vistas
        shakeLayout = findViewById(R.id.shakeLayout);
        btnBack = findViewById(R.id.btnBack);
        
        // Configurar sensor de acelerómetro
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        
        // Configurar listener del botón
        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        
        Toast.makeText(this, "Agita el dispositivo para cambiar el color", Toast.LENGTH_LONG).show();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        // Registrar el listener del sensor
        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        // Desregistrar el listener del sensor
        sensorManager.unregisterListener(this);
    }
    
    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            long curTime = System.currentTimeMillis();
            
            if ((curTime - lastUpdate) > 100) {
                long diffTime = (curTime - lastUpdate);
                lastUpdate = curTime;
                
                float x = event.values[0];
                float y = event.values[1];
                float z = event.values[2];
                
                float speed = Math.abs(x + y + z - last_x - last_y - last_z) / diffTime * 10000;
                
                if (speed > SHAKE_THRESHOLD) {
                    // Shake detectado - cambiar color
                    changeBackgroundColor();
                }
                
                last_x = x;
                last_y = y;
                last_z = z;
            }
        }
    }
    
    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No necesitamos hacer nada aquí
    }
    
    private void changeBackgroundColor() {
        // Cambiar al siguiente color
        currentColorIndex = (currentColorIndex + 1) % colors.length;
        
        // Aplicar el nuevo color al fondo
        shakeLayout.setBackgroundResource(colors[currentColorIndex]);
        
        // Mostrar toast con el color actual
        String[] colorNames = {"Rojo", "Negro", "Azul", "Verde", "Amarillo"};
        Toast.makeText(this, "Color cambiado a: " + colorNames[currentColorIndex], Toast.LENGTH_SHORT).show();
    }
} 