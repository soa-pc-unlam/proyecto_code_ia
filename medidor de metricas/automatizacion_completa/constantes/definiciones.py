"""Define las constantes generales de la aplicación."""

DATOS_PROYECTOS_JSON = "datos_entrada/proyectos.json"
CONFIGURACION_JSON = "configuracion/configuracion.json"


# Cantidad máxima de proyectos procesados simultáneamente.
MAX_WORKERS = 3

# Cantidad máxima de analizadores pesados ejecutándose simultáneamente.
MAX_ANALIZADORES_PESADOS = 1

# Fracción máxima de CPUs lógicas que puede utilizar el programa.
PORCENTAJE_MAX_CPU = 0.65

# Modo de logging
MODO_LOGGING = "INFO"  # DEBUG, INFO, WARNING, ERROR, CRITICAL