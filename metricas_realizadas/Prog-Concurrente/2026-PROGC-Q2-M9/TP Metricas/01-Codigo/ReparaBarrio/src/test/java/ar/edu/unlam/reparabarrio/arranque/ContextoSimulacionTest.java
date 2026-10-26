package ar.edu.unlam.reparabarrio.arranque;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ar.edu.unlam.reparabarrio.aplicacion.ResultadoOperacion.Estado.*;
import static ar.edu.unlam.reparabarrio.dominio.Recurso.Categoria.*;

/** Puerta temprana H6-D01/D02. Sólo memorias nuevas y archivo H5 de fixture nuevo. */
@Timeout(60)
class ContextoSimulacionTest {
    private final Path raiz = Path.of("").toAbsolutePath().normalize();
    private static <T> T confirmado(ResultadoOperacion<T> r) {
        assertEquals(CONFIRMADA, r.estado(), r::toString); return r.valor();
    }
    private static void sana(ContextoTaller c) {
        assertEquals(ContextoTaller.Ciclo.HABILITADO, c.ciclo(), () -> c.informe().toString());
        assertTrue(c.informe().hallazgos().isEmpty(), () -> c.informe().toString());
    }
    @Test void consentimientoYRaizAntesDeCrear() {
        assertThrows(IllegalArgumentException.class, () -> FabricaContextos.crearSimulacion(raiz, false));
        assertThrows(IllegalArgumentException.class, () -> FabricaContextos.crearSimulacion(Path.of("."), true));
    }
    @Test void aperturaCatalogoV2OperacionesYHechosConfirmados() throws Exception {
        try (var c = FabricaContextos.crearSimulacion(raiz, true)) {
            sana(c);
            var s = c.servicio();
            assertEquals(Set.of(TipoHerramienta.values()), confirmado(s.consultar()).catalogo());
            confirmado(s.registrarRecurso(TECNICO, "T1", null, true));
            confirmado(s.registrarRecurso(PUESTO, "P1", null, true));
            confirmado(s.registrarRecurso(HERRAMIENTA, "M1", TipoHerramienta.MULTIMETRO, true));
            confirmado(s.registrarOrden("O1", "Fixture H6", Set.of(TipoHerramienta.MULTIMETRO)));
            var propuesta = confirmado(s.consultar()).planificacion().propuesta().orElseThrow();
            confirmado(s.confirmarInicio(propuesta));
            assertEquals(1, confirmado(s.consultar()).dominio().asignaciones().size());
            assertEquals(RECHAZADA, s.confirmarInicio(propuesta).estado());
            confirmado(s.finalizar("O1"));
            var captura = confirmado(s.consultarHechos());
            assertNull(captura.incompleta());
            assertEquals(6, captura.hechos().size());
            assertEquals(captura.taller().historial(), captura.hechos().stream().map(HechosConfirmados.Hecho::evento).toList());
            for (int i = 0; i < captura.hechos().size(); i++) {
                var h = captura.hechos().get(i);
                assertEquals(c.identidad(), h.contexto());
                assertEquals(i + 1L, h.revision());
                assertEquals(h.evento().recursos(), h.descriptor().recursos());
                if (i > 0) assertTrue(h.nanos() - captura.hechos().get(i-1).nanos() >= 0);
            }
            assertTrue(captura.taller().dominio().asignaciones().isEmpty());
        }
    }
    @Test void dosMemoriasIdentidadesYPropuestasAjenas() throws Exception {
        try (var a = FabricaContextos.crearSimulacion(raiz, true);
             var b = FabricaContextos.crearSimulacion(raiz, true)) {
            sana(a); sana(b); assertNotEquals(a.identidad(), b.identidad());
            for (var c : List.of(a,b)) {
                confirmado(c.servicio().registrarRecurso(TECNICO, "T1", null, true));
                confirmado(c.servicio().registrarRecurso(PUESTO, "P1", null, true));
                confirmado(c.servicio().registrarOrden("O1", "Mismos IDs", Set.of()));
            }
            var antes = confirmado(b.servicio().consultar());
            var p = confirmado(a.servicio().consultar()).planificacion().propuesta().orElseThrow();
            assertEquals(RECHAZADA, b.servicio().confirmarInicio(p).estado());
            assertEquals(antes, confirmado(b.servicio().consultar()));
            confirmado(a.servicio().confirmarInicio(p));
            confirmado(a.servicio().finalizar("O1"));
            assertEquals(antes, confirmado(b.servicio().consultar()));
        }
    }
    @Test void aislamientoRespectoArchivoNuevoDeFixture() throws Exception {
        // Entrada académica de archivo NUEVO: no se abre datos/operativo del proyecto.
        try (var operativo = FabricaContextos.crearAcademico(raiz, "h5-h6-fixture-" + UUID.randomUUID(), true)) {
            sana(operativo);
            confirmado(operativo.servicio().registrarOrden("O1", "Archivo de fixture", Set.of()));
            var antes = confirmado(operativo.servicio().consultar());
            try (var sim = FabricaContextos.crearSimulacion(raiz, true)) {
                sana(sim);
                confirmado(sim.servicio().registrarOrden("O1", "Memoria", Set.of()));
                assertNotEquals(operativo.identidad(), sim.identidad());
                assertEquals(antes, confirmado(operativo.servicio().consultar()));
            }
            assertEquals(antes, confirmado(operativo.servicio().consultar()));
        }
    }
    @Test void guardiaCapacidadAnclaYCierreSinArchivoNegocio() throws Exception {
        Path d = Files.createDirectory(raiz.resolve("target/h6-" + UUID.randomUUID()));
        var g = GuardiaInstancia.adquirir(d);
        var permiso = ar.edu.unlam.reparabarrio.persistencia.AutorizacionSimulacion.nueva(g, true);
        var almacen = ar.edu.unlam.reparabarrio.persistencia.PersistenciaH2.prepararSimulacion(g, permiso);
        try (var c = ContextoTaller.componer(almacen, cerrado -> assertFalse(g.vigente()))) {
            sana(c);
            assertThrows(java.io.IOException.class, () -> GuardiaInstancia.adquirir(d));
            assertThrows(IllegalStateException.class, () -> almacen.abrirUnidad(null));
            assertThrows(IllegalStateException.class, () -> almacen.reclamar(null));
            assertThrows(IllegalStateException.class, () -> ar.edu.unlam.reparabarrio.persistencia.PersistenciaH2.prepararSimulacion(g, permiso));
            confirmado(c.servicio().registrarOrden("O1", "Ancla mantiene datos entre conexiones", Set.of()));
            assertEquals(1, confirmado(c.servicio().consultar()).dominio().ordenes().size());
            c.close();
            assertEquals(ContextoTaller.Ciclo.CERRADO, c.ciclo());
            assertEquals(ERROR_TECNICO, c.servicio().consultar().estado());
        } // Si el contexto no acredita cierre, el test no libera manualmente su guardia.
        assertFalse(g.vigente());
        try (var entradas = Files.list(d)) {
            assertFalse(entradas.anyMatch(p -> p.getFileName().toString().endsWith(".mv.db")));
        }
        try (var nueva = GuardiaInstancia.adquirir(d)) {
            assertTrue(nueva.vigente());
            assertThrows(IllegalArgumentException.class, () -> ar.edu.unlam.reparabarrio.persistencia.AutorizacionSimulacion.nueva(nueva, true));
        }
    }
}
