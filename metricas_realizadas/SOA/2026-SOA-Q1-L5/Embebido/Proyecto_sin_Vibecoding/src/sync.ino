
#include "sync.h"

static SemaphoreHandle_t buttonsMutex = nullptr;
static SemaphoreHandle_t buzzerMutex = nullptr;
static SemaphoreHandle_t weightSensorsMutex = nullptr;

void initMutexs() {
    buttonsMutex = xSemaphoreCreateMutex();
    buzzerMutex = xSemaphoreCreateMutex();
    weightSensorsMutex = xSemaphoreCreateMutex();
}

void lockButtons() {
    if (!buttonsMutex) return;
    xSemaphoreTake(buttonsMutex, portMAX_DELAY);
}

void unlockButtons() {
    if (!buttonsMutex) return;
    xSemaphoreGive(buttonsMutex);
}

void lockBuzzer() {
    if (!buzzerMutex) return;
    xSemaphoreTake(buzzerMutex, portMAX_DELAY);
}

void unlockBuzzer() {
    if (!buzzerMutex) return;
    xSemaphoreGive(buzzerMutex);
}

void lockWeightSensors() {
    if (!weightSensorsMutex) return;
    xSemaphoreTake(weightSensorsMutex, portMAX_DELAY);
}

void unlockWeightSensors() {
    if (!weightSensorsMutex) return;
    xSemaphoreGive(weightSensorsMutex);
}
