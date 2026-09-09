"""Cálculo de eficiencia en tokens de generación de código."""

import math
from numbers import Real

from constantes.definiciones import (
    ENCABEZADOS_TOKENS_ENTRADA,
    HOJA_TOKENS_ENTRADA,
)
from modelos.modelos import MetricaTokens
from reportes.excel import leer_fila_por_codigo


def validar_numero(valor, nombre, permitir_cero=True):
    """Valida un número finito y no negativo."""

    if isinstance(valor, bool) or not isinstance(valor, Real):
        raise ValueError(f"{nombre} debe ser un valor numérico válido")

    if not math.isfinite(valor) or valor < 0:
        raise ValueError(f"{nombre} debe ser un valor numérico no negativo")

    if not permitir_cero and valor == 0:
        raise ValueError(f"{nombre} debe ser mayor que cero")

    return valor


def validar_refinamientos(valor):
    """Valida una cantidad entera y no negativa de refinamientos."""

    validar_numero(valor, "Refinamientos")

    if not float(valor).is_integer():
        raise ValueError("Refinamientos debe representar un número entero")

    return int(valor)


def validar_metodo(valor):
    """Valida y normaliza el método utilizado."""

    metodo = "" if valor is None else str(valor).strip()

    if not metodo:
        raise ValueError("Método utilizado no debe estar vacío")

    return metodo


def calcular_eficiencias(nloc_total, tokens, refinamientos, coeficiente):
    """Calcula las eficiencias sin redondearlas."""

    eficiencia = nloc_total / tokens * 1000
    denominador = 1 + coeficiente * refinamientos

    if denominador <= 0:
        raise ValueError("El denominador de eficiencia ponderada debe ser mayor que cero")

    return eficiencia, eficiencia / denominador


def clasificar_eficiencia(valor):
    """Clasifica la eficiencia ponderada y devuelve su interpretación."""

    if valor <= 10:
        return "Bajo", "Baja NLOC/token, ajustada por refinamientos."
    if valor < 30:
        return "Medio", "Media NLOC/token, ajustada por refinamientos."

    return "Alto", "Alta NLOC/token, ajustada por refinamientos."


def crear_metrica_tokens(codigo, valores, nloc_total, coeficiente):
    """Valida los datos y construye el resultado de tokens."""

    metodo = validar_metodo(valores["Método utilizado"])
    refinamientos = validar_refinamientos(valores["Refinamientos"])
    tokens = validar_numero(valores["Tokens registrados"], "Tokens registrados", False)
    nloc = validar_numero(nloc_total, "NlocTotal")

    eficiencia, ponderada = calcular_eficiencias(nloc, tokens, refinamientos, coeficiente)
    nivel, interpretacion = clasificar_eficiencia(ponderada)

    return MetricaTokens(codigo, metodo, nloc, refinamientos, tokens,
                         round(eficiencia, 2), round(ponderada, 2),
                         nivel, interpretacion)


def analizar_tokens(proyecto, libro_entrada, nloc_total, coeficiente, logger):
    """Lee y analiza los datos de tokens correspondientes a un proyecto."""
    logger.debug(f"[{proyecto.codigo}] Leyendo datos de tokens")

    valores = leer_fila_por_codigo(
        libro_entrada, HOJA_TOKENS_ENTRADA, proyecto.codigo,
        ENCABEZADOS_TOKENS_ENTRADA,
    )
    return crear_metrica_tokens(proyecto.codigo, valores, nloc_total, coeficiente)
