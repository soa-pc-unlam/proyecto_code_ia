package com.espejo.control.ui;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.espejo.control.R;
import com.espejo.control.mqtt.MqttManager;
import com.google.android.material.switchmaterial.SwitchMaterial;

public class ConnectionActivity extends AppCompatActivity {

    private EditText editIp;
    private EditText editPort;
    private SwitchMaterial switchSimulated;
    private Button buttonConnect;
    private TextView textStatus;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_connection);

        editIp = findViewById(R.id.edit_ip);
        editPort = findViewById(R.id.edit_port);
        switchSimulated = findViewById(R.id.switch_simulated);
        buttonConnect = findViewById(R.id.button_connect);
        textStatus = findViewById(R.id.text_connection_status);

        buttonConnect.setOnClickListener(v -> onConnectClicked());
    }

    private void onConnectClicked() {
        if (switchSimulated.isChecked()) {
            MqttManager.getInstance().setSimulatedMode(true);
            goToDashboard();
            return;
        }

        MqttManager.getInstance().setSimulatedMode(false);

        String ip = editIp.getText() != null ? editIp.getText().toString().trim() : "";
        String portText = editPort.getText() != null ? editPort.getText().toString().trim() : "";

        if (TextUtils.isEmpty(ip) || TextUtils.isEmpty(portText)) {
            textStatus.setText(R.string.connection_status_missing_fields);
            return;
        }

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException e) {
            textStatus.setText(R.string.connection_status_missing_fields);
            return;
        }

        setUiConnecting(true);

        new Thread(() -> {
            boolean success = MqttManager.getInstance().connect(ip, port);
            mainHandler.post(() -> {
                setUiConnecting(false);
                if (success) {
                    goToDashboard();
                } else {
                    textStatus.setText(R.string.connection_status_error);
                }
            });
        }).start();
    }

    private void setUiConnecting(boolean connecting) {
        buttonConnect.setEnabled(!connecting);
        if (connecting) {
            textStatus.setText(R.string.connection_status_connecting);
        }
    }

    private void goToDashboard() {
        startActivity(new Intent(this, DashboardActivity.class));
        finish();
    }
}
