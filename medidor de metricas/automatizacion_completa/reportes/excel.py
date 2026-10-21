"""Lectura, creación y actualización de archivos Excel."""

from pathlib import Path
import unicodedata

from openpyxl import Workbook, load_workbook
from openpyxl.styles import Font, PatternFill, Alignment, Border, Side
from openpyxl.worksheet.datavalidation import DataValidation
from openpyxl.formatting.rule import FormulaRule
from openpyxl.utils import get_column_letter
from copy import copy
from reportes.graficos import generar_graficos
from constantes.definiciones import (
    CONFIGURACION_GRAFICOS_DISPOSITIVOS,
    HOJA_COMPARACION,
    ENCABEZADOS_RESUMEN_DISPOSITIVOS,
    TEXTO_MANUAL,
    TEXTO_VIBECODING,
    HOJAS_REPORTE,
    ENCABEZADOS_RESUMEN,
    HOJA_RESUMEN,
    HOJA_COMPLEJIDAD,
    HOJA_MANTENIBILIDAD,
    HOJA_BUGS_SMELLS,
    HOJA_CONCURRENCIA_SALIDA,
    HOJA_TOKENS_SALIDA,
    CONFIGURACION_GRAFICOS,
    ENCABEZADOS_DATOS_PROYECTO_RESUMEN,
    ENCABEZADOS_INTERPRETACION_RESUMEN,
    HOJA_ERRORES,
    HOJA_CPU_MEMORIA_SALIDA,
    TEXTO_ENCABEZADO_CODIGO,
    TEXTO_ENCABEZADO_ID_PROYECTO,
    TEXTO_ENCABEZADO_DISPOSITIVO,
    TEXTO_ENCABEZADO_IMPLEMENTACION,
    TEXTO_ENCABEZADO_CPU_PROMEDIO,
    TEXTO_ENCABEZADO_MEMORIA_PROMEDIO_RESUMEN,
    TEXTO_ENCABEZADO_REGLAS_INCUMPLIDAS,
    COLOR_ENCABEZADO_RESUMEN,
    COLOR_CODIGO_RESUMEN,
    COLOR_DATOS_PROYECTO_RESUMEN,
    COLOR_INTERPRETACION_RESUMEN,
    TIPO_PROYECTO_ESP32_ANDROID,
    TIPO_PROYECTO_PC,
)


class DatosProyectoNoEncontrados(ValueError):
    """Indica que el proyecto no tiene datos en una hoja."""
    pass


def crear_o_abrir_excel_salida(archivo_excel, hojas_reporte=HOJAS_REPORTE):
    """Abre o crea un libro con las hojas del perfil seleccionado.

    Args:
        archivo_excel: Ruta del libro de resultados.
        hojas_reporte: Nombres y encabezados de las hojas requeridas.
            Por defecto conserva la configuración original de PC.

    Returns:
        El libro preparado para recibir datos.
    """
    if Path(archivo_excel).exists():
        libro = load_workbook(archivo_excel)
    else:
        libro = Workbook()
        libro.remove(libro.active)

    crear_hojas_si_no_existen(libro, hojas_reporte)
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

def crear_hojas_si_no_existen(libro, hojas_reporte=HOJAS_REPORTE):
    """Crea las hojas del reporte y verifica sus encabezados.
    Args:
        libro: Libro de Excel que se desea preparar.
    """
    for nombre, encabezados in hojas_reporte.items(): 
        if nombre not in libro.sheetnames:
            libro.create_sheet(nombre).append(encabezados)
            continue

        hoja = libro[nombre]
        asegurar_encabezados(hoja, encabezados)

        if nombre == HOJA_BUGS_SMELLS:
            eliminar_columnas_obsoletas(hoja, encabezados)

