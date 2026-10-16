package com.soa2026.nonofono;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

@SuppressLint("SetTextI18n")
public class MainActivity extends AppCompatActivity {

    TextView txtTitulo, txtIcono, txtDescripcion;
    Button btnAgregarContacto, btnLocalizarDispositivo;
    TelegramApi telegram;
    TelegramGestor telegramGestor; // <-- Instancia de nuestro nuevo Gestor

    // --- Variables de Debug ---
    TextView txtDebugTelegram, txtDebugMQTT;
    private final Handler handlerDebug = new Handler(Looper.getMainLooper());
    private Runnable updaterDebug;

    private final android.content.BroadcastReceiver telegramReceiver = new android.content.BroadcastReceiver() {
        @Override
        public void onReceive(android.content.Context context, Intent intent) {
            String estado = intent.getStringExtra("estado");

            if ("OK".equals(estado)) {
                txtDebugTelegram.setText("Telegram: OK (Mensaje enviado)");
                txtDebugTelegram.setTextColor(Color.parseColor("#4CAF50"));
            } else {
                txtDebugTelegram.setText("Telegram: Error al enviar");
                txtDebugTelegram.setTextColor(Color.parseColor("#F44336"));
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        txtTitulo = findViewById(R.id.txtTitulo);
        txtIcono = findViewById(R.id.txtIcono);
        txtDescripcion = findViewById(R.id.txtDescripcion);
        btnAgregarContacto = findViewById(R.id.btnAgregarContacto);
        btnLocalizarDispositivo = findViewById(R.id.btnLocalizarDispositivo);
        txtDebugTelegram = findViewById(R.id.txtDebugTelegram);
        txtDebugMQTT = findViewById(R.id.txtDebugMQTT);

        btnAgregarContacto.setOnClickListener(v -> startActivity(new Intent(
                MainActivity.this, AgregarContactoActivity.class)));

        btnLocalizarDispositivo.setOnClickListener(v -> startActivity(new Intent(
                MainActivity.this, LocalizarDispositivoActivity.class)));

        telegram = new TelegramApi("8322654023:AAHhtylDzV06H71m-Xko3mhbg2TQ3J5brdA");

        // Le pasamos un listener para que pueda cambiar los colores de los textos desde el otro hilo
        telegramGestor = new TelegramGestor(this, telegram, (mensaje, colorHex) -> {
            txtDebugTelegram.setText(mensaje);
            txtDebugTelegram.setTextColor(Color.parseColor(colorHex));
        });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    101
            );
        }

        // configurar el polling visual para MQTT
        updaterDebug = new Runnable() {
            @Override
            public void run() {
                MQTTManager mqtt = MQTTManager.getInstance(getApplicationContext());
                if (mqtt.isConnected()) {
                    txtDebugMQTT.setText("MQTT: Conectado 🟢");
                    txtDebugMQTT.setTextColor(Color.parseColor("#4CAF50")); // Verde
                } else {
                    txtDebugMQTT.setText("MQTT: Desconectado 🔴");
                    txtDebugMQTT.setTextColor(Color.parseColor("#F44336")); // Rojo
                }
                // Se vuelve a ejecutar a sí mismo cada 2 segundos
                handlerDebug.postDelayed(this, 2000);
            }
        };
    }

    @Override
    protected void onStart() {
        super.onStart();
        // levantamos el servicio en segundo plano
        Intent serviceIntent = new Intent(this, NonofonoService.class);
        startForegroundService(serviceIntent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // prendemos el updater de MQTT
        handlerDebug.post(updaterDebug);

        // registramos el receiver de los envíos
        android.content.IntentFilter filtro = new android.content.IntentFilter("ACTUALIZAR_ESTADO_TELEGRAM");
        androidx.core.content.ContextCompat.registerReceiver(
                this,
                telegramReceiver,
                filtro,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        );

        if (telegramGestor != null) {
            telegramGestor.iniciarMonitoreoContactos();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // apagamos todo lo visual al minimizar
        handlerDebug.removeCallbacks(updaterDebug);
        unregisterReceiver(telegramReceiver);

        // frenamos el bucle de Telegram para no gastar recursos
        if (telegramGestor != null) {
            telegramGestor.detenerMonitoreoContactos();
        }
    }
}