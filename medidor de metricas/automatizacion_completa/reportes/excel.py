"""Lectura, creación y actualización de archivos Excel."""

from pathlib import Path
import unicodedata

from openpyxl import Workbook, load_workbook
from openpyxl.styles import Font, PatternFill, Alignment

from reportes.graficos import generar_graficos
from constantes.definiciones import (
    CANTIDAD_CAMPOS_COMPLEJIDAD,
    CANTIDAD_CAMPOS_CONCURRENCIA,
    CANTIDAD_CAMPOS_MANTENIBILIDAD,
    CANTIDAD_CAMPOS_BUGS_SMELLS,
    HOJAS_REPORTE,
    HOJA_RESUMEN,
    HOJA_COMPLEJIDAD,
    HOJA_MANTENIBILIDAD,
    HOJA_BUGS_SMELLS,
    HOJA_CONCURRENCIA_SALIDA,
    HOJA_TOKENS_SALIDA,
    CONFIGURACION_GRAFICOS,
    HOJA_ERRORES,
    CANTIDAD_CAMPOS_TOKENS,
    CANTIDAD_CAMPOS_CPU_MEMORIA,
    HOJA_CPU_MEMORIA_SALIDA,
)

def crear_o_abrir_excel_salida(archivo_excel):
    """Abre un libro existente o crea uno con las hojas requeridas.

    Args:
        archivo_excel: Ruta del libro de resultados.

    Returns:
        El libro preparado para recibir datos.
    """
    if Path(archivo_excel).exists():
        libro = load_workbook(archivo_excel)
    else:
        libro = Workbook()
        libro.remove(libro.active)

    crear_hojas_si_no_existen(libro)
    return libro

def abrir_excel_entrada(archivo_excel):
    """Abre un libro existente para lectura de datos.

    Args:
        archivo_excel: Ruta del libro de entrada.

    Returns:
        El libro abierto en modo lectura.

    Raises:
        FileNotFoundError: Si el archivo de entrada no existe.
    """
    if not Path(archivo_excel).exists():
        raise FileNotFoundError(f"No se encontró el archivo de entrada: {archivo_excel}")

    return load_workbook(archivo_excel, data_only=True, read_only=True)

def asegurar_encabezados(hoja, encabezados):
    """Crea o actualiza encabezados sin borrar datos existentes.

    Args:
        hoja: Hoja cuyos encabezados deben actualizarse.
        encabezados: Encabezados que deben estar presentes.
    """
    if hoja.max_row == 1 and all(celda.value is None for celda in hoja[1]):
        hoja.append(encabezados)
        hoja.delete_rows(1)
        return

    encabezados_actuales = [hoja.cell(row=1, column=col).value for col in range(1, hoja.max_column + 1)]

    for encabezado in encabezados:
        if encabezado not in encabezados_actuales:
            hoja.cell(row=1, column=hoja.max_column + 1).value = encabezado
            encabezados_actuales.append(encabezado)



def eliminar_columnas_obsoletas(hoja, encabezados_validos):
    """Elimina las columnas ajenas al diseño esperado.

    Args:
        hoja: Hoja que se desea depurar.
        encabezados_validos: Encabezados que deben conservarse.
    """
    encabezados_validos = set(encabezados_validos)

    for columna in range(hoja.max_column, 0, -1):
        encabezado = hoja.cell(row=1, column=columna).value
        if encabezado is not None and encabezado not in encabezados_validos:
            hoja.delete_cols(columna)

def crear_hojas_si_no_existen(libro):
    """Crea y prepara todas las hojas requeridas por el reporte.

    Args:
        libro: Libro de Excel que se desea preparar.
    """
    for nombre, encabezados in HOJAS_REPORTE.items():
        if nombre not in libro.sheetnames:
            libro.create_sheet(nombre).append(encabezados)
            continue

        hoja = libro[nombre]
        asegurar_encabezados(hoja, encabezados)

        if nombre == HOJA_BUGS_SMELLS:
            eliminar_columnas_obsoletas(hoja, encabezados)


