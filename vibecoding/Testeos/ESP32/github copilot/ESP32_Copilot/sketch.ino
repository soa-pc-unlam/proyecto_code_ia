#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>

/*
 * Control de casa inteligente con ESP32
 * Requisitos:
 *  - Sensor de temperatura analógico
 *  - Dos pulsadores
 *  - Relé para ventilador
 *  - Buzzer pasivo (melodía)
 *  - FreeRTOS, timers para buzzer
 *  - Máquina de estados con switches anidados
 *  - Logs para cambios de estado
 */

// Pines (ajusta según esquema)
#define PIN_TEMP_SENSOR 34   // ADC1_CH6
#define PIN_BTN_MODE    25   // Pulsador 1: Cambia modo
#define PIN_BTN_MANUAL  33   // Pulsador 2: Manual ON/OFF
#define PIN_RELAY       26   // Relé Ventilador
#define PIN_BUZZER      27   // Buzzer pasivo

// Configuración
#define TEMP_UMBRAL     28   // Umbral de temperatura para activar ventilador (°C)
#define MELODIA_NOTA    1000 // Frecuencia de la nota del buzzer (Hz)
#define MELODIA_DUR     300  // Duración nota (ms)

// Estados de la máquina
enum State { 
  ST_IDLE,        // Espera, nada encendido
  ST_MANUAL_ON,   // Manual: ventilador ON
  ST_AUTO_ON      // Automático: ventilador ON
};
enum Mode {
  MANUAL,
  AUTOMATICO
};
State current_state = ST_IDLE;
Mode  current_mode  = AUTOMATICO;

// Eventos
enum Event {
  EVT_NONE,
  EVT_MODE_CHANGE,
  EVT_BTN_MANUAL_PRESS,
  EVT_TEMP_HIGH,
  EVT_TEMP_LOW
};

// Variables globales compartidas
volatile bool relay_on = false;
volatile int temperatura_actual = 0;

// Prototipos
void tarea_sensores(void* pvParameters);
void tarea_actuadores(void* pvParameters);
void tarea_melodia(void* pvParameters);
int get_input(Event* evt);
void log_estado(const char* msg);
void fsm();

// ---- SETUP PRINCIPAL ----
void setup() {
  Serial.begin(115200);
  pinMode(PIN_TEMP_SENSOR, INPUT);
  pinMode(PIN_BTN_MODE, INPUT_PULLUP);
  pinMode(PIN_BTN_MANUAL, INPUT_PULLUP);
  pinMode(PIN_RELAY, OUTPUT);
  pinMode(PIN_BUZZER, OUTPUT);

  digitalWrite(PIN_RELAY, LOW);

  // Iniciar tareas FreeRTOS
  xTaskCreatePinnedToCore(tarea_sensores, "Sensores", 2048, NULL, 2, NULL, 1);
  xTaskCreatePinnedToCore(tarea_actuadores, "Actuadores", 2048, NULL, 1, NULL, 0);
  xTaskCreatePinnedToCore(tarea_melodia, "Melodia", 2048, NULL, 1, NULL, 1);

  delay(1000);
  log_estado("Sistema iniciado.");
}

// ---- LOOP PRINCIPAL ----
void loop() {
  fsm(); // Ejecuta la máquina de estados en el loop principal
  delay(100); // Da tiempo a otras tareas
}

