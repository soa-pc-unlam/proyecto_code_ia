package ar.edu.unlam.reparabarrio.dominio;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ar.edu.unlam.reparabarrio.dominio.Recurso.Categoria.*;

class DominioTest {
    static Orden orden(String id, long secuencia, EstadoOrden estado, TipoHerramienta... tipos) {
        return new Orden(id, "Reparación", secuencia, Set.of(tipos), estado);
    }
    static Recurso recurso(Recurso.Categoria categoria, String id, TipoHerramienta tipo, boolean habilitado) {
        return new Recurso(new Recurso.Clave(categoria, id), tipo, habilitado);
    }
    @ParameterizedTest @EnumSource(EstadoOrden.class)
    void matrizCompletaDeTransiciones(EstadoOrden origen) {
        var original = orden("O", 1, origen);
        for (var destino : EstadoOrden.values()) {
            boolean permitida = origen == EstadoOrden.PENDIENTE && (destino == EstadoOrden.EN_CURSO || destino == EstadoOrden.CANCELADA)
                    || origen == EstadoOrden.EN_CURSO && (destino == EstadoOrden.FINALIZADA || destino == EstadoOrden.FALLIDA);
            if (permitida) assertEquals(destino, original.transicionar(destino).estado());
            else assertThrows(IllegalArgumentException.class, () -> original.transicionar(destino));
            assertEquals(origen, original.estado());
        }
    }
    @Test void requisitosYCatalogo() {
        assertEquals(Set.of(TipoHerramienta.MULTIMETRO, TipoHerramienta.SOLDADOR, TipoHerramienta.TALADRO), Set.of(TipoHerramienta.values()));
        assertThrows(IllegalArgumentException.class, () -> TipoHerramienta.valueOf("DESCONOCIDO"));
        assertTrue(Orden.pendiente("O", "Sin especiales", 1, List.of()).requisitos().isEmpty());
        var entrada = new ArrayList<>(List.of(TipoHerramienta.TALADRO, TipoHerramienta.SOLDADOR));
        var o = Orden.pendiente("O", "Dos tipos", 1, entrada); entrada.clear();
        assertEquals(2, o.requisitos().size());
        assertThrows(UnsupportedOperationException.class, () -> o.requisitos().clear());
        assertThrows(IllegalArgumentException.class, () -> Orden.pendiente("O", "x", 1, List.of(TipoHerramienta.TALADRO, TipoHerramienta.TALADRO)));
        assertThrows(NullPointerException.class, () -> Orden.pendiente("O", "x", 1, Arrays.asList((TipoHerramienta) null)));
        assertThrows(IllegalArgumentException.class, () -> Orden.pendiente("O", " ", 1, List.of()));
        assertThrows(IllegalArgumentException.class, () -> Orden.pendiente("O", "x", 0, List.of()));
    }
    @Test void identidadExactaSinNormalizacion() {
        for (String id : new String[]{"", " ", " T2", "T2 ", "\tT2", "T2\n"})
            assertThrows(IllegalArgumentException.class, () -> Identidad.validar(id));
        assertThrows(IllegalArgumentException.class, () -> Identidad.validar(null));
        assertNotEquals(new Recurso.Clave(TECNICO, "T2"), new Recurso.Clave(TECNICO, "t2"));
        assertEquals("ID libre", Identidad.validar("ID libre"));
        assertThrows(IllegalArgumentException.class, () -> recurso(HERRAMIENTA, "M", null, true));
        assertThrows(IllegalArgumentException.class, () -> recurso(TECNICO, "T", TipoHerramienta.TALADRO, true));
    }
    @Test void instantaneaRechazaDuplicadosSinConfundirCategorias() {
        var t = recurso(TECNICO, "X", null, true); var p = recurso(PUESTO, "X", null, true);
        assertDoesNotThrow(() -> new InstantaneaDominio("C", List.of(), List.of(t, p), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new InstantaneaDominio("C", List.of(), List.of(t, t), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new InstantaneaDominio("C", List.of(orden("O", 1, EstadoOrden.PENDIENTE), orden("O", 2, EstadoOrden.FINALIZADA)), List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new InstantaneaDominio("C", List.of(orden("A", 1, EstadoOrden.PENDIENTE), orden("B", 1, EstadoOrden.PENDIENTE)), List.of(), Map.of()));
    }
    @Test void invariantesDeAsignacionYDisponibilidadDerivada() {
        var t = recurso(TECNICO, "T", null, true); var p = recurso(PUESTO, "P", null, true);
        var h = recurso(HERRAMIENTA, "H", TipoHerramienta.TALADRO, true);
        var fuera = recurso(TECNICO, "F", null, false);
        var a = new SeleccionRecursos(t.clave(), p.clave(), Map.of(TipoHerramienta.TALADRO, h.clave()));
        var curso = orden("O", 1, EstadoOrden.EN_CURSO, TipoHerramienta.TALADRO);
        var s = new InstantaneaDominio("C", List.of(curso), List.of(t, p, h, fuera), Map.of("O", a));
        assertEquals(Recurso.Disponibilidad.OCUPADO, s.disponibilidad(h.clave()));
        assertEquals(Recurso.Disponibilidad.FUERA_DE_SERVICIO, s.disponibilidad(fuera.clave()));
        assertThrows(IllegalArgumentException.class, () -> new InstantaneaDominio("C", List.of(curso), List.of(t, p, h), Map.of()));
        for (var estado : List.of(EstadoOrden.PENDIENTE, EstadoOrden.FINALIZADA, EstadoOrden.FALLIDA, EstadoOrden.CANCELADA))
            assertThrows(IllegalArgumentException.class, () -> new InstantaneaDominio("C", List.of(orden("O", 1, estado, TipoHerramienta.TALADRO)), List.of(t, p, h), Map.of("O", a)));
        assertThrows(IllegalArgumentException.class, () -> new InstantaneaDominio("C", List.of(curso, orden("B", 2, EstadoOrden.EN_CURSO, TipoHerramienta.TALADRO)), List.of(t, p, h), Map.of("O", a, "B", a)));
        assertThrows(IllegalArgumentException.class, () -> new InstantaneaDominio("C", List.of(curso), List.of(t, p), Map.of("O", a)));
        assertThrows(IllegalArgumentException.class, () -> new InstantaneaDominio("C", List.of(curso), List.of(t, p, recurso(HERRAMIENTA, "H", TipoHerramienta.SOLDADOR, true)), Map.of("O", a)));
        assertThrows(IllegalArgumentException.class, () -> new InstantaneaDominio("C", List.of(curso), List.of(t, p, recurso(HERRAMIENTA, "H", TipoHerramienta.TALADRO, false)), Map.of("O", a)));
        assertThrows(IllegalArgumentException.class, () -> new InstantaneaDominio("C", List.of(orden("O", 1, EstadoOrden.EN_CURSO)), List.of(t, p, h), Map.of("O", a)));
        var sinHerramientas = new SeleccionRecursos(t.clave(), p.clave(), Map.of());
        assertThrows(IllegalArgumentException.class, () -> new InstantaneaDominio("C", List.of(curso), List.of(t, p, h), Map.of("O", sinHerramientas)));
        assertThrows(IllegalArgumentException.class, () -> new InstantaneaDominio("C", List.of(), List.of(t, p, h), Map.of("Fantasma", a)));
    }
    @Test void seleccionRechazaCategoriasYDuplicacionFisica() {
        var t = new Recurso.Clave(TECNICO, "T"); var p = new Recurso.Clave(PUESTO, "P"); var h = new Recurso.Clave(HERRAMIENTA, "H");
        assertThrows(IllegalArgumentException.class, () -> new SeleccionRecursos(p, t, Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new SeleccionRecursos(t, p, Map.of(TipoHerramienta.TALADRO, t)));
        assertThrows(IllegalArgumentException.class, () -> new SeleccionRecursos(t, p, Map.of(TipoHerramienta.TALADRO, h, TipoHerramienta.SOLDADOR, h)));
    }
}