def aplicar_estilos_basicos(libro):
    """Aplica formato a encabezados y ajusta anchos de columnas.

    Args:
        libro: Libro de Excel que se desea formatear.
    """
    for hoja in libro.worksheets:
        for celda in hoja[1]:
            celda.font = Font(bold=True)
            celda.fill = PatternFill("solid", fgColor="D9EAF7")
            celda.alignment = Alignment(horizontal="center")

        for columna in hoja.columns:
            max_largo = 0
            letra = columna[0].column_letter

            for celda in columna:
                if celda.value is not None:
                    max_largo = max(max_largo, len(str(celda.value)))

            hoja.column_dimensions[letra].width = min(max_largo + 3, 45)


def normalizar_texto(texto):
    """Convierte un valor a texto sin acentos, mayúsculas ni espacios externos.

    Args:
        texto: Valor que se desea normalizar; None se trata como una cadena vacía.

    Returns:
        str: Texto normalizado; conserva los espacios internos.
    """
    texto = "" if texto is None else str(texto).strip().lower()
    texto = unicodedata.normalize("NFKD", texto)
    return "".join(
        caracter for caracter in texto
        if not unicodedata.combining(caracter)
    )

def formatear_celdas_hoja_bug_smells(hoja, codigo):
    """Aplica formato a las celdas de la hoja de bugs y smells.

    Args:
        hoja: Hoja de Excel donde se escriben los datos.
        codigo: Código del proyecto.
    """
    filas = buscar_filas_por_codigo(hoja, codigo)

    if len(filas) > 1:
        raise ValueError(f"Código duplicado '{codigo}' en {hoja.title}")

    if filas:
        fila = filas[0]
        columna_reglas = obtener_mapa_encabezados(hoja)["Reglas incumplidas"]
        celda_top_reglas = hoja.cell(row=fila, column=columna_reglas)

        celda_top_reglas.alignment = Alignment(wrap_text=True,vertical="top")

        hoja.column_dimensions[celda_top_reglas.column_letter].width = 45

def buscar_filas_por_codigo(hoja, codigo, columna_codigo=1, normalizar=True):
    """Devuelve todas las filas cuyo código coincide con el solicitado."""

    valor_buscado = normalizar_texto(codigo) if normalizar else codigo
    filas = []

    for fila in range(2, hoja.max_row + 1):
        valor_celda = hoja.cell(
            row=fila,
            column=columna_codigo
        ).value

        if normalizar:
            valor_celda = normalizar_texto(valor_celda)

        if valor_celda == valor_buscado:
            filas.append(fila)

    return filas


def escribir_o_actualizar_fila(hoja, codigo, valores):
    """Agrega una fila o actualiza la que corresponde a un código.

    Args:
        hoja: Hoja que se desea modificar.
        codigo: Código usado como clave de la fila.
        valores: Valores que deben escribirse.
    """
    filas_existentes = buscar_filas_por_codigo(hoja, codigo)

    if len(filas_existentes) > 1:
        raise ValueError(f"Código duplicado '{codigo}' en {hoja.title}")
    elif not filas_existentes:
        hoja.append(valores)
    else:
        fila_existente = filas_existentes[0]

        for columna, valor in enumerate(valores, start=1):
            hoja.cell(row=fila_existente, column=columna).value = valor


def escribir_valores_por_encabezado(hoja, fila, valores):
    """Escribe varios valores en columnas identificadas por sus encabezados.

    Args:
        hoja: Hoja que se desea modificar.
        fila: Número de fila de destino.
        valores: Pares formados por encabezado y valor.
    """
    encabezados = obtener_mapa_encabezados(hoja)

    for encabezado, valor in valores.items():
        columna = encabezados.get(encabezado)
        if columna is not None:
            hoja.cell(row=fila, column=columna).value = valor


def obtener_mapa_encabezados(hoja, normalizar=False):
    """Crea un mapa entre encabezados y números de columna.

    Args:
        hoja: Hoja que se desea inspeccionar.
        normalizar: Si es True, normaliza el texto de los encabezados.
        normalizar: Si es True, normaliza el texto de los encabezados.

    Returns:
        Diccionario de encabezados a columnas.
    """
    primera_fila = next(hoja.iter_rows(min_row=1, max_row=1,
                                       values_only=True), ())
    return {
        (normalizar_texto(valor) if normalizar else valor): columna
        for columna, valor in enumerate(primera_fila, start=1)
    }


