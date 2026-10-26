package ar.edu.unlam.reparabarrio.aplicacion;

import java.util.concurrent.locks.ReentrantLock;

/** Protege transacciones, salud y diagnóstico de un solo contexto de datos.
 * H4: también protege el ciclo, sesiones y conexiones residuales del propietario.
 * Infraestructura/intención, negocio y resolución se ejecutan secuencialmente.
 * No protege trabajos físicos ni retiene el lock entre propuesta y confirmación.
 * La justicia del lock no garantiza progreso de una orden bloqueada por prioridad.
 */
public final class CoordinadorTaller {
    final ReentrantLock bloqueo = new ReentrantLock(true);
    String operacionIncierta;
    String diagnostico;
}
