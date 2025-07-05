# Modificar código para comunicación mqtt
_Exported on 7/4/2025 at 19:31:59 GMT-3 from Cursor (1.2.1)_

---

**User**

modifica el código generado para que realice lo siguiente:
•	El sistema se debe conectar a un bróker mqtt 
•	La lógica de la comunicación mqtt debe cumplir el patron de máquina de estados implementado .
•	Se debe suscribir al topico /casa/cmd
•	Dependiendo del valor recibido en el topico /casa/cmd,se  encenderá o apagara el rele
•	Cada determinado tiempo se debe publicar la temperatura medida del sensor, en el topico /casa/temperatura del borker mqtt


---

**Cursor**

Para cumplir con tus requisitos, haré las siguientes modificaciones al código:

1. **Agregar soporte MQTT**: Usaré la librería `PubSubClient` y la de WiFi.
2. **Conexión a WiFi y al bróker MQTT**: Añadiré la lógica de conexión en el `setup` y reconexión en el `loop`.
3. **Suscripción al tópico `/casa/cmd`**: El ESP32 se suscribirá a este tópico y reaccionará a los mensajes recibidos.
4. **Control del relé por MQTT**: Si recibe `"ON"` en `/casa/cmd`, enciende el relé; si recibe `"OFF"`, lo apaga.
5. **Publicar temperatura periódicamente**: Cada cierto tiempo, publicará la temperatura en `/casa/temperatura`.
6. **Integrar la lógica MQTT en la máquina de estados**: Los comandos MQTT serán tratados como eventos en la FSM.

¿Quieres que la lógica de control manual (botones) y automática (temperatura) siga funcionando junto con el control MQTT, o el control MQTT debe tener prioridad?  
¿Cada cuánto tiempo quieres que se publique la temperatura (por ejemplo, cada 10 segundos)? 

Por favor, dime el nombre y clave de tu red WiFi, y la dirección del bróker MQTT (puede ser test.mosquitto.org si es para pruebas).  
¿El relé es activo alto (HIGH lo enciende) o activo bajo (LOW lo enciende)? 

Con esa información, haré la modificación completa.

---

**User**

que la logica de control manual de botones y automatica de temperatura siga fucnionando junto al contro mqt

---

**Cursor**

¡Perfecto! Entonces, el sistema funcionará así:

- **Botones**: seguirán cambiando el modo y controlando el relé como hasta ahora.
- **Automático**: la temperatura seguirá encendiendo/apagando el relé en modo AUTO.
- **MQTT**: podrás encender/apagar el relé desde el tópico `/casa/cmd` (esto actuará como si apretaras el botón manual ON/OFF, pero desde MQTT).
- **Publicación periódica**: la temperatura se enviará cada cierto tiempo al tópico `/casa/temperatura`.

Solo me falta que me indiques:
1. **Nombre y clave de tu WiFi** (puedes poner datos de prueba si prefieres).
2. **Dirección del bróker MQTT** (por ejemplo, test.mosquitto.org).
3. **Intervalo de publicación de temperatura** (por ejemplo, cada 10 segundos).
4. **El relé es activo alto (HIGH lo enciende) o activo bajo (LOW lo enciende)?**

Con esos datos, te hago la modificación lista para usar.

---

**User**

que envie los datos de temperatura al broker cada 1 segundo

---

**Cursor**

¡Perfecto! Solo me faltan estos dos datos para dejar el código listo:

1. **Nombre de tu red WiFi (SSID)**
2. **Contraseña de tu red WiFi**
3. **Dirección del bróker MQTT** (por ejemplo, test.mosquitto.org)
4. **¿El relé es activo alto (HIGH lo enciende) o activo bajo (LOW lo enciende)?**

¿Quieres que use datos de ejemplo para WiFi y el bróker, o me los proporcionas?  
Apenas me confirmes esto, te entrego el código modificado para que envíe la temperatura cada 1 segundo y cumpla con toda la lógica que pediste.

---

**User**

modiifca el codigo asi deja los datos de generico. que el rele sea activo en alto

---

**Cursor**