def aplicar_estilos_basicos(libro):
    """Aplica estilos y ajusta columnas de las hojas del reporte."""

    for hoja in libro.worksheets:

        # Comparacion ya tiene sus propios estilos.
        if hoja.title == HOJA_COMPARACION:
            continue

        # Aplicar estilos a los encabezados.
        for celda in hoja[1]:
            celda.font = Font(bold=True)
            celda.fill = PatternFill("solid", fgColor="D9EAF7")
            celda.alignment = Alignment(horizontal="center")

        # Ajustar el ancho de las columnas.
        for indice, columna in enumerate(hoja.columns, start=1):
            max_largo = 0

            # Obtener la letra usando el índice, no la celda.
            letra = get_column_letter(indice)

            for celda in columna:
                if celda.value is not None:
                    max_largo = max(
                        max_largo,
                        len(str(celda.value))
                    )

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
        columna_reglas = obtener_mapa_encabezados(hoja)[TEXTO_ENCABEZADO_REGLAS_INCUMPLIDAS]
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
    """Agrega o actualiza una fila por código, por posición o encabezado.

    Args:
        hoja: Hoja que se desea modificar.
        codigo: Código usado como clave de la fila.
        valores: Lista en el orden de columnas o diccionario por encabezado.
    """
    filas_existentes = buscar_filas_por_codigo(hoja, codigo)

    if len(filas_existentes) > 1:
        raise ValueError(f"Código duplicado '{codigo}' en {hoja.title}")

    if isinstance(valores, dict):
        if filas_existentes:
            fila = filas_existentes[0]
        else:
            hoja.append([codigo])
            fila = hoja.max_row
        escribir_valores_por_encabezado(hoja, fila, valores)
        return

    if not filas_existentes:
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

def resolver_columnas_graficos(libro, configuracion):
    """Resuelve encabezados de gráficos a columnas reales del Excel.

    Args:
        libro: Libro con las hojas que proporcionan los datos.
        configuracion: Tuplas con columna numérica (PC) o encabezado (dispositivos).

    Returns:
        Lista de configuraciones con columnas numéricas.

    Raises:
        ValueError: Si un encabezado requerido no existe en su hoja.
    """
    resultado = []
    for nombre_hoja, columna, titulo, eje_y, posicion in configuracion:
        if isinstance(columna, str):
            columnas = obtener_mapa_encabezados(libro[nombre_hoja])
            if columna not in columnas:
                raise ValueError(f"Falta la columna '{columna}' en la solapa {nombre_hoja}")
            columna = columnas[columna]
        resultado.append((nombre_hoja, columna, titulo, eje_y, posicion))
    return resultado

def generar_hojas_comparacion(libro):
    """Genera una comparación interactiva Manual/Vibecoding desde Resumen."""

    # Obtener y validar los datos.
    resumen = libro[HOJA_RESUMEN]
    columnas = validar_encabezados_comparacion(resumen)
    parejas = obtener_parejas_completas(resumen, columnas)

    # Crear la hoja de comparación.
    hoja, seleccion_anterior = preparar_hoja_comparacion(libro)

    # Configurar presentación y selección del proyecto.
    configurar_encabezado_comparacion(hoja, parejas, seleccion_anterior)
    configurar_tabla_auxiliar(hoja, parejas)

    # Generar las fórmulas y aplicar los estilos.
    generar_tabla_comparacion(hoja, resumen, columnas, parejas)

    # Configurar formato condicional y recálculo.
    finalizar_hoja_comparacion(hoja, libro)

def validar_encabezados_comparacion(resumen):
    """Verifica que Resumen contenga las columnas requeridas."""

    columnas = obtener_mapa_encabezados(resumen)

    faltantes = [
        campo
        for campo in ENCABEZADOS_RESUMEN_DISPOSITIVOS
        if campo not in columnas
    ]

    if faltantes:
        raise ValueError(f"Faltan columnas en Resumen: {faltantes}")

    return columnas

def registrar_pareja_comparacion(
    parejas, identificador, dispositivo, implementacion, codigo
):
    """Registra una implementación según el ID y el dispositivo."""

    tipo = normalizar_texto(implementacion)
    manual = normalizar_texto(TEXTO_MANUAL)
    vibecoding = normalizar_texto(TEXTO_VIBECODING)

    if tipo not in (manual, vibecoding):
        return

    if not dispositivo:
        raise ValueError(
            f"El proyecto '{identificador}' no tiene dispositivo."
        )

    identificador = str(identificador).strip()
    dispositivo = str(dispositivo).strip()

    # Un mismo ID puede tener proyectos Android y ESP32.
    clave = (identificador, normalizar_texto(dispositivo))

    pareja = parejas.setdefault(
        clave, {"dispositivo": dispositivo}
    )

    if tipo in pareja:
        raise ValueError(
            f"La implementación '{implementacion}' está duplicada "
            f"para el ID '{identificador}' y dispositivo '{dispositivo}'."
        )

    pareja[tipo] = codigo


