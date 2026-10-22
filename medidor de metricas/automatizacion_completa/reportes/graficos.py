"""Generación de gráficos para el reporte Excel."""

from openpyxl.chart import BarChart, Reference
from openpyxl.styles import Alignment, Font, PatternFill

from constantes.definiciones import (
    HOJA_COMPARACION,
    HOJA_GRAFICOS,
    TEXTO_MANUAL,
    TEXTO_VIBECODING,
    TEXTO_ENCABEZADO_CCN_PROMEDIO_RESUMEN,
    TEXTO_ENCABEZADO_MI_RESUMEN,
    TEXTO_ENCABEZADO_CPU_PROMEDIO,
    TEXTO_ENCABEZADO_MEMORIA_PROMEDIO_RESUMEN,
    TEXTO_ENCABEZADO_ISSUES_KLOC_RESUMEN,
)


def generar_graficos_pc(libro, configuracion):
    """Regenera la hoja de gráficos según la configuración recibida.

    Args:
        libro: Libro de Excel que contiene las hojas de datos.
        configuracion: Secuencia de configuraciones para cada gráfico.
    """
    nombre_hoja = HOJA_GRAFICOS
    if nombre_hoja in libro.sheetnames:
        del libro[nombre_hoja]
    hoja_graficos = libro.create_sheet(nombre_hoja)

    for nombre_datos, columna, titulo, eje_y, posicion in configuracion:
        hoja_datos = libro[nombre_datos]
        if hoja_datos.max_row >= 2:
            agregar_grafico_barras(
                hoja_graficos, hoja_datos, columna, titulo, eje_y, posicion
            )


def agregar_grafico_barras(hoja_destino, hoja_datos, columna, titulo, eje_y, posicion):
    """Agrega un gráfico de barras usando el código como categoría.

    Args:
        hoja_destino: Hoja en la que se inserta el gráfico.
        hoja_datos: Hoja que proporciona categorías y valores.
        columna: Número de la columna que contiene los valores.
        titulo: Título del gráfico.
        eje_y: Etiqueta del eje vertical.
        posicion: Celda de anclaje del gráfico en la hoja de destino.
    """
    grafico = BarChart()
    grafico.title = titulo
    grafico.y_axis.title = eje_y
    grafico.x_axis.title = "Proyecto"
    datos = Reference(
        hoja_datos,
        min_col=columna,
        min_row=1,
        max_row=hoja_datos.max_row,
    )
    categorias = Reference(
        hoja_datos,
        min_col=1,
        min_row=2,
        max_row=hoja_datos.max_row,
    )
    
    grafico.add_data(datos, titles_from_data=True)
    grafico.set_categories(categorias)
    hoja_destino.add_chart(grafico, posicion)


def preparar_hoja_graficos_comparacion(hoja):
    """Da a la hoja comparativa el aspecto del Excel de referencia."""
    
    hoja.sheet_view.showGridLines = False
    hoja.freeze_panes = "A4"
    hoja.column_dimensions["A"].width = 28
    hoja.column_dimensions["B"].width = 18
    hoja.column_dimensions["C"].width = 18
    hoja.column_dimensions["D"].width = 3
    
    for letra in "EFGHIJKLMNOPQR":
        hoja.column_dimensions[letra].width = 12

    hoja.merge_cells("A1:R1")
    hoja["A1"] = "COMPARACIÓN VISUAL · MANUAL Y VIBECODING"
    hoja["A1"].fill = PatternFill("solid", fgColor="17324D")
    hoja["A1"].font = Font(name="Calibri", size=14, bold=True, color="FFFFFF")
    hoja["A1"].alignment = Alignment(vertical="center")
    hoja.row_dimensions[1].height = 33

    hoja["A2"] = "Proyecto y dispositivo"
    hoja["A2"].font = Font(bold=True)
    hoja["B2"] = (
        '=IF(OR(Comparacion!B3="",Comparacion!B4=""),"",'
        'Comparacion!B3&" · "&Comparacion!B4)'
    )
    hoja["B2"].font = Font(bold=True, color="2866A8")

    hoja.merge_cells("A3:R3")
    hoja["A3"] = (
        "Las gráficas muestran solo mediciones numéricas registradas. "
        "Comparar únicamente el mismo proyecto y dispositivo."
    )
    hoja["A3"].fill = PatternFill("solid", fgColor="EAF3F8")
    hoja["A3"].font = Font(name="Calibri", size=10, color="5D7081")
    hoja.row_dimensions[3].height = 24

    for fila, titulo in (
        (4, "Complejidad"),
        (9, "Mantenibilidad"),
        (14, "Recursos (%)"),
        (21, "Densidad de issues"),
    ):
        for columna, texto in enumerate((titulo, TEXTO_MANUAL, TEXTO_VIBECODING), 1):
            celda = hoja.cell(fila, columna, texto)
            celda.fill = PatternFill("solid", fgColor="2866A8")
            celda.font = Font(name="Calibri", size=11, bold=True, color="FFFFFF")
            celda.alignment = Alignment(horizontal="center" if columna > 1 else "left")
        hoja.row_dimensions[fila].height = 25

    hoja.merge_cells("A27:D29")
    
    hoja["A27"] = (
        "MI requiere un valor del analizador. CPU de ESP32 requiere "
        "instrumentación adecuada. Las mediciones deben utilizar "
        "el mismo hardware y la misma carga."
    )
    hoja["A27"].fill = PatternFill("solid", fgColor="F4F8FB")
    hoja["A27"].font = Font(name="Calibri", size=11, color="5D7081")
    hoja["A27"].alignment = Alignment(vertical="top", wrap_text=True)
    hoja.row_dimensions[27].height = 46


