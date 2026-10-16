#ifndef CONSTANTS_H_INCLUDED
#define CONSTANTS_H_INCLUDED

#include "structs.h"

#define LCD_ROWS 2
#define LCD_COLS 16

#define WEIGHT_SENSOR_ID                       "shelf-01"
#define WEIGHT_SENSOR_PRODUCT_NAME             "Apples"
#define WEIGHT_SENSOR_PRODUCT_WEIGHT           1000
#define WEIGHT_SENSOR_MINIMUM_ACCEPTABLE_STOCK 1

#define WEIGHT_SENSORS_CALIBRATION_FACTOR 0.42

#define ANOMALY_THRESHOLD 200

extern Button stockBtn;
extern Button securityBtn;

extern LCD16x2 LCD;

extern Buzzer buzzer;

extern WeightSensor weightSensor01;

extern SystemStatus systemStatus;

#endif
