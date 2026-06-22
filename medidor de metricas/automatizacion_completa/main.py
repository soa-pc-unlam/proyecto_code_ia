from pathlib import Path

from metricas.bugs_smells import analizar_bugs_smells
from metricas.complejidad import ejecutar_lizard
from metricas.concurrencia import analizar_concurrencia
from metricas.mantenibilidad import analizar_mantenibilidad
from reportes.excel import guardar_concurrencia_excel, guardar_error_excel, guardar_resultado_excel
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
    umbrales_issues = configuracion["umbrales_issues"]
    umbrales_isi = configuracion["umbrales_isi"]
    archivo_datos_entrada = configuracion["archivo_datos_entrada"]
    ponderacion_concurrencia = configuracion["ponderacion_concurrencia"]
    umbrales_concurrencia = configuracion["umbrales_concurrencia"]

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

            metricas_bugs_smells = None
            try:
                metricas_bugs_smells = analizar_bugs_smells(
                    proyecto=proyecto,
                    carpeta_resultados=carpeta_resultados,
                    umbrales_issues=umbrales_issues,
                    umbrales_isi=umbrales_isi,
                    logger=logger,
                    loc_codigo=metricas_mi.nloc_mi
                )
            except Exception as error_bugs_smells:
                mensaje_error = f"Error en analisis de bugs/smells: {error_bugs_smells}"
                logger.error(f"{proyecto.codigo}: {mensaje_error}")
                guardar_error_excel(
                    archivo_excel=archivo_excel,
                    proyecto=proyecto,
                    mensaje_error=mensaje_error,
                )

            guardar_resultado_excel(
                archivo_excel=archivo_excel,
                proyecto=proyecto,
                metricas=metricas,
                metricas_mi=metricas_mi,
                metricas_bugs_smells=metricas_bugs_smells
            )

            metricas_concurrencia = None
            try:
                metricas_concurrencia = analizar_concurrencia(
                    proyecto=proyecto,
                    archivo_datos_entrada=archivo_datos_entrada,
                    ponderacion=ponderacion_concurrencia,
                    umbrales=umbrales_concurrencia,
                )
                guardar_concurrencia_excel(archivo_excel, metricas_concurrencia)
            except Exception as error_concurrencia:
                mensaje_error = f"Error en análisis de concurrencia: {error_concurrencia}"
                logger.error(f"{proyecto.codigo}: {mensaje_error}")
                guardar_error_excel(archivo_excel, proyecto, mensaje_error)

            logger.info(
                f"Finalizado {proyecto.codigo}: "
                f"CCN promedio={metricas.ccn_promedio}, "
                f"Nivel CC={metricas.nivel_cc}, "
                f"MI={metricas_mi.mi}, "
                f"Nivel MI={metricas_mi.nivel_mi}, "
                f"Issues/KLOC={metricas_bugs_smells.issues_kloc if metricas_bugs_smells else 'Sin datos'}, "
                f"ISI={metricas_bugs_smells.isi if metricas_bugs_smells else 'Sin datos'}, "
                f"Promedio concurrencia="
                f"{metricas_concurrencia.promedio if metricas_concurrencia else 'Sin datos'}"
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
