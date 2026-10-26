package ar.edu.unlam.reparabarrio.dominio;

import java.util.Map;
import java.util.Objects;
import static ar.edu.unlam.reparabarrio.dominio.Recurso.Categoria.*;

/** Conjunto físico exacto, reutilizable como propuesta o asignación activa. */
public record SeleccionRecursos(Recurso.Clave tecnico, Recurso.Clave puesto,
                                Map<TipoHerramienta, Recurso.Clave> herramientas) {
    public SeleccionRecursos {
        Objects.requireNonNull(tecnico); Objects.requireNonNull(puesto);
        herramientas = Map.copyOf(herramientas);
        if (tecnico.categoria() != TECNICO || puesto.categoria() != PUESTO
                || herramientas.values().stream().anyMatch(c -> c.categoria() != HERRAMIENTA)
                || herramientas.values().stream().distinct().count() != herramientas.size())
            throw new IllegalArgumentException("Categorías o unidades duplicadas inválidas");
    }
    public java.util.Set<Recurso.Clave> unidades() {
        var conjunto = new java.util.HashSet<>(herramientas.values());
        conjunto.add(tecnico); conjunto.add(puesto);
        return java.util.Set.copyOf(conjunto);
    }
}
