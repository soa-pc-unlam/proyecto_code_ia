"""Cálculo de métricas de uso de CPU y memoria en PC."""

import math
from numbers import Real

from configuracion.configuracion import clasificar_uso_cpu, clasificar_uso_mem
from constantes.definiciones import (
    ENCABEZADOS_CPU_MEMORIA_ENTRADA,
    HOJA_CPU_MEMORIA_ENTRADA,
    MODO_CPU_NUCLEO,
    MODO_CPU_TOTAL,
    TEXTO_CAMPO_UMBRALES_CPU,
    TEXTO_CAMPO_UMBRALES_MEM,
    TEXTO_CPU_MEDIDA,
    TEXTO_ENCABEZADO_CPU_ACC1,
    TEXTO_ENCABEZADO_CPU_ACC2,
    TEXTO_ENCABEZADO_CPUS_LOGICAS,
    TEXTO_ENCABEZADO_MEMORIA_ACC1_MB,
    TEXTO_ENCABEZADO_MEMORIA_ACC2_MB,
    TEXTO_ENCABEZADO_MEMORIA_TOTAL_ENTRADA_MB,
    TEXTO_ENCABEZADO_METODO_MEDICION,
    TEXTO_ENCABEZADO_MODO_CPU,
)
from modelos.modelos import MetricaCpuMemoria
from reportes.excel import leer_fila_por_codigo


def validar_numero_opcional(valor, nombre):
    """Valida un número finito no negativo o devuelve None si está vacío."""

    if valor is None or valor == "":
        return None

    if isinstance(valor, bool) or not isinstance(valor, Real):
        raise ValueError(f"{nombre} debe ser un valor numérico válido")

    if not math.isfinite(valor) or valor < 0:
        raise ValueError(f"{nombre} debe ser un valor numérico no negativo")

    return float(valor)


def validar_cpus_logicas(valor, obligatorio=False):
    """Valida la cantidad de CPUs lógicas cuando es necesaria."""

    if valor is None or valor == "":
        if obligatorio:
            raise ValueError("CPUs lógicas debe ser un entero mayor que cero")
        return None

    if isinstance(valor, bool) or not isinstance(valor, Real):
        raise ValueError("CPUs lógicas debe ser un entero mayor que cero")

    if not float(valor).is_integer() or valor <= 0:
        raise ValueError("CPUs lógicas debe ser un entero mayor que cero")

    return int(valor)


def validar_modo_cpu(valor, hay_medicion_cpu):
    """Valida el modo de CPU si existe al menos una medición."""

    modo = "" if valor is None else str(valor).strip()

    if not hay_medicion_cpu and not modo:
        return ""

    if modo not in (MODO_CPU_NUCLEO, MODO_CPU_TOTAL):
        raise ValueError(f"Modo CPU inválido: {valor}")

    return modo


def convertir_porcentaje(valor, formato):
    """Convierte una celda porcentual de Excel a porcentaje numérico."""
    valor = validar_numero_opcional(valor, TEXTO_CPU_MEDIDA)

    if valor is None:
        return None

    if formato and "%" in formato:
        return valor * 100

    return valor


def normalizar_cpu(valor, modo_cpu, cpus_logicas):
    """Normaliza una medición de CPU según el modo informado."""

    if valor is None:
        return None

    if modo_cpu == MODO_CPU_NUCLEO:
        return valor / cpus_logicas

    return valor


def calcular_promedio(valores):
    """Calcula el promedio usando solamente mediciones disponibles."""
    disponibles = [valor for valor in valores if valor is not None]

    if not disponibles:
        return None

    return sum(disponibles) / len(disponibles)


def calcular_maximo(valores):
    """Calcula el máximo usando solamente mediciones disponibles."""
    disponibles = [valor for valor in valores if valor is not None]

    return max(disponibles) if disponibles else None




def obtener_cpu(valores, formatos):
    """Valida y normaliza las dos mediciones de CPU."""

    cpu_1 = convertir_porcentaje(valores[TEXTO_ENCABEZADO_CPU_ACC1], formatos[TEXTO_ENCABEZADO_CPU_ACC1])
    cpu_2 = convertir_porcentaje(valores[TEXTO_ENCABEZADO_CPU_ACC2], formatos[TEXTO_ENCABEZADO_CPU_ACC2])
    modo = validar_modo_cpu(valores[TEXTO_ENCABEZADO_MODO_CPU], cpu_1 is not None or cpu_2 is not None)
    cpus = validar_cpus_logicas(valores[TEXTO_ENCABEZADO_CPUS_LOGICAS], modo == MODO_CPU_NUCLEO)

    return cpu_1, cpu_2, modo, cpus


