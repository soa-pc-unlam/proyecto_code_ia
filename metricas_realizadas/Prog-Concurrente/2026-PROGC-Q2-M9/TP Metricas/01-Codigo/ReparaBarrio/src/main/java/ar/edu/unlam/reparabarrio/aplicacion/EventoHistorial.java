package ar.edu.unlam.reparabarrio.aplicacion;

import ar.edu.unlam.reparabarrio.dominio.Recurso;
import java.time.Instant;
import java.util.Set;

/** INICIO registra la asignación; FINALIZADA/FALLIDA registran su liberación completa. */
public record EventoHistorial(long secuencia, String operacionId, String ordenId,
                              String accion, Instant momento, Set<Recurso.Clave> recursos) {
    public EventoHistorial { recursos = Set.copyOf(recursos); }
}
