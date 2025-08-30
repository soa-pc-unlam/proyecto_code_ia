#include <Arduino.h>   // Serial, ESP.*, psramFound(), etc.
extern "C" {
  #include "freertos/FreeRTOS.h"
  #include "freertos/task.h"
  #include "freertos/semphr.h"
}
#include <cstdint>     // uint32_t, uint64_t
#include <cstddef>     // size_t, NULL
#include <cstring>     // strncmp

// ------------------ Forward declarations ------------------
static void StatsCpu();
static void printMemoryStats();
static void StatsTask(void *pv);

// ------------------ Estado global (solo acá, no en .h) ----
static TaskHandle_t gStatsTask = nullptr;

// ------------------ Implementación ------------------------
static void StatsCpu() {
#if (configGENERATE_RUN_TIME_STATS == 1)
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

  Serial.println("\n=== Contribución al total del sistema ===");
  if (totalRuntime > 0) {
    for (int core = 0; core < 2; core++) {
      float pctCoreTotal = 100.0f * (float)totalCore[core] / (float)totalRuntime;
      float pctIdleTotal = 100.0f * (float)idleCore[core]  / (float)totalRuntime;
      float pctBusyTotal = pctCoreTotal - pctIdleTotal;
      Serial.printf(
        "Core %d -> Total: %6.2f%% | Ocupado: %6.2f%% | Libre (IDLE): %6.2f%%\n",
        core, pctCoreTotal, pctBusyTotal, pctIdleTotal
      );
    }
  } else {
    Serial.println("(totalRuntime = 0: aún no hay muestras suficientes)");
  }

  vPortFree(taskArray);
#else
  // Si no activaste las stats en FreeRTOS, avisamos para evitar confusión
  Serial.println("\n=== Stats de CPU no disponibles ===");
  Serial.println("Habilita configGENERATE_RUN_TIME_STATS=1 en FreeRTOSConfig.h");
#endif
}

static void printMemoryStats() {
  Serial.println("\n=== Estado de la memoria en ESP32 ===");
  Serial.println("=== Memoria interna (Heap) ===");
  size_t heapTotal = ESP.getHeapSize();
  size_t heapLibre = ESP.getFreeHeap();
  size_t heapUsado = heapTotal - heapLibre;

  Serial.printf("Heap total : %u bytes\n", (unsigned)heapTotal);
  Serial.printf("Heap libre : %u bytes\n", (unsigned)heapLibre);
  Serial.printf("Heap usado : %u bytes\n", (unsigned)heapUsado);
  Serial.printf("Uso        : %.2f %%\n\n",
                heapTotal ? (heapUsado * 100.0) / heapTotal : 0.0);

  if (psramFound()) {
    Serial.println("=== Memoria externa (PSRAM) ===");
    size_t psramTotal = ESP.getPsramSize();
    size_t psramLibre = ESP.getFreePsram();
    size_t psramUsado = psramTotal - psramLibre;

    Serial.printf("PSRAM total: %u bytes\n", (unsigned)psramTotal);
    Serial.printf("PSRAM libre: %u bytes\n", (unsigned)psramLibre);
    Serial.printf("PSRAM usado: %u bytes\n", (unsigned)psramUsado);
    Serial.printf("Uso        : %.2f %%\n\n",
                  psramTotal ? (psramUsado * 100.0) / psramTotal : 0.0);
  } else {
    Serial.println("No se detectó PSRAM en este módulo.");
  }
}

static void StatsTask(void *pv) {
  (void)pv;
  for (;;) {
    StatsCpu();
    printMemoryStats();

    // Espera de 5 segundos entre impresiones
    vTaskDelay(pdMS_TO_TICKS(5000));
  }
}

// ===== API =====
void startMetrics(){
  if (gStatsTask) return; // ya creada
  xTaskCreatePinnedToCore(
    StatsTask, "StatsTask",
    4096, NULL, 1, &gStatsTask,
    1 // core 1 para no interferir con WiFi (típicamente core 0)
  );
}

void pauseMetrics(){
  if (gStatsTask) vTaskSuspend(gStatsTask);
}

void resumeMetrics(){
  if (gStatsTask) vTaskResume(gStatsTask);
}

void stopMetrics(){
  if (!gStatsTask) return;
  vTaskDelete(gStatsTask);
  gStatsTask = nullptr;
}

void printPerCoreTaskStats() {
  if (gStatsTask) xTaskNotifyGive(gStatsTask);
}