def obtener_parejas_completas(resumen, columnas):
    """Obtiene las parejas Manual/Vibecoding por ID y dispositivo."""

    parejas = {}

    for fila in range(2, resumen.max_row + 1):

        identificador = resumen.cell(
            fila, columnas[TEXTO_ENCABEZADO_ID_PROYECTO]
        ).value

        implementacion = resumen.cell(
            fila, columnas[TEXTO_ENCABEZADO_IMPLEMENTACION]
        ).value

        dispositivo = resumen.cell(
            fila, columnas[TEXTO_ENCABEZADO_DISPOSITIVO]
        ).value

        codigo = resumen.cell(
            fila, columnas[TEXTO_ENCABEZADO_CODIGO]
        ).value

        if not identificador or not codigo:
            continue

        registrar_pareja_comparacion(
            parejas,
            identificador,
            dispositivo,
            implementacion,
            codigo
        )

    manual = normalizar_texto(TEXTO_MANUAL)
    vibecoding = normalizar_texto(TEXTO_VIBECODING)

    completas = [
        (
            identificador,
            pareja["dispositivo"],
            pareja[manual],
            pareja[vibecoding]
        )
        for (identificador, _), pareja in parejas.items()
        if manual in pareja and vibecoding in pareja
    ]

    return sorted(
        completas,
        key=lambda p: (p[0], normalizar_texto(p[1]))
    )


def preparar_hoja_comparacion(libro):
    """Crea la hoja conservando el ID y dispositivo seleccionados."""

    seleccion_anterior = (None, None)

    if HOJA_COMPARACION in libro.sheetnames:
        anterior = libro[HOJA_COMPARACION]

        seleccion_anterior = (
            anterior["B3"].value,
            anterior["B4"].value
        )

        del libro[HOJA_COMPARACION]

    hoja = libro.create_sheet(HOJA_COMPARACION, 1)

    hoja.sheet_view.showGridLines = False
    hoja.freeze_panes = "B7"

    hoja.column_dimensions["A"].width = 37
    hoja.column_dimensions["B"].width = 43
    hoja.column_dimensions["C"].width = 43

    return hoja, seleccion_anterior


def configurar_encabezado_comparacion(
    hoja, parejas, seleccion_anterior
):
    """Configura el título, los dos selectores y los encabezados."""

    # Título principal.
    hoja.merge_cells("A1:C1")
    hoja["A1"] = "COMPARACIÓN · MANUAL / VIBECODING"
    hoja["A1"].fill = PatternFill("solid", fgColor="1F4E78")
    hoja["A1"].font = Font(
        name="Calibri", size=14, color="FFFFFF", bold=True
    )
    hoja["A1"].alignment = Alignment(vertical="center")
    hoja.row_dimensions[1].height = 32

    # Recuperar la selección anterior, si sigue siendo válida.
    id_anterior, dispositivo_anterior = seleccion_anterior

    identificadores = {p[0] for p in parejas}

    identificador = (
        id_anterior
        if id_anterior in identificadores
        else (parejas[0][0] if parejas else "")
    )

    dispositivos = [
        p[1] for p in parejas if p[0] == identificador
    ]

    dispositivo = next(
        (
            d for d in dispositivos
            if dispositivo_anterior
            and normalizar_texto(d)
            == normalizar_texto(dispositivo_anterior)
        ),
        dispositivos[0] if dispositivos else ""
    )

    # Selector ID proyecto.
    hoja["A3"] = "ID proyecto"
    hoja["A3"].font = Font(bold=True)

    hoja["B3"] = identificador

    # Selector Dispositivo.
    hoja["A4"] = "Dispositivo"
    hoja["A4"].font = Font(bold=True)

    hoja["B4"] = dispositivo

    # Aplicar el mismo estilo a ambos selectores.
    for direccion in ("B3", "B4"):
        celda = hoja[direccion]

        celda.fill = PatternFill(
            "solid", fgColor=COLOR_ENCABEZADO_RESUMEN
        )
        celda.font = Font(bold=True, color="000000")
        celda.alignment = Alignment(horizontal="center")

    hoja["A5"] = (
        "Seleccioná un ID y un dispositivo para comparar "
        "ambas implementaciones"
    )

    # Encabezados de la tabla: ahora están en la fila 6.
    titulos = ("Campo", TEXTO_MANUAL, TEXTO_VIBECODING)

    for columna, titulo in enumerate(titulos, 1):
        celda = hoja.cell(row=6, column=columna, value=titulo)

        celda.fill = PatternFill(
            "solid", fgColor=COLOR_ENCABEZADO_RESUMEN
        )
        celda.font = Font(color="000000", bold=True)
        celda.alignment = Alignment(horizontal="center")

    hoja.row_dimensions[6].height = 25


