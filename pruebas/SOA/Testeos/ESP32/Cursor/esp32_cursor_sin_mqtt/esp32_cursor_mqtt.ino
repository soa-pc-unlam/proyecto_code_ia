#include <Arduino.h>
#include "freertos/FreeRTOS.h"
#include "freertos/queue.h"

// Pines
#define PIN_TEMP 34
#define PIN_BTN1 25
#define PIN_BTN2 26
#define PIN_RELAY 2
#define PIN_BUZZER 4

// Umbrales de temperatura
#define TEMP_ON 28   // °C
#define TEMP_OFF 26  // °C

// Estados
enum State { MANUAL_OFF, MANUAL_ON, AUTO };
State current_state = MANUAL_OFF;

// Eventos
enum Event { NONE, BTN1, BTN2, TEMP_HIGH, TEMP_LOW };

// Variables globales
bool modo_automatico = false;
bool relay_on = false;
float temperatura = 0;
QueueHandle_t eventQueue; // Cola para eventos

// Variables globales para debounce
unsigned long lastDebounceTime1 = 0;
unsigned long lastDebounceTime2 = 0;
const unsigned long debounceDelay = 50; // 50 ms

// Prototipos
void fsm(Event last_event);
Event get_input();
void log_estado(const char* msg);
void tarea_lectura(void* param);
void tarea_melodia(void* param);
void encender_rele();
void apagar_rele();

void setup() {
  Serial.begin(115200);
  pinMode(PIN_BTN1, INPUT_PULLUP);
  pinMode(PIN_BTN2, INPUT_PULLUP);
  pinMode(PIN_RELAY, OUTPUT);
  pinMode(PIN_BUZZER, OUTPUT);
  digitalWrite(PIN_RELAY, LOW);
  eventQueue = xQueueCreate(10, sizeof(Event)); // Cola de 10 eventos
  xTaskCreatePinnedToCore(tarea_lectura, "Lectura", 2048, NULL, 1, NULL, 1);
}

void loop() {
  Event evt;
  if (xQueueReceive(eventQueue, &evt, pdMS_TO_TICKS(100))) {
    fsm(evt);
  }
  vTaskDelay(100 / portTICK_PERIOD_MS);
}

// Máquina de estados con switch anidados
void fsm(Event last_event) {
  switch (current_state) {
    case MANUAL_OFF:
      switch (last_event) {
        case BTN1:
          current_state = AUTO;
          log_estado("Cambiando a modo AUTOMATICO");
          break;
        case BTN2:
          current_state = MANUAL_ON;
          encender_rele();
          log_estado("Ventilador ENCENDIDO (Manual)");
          break;
        default:
          break;
      }
      break;
    case MANUAL_ON:
      switch (last_event) {
        case BTN1:
          current_state = AUTO;
          apagar_rele();
          log_estado("Cambiando a modo AUTOMATICO");
          break;
        case BTN2:
          current_state = MANUAL_OFF;
          apagar_rele();
          log_estado("Ventilador APAGADO (Manual)");
          break;
        default:
          break;
      }
      break;
    case AUTO:
      switch (last_event) {
        case BTN1:
          current_state = MANUAL_OFF;
          apagar_rele();
          log_estado("Cambiando a modo MANUAL");
          break;
        case TEMP_HIGH:
          encender_rele();
          log_estado("Ventilador ENCENDIDO (Auto)");
          break;
        case TEMP_LOW:
          apagar_rele();
          log_estado("Ventilador APAGADO (Auto)");
          break;
        default:
          break;
      }
      break;
  }
}

// Lectura de entradas y eventos
Event get_input() {
    static bool btn1_last = HIGH, btn2_last = HIGH;
    static bool temp_high_last = false;
    static bool temp_low_last = false;
    bool btn1 = digitalRead(PIN_BTN1) == LOW;
    bool btn2 = digitalRead(PIN_BTN2) == LOW;

    // Leer temperatura
    int raw = analogRead(PIN_TEMP);
    temperatura = (raw / 4095.0) * 3.3 * 100; // Ajusta según tu sensor
    Serial.println(temperatura);
    unsigned long now = millis();
    delay(100);

    // Pulsador 1: solo flanco de bajada y debounce
    if (!btn1 && btn1_last && (now - lastDebounceTime1 > debounceDelay)) {
        lastDebounceTime1 = now;
        btn1_last = btn1;
        return BTN1;
    }
    btn1_last = btn1;

    // Pulsador 2: solo flanco de bajada y debounce
    if (!btn2 && btn2_last && (now - lastDebounceTime2 > debounceDelay)) {
        lastDebounceTime2 = now;
        btn2_last = btn2;
        return BTN2;
    }
    btn2_last = btn2;

    // Solo generar evento cuando cruza el umbral (flanco)
    if (current_state == AUTO) {
        if (temperatura > TEMP_ON && !temp_high_last) {
            temp_high_last = true;
            temp_low_last = false;
            return TEMP_HIGH;
        }
        if (temperatura < TEMP_OFF && !temp_low_last) {
            temp_low_last = true;
            temp_high_last = false;
            return TEMP_LOW;
        }
    } else {
        // Resetear los flags cuando no está en automático
        temp_high_last = false;
        temp_low_last = false;
    }
    return NONE;
}

// Tarea FreeRTOS para leer sensores y actualizar eventos
void tarea_lectura(void* param) {
  while (1) {
    Event evt = get_input();
    if (evt != NONE) {
      xQueueSend(eventQueue, &evt, 0);
    }
    vTaskDelay(50 / portTICK_PERIOD_MS);
  }
}

// Encender y apagar relé con melodía
void encender_rele() {
  if (!relay_on) {
    digitalWrite(PIN_RELAY, HIGH);
    relay_on = true;
    xTaskCreatePinnedToCore(tarea_melodia, "Melodia", 2048, NULL, 1, NULL, 0);
  }
}
void apagar_rele() {
  digitalWrite(PIN_RELAY, LOW);
  relay_on = false;
}

// Tarea FreeRTOS para reproducir melodía
void tarea_melodia(void* param) {
  int notes[] = {262, 294, 330, 349, 392, 440, 494, 523};
  for (int i = 0; i < 8; i++) {
    tone(PIN_BUZZER, notes[i], 150);
    vTaskDelay(200 / portTICK_PERIOD_MS);
  }
  noTone(PIN_BUZZER);
  vTaskDelete(NULL);
}

// Log de cambios de estado
void log_estado(const char* msg) {
 // Serial.println(msg);
}