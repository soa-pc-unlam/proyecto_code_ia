from asyncio.log import logger
from copy import error
from pathlib import Path

from metricas.bugs_smells import analizar_bugs_smells
from metricas.complejidad import ejecutar_lizard
from metricas.concurrencia import analizar_concurrencia
from metricas.mantenibilidad import analizar_mantenibilidad
from reportes.excel import guardar_concurrencia_excel, guardar_error_excel, guardar_resultado_excel
from util import configuracion
from util.archivos import crear_directorio
from util.configuracion import cargar_configuracion, cargar_proyectos, clasificar_ccn
from util.logging_config import configurar_logger


def main():
    configuracion = cargar_configuracion("configuracion.json")


  
    inicializar_directorios(configuracion)
    
    logger = configurar_logger(configuracion["carpeta_logs"])
    logger.info("Inicio del análisis de métricas")

    proyectos = cargar_proyectos("proyectos.json")

    for proyecto in proyectos:
        procesar_proyecto(proyecto, configuracion, logger)

    informar_resultados_finales(logger, configuracion)


def inicializar_directorios(configuracion):
    crear_directorio(configuracion["carpeta_resultados"])
    crear_directorio(configuracion["carpeta_logs"])

def procesar_proyecto(proyecto, configuracion, logger):
    try:
        logger.info(f"Analizando proyecto {proyecto.codigo} - {proyecto.nombre_proyecto}")

        metricas = analizar_complejidad(proyecto, configuracion, logger)
        metricas_mi = analizar_mi(proyecto, configuracion, logger)

        metricas_bugs_smells = analizar_bugs_smells_seguro(
            proyecto=proyecto,
            configuracion=configuracion,
            logger=logger,
            metricas_mi=metricas_mi,
        )
        
        guardar_resultado_excel(
            archivo_excel=configuracion["archivo_excel"],
            proyecto=proyecto,
            metricas=metricas,
            metricas_mi=metricas_mi,
            metricas_bugs_smells=metricas_bugs_smells
        )
        
        metricas_concurrencia = analizar_concurrencia_seguro(
            proyecto=proyecto,
            configuracion=configuracion,
            logger=logger,
        )
        
        registrar_fin_proyecto(
            logger=logger,
            proyecto=proyecto,
            metricas=metricas,
            metricas_mi=metricas_mi,
            metricas_bugs_smells=metricas_bugs_smells,
            metricas_concurrencia=metricas_concurrencia,
        )
        
    except Exception as error:
        mensaje_error = str(error)
        logger.error(f"Error procesando {proyecto.codigo}: {mensaje_error}")

        guardar_error_excel(
            archivo_excel=configuracion["archivo_excel"],
            proyecto=proyecto,
            mensaje_error=mensaje_error,
        )




def analizar_complejidad(proyecto, configuracion, logger):
    
    try:
        metricas = ejecutar_lizard(
            proyecto=proyecto,
            carpeta_resultados=configuracion["carpeta_resultados"],
            logger=logger,
        )

        nivel_cc, interpretacion_cc = clasificar_ccn(
            metricas.ccn_promedio,
            configuracion["umbrales_cc"],
        )

        metricas.nivel_cc = nivel_cc
        metricas.interpretacion_cc = interpretacion_cc
        return metricas    
    except Exception as error:
        mensaje_error = f"Error en analisis de complejidad: {error}"
        logger.error(f"{proyecto.codigo}: {mensaje_error}")
        guardar_error_excel(
            archivo_excel=configuracion["archivo_excel"],   
            proyecto=proyecto,
            mensaje_error=mensaje_error,
        )
        return None

def analizar_mi(proyecto, configuracion, logger):
    try:
        archivo_csv_lizard = Path(configuracion["carpeta_resultados"]) / f"{proyecto.codigo}_lizard.csv"

        metricas_mi = analizar_mantenibilidad(
            proyecto=proyecto,
            archivo_csv_lizard=archivo_csv_lizard,
            carpeta_resultados=configuracion["carpeta_resultados"],
            umbrales_mi=configuracion["umbrales_mi"],
            logger=logger,
        )
        return metricas_mi
    except Exception as error:
        mensaje_error = f"Error en analisis de mantenibilidad: {error}"
        logger.error(f"{proyecto.codigo}: {mensaje_error}")
        guardar_error_excel(
            archivo_excel=configuracion["archivo_excel"],   
            proyecto=proyecto,
            mensaje_error=mensaje_error,
        )
        return None



def analizar_bugs_smells_seguro(proyecto, configuracion, logger, metricas_mi):
    try:
        metricas_bugs_smells = analizar_bugs_smells(
            proyecto=proyecto,
            carpeta_resultados=configuracion["carpeta_resultados"],
            umbrales_issues=configuracion["umbrales_issues"],
            umbrales_isi=configuracion["umbrales_isi"],
            logger=logger,
            loc_codigo=metricas_mi.nloc_mi
        )
        return metricas_bugs_smells
    except Exception as error:
        mensaje_error = f"Error en analisis de bugs/smells: {error}"
        logger.error(f"{proyecto.codigo}: {mensaje_error}")
        guardar_error_excel(
            archivo_excel=configuracion["archivo_excel"],   
            proyecto=proyecto,
            mensaje_error=mensaje_error,
        )
        return None

def analizar_concurrencia_seguro(proyecto, configuracion, logger):
    try:
        metricas_concurrencia = analizar_concurrencia(
            proyecto=proyecto,
            archivo_datos_entrada=configuracion["archivo_datos_entrada"],
            ponderacion=configuracion["ponderacion_concurrencia"],
            umbrales=configuracion["umbrales_concurrencia"],
            logger=logger,
        )
        guardar_concurrencia_excel(
            configuracion["archivo_excel"],
            metricas_concurrencia,
        )
        return metricas_concurrencia
    except Exception as error:
        mensaje_error = f"Error en analisis de concurrencia: {error}"
        logger.error(f"{proyecto.codigo}: {mensaje_error}")
        guardar_error_excel(
        archivo_excel=configuracion["archivo_excel"],   
        proyecto=proyecto,
        mensaje_error=mensaje_error,
    )
    return None


def registrar_fin_proyecto(
    logger,
    proyecto,
    metricas,
    metricas_mi,
    metricas_bugs_smells,
    metricas_concurrencia,
):
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

def informar_resultados_finales(logger,configuracion):
    logger.info("")
    logger.info("=" * 40)
    logger.info("== Proceso finalizado ==")
    logger.info("=" * 40)

    logger.info("Informe creado en el archivo Excel: " + str(configuracion["archivo_excel"]))
 

if __name__ == "__main__":
    main()
