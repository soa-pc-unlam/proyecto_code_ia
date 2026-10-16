// Grupo M3 de Sistemas Operativos Avanzados
// ===================== LIBRERIAS =====================

#include <OneWire.h>
#include <DallasTemperature.h>
#include <Wire.h>
#include "rgb_lcd.h"

#include <freertos/FreeRTOS.h>
#include <freertos/task.h>
#include <freertos/queue.h>
#include <freertos/semphr.h>

#include <WiFi.h>
#include <PubSubClient.h> // MQTT

// ===================== PINES =====================

#define RED_PIN       5
#define GREEN_PIN     4
#define BLUE_PIN      2

#define BUZZER_PIN    15

#define ONE_WIRE_BUS  18

#define POT_PIN       34

// BOTON EN GPIO 32 CON LOGICA DE PULLUP
#define BUTTON_PIN    32

// ===================== LCD =====================
rgb_lcd lcd;

// ===================== SENSOR TEMPERATURA =====================
OneWire oneWire(ONE_WIRE_BUS);
DallasTemperature sensors(&oneWire);

// ===================== ESTADOS =====================
enum States
{
  IDLE,
  Monitoring,
  Alarm
};

// ===================== EVENTOS =====================
enum Event
{
  EV_NONE,

  EV_BUTTON_SHORT,
  EV_BUTTON_LONG,

  EV_TEMP_LOW,
  EV_TEMP_MEDIUM,
  EV_TEMP_HIGH,

  EV_TEMP_REACHED,
  EV_TEMP_PASS,

  EV_TIMEOUT
};

// ===================== RTOS =====================

QueueHandle_t colaEventos;

// Mutex para acceder a variables globales
SemaphoreHandle_t mutexDatos;

// Mutex exclusivo para el LCD
SemaphoreHandle_t mutexLCD;

// ===================== VARIABLES GLOBALES =====================

Event currentEvent = EV_NONE;

States systemState = IDLE;

float tempActual = NAN;
float setTemp    = NAN;

int lecturaAnterior = -1;

// Texto de la linea 0 del LCD
char modoDisplay[17] = "ThermoGuard";

// Texto de la linea 1 del LCD
char linea1Display[17] = "Presione boton";

// Flag para saber si el display debe mostrar temperatura en línea 1
bool mostrarTemp = false;

// ===================== CREDENCIALES WIFI Y MQTT =====================

const char* ssid = "iPhone de Maximo";         
const char* password = "maxirojo";   
const char* mqtt_server = "172.20.10.8"; // Broker

WiFiClient espClient;
PubSubClient client(espClient);

// ===================== TOPICS MQTT =====================
const char* topic_set_modo     = "grupoM3/set/modo";
const char* topic_pub_temp     = "grupoM3/sensor/temperatura";
const char* topic_pub_estado   = "grupoM3/sensor/estado";
const char* topic_sub_cmd      = "grupoM3/comando";

// ===================== CONFIGURACIONES =====================
// Potenciometro
int defUmbralLOW    = 1365;
int defUmbralMedium = 2730;

// Umbrales de temperatura de la bebida
float defTempLow    = 15.0;
float defTempMedium = 1.0;
float defTempHigh   = -1.0;

float defUmbralTolerancia = 0.5;

bool timerAlarmaActivo = false;

unsigned long tiempoInicioAlarma = 0;
unsigned long tiempoBuzzer = 10000;

int defValorErrorSensorTemp = -127; 
int defSerial = 9600;
int defUmbralButtonShort = 1000;
int defMargenToleranciaPotenciometro = 75;

// Delays tasks
int defDelayTaskBoton          = 20;
int defDelayTaskPotenciometro  = 100;
int defDelayTaskTimer          = 100;
int defDelayTaskTemperatura    = 1000;
int defDelayTaskDisplay        = 1000;

// =====================================================
// ===================== FUNCIONES =====================
// =====================================================

// ===================== LED RGB =====================

void setColor(int r, int g, int b)
{
  digitalWrite(RED_PIN, r);
  digitalWrite(GREEN_PIN, g);
  digitalWrite(BLUE_PIN, b);
}

// ===================== TASK BOTON =====================