```cpp
// ... existing code ...
#include <WiFi.h>
#include <PubSubClient.h>

// --- Configuración WiFi y MQTT ---
const char* ssid = "TU_SSID"; // Cambia por tu SSID
const char* password = "TU_PASSWORD"; // Cambia por tu contraseña
const char* mqtt_server = "broker.hivemq.com"; // Cambia por tu broker si es necesario
const int mqtt_port = 1883;

WiFiClient espClient;
PubSubClient client(espClient);
unsigned long lastTempPublish = 0;
const unsigned long tempPublishInterval = 1000; // 1 segundo

// --- MQTT topics ---
const char* topic_cmd = "/casa/cmd";
const char* topic_temp = "/casa/temperatura";

// --- Prototipos extra ---
void setup_wifi();
void reconnect_mqtt();
void callback(char* topic, byte* payload, unsigned int length);
void publicar_temperatura();
void procesar_comando_mqtt(const String& cmd);

// ... existing code ...
void setup() {
  Serial.begin(115200);
  pinMode(PIN_BTN1, INPUT_PULLUP);
  pinMode(PIN_BTN2, INPUT_PULLUP);
  pinMode(PIN_RELAY, OUTPUT);
  pinMode(PIN_BUZZER, OUTPUT);
  digitalWrite(PIN_RELAY, LOW);
  eventQueue = xQueueCreate(10, sizeof(Event)); // Cola de 10 eventos
  xTaskCreatePinnedToCore(tarea_lectura, "Lectura", 2048, NULL, 1, NULL, 1);

  // --- WiFi y MQTT ---
  setup_wifi();
  client.setServer(mqtt_server, mqtt_port);
  client.setCallback(callback);
}

void loop() {
  // --- MQTT ---
  if (!client.connected()) {
    reconnect_mqtt();
  }
  client.loop();

  // Publicar temperatura cada 1 segundo
  unsigned long now = millis();
  if (now - lastTempPublish > tempPublishInterval) {
    lastTempPublish = now;
    publicar_temperatura();
  }

  Event evt;
  if (xQueueReceive(eventQueue, &evt, pdMS_TO_TICKS(100))) {
    fsm(evt);
  }
  vTaskDelay(100 / portTICK_PERIOD_MS);
}
// ... existing code ...
// --- WiFi y MQTT ---
void setup_wifi() {
  delay(10);
  Serial.println();
  Serial.print("Conectando a ");
  Serial.println(ssid);
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("");
  Serial.println("WiFi conectado");
  Serial.println(WiFi.localIP());
}

void reconnect_mqtt() {
  // Loop hasta que se reconecte
  while (!client.connected()) {
    Serial.print("Intentando conexión MQTT...");
    String clientId = "ESP32Client-";
    clientId += String(random(0xffff), HEX);
    if (client.connect(clientId.c_str())) {
      Serial.println("conectado");
      client.subscribe(topic_cmd);
    } else {
      Serial.print("falló, rc=");
      Serial.print(client.state());
      Serial.println(" intentando de nuevo en 5 segundos");
      delay(5000);
    }
  }
}

void callback(char* topic, byte* payload, unsigned int length) {
  String msg;
  for (unsigned int i = 0; i < length; i++) {
    msg += (char)payload[i];
  }
  if (String(topic) == topic_cmd) {
    procesar_comando_mqtt(msg);
  }
}

void procesar_comando_mqtt(const String& cmd) {
  // El comando MQTT actúa como si se presionara el botón manual ON/OFF
  if (cmd == "ON") {
    current_state = MANUAL_ON;
    encender_rele();
    log_estado("Ventilador ENCENDIDO (MQTT)");
  } else if (cmd == "OFF") {
    current_state = MANUAL_OFF;
    apagar_rele();
    log_estado("Ventilador APAGADO (MQTT)");
  } else if (cmd == "AUTO") {
    current_state = AUTO;
    apagar_rele();
    log_estado("Cambiando a modo AUTOMATICO (MQTT)");
  }
}

void publicar_temperatura() {
  char tempStr[16];
  dtostrf(temperatura, 4, 2, tempStr);
  client.publish(topic_temp, tempStr);
}
// ... existing code ...
// Encender y apagar relé con melodía
void encender_rele() {
  if (!relay_on) {
    digitalWrite(PIN_RELAY, HIGH); // Activo en alto
    relay_on = true;
    xTaskCreatePinnedToCore(tarea_melodia, "Melodia", 2048, NULL, 1, NULL, 0);
  }
}
void apagar_rele() {
  digitalWrite(PIN_RELAY, LOW); // Apaga
  relay_on = false;
}
// ... existing code ...
```

Listo, el código ahora cumple con todos tus requisitos:

- Se conecta a WiFi y a un bróker MQTT (datos genéricos, puedes cambiarlos fácilmente).
- Se suscribe al tópico `/casa/cmd` y responde a los comandos `"ON"`, `"OFF"` y `"AUTO"` (puedes enviar estos mensajes desde cualquier cliente MQTT).
- El control manual (botones) y automático (temperatura) siguen funcionando junto al control MQTT.
- Publica la temperatura cada 1 segundo en el tópico `/casa/temperatura`.
- El relé es activo en alto (HIGH lo enciende).

**Recuerda:**  
- Debes instalar las librerías `WiFi.h` y `PubSubClient` en tu entorno de Arduino.
- Cambia los datos de WiFi y broker por los tuyos reales cuando lo vayas a usar.

¿Quieres que te explique cómo probarlo o necesitas algún ajuste más?

