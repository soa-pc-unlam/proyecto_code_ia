package ar.edu.unlam.reparabarrio.planificacion;

import ar.edu.unlam.reparabarrio.dominio.*;
import java.util.*;
import static ar.edu.unlam.reparabarrio.dominio.Recurso.Categoria.*;
import static ar.edu.unlam.reparabarrio.dominio.Recurso.Disponibilidad.DISPONIBLE;

/** Funciones puras sobre una entrada coherente; no adquieren ni reservan unidades. */
public final class Planificador {
    public record Resultado(Optional<PropuestaInicio> propuesta, Map<String, List<String>> causas) {
        public Resultado {
            Objects.requireNonNull(propuesta);
            var copia = new LinkedHashMap<String, List<String>>();
            causas.forEach((id, motivos) -> copia.put(id, List.copyOf(motivos)));
            causas = Collections.unmodifiableMap(copia);
        }
    }
    private List<Recurso> libres(InstantaneaDominio entrada) {
        return entrada.recursos().stream().filter(r -> entrada.disponibilidad(r.clave()) == DISPONIBLE)
                .sorted(Comparator.comparing(r -> r.clave().id(), new OrdenNaturalId())).toList();
    }
    public Resultado proponer(InstantaneaDominio entrada) {
        var libres = libres(entrada);
        var causas = new LinkedHashMap<String, List<String>>();
        PropuestaInicio primera = null;
        for (var orden : entrada.ordenes().stream().filter(o -> o.estado() == EstadoOrden.PENDIENTE)
                .sorted(Comparator.comparingLong(Orden::secuencia)).toList()) {
            var motivos = new ArrayList<String>();
            var tecnico = libres.stream().filter(r -> r.clave().categoria() == TECNICO).findFirst();
            var puesto = libres.stream().filter(r -> r.clave().categoria() == PUESTO).findFirst();
            if (tecnico.isEmpty()) motivos.add("Sin técnico disponible");
            if (puesto.isEmpty()) motivos.add("Sin puesto disponible");
            var herramientas = new EnumMap<TipoHerramienta, Recurso.Clave>(TipoHerramienta.class);
            for (var tipo : TipoHerramienta.values()) if (orden.requisitos().contains(tipo)) {
                var unidad = libres.stream().filter(r -> r.tipo() == tipo).findFirst();
                if (unidad.isEmpty()) motivos.add("Sin unidad disponible de " + tipo);
                else herramientas.put(tipo, unidad.get().clave());
            }
            if (motivos.isEmpty()) {
                if (primera == null) primera = new PropuestaInicio(entrada.contexto(), orden.id(),
                        new SeleccionRecursos(tecnico.orElseThrow().clave(), puesto.orElseThrow().clave(), herramientas));
                else motivos.add("Bloqueada por primera factible " + primera.ordenId());
            }
            causas.put(orden.id(), motivos);
        }
        return new Resultado(Optional.ofNullable(primera), causas);
    }
    /** Predicado de negocio para CA-10/19; H2 deberá ejecutarlo dentro de su transacción.
     * Comprueba unidades exactas, sin exigir que sigan siendo las mínimas.
     */
    public void validarPropuesta(InstantaneaDominio entrada, PropuestaInicio propuesta) {
        if (!entrada.contexto().equals(propuesta.contexto())) throw new IllegalArgumentException("Contexto ajeno");
        var primera = proponer(entrada).propuesta().orElseThrow(() -> new IllegalArgumentException("Sin orden factible"));
        if (!primera.ordenId().equals(propuesta.ordenId())) throw new IllegalArgumentException("No es primera factible");
        var orden = entrada.ordenes().stream().filter(o -> o.id().equals(propuesta.ordenId())).findFirst().orElseThrow();
        var inventario = new HashMap<Recurso.Clave, Recurso>();
        entrada.recursos().forEach(r -> inventario.put(r.clave(), r));
        InstantaneaDominio.validarSeleccion(orden, propuesta.recursos(), inventario);
        if (propuesta.recursos().unidades().stream().anyMatch(c -> entrada.disponibilidad(c) != DISPONIBLE))
            throw new IllegalArgumentException("Unidad exacta ocupada");
    }
}
