package com.soa2026.nonofono;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class AudioDownloader {

    public static boolean descargar(String ip, String ruta, String archivo, String endpoint) {

        // Declaramos la conexión y el archivo antes del try
        HttpURLConnection conn = null;
        File file = new File(ruta, archivo);

        try {
            String urlStr = "http://" + ip + endpoint;
            Log.d("AudioDownloader", "Descargando desde: " + urlStr);

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            // Tiempo máximo para lograr conectarse al ESP32
            conn.setConnectTimeout(5000);
            // Tiempo máximo tolerado sin recibir datos en medio de la descarga
            conn.setReadTimeout(5000);
            conn.connect();

            if (conn.getResponseCode() != 200) {
                Log.e("AudioDownloader", "Error HTTP: " + conn.getResponseCode());
                return false;
            }

            // arranca el Try-with-resources que cierra los flujos automáticamente
            try (InputStream input = conn.getInputStream();
                 FileOutputStream output = new FileOutputStream(file)) {

                byte[] buffer = new byte[4096];
                int len;
                while ((len = input.read(buffer)) != -1) {
                    output.write(buffer, 0, len);
                }

                Log.d("AudioDownloader", "Audio descargado correctamente");
                return true;
            }

        } catch (Exception e) {
            Log.e("AudioDownloader", "Error descargando audio", e);

            // Si falló a la mitad, borramos el archivo corrupto para que no ocupe espacio
            if (file.exists()) {
                return file.delete();
            }
            return false;

        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

}