#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include "DHTesp.h"
#include "constantes.h"
#include "Metrics.h"

// --- ENUMERACIONES FSM ---
typedef enum {
    ST_CERRADA,
    ST_ABIERTA,
    ST_ABRIENDO,
    ST_CERRANDO,
    ST_BLOQUEADO_PIR,
    ST_EMERGENCIA
} EstadoVentana_t;

typedef enum {
    EV_LLUVIA,
    EV_ALTA_HUMEDAD,
    EV_CMD_ABRIR,
    EV_CMD_CERRAR,
    EV_FC_A,
    EV_FC_C,
    EV_PIR_DETECT,
    EV_PIR_TIMEOUT,
    EV_EMERGENCIA,
    EV_REINICIO_SEGURO,
    EV_CONTINUE
} Evento_t;

typedef enum {
    MODO_AUTOMATICO,
    MODO_MANUAL
} Modo_t;

// --- VARIABLES GLOBALES Y HANDLES ---
Modo_t modo_actual = MODO_AUTOMATICO;
EstadoVentana_t estado_actual = ST_CERRADA;

QueueHandle_t queueEventos;
TimerHandle_t timerPIR;

WiFiClient espClient;
PubSubClient client(espClient);
DHTesp dht;

// Variables compartidas (protegidas por ISR)
volatile bool isEmergencia = false;

// Prototipos
void motor_abrir();
void motor_cerrar();
void motor_parar();
void publicar_estado(const char* estado);
void timerPIRCallback(TimerHandle_t xTimer);
void IRAM_ATTR isr_emergencia();

// --- FUNCIONES HARDWARE ---
void motor_abrir() {
    digitalWrite(motor1Dir1, HIGH);
    digitalWrite(motor1Dir2, LOW);
    ledcWrite(motor1PWM, VELOCIDAD);
}

void motor_cerrar() {
    digitalWrite(motor1Dir1, LOW);
    digitalWrite(motor1Dir2, HIGH);
    ledcWrite(motor1PWM, VELOCIDAD);
}

void motor_parar() {
    digitalWrite(motor1Dir1, LOW);
    digitalWrite(motor1Dir2, LOW);
    ledcWrite(motor1PWM, STOP);
}

void publicar_estado(const char* estado) {
    if(client.connected()) {
        client.publish(TOPIC_ESTADO, estado, true); // true para Retain
    }
}

// --- INTERRUPCIONES ---
void IRAM_ATTR isr_emergencia() {
    static unsigned long last_interrupt_time = 0;
    unsigned long interrupt_time = millis();
    
    // Antirebote
    if (interrupt_time - last_interrupt_time > T_ANTIREBOTE) {
        BaseType_t xHigherPriorityTaskWoken = pdFALSE;
        Evento_t ev;
        
        if (isEmergencia) {
            ev = EV_REINICIO_SEGURO;
        } else {
            ev = EV_EMERGENCIA;
        }
        
        xQueueSendFromISR(queueEventos, &ev, &xHigherPriorityTaskWoken);
        if (xHigherPriorityTaskWoken) {
            portYIELD_FROM_ISR();
        }
    }
    last_interrupt_time = interrupt_time;
}

void timerPIRCallback(TimerHandle_t xTimer) {
    Evento_t ev = EV_PIR_TIMEOUT;
    xQueueSend(queueEventos, &ev, portMAX_DELAY);
}

// --- MQTT ---
void callbackMQTT(char* topic, byte* payload, unsigned int length) {
    String msg = "";
    for (unsigned int i = 0; i < length; i++) {
        msg += (char)payload[i];
    }
    
    if (String(topic) == TOPIC_MODO) {
        if (msg == "MANUAL") modo_actual = MODO_MANUAL;
        else if (msg == "AUTOMATICO") modo_actual = MODO_AUTOMATICO;
    } 
    else if (String(topic) == TOPIC_COMANDO) {
        Evento_t ev = EV_CONTINUE;
        if (msg == "ABRIR") ev = EV_CMD_ABRIR;
        else if (msg == "CERRAR") ev = EV_CMD_CERRAR;
        
        if (ev != EV_CONTINUE) {
            xQueueSend(queueEventos, &ev, portMAX_DELAY);
        }
    }
    else if (String(topic) == TOPIC_EMERGENCIA) {
        Evento_t ev;
        if (isEmergencia) ev = EV_REINICIO_SEGURO;
        else ev = EV_EMERGENCIA;
        xQueueSend(queueEventos, &ev, portMAX_DELAY);
    }
}

