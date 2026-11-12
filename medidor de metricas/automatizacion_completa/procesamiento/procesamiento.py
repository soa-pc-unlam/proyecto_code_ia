from reportes.escritor_resultados import EscritorResultadosExcel
"""Gestiona el procesamiento concurrente de los proyectos."""

from concurrent.futures import ThreadPoolExecutor
from threading import Semaphore

from constantes import definiciones
from procesamiento.analisis import (
    analizar_bugs_smells_seguro,
    analizar_complejidad,
    analizar_concurrencia_seguro,
    analizar_cpu_memoria_seguro,
    analizar_mi,
    analizar_tokens_seguro,
)
from modelos.modelos import ContextoAnalisis, ResultadoProyecto
from util.exception import LenguajeNoSoportadoError
from util.lenguajes import normalizar_lenguaje
from procesamiento.etapas import (
    EtapaMetrica, EstadoPipeline, PipelineMetricas, ValidadorFuentesProyecto,
)


class FabricaEtapasMetricas:
    """Factory: ensambla las estrategias sin duplicar lógica de analizadores.

    Las referencias a funciones se resuelven en ejecución para mantener los
    contratos públicos y facilitar su sustitución durante las pruebas.
    """

    @staticmethod
    def crear():
        return PipelineMetricas((
            EtapaMetrica(
                "metricas_cc",
                lambda e: analizar_complejidad(
                    e.proyecto, e.lenguaje, e.configuracion, e.logger, e.contexto,
                    fuentes_wiring=e.fuentes_wiring,
                ),
                requiere_semaforo=True,
            ),
            EtapaMetrica(
                "metricas_mi",
                lambda e: analizar_mi(
                    e.proyecto, e.configuracion, e.logger, e.contexto
                ),
            ),
            EtapaMetrica(
                "metricas_bugs_smells",
                lambda e: analizar_bugs_smells_seguro(
                    proyecto=e.proyecto, lenguaje=e.lenguaje,
                    configuracion=e.configuracion, logger=e.logger,
                    metricas_mi=e.valores["metricas_mi"], contexto=e.contexto,
                    fuentes_wiring=e.fuentes_wiring,
                ),
                requiere_semaforo=True,
            ),
            EtapaMetrica(
                "metricas_concurrencia",
                lambda e: analizar_concurrencia_seguro(
                    proyecto=e.proyecto, configuracion=e.configuracion,
                    logger=e.logger, libro_entrada=e.libro_entrada,
                    contexto=e.contexto,
                ),
            ),
            EtapaMetrica(
                "metricas_tokens",
                lambda e: analizar_tokens_seguro(
                    proyecto=e.proyecto, configuracion=e.configuracion,
                    logger=e.logger, libro_entrada=e.libro_entrada,
                    metricas_cc=e.valores["metricas_cc"], contexto=e.contexto,
                ),
            ),
            EtapaMetrica(
                "metricas_cpu_memoria",
                lambda e: analizar_cpu_memoria_seguro(
                    proyecto=e.proyecto, configuracion=e.configuracion,
                    logger=e.logger, libro_entrada=e.libro_entrada,
                    contexto=e.contexto,
                ),
            ),
        ))


class AnalizadorProyecto:
    """Procesa un proyecto con estrategias independientes y estado aislado."""

    def __init__(self, configuracion, logger, libro_entrada, semaforo_analizadores,
                 pipeline=None, validador=None):
        self.configuracion = configuracion
        self.logger = logger
        self.libro_entrada = libro_entrada
        self.semaforo_analizadores = semaforo_analizadores
        self.pipeline = pipeline if pipeline is not None else FabricaEtapasMetricas.crear()
        self.validador = validador if validador is not None else ValidadorFuentesProyecto()

    def analizar(self, proyecto):
        contexto = ContextoAnalisis()
        self.logger.info(f"[{proyecto.codigo}] Inicio - {proyecto.nombre_proyecto}")
        estado = EstadoPipeline(
            proyecto=proyecto, lenguaje="", configuracion=self.configuracion,
            logger=self.logger, libro_entrada=self.libro_entrada, contexto=contexto,
        )
        omitir_metricas_codigo = False
        try:
            estado.lenguaje = normalizar_lenguaje(proyecto.lenguaje)
            try:
                estado.fuentes_wiring = self.validador.validar(proyecto, estado.lenguaje)
            except ValueError as error:
                # Una validación negativa es un caso previsto, no un fallo interno.
                # Se conserva el motivo en el resultado para la hoja Errores.
                mensaje_error = str(error)
                self.logger.warning(f"[{proyecto.codigo}] Proyecto omitido: {mensaje_error}")
                contexto.errores.append(mensaje_error)
                # La validación de fuentes Wiring es exclusiva de Wiring.
                omitir_metricas_codigo = estado.lenguaje.lower() == definiciones.LENGUAJE_WIRING
            else:
                self.pipeline.ejecutar(estado, self.semaforo_analizadores)
        except LenguajeNoSoportadoError as error:
            mensaje_error = str(error)
            self.logger.error(f"[{proyecto.codigo}] {mensaje_error}")
            contexto.errores.append(mensaje_error)
        except Exception as error:
            mensaje_error = f"Error procesando proyecto: {error}"
            self.logger.exception(f"[{proyecto.codigo}] {mensaje_error}")
            contexto.errores.append(mensaje_error)

        informar_fin_procesamiento(contexto.errores, self.logger, proyecto.codigo)
        return ResultadoProyecto(
            proyecto=proyecto, **estado.valores, errores=list(contexto.errores),
            omitir_metricas_codigo=omitir_metricas_codigo,
        )


