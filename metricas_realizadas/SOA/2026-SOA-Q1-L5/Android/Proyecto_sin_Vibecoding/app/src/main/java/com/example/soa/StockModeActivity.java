package com.example.soa;

import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.content.Context;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import org.json.JSONObject;


public class StockModeActivity extends AppCompatActivity {

    private TextView tvCell1Value, tvCell1Status, tvConnStock;
    private TextView tvShelfName, tvUnitWeight, tvStockAvailable, tvMinStock;
    private Button btnStop;
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private static final int POLL_INTERVAL = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stock_mode);

        tvShelfName = findViewById(R.id.tvShelfName);
        tvCell1Value = findViewById(R.id.tvCell1Value);
        tvUnitWeight = findViewById(R.id.tvUnitWeight);
        tvStockAvailable = findViewById(R.id.tvStockAvailable);
        tvMinStock = findViewById(R.id.tvMinStock);
        tvCell1Status = findViewById(R.id.tvCell1Status);

        tvConnStock = findViewById(R.id.tvConnStock);
        btnStop = findViewById(R.id.btnStopStock);

        btnStop.setOnClickListener(v -> ApiClient.getInstance().sendStock(this, false, new ApiClient.OkCallback() {
            @Override
            public void onOk(JSONObject resp) {
                triggerVibration(100);
                finish();
            }
            @Override
            public void onError(String msg) {
            }
        }));
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

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            ApiClient.getInstance().getState(StockModeActivity.this, new ApiClient.StateCallback() {
                @Override
                public void onState(JSONObject state) {
                    if (isFinishing()) return;
                    tvConnStock.setText("");
                    StateManager.updateFromBackend(state);
                    actualizarInterfaz();
                    if (!StateManager.isStockOn()) {
                        finish();
                        return;
                    }
                    pollHandler.postDelayed(pollRunnable, POLL_INTERVAL);
                }
                @Override
                public void onError(String msg) {
                    if (isFinishing()) return;
                    tvConnStock.setText(msg);
                    pollHandler.postDelayed(pollRunnable, POLL_INTERVAL);
                }
                @Override
                public void onEmpty() {
                    if (isFinishing()) return;
                    tvConnStock.setText("Esperando datos…");
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
        StateManager.ShelfData data = StateManager.getShelf01();

        tvShelfName.setText(data.name.isEmpty() ? getString(R.string.shelf_01) : data.name);

        tvCell1Value.setText(getString(R.string.weight_format, data.weight));
        tvUnitWeight.setText(getString(R.string.unit_weight_format, data.weightPerUnit));
        tvStockAvailable.setText(getString(R.string.stock_available_format, data.stock));
        tvMinStock.setText(getString(R.string.min_stock_format, data.min));

        if (data.available) {
            tvCell1Status.setText(getString(R.string.status_ok));
            tvCell1Status.setTextColor(ContextCompat.getColor(this, R.color.green));
        } else {
            tvCell1Status.setText(getString(R.string.status_low));
            tvCell1Status.setTextColor(ContextCompat.getColor(this, R.color.red));
        }
    }
}