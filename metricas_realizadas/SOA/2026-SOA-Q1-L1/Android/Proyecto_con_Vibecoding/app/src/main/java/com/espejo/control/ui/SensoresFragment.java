package com.espejo.control.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.espejo.control.R;
import com.espejo.control.mqtt.TelemetryBus;
import com.espejo.control.mqtt.TelemetryListener;

public class SensoresFragment extends Fragment implements TelemetryListener {

    private TextView textDistancia1;
    private TextView textDistancia2;
    private TextView textLuz;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_sensores, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        textDistancia1 = view.findViewById(R.id.text_distancia1);
        textDistancia2 = view.findViewById(R.id.text_distancia2);
        textLuz = view.findViewById(R.id.text_luz);

        TelemetryBus.getInstance().addListener(this);
    }

    @Override
    public void onDestroyView() {
        TelemetryBus.getInstance().removeListener(this);
        super.onDestroyView();
    }

    @Override
    public void onDistancia1(String valor) {
        textDistancia1.setText(getString(R.string.sensor_value_cm, valor));
    }

    @Override
    public void onDistancia2(String valor) {
        textDistancia2.setText(getString(R.string.sensor_value_cm, valor));
    }

    @Override
    public void onLuz(String valor) {
        textLuz.setText(getString(R.string.sensor_value_luz, valor));
    }
}
