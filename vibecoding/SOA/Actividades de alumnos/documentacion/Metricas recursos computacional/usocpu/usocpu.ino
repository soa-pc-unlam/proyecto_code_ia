#include "Metrics.h"

void tarea1(void *pvParameters);
void tarea2(void *pvParameters);


void setup() {
  Serial.begin(115200);
  delay(1000);

  // Tareas de prueba
  xTaskCreatePinnedToCore(tarea1, "Tarea1", 2048, NULL, 1, NULL, 0);
  xTaskCreatePinnedToCore(tarea2, "Tarea2", 2048, NULL, 1, NULL, 1);

  startMetrics();
}

void loop() {
 
}

// Carga moderada
void tarea1(void *pvParameters) {
  while (1) {
    //for (volatile int i = 0; i < 100000; i++);
    vTaskDelay(100000000); // cede CPU
  }
}

// Carga alta (intenta saturar su core)
void tarea2(void *pvParameters) {
  while (1) {
    // bucle apretado
    vTaskDelay(100);
  }
}
