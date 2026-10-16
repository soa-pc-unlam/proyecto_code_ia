package com.example.soa;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.content.Context;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.content.Intent;
import android.view.Menu;
import android.view.MenuItem;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private Button btnStock, btnSecurity;
    private ImageButton btnSettings;
    private TextView tvState, tvLastUpdate;
    private boolean isServerOffline = true;
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private static final int POLL_INTERVAL = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvState = findViewById(R.id.tvState);
        tvLastUpdate = findViewById(R.id.tvLastUpdate);
        btnStock = findViewById(R.id.btnStock);
        btnSecurity = findViewById(R.id.btnSecurity);
        btnSettings = findViewById(R.id.btnSettings);

        btnStock.setOnClickListener(v -> {
            if (StateManager.isStockOn()) {
                startActivity(new Intent(this, StockModeActivity.class));
            } else {
                sendStock(true);
            }
        });

        btnSecurity.setOnClickListener(v -> {
            if (StateManager.isSecurityOn()) {
                startActivity(new Intent(this, SecurityModeActivity.class));
            } else {
                sendSecurity(true);
            }
        });

        btnSettings.setOnClickListener(v -> startActivity(new Intent(this, SettingsActivity.class)));
    }

    private void sendStock(boolean on) {
        ApiClient.getInstance().sendStock(this, on, new ApiClient.OkCallback() {
            @Override
            public void onOk(JSONObject resp) {
                StateManager.setStockOn(on);
                triggerVibration(200);
                actualizarInterfaz();
            }
            @Override
            public void onError(String msg) {
            }
        });
    }

    private void sendSecurity(boolean on) {
        ApiClient.getInstance().sendSecurity(this, on, new ApiClient.OkCallback() {
            @Override
            public void onOk(JSONObject resp) {
                StateManager.setSecurityOn(on);
                triggerVibration(200);
                actualizarInterfaz();
            }
            @Override
            public void onError(String msg) {
            }
        });
    }

    private void triggerVibration(long duration) {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createOneShot(duration, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                v.vibrate(duration);
            }
        }
    }

    private void triggerOfflineVibration() {
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            long[] pattern = {0, 100, 100, 100, 100, 100};
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, -1));
            } else {
                v.vibrate(pattern, -1);
            }
        }
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            ApiClient.getInstance().getState(MainActivity.this, new ApiClient.StateCallback() {
                @Override
                public void onState(JSONObject state) {
                    isServerOffline = false;
                    StateManager.updateFromBackend(state);
                    actualizarInterfaz();
                    pollHandler.postDelayed(pollRunnable, POLL_INTERVAL);
                }

                @Override
                public void onError(String msg) {
                    if (!isServerOffline) {
                        isServerOffline = true;
                        triggerOfflineVibration();
                    }
                    actualizarInterfaz();
                    tvState.setText(msg);
                    tvState.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.red));
                    pollHandler.postDelayed(pollRunnable, POLL_INTERVAL);
                }

                @Override
                public void onEmpty() {
                    isServerOffline = false;
                    actualizarInterfaz();
                    tvState.setText("Esperando datos…");
                    tvState.setTextColor(ContextCompat.getColor(MainActivity.this, R.color.primary_light));
                    pollHandler.postDelayed(pollRunnable, POLL_INTERVAL);
                }
            });
        }
    };

    @Override
    protected void onResume() {
        super.onResume();
        pollHandler.post(pollRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pollHandler.removeCallbacks(pollRunnable);
    }

    private void actualizarInterfaz() {
        String availability = StateManager.getAvailability();
        long lastUpd = StateManager.getLastUpdated();

        if (lastUpd > 0) {
            String time = new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(lastUpd));
            tvLastUpdate.setText(getString(R.string.last_updated_format, time));
        }

        if (isServerOffline || availability.equals("offline")) {
            if (availability.equals("offline") && !isServerOffline) {
                isServerOffline = true;
                triggerOfflineVibration();
            }
            tvState.setText(availability.equals("offline") ? "SERVIDOR OFFLINE" : "SIN CONEXIÓN");
            tvState.setTextColor(ContextCompat.getColor(this, R.color.red));
            btnStock.setEnabled(false);
            btnSecurity.setEnabled(false);
            btnStock.setAlpha(0.25f);
            btnSecurity.setAlpha(0.25f);
            return;
        }

        btnStock.setEnabled(true);
        btnSecurity.setEnabled(true);
        btnStock.setAlpha(1.0f);
        btnSecurity.setAlpha(1.0f);

        boolean stockOn = StateManager.isStockOn();
        boolean securityOn = StateManager.isSecurityOn();
        String active = StateManager.getEstadoActual();

        switch (active) {
            case StateManager.ESTADO_SEGURIDAD:
                tvState.setText(R.string.state_security);
                tvState.setTextColor(ContextCompat.getColor(this, R.color.primary_dark));
                break;
            case StateManager.ESTADO_STOCK:
                tvState.setText(R.string.state_stock);
                tvState.setTextColor(ContextCompat.getColor(this, R.color.primary_dark));
                break;
            default:
                tvState.setText(R.string.state_off);
                tvState.setTextColor(ContextCompat.getColor(this, R.color.primary_light));
                break;
        }

        btnStock.setText(stockOn ? R.string.btn_view_stock : R.string.btn_stock);
        btnSecurity.setText(securityOn ? R.string.btn_view_security : R.string.btn_security);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add("Configuración");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        startActivity(new Intent(this, SettingsActivity.class));
        return true;
    }
}