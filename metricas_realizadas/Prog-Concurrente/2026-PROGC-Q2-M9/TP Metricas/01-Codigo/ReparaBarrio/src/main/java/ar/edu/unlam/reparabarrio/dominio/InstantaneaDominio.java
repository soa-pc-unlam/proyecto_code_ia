package ar.edu.unlam.reparabarrio.dominio;

import java.util.*;
import static ar.edu.unlam.reparabarrio.dominio.Recurso.Disponibilidad.*;

/** Entrada inmutable validada, sin almacenamiento ni sincronización.
 * Quien capture los datos en H2 debe garantizar su coherencia bajo el coordinador.
 * Las asignaciones recibidas son activas; las propuestas nunca se incluyen aquí.
 */
public record InstantaneaDominio(String contexto, List<Orden> ordenes, List<Recurso> recursos,
                                Map<String, SeleccionRecursos> asignaciones) {
    public InstantaneaDominio {
        Identidad.validar(contexto);
        ordenes = List.copyOf(ordenes); recursos = List.copyOf(recursos);
        asignaciones = Map.copyOf(asignaciones);
        var ids = new HashSet<String>(); var secuencias = new HashSet<Long>();
        for (var o : ordenes)
            if (!ids.add(o.id()) || !secuencias.add(o.secuencia()))
                throw new IllegalArgumentException("Orden o secuencia duplicada");
        var inventario = new HashMap<Recurso.Clave, Recurso>();
        for (var r : recursos)
            if (inventario.putIfAbsent(r.clave(), r) != null)
                throw new IllegalArgumentException("Recurso duplicado");
        var ocupadas = new HashSet<Recurso.Clave>();
        for (var o : ordenes) {
            var a = asignaciones.get(o.id());
            if ((o.estado() == EstadoOrden.EN_CURSO) != (a != null))
                throw new IllegalArgumentException("INV-02/03: asignación incompatible con estado");
            if (a == null) continue;
            validarSeleccion(o, a, inventario);
            for (var c : a.unidades())
                if (!ocupadas.add(c)) throw new IllegalArgumentException("INV-01: unidad compartida");
        }
        if (!ids.containsAll(asignaciones.keySet())) throw new IllegalArgumentException("Asignación sin orden");
    }
    public static void validarSeleccion(Orden orden, SeleccionRecursos seleccion,
                                         Map<Recurso.Clave, Recurso> inventario) {
        if (!orden.requisitos().equals(seleccion.herramientas().keySet()))
            throw new IllegalArgumentException("INV-02: requisitos incompletos o extras");
        for (var clave : seleccion.unidades()) {
            var r = inventario.get(clave);
            if (r == null || !r.habilitado()) throw new IllegalArgumentException("Unidad inexistente o inhabilitada");
        }
        seleccion.herramientas().forEach((tipo, clave) -> {
            if (inventario.get(clave).tipo() != tipo) throw new IllegalArgumentException("Tipo físico incompatible");
        });
    }
    public Recurso.Disponibilidad disponibilidad(Recurso.Clave clave) {
        var recurso = recursos.stream().filter(r -> r.clave().equals(clave)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Recurso inexistente"));
        if (asignaciones.values().stream().anyMatch(a -> a.unidades().contains(clave))) return OCUPADO;
        return recurso.habilitado() ? DISPONIBLE : FUERA_DE_SERVICIO;
    }
}