def configurar_tabla_auxiliar(hoja, parejas):
    """Configura los datos auxiliares y los dos desplegables."""

    # E: ID proyecto
    # F: Dispositivo
    # G: Código Manual
    # H: Código Vibecoding
    # I: Clave ID|dispositivo

    for fila, (identificador, dispositivo, manual, vibecoding) in enumerate(
        parejas, 2
    ):
        hoja.cell(fila, 5, identificador)
        hoja.cell(fila, 6, dispositivo)
        hoja.cell(fila, 7, manual)
        hoja.cell(fila, 8, vibecoding)

        clave = f"{identificador}|{normalizar_texto(dispositivo)}"
        hoja.cell(fila, 9, clave)

    # Listas sin duplicados para los desplegables.
    identificadores = sorted({p[0] for p in parejas})

    dispositivos = sorted(
        {p[1] for p in parejas},
        key=normalizar_texto
    )

    # J: ID disponibles.
    for fila, identificador in enumerate(identificadores, 2):
        hoja.cell(fila, 10, identificador)

    # K: Dispositivos disponibles.
    for fila, dispositivo in enumerate(dispositivos, 2):
        hoja.cell(fila, 11, dispositivo)

    # Ocultar todos los datos auxiliares.
    for letra in "EFGHIJK":
        hoja.column_dimensions[letra].hidden = True

    if not parejas:
        return

    # Lista desplegable para ID proyecto (B3).
    ultima_id = len(identificadores) + 1

    validacion_id = DataValidation(
        type="list",
        formula1=f"=$J$2:$J${ultima_id}",
        allow_blank=False
    )

    validacion_id.showErrorMessage = True
    validacion_id.error = "Seleccioná un ID existente"

    hoja.add_data_validation(validacion_id)
    validacion_id.add(hoja["B3"])

    # Lista desplegable para dispositivo (B4).
    ultimo_dispositivo = len(dispositivos) + 1

    validacion_dispositivo = DataValidation(
        type="list",
        formula1=f"=$K$2:$K${ultimo_dispositivo}",
        allow_blank=False
    )

    validacion_dispositivo.showErrorMessage = True
    validacion_dispositivo.error = "Seleccioná un dispositivo existente"

    hoja.add_data_validation(validacion_dispositivo)
    validacion_dispositivo.add(hoja["B4"])


def generar_formula_codigo_comparacion(columna_auxiliar, ultima_fila):
    """Busca el código según el ID y dispositivo seleccionados."""

    # La clave combina el ID de B3 y el dispositivo de B4.
    clave = '$B$3&"|"&LOWER($B$4)'

    return (
        f'=IF(OR($B$3="",$B$4=""),"Sin datos",'
        f'IFERROR(INDEX(${columna_auxiliar}$2:'
        f'${columna_auxiliar}${ultima_fila},'
        f'MATCH({clave},$I$2:$I${ultima_fila},0)),'
        f'"Sin datos"))'
    )


