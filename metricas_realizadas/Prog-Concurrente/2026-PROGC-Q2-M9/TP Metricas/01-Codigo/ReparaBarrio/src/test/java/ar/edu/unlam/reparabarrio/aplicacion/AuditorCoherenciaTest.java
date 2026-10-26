package ar.edu.unlam.reparabarrio.aplicacion;

import ar.edu.unlam.reparabarrio.aplicacion.puertos.RepositorioRecuperacion.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import org.junit.jupiter.api.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Verificaciones puras adicionales: no sustituyen ninguna prueba H2 de archivo. */
class AuditorCoherenciaTest {
    private final String base = UUID.randomUUID().toString(), sesion = UUID.randomUUID().toString(), id = UUID.randomUUID().toString();
    private Datos datos(List<Intento> intentos, long antiguedad) { return new Datos(new Metadata(base, 0, 0, false), List.of(new Sesion(sesion, true)), intentos, Map.of(), antiguedad, List.of()); }
    @Test void descriptorExactoRoundTripSinColisionesNiSerializacionJava() {
        var clave = new Recurso.Clave(Recurso.Categoria.HERRAMIENTA, "M|.=á");
        var d = new DescriptorOperacion("REGISTRO_ORDEN", "A", "descripción\ncon puntos . y |", Set.of(TipoHerramienta.MULTIMETRO, TipoHerramienta.TALADRO), null, null, null, Set.of(clave));
        assertEquals(d, DescriptorOperacion.decodificar(d.codificar()));
        assertThrows(IllegalArgumentException.class, () -> DescriptorOperacion.decodificar("INVALIDO"));
        assertNotEquals(d.codificar(), new DescriptorOperacion(d.accion(), d.entidad(), "distinta", d.requisitos(), null, null, null, d.recursos()).codificar());
    }
    @Test void intencionSinTestigoSoloAusenciaConRevisionBaseExacta() {
        var s = new InstantaneaDominio(base, List.of(), List.of(), Map.of());
        var d = new DescriptorOperacion("REGISTRO_ORDEN", "A", "x", Set.of(), null, null, null, Set.of());
        var i = new Intento(id, base, sesion, "NEGOCIO", d, 0, "PENDIENTE", null, null);
        assertTrue(new AuditorCoherencia().auditar(s, List.of(), 0, datos(List.of(i), 0), true).isEmpty());
        var mal = new Intento(id, base, sesion, "NEGOCIO", d, 1, "PENDIENTE", null, null);
        assertFalse(new AuditorCoherencia().auditar(s, List.of(), 0, datos(List.of(mal), 0), true).isEmpty());
    }
    @Test void igualdadLiteralITestigoResolucionDescriptorYCadena() {
        var o = Orden.pendiente("A", "x", 1, List.of()); var s = new InstantaneaDominio(base, List.of(o), List.of(), Map.of());
        var d = new DescriptorOperacion("REGISTRO_ORDEN", "A", "x", Set.of(), null, null, null, Set.of());
        var i = new Intento(id, base, sesion, "NEGOCIO", d, 0, "CONFIRMADA", 1L, null);
        var e = new EventoHistorial(1, id, "A", "REGISTRO_ORDEN", Instant.EPOCH, Set.of());
        assertTrue(new AuditorCoherencia().auditar(s, List.of(e), 1, datos(List.of(i), 1), true).isEmpty());
        var ajeno = new EventoHistorial(1, UUID.randomUUID().toString(), "A", "REGISTRO_ORDEN", Instant.EPOCH, Set.of());
        assertFalse(new AuditorCoherencia().auditar(s, List.of(ajeno), 1, datos(List.of(i), 1), true).isEmpty());
        assertFalse(new AuditorCoherencia().auditar(s, List.of(), 0, datos(List.of(i), 1), true).isEmpty());
    }
}
