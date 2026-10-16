#ifndef USER_FUNCTIONS_H_INCLUDED
#define USER_FUNCTIONS_H_INCLUDED

#include "enums.h"
#include "structs.h"

void switchBtn(Button* btn);

void lcdPrint(LCD16x2* lcd, const String line);

void lcdPrint(LCD16x2* lcd, const String line01, const String line02);

void lcdClear(LCD16x2* lcd);

void playBuzzer(Buzzer* buzzer);

void stopBuzzer(Buzzer* buzzer);

void muteBuzzer(Buzzer* buzzer);

void unmuteBuzzer(Buzzer* buzzer);

const int32_t getOffset(WeightSensor* sensor);

const unsigned int getWeight(const WeightSensor* sensor);

const unsigned int getStock(const WeightSensor* sensor);

const String* getProductName(WeightSensor* sensor);

const unsigned int getMinimumAcceptableStock(const WeightSensor* sensor);

bool hasAnomaly(const WeightSensor* sensor);

void setOffset(WeightSensor* sensor, const int32_t offset);

void setWeight(WeightSensor* sensor);

void setBaselineWeight(WeightSensor* sensor);

void tare(WeightSensor* sensor);

void ledOn(const WeightSensor* sensor);

void ledOff(const WeightSensor* sensor);

#endif
