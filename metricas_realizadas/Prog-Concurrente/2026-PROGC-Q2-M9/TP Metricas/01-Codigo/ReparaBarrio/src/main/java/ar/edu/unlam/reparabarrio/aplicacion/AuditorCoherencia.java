package ar.edu.unlam.reparabarrio.aplicacion;

import ar.edu.unlam.reparabarrio.aplicacion.puertos.RepositorioRecuperacion.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import ar.edu.unlam.reparabarrio.planificacion.Planificador;
import java.util.*;

/** Auditor puro: reconstruye la cadena completa; no corrige filas ni decide por timestamps. */
public final class AuditorCoherencia {
    private static void exigir(boolean condicion, String mensaje) {
        if (!condicion) throw new IllegalStateException(mensaje);
    }
    public List<String> auditar(InstantaneaDominio actual, List<EventoHistorial> eventos, long revision, Datos datos, boolean h4) {
        var errores = new ArrayList<>(datos.hallazgos());
        try {
            exigir(datos.antiguedad() == actual.ordenes().stream().mapToLong(Orden::secuencia).max().orElse(0), "Contador antigüedad");
            exigir(actual.ordenes().size() == datos.antiguedad(), "Órdenes/secuencias eliminadas");
            exigir(revision == eventos.size(), "Contador revisión/historial omitido");
            exigir(datos.metadata().fronteraV1() >= 0 && datos.metadata().fronteraV1() <= revision, "Frontera heredada incompatible");
            var finales = new HashMap<String, Orden>(); actual.ordenes().forEach(o -> finales.put(o.id(), o));
            var inventarioFinal = new HashMap<Recurso.Clave, Recurso>(); actual.recursos().forEach(r -> inventarioFinal.put(r.clave(), r));
            var ordenes = new LinkedHashMap<String, Orden>(); var recursos = new HashMap<Recurso.Clave, Recurso>();
            var asignaciones = new HashMap<String, SeleccionRecursos>(); var ids = new HashSet<String>();
            var intentos = new HashMap<String, Intento>(); var sesiones = new HashSet<String>();
            datos.sesiones().forEach(s -> sesiones.add(s.id()));
            for (var i : datos.intentos()) {
                UUID.fromString(i.id());
                exigir(intentos.put(i.id(), i) == null, "Intención duplicada");
                exigir(i.baseId().equals(datos.metadata().baseId()) && sesiones.contains(i.sesionId()), "Intención ajena/huérfana");
                exigir(i.revisionBase() <= revision, "Revisión base futura");
            }
            long secuencia = 0, antiguedad = 0;
            for (var e : eventos) {
                UUID.fromString(e.operacionId());
                exigir(e.secuencia() == ++secuencia && ids.add(e.operacionId()), "Secuencia/ID historial");
                Intento i = intentos.get(e.operacionId());
                if (h4 && e.secuencia() > datos.metadata().fronteraV1()) {
                    exigir(i != null && i.clase().equals("NEGOCIO"), "Evento H4 sin intención");
                    exigir(i.resolucion().equals("CONFIRMADA") && Objects.equals(i.evento(), e.secuencia()), "Testigo/resolución incompatible");
                    exigir(i.revisionBase() == e.secuencia() - 1, "Revisión base/testigo");
                    var d = i.descriptor();
                    if (d.categoria() == null) {
                        exigir(d.tipo() == null && d.habilitado() == null, "Descriptor orden con atributos de recurso");
                        if (!d.accion().equals("REGISTRO_ORDEN")) exigir(d.descripcion() == null && d.requisitos().isEmpty(), "Descriptor transición con atributos de alta");
                    } else exigir(d.descripcion() == null && d.requisitos().isEmpty(), "Descriptor recurso con atributos de orden");
                    exigir(d.accion().equals(e.accion()), "Acción descriptor/testigo");
                    exigir(Objects.equals(d.categoria() == null ? d.entidad() : null, e.ordenId()), "Entidad descriptor/testigo");
                    exigir(d.recursos().equals(e.recursos()), "Recursos descriptor/testigo");
                }
                switch (e.accion()) {
                    case "REGISTRO_ORDEN" -> {
                        Orden finalO = finales.get(e.ordenId()); exigir(finalO != null && !ordenes.containsKey(finalO.id()) && e.recursos().isEmpty(), "Registro orden incompatible");
                        exigir(finalO.secuencia() == ++antiguedad, "Secuencia registro");
                        if (i != null) exigir(Objects.equals(i.descriptor().descripcion(), finalO.descripcion()) && i.descriptor().requisitos().equals(finalO.requisitos()), "Alta orden/descriptor");
                        ordenes.put(finalO.id(), Orden.pendiente(finalO.id(), finalO.descripcion(), finalO.secuencia(), finalO.requisitos()));
                    }
                    case "REGISTRO_RECURSO" -> {
                        exigir(e.ordenId() == null && e.recursos().size() == 1, "Alta recurso incompatible");
                        Recurso.Clave clave = e.recursos().iterator().next(); Recurso fin = inventarioFinal.get(clave);
                        exigir(fin != null && !recursos.containsKey(clave), "Recurso omitido/duplicado");
                        boolean inicial = fin.habilitado();
                        for (var futuro : eventos) if (futuro.secuencia() > e.secuencia() && futuro.recursos().contains(clave)
                                && Set.of("HABILITACION", "INHABILITACION").contains(futuro.accion())) { inicial = futuro.accion().equals("INHABILITACION"); break; }
                        if (i != null) {
                            var d = i.descriptor(); exigir(d.categoria() == clave.categoria() && Objects.equals(d.entidad(), clave.id())
                                    && d.tipo() == fin.tipo() && Objects.equals(d.habilitado(), inicial), "Alta recurso/descriptor");
                        }
                        recursos.put(clave, new Recurso(clave, fin.tipo(), inicial));
                    }
                    case "HABILITACION", "INHABILITACION" -> {
                        exigir(e.ordenId() == null && e.recursos().size() == 1, "Habilitación incompatible");
                        Recurso.Clave clave = e.recursos().iterator().next(); Recurso anterior = recursos.get(clave);
                        boolean hab = e.accion().equals("HABILITACION");
                        exigir(anterior != null && anterior.habilitado() != hab && asignaciones.values().stream().noneMatch(a -> a.unidades().contains(clave)), "Cambio sobre ocupado/sin cambio");
                        if (i != null) exigir(Objects.equals(i.descriptor().entidad(), clave.id()) && i.descriptor().categoria() == clave.categoria()
                                && Objects.equals(i.descriptor().habilitado(), hab), "Destino habilitación incompatible");
                        recursos.put(clave, new Recurso(clave, anterior.tipo(), hab));
                    }
                    case "INICIO" -> {
                        Orden o = ordenes.get(e.ordenId()); exigir(o != null && o.estado() == EstadoOrden.PENDIENTE, "Inicio sin pendiente");
                        var vista = new InstantaneaDominio(actual.contexto(), new ArrayList<>(ordenes.values()), new ArrayList<>(recursos.values()), asignaciones);
                        var seleccion = seleccion(e.recursos(), recursos);
                        new Planificador().validarPropuesta(vista, new PropuestaInicio(actual.contexto(), o.id(), seleccion));
                        ordenes.put(o.id(), o.transicionar(EstadoOrden.EN_CURSO)); asignaciones.put(o.id(), seleccion);
                    }
                    case "FINALIZADA", "FALLIDA", "CANCELADA" -> {
                        Orden o = ordenes.get(e.ordenId()); exigir(o != null, "Cierre sin registro");
                        var activa = asignaciones.get(o.id());
                        exigir(e.recursos().equals(activa == null ? Set.of() : activa.unidades()), "Liberación incompatible");
                        ordenes.put(o.id(), o.transicionar(EstadoOrden.valueOf(e.accion()))); asignaciones.remove(o.id());
                    }
                    default -> throw new IllegalStateException("Acción desconocida " + e.accion());
                }
            }
            exigir(finales.equals(ordenes) && inventarioFinal.equals(recursos) && actual.asignaciones().equals(asignaciones), "Cadena/estado/asignaciones actuales contradictorios");
            for (var i : datos.intentos()) {
                var testigos = eventos.stream().filter(e -> e.operacionId().equals(i.id())).toList();
                if (i.resolucion().equals("CONFIRMADA")) exigir(testigos.size() == 1 && Objects.equals(i.evento(), testigos.getFirst().secuencia()), "Confirmación sin testigo exacto");
                else {
                    exigir(testigos.isEmpty() && i.evento() == null, "Resolución sin efecto con evento");
                    if (i.resolucion().equals("PENDIENTE") && i.clase().equals("NEGOCIO")) exigir(i.revisionBase() == revision, "Escrituras posteriores a intención pendiente");
                }
            }
            for (var r : datos.revisiones().values()) {
                Orden o = finales.get(r.ordenId()); exigir(o != null && sesiones.contains(r.sesionId()), "Revisión huérfana");
                if (r.cierreId() == null) exigir(o.estado() == EstadoOrden.EN_CURSO, "Revisión pendiente incompatible");
                else {
                    var cierre = eventos.stream().filter(e -> e.operacionId().equals(r.cierreId())).findFirst().orElseThrow();
                    exigir(Objects.equals(cierre.ordenId(), o.id()) && Set.of("FINALIZADA", "FALLIDA").contains(cierre.accion()), "Revisión resuelta sin cierre");
                }
            }
        } catch (Exception e) { errores.add("INCONSISTENCIA: " + e); }
        return List.copyOf(errores);
    }
    private static SeleccionRecursos seleccion(Set<Recurso.Clave> unidades, Map<Recurso.Clave, Recurso> inventario) {
        var tecnicos = unidades.stream().filter(c -> c.categoria() == Recurso.Categoria.TECNICO).toList();
        var puestos = unidades.stream().filter(c -> c.categoria() == Recurso.Categoria.PUESTO).toList();
        exigir(tecnicos.size() == 1 && puestos.size() == 1, "Inicio incompleto");
        var herramientas = new EnumMap<TipoHerramienta, Recurso.Clave>(TipoHerramienta.class);
        for (var c : unidades) if (c.categoria() == Recurso.Categoria.HERRAMIENTA) {
            Recurso r = inventario.get(c); exigir(r != null && herramientas.put(r.tipo(), c) == null, "Herramienta inexistente/duplicada");
        }
        return new SeleccionRecursos(tecnicos.getFirst(), puestos.getFirst(), herramientas);
    }
}
