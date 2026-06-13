from config_manager import cargar_configuracion, cargar_proyectos, clasificar_ccn
from lizard_analyzer import ejecutar_lizard
from excel_manager import guardar_resultado_excel, guardar_error_excel
from utils import configurar_logger, crear_directorio


def main():
    configuracion = cargar_configuracion("configuracion.json")

    archivo_excel = configuracion["archivo_excel"]
    carpeta_resultados = configuracion["carpeta_resultados"]
    carpeta_logs = configuracion["carpeta_logs"]
    umbrales_cc = configuracion["umbrales_cc"]

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
                logger=logger
            )

            nivel_cc, interpretacion_cc = clasificar_ccn(
                metricas.ccn_promedio,
                umbrales_cc
            )

            metricas.nivel_cc = nivel_cc
            metricas.interpretacion_cc = interpretacion_cc

            guardar_resultado_excel(
                archivo_excel=archivo_excel,
                proyecto=proyecto,
                metricas=metricas
            )

            logger.info(
                f"Finalizado {proyecto.codigo}: "
                f"CCN promedio={metricas.ccn_promedio}, "
                f"Nivel={metricas.nivel_cc}"
            )

        except Exception as error:
            mensaje_error = str(error)
            logger.error(f"Error procesando {proyecto.codigo}: {mensaje_error}")

            guardar_error_excel(
                archivo_excel=archivo_excel,
                proyecto=proyecto,
                mensaje_error=mensaje_error
            )

    logger.info("Proceso finalizado")


if __name__ == "__main__":
    main()
