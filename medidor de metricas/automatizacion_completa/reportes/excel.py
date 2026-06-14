from pathlib import Path
from openpyxl import Workbook, load_workbook
from openpyxl.chart import BarChart, Reference
from openpyxl.styles import Font, PatternFill, Alignment


HOJA_RESUMEN = "Resumen"
HOJA_COMPLEJIDAD = "Complejidad"
HOJA_MANTENIBILIDAD = "Mantenibilidad"
HOJA_ERRORES = "Errores"

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

ENCABEZADOS_ERRORES = [
    "Código",
    "Nombre del proyecto",
    "Error",
]


def crear_o_abrir_excel(archivo_excel):
    if Path(archivo_excel).exists():
        libro = load_workbook(archivo_excel)
    else:
        libro = Workbook()
        libro.remove(libro.active)

    crear_hojas_si_no_existen(libro)
    return libro


def asegurar_encabezados(hoja, encabezados):
    """Crea o actualiza encabezados sin borrar datos existentes."""
    if hoja.max_row == 1 and all(celda.value is None for celda in hoja[1]):
        hoja.append(encabezados)
        hoja.delete_rows(1)
        return

    encabezados_actuales = [hoja.cell(row=1, column=col).value for col in range(1, hoja.max_column + 1)]

    for encabezado in encabezados:
        if encabezado not in encabezados_actuales:
            hoja.cell(row=1, column=hoja.max_column + 1).value = encabezado
            encabezados_actuales.append(encabezado)


def crear_hojas_si_no_existen(libro):
    if HOJA_RESUMEN not in libro.sheetnames:
        hoja = libro.create_sheet(HOJA_RESUMEN)
        hoja.append(ENCABEZADOS_RESUMEN)
    else:
        asegurar_encabezados(libro[HOJA_RESUMEN], ENCABEZADOS_RESUMEN)

    if HOJA_COMPLEJIDAD not in libro.sheetnames:
        hoja = libro.create_sheet(HOJA_COMPLEJIDAD)
        hoja.append(ENCABEZADOS_COMPLEJIDAD)
    else:
        asegurar_encabezados(libro[HOJA_COMPLEJIDAD], ENCABEZADOS_COMPLEJIDAD)

    if HOJA_MANTENIBILIDAD not in libro.sheetnames:
        hoja = libro.create_sheet(HOJA_MANTENIBILIDAD)
        hoja.append(ENCABEZADOS_MANTENIBILIDAD)
    else:
        asegurar_encabezados(libro[HOJA_MANTENIBILIDAD], ENCABEZADOS_MANTENIBILIDAD)

    if HOJA_ERRORES not in libro.sheetnames:
        hoja = libro.create_sheet(HOJA_ERRORES)
        hoja.append(ENCABEZADOS_ERRORES)
    else:
        asegurar_encabezados(libro[HOJA_ERRORES], ENCABEZADOS_ERRORES)

    aplicar_estilos_basicos(libro)


def aplicar_estilos_basicos(libro):
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


def buscar_fila_por_codigo(hoja, codigo):
    for fila in range(2, hoja.max_row + 1):
        if hoja.cell(row=fila, column=1).value == codigo:
            return fila

    return None


def escribir_o_actualizar_fila(hoja, codigo, valores):
    fila_existente = buscar_fila_por_codigo(hoja, codigo)

    if fila_existente is None:
        hoja.append(valores)
    else:
        for columna, valor in enumerate(valores, start=1):
            hoja.cell(row=fila_existente, column=columna).value = valor


