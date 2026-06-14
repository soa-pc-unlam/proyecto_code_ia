from pathlib import Path

from metricas.complejidad import ejecutar_lizard
from metricas.mantenibilidad import analizar_mantenibilidad
from reportes.excel import guardar_error_excel, guardar_resultado_excel
from util.archivos import crear_directorio
from util.configuracion import cargar_configuracion, cargar_proyectos, clasificar_ccn
from util.logging_config import configurar_logger


def main():
    configuracion = cargar_configuracion("configuracion.json")

    archivo_excel = configuracion["archivo_excel"]
    carpeta_resultados = configuracion["carpeta_resultados"]
    carpeta_logs = configuracion["carpeta_logs"]
    umbrales_cc = configuracion["umbrales_cc"]
    umbrales_mi = configuracion["umbrales_mi"]

    crear_directorio(carpeta_resultados)
    crear_directorio(carpeta_logs)

    logger = configurar_logger(carpeta_logs)
    logger.info("Inicio del análisis de métricas")

    proyectos = cargar_proyectos("proyectos.json")

    for proyecto in proyectos:
        try:
            logger.info(f"Analizando proyecto {proyecto.codigo} - {proyecto.nombre_proyecto}")

            metricas = ejecutar_lizard(
                proyecto=proyecto,
                carpeta_resultados=carpeta_resultados,
                logger=logger,
            )

            nivel_cc, interpretacion_cc = clasificar_ccn(
                metricas.ccn_promedio,
                umbrales_cc,
            )

            metricas.nivel_cc = nivel_cc
            metricas.interpretacion_cc = interpretacion_cc

            archivo_csv_lizard = Path(carpeta_resultados) / f"{proyecto.codigo}_lizard.csv"
            metricas_mi = analizar_mantenibilidad(
                proyecto=proyecto,
                archivo_csv_lizard=archivo_csv_lizard,
                carpeta_resultados=carpeta_resultados,
                umbrales_mi=umbrales_mi,
                logger=logger,
            )

            guardar_resultado_excel(
                archivo_excel=archivo_excel,
                proyecto=proyecto,
                metricas=metricas,
                metricas_mi=metricas_mi,
            )

            logger.info(
                f"Finalizado {proyecto.codigo}: "
                f"CCN promedio={metricas.ccn_promedio}, "
                f"Nivel CC={metricas.nivel_cc}, "
                f"MI={metricas_mi.mi}, "
                f"Nivel MI={metricas_mi.nivel_mi}"
            )

        except Exception as error:
            mensaje_error = str(error)
            logger.error(f"Error procesando {proyecto.codigo}: {mensaje_error}")

            guardar_error_excel(
                archivo_excel=archivo_excel,
                proyecto=proyecto,
                mensaje_error=mensaje_error,
            )

    logger.info("Proceso finalizado")


if __name__ == "__main__":
    main()