// --- FUNCIONES FSM ---
Evento_t get_input() {
    Evento_t ev = EV_CONTINUE;
    if (xQueueReceive(queueEventos, &ev, pdMS_TO_TICKS(T_TASK)) == pdTRUE) {
        return ev;
    }
    return EV_CONTINUE;
}

// --- TAREA FSM (CONSUMIDOR) ---
void vTaskFSM(void *pvParameters) {
    for(;;) {
        Evento_t ev = get_input();
        if (ev == EV_CONTINUE) continue;
        
        EstadoVentana_t estado_ant = estado_actual;
        
        // Se exige estricto patron switch(estado) -> switch(evento) sin if/else.
        switch(estado_actual) {
            
            case ST_CERRADA:
                switch(ev) {
                    case EV_CMD_ABRIR:
                    case EV_ALTA_HUMEDAD:
                        Serial.println("\n=== [MÉTRICAS] CASO B: INICIO DE ACCIÓN (APERTURA) ===");
                        initStats();
                        estado_actual = ST_ABRIENDO;
                        motor_abrir();
                        publicar_estado("ABRIENDO");
                        break;
                    case EV_EMERGENCIA:
                        isEmergencia = true;
                        estado_actual = ST_EMERGENCIA;
                        publicar_estado("BLOQUEADO");
                        client.publish(TOPIC_EMERGENCIA_ESTADO, "EMERGENCIA", true);
                        break;
                    default: break;
                }
                break;
                
            case ST_ABIERTA:
                switch(ev) {
                    case EV_CMD_CERRAR:
                    case EV_LLUVIA:
                        estado_actual = ST_CERRANDO;
                        motor_cerrar();
                        publicar_estado("CERRANDO");
                        break;
                    case EV_EMERGENCIA:
                        isEmergencia = true;
                        estado_actual = ST_EMERGENCIA;
                        publicar_estado("BLOQUEADO");
                        client.publish(TOPIC_EMERGENCIA_ESTADO, "EMERGENCIA", true);
                        break;
                    default: break;
                }
                break;
                
            case ST_ABRIENDO:
                switch(ev) {
                    case EV_FC_A:
                        estado_actual = ST_ABIERTA;
                        motor_parar();
                        publicar_estado("ABIERTA");
                        finishStats();
                        Serial.println("=== [MÉTRICAS] FIN DE ACCIÓN =========================\n");
                        break;
                    case EV_CMD_CERRAR:
                    case EV_LLUVIA:
                        estado_actual = ST_CERRANDO;
                        motor_cerrar();
                        publicar_estado("CERRANDO");
                        break;
                    case EV_EMERGENCIA:
                        motor_parar();
                        isEmergencia = true;
                        estado_actual = ST_EMERGENCIA;
                        publicar_estado("BLOQUEADO");
                        client.publish(TOPIC_EMERGENCIA_ESTADO, "EMERGENCIA", true);
                        finishStats();
                        break;
                    default: break;
                }
                break;
                
            case ST_CERRANDO:
                switch(ev) {
                    case EV_FC_C:
                        estado_actual = ST_CERRADA;
                        motor_parar();
                        publicar_estado("CERRADA");
                        break;
                    case EV_CMD_ABRIR:
                        // En automático esto nunca pasa porque lluvia tiene prioridad,
                        // en manual el usuario puede ordenar abrir.
                        estado_actual = ST_ABRIENDO;
                        motor_abrir();
                        publicar_estado("ABRIENDO");
                        break;
                    case EV_PIR_DETECT:
                        motor_parar();
                        estado_actual = ST_BLOQUEADO_PIR;
                        publicar_estado("BLOQUEADO");
                        xTimerStart(timerPIR, 0); // Inicia timer 5s
                        break;
                    case EV_EMERGENCIA:
                        motor_parar();
                        isEmergencia = true;
                        estado_actual = ST_EMERGENCIA;
                        publicar_estado("BLOQUEADO");
                        client.publish(TOPIC_EMERGENCIA_ESTADO, "EMERGENCIA", true);
                        break;
                    default: break;
                }
                break;
                
            case ST_BLOQUEADO_PIR:
                switch(ev) {
                    case EV_PIR_TIMEOUT:
                        estado_actual = ST_CERRANDO;
                        motor_cerrar();
                        publicar_estado("CERRANDO");
                        break;
                    case EV_PIR_DETECT:
                        xTimerReset(timerPIR, 0); // Reinicia los 5s
                        break;
                    case EV_EMERGENCIA:
                        xTimerStop(timerPIR, 0);
                        isEmergencia = true;
                        estado_actual = ST_EMERGENCIA;
                        client.publish(TOPIC_EMERGENCIA_ESTADO, "EMERGENCIA", true);
                        break;
                    default: break;
                }
                break;
                
            case ST_EMERGENCIA:
                switch(ev) {
                    case EV_REINICIO_SEGURO:
                        isEmergencia = false;
                        client.publish(TOPIC_EMERGENCIA_ESTADO, "NORMAL", true);
                        estado_actual = ST_CERRANDO;
                        motor_cerrar();
                        publicar_estado("CERRANDO");
                        break;
                    default: break;
                }
                break;
        }
        
        if (estado_actual != estado_ant) {
            Serial.printf("[FSM] Transicion de Estado: %d -> %d\n", estado_ant, estado_actual);
        }
    }
}

