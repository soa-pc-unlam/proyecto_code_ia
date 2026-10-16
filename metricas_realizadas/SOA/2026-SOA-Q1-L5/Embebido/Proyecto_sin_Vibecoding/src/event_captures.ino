
#include "constants.h"
#include "enums.h"
#include "event_captures.h"
#include "sync.h"
#include "user_functions.h"

const SystemEvent getStockBtnEvent(const SystemStatus status) {
    lockButtons();
    const ButtonStatus btnStatus = stockBtn.status;
    unlockButtons();

    return btnStatus == ButtonStatus::ON ? SystemEvent::STOCK_ON : SystemEvent::STOCK_OFF;
}

const SystemEvent getStockSensorEvent(const SystemStatus status) {
    if (status != SystemStatus::STOCK_MODE) return SystemEvent::NO_MISSING_STOCK;

    bool valid = false;
    unsigned int stock = 0;

    lockWeightSensors();
    if (weightSensor01.sample.isValid) {
        valid = true;
        stock = weightSensor01.sample.weight / weightSensor01.product.weight;
    }

    const unsigned int minimum = weightSensor01.product.minimumAcceptableStock;
    unlockWeightSensors();

    const bool missingStockSensor01 = valid && stock < minimum;
    if (missingStockSensor01) return SystemEvent::STOCK_MISSING_SENSOR_01;

    return SystemEvent::NO_MISSING_STOCK;
}

const SystemEvent getSecurityBtnEvent(const SystemStatus status) {
    lockButtons();
    const ButtonStatus securityStatus = securityBtn.status;
    const ButtonStatus stockStatus = stockBtn.status;
    unlockButtons();

    if (securityStatus == ButtonStatus::ON) return SystemEvent::SECURITY_ON;
    if (status == SystemStatus::SECURITY_MODE && stockStatus == ButtonStatus::ON)
        return SystemEvent::SECURITY_OFF_TO_STOCK;
    return SystemEvent::SECURITY_OFF;
}

const SystemEvent getAnomalySensorEvent(const SystemStatus status) {
    if (status != SystemStatus::SECURITY_MODE) return SystemEvent::NO_ANOMALY;

    bool anomalySensor01 = hasAnomaly(&weightSensor01);

    if (anomalySensor01) return SystemEvent::ANOMALY_SENSOR_01;
    return SystemEvent::NO_ANOMALY;
}
