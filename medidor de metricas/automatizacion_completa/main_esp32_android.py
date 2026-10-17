"""Punto de entrada para el análisis de proyectos de ESP32 y Android."""

from constantes import definiciones
from procesamiento.procesamiento_comun import ejecutar_analisis


def main():
    """Procesa ESP32 y Android con sus propias entradas y destinos de salida."""
    ejecutar_analisis(definiciones.TIPO_PROYECTO_ESP32_ANDROID)


if __name__ == "__main__":
    main()
