package com.example.myapplication;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

public class ShakeDetectorActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private ConstraintLayout shakeLayout;
    private TextView tvShakeCount;
    private Button btnBackToMain;
    
    private int shakeCount = 0;
    private int currentColorIndex = 0;
    private long lastShakeTime = 0;
    private static final long SHAKE_THRESHOLD = 1000; // 1 segundo entre shakes
    private static final float SHAKE_SENSITIVITY = 12.0f; // Sensibilidad del shake
    
    // Colores para cambiar (en orden secuencial)
    private int[] colors = {
        0xFF5722, // Naranja (color inicial)
        0xFF4081, // Rosa
        0x3F51B5, // Azul
        0x4CAF50, // Verde
        0xFF9800, // Naranja claro
        0x9C27B0, // Púrpura
        0x00BCD4, // Cian
        0xFFEB3B, // Amarillo
        0x795548, // Marrón
        0x607D8B, // Azul gris
        0xE91E63, // Rosa oscuro
        0x2196F3, // Azul claro
        0x8BC34A, // Verde claro
        0xFFC107, // Ámbar
        0x9E9E9E  // Gris
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_shake_detector);
        
        // Inicializar vistas
        shakeLayout = findViewById(R.id.shakeLayout);
        tvShakeCount = findViewById(R.id.tvShakeCount);
        btnBackToMain = findViewById(R.id.btnBackToMain);
        
        // Configurar sensor de acelerómetro
        setupAccelerometer();
        
        // Configurar botón de regreso
        btnBackToMain.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish(); // Cerrar esta actividad y volver a la anterior
            }
        });
    }

    private void setupAccelerometer() {
        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            if (accelerometer != null) {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            } else {
                Toast.makeText(this, "Acelerómetro no disponible en este dispositivo", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            
            // Calcular la aceleración total
            float acceleration = (float) Math.sqrt(x * x + y * y + z * z);
            
            // Detectar shake
            if (acceleration > SHAKE_SENSITIVITY) {
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastShakeTime > SHAKE_THRESHOLD) {
                    // Es un shake válido
                    shakeDetected();
                    lastShakeTime = currentTime;
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No necesitamos manejar cambios de precisión
    }

    private void shakeDetected() {
        shakeCount++;
        
        // Cambiar al siguiente color de manera secuencial
        currentColorIndex = (currentColorIndex + 1) % colors.length;
        int newColor = colors[currentColorIndex];
        shakeLayout.setBackgroundColor(newColor);
        
        // Actualizar contador
        tvShakeCount.setText("Shakes detectados: " + shakeCount);
        
        // Mostrar feedback con el nombre del color
        String colorName = getColorName(newColor);
        Toast.makeText(this, "¡Shake detectado! Color: " + colorName, Toast.LENGTH_SHORT).show();
    }
    
    private String getColorName(int color) {
        switch (color) {
            case 0xFF5722: return "Naranja";
            case 0xFF4081: return "Rosa";
            case 0x3F51B5: return "Azul";
            case 0x4CAF50: return "Verde";
            case 0xFF9800: return "Naranja Claro";
            case 0x9C27B0: return "Púrpura";
            case 0x00BCD4: return "Cian";
            case 0xFFEB3B: return "Amarillo";
            case 0x795548: return "Marrón";
            case 0x607D8B: return "Azul Gris";
            case 0xE91E63: return "Rosa Oscuro";
            case 0x2196F3: return "Azul Claro";
            case 0x8BC34A: return "Verde Claro";
            case 0xFFC107: return "Ámbar";
            case 0x9E9E9E: return "Gris";
            default: return "Desconocido";
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
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
    protected void onDestroy() {
        super.onDestroy();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }
} 