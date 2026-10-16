package com.example.soa;

import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.TextInputEditText;

public class SettingsActivity extends AppCompatActivity {

    private TextInputEditText etUrl;
    private TextInputEditText etDeviceId;
    private Button btnSave;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        etUrl = findViewById(R.id.etUrl);
        etDeviceId = findViewById(R.id.etDeviceId);
        btnSave = findViewById(R.id.btnSave);

        etUrl.setText(ApiClient.getInstance().getBaseUrl(this));
        etDeviceId.setText(ApiClient.getInstance().getDeviceId(this));

        btnSave.setOnClickListener(v -> {
            String url = etUrl.getText() != null ? etUrl.getText().toString().trim() : "";
            String deviceId = etDeviceId.getText() != null ? etDeviceId.getText().toString().trim() : "";

            if (url.isEmpty() || deviceId.isEmpty()) {
                Toast.makeText(this, "Por favor completa todos los campos", Toast.LENGTH_SHORT).show();
                return;
            }

            ApiClient.getInstance().saveSettings(this, url, deviceId);
            Toast.makeText(this, "Ajustes guardados correctamente", Toast.LENGTH_SHORT).show();
            finish();
        });
    }
}