// ---- FUNCIÓN PRINCIPAL DE MÁQUINA DE ESTADOS ----
void fsm() {
  static Event input_evt = EVT_NONE;

  // Obtener evento
  get_input(&input_evt);

  switch(current_state) {
    case ST_IDLE:
      switch(input_evt) {
        case EVT_MODE_CHANGE:
          current_mode = (current_mode == MANUAL) ? AUTOMATICO : MANUAL;
          log_estado(current_mode == MANUAL ? "Modo manual" : "Modo automático");
          input_evt = EVT_NONE;
          break;
        case EVT_BTN_MANUAL_PRESS:
          if(current_mode == MANUAL) {
            current_state = ST_MANUAL_ON;
            log_estado("Ventilador encendido (Manual)");
          }
          input_evt = EVT_NONE;
          break;
        case EVT_TEMP_HIGH:
          if(current_mode == AUTOMATICO) {
            current_state = ST_AUTO_ON;
            log_estado("Ventilador encendido (Automático)");
          }
          input_evt = EVT_NONE;
          break;
        default:
          break;
      }
      break;

    case ST_MANUAL_ON:
      switch(input_evt) {
        case EVT_MODE_CHANGE:
          current_mode = (current_mode == MANUAL) ? AUTOMATICO : MANUAL;
          current_state = ST_IDLE;
          log_estado("Modo cambiado. Ventilador apagado.");
          input_evt = EVT_NONE;
          break;
        case EVT_BTN_MANUAL_PRESS:
          current_state = ST_IDLE;
          log_estado("Ventilador apagado (Manual)");
          input_evt = EVT_NONE;
          break;
        default:
          break;
      }
      break;

    case ST_AUTO_ON:
      switch(input_evt) {
        case EVT_MODE_CHANGE:
          current_mode = (current_mode == MANUAL) ? AUTOMATICO : MANUAL;
          current_state = ST_IDLE;
          log_estado("Modo cambiado. Ventilador apagado.");
          input_evt = EVT_NONE;
          break;
        case EVT_TEMP_LOW:
          current_state = ST_IDLE;
          log_estado("Ventilador apagado (Automático)");
          input_evt = EVT_NONE;
          break;
        default:
          break;
      }
      break;
  }
}

// ---- OBTENCIÓN DE EVENTOS Y LECTURA DE SENSORES ----
int get_input(Event* evt) {
  static bool ultimo_btn_mode = HIGH, ultimo_btn_manual = HIGH;
  bool btn_mode = digitalRead(PIN_BTN_MODE);
  bool btn_manual = digitalRead(PIN_BTN_MANUAL);

  // Leer sensor de temperatura (simulación simple)
  int raw = analogRead(PIN_TEMP_SENSOR);
  temperatura_actual = map(raw, 0, 4095, 0, 50); // Ajusta según sensor

  // Cambio de modo
  if(btn_mode == LOW && ultimo_btn_mode == HIGH) {
    *evt = EVT_MODE_CHANGE;
  }
  // Pulsador manual
  else if(btn_manual == LOW && ultimo_btn_manual == HIGH) {
    *evt = EVT_BTN_MANUAL_PRESS;
  }
  // Temperatura alta
  else if(current_mode == AUTOMATICO && temperatura_actual > TEMP_UMBRAL && current_state == ST_IDLE) {
    *evt = EVT_TEMP_HIGH;
  }
  // Temperatura baja
  else if(current_mode == AUTOMATICO && temperatura_actual <= TEMP_UMBRAL && current_state == ST_AUTO_ON) {
    *evt = EVT_TEMP_LOW;
  }
  else {
    *evt = EVT_NONE;
  }

  ultimo_btn_mode = btn_mode;
  ultimo_btn_manual = btn_manual;
  return *evt;
}

// ---- TAREA: SENSORES ----
void tarea_sensores(void* pvParameters) {
  while(true) {
    // Sensores leídos en get_input por restricción
    vTaskDelay(100 / portTICK_PERIOD_MS);
  }
}

// ---- TAREA: ACTUADORES ----
void tarea_actuadores(void* pvParameters) {
  static State last_state = ST_IDLE;
  while(true) {
    if(current_state == ST_MANUAL_ON || current_state == ST_AUTO_ON) {
      if(!relay_on) {
        relay_on = true;
        digitalWrite(PIN_RELAY, HIGH);
        xTaskNotifyGive(xTaskGetHandle("Melodia")); // Lanza melodía
      }
    } else {
      relay_on = false;
      digitalWrite(PIN_RELAY, LOW);
    }
    last_state = current_state;
    vTaskDelay(50 / portTICK_PERIOD_MS);
  }
}

// ---- TAREA: MELODÍA EN BUZZER ----
void tarea_melodia(void* pvParameters) {
  while(true) {
    ulTaskNotifyTake(pdTRUE, portMAX_DELAY); // Espera trigger
    tone(PIN_BUZZER, MELODIA_NOTA, MELODIA_DUR);
    vTaskDelay(MELODIA_DUR / portTICK_PERIOD_MS);
    noTone(PIN_BUZZER);
  }
}

// ---- LOGS DE ESTADO ----
void log_estado(const char* msg) {
  Serial.print("[LOG] ");
  Serial.print(msg);
  Serial.print(" | Estado: ");
  switch(current_state) {
    case ST_IDLE: Serial.println("IDLE"); break;
    case ST_MANUAL_ON: Serial.println("MANUAL_ON"); break;
    case ST_AUTO_ON: Serial.println("AUTO_ON"); break;
  }
}