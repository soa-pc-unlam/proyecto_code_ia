
#include <Arduino.h>

#include "enums.h"

const ButtonStatus buttonStatusToEnum(const char* status) {
    if (strcmp(status, "ON") == 0) {
        return ButtonStatus::ON;
    } else if (strcmp(status, "OFF") == 0) {
        return ButtonStatus::OFF;
    } else {
        return ButtonStatus::UNKNOWN_BUTTON_STATUS;
    }
}

const AlarmStatus alarmStatusToEnum(const char* status) {
    if (strcmp(status, "MUTE") == 0) {
        return AlarmStatus::MUTE;
    } else if (strcmp(status, "UNMUTE") == 0) {
        return AlarmStatus::UNMUTE;
    } else {
        return AlarmStatus::UNKNOWN_ALARM_STATUS;
    }
}

const char* toString(const ButtonStatus status) {
    switch (status) {
        case ButtonStatus::OFF:
            return "OFF";

        case ButtonStatus::ON:
            return "ON";

        case ButtonStatus::UNKNOWN_BUTTON_STATUS:
            return "UNKNOWN_BUTTON_STATUS";

        default:
            return nullptr;
    }
}

const char* toString(const SystemStatus status) {
    switch (status) {
        case SystemStatus::VIRGIN_EMBEDDED:
            return "VIRGIN_EMBEDDED";

        case SystemStatus::STOCK_MODE:
            return "STOCK_MODE";

        case SystemStatus::SECURITY_MODE:
            return "SECURITY_MODE";

        case SystemStatus::UNKNOWN_SYSTEM_STATUS:
            return "UNKNOWN_SYSTEM_STATUS";

        default:
            return nullptr;
    }
}
