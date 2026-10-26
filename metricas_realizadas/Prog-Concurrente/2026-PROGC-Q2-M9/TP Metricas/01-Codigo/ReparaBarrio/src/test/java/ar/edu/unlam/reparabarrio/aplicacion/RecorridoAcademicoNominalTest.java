package ar.edu.unlam.reparabarrio.aplicacion;

import ar.edu.unlam.reparabarrio.arranque.GuardiaInstancia;
import ar.edu.unlam.reparabarrio.dominio.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import static org.junit.jupiter.api.Assertions.*;
import static ar.edu.unlam.reparabarrio.aplicacion.ResultadoOperacion.Estado.*;
import static ar.edu.unlam.reparabarrio.dominio.Recurso.Categoria.*;
import static ar.edu.unlam.reparabarrio.dominio.Recurso.Disponibilidad.*;
import static ar.edu.unlam.reparabarrio.dominio.TipoHerramienta.*;

/** P03-H4-verificacion-academica: una sesión nueva, sin reapertura ni caída.
 * Solicitudes en dos hilos; transacciones serializadas por el coordinador real.
 * Dos EN_CURSO acreditan asignaciones simultáneas, no duración física ni H6.
 */
class RecorridoAcademicoNominalTest {
    @Test @Timeout(180)
    void recorridoNominalEnContextoNuevo() throws Exception {
        Path raiz = TestContextos.nuevaRaiz();
        Path base = raiz.resolve("taller");
        assertEquals(base, TestContextos.validar(base));
        assertFalse(Files.exists(Path.of(base + ".mv.db")));
        System.out.println("NOMINAL fixture=" + raiz);
        var tecnicas = new TestContextos.ContadoresFase();
        var muestras = new ConcurrentLinkedQueue<TestContextos.Muestra>();
        try (var contexto = TestContextos.abrir(base, UnaryOperator.identity(), tecnicas::decorar,
                new CoordinadorTaller(), muestras::add, TestContextos.solicitud(base))) {
            assertEquals(ContextoTaller.Ciclo.HABILITADO, contexto.ciclo(), () -> contexto.informe().toString());
            assertTrue(contexto.informe().hallazgos().isEmpty());
            assertThrows(java.io.IOException.class, () -> GuardiaInstancia.adquirir(raiz));
            var taller = contexto.servicio();
            assertTrue(vista(taller).dominio().ordenes().isEmpty());
            paso(1);

            recurso(taller, TECNICO, "T1", null, true);
            recurso(taller, TECNICO, "T2", null, true);
            recurso(taller, PUESTO, "P1", null, true);
            recurso(taller, PUESTO, "P2", null, true);
            recurso(taller, HERRAMIENTA, "M1", MULTIMETRO, true);
            recurso(taller, HERRAMIENTA, "M2", MULTIMETRO, true);
            recurso(taller, HERRAMIENTA, "S1", SOLDADOR, true);
            recurso(taller, HERRAMIENTA, "D1", TALADRO, false);
            var inventario = vista(taller);
            assertEquals(Set.of(MULTIMETRO, SOLDADOR, TALADRO), inventario.catalogo());
            assertEquals(8, inventario.dominio().recursos().size());
            assertEquals(FUERA_DE_SERVICIO, inventario.dominio().disponibilidad(clave(HERRAMIENTA, "D1")));
            assertEquals(7, inventario.dominio().recursos().stream()
                    .filter(r -> inventario.dominio().disponibilidad(r.clave()) == DISPONIBLE).count());
            paso(2);

            confirmado(taller.registrarOrden("IMPOSIBLE", "Taladro inhabilitado", List.of(TALADRO)));
            confirmado(taller.registrarOrden("A", "Dos tipos", List.of(MULTIMETRO, SOLDADOR)));
            confirmado(taller.registrarOrden("B", "Otra unidad física", List.of(MULTIMETRO)));
            confirmado(taller.registrarOrden("C", "Sin herramientas", List.of()));
            var pendientes = vista(taller);
            assertEquals(List.of(1L, 2L, 3L, 4L), pendientes.dominio().ordenes().stream().map(Orden::secuencia).toList());
            var a = propuesta(taller);
            var seleccionA = new SeleccionRecursos(clave(TECNICO, "T1"), clave(PUESTO, "P1"),
                    Map.of(MULTIMETRO, clave(HERRAMIENTA, "M1"), SOLDADOR, clave(HERRAMIENTA, "S1")));
            assertEquals(new PropuestaInicio(contexto.identidad(), "A", seleccionA), a);
            assertEquals(pendientes, vista(taller)); // Consultar/proponer no reserva.
            assertTrue(pendientes.dominio().asignaciones().isEmpty());
            assertFalse(pendientes.planificacion().causas().get("IMPOSIBLE").isEmpty());
            sinEfectos(taller, () -> taller.confirmarInicio(new PropuestaInicio(a.contexto(), "B", a.recursos())));

            // Una barrera libera dos solicitudes reales, sin sleeps ni duración ficticia.
            var barrera = new CyclicBarrier(2);
            try (var ejecutor = Executors.newFixedThreadPool(2)) {
                Callable<ResultadoOperacion<PropuestaInicio>> solicitar = () -> {
                    barrera.await(10, TimeUnit.SECONDS);
                    return taller.confirmarInicio(a);
                };
                var f1 = ejecutor.submit(solicitar);
                var f2 = ejecutor.submit(solicitar);
                var resultados = List.of(f1.get(30, TimeUnit.SECONDS), f2.get(30, TimeUnit.SECONDS));
                assertEquals(1, resultados.stream().filter(r -> r.estado() == CONFIRMADA).count(), resultados::toString);
                assertEquals(1, resultados.stream().filter(r -> r.estado() == RECHAZADA).count(), resultados::toString);
                assertEquals(2, muestras.stream().filter(m -> resultados.stream()
                        .anyMatch(r -> Objects.equals(r.operacionId(), m.intentoId())))
                        .map(TestContextos.Muestra::hiloId).distinct().count());
                var iniciada = vista(taller);
                assertEquals(Map.of("A", seleccionA), iniciada.dominio().asignaciones());
                assertEquals(pendientes.historial().size() + 1, iniciada.historial().size());
                assertEquals(List.of("REGISTRO_ORDEN", "INICIO"), acciones(iniciada, "A"));
            }
            var b = propuesta(taller);
            var seleccionB = new SeleccionRecursos(clave(TECNICO, "T2"), clave(PUESTO, "P2"),
                    Map.of(MULTIMETRO, clave(HERRAMIENTA, "M2")));
            assertEquals(new PropuestaInicio(contexto.identidad(), "B", seleccionB), b);
            confirmado(taller.confirmarInicio(b));
            paso(3);

            var simultaneas = vista(taller);
            assertEquals(Map.of("A", seleccionA, "B", seleccionB), simultaneas.dominio().asignaciones());
            assertEquals(2, simultaneas.dominio().ordenes().stream().filter(o -> o.estado() == EstadoOrden.EN_CURSO).count());
            assertTrue(Collections.disjoint(seleccionA.unidades(), seleccionB.unidades()));
            assertEquals(7, simultaneas.dominio().asignaciones().values().stream()
                    .flatMap(s -> s.unidades().stream()).distinct().count());
            seleccionA.unidades().forEach(k -> assertEquals(OCUPADO, simultaneas.dominio().disponibilidad(k)));
            seleccionB.unidades().forEach(k -> assertEquals(OCUPADO, simultaneas.dominio().disponibilidad(k)));
            assertEquals(EstadoOrden.PENDIENTE, estado(simultaneas, "C"));
            assertTrue(simultaneas.planificacion().propuesta().isEmpty());
            paso(4);

            sinEfectos(taller, () -> taller.confirmarInicio(a));
            sinEfectos(taller, () -> taller.registrarOrden("A", "Duplicada", List.of()));
            sinEfectos(taller, () -> taller.cambiarHabilitacion(clave(HERRAMIENTA, "M1"), false));
            paso(5);

            confirmado(taller.finalizar("A"));
            var liberada = vista(taller);
            assertEquals(EstadoOrden.FINALIZADA, estado(liberada, "A"));
            assertEquals(Map.of("B", seleccionB), liberada.dominio().asignaciones());
            seleccionA.unidades().forEach(k -> assertEquals(DISPONIBLE, liberada.dominio().disponibilidad(k)));
            seleccionB.unidades().forEach(k -> assertEquals(OCUPADO, liberada.dominio().disponibilidad(k)));
            var c = propuesta(taller);
            assertEquals(new PropuestaInicio(contexto.identidad(), "C",
                    new SeleccionRecursos(clave(TECNICO, "T1"), clave(PUESTO, "P1"), Map.of())), c);
            confirmado(taller.confirmarInicio(c));
            sinEfectos(taller, () -> taller.finalizar("A")); // No libera T1/P1 reasignados a C.
            confirmado(taller.fallar("B"));
            var fallida = vista(taller);
            assertEquals(EstadoOrden.FALLIDA, estado(fallida, "B"));
            assertEquals(Map.of("C", c.recursos()), fallida.dominio().asignaciones());
            seleccionB.unidades().forEach(k -> assertEquals(DISPONIBLE, fallida.dominio().disponibilidad(k)));
            sinEfectos(taller, () -> taller.fallar("B"));
            confirmado(taller.finalizar("C"));
            var finalizada = vista(taller);
            assertTrue(finalizada.dominio().asignaciones().isEmpty());
            assertEquals(EstadoOrden.FINALIZADA, estado(finalizada, "C"));
            assertEquals(EstadoOrden.PENDIENTE, estado(finalizada, "IMPOSIBLE"));
            assertEquals(18, finalizada.historial().size());
            assertEquals(18, finalizada.revision());
            assertEquals(18, finalizada.historial().stream().map(EventoHistorial::operacionId).distinct().count());
            for (int i = 0; i < finalizada.historial().size(); i++)
                assertEquals(i + 1L, finalizada.historial().get(i).secuencia());
            historial(finalizada, "A", "FINALIZADA", seleccionA);
            historial(finalizada, "B", "FALLIDA", seleccionB);
            historial(finalizada, "C", "FINALIZADA", c.recursos());
            assertTrue(finalizada.revisiones().isEmpty());
            assertEquals(ContextoTaller.Ciclo.HABILITADO, contexto.ciclo());
            paso(6);

            // Observar el commit real de marca limpia y el cierre de su conexión.
            tecnicas.reiniciar();
            contexto.close();
            assertEquals(1, tecnicas.aperturas.get());
            assertEquals(1, tecnicas.commits.get());
            assertEquals(0, tecnicas.rollbacks.get());
            assertEquals(1, tecnicas.cierres.get());
            assertEquals(ContextoTaller.Ciclo.CERRADO, contexto.ciclo());
            assertTrue(contexto.informe().hallazgos().isEmpty(), () -> contexto.informe().toString());
            try (var guardia = GuardiaInstancia.adquirir(raiz)) { assertTrue(guardia.vigente()); }
            assertEquals(ERROR_TECNICO, taller.consultar().estado());
            assertEquals(1, tecnicas.aperturas.get()); // Referencia cerrada no vuelve a JDBC.
            paso(7);
        }
    }

