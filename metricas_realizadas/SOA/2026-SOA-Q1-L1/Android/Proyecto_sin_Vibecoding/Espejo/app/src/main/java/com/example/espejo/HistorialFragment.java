package com.example.espejo;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;

public class HistorialFragment extends Fragment {

    private ReceptorOperacion receiver = new ReceptorOperacion();
    public IntentFilter filterReceive;

    private LineChart chartDistancia1;
    private LineChart chartDistancia2;
    private LineChart chartLuz;



    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_historial, container, false);

        chartDistancia1 = view.findViewById(R.id.chartDistancia1);
        chartDistancia2 = view.findViewById(R.id.chartDistancia2);
        chartLuz = view.findViewById(R.id.chartLuz);

        if (!HistorialDatos.getDistancia1().isEmpty()) {
            chartDistancia1.setData(new LineData(new LineDataSet(HistorialDatos.getDistancia1(), "")));
            chartDistancia1.invalidate();
        }
        if (!HistorialDatos.getDistancia2().isEmpty()) {
            chartDistancia2.setData(new LineData(new LineDataSet(HistorialDatos.getDistancia2(), "")));
            chartDistancia2.invalidate();
        }
        if (!HistorialDatos.getLuz().isEmpty()) {
            chartLuz.setData(new LineData(new LineDataSet(HistorialDatos.getLuz(), "")));
            chartLuz.invalidate();
        }

        return view;
    }

    private void agregarDato(LineChart chart, ArrayList<Entry> datos, String valor) {
        try {
            float floatValor = Float.parseFloat(valor);
            datos.add(new Entry(datos.size(), floatValor));

            LineDataSet dataSet = new LineDataSet(datos, "");
            LineData lineData = new LineData(dataSet);
            chart.setData(lineData);
            chart.invalidate();
        } catch ( NumberFormatException e){
            Log.d("MQTT", "Valor no numérico recibido: " + valor);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        configurarBroadcastReceiver();
    }

    @Override
    public void onPause() {
        super.onPause();
        requireContext().unregisterReceiver(receiver);
    }


    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void configurarBroadcastReceiver()
    {
        filterReceive = new IntentFilter(MqttHandler.ACTION_DATA_RECEIVE);
        filterReceive.addCategory(Intent.CATEGORY_DEFAULT);
        requireContext().registerReceiver(receiver, filterReceive);

    }

    public class ReceptorOperacion extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            String topic = intent.getStringExtra("topic");
            String payload = intent.getStringExtra("payload");

            if (topic.equals("espejo/sensor/distancia1")) {
                agregarDato(chartDistancia1, HistorialDatos.getDistancia1(), payload);
            } else if (topic.equals("espejo/sensor/distancia2")) {
                agregarDato(chartDistancia2, HistorialDatos.getDistancia2(), payload);
            } else if (topic.equals("espejo/sensor/luz")) {
                agregarDato(chartLuz, HistorialDatos.getLuz(), payload);
            }


        }

    }

}
