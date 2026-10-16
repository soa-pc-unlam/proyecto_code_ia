package com.example.espejo;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

public class SensoresFragment extends Fragment {

    private TextView distancia1;
    private TextView distancia2;
    private TextView luz;
    private ReceptorOperacion receiver = new ReceptorOperacion();
    public IntentFilter filterReceive;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_sensores, container, false);

        distancia1 = view.findViewById(R.id.valorDistancia1);
        distancia2 = view.findViewById(R.id.valorDistancia2);
        luz = view.findViewById(R.id.valorLuz);


        return view;
    }



    @Override
    public void onResume() {
        super.onResume();

        configurarBroadcastReceiver();
    }

    @Override
    public void onPause() {
        super.onPause();
        requireContext().unregisterReceiver(receiver);
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private void configurarBroadcastReceiver()
    {
        filterReceive = new IntentFilter(MqttHandler.ACTION_DATA_RECEIVE);
        filterReceive.addCategory(Intent.CATEGORY_DEFAULT);
        requireContext().registerReceiver(receiver, filterReceive);

    }

    public class ReceptorOperacion extends BroadcastReceiver {

        public void onReceive(Context context, Intent intent) {
            String topic = intent.getStringExtra("topic");
            String payload = intent.getStringExtra("payload");

            if (topic.equals("espejo/sensor/distancia1")) {
                distancia1.setText(payload + " cm");
            } else if (topic.equals("espejo/sensor/distancia2")) {
                distancia2.setText(payload + " cm");
            } else if (topic.equals("espejo/sensor/luz")) {
                luz.setText(payload);
            }
        }

    }


}
