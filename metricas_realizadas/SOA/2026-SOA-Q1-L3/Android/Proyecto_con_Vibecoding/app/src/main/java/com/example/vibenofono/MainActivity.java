package com.example.vibenofono;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_CODE_POST_NOTIFICATIONS = 101;

    private TextView mqttStatusText;
    private TextView telegramStatusText;
    private BroadcastReceiver statusReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mqttStatusText = findViewById(R.id.text_mqtt_status);
        telegramStatusText = findViewById(R.id.text_telegram_status);
        Button buttonContactos = findViewById(R.id.button_contactos);
        Button buttonLocalizar = findViewById(R.id.button_localizar);

        buttonContactos.setOnClickListener(v -> startActivity(new Intent(this, AgregarContactosActivity.class)));
        buttonLocalizar.setOnClickListener(v -> startActivity(new Intent(this, LocalizarActivity.class)));

        requestNotificationsPermission();

        MqttClientSingleton.initialize(this);
        Intent serviceIntent = new Intent(this, NonofonoService.class);
        startForegroundService(serviceIntent);

        statusReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if (intent == null || intent.getAction() == null) {
                    return;
                }
                String status = intent.getStringExtra(NonofonoService.EXTRA_STATUS_TEXT);
                if (NonofonoService.ACTION_MQTT_STATUS.equals(intent.getAction())) {
                    mqttStatusText.setText(status);
                } else if (NonofonoService.ACTION_TELEGRAM_STATUS.equals(intent.getAction())) {
                    telegramStatusText.setText(status);
                }
            }
        };
    }

    @Override
    protected void onResume() {
        super.onResume();
        IntentFilter filter = new IntentFilter();
        filter.addAction(NonofonoService.ACTION_MQTT_STATUS);
        filter.addAction(NonofonoService.ACTION_TELEGRAM_STATUS);
        registerReceiver(statusReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(statusReceiver);
    }

    private void requestNotificationsPermission() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_CODE_POST_NOTIFICATIONS);
            }
        }
    }
}
