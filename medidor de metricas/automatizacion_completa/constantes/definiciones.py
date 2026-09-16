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

MODO_CPU_NUCLEO = "100%=1 núcleo"
MODO_CPU_TOTAL = "100%=capacidad total"

HOJA_RESUMEN = "Resumen"
HOJA_COMPLEJIDAD = "Complejidad"
HOJA_MANTENIBILIDAD = "Mantenibilidad"
HOJA_BUGS_SMELLS = "Bugs_Smells"
HOJA_ERRORES = "Errores"
HOJA_CONCURRENCIA_SALIDA = "Concurrencia"
HOJA_TOKENS_SALIDA = "Tokens"
HOJA_CPU_MEMORIA_SALIDA = "CPU-Memoria PC"

HOJA_CONCURRENCIA_ENTRADA = "Concurrencia"
HOJA_TOKENS_ENTRADA = "Datos Tokens"
HOJA_CPU_MEMORIA_ENTRADA = "CPU_Memoria"

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
    "% CPU promedio",
    "Interpretación CPU",
    "% Memoria promedio",
    "Interpretación memoria",  
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


ENCABEZADOS_CPU_MEMORIA_ENTRADA = [
    "Método medición",
    "Modo CPU",
    "CPUs lógicas",
    "Acción 1",
    "CPU Acc1",
    "Acción 2",
    "CPU Acc2",
    "Memoria Acc1 (MB)",
    "Memoria Acc2 (MB)",
    "MemoriaTotal (MB)",
]

ENCABEZADOS_CPU_MEMORIA_SALIDA = [
    "Código",
    "Lenguaje",
    "Método medición",
    "Modo CPU",
    "CPUs lógicas",
    "Acción 1",
    "CPU medida 1",
    "Acción 2",
    "CPU medida 2",
    "CPU normalizada 1",
    "CPU normalizada 2",
    "CPU promedio normalizada",
    "CPU máxima normalizada",
    "Nivel CPU",
    "Interpretación CPU",
    "Memoria Acc1 (MB)",
    "Memoria Acc2 (MB)",
    "Memoria de total (MB)",
    "Memoria promedio (MB)",
    "Memoria normalizada promedio (%)",
    "Memoria máxima (MB)",
    "Nivel memoria",
    "Interpretación memoria",
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
    HOJA_CPU_MEMORIA_SALIDA: ENCABEZADOS_CPU_MEMORIA_SALIDA,
}

CAMPOS_OBLIGATORIOS_CONFIGURACION_JSON = [
        "archivo_excel_entrada",
        "archivo_excel_salida",
        "carpeta_resultados",
        "carpeta_logs",
        "umbrales_cc",
        "umbrales_mi",
        "umbrales_issues",
        "umbrales_isi",
        "ponderacion_concurrencia",
        "umbrales_concurrencia",
        "coeficiente_penalizacion_tokens",
        "uso_cpu",
        "uso_memoria",
]

COLUMNA_CCN_PROMEDIO = 6
COLUMNA_NLOC_TOTAL = 5
COLUMNA_MI = 8
COLUMNA_ISI = 11
COLUMNA_PROMEDIO_CONCURRENCIA = 13
COLUMNA_EFICIENCIA_TOKENS = 15

FILA_GRAFICO_CCN = 1
FILA_GRAFICO_NLOC = FILA_GRAFICO_CCN+18
FILA_GRAFICO_MI = FILA_GRAFICO_NLOC*2
FILA_GRAFICO_ISI = FILA_GRAFICO_NLOC*3
FILA_GRAFICO_CONCURRENCIA = FILA_GRAFICO_NLOC*4
FILA_GRAFICO_TOKENS = FILA_GRAFICO_NLOC*5

CONFIGURACION_GRAFICOS = [
    #hoja, columna_datos, titulo, etiqueta, posicion_del_grafico
    (HOJA_RESUMEN, COLUMNA_CCN_PROMEDIO, "CCN promedio por proyecto", "CCN promedio", f"A{FILA_GRAFICO_CCN}"),
    (HOJA_COMPLEJIDAD, COLUMNA_NLOC_TOTAL, "NLOC total por proyecto", "NLOC total", f"A{FILA_GRAFICO_NLOC}"),
    (HOJA_RESUMEN, COLUMNA_MI, "MI por proyecto", "Índice de mantenibilidad", f"A{FILA_GRAFICO_MI}"),
    (HOJA_RESUMEN, COLUMNA_ISI, "ISI por proyecto", "Índice de severidad de issues", f"A{FILA_GRAFICO_ISI}"),
    (HOJA_RESUMEN, COLUMNA_PROMEDIO_CONCURRENCIA, "Promedio de concurrencia por proyecto", "Promedio concurrencia", f"A{FILA_GRAFICO_CONCURRENCIA}"),
    (HOJA_RESUMEN, COLUMNA_EFICIENCIA_TOKENS, "Eficiencia de generación por proyecto", "Eficiencia de generación", f"A{FILA_GRAFICO_TOKENS}"),
]

CANTIDAD_CAMPOS_COMPLEJIDAD = len(ENCABEZADOS_COMPLEJIDAD) - 1
CANTIDAD_CAMPOS_MANTENIBILIDAD = len(ENCABEZADOS_MANTENIBILIDAD) - 1
CANTIDAD_CAMPOS_CONCURRENCIA = len(ENCABEZADOS_CONCURRENCIA_SALIDA) - 1
CANTIDAD_CAMPOS_BUGS_SMELLS = len(ENCABEZADOS_BUGS_SMELLS) - 1
CANTIDAD_CAMPOS_TOKENS = len(ENCABEZADOS_TOKENS_SALIDA) - 1
CANTIDAD_CAMPOS_CPU_MEMORIA = len(ENCABEZADOS_CPU_MEMORIA_SALIDA) - 1
