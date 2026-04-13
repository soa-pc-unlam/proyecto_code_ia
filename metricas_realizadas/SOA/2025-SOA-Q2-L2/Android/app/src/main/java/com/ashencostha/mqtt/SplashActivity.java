package com.ashencostha.mqtt;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    private View btnComenzar;
    private Button btnAyuda;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        btnComenzar = findViewById(R.id.btnComenzar);
        btnAyuda = findViewById(R.id.btnAyuda);

        btnComenzar.bringToFront();
        btnComenzar.setClickable(true);
        btnComenzar.setFocusable(true);

        btnComenzar.setOnClickListener(v -> {
            startActivity(new Intent(SplashActivity.this, MainActivity.class));
            finish();
        });

        btnAyuda.setOnClickListener(v ->
                startActivity(new Intent(SplashActivity.this, HelpActivity.class))
        );

        Button cmdExit = findViewById(R.id.cmdExit);
        cmdExit.setOnClickListener(v -> {
            finishAffinity();
            System.exit(0);
        });
    }
}
