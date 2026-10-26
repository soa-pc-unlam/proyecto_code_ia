package ar.edu.unlam.reparabarrio.planificacion;

import ar.edu.unlam.reparabarrio.dominio.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ar.edu.unlam.reparabarrio.dominio.Recurso.Categoria.*;
import static ar.edu.unlam.reparabarrio.dominio.TipoHerramienta.*;

class PlanificadorTest {
    private final Planificador planificador = new Planificador();
    private Recurso r(Recurso.Categoria c, String id, TipoHerramienta tipo, boolean habilitado) {
        return new Recurso(new Recurso.Clave(c, id), tipo, habilitado);
    }
    private Orden o(String id, long secuencia, TipoHerramienta... tipos) {
        return Orden.pendiente(id, "Reparar", secuencia, List.of(tipos));
    }
    private List<Recurso> basicos() {
        return List.of(r(TECNICO, "T10", null, true), r(TECNICO, "T2", null, true), r(TECNICO, "T02", null, true),
                r(TECNICO, "AUX", null, false), r(PUESTO, "P10", null, true), r(PUESTO, "P2", null, true),
                r(HERRAMIENTA, "M10", MULTIMETRO, true), r(HERRAMIENTA, "M2", MULTIMETRO, true));
    }
    @Test void primeraFactiblePorSecuenciaSinMutacionNiReservas() {
        var ordenes = new ArrayList<>(List.of(o("C", 3), o("B", 2, MULTIMETRO), o("A", 1, TALADRO)));
        var recursos = new ArrayList<>(basicos());
        var s = new InstantaneaDominio("C", ordenes, recursos, Map.of());
        var resultado = planificador.proponer(s); var propuesta = resultado.propuesta().orElseThrow();
        assertEquals("B", propuesta.ordenId());
        assertEquals("T02", propuesta.recursos().tecnico().id());
        assertEquals("P2", propuesta.recursos().puesto().id());
        assertEquals("M2", propuesta.recursos().herramientas().get(MULTIMETRO).id());
        assertEquals(List.of("Sin unidad disponible de TALADRO"), resultado.causas().get("A"));
        assertEquals(List.of("Bloqueada por primera factible B"), resultado.causas().get("C"));
        assertEquals(resultado, planificador.proponer(s));
        assertTrue(s.asignaciones().isEmpty());
        assertTrue(s.ordenes().stream().allMatch(o -> o.estado() == EstadoOrden.PENDIENTE));
        propuesta.recursos().unidades().forEach(c -> assertEquals(Recurso.Disponibilidad.DISPONIBLE, s.disponibilidad(c)));
        ordenes.clear(); recursos.clear();
        assertEquals(resultado, planificador.proponer(s));
        assertThrows(UnsupportedOperationException.class, () -> propuesta.recursos().herramientas().clear());
        assertThrows(UnsupportedOperationException.class, () -> resultado.causas().get("A").clear());
        assertThrows(UnsupportedOperationException.class, () -> s.ordenes().clear());
    }
    @Test void ceroHerramientasYTiposDistintosExactos() {
        var s = new InstantaneaDominio("C", List.of(o("A", 1)), basicos(), Map.of());
        assertTrue(planificador.proponer(s).propuesta().orElseThrow().recursos().herramientas().isEmpty());
        var inventario = new ArrayList<>(basicos()); inventario.add(r(HERRAMIENTA, "S1", SOLDADOR, true));
        var ambos = new InstantaneaDominio("C", List.of(o("B", 1, MULTIMETRO, SOLDADOR)), inventario, Map.of());
        assertEquals(Set.of(MULTIMETRO, SOLDADOR), planificador.proponer(ambos).propuesta().orElseThrow().recursos().herramientas().keySet());
    }
    @Test void ausenciaConCausasYExclusionDeOcupados() {
        var vacia = new InstantaneaDominio("C", List.of(o("A", 1, SOLDADOR)), List.of(), Map.of());
        assertTrue(planificador.proponer(vacia).propuesta().isEmpty());
        assertEquals(3, planificador.proponer(vacia).causas().get("A").size());
        var inicial = new InstantaneaDominio("C", List.of(o("A", 1, MULTIMETRO)), basicos(), Map.of());
        var a = planificador.proponer(inicial).propuesta().orElseThrow().recursos();
        var ocupada = new InstantaneaDominio("C", List.of(o("A", 1, MULTIMETRO).transicionar(EstadoOrden.EN_CURSO), o("B", 2, MULTIMETRO)), basicos(), Map.of("A", a));
        var b = planificador.proponer(ocupada).propuesta().orElseThrow();
        assertEquals("B", b.ordenId());
        assertEquals("T2", b.recursos().tecnico().id());
        assertEquals("P10", b.recursos().puesto().id());
        assertEquals("M10", b.recursos().herramientas().get(MULTIMETRO).id());
        assertTrue(planificador.proponer(new InstantaneaDominio("C", List.of(), basicos(), Map.of())).propuesta().isEmpty());
    }
    @Test void validaExactasAunqueAparezcaOtraMenorYRechazaObsolescencia() {
        var s = new InstantaneaDominio("C", List.of(o("A", 1, TALADRO), o("B", 2, MULTIMETRO)), basicos(), Map.of());
        var propuesta = planificador.proponer(s).propuesta().orElseThrow();
        assertDoesNotThrow(() -> planificador.validarPropuesta(s, propuesta));
        var adicional = new ArrayList<>(basicos().stream().map(r -> r.clave().id().equals("AUX")
                ? new Recurso(r.clave(), r.tipo(), true) : r).toList());
        var nueva = new InstantaneaDominio("C", s.ordenes(), adicional, Map.of());
        assertNotEquals(propuesta, planificador.proponer(nueva).propuesta().orElseThrow());
        assertDoesNotThrow(() -> planificador.validarPropuesta(nueva, propuesta));
        adicional.add(r(HERRAMIENTA, "D1", TALADRO, true));
        var anteriorFactible = new InstantaneaDominio("C", s.ordenes(), adicional, Map.of());
        assertThrows(IllegalArgumentException.class, () -> planificador.validarPropuesta(anteriorFactible, propuesta));
        var deshabilitados = basicos().stream().map(r -> r.clave().equals(propuesta.recursos().tecnico()) ? new Recurso(r.clave(), r.tipo(), false) : r).toList();
        assertThrows(IllegalArgumentException.class, () -> planificador.validarPropuesta(new InstantaneaDominio("C", s.ordenes(), deshabilitados, Map.of()), propuesta));
        assertThrows(IllegalArgumentException.class, () -> planificador.validarPropuesta(new InstantaneaDominio("Otro", s.ordenes(), basicos(), Map.of()), propuesta));
        var canceladas = List.of(o("A", 1, TALADRO), o("B", 2, MULTIMETRO).transicionar(EstadoOrden.CANCELADA));
        assertThrows(IllegalArgumentException.class, () -> planificador.validarPropuesta(new InstantaneaDominio("C", canceladas, basicos(), Map.of()), propuesta));
    }
    @Test void rechazaSalteoExtrasYExactasOcupadas() {
        var s = new InstantaneaDominio("C", List.of(o("A", 1), o("B", 2)), basicos(), Map.of());
        var propuesta = planificador.proponer(s).propuesta().orElseThrow();
        assertThrows(IllegalArgumentException.class, () -> planificador.validarPropuesta(s, new PropuestaInicio("C", "B", propuesta.recursos())));
        var extra = new SeleccionRecursos(propuesta.recursos().tecnico(), propuesta.recursos().puesto(), Map.of(MULTIMETRO, new Recurso.Clave(HERRAMIENTA, "M2")));
        assertThrows(IllegalArgumentException.class, () -> planificador.validarPropuesta(s, new PropuestaInicio("C", "A", extra)));
        var ocupada = new InstantaneaDominio("C", List.of(o("A", 1), o("Z", 2).transicionar(EstadoOrden.EN_CURSO)), basicos(), Map.of("Z", propuesta.recursos()));
        assertThrows(IllegalArgumentException.class, () -> planificador.validarPropuesta(ocupada, propuesta));
    }
}
