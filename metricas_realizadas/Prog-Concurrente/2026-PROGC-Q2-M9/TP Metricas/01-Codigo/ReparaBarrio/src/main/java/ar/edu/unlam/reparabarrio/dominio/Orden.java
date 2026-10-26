package ar.edu.unlam.reparabarrio.dominio;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/** Valor local: la secuencia se recibe, no se genera ni conserva en H1. */
public record Orden(String id, String descripcion, long secuencia,
                    Set<TipoHerramienta> requisitos, EstadoOrden estado) {
    public Orden {
        Identidad.validar(id);
        if (descripcion == null || descripcion.isBlank()) throw new IllegalArgumentException("Falta descripción");
        if (secuencia <= 0) throw new IllegalArgumentException("Secuencia positiva requerida");
        requisitos = Set.copyOf(requisitos);
        Objects.requireNonNull(estado);
    }
    public static Orden pendiente(String id, String descripcion, long secuencia,
                                   Collection<TipoHerramienta> requisitos) {
        Objects.requireNonNull(requisitos);
        if (new HashSet<>(requisitos).size() != requisitos.size())
            throw new IllegalArgumentException("Sólo una unidad por tipo");
        return new Orden(id, descripcion, secuencia, Set.copyOf(requisitos), EstadoOrden.PENDIENTE);
    }
    /** Valida solamente la máquina de estados; no confirma un inicio ni libera recursos. */
    public Orden transicionar(EstadoOrden destino) {
        if (!estado.permite(destino)) throw new IllegalArgumentException("Transición inválida: " + estado + " -> " + destino);
        return new Orden(id, descripcion, secuencia, requisitos, destino);
    }
}
