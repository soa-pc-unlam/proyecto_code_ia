package ar.edu.unlam.reparabarrio.aplicacion;

import ar.edu.unlam.reparabarrio.persistencia.H3Fixture;
import ar.edu.unlam.reparabarrio.dominio.*;
import org.junit.jupiter.api.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static ar.edu.unlam.reparabarrio.persistencia.H3Fixture.*;
import static ar.edu.unlam.reparabarrio.aplicacion.ResultadoOperacion.Estado.*;
import static org.junit.jupiter.api.Assertions.*;

/** H3-T10 y H3-D04/OD-H3-03. Observador interno poslock, fixture JDBC exclusivo.
 * 10 s por espera y 60 s por prueba; no se miden CPU ni trabajo de reparación.
 */
@Timeout(60)
class CoordinadorConcurrenciaTest {
    private ExecutorService pool;
    private final List<Future<?>> tareas = new ArrayList<>();
    private final List<H3Fixture.Control> controles = new ArrayList<>();
    @BeforeEach void preparar() { pool = Executors.newFixedThreadPool(4); }
    @AfterEach void cerrar() throws Exception {
        controles.forEach(c -> c.continuar.countDown());
        tareas.forEach(f -> { if (!f.isDone()) f.cancel(true); });
        pool.shutdownNow(); assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), () -> diagnostico("Executor observación no terminó"));
        TestContextos.cerrarTodos();
    }
    private <T> Future<T> enviar(Callable<T> c) { var f = pool.submit(c); tareas.add(f); return f; }
    private H3Fixture.Control detener(H3Fixture f, String punto, boolean error) {
        var c = new H3Fixture.Control(punto, error); controles.add(c); f.control.set(c); return c;
    }
    private record Fixture(H3Fixture jdbc, CoordinadorTaller coordinador, ServicioTaller servicio) { }
    private Fixture fixture(ServicioTaller.Observador o, CoordinadorTaller c) throws Exception {
        var f = new H3Fixture(false, c, o == null ? null : m -> o.recibir(new ServicioTaller.Muestra(
                m.contexto(), m.intentoId(), m.hiloId(), m.hiloNombre(), m.adquirido(), m.esperaNanos(), m.posesionNanos())));
        return new Fixture(f, c, f.taller);
    }
    private static final class Recolector implements ServicioTaller.Observador {
        final ArrayBlockingQueue<ServicioTaller.Muestra> cola;
        final AtomicInteger descartadas = new AtomicInteger();
        Recolector(int capacidad) { cola = new ArrayBlockingQueue<>(capacidad); }
        public void recibir(ServicioTaller.Muestra m) { if (!cola.offer(m)) descartadas.incrementAndGet(); }
    }

    @Test void t10_interrupcionEnEsperaNoAbreTransaccionNiRetieneLock() throws Exception {
        var c = new CoordinadorTaller(); var muestras = new Recolector(16); var f = fixture(muestras, c);
        var pausa = detener(f.jdbc(), "COMMIT_ANTES", false);
        var dueno = enviar(() -> f.servicio().registrarOrden("A", "Dueño", List.of()));
        esperarPunto(pausa, dueno, f.jdbc().archivo);
        var hilo = new AtomicReference<Thread>(); var anuncio = new CountDownLatch(1); var indicador = new AtomicBoolean();
        var interrumpido = enviar(() -> {
            hilo.set(Thread.currentThread()); anuncio.countDown();
            var r = f.servicio().registrarOrden("B", "No debe abrir", List.of());
            indicador.set(Thread.currentThread().isInterrupted()); return r;
        });
        try {
            esperar(anuncio, "interrumpible"); esperandoLock(hilo.get());
            assertTrue(c.bloqueo.hasQueuedThread(hilo.get())); hilo.get().interrupt();
            var r = obtener(interrumpido); assertEquals(ERROR_TECNICO, r.estado()); assertTrue(indicador.get());
            assertEquals(1, f.jdbc().aperturas.get());
            assertEquals(1, f.jdbc().tecnicas.aperturas.get()); // sólo la intención del dueño
            var m = muestras.cola.peek(); assertNotNull(m); assertFalse(m.adquirido());
            assertNull(m.posesionNanos()); assertTrue(m.esperaNanos() >= 0); assertEquals(r.operacionId(), m.intentoId());
        } finally { pausa.continuar.countDown(); }
        ok(obtener(dueno)); ok(f.servicio().registrarOrden("C", "Posterior", List.of()));
        var s = ok(f.servicio().consultar());
        assertEquals(List.of("A", "C"), s.dominio().ordenes().stream().map(Orden::id).toList());
        assertEquals(3, f.jdbc().aperturas.get()); f.jdbc().durable(s);
    }
    @Test void observadorFallidoEnInterrupcionPreservaIndicadorYResultado() throws Exception {
        var c = new CoordinadorTaller(); var muestra = new AtomicReference<ServicioTaller.Muestra>();
        var bajoLock = new AtomicBoolean();
        var f = fixture(m -> {
            muestra.set(m); if (c.bloqueo.isHeldByCurrentThread()) bajoLock.set(true);
            Thread.interrupted(); throw new AssertionError("Observador interrumpido roto");
        }, c);
        var hilo = new AtomicReference<Thread>(); var anuncio = new CountDownLatch(1); var indicador = new AtomicBoolean();
        // Control interno del coordinador, complementario al dueño JDBC real de T10.
        c.bloqueo.lock();
        try {
            var tarea = enviar(() -> {
                hilo.set(Thread.currentThread()); anuncio.countDown();
                var r = f.servicio().consultar(); indicador.set(Thread.currentThread().isInterrupted()); return r;
            });
            esperar(anuncio, "interrupción con observador fallido"); esperandoLock(hilo.get()); hilo.get().interrupt();
            var r = obtener(tarea); assertEquals(ERROR_TECNICO, r.estado()); assertTrue(indicador.get());
            assertEquals(0, f.jdbc().aperturas.get()); assertFalse(muestra.get().adquirido());
            assertNull(muestra.get().posesionNanos()); assertFalse(bajoLock.get());
            assertTrue(c.bloqueo.isHeldByCurrentThread());
        } finally { c.bloqueo.unlock(); }
        ok(f.servicio().consultar()); assertFalse(c.bloqueo.isLocked());
    }
    @Test void observacionPoslockEsAcotadaYMideEsperaYPosesion() throws Exception {
        var c = new CoordinadorTaller(); var recolector = new Recolector(16); var bajoLock = new AtomicBoolean();
        var f = fixture(m -> { if (c.bloqueo.isHeldByCurrentThread()) bajoLock.set(true); recolector.recibir(m); }, c);
        var pausa = detener(f.jdbc(), "COMMIT_ANTES", false);
        var dueno = enviar(() -> f.servicio().registrarOrden("A", "Medida", List.of()));
        esperarPunto(pausa, dueno, f.jdbc().archivo);
        var hilo = new AtomicReference<Thread>(); var anuncio = new CountDownLatch(1);
        var lector = enviar(() -> { hilo.set(Thread.currentThread()); anuncio.countDown(); return f.servicio().consultar(); });
        try { esperar(anuncio, "lector medido"); esperandoLock(hilo.get()); }
        finally { pausa.continuar.countDown(); }
        ok(obtener(dueno)); var s = ok(obtener(lector)); f.jdbc().durable(s);
        assertFalse(bajoLock.get()); assertEquals(0, recolector.descartadas.get());
        var muestras = new ArrayList<ServicioTaller.Muestra>(); recolector.cola.drainTo(muestras);
        assertEquals(2, muestras.size());
        for (var m : muestras) {
            assertTrue(m.adquirido()); assertTrue(m.esperaNanos() >= 0); assertNotNull(m.posesionNanos());
            assertTrue(m.posesionNanos() > 0); assertEquals(TestContextos.abrir(f.jdbc().archivo).identidad(), m.contexto());
        }
        var lectura = muestras.stream().filter(m -> m.intentoId() == null).findFirst().orElseThrow();
        assertEquals(hilo.get().threadId(), lectura.hiloId()); assertTrue(lectura.esperaNanos() > 0);
        // Sólo estas dos muestras activas: distribución real, sin umbral de producto.
        var esperas = muestras.stream().mapToLong(ServicioTaller.Muestra::esperaNanos).sorted().toArray();
        var posesiones = muestras.stream().mapToLong(m -> m.posesionNanos()).sorted().toArray();
        System.out.println("H3-D04 capacidad=16 muestras=2 descartadas=0 espera_ns[min,max]="
                + Arrays.toString(esperas) + " posesión_ns[min,max]=" + Arrays.toString(posesiones));
    }
    @Test void colaLlenaDescartaSinAlterarResultados() throws Exception {
        var recolector = new Recolector(1); var f = fixture(recolector, new CoordinadorTaller());
        ok(f.servicio().registrarOrden("A", "Cola llena", List.of()));
        ok(f.servicio().consultar()); ok(f.servicio().cancelar("A"));
        assertEquals(1, recolector.cola.size()); assertEquals(2, recolector.descartadas.get());
        var s = ok(f.servicio().consultar()); assertEquals(EstadoOrden.CANCELADA, s.dominio().ordenes().getFirst().estado());
        f.jdbc().durable(s);
    }
    @Test void observadorQueFallaNoCambiaCommitRechazoErrorNiCierre() throws Exception {
        var c = new CoordinadorTaller(); var llamadas = new AtomicInteger(); var bajoLock = new AtomicBoolean();
        var f = fixture(m -> {
            if (c.bloqueo.isHeldByCurrentThread()) bajoLock.set(true);
            llamadas.incrementAndGet(); Thread.currentThread().interrupt(); throw new IllegalStateException("Observador roto");
        }, c);
        var r = f.servicio().registrarOrden("A", "Confirmada", List.of()); ok(r); assertFalse(Thread.currentThread().isInterrupted());
        assertEquals(RECHAZADA, f.servicio().registrarOrden("A", "Duplicada", List.of()).estado());
        var error = detener(f.jdbc(), "registrar", true); error.continuar.countDown();
        assertEquals(ERROR_TECNICO, f.servicio().registrarOrden("B", "Rollback", List.of()).estado());
        var cierre = detener(f.jdbc(), "CIERRE", true); cierre.continuar.countDown();
        var conservada = f.servicio().registrarOrden("C", "Cierre fallido", List.of());
        ok(conservada); assertTrue(conservada.diagnostico().contains("Fallo al cerrar conexión"));
        f.jdbc().control.set(null);
        var s = ok(f.servicio().consultar()); assertEquals(List.of("A", "C"), s.dominio().ordenes().stream().map(Orden::id).toList());
        assertEquals(5, llamadas.get()); assertFalse(bajoLock.get()); assertFalse(c.bloqueo.isLocked()); f.jdbc().durable(s);
    }
    @Test void retornoPorSaludInciertaSeObservaSinOcultarIdNiAbrir() throws Exception {
        var c = new CoordinadorTaller(); var muestras = new Recolector(8); var bajoLock = new AtomicBoolean();
        var f = fixture(m -> { if (c.bloqueo.isHeldByCurrentThread()) bajoLock.set(true); muestras.recibir(m);
            throw new AssertionError("Diagnóstico roto"); }, c);
        var pausa = detener(f.jdbc(), "COMMIT_DESPUES", true); pausa.continuar.countDown();
        var r = f.servicio().registrarOrden("A", "Incierta", List.of());
        assertEquals(PENDIENTE_VERIFICACION, r.estado()); assertNotNull(r.operacionId());
        var lectura = f.servicio().consultar(); var siguiente = f.servicio().cancelar("A");
        assertEquals(PENDIENTE_VERIFICACION, lectura.estado()); assertEquals(r.operacionId(), lectura.operacionId());
        assertEquals(PENDIENTE_VERIFICACION, siguiente.estado()); assertEquals(r.operacionId(), siguiente.operacionId());
        assertEquals(1, f.jdbc().aperturas.get()); assertEquals(3, muestras.cola.size()); assertFalse(bajoLock.get());
        assertEquals(2, f.jdbc().tecnicas.aperturas.get()); assertEquals(2, f.jdbc().tecnicas.commits.get());
        assertEquals(0, f.jdbc().tecnicas.rollbacks.get());
        assertTrue(muestras.cola.stream().allMatch(ServicioTaller.Muestra::adquirido)); assertFalse(c.bloqueo.isLocked());
    }
    @Test void observacionActivadaYDesactivadaConservanContrato() throws Exception {
        var muestras = new Recolector(32);
        var activa = fixture(muestras, new CoordinadorTaller()); var inactiva = fixture(null, new CoordinadorTaller());
        var vistas = new ArrayList<InstantaneaTaller>();
        for (var f : List.of(activa, inactiva)) {
            ok(f.servicio().registrarRecurso(Recurso.Categoria.TECNICO, "T", null, true));
            ok(f.servicio().registrarRecurso(Recurso.Categoria.PUESTO, "P", null, true));
            ok(f.servicio().registrarOrden("A", "Contrato", List.of()));
            var propuesta = ok(f.servicio().consultar()).planificacion().propuesta().orElseThrow();
            ok(f.servicio().confirmarInicio(propuesta));
            assertEquals(RECHAZADA, f.servicio().cancelar("A").estado()); ok(f.servicio().finalizar("A"));
            var s = ok(f.servicio().consultar()); f.jdbc().durable(s); vistas.add(s);
        }
        var a = vistas.get(0); var b = vistas.get(1);
        assertEquals(a.dominio().ordenes(), b.dominio().ordenes()); assertEquals(a.dominio().recursos(), b.dominio().recursos());
        assertEquals(a.dominio().asignaciones(), b.dominio().asignaciones()); assertEquals(a.revision(), b.revision());
        assertEquals(a.historial().stream().map(EventoHistorial::accion).toList(), b.historial().stream().map(EventoHistorial::accion).toList());
        assertEquals(8, muestras.cola.size()); assertEquals(0, muestras.descartadas.get());
    }
}