void taskBoton(void *pvParameter)
{
  bool estadoAnterior = HIGH;
  unsigned long tiempoPresionado = 0;

  while(1)
  {
    bool estadoActual = digitalRead(BUTTON_PIN);

    if (estadoAnterior == HIGH && estadoActual == LOW)
    {
      tiempoPresionado = millis();
      Serial.println("Boton presionado");
    }

    if (estadoAnterior == LOW && estadoActual == HIGH)
    {
      unsigned long duracion = millis() - tiempoPresionado;
      Event evt;

      if (duracion < defUmbralButtonShort)
      {
        evt = EV_BUTTON_SHORT;
        Serial.println("EV_BUTTON_SHORT");
      }
      else
      {
        evt = EV_BUTTON_LONG;
        Serial.println("EV_BUTTON_LONG");
      }

      xQueueSend(colaEventos, &evt, 0);
    }

    estadoAnterior = estadoActual;
    vTaskDelay(pdMS_TO_TICKS(defDelayTaskBoton));
  }
}

// ===================== TASK POTENCIOMETRO =====================

void taskPotenciometro(void *pvParameter)
{
  while (1)
  {
    int lectura = analogRead(POT_PIN);

    if (lecturaAnterior == -1 || abs(lectura - lecturaAnterior) > defMargenToleranciaPotenciometro)
    {
      Event evt;

      if (lectura < defUmbralLOW) evt = EV_TEMP_LOW;
      else if (lectura < defUmbralMedium) evt = EV_TEMP_MEDIUM;
      else evt = EV_TEMP_HIGH;

      xQueueSend(colaEventos, &evt, 0);
      lecturaAnterior = lectura;
      Serial.println("Nueva lectura potenciometro: " + String(lectura));
    }
    vTaskDelay(pdMS_TO_TICKS(defDelayTaskPotenciometro));
  }
}

// ===================== TASK TEMPERATURA =====================

void taskTemperatura(void *pvParameter)
{
  static bool reachedEnviado = false;
  static bool passEnviado    = false;

  while(1)
  {
    sensors.requestTemperatures();
    float lectura = sensors.getTempCByIndex(0);

    if (lectura == defValorErrorSensorTemp)
    {
      Serial.println("Error: DS18B20 desconectado");
      vTaskDelay(pdMS_TO_TICKS(defDelayTaskTemperatura));
      continue;
    }

    xSemaphoreTake(mutexDatos, portMAX_DELAY);
    tempActual   = lectura;
    float objetivo = setTemp;
    States estado  = systemState;
    xSemaphoreGive(mutexDatos);

    if ((estado == Monitoring || estado == Alarm) && !isnan(objetivo))
    {
      if (abs(lectura - objetivo) <= defUmbralTolerancia)
      {
        if (!reachedEnviado)
        {
          Event evt = EV_TEMP_REACHED;
          xQueueSend(colaEventos, &evt, 0);
          reachedEnviado = true; passEnviado = false;
        }
      }
      else if (lectura < (objetivo - defUmbralTolerancia))
      {
        if (!passEnviado)
        {
          Event evt = EV_TEMP_PASS;
          xQueueSend(colaEventos, &evt, 0);
          passEnviado = true; reachedEnviado = false;
        }
      }
      else
      {
        reachedEnviado = false; passEnviado = false;
      }
    }
    else
    {
      reachedEnviado = false; passEnviado = false;
    }
    vTaskDelay(pdMS_TO_TICKS(defDelayTaskTemperatura));
  }
}

// ===================== TASK DISPLAY =====================

void taskDisplay(void *pvParameter)
{
  while(1)
  {
    xSemaphoreTake(mutexDatos, portMAX_DELAY);
    float  temp     = tempActual;
    float  objetivo = setTemp;
    States estado   = systemState;
    char   modo[17];
    char   linea1[17];
    bool   showTemp = mostrarTemp;
    strncpy(modo,   modoDisplay,   16);
    strncpy(linea1, linea1Display, 16);
    modo[16]   = '\0';
    linea1[16] = '\0';
    xSemaphoreGive(mutexDatos);

    xSemaphoreTake(mutexLCD, portMAX_DELAY);

    if (estado == Monitoring && showTemp)
    {
      lcd.setCursor(0, 0); lcd.print(modo);
      for (int i = strlen(modo); i < 16; i++) lcd.print(" ");
      lcd.setCursor(0, 1);
      lcd.print("TO:"); lcd.print(objetivo, 1);
      lcd.print(" TA:"); lcd.print(temp, 1);
      lcd.print("   ");
    }
    else if (estado == Monitoring && !showTemp)
    {
      lcd.setCursor(0, 0); lcd.print(modo);
      for (int i = strlen(modo); i < 16; i++) lcd.print(" ");
      lcd.setCursor(0, 1); lcd.print("Seleccione modo ");
    }
    else if (estado == IDLE && !showTemp)
    {
      lcd.setCursor(0, 0); lcd.print(modo);
      lcd.setCursor(0, 1); lcd.print("Grupo M3 SOA    ");
    }

    xSemaphoreGive(mutexLCD);
    vTaskDelay(pdMS_TO_TICKS(defDelayTaskDisplay));
  }
}

