#include "Config.h"
#include <Arduino.h>
#include <Preferences.h>

extern char payloadGlobal[512];
extern volatile bool hayMensajeNuevo;

Contacto listaContactos[MAX_CONTACTOS] = {};


// ==========================================
// VARIABLES GLOBALES Y MOCKUPS
// ==========================================

Evento evento, eventoAnterior;

EstadoFSM estadoActual = INIT;
EstadoFSM estadoAnterior = INIT;

// --- Variables de Datos (persistidas en memoria no volátil) ---
String listaMensajes[MAX_MENSAJES] = {"Llamame", "Todo bien", "Ven a casa"};

int indiceContacto = 0;
int indiceActual = 0;
int indiceMensajeActual = 0;
int indiceMensaje = 0;


// ==========================================
// INSTANCIACIÓN DE GESTORES
// ==========================================

GestorBoton gestorBotonEmergencia(BTN_EMERGENCIA, BTN_EMERGENCIA_TIEMPO);
GestorBoton gestorBotonArriba(BTN_ARRIBA, 0);
GestorBoton gestorBotonAbajo(BTN_ABAJO, 0);
GestorBoton gestorBotonConfirmar(BTN_CONFIRMAR, 0);
GestorBoton gestorBotonCancelar(BTN_CANCELAR, 0);
GestorBoton gestorBotonGrabar(BTN_GRABAR, 0);
GestorAlmacenamiento gestorSD(SD_CS, SD_SCK, SD_MISO, SD_MOSI);
GestorDeMicrofono gestorAudio(BUZZER_PIN, LED_PIN, POT_PIN, FREC_BUZZER,
                              MIC_SCK, MIC_WS, MIC_SD);
GestorDeInterfaz gestorUI;

QueueHandle_t colaEventos;
TimerHandle_t xTimeoutTimer, xRecordingTimer, xBuzzerTimer, xWiFiTimeoutTimer;
TaskHandle_t getEventHandler;

  #ifdef ACTIVAR_RED
GestorMQTT gestorMQTT(WIFI_SSID, WIFI_PASS, MQTT_BROKER, MQTT_USER, MQTT_PASS, colaEventos); // <-- Pasamos la cola al constructor
GestorHTTP gestorHTTP(80, "/archivoAudio.wav");
#endif

TaskHandle_t xGrabacionTaskHandle = NULL;
TaskHandle_t xProcesarSDHandle = NULL;
TaskHandle_t xTaskRedHandle = NULL;

const TickType_t xFrecuenciaEstricta = pdMS_TO_TICKS(DELAY_INIT);

volatile bool grabandoAudio = false;
volatile bool audioListoParaEnviar = false;

void taskRed(void *pvParameters) {
    Serial.println("[Red] Tarea de fondo para MQTT iniciada.");
    
    while (1) {
        #ifdef ACTIVAR_RED
            // Mantiene el Keep-Alive y procesa los mensajes entrantes de configuración
            gestorMQTT.mantenerConexion();
            gestorHTTP.atenderClientes();
        #endif

        vTaskDelay(pdMS_TO_TICKS(50));
    }
}

void taskProcesarSD(void *pvParameters) {
    while (1) {
        // La tarea duerme aquí indefinidamente hasta recibir la señal
        ulTaskNotifyTake(pdTRUE, portMAX_DELAY);

        Serial.println("[Fondo] Iniciando procesamiento pesado de la SD...");
        
        gestorAudio.detenerGrabacion(); 
            
        #if SERIAL_ENABLED
          gestorSD.depurarArchivo();  
        #endif
        Serial.println("[Fondo] Procesamiento SD terminado.");
        audioListoParaEnviar = true;
    }
}


void taskGrabacionAudio(void *pvParameters) {
  Serial.println("[Audio] Tarea de grabación iniciada en segundo plano.");
  while (grabandoAudio)
  {
    gestorAudio.registrarMedida();
  }

  // Cuando la flag sea falsa, sale del while y llega aquí
  Serial.println("[Audio] Tarea finalizada limpiamente.");
  vTaskDelete(NULL); // Se autodestruye de forma segura
}
// ==========================================
// PROTOTIPOS DE FUNCIONES
// ==========================================

