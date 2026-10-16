package com.example.espejo;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

public class ControlFragment extends Fragment implements SensorEventListener {

    private Switch switchModo;
    private SeekBar seekServo;
    private SeekBar seekLuz;
    private Button btnEnviar;
    private TextView progressPos;
    private TextView progressLuz;
    private SensorManager sensorManager;
    private Sensor acelerometro;
    private Switch switchShake;
    private boolean ledEncendido = false;

    private int CENTRO_SEEK_CERVO = 90;
    private long ultimoShake = 0;
    private static final float UMBRAL_SHAKE = 12f;
    private static final long DELAY_SHAKE = 1000;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_control, container, false);

        switchModo = view.findViewById(R.id.switchModo);
        seekServo = view.findViewById(R.id.seekServo);
        seekLuz = view.findViewById(R.id.seekLuz);
        btnEnviar = view.findViewById(R.id.btnEnviar);
        progressPos = view.findViewById(R.id.progressPos);
        progressLuz = view.findViewById(R.id.progressLuz);
        switchShake = view.findViewById(R.id.switchShake);

        seekServo.setProgress(CENTRO_SEEK_CERVO);
        btnEnviar.setEnabled(false);

        sensorManager = (SensorManager) requireContext().getSystemService(getContext().SENSOR_SERVICE);
        acelerometro = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        switchShake.setOnCheckedChangeListener((buttonView, isChecked) -> {

            if (isChecked) {
                if(switchModo.isChecked()) {
                    sensorManager.registerListener(this, acelerometro, SensorManager.SENSOR_DELAY_NORMAL);
                } else {
                    switchShake.setChecked(false);
                    Toast.makeText(getContext(), "Activar modo manual primero", Toast.LENGTH_SHORT).show();
                }

            } else {
                sensorManager.unregisterListener(this);
            }

        });

        switchModo.setOnCheckedChangeListener((buttonView, isChecked) -> {
            String modo = isChecked ? "manual" : "automatico";
            MqttHandler.getInstance(getContext()).publish("espejo/control/modo", modo);

            btnEnviar.setEnabled(isChecked);

            if(!isChecked){
                switchShake.setChecked(false);
            }

        });

        btnEnviar.setOnClickListener(v -> {
                String valorServo = String.valueOf(seekServo.getProgress());
                String valorLuz = String.valueOf(seekLuz.getProgress());

                MqttHandler.getInstance(getContext()).publish("espejo/control/servo", valorServo);
                MqttHandler.getInstance(getContext()).publish("espejo/control/luz", valorLuz);
        });

        seekServo.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int valorReal = progress - CENTRO_SEEK_CERVO;

                if (valorReal < 0) {
                    progressPos.setText(valorReal + "° Izquierda");
                } else if (valorReal > 0){
                    progressPos.setText(valorReal + "° Derecha");
                } else {
                    progressPos.setText("centro");
                }

            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {

            }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        seekLuz.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                progressLuz.setText(String.valueOf(progress));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override public void onStopTrackingTouch(SeekBar seekBar) {

            }
        });

        return view;
    }


    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        double fuerza = Math.sqrt(x * x + y * y + z * z);

        if (fuerza > UMBRAL_SHAKE){
            long ahora = System.currentTimeMillis();
            if(ahora - ultimoShake > DELAY_SHAKE) {
                ultimoShake = ahora;
                ledEncendido = !ledEncendido;
                String mensaaje = ledEncendido ? "1" : "0";
                MqttHandler.getInstance(getContext()).publish("espejo/control/led", mensaaje);
            }
        }

    }

    @Override
    public void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        switchShake.setChecked(false);
    }
}
