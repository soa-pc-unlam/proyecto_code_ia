package com.example.espejo;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class DashboardActivity extends AppCompatActivity {



    public IntentFilter filterConncetionLost;
    private ConnectionLost connectionLost =new ConnectionLost();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        MqttHandler.getInstance(this).suscribe("espejo/sensor/distancia1");
        MqttHandler.getInstance(this).suscribe("espejo/sensor/distancia2");
        MqttHandler.getInstance(this).suscribe("espejo/sensor/luz");


        configurarBroadcastReceiver();

        // Carga el fragment inicial
        cargarFragment(new SensoresFragment());

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.nav_sensores) {
                cargarFragment(new SensoresFragment());
            } else if (id == R.id.nav_control) {
                cargarFragment(new ControlFragment());
            } else if (id == R.id.nav_historial) {
                cargarFragment(new HistorialFragment());
            }
            return true;
        });

        Intent serviceIntent = new Intent(this, TimeUseAlertService.class);
        startForegroundService(serviceIntent);
    }

    private void cargarFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }


    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void configurarBroadcastReceiver()
    {
        filterConncetionLost = new IntentFilter(MqttHandler.ACTION_CONNECTION_LOST);

        filterConncetionLost.addCategory(Intent.CATEGORY_DEFAULT);

        registerReceiver(connectionLost,filterConncetionLost);

    }

    @Override
    protected void onDestroy() {
        MqttHandler.getInstance(this).disconnect();
        unregisterReceiver(connectionLost);
        stopService(new Intent(this, TimeUseAlertService.class));
        super.onDestroy();

    }
    private void subscribeToTopic(String topic){
        Toast.makeText(this, "Subscribing to topic "+ topic, Toast.LENGTH_SHORT).show();
        MqttHandler.getInstance(this).suscribe(topic);
    }



    public class ConnectionLost extends BroadcastReceiver{

        public void onReceive(Context context, Intent intent) {

            Toast.makeText(getApplicationContext(),"Conexion Perdida",Toast.LENGTH_SHORT).show();

        }

    }



}
