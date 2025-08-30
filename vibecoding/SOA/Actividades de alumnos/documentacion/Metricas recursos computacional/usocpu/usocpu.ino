#include <Arduino.h>
extern "C" {
  #include "freertos/FreeRTOS.h"
  #include "freertos/task.h"
  #include "freertos/semphr.h"
}

#define CPURESUME 1
#define CPUVIEWTASK 2

#define STATICS CPURESUME

void tarea1(void *pvParameters);
void tarea2(void *pvParameters);

// === Infra de stats no bloqueante ===
static TaskHandle_t gStatsTask = nullptr;

// Llamá a esta función: es NO bloqueante.
// Solo notifica a la tarea de stats que tome una muestra y la imprima.
void printPerCoreTaskStats() {
  if (gStatsTask) {
    xTaskNotifyGive(gStatsTask);  // dispara muestreo (returns immediately)
  }
}

static void StatsTask(void *pv) {
    while(true){
    StatsCpu();
    printMemoryStats();
    }
}

static void StatsCpu() {
    ulTaskNotifyTake(pdTRUE, portMAX_DELAY);

    UBaseType_t maxTasks = uxTaskGetNumberOfTasks();
    TaskStatus_t *taskArray = (TaskStatus_t*) pvPortMalloc(maxTasks * sizeof(TaskStatus_t));
    if (!taskArray) {
      Serial.println("[Stats] Error de memoria");
      return;
    }

    unsigned long totalRuntime = 0;
    UBaseType_t taskCount = uxTaskGetSystemState(taskArray, maxTasks, &totalRuntime);

    uint64_t totalCore[2] = {0, 0};
    uint64_t idleCore[2]  = {0, 0};

    // Acumulo tiempos por core y detecto IDLE0/IDLE1
    for (UBaseType_t i = 0; i < taskCount; i++) {
      if (taskArray[i].xCoreID < 2) {
        totalCore[taskArray[i].xCoreID] += taskArray[i].ulRunTimeCounter;

        // Heurística: nombres "IDLE0"/"IDLE1" en ESP32
        const char* name = taskArray[i].pcTaskName;
        if (name && (strncmp(name, "IDLE0", 5) == 0 || strncmp(name, "IDLE1", 5) == 0)) {
          idleCore[taskArray[i].xCoreID] += taskArray[i].ulRunTimeCounter;
        }
      }
    }

    Serial.println("\n=== Uso de CPU por núcleo y tarea ===");
    for (UBaseType_t i = 0; i < taskCount; i++) {
      uint32_t core = taskArray[i].xCoreID;
      if (core < 2 && totalCore[core] > 0) {
        float pct = 100.0f * (float)taskArray[i].ulRunTimeCounter / (float)totalCore[core];
        Serial.printf("Core %u | %-16s | %6.2f%%\n", core, taskArray[i].pcTaskName, pct);
        vTaskDelay(1);
      }
    }

    // ----- Contribución de cada core al total del chip -----
    Serial.println("\n=== Contribución al total del sistema ===");
    if (totalRuntime > 0) {
      for (int core = 0; core < 2; core++) {
        float pctCoreTotal = 100.0f * (float)totalCore[core] / (float)totalRuntime;
        float pctIdleTotal = 100.0f * (float)idleCore[core]  / (float)totalRuntime;
        float pctBusyTotal = pctCoreTotal - pctIdleTotal; // equivalente a (totalCore - idleCore) / totalRuntime
        Serial.printf(
          "Core %d -> Total: %6.2f%% | Ocupado: %6.2f%% | Libre (IDLE): %6.2f%%\n",
          core, pctCoreTotal, pctBusyTotal, pctIdleTotal
        );
      }
    } else {
      Serial.println("(totalRuntime = 0: aún no hay muestras suficientes)");
    }

    vPortFree(taskArray);
 }


void printMemoryStats() {
  Serial.println("\n=== Estado de la memoria en ESP32 ===");
  Serial.println("=== Memoria interna (Heap) ===");
  size_t heapTotal = ESP.getHeapSize();
  size_t heapLibre = ESP.getFreeHeap();
  size_t heapUsado = heapTotal - heapLibre;

  Serial.printf("Heap total : %d bytes\n", heapTotal);
  Serial.printf("Heap libre : %d bytes\n", heapLibre);
  Serial.printf("Heap usado : %d bytes\n", heapUsado);
  Serial.printf("Uso        : %.2f %%\n\n", (heapUsado * 100.0) / heapTotal);

  if (psramFound()) {
    Serial.println("=== Memoria externa (PSRAM) ===");
    size_t psramTotal = ESP.getPsramSize();
    size_t psramLibre = ESP.getFreePsram();
    size_t psramUsado = psramTotal - psramLibre;

    Serial.printf("PSRAM total: %d bytes\n", psramTotal);
    Serial.printf("PSRAM libre: %d bytes\n", psramLibre);
    Serial.printf("PSRAM usado: %d bytes\n", psramUsado);
    Serial.printf("Uso        : %.2f %%\n\n", (psramUsado * 100.0) / psramTotal);
  } else {
    Serial.println("No se detectó PSRAM en este módulo.");
  }
}

void setup() {
  Serial.begin(115200);
  delay(1000);

  // Tareas de prueba
  xTaskCreatePinnedToCore(tarea1, "Tarea1", 2048, NULL, 1, NULL, 0);
  xTaskCreatePinnedToCore(tarea2, "Tarea2", 2048, NULL, 1, NULL, 1);

  // Crear la tarea de stats (baja prioridad, por ejemplo 1)
  xTaskCreatePinnedToCore(
    StatsTask, "StatsTask",
    4096, NULL, 1, &gStatsTask,
    0 // podés cambiar de core si querés
  );
}

void loop() {
  static unsigned long last = 0;
  if (millis() - last > 5000) {    // cada 5s pedimos una muestra
    printPerCoreTaskStats();       // NO bloquea
    last = millis();
  }

  // Tu loop sigue respondiendo normal
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
