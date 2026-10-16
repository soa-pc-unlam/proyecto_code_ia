package com.example.thermoguard;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import org.json.JSONArray;
import org.json.JSONObject;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

/** Modelo de datos para registros de telemetría. */
class TelemetryEntry {
    private final String dev, date;
    private final double temp;
    public TelemetryEntry(int id, String dev, double temp, String date) { this.dev = dev; this.temp = temp; this.date = date; }
    public String getDev() { return dev; }
    public double getTemp() { return temp; }
    public String getDate() { return date; }
}

/**
 * ACTIVIDAD: HistorialActivity
 * DESCRIPCIÓN: Consulta y lista registros históricos de telemetría desde el servidor API.
 */
public class HistorialActivity extends AppCompatActivity {

    private ProgressBar pb;
    private TextInputEditText etIp, etId, etLimit;
    private MaterialButton btn;
    private final List<TelemetryEntry> dataList = new ArrayList<>();
    private HistorialAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_historial);

        pb = findViewById(R.id.progressBar);
        etIp = findViewById(R.id.etApiIp);
        etId = findViewById(R.id.etDeviceId);
        etLimit = findViewById(R.id.etLimit);
        btn = findViewById(R.id.btnRefresh);
        
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        etId.setText(Constants.DEFAULT_DEVICE_ID);
        etIp.setText(Constants.API_BASE_IP);

        RecyclerView rv = findViewById(R.id.rvHistorial);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new HistorialAdapter(dataList);
        rv.setAdapter(adapter);

        btn.setOnClickListener(v -> fetchData());
        fetchData();
    }

    /** Realiza la petición HTTP GET asíncrona para obtener el JSON de registros. */
    private void fetchData() {
        String ip = etIp.getText().toString().trim();
        String dev = etId.getText().toString().trim();
        String limit = etLimit.getText().toString().trim();
        if (ip.isEmpty()) return;

        pb.setVisibility(View.VISIBLE);
        btn.setEnabled(false);
        new Thread(() -> {
            try {
                String urlS = "http://" + ip + ":1880" + Constants.API_HISTORIAL_PATH + "?limit=" + limit;
                if (!dev.isEmpty()) urlS += "&device_id=" + dev;
                HttpURLConnection conn = (HttpURLConnection) new URL(urlS).openConnection();
                if (conn.getResponseCode() == 200) {
                    Scanner s = new Scanner(conn.getInputStream());
                    StringBuilder res = new StringBuilder();
                    while (s.hasNextLine()) res.append(s.nextLine());
                    s.close();
                    JSONArray arr = new JSONObject(res.toString()).getJSONArray("data");
                    dataList.clear();
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.getJSONObject(i);
                        dataList.add(new TelemetryEntry(o.getInt("id"), o.getString("device_id"), o.getDouble("temperature"), o.getString("created_at")));
                    }
                    runOnUiThread(() -> { adapter.notifyDataSetChanged(); pb.setVisibility(View.GONE); btn.setEnabled(true); });
                }
            } catch (Exception e) { runOnUiThread(() -> { pb.setVisibility(View.GONE); btn.setEnabled(true); }); }
        }).start();
    }

    /** Adaptador para renderizar los ítems de telemetría en el RecyclerView. */
    private static class HistorialAdapter extends RecyclerView.Adapter<HistorialAdapter.VH> {
        private final List<TelemetryEntry> list;
        public HistorialAdapter(List<TelemetryEntry> list) { this.list = list; }
        public static class VH extends RecyclerView.ViewHolder {
            TextView t, d, id;
            public VH(View v) { super(v); t=v.findViewById(R.id.tvTemp); d=v.findViewById(R.id.tvDate); id=v.findViewById(R.id.tvDeviceId); }
        }
        @NonNull @Override public VH onCreateViewHolder(@NonNull ViewGroup p, int vt) {
            return new VH(LayoutInflater.from(p.getContext()).inflate(R.layout.item_historial, p, false));
        }
        @Override public void onBindViewHolder(@NonNull VH h, int pos) {
            TelemetryEntry e = list.get(pos);
            h.t.setText(String.format(Locale.getDefault(), "%.1f°C", e.getTemp()));
            h.d.setText(e.getDate());
            h.id.setText(e.getDev());
        }
        @Override public int getItemCount() { return list.size(); }
    }
}