def generar_formula_valor_comparacion(
    campo, columna, fila_codigo, columnas, rango_codigos, ultima_resumen
):
    """Busca una métrica del proyecto en la hoja Resumen."""

    columna_valor = get_column_letter(columnas[campo])

    rango_valor = (
        f"'{HOJA_RESUMEN}'!${columna_valor}$2:"
        f"${columna_valor}${ultima_resumen}"
    )

    consulta = (
        f"INDEX({rango_valor},"
        f"MATCH({columna}${fila_codigo},{rango_codigos},0))"
    )

    return (
        f'=IF(OR($B$3="",$B$4="",'
        f'{columna}${fila_codigo}="Sin datos"),"Sin datos",'
        f'IFERROR(IF(COUNTBLANK({consulta})>0,"Sin datos",'
        f'{consulta}),"Sin datos"))'
    )



def aplicar_estilo_celda_comparacion(celda, campo, borde):
    """Aplica los estilos correspondientes al tipo de campo."""

    celda.font = Font(name="Calibri", size=11, color="000000")
    celda.alignment = Alignment(vertical="center", wrap_text=True)
    celda.border = borde

    if campo == TEXTO_ENCABEZADO_CODIGO:
        color = COLOR_CODIGO_RESUMEN

    elif campo in ENCABEZADOS_INTERPRETACION_RESUMEN:
        color = COLOR_INTERPRETACION_RESUMEN

    elif campo in ENCABEZADOS_DATOS_PROYECTO_RESUMEN:
        color = COLOR_DATOS_PROYECTO_RESUMEN

    else:
        color = None

    if color:
        celda.fill = PatternFill("solid", fgColor=color)

    if campo in (
        TEXTO_ENCABEZADO_CPU_PROMEDIO,
        TEXTO_ENCABEZADO_MEMORIA_PROMEDIO_RESUMEN,
    ):
        celda.number_format = r"0.00\%"

def generar_tabla_comparacion(hoja, resumen, columnas, parejas):
    """Genera las métricas Manual/Vibecoding y aplica sus estilos."""

    campos = ENCABEZADOS_RESUMEN_DISPOSITIVOS

    ultima_auxiliar = max(2, len(parejas) + 1)
    ultima_resumen = max(2, resumen.max_row)

    # Las métricas empiezan ahora en la fila 7.
    fila_codigo = 7 + campos.index(TEXTO_ENCABEZADO_CODIGO)

    columna_codigo = get_column_letter(
        columnas[TEXTO_ENCABEZADO_CODIGO]
    )

    rango_codigos = (
        f"'{HOJA_RESUMEN}'!${columna_codigo}$2:"
        f"${columna_codigo}${ultima_resumen}"
    )

    borde = Border(
        bottom=Side(style="hair", color="D9E2F3")
    )

    for fila, campo in enumerate(campos, 7):

        # Etiqueta de cada métrica.
        etiqueta = hoja.cell(fila, 1, campo)
        etiqueta.font = Font(color="000000", bold=True)
        etiqueta.fill = PatternFill("solid", fgColor="E7EEF4")
        etiqueta.border = borde
        etiqueta.alignment = Alignment(
            vertical="center", wrap_text=True
        )

        hoja.row_dimensions[fila].height = 30

        # Columnas B (Manual) y C (Vibecoding).
        for columna, origen in (("B", "G"), ("C", "H")):

            celda = hoja[f"{columna}{fila}"]

            if campo == TEXTO_ENCABEZADO_CODIGO:

                celda.value = generar_formula_codigo_comparacion(
                    origen,
                    ultima_auxiliar
                )

            else:

                celda.value = generar_formula_valor_comparacion(
                    campo,
                    columna,
                    fila_codigo,
                    columnas,
                    rango_codigos,
                    ultima_resumen
                )

            aplicar_estilo_celda_comparacion(
                celda, campo, borde
            )

def finalizar_hoja_comparacion(hoja, libro):
    """Configura el formato condicional y recálculo automático."""

    ultima_fila = 6 + len(ENCABEZADOS_RESUMEN_DISPOSITIVOS)

    hoja.conditional_formatting.add(
        f"B7:C{ultima_fila}",
        FormulaRule(
            formula=['B7="Sin datos"'],
            font=Font(color="008000", bold=True)
        )
    )

    libro.calculation.calcMode = "auto"
    libro.calculation.fullCalcOnLoad = True
    libro.calculation.forceFullCalc = True


