"""Adaptador para invocar analizadores estáticos sin acoplarlos al proceso."""

import subprocess

from constantes.definiciones import TIEMPO_MAXIMO_ANALIZADOR


class EjecutorHerramientas:
    """Adapter: unifica la ejecución de herramientas externas.

    Conserva los parámetros de subprocess.run y no interpreta códigos de
    retorno: cada herramienta tiene sus propias reglas de éxito y error.
    """

    def __init__(self, ejecutar_proceso=None):
        self.ejecutar_proceso = ejecutar_proceso

    def ejecutar(self, comando, timeout=TIEMPO_MAXIMO_ANALIZADOR,
                entrada=None):
        """Ejecuta el comando capturando stdout/stderr en UTF-8.

        Args:
            comando: Argumentos del proceso como secuencia.
            timeout: Tiempo máximo de ejecución en segundos.
            entrada: Texto opcional suministrado a stdin.

        Returns:
            Objeto CompletedProcess del ejecutable.

        Raises:
            subprocess.TimeoutExpired: Si el proceso supera el límite.
        """
        ejecutor = self.ejecutar_proceso or subprocess.run
        argumentos = {
            "capture_output": True,
            "text": True,
            "encoding": "utf-8",
            "errors": "replace",
            "timeout": timeout,
        }
        if entrada is not None:
            argumentos["input"] = entrada
        return ejecutor(comando, **argumentos)