class EscritorResultados:
    """Único responsable de volcar los resultados al libro compartido."""

    def __init__(self, libro_salida, logger):
        self.libro_salida = libro_salida
        self.logger = logger

    def guardar(self, resultado):
        guardar_resultado_proyecto(self.libro_salida, resultado, self.logger)

    def guardar_error(self, proyecto, mensaje):
        EscritorResultadosExcel().guardar_error(libro=self.libro_salida, proyecto=proyecto, mensaje_error=mensaje)


class GestorProcesamiento:
    """Coordina tareas concurrentes sin compartir un libro de salida entre hilos."""

    def __init__(self, configuracion, libro_salida, libro_entrada, logger):
        self.configuracion = configuracion
        self.libro_entrada = libro_entrada
        self.logger = logger
        self.escritor = EscritorResultados(libro_salida, logger)
        self.semaforo = Semaphore(definiciones.MAX_ANALIZADORES_PESADOS)

    def ejecutar(self, proyectos):
        with ThreadPoolExecutor(max_workers=definiciones.MAX_WORKERS) as executor:
            futuros = ejecutar_procesamiento_proyectos(
                executor, proyectos, self.configuracion, self.logger,
                self.libro_entrada, self.semaforo,
            )
            for futuro, proyecto in futuros.items():
                try:
                    self.escritor.guardar(futuro.result())
                except Exception as error:
                    mensaje = f"Error en el procesamiento del proyecto: {error}"
                    self.logger.exception(f"[{proyecto.codigo}] {mensaje}")
                    self.escritor.guardar_error(proyecto, mensaje)


def gestionar_procesamiento_proyectos(proyectos, configuracion, libro_salida, libro_entrada, logger):
    """ Funcion wrapper que gestiona el procesamiento concurrente de los proyectos y 
        la escritura de resultados."""
    return GestorProcesamiento(configuracion, libro_salida, libro_entrada, logger).ejecutar(proyectos)


def ejecutar_procesamiento_proyectos(executor, proyectos, configuracion, logger, libro_entrada, semaforo_analizadores):
    """Programa un proyecto por tarea y devuelve el mapa Future-proyecto."""
    return {
        executor.submit(
            procesar_proyecto, proyecto, configuracion, logger,
            libro_entrada, semaforo_analizadores
        ): proyecto
        for proyecto in proyectos
    }


def procesar_proyecto(proyecto, configuracion, logger, libro_entrada, semaforo_analizadores):
    return AnalizadorProyecto(configuracion, logger, libro_entrada, semaforo_analizadores).analizar(proyecto)


def informar_fin_procesamiento(errores, logger, proyecto_codigo=None):
    """Registra si el proyecto terminó correctamente o con errores.

    Args:
        errores: Colección de mensajes de error del proyecto.
        logger: Registrador de eventos de la ejecución.
        proyecto_codigo: Código del proyecto para identificar sus mensajes; puede ser None.
    """
    if errores:
        logger.warning(f"[{proyecto_codigo}] Finalizado con {len(errores)} error(es)")
    else:
        logger.info(f"[{proyecto_codigo}] Finalizado correctamente")

def guardar_resultado_proyecto(libro_salida, resultado, logger):
    """Escribe las métricas y los errores del proyecto en el libro en memoria.

    Args:
        libro_salida: Libro abierto que recibe las métricas y los errores en memoria.
        resultado: ResultadoProyecto con las métricas disponibles y los errores.
        logger: Registrador de eventos de la ejecución.
    """
    proyecto = resultado.proyecto

    EscritorResultadosExcel().guardar_resultado(
        libro=libro_salida,
        proyecto=proyecto,
        metricas_cc=resultado.metricas_cc,
        metricas_mi=resultado.metricas_mi,
        metricas_bugs_smells=resultado.metricas_bugs_smells,
        metricas_concurrencia=resultado.metricas_concurrencia,
        metricas_tokens=resultado.metricas_tokens,
        metricas_cpu_memoria=resultado.metricas_cpu_memoria,
        omitir_metricas_codigo=resultado.omitir_metricas_codigo,
    )

    for mensaje_error in resultado.errores or []:
        EscritorResultadosExcel().guardar_error(
            libro=libro_salida,
            proyecto=proyecto,
            mensaje_error=mensaje_error,
        )

    logger.debug(f"Resultados almacenados para {proyecto.codigo}")