def guardar_resultado_excel(archivo_excel, proyecto, metricas, metricas_mi):
    libro = crear_o_abrir_excel(archivo_excel)

    hoja_resumen = libro[HOJA_RESUMEN]
    hoja_complejidad = libro[HOJA_COMPLEJIDAD]
    hoja_mantenibilidad = libro[HOJA_MANTENIBILIDAD]

    escribir_o_actualizar_fila(
        hoja_resumen,
        proyecto.codigo,
        [
            proyecto.codigo,
            proyecto.nombre_proyecto,
            proyecto.herramienta_ia,
            proyecto.modelo_ia,
            proyecto.lenguaje,
            metricas.ccn_promedio,
            metricas.nivel_cc,
            metricas_mi.mi,
            metricas_mi.nivel_mi,
        ],
    )

    escribir_o_actualizar_fila(
        hoja_complejidad,
        proyecto.codigo,
        [
            proyecto.codigo,
            metricas.cantidad_funciones,
            metricas.ccn_total,
            metricas.ccn_promedio,
            metricas.nloc_total,
            metricas.nivel_cc,
            metricas.interpretacion_cc,
            metricas.nloc_promedio,
        ],
    )

    escribir_o_actualizar_fila(
        hoja_mantenibilidad,
        proyecto.codigo,
        [
            proyecto.codigo,
            metricas_mi.nloc_mi,
            metricas_mi.cantidad_funciones_mi,
            metricas_mi.tokens_codigo,
            metricas_mi.mi,
            metricas_mi.nivel_mi,
            metricas_mi.interpretacion_mi,
        ],
    )

    aplicar_estilos_basicos(libro)
    generar_graficos(libro)
    libro.save(archivo_excel)


def guardar_error_excel(archivo_excel, proyecto, mensaje_error):
    libro = crear_o_abrir_excel(archivo_excel)
    hoja = libro[HOJA_ERRORES]

    escribir_o_actualizar_fila(
        hoja,
        proyecto.codigo,
        [
            proyecto.codigo,
            proyecto.nombre_proyecto,
            mensaje_error,
        ],
    )

    aplicar_estilos_basicos(libro)
    libro.save(archivo_excel)


def generar_graficos(libro):
    nombre_hoja = "Graficos"

    if nombre_hoja in libro.sheetnames:
        del libro[nombre_hoja]

    hoja_graficos = libro.create_sheet(nombre_hoja)
    hoja_resumen = libro[HOJA_RESUMEN]
    hoja_complejidad = libro[HOJA_COMPLEJIDAD]
    hoja_mantenibilidad = libro[HOJA_MANTENIBILIDAD]

    if hoja_resumen.max_row < 2:
        return

    grafico_cc = BarChart()
    grafico_cc.title = "CCN promedio por proyecto"
    grafico_cc.y_axis.title = "CCN promedio"
    grafico_cc.x_axis.title = "Proyecto"

    datos_cc = Reference(
        hoja_resumen,
        min_col=6,
        min_row=1,
        max_row=hoja_resumen.max_row,
    )

    categorias = Reference(
        hoja_resumen,
        min_col=1,
        min_row=2,
        max_row=hoja_resumen.max_row,
    )

    grafico_cc.add_data(datos_cc, titles_from_data=True)
    grafico_cc.set_categories(categorias)
    hoja_graficos.add_chart(grafico_cc, "A1")

    grafico_nloc = BarChart()
    grafico_nloc.title = "NLOC total por proyecto"
    grafico_nloc.y_axis.title = "NLOC total"
    grafico_nloc.x_axis.title = "Proyecto"

    datos_nloc = Reference(
        hoja_complejidad,
        min_col=5,
        min_row=1,
        max_row=hoja_complejidad.max_row,
    )

    categorias_nloc = Reference(
        hoja_complejidad,
        min_col=1,
        min_row=2,
        max_row=hoja_complejidad.max_row,
    )

    grafico_nloc.add_data(datos_nloc, titles_from_data=True)
    grafico_nloc.set_categories(categorias_nloc)
    hoja_graficos.add_chart(grafico_nloc, "A18")

    if hoja_mantenibilidad.max_row >= 2:
        grafico_mi = BarChart()
        grafico_mi.title = "MI por proyecto"
        grafico_mi.y_axis.title = "Índice de mantenibilidad"
        grafico_mi.x_axis.title = "Proyecto"

        datos_mi = Reference(
            hoja_mantenibilidad,
            min_col=5,
            min_row=1,
            max_row=hoja_mantenibilidad.max_row,
        )

        categorias_mi = Reference(
            hoja_mantenibilidad,
            min_col=1,
            min_row=2,
            max_row=hoja_mantenibilidad.max_row,
        )

        grafico_mi.add_data(datos_mi, titles_from_data=True)
        grafico_mi.set_categories(categorias_mi)
        hoja_graficos.add_chart(grafico_mi, "A35")
