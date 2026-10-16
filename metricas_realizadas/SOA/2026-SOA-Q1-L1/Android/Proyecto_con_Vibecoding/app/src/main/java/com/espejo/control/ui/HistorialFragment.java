package com.espejo.control.ui;

import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.espejo.control.R;
import com.espejo.control.mqtt.TelemetryBus;
import com.espejo.control.mqtt.TelemetryListener;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;

import java.util.ArrayList;

public class HistorialFragment extends Fragment implements TelemetryListener {

    private static final int MAX_POINTS = 300;

    private ChartSeries distancia1Series;
    private ChartSeries distancia2Series;
    private ChartSeries luzSeries;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                              @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_historial, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        distancia1Series = new ChartSeries(view.findViewById(R.id.chart_distancia1), Color.rgb(46, 92, 110));
        distancia2Series = new ChartSeries(view.findViewById(R.id.chart_distancia2), Color.rgb(242, 169, 59));
        luzSeries = new ChartSeries(view.findViewById(R.id.chart_luz), Color.rgb(192, 57, 43));

        TelemetryBus.getInstance().addListener(this);
    }

    @Override
    public void onDestroyView() {
        TelemetryBus.getInstance().removeListener(this);
        super.onDestroyView();
    }

    @Override
    public void onDistancia1(String valor) {
        distancia1Series.addPoint(parse(valor));
    }

    @Override
    public void onDistancia2(String valor) {
        distancia2Series.addPoint(parse(valor));
    }

    @Override
    public void onLuz(String valor) {
        luzSeries.addPoint(parse(valor));
    }

    @Nullable
    private Float parse(String valor) {
        try {
            return Float.parseFloat(valor);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Envuelve un LineChart con su dataset y un contador de X creciente,
     * limitando la cantidad de puntos retenidos para no crecer sin limite
     * en sesiones largas.
     */
    private static class ChartSeries {

        private final LineChart chart;
        private final LineDataSet dataSet;
        private float nextX = 0f;

        ChartSeries(LineChart chart, int color) {
            this.chart = chart;
            this.dataSet = new LineDataSet(new ArrayList<Entry>(), "");
            dataSet.setColor(color);
            dataSet.setCircleColor(color);
            dataSet.setDrawValues(false);
            dataSet.setDrawCircles(false);
            dataSet.setLineWidth(2f);

            Description description = new Description();
            description.setText("");
            chart.setDescription(description);
            chart.getLegend().setEnabled(false);
            chart.setData(new LineData(dataSet));
        }

        void addPoint(@Nullable Float value) {
            if (value == null) {
                return;
            }
            LineData data = chart.getData();
            data.addEntry(new Entry(nextX, value), 0);
            nextX += 1f;

            if (dataSet.getEntryCount() > MAX_POINTS) {
                dataSet.removeFirst();
            }

            data.notifyDataChanged();
            chart.notifyDataSetChanged();
            chart.invalidate();
        }
    }
}