def obtener_columna_encabezado(encabezados, nombre, nombre_hoja):
    """Obtiene la columna de un encabezado obligatorio.

    Args:
        encabezados (dict): Mapa de encabezados normalizados a números de columna.
        nombre (str): Encabezado buscado, que se normaliza antes de consultar.
        nombre_hoja (str): Nombre de la hoja usado en el mensaje de error.

    Returns:
        int: Número de columna, contado desde uno.

    Raises:
        ValueError: Si el encabezado no está presente en el mapa.
    """
    columna = encabezados.get(normalizar_texto(nombre))
    if columna is None:
        raise ValueError(
            f"Falta la columna '{nombre}' en la solapa {nombre_hoja}"
        )
    return columna

def finalizar_libro(libro_salida, archivo_excel, incluir_graficos=False):
    """Aplica las tareas finales y guarda el libro una sola vez.

    Args:
        libro_salida: Libro de Excel que se desea finalizar.
        archivo_excel: Ruta en la que se guarda el libro.
        incluir_graficos: Indica si deben regenerarse los gráficos.

    Raises:
        OSError: Si no se puede guardar el temporal o reemplazar el archivo final.
    """
    aplicar_estilos_basicos(libro_salida)

    if incluir_graficos:
        generar_graficos(libro_salida, CONFIGURACION_GRAFICOS)

    ruta_salida = Path(archivo_excel)
    ruta_salida.parent.mkdir(parents=True, exist_ok=True)
    ruta_temporal = ruta_salida.with_name(f".{ruta_salida.stem}.tmp{ruta_salida.suffix}")
    libro_salida.save(ruta_temporal)
    ruta_temporal.replace(ruta_salida)

def leer_fila_por_codigo(libro_entrada, nombre_hoja, codigo, campos, incluir_formato=False):
    """Lee los campos de una única fila identificada por código."""
    if nombre_hoja not in libro_entrada.sheetnames:
        raise ValueError(
            f"No existe la solapa '{nombre_hoja}' "
            f"en el archivo excel con los datos de entrada."
        )

    hoja = libro_entrada[nombre_hoja]
    encabezados = obtener_mapa_encabezados(hoja, normalizar=True)
    columna_codigo = obtener_columna_encabezado(encabezados, "Código", nombre_hoja)
    filas = buscar_filas_por_codigo(hoja, codigo, columna_codigo)

    if not filas:
        raise ValueError(f"No se encontró el código '{codigo}' en {nombre_hoja}")

    if len(filas) > 1:
        raise ValueError(f"Código duplicado '{codigo}' en {nombre_hoja}")

    valores = {}
    formatos = {}

    for campo in campos:
        columna = obtener_columna_encabezado(encabezados, campo, nombre_hoja)
        celda = hoja.cell(row=filas[0], column=columna)

        valores[campo] = celda.value

        if incluir_formato:
            formatos[campo] = celda.number_format

    return (valores, formatos) if incluir_formato else valores

def guardar_resultado_excel(
    libro,
    proyecto,
    metricas_cc,
    metricas_mi,
    metricas_bugs_smells=None,
    metricas_concurrencia=None,
    metricas_tokens=None,
    metricas_cpu_memoria=None,
):
    """Guarda todas las métricas de un proyecto en Excel.

    Args:
        libro: Libro de Excel en el que se escriben las métricas.
        proyecto: Proyecto analizado.
        metricas_cc: Métricas de complejidad.
        metricas_mi: Métricas de mantenibilidad.
        metricas_bugs_smells: Métricas de incidencias, si existen.
        metricas_concurrencia: Métricas de concurrencia, si existen.
    """
    hoja_resumen = libro[HOJA_RESUMEN]
    hoja_complejidad = libro[HOJA_COMPLEJIDAD]
    hoja_mantenibilidad = libro[HOJA_MANTENIBILIDAD]
    hoja_bugs_smells = libro[HOJA_BUGS_SMELLS]
    hoja_concurrencia = libro[HOJA_CONCURRENCIA_SALIDA]
    hoja_tokens = libro[HOJA_TOKENS_SALIDA]
    hoja_cpu_memoria = libro[HOJA_CPU_MEMORIA_SALIDA]

    escribir_hoja_resumen(hoja_resumen, proyecto, metricas_cc, metricas_mi, metricas_bugs_smells, metricas_concurrencia,metricas_tokens,metricas_cpu_memoria)

    escribir_hoja_complejidad(hoja_complejidad, proyecto.codigo, metricas_cc)
    escribir_hoja_mantenibilidad(hoja_mantenibilidad, proyecto.codigo, metricas_mi)
    escribir_hoja_bugs_smells(hoja_bugs_smells, proyecto, metricas_bugs_smells)
    escribir_hoja_concurrencia(hoja_concurrencia, proyecto.codigo, metricas_concurrencia)
    escribir_hoja_tokens(hoja_tokens,proyecto.codigo,metricas_tokens)
    escribir_hoja_cpu_memoria(hoja_cpu_memoria, proyecto.codigo, metricas_cpu_memoria)

