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

import com.google.android.material.card.MaterialCardView;

import org.json.JSONObject;


public class SecurityModeActivity extends AppCompatActivity {

    private TextView tvCell1Status, tvSecDetail1, tvConnSec;
    private MaterialCardView card1;
    private Button btnStop, btnAlarmOn, btnAlarmOff;
    private boolean isServerOffline = false;
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private static final int POLL_INTERVAL = 2000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_security_mode);

        tvCell1Status = findViewById(R.id.tvSecCell1Status);
        tvSecDetail1 = findViewById(R.id.tvSecDetail1);
        card1 = findViewById(R.id.cardSec1);
        tvConnSec = findViewById(R.id.tvConnSec);
        btnStop = findViewById(R.id.btnStopSecurity);
        btnAlarmOn = findViewById(R.id.btnAlarmOn);
        btnAlarmOff = findViewById(R.id.btnAlarmOff);

        btnStop.setOnClickListener(v -> ApiClient.getInstance().sendSecurity(this, false, new ApiClient.OkCallback() {
            @Override
            public void onOk(JSONObject resp) {
                triggerVibration(200);
                finish();
            }
            @Override
            public void onError(String msg) {
            }
        }));

        btnAlarmOn.setOnClickListener(v -> sendAlarm("UNMUTE"));
        btnAlarmOff.setOnClickListener(v -> sendAlarm("MUTE"));
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

    private void sendAlarm(String val) {
        ApiClient.getInstance().sendAlarm(this, val, new ApiClient.OkCallback() {
            @Override
            public void onOk(JSONObject resp) { 
                triggerVibration(100);
            }
            @Override
            public void onError(String msg) { }
        });
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
            ApiClient.getInstance().getState(SecurityModeActivity.this, new ApiClient.StateCallback() {
                @Override
                public void onState(JSONObject state) {
                    if (isFinishing()) return;
                    isServerOffline = false;
                    tvConnSec.setText("");
                    StateManager.updateFromBackend(state);
                    actualizarInterfaz();
                    if (!StateManager.isSecurityOn()) {
                        finish();
                        return;
                    }
                    pollHandler.postDelayed(pollRunnable, POLL_INTERVAL);
                }
                @Override
                public void onError(String msg) {
                    if (isFinishing()) return;
                    if (!isServerOffline) {
                        isServerOffline = true;
                        triggerOfflineVibration();
                    }
                    tvConnSec.setText(msg);
                    actualizarInterfaz();
                    pollHandler.postDelayed(pollRunnable, POLL_INTERVAL);
                }
                @Override
                public void onEmpty() {
                    if (isFinishing()) return;
                    isServerOffline = false;
                    tvConnSec.setText("Esperando datos…");
                    actualizarInterfaz();
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
        boolean isMuted = StateManager.isBuzzerMuted();

        if (isServerOffline || availability.equals("offline")) {
            btnAlarmOn.setEnabled(false);
            btnAlarmOff.setEnabled(false);
            btnAlarmOn.setAlpha(0.5f);
            btnAlarmOff.setAlpha(0.5f);
        } else {
            btnAlarmOn.setEnabled(isMuted);
            btnAlarmOn.setAlpha(isMuted ? 1.0f : 0.5f);

            btnAlarmOff.setEnabled(!isMuted);
            btnAlarmOff.setAlpha(!isMuted ? 1.0f : 0.5f);
        }

        updateSecurityUI(StateManager.getShelf01(), tvCell1Status, tvSecDetail1, card1);
    }

    private void updateSecurityUI(StateManager.ShelfData data, TextView tvStat, TextView tvDet, MaterialCardView card) {
        if (data.secure) {
            tvStat.setText(R.string.status_safe);
            tvStat.setTextColor(ContextCompat.getColor(this, R.color.green));
            card.setStrokeWidth(0);
        } else {
            tvStat.setText(R.string.status_alert);
            tvStat.setTextColor(ContextCompat.getColor(this, R.color.red));
            card.setStrokeWidth(4);
            card.setStrokeColor(ContextCompat.getColor(this, R.color.red));
        }

        tvDet.setText(getString(R.string.security_detail_format, data.baseline, data.current));
    }
}