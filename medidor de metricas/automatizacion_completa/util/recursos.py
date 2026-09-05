"""Controla los recursos de CPU utilizados por la aplicación."""

import psutil


def limitar_cpu(porcentaje, logger):
    """Limita los CPUs lógicos que puede utilizar el programa.

    La afinidad configurada también es heredada normalmente por los
    procesos externos ejecutados por la aplicación.

    Args:
        porcentaje: Fracción de CPUs que se desea habilitar.
        logger: Registrador de eventos de la ejecución.

    Returns:
        Lista de CPUs lógicas habilitadas.
    """
    proceso = psutil.Process()

    nucleos_disponibles = proceso.cpu_affinity()

    cantidad = max(1,int(len(nucleos_disponibles) * porcentaje))

    nucleos_permitidos = nucleos_disponibles[:cantidad]

    proceso.cpu_affinity(nucleos_permitidos)

    porcentaje_real = (len(nucleos_permitidos) / len(nucleos_disponibles)* 100)

    logger.info(
        f"CPU limitada a {len(nucleos_permitidos)} de "
        f"{len(nucleos_disponibles)} CPUs lógicas "
        f"({porcentaje_real:.1f} %)"
    )

    return nucleos_permitidos