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



HOJA_RESUMEN = "Resumen"
HOJA_COMPLEJIDAD = "Complejidad"
HOJA_MANTENIBILIDAD = "Mantenibilidad"
HOJA_BUGS_SMELLS = "Bugs_Smells"
HOJA_ERRORES = "Errores"
HOJA_CONCURRENCIA_SALIDA = "Concurrencia"
HOJA_TOKENS_SALIDA = "Tokens"

HOJA_CONCURRENCIA_ENTRADA = "Concurrencia"
HOJA_TOKENS_ENTRADA = "Datos Tokens"

ENCABEZADOS_RESUMEN = [
    "Código",
    "Nombre del proyecto",
    "Herramienta IA",
    "Modelo IA",
    "Lenguaje",
    "CCN promedio",
    "Nivel de CC",
    "MI",
    "Nivel de MI",
    "Issues/KLOC",
    "ISI",
    "Interpretación de Issue",
    "Promedio concurrencia",
    "Interpretación concurrencia",
    "Eficiencia de generación",
    "Interpretación tokens",    
]

ENCABEZADOS_COMPLEJIDAD = [
    "Código",
    "Cantidad de funciones",
    "CCN Total",
    "CCN promedio",
    "NLOC total",
    "Nivel de CC",
    "Interpretación CC",
    "NLOC promedio",
]

ENCABEZADOS_MANTENIBILIDAD = [
    "Código",
    "NLOC MI",
    "Cantidad de funciones MI",
    "Tokens código",
    "MI",
    "Nivel de MI",
    "Interpretación MI",
]

ENCABEZADOS_BUGS_SMELLS = [
    "Código",
    "Analizador",
    "Total de issues",
    "Issues/KLOC",
    "ISI",
    "Nivel de ISI",
    "Interpretación de ISI",
    "Observacion",
    "Cant. severidad alta",
    "Cant. severidad media",
    "Cant. severidad baja",
    "Reglas incumplidas",
]

ENCABEZADOS_ERRORES = [
    "Código",
    "Nombre del proyecto",
    "Error",
]

ENCABEZADOS_CONCURRENCIA_SALIDA = [
    "Código",
    "Sincronización correcta",
    "Ausencia de deadlocks",
    "Ausencia de condición de carrera",
    "Uso correcto de exclusión mutua",
    "Promedio concurrencia",
    "Interpretación concurrencia",
]

ENCABEZADOS_CONCURRENCIA_ENTRADA = [
    "Sincronización correcta",
    "Ausencia de deadlocks",
    "Ausencia de condición de carrera",
    "Uso correcto de exclusión mutua",
]

ENCABEZADOS_TOKENS_ENTRADA = [
    "Método utilizado",
    "Refinamientos",
    "Tokens registrados",
]

ENCABEZADOS_TOKENS_SALIDA = [
    "Código",
    "Método utilizado",
    "NlocTotal",
    "Refinamientos",
    "Tokens registrados",
    "Eficiencia de generación (NLOC/1000 tokens)",
    "Eficiencia ponderada",
    "Nivel de eficiencia",
    "Interpretación",
]

HOJAS_REPORTE = {
    HOJA_RESUMEN: ENCABEZADOS_RESUMEN,
    HOJA_COMPLEJIDAD: ENCABEZADOS_COMPLEJIDAD,
    HOJA_MANTENIBILIDAD: ENCABEZADOS_MANTENIBILIDAD,
    HOJA_BUGS_SMELLS: ENCABEZADOS_BUGS_SMELLS,
    HOJA_ERRORES: ENCABEZADOS_ERRORES,
    HOJA_CONCURRENCIA_SALIDA: ENCABEZADOS_CONCURRENCIA_SALIDA,
    HOJA_TOKENS_SALIDA: ENCABEZADOS_TOKENS_SALIDA,
}

CONFIGURACION_GRAFICOS = [
    (HOJA_RESUMEN, 6, "CCN promedio por proyecto", "CCN promedio", "A1"),
    (HOJA_COMPLEJIDAD, 5, "NLOC total por proyecto", "NLOC total", "A18"),
    (HOJA_MANTENIBILIDAD, 5, "MI por proyecto", "Índice de mantenibilidad", "A35"),
    (HOJA_BUGS_SMELLS, 5, "ISI por proyecto", "Índice de severidad de issues", "A52"),
    (HOJA_RESUMEN, 13, "Promedio de concurrencia por proyecto", "Promedio concurrencia", "A69"),
]

CANTIDAD_CAMPOS_COMPLEJIDAD = len(ENCABEZADOS_COMPLEJIDAD) - 1
CANTIDAD_CAMPOS_MANTENIBILIDAD = len(ENCABEZADOS_MANTENIBILIDAD) - 1
CANTIDAD_CAMPOS_CONCURRENCIA = len(ENCABEZADOS_CONCURRENCIA_SALIDA) - 1
CANTIDAD_CAMPOS_BUGS_SMELLS = len(ENCABEZADOS_BUGS_SMELLS) - 1
CANTIDAD_CAMPOS_TOKENS = len(ENCABEZADOS_TOKENS_SALIDA) - 1