def generar_hojas_comparacion(libro):
    """Genera la comparación Manual/Vibecoding por ID y dispositivo."""

    # 1. Obtener y validar Resumen.
    resumen = libro[HOJA_RESUMEN]
    columnas = validar_encabezados_comparacion(resumen)

    # 2. Identificar parejas completas por ID y dispositivo.
    parejas = obtener_parejas_completas(resumen, columnas)

    # 3. Preparar la hoja y conservar selecciones anteriores.
    hoja, seleccion_anterior = preparar_hoja_comparacion(libro)

    # 4. Configurar título y selectores ID/Dispositivo.
    configurar_encabezado_comparacion(
        hoja, parejas, seleccion_anterior
    )

    # 5. Crear datos auxiliares y listas desplegables.
    configurar_tabla_auxiliar(hoja, parejas)

    # 6. Generar la tabla de métricas y sus estilos.
    generar_tabla_comparacion(
        hoja, resumen, columnas, parejas
    )

    # 7. Configurar formato condicional y recálculo.
    finalizar_hoja_comparacion(hoja, libro)

def finalizar_libro(libro_salida, archivo_excel, tipo_proyecto=TIPO_PROYECTO_PC):
    """Formatea, genera los gráficos del perfil y guarda el libro.

    Args:
        libro_salida: Libro de Excel que se desea finalizar.
        archivo_excel: Ruta en la que se guarda el libro.
        incluir_graficos: Indica si deben regenerarse los gráficos.
        configuracion_graficos: Gráficos a generar; None usa los de PC.

    Raises:
        OSError: Si no se puede guardar el temporal o reemplazar el archivo final.
    """
    aplicar_estilos_basicos(libro_salida)

    #si el tipo de proyecto es PC, se generan los graficos de PC, 
    # si es ESP32_ANDROID se generan los graficos de dispositivos y la hoja de comparacion
    graficos = []
   
    if tipo_proyecto == TIPO_PROYECTO_PC:
        graficos = CONFIGURACION_GRAFICOS
   
    if tipo_proyecto == TIPO_PROYECTO_ESP32_ANDROID:
        graficos = CONFIGURACION_GRAFICOS_DISPOSITIVOS
        generar_hojas_comparacion(libro_salida)
        
    graficos = resolver_columnas_graficos(libro_salida, graficos)
    generar_graficos(libro_salida, graficos)

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
    columna_codigo = obtener_columna_encabezado(encabezados,TEXTO_ENCABEZADO_CODIGO, nombre_hoja)
    filas = buscar_filas_por_codigo(hoja, codigo, columna_codigo)

    if not filas:
        raise DatosProyectoNoEncontrados(f"No se encontró el código '{codigo}' en {nombre_hoja}")

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
        getattr(metricas_cpu_memoria, "cpu_promedio_normalizada", "")
        if metricas_cpu_memoria
        else ""
    )

    interpretacion_cpu = (
        getattr(metricas_cpu_memoria, "interpretacion_cpu", "")
        if metricas_cpu_memoria
        else "" 
    )

    memoria_promedio = (    
        getattr(metricas_cpu_memoria, "memoria_promedio_normalizada", "")
        if metricas_cpu_memoria
        else ""
    )

    interpretacion_memoria = (
        getattr(metricas_cpu_memoria, "interpretacion_memoria", "")
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

    # El perfil de PC conserva la escritura por posición. En dispositivos se
    # utilizan encabezados para no desplazar las métricas al agregar columnas.
    if TEXTO_ENCABEZADO_ID_PROYECTO in obtener_mapa_encabezados(hoja):
        valores = dict(zip(ENCABEZADOS_RESUMEN, valores_resumen))
        valores.update({
            TEXTO_ENCABEZADO_ID_PROYECTO: getattr(proyecto, "id_proyecto", ""),
            TEXTO_ENCABEZADO_DISPOSITIVO: getattr(proyecto, "dispositivo", ""),
            TEXTO_ENCABEZADO_IMPLEMENTACION: getattr(proyecto, "implementacion", ""),
        })
        escribir_o_actualizar_fila(hoja, proyecto.codigo, valores)
    else:
        escribir_o_actualizar_fila(hoja, proyecto.codigo, valores_resumen)
    formatear_celdas_hoja_resumen(hoja, proyecto.codigo)

def escribir_hoja_complejidad(hoja, codigo, metricas_cc):
    """Escribe las métricas de complejidad en la hoja correspondiente.

    Args:
        hoja: Hoja de Excel donde se escriben los datos.
        codigo: Código del proyecto.
        metricas_cc: Métricas de complejidad.
    """

    if metricas_cc is  not None:
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
    if metricas_mi is not None:
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
    if metricas_bugs_smells is not None:
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

    if metricas_concurrencia is not None:
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


    if metricas_tokens is not None:
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
    """Escribe las mismas métricas de CPU/memoria en PC y Android/ESP32."""

    if metricas is not None:
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
    """Colorea datos e interpretaciones usando los encabezados reales."""
    filas = buscar_filas_por_codigo(hoja, codigo)

    if len(filas) > 1:
        raise ValueError(f"Código duplicado '{codigo}' en {hoja.title}")
    if not filas:
        return

    fila = filas[0]
    columnas = obtener_mapa_encabezados(hoja)

    # Formato de encabezados.
    for celda in hoja[1]:
        celda.fill = PatternFill("solid", fgColor=COLOR_ENCABEZADO_RESUMEN)

    # Formato del código.
    hoja.cell(row=fila, column=columnas[TEXTO_ENCABEZADO_CODIGO]).fill = (
        PatternFill("solid", fgColor=COLOR_CODIGO_RESUMEN)
    )

    # Formato de datos del proyecto.
    for encabezado in ENCABEZADOS_DATOS_PROYECTO_RESUMEN:
        columna = columnas.get(encabezado)
        if columna is not None:
            hoja.cell(row=fila, column=columna).fill = PatternFill(
                "solid", fgColor=COLOR_DATOS_PROYECTO_RESUMEN
            )

    # Formato de interpretaciones.
    for encabezado in ENCABEZADOS_INTERPRETACION_RESUMEN:
        columna = columnas.get(encabezado)
        if columna is not None:
            hoja.cell(row=fila, column=columna).fill = PatternFill(
                "solid", fgColor=COLOR_INTERPRETACION_RESUMEN
            )

    # Formato porcentual de CPU y memoria.
    for encabezado in (
        TEXTO_ENCABEZADO_CPU_PROMEDIO,
        TEXTO_ENCABEZADO_MEMORIA_PROMEDIO_RESUMEN,
    ):
        columna = columnas.get(encabezado)
        if columna is not None:
            hoja.cell(
                row=fila, column=columna
            ).number_format = r'0.00\%'

    # Identificar las columnas que no son métricas.
    campos_identificacion = {
        TEXTO_ENCABEZADO_CODIGO,
        *ENCABEZADOS_DATOS_PROYECTO_RESUMEN,
        "ID proyecto",
        "Dispositivo",
        "Implementación",
    }

    # Mostrar "Sin datos" en verde y los valores válidos en negro.
    for encabezado, columna in columnas.items():
        if encabezado in campos_identificacion:
            continue

        celda = hoja.cell(row=fila, column=columna)
        fuente = copy(celda.font)

        if celda.value is None or celda.value == "" or celda.value == "Sin datos":
            celda.value = "Sin datos"
            fuente.color = "008000"
            fuente.bold = True
        else:
            fuente.color = "000000"
            fuente.bold = False

        celda.font = fuente

def aplicar_formato_cpu_memoria(hoja, codigo):
    """Aplica el mismo formato de CPU/memoria a todos los dispositivos."""
    filas = buscar_filas_por_codigo(hoja, codigo)

    if not filas:
        raise ValueError(f"No se encontró el código '{codigo}' en {hoja.title}")
    if len(filas) > 1:
        raise ValueError(f"Código duplicado '{codigo}' en {hoja.title}")

    fila = filas[0]
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


