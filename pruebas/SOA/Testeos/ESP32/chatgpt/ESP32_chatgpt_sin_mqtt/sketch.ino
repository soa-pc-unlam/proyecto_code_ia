#include <Arduino.h>

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
  PULSADOR_MANUAL,
  TEMP_ALTA,
  TEMP_BAJA
};

// === Variables globales ===
Estado estadoActual = INICIAL;
Evento evento = NINGUNO;
bool releEncendido = false;

// === RTOS ===
QueueHandle_t colaEventos;
TaskHandle_t handleMelodia;  // Renombrado para evitar conflicto con función

// === Prototipos ===
void tareaEntradas(void *pvParameters);
void tareaControl(void *pvParameters);
void tareaMelodia(void *pvParameters);  // Esta es la función, sin conflicto
Evento get_input();
void logEstado(const char*);

// === Setup ===
void setup() {
  Serial.begin(115200);

  pinMode(pinTemp, INPUT);
  pinMode(pinRelay, OUTPUT);
  pinMode(pinButtonMode, INPUT_PULLUP);
  pinMode(pinButtonManual, INPUT_PULLUP);
  pinMode(pinBuzzer, OUTPUT);
  digitalWrite(pinRelay, LOW);

  colaEventos = xQueueCreate(10, sizeof(Evento));

  xTaskCreatePinnedToCore(tareaEntradas, "Entradas", 2048, NULL, 1, NULL, 1);
  xTaskCreatePinnedToCore(tareaControl, "Control", 4096, NULL, 1, NULL, 1);
}

// === Loop vacío ===
void loop() {}

// === Tarea de entrada (produce eventos) ===
void tareaEntradas(void *pvParameters) {
  for (;;) {
    Evento nuevoEvento = get_input();
    if (nuevoEvento != NINGUNO) {
      xQueueSend(colaEventos, &nuevoEvento, portMAX_DELAY);
    }
    vTaskDelay(200 / portTICK_PERIOD_MS);
  }
}

// === Función que detecta eventos y lee sensores ===
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
  float temp = (lectura * 3.3 / 4095.0) * 100.0; // LM35
  if (temp > 30.0) return TEMP_ALTA;
  else return TEMP_BAJA;
}

// === Tarea que ejecuta la máquina de estados ===
void tareaControl(void *pvParameters) {
  Evento eventoActual;

  for (;;) {
    if (xQueueReceive(colaEventos, &eventoActual, portMAX_DELAY)) {
      evento = eventoActual;

      switch (estadoActual) {
        case INICIAL:
          switch (evento) {
            case CAMBIO_MODO:
              estadoActual = MANUAL;
              logEstado("→ MODO MANUAL");
              break;
            default:
              break;
          }
          break;

        case MANUAL:
          switch (evento) {
            case CAMBIO_MODO:
              estadoActual = AUTOMATICO;
              logEstado("→ MODO AUTOMATICO");
              break;

            case PULSADOR_MANUAL:
              releEncendido = !releEncendido;
              digitalWrite(pinRelay, releEncendido ? HIGH : LOW);
              if (releEncendido) {
                logEstado("VENTILADOR ON (MANUAL)");
                xTaskCreate(tareaMelodia, "Melodia", 2048, NULL, 1, &handleMelodia);
              } else {
                logEstado("VENTILADOR OFF (MANUAL)");
              }
              break;

            default:
              break;
          }
          break;

        case AUTOMATICO:
          switch (evento) {
            case CAMBIO_MODO:
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

// === Tarea de melodía ===
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

// === Logging por serial ===
void logEstado(const char* mensaje) {
  Serial.println(mensaje);
}
