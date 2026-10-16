package com.example.espejo;

import com.github.mikephil.charting.data.Entry;

import java.util.ArrayList;
import com.github.mikephil.charting.data.Entry;
import java.util.ArrayList;

public class HistorialDatos {

    private static ArrayList<Entry> datosDistancia1 = new ArrayList<>();
    private static ArrayList<Entry> datosDistancia2 = new ArrayList<>();
    private static ArrayList<Entry> datosLuz = new ArrayList<>();

    public static ArrayList<Entry> getDistancia1() {
        return datosDistancia1;
    }
    public static ArrayList<Entry> getDistancia2() {
        return datosDistancia2;
    }
    public static ArrayList<Entry> getLuz() {
        return datosLuz;
    }


}