void vTimeoutCallback(TimerHandle_t xTimer);
void vBuzzerCallback(TimerHandle_t xTimer);
void vWiFiTimeoutCallback(TimerHandle_t xTimer);
void setup();
void loop();
void taskEvento(void *pvParameters);
void taskFSM(void *pvParameters);

// ==========================================
// CALLBACKS Y FUNCIONES AUXILIARES
// ==========================================
/*Tenemos las señales de timeout y de finalizacion del buzzer, para
diferenciarlas usamos un bit */
void vTimeoutCallback(TimerHandle_t xTimer) {
  xTaskNotify(getEventHandler, BIT_TIMEOUT, eSetBits);
}

void vBuzzerCallback(TimerHandle_t xTimer) {
  xTaskNotify(getEventHandler, BIT_BUZZER_FIN, eSetBits);
}

void vWiFiTimeoutCallback(TimerHandle_t xTimer) {
  xTaskNotify(getEventHandler, BIT_WIFI_TIMEOUT, eSetBits);
}

// ==========================================
// INICIALIZACIÓN (VOID SETUP)
// ==========================================

void setup() {
  Serial.begin(921600);
  Serial.println("Iniciando Sistema de Gerontotecnología...");

  pinMode(LED_PIN, OUTPUT);
  digitalWrite(LED_PIN, LOW);
  pinMode(BUZZER_PIN, OUTPUT);
  digitalWrite(BUZZER_PIN, LOW);

  pinMode(BTN_ARRIBA, INPUT);
  pinMode(BTN_ABAJO, INPUT);
  pinMode(BTN_CONFIRMAR, INPUT);
  pinMode(BTN_CANCELAR, INPUT);

  pinMode(BTN_GRABAR, INPUT_PULLUP);
  pinMode(BTN_EMERGENCIA, INPUT_PULLUP);

  // Resolucion 12 bits, 0 a 4096
  analogReadResolution(BITS_RESOLUCION);

  // Inicialización del Bus I2C (Pantalla LCD)
  Wire.begin(LCD_SDA, LCD_SCL);
  gestorAudio.setAlmacenamiento(&gestorSD);
  gestorAudio.iniciar();

  Serial.println("Iniciando módulos de red...");
  if (!gestorSD.iniciarSD()) {
    Serial.println("ERROR: No se detectó Tarjeta SD o falló el montaje.");
  } else {
    Serial.println("Tarjeta SD montada correctamente.");
  }

  Serial.println("Setup completado. Iniciando FSM...");
  cargarContactosDesdeMemoriaPersistente();
  gestorUI.iniciar();

  #ifdef ACTIVAR_RED
  Serial.println("Iniciando módulos de red...");
  gestorMQTT.configurarReceptor(callbackMQTT);
  #else
  Serial.println("[MODO SIMULADOR] Módulos de red DESACTIVADOS.");
  #endif
  // Creamos una cola capaz de almacenar todos los eventos
  colaEventos = xQueueCreate(MAX_EVENTOS, sizeof(TipoEvento));

  if (colaEventos != NULL) {
    xTaskCreate(taskEvento, "GeneradorEventos", TAM_STACK_GET_EVENT, NULL, 1,
                &getEventHandler);
    xTaskCreate(taskFSM, "MaquinaEstados", TAM_STACK_FSM, NULL, 2, NULL);
    xTaskCreate(taskProcesarSD, "ProcesarSD", TAM_STACK_SD, NULL, 3, &xProcesarSDHandle);
    
    Serial.println("Tareas creadas");
  }


  // Inicializacion de timers
  xTimeoutTimer = xTimerCreate("TimeoutTimer", pdMS_TO_TICKS(TIMEOUT_GENERAL),
                               pdFALSE, NULL, vTimeoutCallback);

  xRecordingTimer = xTimerCreate("RecordingTimer", pdMS_TO_TICKS(TIMEOUT_GRABACION),
                               pdFALSE, NULL, vTimeoutCallback);

  xBuzzerTimer = xTimerCreate("BuzzerTimer", pdMS_TO_TICKS(TIMEOUT_BUZZER),
                               pdFALSE, NULL, vBuzzerCallback);

  xWiFiTimeoutTimer = xTimerCreate("WiFiTimeoutTimer", pdMS_TO_TICKS(TIMEOUT_WIFI),
                               pdFALSE, NULL, vWiFiTimeoutCallback);
}

void loop() { vTaskDelete(NULL); }

