package com.soa2026.nonofono;

import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class TelegramApi {
    private final String urlBase;

    public TelegramApi(String token) {
        this.urlBase = "https://api.telegram.org/bot" + token + "/";
    }

    //Envía un mensaje al chat indicado
    public String enviarMensaje(String idChat, String mensaje) {

        try {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mensaje = URLEncoder.encode(mensaje, StandardCharsets.UTF_8);
            }

            URL url = new URL(urlBase +
                    "sendMessage?chat_id=" +
                    idChat +
                    "&text=" +
                    mensaje);

            HttpURLConnection conexion =
                    (HttpURLConnection) url.openConnection();

            conexion.setRequestMethod("GET");

            BufferedReader br = new BufferedReader(
                    new InputStreamReader(conexion.getInputStream()));

            StringBuilder respuesta = new StringBuilder();

            String linea;

            while ((linea = br.readLine()) != null) {
                respuesta.append(linea);
            }

            br.close();
            conexion.disconnect();

            return respuesta.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    // Hace un ping rápido para comprobar conexión sin bloquear el hilo
    public boolean ping() {
        try {
            URL url = new URL(urlBase + "getMe");
            HttpURLConnection conexion = (HttpURLConnection) url.openConnection();
            conexion.setRequestMethod("GET");
            conexion.setConnectTimeout(3000); // 3 segundos de tolerancia máxima
            int status = conexion.getResponseCode();
            conexion.disconnect();
            return status == 200;
        } catch (Exception e) {
            return false; // Error de red o sin internet
        }
    }
    // Obtiene solo los mensajes posteriores al offset.
    public String getUpdates(long offset) {
        HttpURLConnection conexion = null;
        BufferedReader br = null;
        try {
            URL url = new URL(urlBase + "getUpdates?offset=" + offset);
            conexion = (HttpURLConnection) url.openConnection();
            conexion.setRequestMethod("GET");
            int status = conexion.getResponseCode();
            InputStream is;

            if (status >= 200 && status < 300) {
                is = conexion.getInputStream(); // Éxito (200 OK)
            } else {
                is = conexion.getErrorStream(); // Error (400, 429, etc.)

                Log.e("TelegramApi", "¡Telegram rechazó la petición! Código HTTP: " + status + " - " + conexion.getResponseMessage());
            }

            if (is == null) return null; // Si no hay ni inputStream ni errorStream, falló la red

            br = new BufferedReader(new InputStreamReader(is));
            StringBuilder respuesta = new StringBuilder();
            String linea;
            while ((linea = br.readLine()) != null) {
                respuesta.append(linea);
            }
            return respuesta.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            // esto se ejecuta siempre, liberando memoria y sockets
            try {
                if (br != null) br.close();
                if (conexion != null) conexion.disconnect();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
    public String enviarAudio(String chatId, File audio) {
        String boundary = "===" + System.currentTimeMillis() + "===";
        HttpURLConnection conn = null;
        DataOutputStream request = null;
        FileInputStream inputStream = null;
        BufferedReader br = null;

        try {
            URL url = new URL(urlBase + "sendAudio");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setUseCaches(false);
            conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            request = new DataOutputStream(conn.getOutputStream());

            // chat_id
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"chat_id\"\r\n\r\n");
            request.writeBytes(chatId + "\r\n");

            // Archivo
            request.writeBytes("--" + boundary + "\r\n");
            request.writeBytes("Content-Disposition: form-data; name=\"audio\"; filename=\"" + audio.getName() + "\"\r\n");
            request.writeBytes("Content-Type: audio/mpeg\r\n\r\n");

            inputStream = new FileInputStream(audio);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                request.write(buffer, 0, bytesRead);
            }
            request.writeBytes("\r\n");
            request.writeBytes("--" + boundary + "--\r\n");
            request.flush();

            // Leer respuesta (usando getErrorStream si falla)
            InputStream is = (conn.getResponseCode() >= 200 && conn.getResponseCode() < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            if (is == null) return null;

            br = new BufferedReader(new InputStreamReader(is));
            StringBuilder respuesta = new StringBuilder();
            String linea;
            while ((linea = br.readLine()) != null) {
                respuesta.append(linea);
            }
            return respuesta.toString();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        } finally {
            // Cierre seguro de todos los recursos
            try {
                if (inputStream != null) inputStream.close();
                if (request != null) request.close();
                if (br != null) br.close();
                if (conn != null) conn.disconnect();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
}