// --- TAREA SENSORES (PRODUCTOR) ---
void vTaskSensores(void *pvParameters) {
    dht.setup(GPIO_DHT11, DHTesp::DHT11);
    
    // Estados previos para detectar flancos (Pull-down: normal LOW, apretado HIGH)
    bool fc_a_ant = LOW;
    bool fc_c_ant = LOW;
    bool pir_ant = LOW;
    
    // Control tiempo DHT (Polling ambiental)
    unsigned long ultimaLecturaDHT = 0;
    
    for(;;) {
        // --- LEER CONSOLA ---
        if (Serial.available()) {
            char c = tolower(Serial.read());
            while(Serial.available()) Serial.read(); // limpiar buffer
            Evento_t ev = EV_CONTINUE;
            Serial.printf("[Consola] Comando: %c\n", c);
            switch(c) {
                case 'a': ev = EV_CMD_ABRIR; break;
                case 'c': ev = EV_CMD_CERRAR; break;
                case 'm': modo_actual = MODO_MANUAL; Serial.println("Modo actual: MANUAL"); break;
                case 's': modo_actual = MODO_AUTOMATICO; Serial.println("Modo actual: AUTO"); break;
                case 'e': ev = EV_EMERGENCIA; break;
                case '1': ev = EV_FC_A; Serial.println("Simulando Final Carrera ABIERTA"); break;
                case '2': ev = EV_FC_C; Serial.println("Simulando Final Carrera CERRADA"); break;
            }
            if (ev != EV_CONTINUE) xQueueSend(queueEventos, &ev, 0);
        }

        // Lectura FC Apertura
        bool fc_a_act = digitalRead(GPIO_FC_A);
        if (fc_a_ant == LOW && fc_a_act == HIGH) { 
            Evento_t ev = EV_FC_A;
            xQueueSend(queueEventos, &ev, 0);
        }
        fc_a_ant = fc_a_act;

        // Lectura FC Cierre
        bool fc_c_act = digitalRead(GPIO_FC_C);
        if (fc_c_ant == LOW && fc_c_act == HIGH) { 
            Evento_t ev = EV_FC_C;
            xQueueSend(queueEventos, &ev, 0);
        }
        fc_c_ant = fc_c_act;

        // Lectura PIR
        bool pir_act = digitalRead(GPIO_PIR);
        if (pir_act == HIGH && pir_ant == LOW) {
            Evento_t ev = EV_PIR_DETECT;
            xQueueSend(queueEventos, &ev, 0);
        }
        pir_ant = pir_act;

        // Lectura DHT y Ambiental (Se procesa cada INTERVALO ms)
        unsigned long actualMs = millis();
        if (actualMs - ultimaLecturaDHT > INTERVALO) {
            ultimaLecturaDHT = actualMs;
            
            float h = dht.getHumidity();
            float t = dht.getTemperature();
            
            Serial.printf("[Sensor] Temp: %.1f C, Hum: %.1f %%\n", t, h);
            
            if (client.connected()) {
                client.publish(TOPIC_HUMEDAD, String(h).c_str());
                client.publish(TOPIC_TEMP, String(t).c_str());
            }

            // Lectura Lluvia
            int valLluvia = analogRead(GPIO_Lluvia);
            // Si tu sensor funciona al revés (valores altos = lluvia), usamos >
            bool lluvia_act = (valLluvia > LLUVIA_MIN_VALIDA); 

            // Filtrado y enrutado en base al modo MODO_AUTOMATICO
            if (modo_actual == MODO_AUTOMATICO) {
                static bool lluvia_enviada = false;
                static bool humedad_enviada = false;

                // Disparo por lluvia
                if (lluvia_act) {
                    if (!lluvia_enviada) {
                        Evento_t ev = EV_LLUVIA;
                        xQueueSend(queueEventos, &ev, 0);
                        lluvia_enviada = true;
                    }
                } else {
                    lluvia_enviada = false;
                }
                
                // Disparo por humedad (solo si NO llueve)
                if (h > HUMEDAD_UMBRAL_MAX && !lluvia_act) {
                    if (!humedad_enviada) {
                        Evento_t ev = EV_ALTA_HUMEDAD;
                        xQueueSend(queueEventos, &ev, 0);
                        humedad_enviada = true;
                    }
                } else {
                    humedad_enviada = false;
                }
            }
        }

        vTaskDelay(pdMS_TO_TICKS(INTERVALO_TAREAS));
    }
}