def escribir_hoja_resumen(
    hoja,
    proyecto,
    metricas_cc,
    metricas_mi,
    metricas_bugs_smells=None,
    metricas_concurrencia=None,
    metricas_tokens=None,
    metricas_cpu_memoria=None
):
    """Escribe las métricas de un proyecto en la hoja de resumen.

    Args:
        hoja: Hoja de Excel donde se escriben los datos.
        proyecto: Proyecto analizado.
        metricas_cc: Métricas de complejidad.
        metricas_mi: Métricas de mantenibilidad.
        metricas_bugs_smells: Métricas de incidencias, si existen.
        metricas_concurrencia: Métricas de concurrencia, si existen.
    """
    ccn_promedio = metricas_cc.ccn_promedio if metricas_cc else ""
    nivel_cc = metricas_cc.nivel_cc if metricas_cc else ""

    mi = metricas_mi.mi if metricas_mi else ""
    nivel_mi = metricas_mi.nivel_mi if metricas_mi else ""

    issues_kloc = (
        metricas_bugs_smells.issues_kloc
        if metricas_bugs_smells
        else ""
    )
    isi = metricas_bugs_smells.isi if metricas_bugs_smells else ""
    interpretacion_isi = (
        metricas_bugs_smells.interpretacion_isi
        if metricas_bugs_smells
        else ""
    )

    promedio_concurrencia = (
        metricas_concurrencia.promedio
        if metricas_concurrencia
        else ""
    )
    interpretacion_concurrencia = (
        metricas_concurrencia.interpretacion
        if metricas_concurrencia
        else ""
    )

    eficiencia_ponderada = (
        metricas_tokens.eficiencia_ponderada
        if metricas_tokens
        else ""
    )

    interpretacion_tokens = (
        metricas_tokens.interpretacion
        if metricas_tokens
        else ""
    )

    cpu_promedio = (
        metricas_cpu_memoria.cpu_promedio_normalizada
        if metricas_cpu_memoria
        else ""
    )

    interpretacion_cpu = (
        metricas_cpu_memoria.interpretacion_cpu
        if metricas_cpu_memoria
        else "" 
    )

    memoria_promedio = (    
        metricas_cpu_memoria.memoria_promedio_normalizada
        if metricas_cpu_memoria
        else ""
    )

    interpretacion_memoria = (
        metricas_cpu_memoria.interpretacion_memoria
        if metricas_cpu_memoria
        else "" 
    )

    
    valores_resumen = [
        proyecto.codigo,
        proyecto.nombre_proyecto,
        proyecto.herramienta_ia,
        proyecto.modelo_ia,
        proyecto.lenguaje,
        ccn_promedio,
        nivel_cc,
        mi,
        nivel_mi,
        issues_kloc,
        isi,
        interpretacion_isi,
        promedio_concurrencia,
        interpretacion_concurrencia,
        eficiencia_ponderada,
        interpretacion_tokens,
        cpu_promedio,
        interpretacion_cpu,
        memoria_promedio,
        interpretacion_memoria,
    ]

    escribir_o_actualizar_fila(
        hoja,
        proyecto.codigo,
        valores_resumen,
    )
    formatear_celdas_hoja_resumen(hoja, proyecto.codigo)