// ==========================================
// GENERADOR DE EVENTOS
// ==========================================

void taskEvento(void *pvParameters) {
  while (1) {
    #if SERIAL_ENABLED
    eventoAnterior.tipo = evento.tipo;
    #endif
    evento.tipo = EV_CONTINUE;
    uint32_t notificaciones = 0;

    if (gestorBotonEmergencia.estaMantenido()) {
      evento.tipo = EV_BTN_EMERGENCIA;
    } else if (gestorBotonEmergencia.leerUnClic()) {
      evento.tipo = EV_BTN_EMERGENCIA_PRESS;
    }

    xTaskNotifyWait(0, 0xFFFFFFFF, &notificaciones, 0);

    if (notificaciones & BIT_TIMEOUT) {
      evento.tipo = EV_TIMEOUT;
    } else if (notificaciones & BIT_BUZZER_FIN) {
      evento.tipo = EV_BUZZER_FIN;
    } else if (notificaciones & BIT_WIFI_TIMEOUT) {
      evento.tipo = EV_WIFI_ERROR;
    
    } else if (gestorBotonArriba.leerUnClic()) {
      evento.tipo = EV_BTN_ARRIBA;
    } else if (gestorBotonAbajo.leerUnClic()) {
      evento.tipo = EV_BTN_ABAJO;
    } else if (gestorBotonConfirmar.leerUnClic()) {
      evento.tipo = EV_BTN_CONFIRMAR;
    } else if (gestorBotonCancelar.leerUnClic()) {
      evento.tipo = EV_BTN_CANCELAR;
    } else if (gestorBotonGrabar.leerUnClic()) {
      evento.tipo = EV_BTN_GRABAR;
    }

    #if SERIAL_ENABLED
    if (evento.tipo != eventoAnterior.tipo) {
      SerialPrint("Evento actual: " + eventoToString(evento.tipo));
    }
    #endif
    // xQueueSend va debajo porque sino genera una race condition y no permite
    // visualizar correctamente el debug
    xQueueSend(colaEventos, &evento.tipo, portMAX_DELAY);
    vTaskDelay(pdMS_TO_TICKS(100));
  }
}

// ==========================================
// LOOP PRINCIPAL (FSM)
// ==========================================

