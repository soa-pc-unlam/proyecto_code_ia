
#include <rgb_lcd.h>

#include "constants.h"
#include "pins.h"

Button stockBtn = {
    .pin = STOCK_BUTTON_PIN,
    .ledPin = STOCK_LED_PIN,
    .debounceDelay = 25,
};

Button securityBtn = {
    .pin = SECURITY_BUTTON_PIN,
    .ledPin = SECURITY_LED_PIN,
    .debounceDelay = 25,
};

rgb_lcd lcdDevice;

LCD16x2 LCD = {
    .device = &lcdDevice,
    .line01 = "",
    .line02 = "",
};

const BuzzerStep buzzerSteps[] = {
    { .frequency = 700, .duration = 500 },
    { .frequency = 560, .duration = 500 },
    { .frequency = 700, .duration = 500 },
    { .frequency = 560, .duration = 500 },
};

Buzzer buzzer = {
    .pin = BUZZER_PIN,
    .steps = buzzerSteps,
    .stepsLength = sizeof(buzzerSteps) / sizeof(buzzerSteps[0]),
    .muted = false,
    .playing = false,
};

WeightSensor weightSensor01 = {
    .id = WEIGHT_SENSOR_ID,
    .dtPin = WEIGHT_SENSOR_DT_PIN,
    .sckPin = WEIGHT_SENSOR_SCK_PIN,
    .ledPin = WEIGHT_SENSOR_LED_PIN,
    .product = {
        .name = WEIGHT_SENSOR_PRODUCT_NAME,
        .weight = WEIGHT_SENSOR_PRODUCT_WEIGHT,
        .minimumAcceptableStock = WEIGHT_SENSOR_MINIMUM_ACCEPTABLE_STOCK,
    },
    .sample = {
        .weight = 0,
        .isValid = false,
    },
    .anomaly = false,
};
