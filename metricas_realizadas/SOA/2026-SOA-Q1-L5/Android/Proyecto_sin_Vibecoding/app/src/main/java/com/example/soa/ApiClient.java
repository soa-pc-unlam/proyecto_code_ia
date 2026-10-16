package com.example.soa;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ApiClient {
    private static ApiClient instance;
    private final OkHttpClient client;
    private final Handler mainHandler;
    private static final String PREFS_NAME = "ApiPrefs";
    private static final String KEY_BASE_URL = "baseUrl";
    private static final String KEY_DEVICE_ID = "deviceId";
    private static final String DEFAULT_URL = "http://10.0.2.2:1880";
    private static final String DEFAULT_ID = "corridor-01";

    public interface StateCallback {
        void onState(JSONObject state);
        void onError(String msg);
        default void onEmpty() { onError("Esperando datos de la góndola…"); }
    }

    public interface OkCallback {
        void onOk(JSONObject resp);
        void onError(String msg);
    }

    private ApiClient() {
        client = new OkHttpClient.Builder()
                .connectTimeout(1, TimeUnit.SECONDS)
                .readTimeout(1, TimeUnit.SECONDS)
                .writeTimeout(1, TimeUnit.SECONDS)
                .build();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    public static synchronized ApiClient getInstance() {
        if (instance == null) {
            instance = new ApiClient();
        }
        return instance;
    }

    public String getBaseUrl(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_BASE_URL, DEFAULT_URL);
    }

    public String getDeviceId(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getString(KEY_DEVICE_ID, DEFAULT_ID);
    }

    public void saveSettings(Context context, String url, String id) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_BASE_URL, url)
                .putString(KEY_DEVICE_ID, id)
                .apply();
    }

    public void getState(Context context, StateCallback cb) {
        String url = getBaseUrl(context) + "/api/" + getDeviceId(context);
        Request request = new Request.Builder().url(url).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                postError(cb, "Sin conexión con el servidor");
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response resp = response) {
                    if (resp.body() == null) {
                        postError(cb, "Respuesta vacía");
                        return;
                    }
                    String body = resp.body().string();
                    if (resp.isSuccessful()) {
                        JSONObject json = new JSONObject(body);
                        mainHandler.post(() -> cb.onState(json));
                    } else if (resp.code() == 404) {
                        mainHandler.post(cb::onEmpty);
                    } else {
                        postError(cb, "Error " + resp.code());
                    }
                } catch (Exception e) {
                    postError(cb, "Error parseando respuesta");
                }
            }
        });
    }

    public void sendStock(Context context, boolean on, OkCallback cb) {
        JSONObject json = new JSONObject();
        try { json.put("status", on? "ON" : "OFF"); } catch (JSONException ignored) {}
        postRequest(context, "/stock", json, cb);
    }

    public void sendSecurity(Context context, boolean on, OkCallback cb) {
        JSONObject json = new JSONObject();
        try { json.put("status", on? "ON" : "OFF"); } catch (JSONException ignored) {}
        postRequest(context, "/security", json, cb);
    }

    public void sendAlarm(Context context, String value, OkCallback cb) {
        JSONObject json = new JSONObject();
        try { json.put("status", value); } catch (JSONException ignored) {}
        postRequest(context, "/security/alarm", json, cb);
    }

    private void postRequest(Context context, String endpoint, JSONObject body, OkCallback cb) {
        String url = getBaseUrl(context) + "/api/" + getDeviceId(context) + endpoint;
        RequestBody requestBody = RequestBody.create(
                body.toString(), MediaType.parse("application/json; charset=utf-8"));
        
        Request request = new Request.Builder().url(url).post(requestBody).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                postError(cb, "Error de red: " + e.getMessage());
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) {
                try (Response resp = response) {
                    if (resp.body() == null) {
                        postError(cb, "Sin respuesta del servidor");
                        return;
                    }
                    String respBody = resp.body().string();
                    if (resp.isSuccessful()) {
                        JSONObject json;
                        try {
                            json = new JSONObject(respBody);
                        } catch (Exception e) {
                            json = new JSONObject();
                            try { json.put("result", respBody); } catch (JSONException ignored) {}
                        }
                        final JSONObject finalJson = json;
                        mainHandler.post(() -> cb.onOk(finalJson));
                    } else {
                        postError(cb, "Error " + resp.code());
                    }
                } catch (Exception e) {
                    postError(cb, "Error en comando");
                }
            }
        });
    }

    private void postError(StateCallback cb, String msg) {
        mainHandler.post(() -> cb.onError(msg));
    }

    private void postError(OkCallback cb, String msg) {
        mainHandler.post(() -> cb.onError(msg));
    }
}