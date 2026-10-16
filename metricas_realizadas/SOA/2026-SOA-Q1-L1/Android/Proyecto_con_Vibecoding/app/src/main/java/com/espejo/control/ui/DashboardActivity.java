package com.espejo.control.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.espejo.control.R;
import com.espejo.control.mqtt.MqttManager;
import com.espejo.control.mqtt.TelemetryBus;
import com.espejo.control.mqtt.TelemetryListener;
import com.espejo.control.service.UsageAlertService;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class DashboardActivity extends AppCompatActivity implements TelemetryListener {

    private Fragment sensoresFragment;
    private Fragment controlFragment;
    private Fragment historialFragment;
    private Fragment activeFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        setupFragments();
        setupBottomNavigation();

        TelemetryBus.getInstance().addListener(this);

        new Thread(() -> MqttManager.getInstance().subscribeToSensors()).start();
        ContextCompat.startForegroundService(this, new Intent(this, UsageAlertService.class));
    }

    private void setupFragments() {
        FragmentManager fm = getSupportFragmentManager();
        sensoresFragment = new SensoresFragment();
        controlFragment = new ControlFragment();
        historialFragment = new HistorialFragment();

        FragmentTransaction transaction = fm.beginTransaction();
        transaction.add(R.id.fragment_container, historialFragment, "historial");
        transaction.add(R.id.fragment_container, controlFragment, "control");
        transaction.add(R.id.fragment_container, sensoresFragment, "sensores");
        transaction.hide(historialFragment);
        transaction.hide(controlFragment);
        transaction.commit();

        activeFragment = sensoresFragment;
    }

    private void setupBottomNavigation() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment target;
            int itemId = item.getItemId();
            if (itemId == R.id.nav_control) {
                target = controlFragment;
            } else if (itemId == R.id.nav_historial) {
                target = historialFragment;
            } else {
                target = sensoresFragment;
            }
            showFragment(target);
            return true;
        });
    }

    private void showFragment(Fragment target) {
        if (target == activeFragment) {
            return;
        }
        getSupportFragmentManager().beginTransaction()
                .hide(activeFragment)
                .show(target)
                .commit();
        activeFragment = target;
    }

    @Override
    public void onConnectionLost() {
        Toast.makeText(this, R.string.dashboard_connection_lost, Toast.LENGTH_LONG).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        TelemetryBus.getInstance().removeListener(this);
        stopService(new Intent(this, UsageAlertService.class));
        new Thread(() -> MqttManager.getInstance().disconnect()).start();
    }
}
