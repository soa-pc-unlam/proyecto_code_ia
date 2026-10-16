#ifndef ENUMS_H_INCLUDED
#define ENUMS_H_INCLUDED

enum ButtonStatus {
    OFF,
    ON,
    UNKNOWN_BUTTON_STATUS,
};

enum AlarmStatus {
    MUTE,
    UNMUTE,
    UNKNOWN_ALARM_STATUS,
};

enum SystemStatus {
    VIRGIN_EMBEDDED,
    STOCK_MODE,
    SECURITY_MODE,
    UNKNOWN_SYSTEM_STATUS,
};

enum SystemEvent {
    STOCK_ON,
    STOCK_OFF,
    STOCK_MISSING_SENSOR_01,
    NO_MISSING_STOCK,
    SECURITY_ON,
    SECURITY_OFF,
    SECURITY_OFF_TO_STOCK,
    ANOMALY_SENSOR_01,
    NO_ANOMALY,
    UNKNOWN_SYSTEM_EVENT,
};

const ButtonStatus buttonStatusToEnum(const char* status);

const AlarmStatus alarmStatusToEnum(const char* status);

const char* toString(ButtonStatus status);

const char* toString(SystemStatus status);

#endif
