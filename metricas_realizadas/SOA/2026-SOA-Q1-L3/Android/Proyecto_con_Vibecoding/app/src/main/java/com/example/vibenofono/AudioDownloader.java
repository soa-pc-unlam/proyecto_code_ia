package com.example.vibenofono;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class AudioDownloader {
    private static final String TAG = "AudioDownloader";
    private static final int TIMEOUT_MS = 10000;

    public static File downloadAudio(Context context, String host, String endpoint) {
        HttpURLConnection connection = null;
        InputStream input = null;
        FileOutputStream output = null;
        try {
            String urlString = "http://" + host + endpoint;
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.connect();
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "Error descargando audio: " + connection.getResponseCode());
                return null;
            }
            input = new BufferedInputStream(connection.getInputStream());
            File destination = new File(context.getCacheDir(), "nonofono_audio.wav");
            output = new FileOutputStream(destination);
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
            output.flush();
            return destination;
        } catch (IOException e) {
            Log.e(TAG, "Error descargando audio", e);
            return null;
        } finally {
            closeQuietly(input);
            closeQuietly(output);
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static boolean sendAudioToTelegram(String botToken, String chatId, File file) {
        String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
        HttpURLConnection connection = null;
        DataOutputStream request = null;
        try {
            URL url = new URL("https://api.telegram.org/bot" + botToken + "/sendAudio");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setDoInput(true);
            connection.setUseCaches(false);
            connection.setRequestProperty("Connection", "Keep-Alive");
            connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

            request = new DataOutputStream(new BufferedOutputStream(connection.getOutputStream()));
            addFormField(request, boundary, "chat_id", chatId);
            addFilePart(request, boundary, "audio", file);
            finishMultipart(request, boundary);
            request.flush();

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return true;
            } else {
                Log.e(TAG, "Telegram sendAudio failed: " + responseCode);
                return false;
            }
        } catch (IOException e) {
            Log.e(TAG, "Error enviando audio a Telegram", e);
            return false;
        } finally {
            closeQuietly(request);
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static void addFormField(DataOutputStream stream, String boundary, String name, String value) throws IOException {
        stream.writeBytes("--" + boundary + "\r\n");
        stream.writeBytes("Content-Disposition: form-data; name=\"" + name + "\"\r\n");
        stream.writeBytes("\r\n");
        stream.writeBytes(value + "\r\n");
    }

    private static void addFilePart(DataOutputStream stream, String boundary, String fieldName, File uploadFile) throws IOException {
        stream.writeBytes("--" + boundary + "\r\n");
        stream.writeBytes("Content-Disposition: form-data; name=\"" + fieldName + "\"; filename=\"" + uploadFile.getName() + "\"\r\n");
        stream.writeBytes("Content-Type: audio/wav\r\n");
        stream.writeBytes("\r\n");

        try (FileInputStream inputStream = new FileInputStream(uploadFile)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                stream.write(buffer, 0, bytesRead);
            }
            stream.writeBytes("\r\n");
        }
    }

    private static void finishMultipart(DataOutputStream stream, String boundary) throws IOException {
        stream.writeBytes("--" + boundary + "--\r\n");
    }

    private static void closeQuietly(AutoCloseable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }
}
