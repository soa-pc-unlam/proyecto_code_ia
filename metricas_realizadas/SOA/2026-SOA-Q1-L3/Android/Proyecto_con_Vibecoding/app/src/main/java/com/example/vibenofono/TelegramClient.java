package com.example.vibenofono;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class TelegramClient {
    private static final String TAG = "TelegramClient";
    private static final int TIMEOUT_MS = 10000;

    public static boolean sendMessage(String botToken, String chatId, String text) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL("https://api.telegram.org/bot" + botToken + "/sendMessage");
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(TIMEOUT_MS);
            connection.setReadTimeout(TIMEOUT_MS);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");

            JSONObject body = new JSONObject();
            body.put("chat_id", chatId);
            body.put("text", text);

            try (OutputStream os = new BufferedOutputStream(connection.getOutputStream())) {
                os.write(body.toString().getBytes("UTF-8"));
                os.flush();
            }

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return true;
            }
            Log.e(TAG, "Telegram sendMessage failed: " + responseCode + " " + readResponse(connection));
            return false;
        } catch (Exception e) {
            Log.e(TAG, "Error enviando mensaje a Telegram", e);
            return false;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static boolean sendAudio(String botToken, String chatId, File audioFile) {
        return AudioDownloader.sendAudioToTelegram(botToken, chatId, audioFile);
    }

    private static String readResponse(HttpURLConnection connection) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream() != null ? connection.getErrorStream() : connection.getInputStream()))) {
            StringBuilder builder = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
            return builder.toString();
        } catch (IOException e) {
            return "";
        }
    }
}