void taskFSM(void *pvParameters) {
  TipoEvento eventoRecibido;
  while (1) {
    if (xQueueReceive(colaEventos, &eventoRecibido, portMAX_DELAY) == pdPASS) {
      /*Llegan eventos a esta cola de dos maneras: la FSM o desde las tareas de red*/      
      evento.tipo = eventoRecibido;
      estadoAnterior = estadoActual;

      if (SERIAL_ENABLED && estadoActual != estadoAnterior) {
        SerialPrint(
            "-------------------------------------------------------\n");
        SerialPrint("Estado actual: " + estadoToString(estadoActual) + "\n");
        SerialPrint(
            "-------------------------------------------------------\n");
      }
      switch (estadoActual) {

      //------------------------------------------
      case INIT: {
        switch (evento.tipo) {
        case EV_CONTINUE:
          gestorUI.mostrarPantallaInit();
          gestorSD.limpiarArchivosResiduales();
          // INIT tiene una duracion minima fija, el "vTaskDelayUntil" se asegura de que dure por lo menos 3 segundos
          TickType_t xUltimoTiempoDespertar = xTaskGetTickCount();
          #ifdef ACTIVAR_RED
            gestorMQTT.iniciarWiFi();
            gestorMQTT.conectarBroker();
            gestorHTTP.iniciar();
            xTaskCreate(taskRed,"TareaRedMQTT",TAM_STACK_RED,NULL, 1, &xTaskRedHandle);
          #endif

          vTaskDelayUntil(&xUltimoTiempoDespertar, xFrecuenciaEstricta);
          gestorUI.mostrarPantallaReposo();
          indiceContacto = 0;
          estadoActual = IDLE;
          #ifdef ACTIVAR_RED
            gestorMQTT.notificarLive();
          #endif
          break;
        }
      } break;

      //------------------------------------------
      case IDLE: {
        switch (evento.tipo) {
        case EV_RECIBIR_CONTACTOS:
            procesarContactosDesdeJson(payloadGlobal);
            cargarContactosDesdeMemoriaPersistente();
            Serial.println("Contactos actualizados desde JSON.");
          break;
        case EV_BTN_ARRIBA:
        case EV_BTN_ABAJO:
          estadoActual = NAVEGANDO;
          xTimerStart(xTimeoutTimer, 0);
          gestorUI.mostrarNavegandoContactos(listaContactos, indiceContacto);
          break;
        case EV_ENCONTRAR_DISPOSITIVO:
          gestorAudio.encenderBuzzer();
          delay(DELAY_ENCONTRAR_DISPOSITIVO);
          gestorAudio.apagarBuzzer();
          break;
        case EV_BTN_EMERGENCIA:
          estadoActual = EMERGENCIA;
          xTimerStop(xTimeoutTimer, 0);
          gestorUI.mostrarEmergencia();

          #ifdef ACTIVAR_RED
            gestorMQTT.notificarEmergencia();
          #endif
          break;

        case EV_BTN_EMERGENCIA_PRESS:
          xTimerReset(xTimeoutTimer, 0);
          break;
        }
      } break;

      //------------------------------------------
      case NAVEGANDO: {
        switch (evento.tipo) {
        case EV_BTN_ABAJO:
          indiceContacto =
              min(indiceContacto + 1,
                  gestorUI.obtenerCantidadEfectiva(listaContactos) - 1);
          if (indiceActual != indiceContacto)
            gestorUI.mostrarNavegandoContactos(listaContactos, indiceContacto);
          indiceActual = indiceContacto;
          xTimerReset(xTimeoutTimer, 0);
          break;

        case EV_BTN_ARRIBA:
          indiceContacto = max(indiceContacto - 1, 0);
          if (indiceActual != indiceContacto)
            gestorUI.mostrarNavegandoContactos(listaContactos, indiceContacto);
          indiceActual = indiceContacto;
          xTimerReset(xTimeoutTimer, 0);
          break;

        case EV_BTN_CONFIRMAR:
          estadoActual = CONFIRMAR_CONTACTO;
          xTimerReset(xTimeoutTimer, 0);
          gestorUI.mostrarConfirmarContacto(listaContactos[indiceContacto]);
          break;

        case EV_BTN_EMERGENCIA:
          estadoActual = EMERGENCIA;
          xTimerStop(xTimeoutTimer, 0);
          gestorUI.mostrarEmergencia();
          #ifdef ACTIVAR_RED
            gestorMQTT.notificarEmergencia();
          #endif
          break;
        case EV_BTN_EMERGENCIA_PRESS:
          xTimerReset(xTimeoutTimer, 0);
          break;

        case EV_TIMEOUT:
          estadoActual = IDLE;
          xTimerStop(xTimeoutTimer, 0);
          gestorUI.mostrarPantallaReposo();
          indiceContacto = 0;
          break;
        }
      } break;

      //------------------------------------------
      case CONFIRMAR_CONTACTO: {
        switch (evento.tipo) {
        case EV_BTN_CANCELAR:
          estadoActual = NAVEGANDO;
          gestorUI.mostrarNavegandoContactos(listaContactos, indiceContacto);
          xTimerReset(xTimeoutTimer, 0);
          break;

        case EV_BTN_CONFIRMAR:
          estadoActual = MENSAJE_PREDEFINIDO;
          xTimerReset(xTimeoutTimer, 0);
          gestorUI.mostrarMensajesPredefinidos(listaContactos[indiceContacto],
                                               listaMensajes, indiceMensaje);
          break;

        case EV_BTN_GRABAR:
          gestorAudio.confirmarInicioGrabacion();
          xTimerStop(xTimeoutTimer, 0);
          xTimerStart(xBuzzerTimer, 0);
          gestorAudio.leerYActualizarVolumen();
          estadoActual = INICIANDO_GRABACION;
          gestorUI.mostrarConfirmarContacto(listaContactos[indiceContacto]);
          break;

        case EV_BTN_EMERGENCIA:
          estadoActual = EMERGENCIA;
          xTimerStop(xTimeoutTimer, 0);
          gestorUI.mostrarEmergencia();
          #ifdef ACTIVAR_RED
            gestorMQTT.notificarEmergencia();
          #endif
          break;
        case EV_BTN_EMERGENCIA_PRESS:
          xTimerReset(xTimeoutTimer, 0);
          break;
        
        case EV_TIMEOUT:
          estadoActual = IDLE;
          gestorUI.mostrarPantallaReposo();
          indiceContacto = 0;
          xTimerStop(xTimeoutTimer, 0);
          break;
        }
      } break;

      case INICIANDO_GRABACION: {
        switch (evento.tipo) {
        case EV_BUZZER_FIN:
          gestorAudio.iniciarGrabacion("/archivoAudio.wav");
          estadoActual = GRABANDO;
          audioListoParaEnviar=false;
          xTimerStop(xBuzzerTimer, 0);
          xTimerStart(xRecordingTimer, 0);
          gestorUI.mostrarGrabando(listaContactos[indiceContacto]);
          grabandoAudio = true;
          xTaskCreate(taskGrabacionAudio, "GrabandoAudio", TAM_STACK_MIC, NULL, 3,
                      &xGrabacionTaskHandle);
          break;

        case EV_BTN_CANCELAR:
          gestorAudio.apagarBuzzer();
          estadoActual = CONFIRMAR_CONTACTO;
          xTimerStop(xBuzzerTimer, 0);
          xTimerStart(xTimeoutTimer, 0);
          gestorUI.mostrarConfirmarContacto(listaContactos[indiceContacto]);
          break;
        }
        // No hay tiempo suficiente para activar el boton de emergencia, por
        // tanto no es necesario modelarlo.
      } break;

      //------------------------------------------
      case GRABANDO: {
        switch (evento.tipo) {
        
        case EV_TIMEOUT:
        case EV_BTN_GRABAR:
          grabandoAudio = false;
          vTaskDelay(pdMS_TO_TICKS(50));
          
          xTaskNotifyGive(xProcesarSDHandle);
          // La FSM cambia de estado y actualiza la pantalla al instante 
          estadoActual = CONFIRMAR_AUDIO;
          gestorUI.mostrarConfirmarAudio();
          
          xTimerStop(xRecordingTimer, 0);
          xTimerStart(xTimeoutTimer, 0);
          break;

        case EV_BTN_EMERGENCIA:
          grabandoAudio = false;

          vTaskDelay(pdMS_TO_TICKS(50));
          // Incluso en emergencia, lo ideal es mandar a cerrar el archivo de fondo
          xTaskNotifyGive(xProcesarSDHandle);
    
          estadoActual = EMERGENCIA;
          gestorUI.mostrarEmergencia();
          xTimerStop(xRecordingTimer, 0);
          #ifdef ACTIVAR_RED
            gestorMQTT.notificarEmergencia();
          #endif
          break;
        }
      } break;

      case MENSAJE_PREDEFINIDO: {
        switch (evento.tipo) {
        case EV_BTN_ABAJO:
          indiceMensaje = (indiceMensaje == 2) ? 2 : indiceMensaje + 1;
          if (indiceMensaje != indiceMensajeActual)
            gestorUI.mostrarMensajesPredefinidos(listaContactos[indiceContacto],
                                                 listaMensajes, indiceMensaje);
          indiceMensajeActual = indiceMensaje;
          xTimerReset(xTimeoutTimer, 0);
          break;

        case EV_BTN_ARRIBA:
          indiceMensaje = (indiceMensaje == 0) ? 0 : indiceMensaje - 1;
          if (indiceMensaje != indiceMensajeActual)
            gestorUI.mostrarMensajesPredefinidos(listaContactos[indiceContacto],
                                                 listaMensajes, indiceMensaje);
          indiceMensajeActual = indiceMensaje;
          xTimerReset(xTimeoutTimer, 0);
          break;

        case EV_BTN_CONFIRMAR:
          #ifdef ACTIVAR_RED
            gestorMQTT.notificarMensajePredeterminado(listaContactos[indiceContacto].telefono, listaMensajes[indiceMensaje]);
          #endif
          estadoActual = ESPERANDO_WIFI;
          xTimerStart(xWiFiTimeoutTimer, 0);
          gestorUI.mostrarEnviando();
          xTimerStop(xTimeoutTimer, 0);
          break;

        case EV_BTN_CANCELAR:
          estadoActual = CONFIRMAR_CONTACTO;
          xTimerReset(xTimeoutTimer, 0);
          gestorUI.mostrarConfirmarContacto(listaContactos[indiceContacto]);
          break;

        case EV_BTN_EMERGENCIA:
          estadoActual = EMERGENCIA;
          xTimerStop(xTimeoutTimer, 0);
          gestorUI.mostrarEmergencia();
          #ifdef ACTIVAR_RED
            gestorMQTT.notificarEmergencia();
          #endif
          break;
        case EV_BTN_EMERGENCIA_PRESS:
          xTimerReset(xTimeoutTimer, 0);
          break;

        case EV_TIMEOUT:
          estadoActual = IDLE;
          gestorUI.mostrarPantallaReposo();
          indiceContacto = 0;
          xTimerStop(xTimeoutTimer, 0);
          break;
        }
      } break;

      //------------------------------------------
      case CONFIRMAR_AUDIO: {
        switch (evento.tipo) {
        case EV_BTN_CONFIRMAR:
          if (audioListoParaEnviar) {
            #ifdef ACTIVAR_RED
              gestorMQTT.notificarAudio(listaContactos[indiceContacto].telefono, "/descargar_audio", gestorSD.obtenerPesoAudioFinal());
            #endif
            estadoActual = ESPERANDO_WIFI;
            xTimerStart(xWiFiTimeoutTimer, 0);
            gestorUI.mostrarEnviando();
            xTimerStop(xTimeoutTimer, 0);
          } else {
            gestorUI.mostrarProcesandoAudio();
          }
          break;

        case EV_BTN_CANCELAR:
          estadoActual = CONFIRMAR_CONTACTO;
          gestorSD.eliminarArchivo();
          xTimerReset(xTimeoutTimer, 0);
          gestorUI.mostrarConfirmarContacto(listaContactos[indiceContacto]);
          break;

        case EV_BTN_EMERGENCIA:
          estadoActual = EMERGENCIA;
          gestorSD.eliminarArchivo();
          xTimerStop(xTimeoutTimer, 0);
          gestorUI.mostrarEmergencia();
          #ifdef ACTIVAR_RED
            gestorMQTT.notificarEmergencia();
          #endif
          break;
        case EV_BTN_EMERGENCIA_PRESS:
          xTimerReset(xTimeoutTimer, 0);
          break;

        case EV_TIMEOUT:
          estadoActual = ESPERANDO_WIFI;
          xTimerStart(xWiFiTimeoutTimer, 0);
          gestorUI.mostrarEnviando();
          xTimerStop(xTimeoutTimer, 0);
          break;
        }
      } break;

      //------------------------------------------
      case ESPERANDO_WIFI: {
        switch (evento.tipo) {
        case EV_WIFI_EXITO:
          estadoActual = MOSTRANDO_EXITO;
          gestorUI.mostrarExito();
          vTaskDelay(TIMEOUT_EXITO_FRACASO);
          gestorSD.eliminarArchivo();
          xTimerStart(xTimeoutTimer, 0);
          break;

        case EV_WIFI_ERROR:
          estadoActual = MOSTRANDO_ERROR;
          gestorUI.mostrarError();
          vTaskDelay(TIMEOUT_EXITO_FRACASO);
          gestorSD.eliminarArchivo();
          xTimerStart(xTimeoutTimer, 0);
          break;

        case EV_BTN_EMERGENCIA:
          estadoActual = EMERGENCIA;
          gestorSD.eliminarArchivo();
          xTimerStop(xTimeoutTimer, 0);
          gestorUI.mostrarEmergencia();
          #ifdef ACTIVAR_RED
            gestorMQTT.notificarEmergencia();
          #endif
          break;
        }
      } break;

      //------------------------------------------
      case MOSTRANDO_EXITO: {
        switch (evento.tipo) {

        case EV_CONTINUE:
          estadoActual = NAVEGANDO;
          gestorUI.mostrarNavegandoContactos(listaContactos, indiceContacto);
          break;
        }
      } break;

      //------------------------------------------
      case MOSTRANDO_ERROR: {
        switch (evento.tipo) {

        case EV_CONTINUE:
          estadoActual = NAVEGANDO;
          gestorUI.mostrarNavegandoContactos(listaContactos, indiceContacto);
        }
      } break;

      //------------------------------------------
      case EMERGENCIA: {
        switch (evento.tipo) {
        case EV_BTN_CANCELAR:
          estadoActual = IDLE;
          gestorUI.mostrarPantallaReposo();
          indiceContacto = 0;
          break;
        }
      } break;
      }

      // Consumo del evento
      evento.tipo = EV_CONTINUE;
    }
  }
}