// --- TAREA MQTT / WIFI ---
void vTaskMQTT(void *pvParameters) {
    WiFi.begin(WIFISSID, PASSWORD);
    
    while (WiFi.status() != WL_CONNECTED) {
        vTaskDelay(pdMS_TO_TICKS(T_WIFI));
    }
    
    client.setServer(MQTT_SERVER, MQTT_PORT);
    client.setCallback(callbackMQTT);
    
    for(;;) {
        if (!client.connected()) {
            if (client.connect(MQTT_NAME, MQTT_USER, MQTT_PASSWORD)) {
                client.subscribe(TOPIC_MODO);
                client.subscribe(TOPIC_COMANDO);
                client.subscribe(TOPIC_EMERGENCIA);
            }
        }
        client.loop();
        vTaskDelay(pdMS_TO_TICKS(INTERVALO_TAREAS));
    }
}

// --- SETUP Y LOOP PRINCIPAL ---
void setup() {
    Serial.begin(115200);
    
    // initStats(); // Eliminado temporalmente ya que falta la implementación en Metrics.cpp
    
    // Configuración de pines
    pinMode(GPIO_FC_A, INPUT_PULLDOWN);
    pinMode(GPIO_FC_C, INPUT_PULLDOWN);
    pinMode(GPIO_PIR, INPUT);
    pinMode(GPIO_EMERG, INPUT_PULLUP);
    
    pinMode(motor1Dir1, OUTPUT);
    pinMode(motor1Dir2, OUTPUT);
    
    // Configuración PWM ESP32
    ledcAttach(motor1PWM, PWM_FREQ, PWM_RES);
    motor_parar();

    // Creación de recursos FreeRTOS
    queueEventos = xQueueCreate(QUEUE_SIZE, sizeof(Evento_t));
    
    timerPIR = xTimerCreate("TimerPIR", pdMS_TO_TICKS(PIR_TIME), pdFALSE, (void *)0, timerPIRCallback);
    
    // Interrupción de emergencia
    attachInterrupt(digitalPinToInterrupt(GPIO_EMERG), isr_emergencia, FALLING);

    // Tareas FreeRTOS
    xTaskCreate(vTaskSensores, "Sensores", STACK_SIZE, NULL, PRIORIDAD_TAREAS, NULL);
    xTaskCreate(vTaskFSM, "FSM", STACK_SIZE, NULL, PRIORIDAD_TAREAS + 1, NULL); 
    xTaskCreate(vTaskMQTT, "MQTT", STACK_SIZE, NULL, PRIORIDAD_TAREAS, NULL);

    initStats();
}

void loop() {
    
    static bool reposo_medido = false;

    // Caso A
    if (!reposo_medido && millis() > TIEMPO_REPOSO) {
        Serial.println("\n=== MÉTRICAS: CASO A (REPOSO 10s SIN INTERACCIÓN) ===");
        finishStats();
        reposo_medido = true;
        Serial.println("=====================================================\n");
    }
    
    // El loop principal no hace nada, cedemos tiempo.
    vTaskDelay(pdMS_TO_TICKS(T_LOOP));
}