def obtener_memoria(valores):
    """Valida las mediciones de memoria y la memoria total."""

    memoria_1 = validar_numero_opcional(valores[TEXTO_ENCABEZADO_MEMORIA_ACC1_MB], TEXTO_ENCABEZADO_MEMORIA_ACC1_MB)
    memoria_2 = validar_numero_opcional(valores[TEXTO_ENCABEZADO_MEMORIA_ACC2_MB], TEXTO_ENCABEZADO_MEMORIA_ACC2_MB)
    total = validar_numero_opcional(valores[TEXTO_ENCABEZADO_MEMORIA_TOTAL_ENTRADA_MB], TEXTO_ENCABEZADO_MEMORIA_TOTAL_ENTRADA_MB)

    return memoria_1, memoria_2, total


def crear_metrica_cpu_memoria(proyecto, valores, formatos, configuracion_cpu, configuracion_memoria):
    """Valida los datos y construye las métricas de CPU y memoria."""

    cpu_1, cpu_2, modo, cpus = obtener_cpu(valores, formatos)
    norm_1 = normalizar_cpu(cpu_1, modo, cpus)
    norm_2 = normalizar_cpu(cpu_2, modo, cpus)
    cpu_promedio = calcular_promedio([norm_1, norm_2])
    cpu_maxima = calcular_maximo([norm_1, norm_2])
    memoria_1, memoria_2, memoria_total = obtener_memoria(valores)
    memoria_promedio = calcular_promedio([memoria_1, memoria_2])
    memoria_maxima = calcular_maximo([memoria_1, memoria_2])

    if memoria_promedio is not None and memoria_total and memoria_total > 0:
        memoria_norm = (memoria_promedio / memoria_total) * 100 
    else:
        memoria_norm=None

    return construir_resultado(proyecto, valores, configuracion_cpu, configuracion_memoria, cpu_1, cpu_2, modo, cpus, norm_1, norm_2, cpu_promedio, cpu_maxima, memoria_1, memoria_2, memoria_total, memoria_promedio, memoria_norm, memoria_maxima)


def construir_resultado(proyecto, valores, configuracioncpu, configuracionmemoria, cpu_1, cpu_2, modo, cpus, norm_1, norm_2, cpu_promedio, cpu_maxima, memoria_1, memoria_2, memoria_total, memoria_promedio, memoria_norm, memoria_maxima):
    """Construye el objeto final conservando valores sin redondear."""

    # Clasificación de CPU
    nivel_cpu, int_cpu = clasificar_uso_cpu(cpu_promedio, configuracioncpu)

    # Clasificación de memoria
    nivel_mem, int_mem = clasificar_uso_mem(memoria_norm, configuracionmemoria)

    return MetricaCpuMemoria(proyecto.codigo, proyecto.lenguaje, str(valores[TEXTO_ENCABEZADO_METODO_MEDICION] or "").strip(), modo, cpus, str(valores["Acción 1"] or "").strip(), cpu_1, str(valores["Acción 2"] or "").strip(), cpu_2, norm_1, norm_2, cpu_promedio, cpu_maxima, nivel_cpu, int_cpu, memoria_1, memoria_2, memoria_total, memoria_promedio, memoria_norm, memoria_maxima, nivel_mem, int_mem)


def analizar_cpu_memoria(proyecto, libro_entrada, configuracion_cpu, configuracion_memoria, logger):
    """Lee y analiza los datos de CPU y memoria de un proyecto."""

    logger.debug(f"[{proyecto.codigo}] Leyendo datos de CPU y memoria")

    valores, formatos = leer_fila_por_codigo(
        libro_entrada=libro_entrada,
        nombre_hoja=HOJA_CPU_MEMORIA_ENTRADA,
        codigo=proyecto.codigo,
        campos=ENCABEZADOS_CPU_MEMORIA_ENTRADA,
        incluir_formato=True
    )

    return crear_metrica_cpu_memoria(proyecto, valores, formatos, configuracion_cpu, configuracion_memoria)