def escribir_hoja_complejidad(hoja, codigo, metricas_cc):
    """Escribe las métricas de complejidad en la hoja correspondiente.

    Args:
        hoja: Hoja de Excel donde se escriben los datos.
        codigo: Código del proyecto.
        metricas_cc: Métricas de complejidad.
    """
    fila_complejidad_vacia = [codigo] + [""] * CANTIDAD_CAMPOS_COMPLEJIDAD

    if metricas_cc is None:
        escribir_o_actualizar_fila(hoja, codigo, fila_complejidad_vacia)
        return
    else:
        escribir_o_actualizar_fila(
            hoja,
            codigo,
            [
                codigo,
                metricas_cc.cantidad_funciones,
                metricas_cc.ccn_total,
                metricas_cc.ccn_promedio,
                metricas_cc.nloc_total,
                metricas_cc.nivel_cc,
                metricas_cc.interpretacion_cc,
                metricas_cc.nloc_promedio,
            ],
        )

def escribir_hoja_mantenibilidad(hoja, proyecto_codigo, metricas_mi):
    """Escribe las métricas de mantenibilidad en la hoja correspondiente.

    Args:
        hoja: Hoja de Excel donde se escriben los datos.
        proyecto_codigo: Código del proyecto.
        metricas_mi: Métricas de mantenibilidad.
    """
    fila_mantenibilidad_vacia = [proyecto_codigo] + [""] * CANTIDAD_CAMPOS_MANTENIBILIDAD

    if metricas_mi is None:
        escribir_o_actualizar_fila(hoja, proyecto_codigo, fila_mantenibilidad_vacia)
        return
    escribir_o_actualizar_fila(
        hoja,
        proyecto_codigo,
        [
            proyecto_codigo,
            metricas_mi.nloc_mi,
            metricas_mi.cantidad_funciones_mi,
            metricas_mi.tokens_codigo,
            metricas_mi.mi,
            metricas_mi.nivel_mi,
            metricas_mi.interpretacion_mi,
        ],
    )

def escribir_hoja_bugs_smells(hoja, proyecto, metricas_bugs_smells):
    """Escribe las métricas de bugs y smells en la hoja correspondiente.

    Args:
        hoja: Hoja de Excel donde se escriben los datos.
        proyecto: Proyecto analizado.
        metricas_bugs_smells: Métricas de bugs y smells.
    """
    fila_bugs_smells_vacia = [proyecto.codigo] + [""] * CANTIDAD_CAMPOS_BUGS_SMELLS   

    if metricas_bugs_smells is None:
        escribir_o_actualizar_fila(hoja, proyecto.codigo, fila_bugs_smells_vacia)
    else:
        escribir_o_actualizar_fila(
            hoja,
            proyecto.codigo,
            [
                proyecto.codigo,
                metricas_bugs_smells.analizador,
                metricas_bugs_smells.total_issues,
                metricas_bugs_smells.issues_kloc,
                metricas_bugs_smells.isi,
                metricas_bugs_smells.nivel_isi,
                metricas_bugs_smells.interpretacion_isi,
                metricas_bugs_smells.observacion,
                metricas_bugs_smells.cantidad_alta,
                metricas_bugs_smells.cantidad_media,
                metricas_bugs_smells.cantidad_baja,
                formatear_top_reglas(metricas_bugs_smells.top_reglas_violadas),
            ],
        )

    formatear_celdas_hoja_bug_smells(hoja, proyecto.codigo)    
    

def escribir_hoja_concurrencia(hoja, codigo, metricas_concurrencia):
    """Escribe las métricas de concurrencia en la hoja correspondiente.

    Args:
        hoja: Hoja de Excel donde se escriben los datos.
        codigo: Código del proyecto.
        metricas_concurrencia: Métricas de concurrencia.
    """
    fila_concurrencia_vacia = [codigo] + [""] * CANTIDAD_CAMPOS_CONCURRENCIA

    if metricas_concurrencia is None:
        escribir_o_actualizar_fila(hoja, codigo, fila_concurrencia_vacia)
    else:
        escribir_o_actualizar_fila(
            hoja,
            codigo,
            [
                codigo,
                metricas_concurrencia.sincronizacion_correcta,
                metricas_concurrencia.ausencia_de_deadlocks,
                metricas_concurrencia.ausencia_de_condicion_de_carrera,
                metricas_concurrencia.uso_correcto_de_exclusion_mutua,
                metricas_concurrencia.promedio,
                metricas_concurrencia.interpretacion,
            ],
        )