// ===================== TASK TIMER =====================

void taskTimer(void *pvParameter)
{
  while (1)
  {
    bool          alarmaActiva;
    unsigned long inicio;

    xSemaphoreTake(mutexDatos, portMAX_DELAY);
    alarmaActiva = timerAlarmaActivo;
    inicio       = tiempoInicioAlarma;
    xSemaphoreGive(mutexDatos);

    if (alarmaActiva)
    {
      if (millis() - inicio >= tiempoBuzzer)
      {
        Event evt = EV_TIMEOUT;
        xQueueSend(colaEventos, &evt, 0);
      }
    }
    vTaskDelay(pdMS_TO_TICKS(defDelayTaskTimer));
  }
}

// ===================== TAREA WIFI Y MQTT =====================

// Convierte el estado de la FSM a un string legible para publicar por MQTT
const char* estadoToString(States e)
{
  switch (e)
  {
    case IDLE:       return "IDLE";
    case Monitoring: return "MONITOREANDO";
    case Alarm:      return "ALARMA";
    default:         return "DESCONOCIDO";
  }
}

// Callback: se ejecuta cuando llega un mensaje MQTT desde la APP
void mqttCallback(char* topic, byte* payload, unsigned int length) {
  String mensaje = "";
  for (unsigned int i = 0; i < length; i++) {
    mensaje += (char)payload[i];
  }

  String topicStr = String(topic);

  Serial.print("MQTT [");
  Serial.print(topicStr);
  Serial.print("] -> ");
  Serial.println(mensaje);

  // ---- Comandos ENCENDER / APAGAR ----
  if (topicStr == topic_sub_cmd)
  {
    if (mensaje == "ENCENDER") {
      Event evt = EV_BUTTON_SHORT;
      xQueueSend(colaEventos, &evt, 0);
    }
    else if (mensaje == "APAGAR") {
      Event evt = EV_BUTTON_LONG;
      xQueueSend(colaEventos, &evt, 0);
    }
  }
  // ---- Seleccionar modo de temperatura MSG_MODO_BAJO/MEDIO/ALTO
  else if (topicStr == topic_set_modo)
  {
    Event evt;
    if (mensaje == "BAJO")       evt = EV_TEMP_LOW;
    else if (mensaje == "MEDIO") evt = EV_TEMP_MEDIUM;
    else if (mensaje == "ALTO")  evt = EV_TEMP_HIGH;
    else {
      Serial.println("Valor de modo no reconocido");
      return;
    }
    xQueueSend(colaEventos, &evt, 0);
  }
}

void reconectarMQTT() {
  while (!client.connected()) {
    Serial.print("Intentando conectar a MQTT...");
    // Creamos un ID de cliente aleatorio
    String clientId = "ESP32_GrupoM3_";
    clientId += String(random(0xffff), HEX);

    if (client.connect(clientId.c_str())) {
      Serial.println("¡Conectado!");
      // Nos suscribimos a TODOS los topics que la app puede publicar
      client.subscribe(topic_sub_cmd);
      client.subscribe(topic_set_modo);
    } else {
      Serial.print("Fallo, rc=");
      Serial.print(client.state());
      Serial.println(" Intentando de nuevo en 5s");
      vTaskDelay(pdMS_TO_TICKS(5000));
    }
  }
}

