#ifndef EVENT_CAPTURES_H_INCLUDED
#define EVENT_CAPTURES_H_INCLUDED

#include "enums.h"

const SystemEvent getStockBtnEvent(const SystemStatus status);

const SystemEvent getStockSensorEvent(const SystemStatus status);

const SystemEvent getSecurityBtnEvent(const SystemStatus status);

const SystemEvent getAnomalySensorEvent(const SystemStatus status);

#endif
