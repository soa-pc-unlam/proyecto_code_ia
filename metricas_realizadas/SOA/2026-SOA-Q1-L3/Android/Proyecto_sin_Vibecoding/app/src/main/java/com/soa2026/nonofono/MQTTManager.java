package com.soa2026.nonofono;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import org.eclipse.paho.client.mqttv3.*;
import javax.net.ssl.SSLSocketFactory;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;

public class MQTTManager {

    private MqttClient client;
    private static MQTTManager instance;
    private final TelegramApi apiTelegram;
    private final Context contexto;

    public MQTTManager(Context contexto){
        apiTelegram = new TelegramApi("8322654023:AAHhtylDzV06H71m-Xko3mhbg2TQ3J5brdA");
        this.contexto = contexto;
    }
    public void conectar() throws MqttException {

        if (client != null && client.isConnected())
            return;

        String broker =
                "ssl://e32525b034cd4ef5a01f6e9715295f64.s1.eu.hivemq.cloud";

        MemoryPersistence persistence = new MemoryPersistence();

        client = new MqttClient(
                broker,
                MqttClient.generateClientId(),
                persistence
        );

        MqttConnectOptions options =
                new MqttConnectOptions();

        options.setAutomaticReconnect(true); //hace el keep-alive. En el esp32 esto es manual
        options.setCleanSession(false);
        options.setUserName("admin_prueba");
        options.setPassword("Nonofono8".toCharArray());

        options.setSocketFactory(SSLSocketFactory.getDefault());
        //para conexion segura

        client.setCallback(new MqttCallback() {

            @Override
            public void connectionLost(Throwable cause) {
                System.out.println("MQTT desconectado");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws JSONException {
                String payload =
                        new String(message.getPayload());
                procesarMensaje(payload);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {}
        });

        client.connect(options);
    }


    public void desconectar() {
        try {
            if (client != null && client.isConnected()) {
                client.disconnect();

                client.close(); // Libera los sockets de red
                client = null;  // Fuerza al conectar() a instanciar uno nuevo

                Log.i("MQTTManager", "Desconectado de HiveMQ exitosamente de forma limpia.");
            } else {
                Log.w("MQTTManager", "Se intentó desconectar, pero el cliente ya estaba desconectado.");
            }
        } catch (Exception e) {
            Log.e("MQTTManager", "Error crítico al intentar desconectar de MQTT: " + e.getMessage());
        }
    }

    public static synchronized MQTTManager getInstance(Context contexto) {

        if (instance == null)
            instance = new MQTTManager(contexto);

        return instance;
    }

     public void suscribirse() throws MqttException {

        if (client == null || !client.isConnected())
            return;

        client.subscribe("nonofono/emergencias");
        client.subscribe("nonofono/mensajes/audio");
        client.subscribe("nonofono/mensajes/predeterminado");
        client.subscribe("nonofono/live");
    }

    public void publicar(String topic, String mensaje) throws MqttException {

        if (client == null || !client.isConnected()) {
            throw new IllegalStateException("MQTT no está conectado");
        }

        MqttMessage mqttMessage = new MqttMessage();
        mqttMessage.setPayload(mensaje.getBytes());
        mqttMessage.setQos(1);          // Entrega al menos una vez
        mqttMessage.setRetained(false); // No guardar el mensaje en el broker

        client.publish(topic, mqttMessage);
    }

    private void procesarMensaje(String mensaje) throws JSONException {

        System.out.println("Mensaje: " + mensaje);
        JSONObject json = new JSONObject(mensaje);
        String evento = json.getString("evento");

        // Extraemos directamente como JSONObject para poder parsearlo
        JSONObject jsonContenido = json.getJSONObject("contenido");
        if(evento.equals("emergencia"))
        {
            String mensajeEmergencia = jsonContenido.getString("mensaje");

            new Thread(() -> {
                ArrayList<Contacto> contactos = ContactosManager.cargar(contexto);

                for(Contacto cont : contactos){
                    Long chatId = cont.getChatId();

                    // Validamos que no sea nulo Y que no sea 0
                    if(chatId != null && chatId != 0){
                        apiTelegram.enviarMensaje(String.valueOf(chatId), mensajeEmergencia);
                    } else {
                        System.err.println("Contacto " + cont.getNombre() + " ignorado: chat_id inválido o es 0");
                    }
                }

            }).start();
        }
        else if (evento.equals("audio")) {

            // extraemos los datos que si existen en el JSON
            // Si no viene la IP, asigna un valor por defecto
            String ip = jsonContenido.optString("ip", "0.0.0.0");
            String endpoint= jsonContenido.optString("endpoint", "/descargar_audio");
            int idMensaje = jsonContenido.optInt("id_mensaje", -1);

            if (ip.equals("0.0.0.0")) {
                Log.e("MQTTManager", "JSON malformado: No se recibió IP del ESP32");
                return;
            }
            String nombreArchivo = "audio_" + idMensaje + ".wav";

            System.out.println("RESPUESTA JSON:\n" + jsonContenido);
            System.out.println("IP A ENVIAR: " + ip);

            new Thread(() -> {
                try {
                    boolean exito = AudioDownloader.descargar(ip, "/data/data/com.soa2026.nonofono/files/", nombreArchivo, endpoint);
                    Intent intent = new Intent("ACTUALIZAR_ESTADO_TELEGRAM");
                    intent.putExtra("estado", "OK");
                    contexto.sendBroadcast(intent);
                    if (!exito) {
                        System.err.println("Se aborta el envío a Telegram porque la descarga falló.");
                        return; // Cortamos la ejecución acá
                    }

                    File archAudio = new File("/data/data/com.soa2026.nonofono/files/", nombreArchivo);
                    System.out.println("Mensaje recibido :D");
                    String telefono = jsonContenido.getString("destinatario");
                    Contacto destino = buscarContacto(telefono);

                    if (destino == null) {
                        System.err.println("No se encontró el destinatario del mensaje");
                    } else {
                        JSONObject respuesta = new JSONObject(apiTelegram.enviarAudio(
                                String.valueOf(destino.getChatId()),
                                archAudio));

                        if(respuesta.optBoolean("ok", false)
                        ){
                            publicar("nonofono/mensajes/audio/ack", "{\"exito\":\"Ok\"}");
                        }
                        System.out.println("Respuesta al audio: "+respuesta);
                    }
                } catch (JSONException | MqttException e) {
                    System.err.println("Error procesando audio en el hilo: " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("Error general en descarga: " + e.getMessage());
                }
            }).start();
        }
        else if(evento.equals("mensaje-predefinido")){
            String telefono = jsonContenido.optString("telefono", "");
            String msj = jsonContenido.optString("contenido", "");

            Contacto destino = buscarContacto(telefono);

            if (destino != null) {
                Long chatId = destino.getChatId();

                if (chatId != null && chatId != 0) {
                    String respuestaString = apiTelegram.enviarMensaje(String.valueOf(chatId), msj);

                    if (respuestaString != null) {
                        try {
                            JSONObject respuesta = new JSONObject(respuestaString);
                            if (respuesta.optBoolean("ok", false)){
                                publicar("nonofono/mensajes/predeterminado/ack", "{\"exito\":\"Ok\"}");
                                Log.i("MQTTManager", "Mensaje predefinido enviado con éxito.");
                                Intent intent = new Intent("ACTUALIZAR_ESTADO_TELEGRAM");
                                intent.putExtra("estado", "OK");
                                contexto.sendBroadcast(intent);
                            } else {
                                Log.e("MQTTManager", "Telegram rechazó el mensaje: " + respuestaString);
                            }
                        } catch (JSONException | MqttException e) {
                            Log.e("MQTTManager", "Error procesando respuesta de Telegram: " + e.getMessage());
                        }
                    } else {
                        Log.e("MQTTManager", "Error de red: apiTelegram devolvió null");
                    }
                } else {
                    Log.w("MQTTManager", "Imposible enviar: El contacto " + destino.getNombre() + " (tel: " + telefono + ") no tiene un chat_id vinculado en Telegram.");
                }
            } else {
                Log.e("MQTTManager", "No se encontró el destinatario del mensaje predefinido en la base de datos local");
            }
        }else if (evento.equals("live")){
            ArrayList<Contacto> contactos = ContactosManager.cargar(contexto);

            JSONArray arrayContactos = new JSONArray();

            if(!contactos.isEmpty()){
                for(Contacto cont : contactos){
                    JSONObject contactoActual = new JSONObject();
                    contactoActual.put("name", cont.getNombre());
                    contactoActual.put("phone", cont.getTelefono());
                    arrayContactos.put(contactoActual);
                }

                new Thread(() -> {
                    try {
                        Thread.sleep(500);
                        publicar("nonofono/config/contactos", arrayContactos.toString());
                    } catch (MqttException | InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }).start();
            }
        }


    }

    private Contacto buscarContacto(String telefono){
        ArrayList<Contacto> contactos = ContactosManager.cargar(contexto);
        int i = 0, tam = contactos.size();

        while(i < tam && !contactos.get(i).getTelefono().equals(telefono)){
            i++;
        }

        if (i < tam)
            return contactos.get(i);

        return null;
    }
    public boolean isConnected() {
        return client != null && client.isConnected();
    }
}