package com.example.espejo;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class Conexion extends AppCompatActivity {

    private MqttHandler mqttHandler;
    private EditText editIp;
    private EditText editPuerto;
    private TextView textEstado;
    private Button botonConectar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_conexion);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        editIp = findViewById(R.id.editIp);
        editPuerto = findViewById(R.id.editPuerto);
        textEstado = findViewById(R.id.textEstado);
        botonConectar = findViewById(R.id.botonConectar);

        botonConectar.setOnClickListener(v -> {
            String ip = editIp.getText().toString().trim();
            String puerto = editPuerto.getText().toString().trim();

            if (ip.isEmpty() || puerto.isEmpty()) {
                textEstado.setText("Completá todos los campos");
                return;
            }

            textEstado.setText("Conectando a " + ip + ":" + puerto + "...");

            connect(ip,puerto);
        });
    }

    private void connect(String ip, String puerto){
        try {
            String brokerUrl = "tcp://" + ip + ":" + puerto;

            boolean exito = mqttHandler.getInstance(this).connect(brokerUrl);
            Thread.sleep(1000);

            if(exito){
                Intent intent = new Intent(this, DashboardActivity.class);
                startActivity(intent);
                finish();
            }else{
                Toast.makeText(this, "No se pudo conectar", Toast.LENGTH_SHORT).show();
            }


        }catch (InterruptedException e){
            throw new RuntimeException(e);
        }
    }


}
