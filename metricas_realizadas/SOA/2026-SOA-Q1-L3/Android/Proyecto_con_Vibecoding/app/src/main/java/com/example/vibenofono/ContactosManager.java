package com.example.vibenofono;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ContactosManager {
    private static final String TAG = "ContactosManager";
    private static final String PREFS_NAME = "nonofono_prefs";
    private static final String KEY_CONTACTOS = "contactos_list";
    private static final int MAX_CONTACTOS = 10;

    private final SharedPreferences prefs;

    public ContactosManager(Context context) {
        this.prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<String> obtenerContactos() {
        String json = prefs.getString(KEY_CONTACTOS, "[]");
        try {
            JSONArray array = new JSONArray(json);
            List<String> contactos = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                contactos.add(array.optString(i));
            }
            return contactos;
        } catch (Exception e) {
            Log.e(TAG, "Error parseando contactos", e);
            return Collections.emptyList();
        }
    }

    public boolean agregarContacto(String contacto) {
        if (!validarContacto(contacto)) {
            return false;
        }
        List<String> contactos = new ArrayList<>(obtenerContactos());
        if (contactos.size() >= MAX_CONTACTOS || contactos.contains(contacto)) {
            return false;
        }
        contactos.add(contacto);
        return guardarContactos(contactos);
    }

    public boolean eliminarContacto(String contacto) {
        List<String> contactos = new ArrayList<>(obtenerContactos());
        boolean removed = contactos.remove(contacto);
        if (!removed) {
            return false;
        }
        return guardarContactos(contactos);
    }

    private boolean guardarContactos(List<String> contactos) {
        JSONArray array = new JSONArray();
        for (String contacto : contactos) {
            array.put(contacto);
        }
        return prefs.edit().putString(KEY_CONTACTOS, array.toString()).commit();
    }

    public static boolean validarContacto(String contacto) {
        return contacto != null && contacto.matches("^.{8,15}$");
    }

    public String toJsonObjectArray() {
        JSONArray array = new JSONArray();
        for (String contacto : obtenerContactos()) {
            JSONObject contactObject = new JSONObject();
            try {
                contactObject.put("name", contacto);
                contactObject.put("phone", contacto);
                array.put(contactObject);
            } catch (Exception e) {
                Log.e(TAG, "Error creando JSON de contactos", e);
            }
        }
        return array.toString();
    }
}
