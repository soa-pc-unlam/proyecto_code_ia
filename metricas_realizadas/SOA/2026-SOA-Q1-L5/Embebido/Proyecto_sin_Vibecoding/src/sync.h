#ifndef SYNC_H_INCLUDED
#define SYNC_H_INCLUDED

#include <freertos/FreeRTOS.h>
#include <freertos/semphr.h>

void initMutexs();

void lockButtons();
void unlockButtons();

void lockBuzzer();
void unlockBuzzer();

void lockWeightSensors();
void unlockWeightSensors();

#endif
