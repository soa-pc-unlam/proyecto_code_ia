package com.soa2026.nonofono;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TelegramGestor {
    private static final String TAG = "TelegramGestor";
    private ScheduledExecutorService scheduler;
    private final Context contexto;
    private final TelegramApi telegram;
    private final Handler mainHandler;
    private final TelegramUIListener uiListener;

    // Interfaz para mandarle los colores y textos a la MainActivity de forma segura
    public interface TelegramUIListener {
        void onStatusUpdate(String mensaje, String colorHex);
    }

    public TelegramGestor(Context contexto, TelegramApi telegram, TelegramUIListener uiListener) {
        this.contexto = contexto;
        this.telegram = telegram;
        this.uiListener = uiListener;
        this.mainHandler = new Handler(Looper.getMainLooper());
    }

    public void iniciarMonitoreoContactos() {
        if (scheduler == null || scheduler.isShutdown()) {
            scheduler = Executors.newSingleThreadScheduledExecutor();
            scheduler.scheduleWithFixedDelay(this::procesarActualizaciones, 0, 5, TimeUnit.SECONDS);
            Log.i(TAG, "Monitoreo automático de Telegram iniciado.");
        }
    }

    public void detenerMonitoreoContactos() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
            scheduler = null;
            Log.i(TAG, "Monitoreo automático de Telegram detenido.");
        }
    }

    private void procesarActualizaciones() {
        // ping rápido para el estado visual
        boolean conectado = telegram.ping();
        actualizarUI(conectado ? "Telegram: OK (En línea) 🟢" : "Telegram: Buscando red... 🔴",
                conectado ? "#4CAF50" : "#F44336");

        ArrayList<Contacto> contactos = ContactosManager.cargar(contexto);
        SharedPreferences prefs = contexto.getSharedPreferences("offsetAPI", Context.MODE_PRIVATE);
        long offset = prefs.getLong("offset", 0);

        HashMap<String, Long> telefonosChatId = new HashMap<>();
        HashSet<Long> idsAsociadas = new HashSet<>();
        HashSet<Long> chatIds = new HashSet<>();

        for (Contacto cont : contactos){
            Long chatId = cont.getChatId();
            if(chatId != null && chatId != 0) {
                idsAsociadas.add(chatId);
            }
        }

        // traer los mensajes de Telegram
        String json = telegram.getUpdates(offset);

        if (json == null || json.trim().isEmpty()) {
            actualizarUI("Telegram: Error de Red 🔴", "#F44336");
            return;
        }

        try {
            JSONObject objeto = new JSONObject(json);
            JSONArray resultados = objeto.getJSONArray("result");

            // si no hay mensajes nuevos, cortamos acá para no hacer trabajo innecesario
            if (resultados.length() == 0) return;

            long updateId = 0;
            String regexTelefono = "^\\+?\\d{8,15}$";

            for (int i = 0; i < resultados.length(); i++) {
                JSONObject update = resultados.getJSONObject(i);
                updateId = update.getLong("update_id");

                // ignoramos si es un mensaje editado u otro evento de Telegram
                if (!update.has("message")) continue;

                JSONObject message = update.getJSONObject("message");
                if (!message.has("text")) continue;

                long chatId = message.getJSONObject("chat").getLong("id");
                String texto = message.getString("text");

                if (!idsAsociadas.contains(chatId) && texto.trim().matches(regexTelefono)) {
                    telefonosChatId.put(texto.replace(" ", ""), chatId);
                }
                chatIds.add(chatId);
            }

            Set<Long> idsConNumero = new HashSet<>(telefonosChatId.values());
            chatIds.removeAll(idsConNumero);
            chatIds.removeAll(idsAsociadas);

            // mensajes del bot
            for (long id : chatIds) {
                telegram.enviarMensaje(String.valueOf(id), "Por favor, envíe un mensaje que SOLAMENTE CONTENGA su número telefónico (11 digitos sin espacios)");
            }

            for (long id : idsConNumero) {
                telegram.enviarMensaje(String.valueOf(id), "Gracias por colaborar, ya puede recibir mensajes y noticias del nono");
            }

            boolean hayCambios = false;
            for (Contacto cont : contactos) {
                Long chatId = telefonosChatId.getOrDefault(cont.getTelefono(), null);
                if (chatId != null) {
                    System.out.println("CONTACTO CON CHATID ACTUALIZADO: " + cont.getNombre());
                    cont.setChatId(chatId);
                    hayCambios = true;
                }
            }

            // solo guardamos en SharedPreferences si realmente emparejamos un número
            if (hayCambios) {
                ContactosManager.guardar(contexto, contactos);
            }

            // Actualizamos el offset para los próximos 5 segundos
            prefs.edit().putLong("offset", updateId + 1).apply();

        } catch (JSONException ex) {
            Log.e(TAG, "Error con la API de Telegram", ex);
        }
    }

    private void actualizarUI(String texto, String colorHex) {
        // aseguramos que la actualización de los TextViews corra en el Hilo Principal
        mainHandler.post(() -> {
            if (uiListener != null) {
                uiListener.onStatusUpdate(texto, colorHex);
            }
        });
    }
}