    private static <T> T confirmado(ResultadoOperacion<T> resultado) {
        assertEquals(CONFIRMADA, resultado.estado(), resultado::toString);
        return resultado.valor();
    }
    private static InstantaneaTaller vista(ServicioTaller taller) { return confirmado(taller.consultar()); }
    private static PropuestaInicio propuesta(ServicioTaller taller) { return vista(taller).planificacion().propuesta().orElseThrow(); }
    private static Recurso.Clave clave(Recurso.Categoria categoria, String id) { return new Recurso.Clave(categoria, id); }
    private static void recurso(ServicioTaller taller, Recurso.Categoria categoria, String id, TipoHerramienta tipo, boolean habilitado) {
        assertEquals(new Recurso(clave(categoria, id), tipo, habilitado), confirmado(taller.registrarRecurso(categoria, id, tipo, habilitado)));
    }
    private static void sinEfectos(ServicioTaller taller, Supplier<ResultadoOperacion<?>> intento) {
        var antes = vista(taller);
        var resultado = intento.get();
        assertEquals(RECHAZADA, resultado.estado(), resultado::toString);
        assertEquals(antes, vista(taller)); // Incluye estado, asignación, disponibilidad, revisión e historial.
    }
    private static EstadoOrden estado(InstantaneaTaller vista, String id) {
        return vista.dominio().ordenes().stream().filter(o -> o.id().equals(id)).findFirst().orElseThrow().estado();
    }
    private static List<String> acciones(InstantaneaTaller vista, String id) {
        return vista.historial().stream().filter(e -> id.equals(e.ordenId())).map(EventoHistorial::accion).toList();
    }
    private static void historial(InstantaneaTaller vista, String id, String cierre, SeleccionRecursos seleccion) {
        assertEquals(List.of("REGISTRO_ORDEN", "INICIO", cierre), acciones(vista, id));
        vista.historial().stream().filter(e -> id.equals(e.ordenId()) && !e.accion().equals("REGISTRO_ORDEN"))
                .forEach(e -> assertEquals(seleccion.unidades(), e.recursos()));
    }
    private static void paso(int numero) { System.out.println("NOMINAL paso=" + numero + " OK"); }
}