def cargar_metricas_graficos_comparacion(hoja, comparacion):
    """Enlaza las métricas por nombre, sin depender de filas fijas."""
    
    filas = {
        str(comparacion.cell(fila, 1).value).strip(): fila
        
        for fila in range(7, comparacion.max_row + 1)
        if comparacion.cell(fila, 1).value is not None
    }

    datos = (
        (5, TEXTO_ENCABEZADO_CCN_PROMEDIO_RESUMEN, "CCN promedio", "0.00"),
        (10, TEXTO_ENCABEZADO_MI_RESUMEN, "MI", "0.00"),
        (15, TEXTO_ENCABEZADO_CPU_PROMEDIO, "CPU promedio (%)", '0.00"%"'),
        (16, TEXTO_ENCABEZADO_MEMORIA_PROMEDIO_RESUMEN, "Memoria promedio (%)", '0.00"%"'),
        (22, TEXTO_ENCABEZADO_ISSUES_KLOC_RESUMEN, "Issues/KLOC", "0.00"),
    )

    for fila_destino, campo, etiqueta, formato in datos:
        if campo not in filas:
            raise ValueError(f"No se encontró la métrica '{campo}' en la hoja {comparacion.title}.")

        hoja.cell(fila_destino, 1, etiqueta)

        for columna in ("B", "C"):
            referencia = f"'{comparacion.title}'!{columna}{filas[campo]}"
            celda = hoja[f"{columna}{fila_destino}"]
            # No representa 'Sin datos' o 'No aplica' como si fueran cero.
            celda.value = f'=IF(ISNUMBER({referencia}),{referencia},"")'
            celda.number_format = formato
            celda.alignment = Alignment(horizontal="center")


def agregar_grafico_comparativo(hoja, fila_encabezado, primera_fila, ultima_fila,titulo, eje_y, posicion):
    """Agrega dos series, Manual y Vibecoding, al gráfico de columnas."""

    grafico = BarChart()
    grafico.type = "col"
    grafico.grouping = "clustered"
    grafico.style = 10
    grafico.title = titulo
    grafico.y_axis.title = eje_y
    grafico.legend.position = "b"
    grafico.width = 16.5
    grafico.height = 7.4

    datos = Reference(
        hoja, min_col=2, max_col=3,
        min_row=fila_encabezado,
        max_row=ultima_fila
    )

    categorias = Reference(
        hoja, 
        min_col=1,
        min_row=primera_fila, 
        max_row=ultima_fila
    )
    grafico.add_data(datos, titles_from_data=True)
    grafico.set_categories(categorias)
    hoja.add_chart(grafico, posicion)


def generar_graficos_comparativo_dispositivos(libro):
    """Crea los cuatro gráficos dinámicos del perfil Android/ESP32."""

    if HOJA_COMPARACION not in libro.sheetnames:
        raise ValueError("Primero debe generarse la hoja Comparacion.")

    if HOJA_GRAFICOS in libro.sheetnames:
        del libro[HOJA_GRAFICOS]

    indice = libro.sheetnames.index(HOJA_COMPARACION) + 1
    hoja = libro.create_sheet(HOJA_GRAFICOS, indice)
    
    preparar_hoja_graficos_comparacion(hoja)
    cargar_metricas_graficos_comparacion(hoja, libro[HOJA_COMPARACION])

    configuracion = (
        (4, 5, 5, "Complejidad ciclomática (CCN)", "CCN promedio", "E4"),
        (9, 10, 10, "Índice de mantenibilidad (MI)", "MI", "N4"),
        (14, 15, 16, "CPU y memoria (%)", "Porcentaje", "E17"),
        (21, 22, 22, "Issues por cada 1000 líneas", "Issues/KLOC", "N17"),
    )
    
    for encabezado, desde, hasta, titulo, eje_y, posicion in configuracion:
        agregar_grafico_comparativo(hoja, encabezado, desde, hasta, titulo, eje_y, posicion)

    libro.calculation.calcMode = "auto"
    libro.calculation.fullCalcOnLoad = True
    libro.calculation.forceFullCalc = True
