package com.espejo.control.ui;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.espejo.control.R;
import com.espejo.control.mqtt.MqttManager;
import com.espejo.control.mqtt.MqttTopics;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class ControlFragment extends Fragment implements SensorEventListener {

    private static final float SHAKE_THRESHOLD = 12f;
    private static final long SHAKE_DEBOUNCE_MS = 1000L;

    private SwitchMaterial switchManual;
    private SwitchMaterial switchShake;
    private Slider sliderPosition;
    private Slider sliderLight;
    private TextView textPositionState;
    private TextView textLightValue;
    private Button buttonSend;

    private SensorManager sensorManager;
    private Sensor accelerometer;

    private long lastShakeTimestamp = 0L;
    private boolean ledOn = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_control, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        switchManual = view.findViewById(R.id.switch_manual);
        switchShake = view.findViewById(R.id.switch_shake);
        sliderPosition = view.findViewById(R.id.slider_position);
        sliderLight = view.findViewById(R.id.slider_light);
        textPositionState = view.findViewById(R.id.text_position_state);
        textLightValue = view.findViewById(R.id.text_light_value);
        buttonSend = view.findViewById(R.id.button_send);

        Context context = requireContext();
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        textLightValue.setText(String.valueOf((int) sliderLight.getValue()));

        switchManual.setOnCheckedChangeListener((buttonView, isChecked) -> {
            buttonSend.setEnabled(isChecked);
            MqttManager.getInstance().publish(MqttTopics.PUB_MODO, isChecked ? "manual" : "automatico");
            if (!isChecked && switchShake.isChecked()) {
                switchShake.setChecked(false);
            }
        });

        sliderPosition.addOnChangeListener((slider, value, fromUser) -> updatePositionText(value));

        sliderLight.addOnChangeListener((slider, value, fromUser) ->
                textLightValue.setText(String.valueOf((int) value)));

        buttonSend.setOnClickListener(v -> {
            int position = (int) sliderPosition.getValue();
            int light = (int) sliderLight.getValue();
            MqttManager.getInstance().publish(MqttTopics.PUB_SERVO, String.valueOf(position));
            MqttManager.getInstance().publish(MqttTopics.PUB_LUZ, String.valueOf(light));
        });

        switchShake.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked && !switchManual.isChecked()) {
                switchShake.setChecked(false);
                Toast.makeText(context, R.string.control_shake_requires_manual, Toast.LENGTH_SHORT).show();
                return;
            }
            syncAccelerometerListening();
        });
    }

    private void updatePositionText(float value) {
        if (value < 90) {
            textPositionState.setText(R.string.control_position_left);
        } else if (value > 90) {
            textPositionState.setText(R.string.control_position_right);
        } else {
            textPositionState.setText(R.string.control_position_center);
        }
    }

    /**
     * El acelerometro solo debe escucharse mientras el switch Shake esta
     * activo Y la pantalla de Control esta realmente visible (no oculta por
     * el patron show/hide del bottom navigation, ni en background).
     */
    private void syncAccelerometerListening() {
        sensorManager.unregisterListener(this);
        boolean shouldListen = accelerometer != null && switchShake.isChecked() && !isHidden() && isResumed();
        if (shouldListen) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        syncAccelerometerListening();
    }

    @Override
    public void onPause() {
        sensorManager.unregisterListener(this);
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        syncAccelerometerListening();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        double magnitude = Math.sqrt(x * x + y * y + z * z);

        if (magnitude > SHAKE_THRESHOLD) {
            long now = System.currentTimeMillis();
            if (now - lastShakeTimestamp > SHAKE_DEBOUNCE_MS) {
                lastShakeTimestamp = now;
                ledOn = !ledOn;
                MqttManager.getInstance().publish(MqttTopics.PUB_LED, ledOn ? "1" : "0");
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }
}
