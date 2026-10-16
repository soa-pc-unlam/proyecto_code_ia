#include <Arduino.h>
class GestorBoton {
  private:
    int pin;
    unsigned long tiempoRequerido;
    unsigned long tiempoInicioPresion;
    bool botonEstaPresionado;
    bool estadoAnterior;

  public:
    // inicializa el gestor con el pin y el tiempo específico
    GestorBoton(int pinAsignado, unsigned long tiempoAsignado) {
        pin = pinAsignado;
        tiempoRequerido = tiempoAsignado;
        tiempoInicioPresion = 0;
        botonEstaPresionado = false;
        estadoAnterior = HIGH;
    }

    // Método principal con la lógica de negocio del botón
    bool estaMantenido() {
        int lecturaBoton = digitalRead(pin);

        if (lecturaBoton == LOW) {
            if (!botonEstaPresionado) {
                botonEstaPresionado = true;
                tiempoInicioPresion = millis();
            } else {
                // Ya estaba presionado, verificamos el cronómetro
                if (millis() - tiempoInicioPresion >= tiempoRequerido) {
                    botonEstaPresionado = false; // Reiniciamos para evitar múltiples gatillos
                    return true;
                }
            }
        } else {
            // El botón se soltó, reiniciamos el estado
            botonEstaPresionado = false;
        }

        return false; // Aún no se cumplió el tiempo o no está presionado
    }

    bool leerUnClic() {
        bool estadoActual = digitalRead(pin);
        bool clicDetectado = (estadoAnterior == HIGH && estadoActual == LOW);
        estadoAnterior = estadoActual;
        return clicDetectado;
    }
};