def escribir_hoja_tokens(hoja,proyecto_codigo, metricas_tokens):
    """Agrega o actualiza un resultado válido de eficiencia en tokens."""
    fila_tokens_vacia = [proyecto_codigo] + [""] * CANTIDAD_CAMPOS_TOKENS

    if metricas_tokens is None:
        valores = fila_tokens_vacia   
    else:
        valores = [
            metricas_tokens.codigo,
            metricas_tokens.metodo_utilizado,
            metricas_tokens.nloc_total,
            metricas_tokens.refinamientos,
            metricas_tokens.tokens_registrados,
            metricas_tokens.eficiencia_generacion,
            metricas_tokens.eficiencia_ponderada,
            metricas_tokens.nivel_eficiencia,
            metricas_tokens.interpretacion,
        ]
    escribir_o_actualizar_fila(hoja, proyecto_codigo, valores)



def escribir_hoja_cpu_memoria(hoja, proyecto_codigo, metricas):
    """Agrega o actualiza las métricas de CPU y memoria de un proyecto."""
    if metricas is None:
        valores = [proyecto_codigo] + [""] * CANTIDAD_CAMPOS_CPU_MEMORIA
    else:
        valores = obtener_valores_cpu_memoria(metricas)
    escribir_o_actualizar_fila(hoja, proyecto_codigo, valores)
    aplicar_formato_cpu_memoria(hoja, proyecto_codigo)


def obtener_valores_cpu_memoria(metricas):
    """Devuelve los valores de CPU/memoria en el orden definido para la salida."""
    return [
        metricas.codigo, metricas.lenguaje, metricas.metodo_medicion, metricas.modo_cpu,
        metricas.cpus_logicas, metricas.accion_1, metricas.cpu_medida_1, metricas.accion_2,
        metricas.cpu_medida_2, metricas.cpu_normalizada_1, metricas.cpu_normalizada_2,
        metricas.cpu_promedio_normalizada, metricas.cpu_maxima_normalizada, metricas.nivel_cpu,
        metricas.interpretacion_cpu, metricas.memoria_acc1, metricas.memoria_acc2,
        metricas.memoria_total, metricas.memoria_promedio, metricas.memoria_promedio_normalizada,
        metricas.memoria_maxima, metricas.nivel_memoria, metricas.interpretacion_memoria,
    ]

def formatear_celdas_hoja_resumen(hoja, codigo):
    """Aplica formato a las celdas de la hoja Resumen."""

    filas = buscar_filas_por_codigo(hoja, codigo)

    if len(filas) > 1:
        raise ValueError(f"Código duplicado '{codigo}' en {hoja.title}")

    if filas:
        fila = filas[0]
        columnas = obtener_mapa_encabezados(hoja)

        hoja.cell(row=fila, column=columnas["% CPU promedio"]).number_format = r'0.00\%'
        hoja.cell(row=fila, column=columnas["% Memoria promedio"]).number_format = r'0.00\%'

def aplicar_formato_cpu_memoria(hoja, codigo):
    """Aplica formatos de visualización a porcentajes y valores decimales."""
    filas = buscar_filas_por_codigo(hoja, codigo)

    if not filas:
        raise ValueError(f"No se encontró el código '{codigo}' en {hoja.title}")
    if len(filas) > 1:
        raise ValueError(f"Código duplicado '{codigo}' en {hoja.title}")

    fila=filas[0]
    for columna in (7, 9, 10, 11, 12, 13):
        hoja.cell(row=fila, column=columna).number_format = r'0.00\%'
    hoja.cell(row=fila, column=20).number_format = r'0.00\%'

    for columna in (16, 17, 18, 19, 21):
        hoja.cell(row=fila, column=columna).number_format = '0.00'

def formatear_top_reglas(top_reglas):
    """Convierte el ranking de reglas en texto legible.

    Args:
        top_reglas: Pares formados por regla y cantidad.

    Returns:
        Las reglas y cantidades separadas por punto y coma.
    """
    return "; ".join(f"{regla}: {cantidad}" for regla, cantidad in top_reglas)



def guardar_error_excel(libro, proyecto, mensaje_error):
    """Registra en el libro un error asociado con un proyecto.

    Args:
        libro: Libro de Excel en el que se registra el error.
        proyecto: Proyecto cuyo análisis falló.
        mensaje_error: Descripción del error producido.
    """
    hoja = libro[HOJA_ERRORES]
    hoja.append([proyecto.codigo, proyecto.nombre_proyecto, mensaje_error])


