#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>

// === Pines ===
const int pinTemp = 34;
const int pinRelay = 26;
const int pinButtonMode = 25;
const int pinButtonManual = 33;
const int pinBuzzer = 27;

// === Estados y eventos ===
enum Estado { INICIAL, MANUAL, AUTOMATICO };
enum Evento {
  NINGUNO,
  CAMBIO_MODO,
  CAMBIO_MODO_MQTT,
  PULSADOR_MANUAL,
  TEMP_ALTA,
  TEMP_BAJA
};

// === Variables globales ===
Estado estadoActual = INICIAL;
Evento evento = NINGUNO;
bool releEncendido = false;
QueueHandle_t colaEventos;
TaskHandle_t handleMelodia;

// === WiFi y MQTT ===
const char* ssid = "Wokwi-GUEST";
const char* password = "";
const char* mqtt_server = "broker.emqx.io";
WiFiClient espClient;
PubSubClient client(espClient);
unsigned long lastSendTime = 0;
const unsigned long intervalSend = 5000; // cada 5 segundos

// === Prototipos ===
void tareaEntradas(void *pvParameters);
void tareaControl(void *pvParameters);
void tareaMelodia(void *pvParameters);
void tareaMQTT(void *pvParameters);
Evento get_input();
void logEstado(const char*);
void reconnect();
void callback(char* topic, byte* message, unsigned int length);

// === Setup ===
void setup() {
  Serial.begin(115200);

  pinMode(pinTemp, INPUT);
  pinMode(pinRelay, OUTPUT);
  pinMode(pinButtonMode, INPUT_PULLUP);
  pinMode(pinButtonManual, INPUT_PULLUP);
  pinMode(pinBuzzer, OUTPUT);
  digitalWrite(pinRelay, LOW);

  // Conexión WiFi
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    delay(500);
    Serial.print(".");
  }
  Serial.println("\nWiFi conectado");

  client.setServer(mqtt_server, 1883);
  client.setCallback(callback);

  colaEventos = xQueueCreate(10, sizeof(Evento));

  xTaskCreatePinnedToCore(tareaEntradas, "Entradas", 2048, NULL, 1, NULL, 1);
  xTaskCreatePinnedToCore(tareaControl, "Control", 4096, NULL, 1, NULL, 1);
  xTaskCreatePinnedToCore(tareaMQTT, "MQTT", 4096, NULL, 1, NULL, 1);
}

// === Loop vacío ===
void loop() {}

// === Tarea MQTT ===
void tareaMQTT(void *pvParameters) {
  for (;;) {
    if (!client.connected()) {
      reconnect();
    }
    client.loop();

    unsigned long now = millis();
    if (now - lastSendTime > intervalSend) {
      int lectura = analogRead(pinTemp);
      float temp = (lectura * 3.3 / 4095.0) * 100.0;

      char tempStr[16];
      dtostrf(temp, 4, 2, tempStr);
      client.publish("sensor/temperatura", tempStr);

      lastSendTime = now;
    }

    vTaskDelay(100 / portTICK_PERIOD_MS);
  }
}

void reconnect() {
  while (!client.connected()) {
    if (client.connect("ESP32Client")) {
      client.subscribe("/control/modo");
    } else {
      delay(500);
    }
  }
}

// === Callback al recibir MQTT ===
void callback(char* topic, byte* message, unsigned int length) {
  message[length] = '\0'; // Null terminamos el mensaje
  String msg = String((char*)message);

  if (String(topic) == "/control/modo") {
    if (msg == "manual" || msg == "auto") {
      Evento e = CAMBIO_MODO_MQTT;
      xQueueSend(colaEventos, &e, portMAX_DELAY);
    }
  }
}

// === Tarea Entradas ===
void tareaEntradas(void *pvParameters) {
  for (;;) {
    Evento nuevoEvento = get_input();
    if (nuevoEvento != NINGUNO) {
      xQueueSend(colaEventos, &nuevoEvento, portMAX_DELAY);
    }
    vTaskDelay(200 / portTICK_PERIOD_MS);
  }
}

// === Lógica de lectura de entradas ===
Evento get_input() {
  static bool modoAnt = HIGH;
  static bool manualAnt = HIGH;

  bool modoNow = digitalRead(pinButtonMode);
  bool manualNow = digitalRead(pinButtonManual);

  if (modoAnt == HIGH && modoNow == LOW) {
    modoAnt = modoNow;
    return CAMBIO_MODO;
  }
  modoAnt = modoNow;

  if (manualAnt == HIGH && manualNow == LOW) {
    manualAnt = manualNow;
    return PULSADOR_MANUAL;
  }
  manualAnt = manualNow;

  int lectura = analogRead(pinTemp);
  float temp = (lectura * 3.3 / 4095.0) * 100.0;

  if (temp > 30.0) return TEMP_ALTA;
  else return TEMP_BAJA;
}

// === Máquina de estados ===
void tareaControl(void *pvParameters) {
  Evento eventoActual;

  for (;;) {
    if (xQueueReceive(colaEventos, &eventoActual, portMAX_DELAY)) {
      evento = eventoActual;

      switch (estadoActual) {
        case INICIAL:
          if (evento == CAMBIO_MODO || evento == CAMBIO_MODO_MQTT) {
            estadoActual = MANUAL;
            logEstado("→ MODO MANUAL");
          }
          break;

        case MANUAL:
          switch (evento) {
            case CAMBIO_MODO:
            case CAMBIO_MODO_MQTT:
              estadoActual = AUTOMATICO;
              logEstado("→ MODO AUTOMATICO");
              break;

            case PULSADOR_MANUAL:
              releEncendido = !releEncendido;
              digitalWrite(pinRelay, releEncendido ? HIGH : LOW);
              logEstado(releEncendido ? "VENTILADOR ON (MANUAL)" : "VENTILADOR OFF (MANUAL)");
              if (releEncendido)
                xTaskCreate(tareaMelodia, "Melodia", 2048, NULL, 1, &handleMelodia);
              break;

            default:
              break;
          }
          break;

        case AUTOMATICO:
          switch (evento) {
            case CAMBIO_MODO:
            case CAMBIO_MODO_MQTT:
              estadoActual = MANUAL;
              logEstado("→ MODO MANUAL");
              break;

            case TEMP_ALTA:
              releEncendido = true;
              digitalWrite(pinRelay, HIGH);
              logEstado("VENTILADOR ON (AUTO)");
              xTaskCreate(tareaMelodia, "Melodia", 2048, NULL, 1, &handleMelodia);
              break;

            case TEMP_BAJA:
              releEncendido = false;
              digitalWrite(pinRelay, LOW);
              logEstado("VENTILADOR OFF (AUTO)");
              break;

            default:
              break;
          }
          break;
      }
    }
  }
}

// === Tarea Melodía ===
void tareaMelodia(void *pvParameters) {
  int notas[] = {262, 294, 330, 392};
  int duracion = 200;

  for (int i = 0; i < 4; i++) {
    tone(pinBuzzer, notas[i], duracion);
    vTaskDelay((duracion + 50) / portTICK_PERIOD_MS);
  }

  noTone(pinBuzzer);
  vTaskDelete(NULL);
}

// === Logging por Serial ===
void logEstado(const char* mensaje) {
  Serial.println(mensaje);
}