void taskMQTT(void *pvParameter) {
  // 1. Conectar a WiFi
  WiFi.begin(ssid, password);
  while (WiFi.status() != WL_CONNECTED) {
    vTaskDelay(pdMS_TO_TICKS(500));
    Serial.print(".");
  }
  Serial.println("\nWiFi Conectado!");

  // 2. Configurar MQTT
  client.setServer(mqtt_server, 1883);
  client.setCallback(mqttCallback);

  unsigned long lastMsg = 0;

  while(1) {
    if (!client.connected() && WiFi.status() == WL_CONNECTED) {
      reconectarMQTT();
    }
    client.loop();

    // Informar por MQTT temperatura y estado cada 5 segundos
    unsigned long now = millis();
    if (now - lastMsg > 5000) {
      lastMsg = now;

      // Leemos temperatura y estado protegidos por el Mutex
      xSemaphoreTake(mutexDatos, portMAX_DELAY);
      float  tempParaEnviar   = tempActual;
      States estadoParaEnviar = systemState;
      xSemaphoreGive(mutexDatos);

      if (!isnan(tempParaEnviar)) {
        String payload = String(tempParaEnviar, 2);
        client.publish(topic_pub_temp, payload.c_str());
        Serial.println("MQTT: Temperatura publicada -> " + payload);
      }

      const char* estadoStr = estadoToString(estadoParaEnviar);
      client.publish(topic_pub_estado, estadoStr);
      Serial.println("MQTT: Estado publicado -> " + String(estadoStr));
    }

    vTaskDelay(pdMS_TO_TICKS(50));
  }
}

// ===================== ALARMA =====================

void init_alarm()
{
  xSemaphoreTake(mutexDatos, portMAX_DELAY);
  timerAlarmaActivo  = true;
  tiempoInicioAlarma = millis();
  xSemaphoreGive(mutexDatos);
}

void stop_alarm()
{
  xSemaphoreTake(mutexDatos, portMAX_DELAY);
  timerAlarmaActivo = false;
  xSemaphoreGive(mutexDatos);
}

// ===================== GET EVENT =====================

void getEvent()
{
  if (xQueueReceive(colaEventos, &currentEvent, 0) != pdTRUE)
  {
    currentEvent = EV_NONE;
  }
}

// ===================== HELPER =====================
void lcdWriteFSM(int col0, int row0, const char* linea0,
                 int col1, int row1, const char* linea1)
{
  xSemaphoreTake(mutexLCD, portMAX_DELAY);
  lcd.clear();
  lcd.setCursor(col0, row0); lcd.print(linea0);
  lcd.setCursor(col1, row1); lcd.print(linea1);
  xSemaphoreGive(mutexLCD);
}

// =====================================================
// ===================== FSM ===========================
// =====================================================

