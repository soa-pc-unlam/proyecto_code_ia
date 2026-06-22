import unicodedata
from pathlib import Path

from openpyxl import load_workbook

from util.modelos import MetricaConcurrencia

HOJA_CONCURRENCIA = "Concurrencia"
CAMPOS_RUBRICA = [
    "Sincronización correcta",
    "Ausencia de deadlocks",
    "Ausencia de condición de carrera",
    "Uso correcto de exclusión mutua",
]


def normalizar_texto(texto):
    texto = "" if texto is None else str(texto).strip().lower()
    texto = unicodedata.normalize("NFKD", texto)
    return "".join(c for c in texto if not unicodedata.combining(c))


def cargar_hoja_concurrencia(archivo_datos_entrada):
    ruta = Path(archivo_datos_entrada)
    if not ruta.exists():
        raise FileNotFoundError(f"No se encontró el archivo: {ruta}")
    libro = load_workbook(ruta, data_only=True)
    if HOJA_CONCURRENCIA not in libro.sheetnames:
        raise ValueError(f"No existe la solapa '{HOJA_CONCURRENCIA}' en {ruta}")
    return libro[HOJA_CONCURRENCIA]


def mapear_encabezados(hoja):
    encabezados = {}
    for columna in range(1, hoja.max_column + 1):
        valor = hoja.cell(row=1, column=columna).value
        encabezados[normalizar_texto(valor)] = columna
    return encabezados


def obtener_columna(encabezados, nombre):
    columna = encabezados.get(normalizar_texto(nombre))
    if columna is None:
        raise ValueError(f"Falta la columna '{nombre}' en la solapa Concurrencia")
    return columna


def buscar_fila_rubrica(hoja, columna_codigo, codigo):
    for fila in range(2, hoja.max_row + 1):
        valor = hoja.cell(row=fila, column=columna_codigo).value
        if normalizar_texto(valor) == normalizar_texto(codigo):
            return fila
    return None


def leer_valores_rubrica(hoja, fila, encabezados):
    valores = {}
    for campo in CAMPOS_RUBRICA:
        columna = obtener_columna(encabezados, campo)
        valores[campo] = hoja.cell(row=fila, column=columna).value
    return valores


def obtener_puntaje(valor, ponderacion):
    nivel = normalizar_texto(valor).capitalize()
    if nivel not in ponderacion:
        raise ValueError(f"Nivel de concurrencia inválido: {valor}")
    return ponderacion[nivel]


def calcular_puntajes(valores, ponderacion):
    puntajes = []
    for valor in valores.values():
        puntajes.append(obtener_puntaje(valor, ponderacion))
    return puntajes


def calcular_promedio(puntajes):
    if not puntajes:
        return 0
    return round(sum(puntajes) / len(puntajes), 2)


def interpretar_concurrencia(promedio, umbrales):
    for umbral in umbrales:
        minimo = umbral["min"]
        maximo = umbral["max"]
        if maximo is None and promedio >= minimo:
            return umbral["interpretacion"]
        if maximo is not None and minimo <= promedio <= maximo:
            return umbral["interpretacion"]
    return "Sin interpretación"


def crear_metrica_concurrencia(codigo, valores, ponderacion, umbrales):
    puntajes = calcular_puntajes(valores, ponderacion)
    promedio = calcular_promedio(puntajes)
    interpretacion = interpretar_concurrencia(promedio, umbrales)
    return MetricaConcurrencia(
        codigo=codigo,
        sincronizacion_correcta=valores["Sincronización correcta"],
        ausencia_de_deadlocks=valores["Ausencia de deadlocks"],
        ausencia_de_condicion_de_carrera=valores["Ausencia de condición de carrera"],
        uso_correcto_de_exclusion_mutua=valores["Uso correcto de exclusión mutua"],
        promedio=promedio,
        interpretacion=interpretacion,
    )


def analizar_concurrencia(proyecto, archivo_datos_entrada, ponderacion, umbrales):
    hoja = cargar_hoja_concurrencia(archivo_datos_entrada)
    encabezados = mapear_encabezados(hoja)
    columna_codigo = obtener_columna(encabezados, "Código")
    fila = buscar_fila_rubrica(hoja, columna_codigo, proyecto.codigo)
    if fila is None:
        raise ValueError(f"No se encontró el código '{proyecto.codigo}' en Concurrencia")
    valores = leer_valores_rubrica(hoja, fila, encabezados)
    return crear_metrica_concurrencia(proyecto.codigo, valores, ponderacion, umbrales)
