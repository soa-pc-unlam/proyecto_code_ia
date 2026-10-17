"""Punto de entrada para el análisis de proyectos de PC."""

from constantes import definiciones
from procesamiento.procesamiento_comun import ejecutar_analisis


def main():
    """Procesa los proyectos de PC con la configuración habitual."""
    ejecutar_analisis(definiciones.TIPO_PROYECTO_PC)


if __name__ == "__main__":
    main()