void stateMachine()
{
  switch(systemState)
  {
    // ===================== IDLE =====================
    case IDLE:
      switch(currentEvent)
      {
        case EV_BUTTON_SHORT:
        {
          xSemaphoreTake(mutexDatos, portMAX_DELAY);
          systemState = Monitoring;
          strncpy(modoDisplay, "ThermoGuard ON  ", 16);
          strncpy(linea1Display, "Seleccione modo ", 16);
          mostrarTemp = false;
          xSemaphoreGive(mutexDatos);

          lcd.setRGB(0, 0, 255);
          setColor(0, 0, 1);
          lcdWriteFSM(0, 0, "ThermoGuard ON", 0, 1, "Seleccione modo ");
        }
        break;
        default: break;
      }
    break;

    // ===================== MONITORING =====================
    case Monitoring:
      switch(currentEvent)
      {
        case EV_NONE: break;

        case EV_TEMP_LOW:
        {
          xSemaphoreTake(mutexDatos, portMAX_DELAY);
          setTemp = defTempLow;
          strncpy(modoDisplay, "Modo: FRIO      ", 16);
          mostrarTemp = true;
          xSemaphoreGive(mutexDatos);
        }
        break;

        case EV_TEMP_MEDIUM:
        {
          xSemaphoreTake(mutexDatos, portMAX_DELAY);
          setTemp = defTempMedium;
          strncpy(modoDisplay, "Modo: MUY FRIO  ", 16);
          mostrarTemp = true;
          xSemaphoreGive(mutexDatos);
        }
        break;

        case EV_TEMP_HIGH:
        {
          xSemaphoreTake(mutexDatos, portMAX_DELAY);
          setTemp = defTempHigh;
          strncpy(modoDisplay, "Modo: HELARSE   ", 16);
          mostrarTemp = true;
          xSemaphoreGive(mutexDatos);
        }
        break;

        case EV_TEMP_REACHED:
        {
          xSemaphoreTake(mutexDatos, portMAX_DELAY);
          systemState = Alarm;
          mostrarTemp = false;
          xSemaphoreGive(mutexDatos);

          lcd.setRGB(0, 255, 0); setColor(0, 1, 0);
          digitalWrite(BUZZER_PIN, LOW);
          init_alarm();
          lcdWriteFSM(0, 0, "Temp alcanzada", 0, 1, "Ya puede tomar");
        }
        break;

        case EV_TEMP_PASS:
        {
          lcd.setRGB(255, 0, 0); setColor(1, 0, 0);
        }
        break;

        case EV_BUTTON_LONG:
        {
          xSemaphoreTake(mutexDatos, portMAX_DELAY);
          systemState = IDLE;
          setTemp     = NAN;
          tempActual  = NAN;
          strncpy(modoDisplay, "ThermoGuard     ", 16);
          strncpy(linea1Display, "Grupo M3 SOA    ", 16);
          mostrarTemp = false;
          xSemaphoreGive(mutexDatos);
          lcd.setRGB(255, 255, 255); setColor(0, 0, 0);
        }
        break;
        default: break;
      }
    break;

    // ===================== ALARM =====================
    case Alarm:
      switch(currentEvent)
      {
        case EV_BUTTON_SHORT:
        {
          stop_alarm();
          digitalWrite(BUZZER_PIN, HIGH);
          lcd.setRGB(0, 255, 0); setColor(0, 1, 0);

          xSemaphoreTake(mutexDatos, portMAX_DELAY);
          systemState = Monitoring;
          mostrarTemp = true;
          xSemaphoreGive(mutexDatos);
        }
        break;

        case EV_TIMEOUT:
        {
          digitalWrite(BUZZER_PIN, HIGH);
          lcd.setRGB(0, 255, 0); setColor(0, 1, 0);

          xSemaphoreTake(mutexDatos, portMAX_DELAY);
          systemState = Monitoring;
          mostrarTemp = true;
          xSemaphoreGive(mutexDatos);
        }
        break;

        case EV_BUTTON_LONG:
        {
          digitalWrite(BUZZER_PIN, HIGH);
          stop_alarm();

          xSemaphoreTake(mutexDatos, portMAX_DELAY);
          setTemp     = NAN;
          tempActual  = NAN;
          strncpy(modoDisplay, "ThermoGuard     ", 16);
          strncpy(linea1Display, "Grupo M3 SOA    ", 16);
          mostrarTemp = false;
          systemState = IDLE;
          xSemaphoreGive(mutexDatos);

          lcd.setRGB(255, 255, 255); setColor(0, 0, 0);
        }
        break;
        default: break;
      }
    break;
  }
}

// =====================================================
// ===================== SETUP =====================
// =====================================================

void setup()
{
  Serial.begin(defSerial);

  pinMode(RED_PIN,   OUTPUT);
  pinMode(GREEN_PIN, OUTPUT);
  pinMode(BLUE_PIN,  OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, HIGH);
  pinMode(BUTTON_PIN, INPUT_PULLUP);

  sensors.begin();
  Wire.begin(21, 22);

  lcd.begin(16, 2);
  lcd.setRGB(255, 255, 255);
  lcd.clear();
  lcd.setCursor(0, 0); lcd.print("ThermoGuard");
  lcd.setCursor(0, 1); lcd.print("Presione boton");

  colaEventos = xQueueCreate(10, sizeof(Event));
  mutexDatos  = xSemaphoreCreateMutex();
  mutexLCD    = xSemaphoreCreateMutex();

  xTaskCreate(taskBoton, "Boton", 2048, NULL, 2, NULL);
  xTaskCreate(taskTemperatura, "Temp", 2048, NULL, 1, NULL);
  xTaskCreate(taskDisplay, "Display", 2048, NULL, 1, NULL);
  xTaskCreate(taskTimer, "Timer", 2048, NULL, 1, NULL);
  xTaskCreate(taskPotenciometro, "Pot", 2048, NULL, 1, NULL);

  // TAREA WIFI/MQTT (Requiere mas stack por las conexiones de red)
  xTaskCreate(taskMQTT, "MQTT", 4096, NULL, 1, NULL);
}

// =====================================================
// ===================== LOOP =====================
// =====================================================

void loop()
{
  getEvent();
  stateMachine();
  vTaskDelay(pdMS_TO_TICKS(50));
}
