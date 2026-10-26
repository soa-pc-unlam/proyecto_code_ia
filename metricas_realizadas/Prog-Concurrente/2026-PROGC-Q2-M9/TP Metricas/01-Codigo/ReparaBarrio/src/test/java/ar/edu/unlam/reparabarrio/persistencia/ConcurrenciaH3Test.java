package ar.edu.unlam.reparabarrio.persistencia;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;
import static ar.edu.unlam.reparabarrio.persistencia.H3Fixture.*;
import static ar.edu.unlam.reparabarrio.aplicacion.ResultadoOperacion.Estado.*;
import static ar.edu.unlam.reparabarrio.dominio.Recurso.Categoria.*;
import static ar.edu.unlam.reparabarrio.dominio.TipoHerramienta.*;
import static org.junit.jupiter.api.Assertions.*;

/** H3-T01..T09/T11 sobre archivo real. T07 complementaria incluida; T12 pendiente.
 * Cuatro trabajadores; 10 s por espera, 600 s por caso de 50 carreras.
 * El presupuesto global externo del proceso es 900 s (surefire.timeout).
 * Sin locks por recurso, reparaciones fuera de la fachada. Pruebas finitas no
 * demuestran ausencia universal de deadlock ni starvation del negocio.
 */
@Timeout(value = 600, unit = TimeUnit.SECONDS)
class ConcurrenciaH3Test {
    private static final int REPETICIONES = Integer.getInteger("h3.repeticiones", 50);
    enum Precedencia { LIBRE, PRIMERO, SEGUNDO }
    private ExecutorService pool;
    private final List<Future<?>> tareas = new ArrayList<>();
    private final List<H3Fixture.Control> controles = new ArrayList<>();
    @BeforeEach void preparar() { pool = Executors.newFixedThreadPool(4); }
    @AfterEach void cerrar() throws Exception {
        controles.forEach(c -> c.continuar.countDown());
        tareas.forEach(f -> { if (!f.isDone()) f.cancel(true); });
        pool.shutdownNow();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS), () -> diagnostico("Executor H3 no terminó; conservar archivos"));
        TestContextos.cerrarTodos();
    }
    private <T> Future<T> enviar(Callable<T> tarea) {
        var f = pool.submit(tarea); tareas.add(f); return f;
    }
    private H3Fixture.Control detener(H3Fixture f, String punto, boolean error) {
        var c = new H3Fixture.Control(punto, error); controles.add(c); f.control.set(c); return c;
    }
    private List<ResultadoOperacion<?>> carrera(H3Fixture f, Precedencia p,
            Supplier<ResultadoOperacion<?>> a, Supplier<ResultadoOperacion<?>> b) throws Exception {
        if (p == Precedencia.LIBRE) {
            var barrera = new CyclicBarrier(2);
            var fa = enviar(() -> { barrera.await(10, TimeUnit.SECONDS); return a.get(); });
            var fb = enviar(() -> { barrera.await(10, TimeUnit.SECONDS); return b.get(); });
            return List.of(obtener(fa), obtener(fb));
        }
        // El primer contendiente posee el coordinador tras cargar su transacción.
        // Sirve también para precedencia de un rechazo (sin commit de negocio).
        var c = detener(f, "cargar", false);
        var primero = enviar(() -> (p == Precedencia.PRIMERO ? a : b).get());
        esperarPunto(c, primero, f.archivo);
        var hilo = new AtomicReference<Thread>(); var anunciado = new CountDownLatch(1);
        var segundo = enviar(() -> {
            hilo.set(Thread.currentThread()); anunciado.countDown();
            return (p == Precedencia.PRIMERO ? b : a).get();
        });
        try {
            esperar(anunciado, "segundo contendiente"); esperandoLock(hilo.get());
            assertFalse(segundo.isDone());
        } finally { c.continuar.countDown(); }
        var r1 = obtener(primero); var r2 = obtener(segundo); f.control.set(null);
        return p == Precedencia.PRIMERO ? List.of(r1, r2) : List.of(r2, r1);
    }
    private void unGanador(List<ResultadoOperacion<?>> r, Precedencia p) {
        assertEquals(1, r.stream().filter(x -> x.estado() == CONFIRMADA).count(), r.toString());
        assertEquals(1, r.stream().filter(x -> x.estado() == RECHAZADA).count(), r.toString());
        if (p != Precedencia.LIBRE) assertEquals(CONFIRMADA, r.get(p == Precedencia.PRIMERO ? 0 : 1).estado());
    }
    private void sinEvento(InstantaneaTaller s, ResultadoOperacion<?> rechazo) {
        assertEquals(RECHAZADA, rechazo.estado());
        assertTrue(s.historial().stream().noneMatch(e -> e.operacionId().equals(rechazo.operacionId())));
    }
    private InstantaneaTaller comprobar(H3Fixture f) throws Exception {
        var s = f.vista(); oraculo(s); f.durable(s); return s;
    }

    /** Oráculo independiente: replay por revisión, sin usar Planificador ni
     * validadores de producción para decidir prioridad, titulares o transiciones.
     */
    private void oraculo(InstantaneaTaller s) {
        Map<String, Orden> ordenes = new HashMap<>();
        s.dominio().ordenes().forEach(o -> ordenes.put(o.id(), o));
        Map<Recurso.Clave, Recurso> recursos = new HashMap<>();
        s.dominio().recursos().forEach(r -> recursos.put(r.clave(), r));
        Map<Recurso.Clave, Boolean> habilitados = new HashMap<>();
        Map<String, EstadoOrden> estados = new HashMap<>();
        Map<Recurso.Clave, String> titulares = new HashMap<>();
        Map<String, Set<Recurso.Clave>> conjuntos = new HashMap<>();
        Set<String> operaciones = new HashSet<>();
        long revision = 0;
        for (var e : s.historial()) {
            assertEquals(++revision, e.secuencia()); assertTrue(operaciones.add(e.operacionId()));
            assertNotNull(e.momento());
            switch (e.accion()) {
                case "REGISTRO_RECURSO" -> {
                    for (var clave : e.recursos()) {
                        var cambio = s.historial().stream().filter(x -> x.recursos().contains(clave)
                                && Set.of("HABILITACION", "INHABILITACION").contains(x.accion())).findFirst();
                        boolean inicial = cambio.map(x -> x.accion().equals("INHABILITACION"))
                                .orElse(recursos.get(clave).habilitado());
                        assertNull(habilitados.put(clave, inicial));
                    }
                }
                case "REGISTRO_ORDEN" -> assertNull(estados.put(e.ordenId(), EstadoOrden.PENDIENTE));
                case "HABILITACION", "INHABILITACION" -> {
                    for (var clave : e.recursos()) {
                        assertFalse(titulares.containsKey(clave));
                        habilitados.put(clave, e.accion().equals("HABILITACION"));
                    }
                }
                case "INICIO" -> {
                    var primera = ordenes.values().stream().filter(o -> estados.get(o.id()) == EstadoOrden.PENDIENTE)
                            .filter(o -> factible(o, recursos, habilitados, titulares))
                            .min(Comparator.comparingLong(Orden::secuencia)).orElseThrow();
                    assertEquals(primera.id(), e.ordenId(), "Prioridad al commit " + e.secuencia());
                    assertEquals(EstadoOrden.PENDIENTE, estados.put(e.ordenId(), EstadoOrden.EN_CURSO));
                    assertEquals(1, e.recursos().stream().filter(c -> c.categoria() == TECNICO).count());
                    assertEquals(1, e.recursos().stream().filter(c -> c.categoria() == PUESTO).count());
                    var tipos = new HashSet<TipoHerramienta>();
                    for (var clave : e.recursos()) {
                        assertEquals(Boolean.TRUE, habilitados.get(clave));
                        assertNull(titulares.put(clave, e.ordenId()), "Unidad ya ocupada");
                        if (clave.categoria() == HERRAMIENTA) assertTrue(tipos.add(recursos.get(clave).tipo()));
                    }
                    assertEquals(primera.requisitos(), tipos); conjuntos.put(e.ordenId(), e.recursos());
                }
                case "FINALIZADA", "FALLIDA" -> {
                    assertEquals(EstadoOrden.EN_CURSO, estados.put(e.ordenId(), EstadoOrden.valueOf(e.accion())));
                    assertEquals(conjuntos.remove(e.ordenId()), e.recursos());
                    e.recursos().forEach(c -> assertEquals(e.ordenId(), titulares.remove(c)));
                }
                case "CANCELADA" -> {
                    assertEquals(EstadoOrden.PENDIENTE, estados.put(e.ordenId(), EstadoOrden.CANCELADA));
                    assertTrue(e.recursos().isEmpty()); assertFalse(conjuntos.containsKey(e.ordenId()));
                }
                default -> fail("Evento inesperado " + e);
            }
        }
        assertEquals(s.revision(), revision);
        ordenes.forEach((id, o) -> assertEquals(o.estado(), estados.get(id)));
        recursos.forEach((c, r) -> assertEquals(r.habilitado(), habilitados.get(c)));
        var activas = new HashMap<String, Set<Recurso.Clave>>();
        s.dominio().asignaciones().forEach((id, seleccion) -> activas.put(id, seleccion.unidades()));
        assertEquals(activas, conjuntos);
        recursos.keySet().forEach(c -> assertEquals(titulares.containsKey(c) ? Recurso.Disponibilidad.OCUPADO
                : habilitados.get(c) ? Recurso.Disponibilidad.DISPONIBLE : Recurso.Disponibilidad.FUERA_DE_SERVICIO,
                s.dominio().disponibilidad(c)));
    }
    private boolean factible(Orden o, Map<Recurso.Clave, Recurso> recursos,
            Map<Recurso.Clave, Boolean> habilitados, Map<Recurso.Clave, String> titulares) {
        var libres = recursos.values().stream().filter(r -> Boolean.TRUE.equals(habilitados.get(r.clave()))
                && !titulares.containsKey(r.clave())).toList();
        return libres.stream().anyMatch(r -> r.clave().categoria() == TECNICO)
                && libres.stream().anyMatch(r -> r.clave().categoria() == PUESTO)
                && o.requisitos().stream().allMatch(t -> libres.stream().anyMatch(r -> r.tipo() == t));
    }

    @ParameterizedTest @EnumSource(Precedencia.class)
    void t01_dobleInicio_CA07(Precedencia p) throws Exception {
        var f = new H3Fixture(p == Precedencia.LIBRE); f.capacidad(1);
        for (int i = 0; i < REPETICIONES; i++) {
            String id = "A" + i; f.orden(id, MULTIMETRO);
            if (i == 0) f.recurso(HERRAMIENTA, "M", MULTIMETRO, true);
            var propuesta = f.propuesta();
            var r = carrera(f, p, () -> f.taller.confirmarInicio(propuesta), () -> f.taller.confirmarInicio(propuesta));
            unGanador(r, p); var s = comprobar(f);
            assertEquals(propuesta.recursos(), s.dominio().asignaciones().get(id));
            assertEquals(1, s.historial().stream().filter(e -> id.equals(e.ordenId()) && e.accion().equals("INICIO")).count());
            r.stream().filter(x -> x.estado() == RECHAZADA).forEach(x -> sinEvento(s, x));
            ok(f.taller.finalizar(id)); comprobar(f);
        }
        System.out.println("H3-T01 " + p + " repeticiones=" + REPETICIONES + " archivo=" + f.archivo);
    }
    @ParameterizedTest @EnumSource(Precedencia.class)
    void t02_herramientaUnica_CA08_CA06_CA19(Precedencia p) throws Exception {
        var f = new H3Fixture(p == Precedencia.LIBRE); f.capacidad(2); f.recurso(HERRAMIENTA, "M", MULTIMETRO, true);
        f.orden("A", MULTIMETRO); f.orden("B", MULTIMETRO);
        var a = f.propuesta();
        // B deliberadamente construida: no es propuesta emitida por la fachada.
        var b = new PropuestaInicio(a.contexto(), "B", new SeleccionRecursos(
                new Recurso.Clave(TECNICO, "T2"), new Recurso.Clave(PUESTO, "P2"), a.recursos().herramientas()));
        var r = carrera(f, p, () -> f.taller.confirmarInicio(a), () -> f.taller.confirmarInicio(b));
        assertEquals(CONFIRMADA, r.get(0).estado()); assertEquals(RECHAZADA, r.get(1).estado());
        var s = comprobar(f); sinEvento(s, r.get(1));
        assertEquals(Set.of("A"), s.dominio().asignaciones().keySet());
        assertEquals(Recurso.Disponibilidad.DISPONIBLE, s.dominio().disponibilidad(b.recursos().tecnico()));
        assertEquals(Recurso.Disponibilidad.DISPONIBLE, s.dominio().disponibilidad(b.recursos().puesto()));
        assertTrue(s.planificacion().propuesta().isEmpty());
        ok(f.taller.finalizar("A")); comprobar(f);
        assertEquals("B", f.propuesta().ordenId()); ok(f.taller.confirmarInicio(f.propuesta())); comprobar(f);
    }
    private record Intervalo(long hilo, String nombre, long inicio, long fin) { }
    @Test void t03_trabajosRealmenteSuperpuestos_CA09() throws Exception {
        var f = new H3Fixture(true); f.capacidad(2);
        f.recurso(HERRAMIENTA, "M", MULTIMETRO, true); f.recurso(HERRAMIENTA, "S", SOLDADOR, true);
        f.orden("A", MULTIMETRO); f.orden("B", SOLDADOR);
        var a = f.propuesta();
        var bInvalida = new PropuestaInicio(a.contexto(), "B", new SeleccionRecursos(
                new Recurso.Clave(TECNICO, "T2"), new Recurso.Clave(PUESTO, "P2"),
                Map.of(SOLDADOR, new Recurso.Clave(HERRAMIENTA, "S"))));
        var intento = f.taller.confirmarInicio(bInvalida); sinEvento(comprobar(f), intento);
        var aTrabaja = new CountDownLatch(1); var ambos = new CountDownLatch(2); var cerrar = new CountDownLatch(1);
        Callable<Intervalo> trabajoA = () -> {
            ok(f.taller.confirmarInicio(a)); long inicio = System.nanoTime();
            aTrabaja.countDown(); ambos.countDown(); esperar(cerrar, "cierre A exterior");
            long fin = System.nanoTime(); ok(f.taller.finalizar("A"));
            return new Intervalo(Thread.currentThread().threadId(), Thread.currentThread().getName(), inicio, fin);
        };
        var fa = enviar(trabajoA); var fb = enviar(() -> {
            esperar(aTrabaja, "trabajo A"); var b = f.propuesta(); assertEquals("B", b.ordenId());
            ok(f.taller.confirmarInicio(b)); long inicio = System.nanoTime(); ambos.countDown();
            esperar(cerrar, "cierre B exterior"); long fin = System.nanoTime(); ok(f.taller.fallar("B"));
            return new Intervalo(Thread.currentThread().threadId(), Thread.currentThread().getName(), inicio, fin);
        });
        try {
            esperar(ambos, "dos trabajos exteriores"); var s = comprobar(f);
            assertEquals(2, s.dominio().asignaciones().size());
            assertTrue(Collections.disjoint(s.dominio().asignaciones().get("A").unidades(),
                    s.dominio().asignaciones().get("B").unidades()));
        } finally { cerrar.countDown(); }
        var ia = obtener(fa); var ib = obtener(fb);
        assertNotEquals(ia.hilo(), ib.hilo()); assertTrue(Math.max(ia.inicio(), ib.inicio()) < Math.min(ia.fin(), ib.fin()));
        System.out.println("H3-T03 intervalos monotónicos ns: " + ia + " / " + ib + " archivo=" + f.archivo);
        comprobar(f);
    }
    @ParameterizedTest @EnumSource(Precedencia.class)
    void t04_inicioCancelacion_CA13(Precedencia p) throws Exception {
        var f = new H3Fixture(p == Precedencia.LIBRE); f.capacidad(1);
        for (int i = 0; i < REPETICIONES; i++) {
            String id = "A" + i; f.orden(id); var a = f.propuesta();
            var r = carrera(f, p, () -> f.taller.confirmarInicio(a), () -> f.taller.cancelar(id));
            unGanador(r, p); var s = comprobar(f);
            r.stream().filter(x -> x.estado() == RECHAZADA).forEach(x -> sinEvento(s, x));
            if (r.get(0).estado() == CONFIRMADA) { assertEquals(a.recursos(), s.dominio().asignaciones().get(id)); ok(f.taller.finalizar(id)); }
            else { assertFalse(s.dominio().asignaciones().containsKey(id)); assertEquals(EstadoOrden.CANCELADA, s.dominio().ordenes().getLast().estado()); }
            comprobar(f);
        }
        System.out.println("H3-T04 " + p + " repeticiones=" + REPETICIONES + " archivo=" + f.archivo);
    }
    @ParameterizedTest @EnumSource(Precedencia.class)
    void t05_cierreCompetidorYReasignacion_CA14(Precedencia p) throws Exception {
        var f = new H3Fixture(p == Precedencia.LIBRE); f.capacidad(1); f.recurso(HERRAMIENTA, "M", MULTIMETRO, true);
        for (int i = 0; i < REPETICIONES; i++) {
            String a = "A" + i, b = "B" + i; f.orden(a, MULTIMETRO); f.orden(b, MULTIMETRO);
            var propuesta = f.propuesta(); ok(f.taller.confirmarInicio(propuesta));
            var anticipada = new PropuestaInicio(propuesta.contexto(), b, propuesta.recursos());
            // Tercer solicitante realmente concurrente con ambos cierres.
            var barrera = new CyclicBarrier(2);
            var inicioB = enviar(() -> { barrera.await(10, TimeUnit.SECONDS); return f.taller.confirmarInicio(anticipada); });
            var r = carrera(f, p, () -> {
                try { barrera.await(10, TimeUnit.SECONDS); } catch (Exception e) { throw new IllegalStateException(e); }
                return f.taller.finalizar(a);
            }, () -> f.taller.fallar(a));
            unGanador(r, p); var temprano = obtener(inicioB); var s = comprobar(f);
            r.stream().filter(x -> x.estado() == RECHAZADA).forEach(x -> sinEvento(s, x));
            if (temprano.estado() == RECHAZADA) { sinEvento(s, temprano); assertEquals(b, f.propuesta().ordenId()); ok(f.taller.confirmarInicio(f.propuesta())); }
            else assertEquals(CONFIRMADA, temprano.estado(), temprano::diagnostico);
            var antes = comprobar(f); assertEquals(propuesta.recursos(), antes.dominio().asignaciones().get(b));
            sinEvento(antes, f.taller.finalizar(a)); sinEvento(antes, f.taller.fallar(a));
            assertEquals(antes, comprobar(f)); ok(f.taller.finalizar(b)); comprobar(f);
        }
        System.out.println("H3-T05 " + p + " repeticiones=" + REPETICIONES + " archivo=" + f.archivo);
    }

    enum Lectura { INICIO_COMMIT, INICIO_ROLLBACK, CIERRE_ENTRE_DELETES }
    @ParameterizedTest @EnumSource(Lectura.class)
    void t06_lectoresNoVenEscrituraParcial_CA05(Lectura caso) throws Exception {
        var f = new H3Fixture(false); f.capacidad(1); f.recurso(HERRAMIENTA, "M", MULTIMETRO, true);
        f.orden("A", MULTIMETRO); var a = f.propuesta();
        if (caso == Lectura.CIERRE_ENTRE_DELETES) ok(f.taller.confirmarInicio(a));
        var antes = comprobar(f);
        var c = detener(f, caso == Lectura.CIERRE_ENTRE_DELETES ? "DELETE_HERRAMIENTAS" : "estado", caso == Lectura.INICIO_ROLLBACK);
        var escritor = enviar(() -> caso == Lectura.CIERRE_ENTRE_DELETES ? f.taller.finalizar("A") : f.taller.confirmarInicio(a));
        esperarPunto(c, escritor, f.archivo);
        var hilos = List.of(new AtomicReference<Thread>(), new AtomicReference<Thread>());
        var anunciados = new CountDownLatch(2); var lectores = new ArrayList<Future<ResultadoOperacion<InstantaneaTaller>>>();
        try {
            for (var hilo : hilos) lectores.add(enviar(() -> { hilo.set(Thread.currentThread()); anunciados.countDown(); return f.taller.consultar(); }));
            esperar(anunciados, "lectores lanzados"); hilos.forEach(h -> esperandoLock(h.get()));
            lectores.forEach(l -> assertFalse(l.isDone(), "Lector retornó mientras existía escritura parcial"));
        } finally { c.continuar.countDown(); }
        var resultado = obtener(escritor);
        assertEquals(caso == Lectura.INICIO_ROLLBACK ? ERROR_TECNICO : CONFIRMADA, resultado.estado(), resultado::diagnostico);
        var despues = comprobar(f);
        for (var lector : lectores) { var s = ok(obtener(lector)); assertEquals(despues, s); oraculo(s); }
        if (caso == Lectura.INICIO_ROLLBACK) assertEquals(antes, despues);
    }
    @Test void t07_registrosConcurrentesComplementaria_CA01() throws Exception {
        var f = new H3Fixture(true); var barrera = new CyclicBarrier(4);
        var ids = List.of("A", "B", "C", "C"); var futuros = new ArrayList<Future<ResultadoOperacion<Orden>>>();
        for (var id : ids) futuros.add(enviar(() -> { barrera.await(10, TimeUnit.SECONDS); return f.taller.registrarOrden(id, "Registro", List.of()); }));
        var resultados = new ArrayList<ResultadoOperacion<Orden>>(); for (var futuro : futuros) resultados.add(obtener(futuro));
        assertEquals(3, resultados.stream().filter(r -> r.estado() == CONFIRMADA).count());
        assertEquals(1, resultados.stream().filter(r -> r.estado() == RECHAZADA).count());
        var s = comprobar(f); assertEquals(List.of(1L, 2L, 3L), s.dominio().ordenes().stream().map(Orden::secuencia).toList());
        resultados.stream().filter(r -> r.estado() == RECHAZADA).forEach(r -> sinEvento(s, r));
    }

    @ParameterizedTest @EnumSource(Precedencia.class)
    void t08_prioridadHabilitadaDuranteInicio_CA03_CA10(Precedencia p) throws Exception {
        var f = new H3Fixture(p == Precedencia.LIBRE); f.capacidad(2);
        f.recurso(HERRAMIENTA, "D", TALADRO, false); f.orden("A", TALADRO); f.orden("B");
        var b = f.propuesta(); assertEquals("B", b.ordenId());
        var r = carrera(f, p, () -> f.taller.cambiarHabilitacion(new Recurso.Clave(HERRAMIENTA, "D"), true),
                () -> f.taller.confirmarInicio(b));
        assertEquals(CONFIRMADA, r.get(0).estado());
        if (p == Precedencia.PRIMERO) assertEquals(RECHAZADA, r.get(1).estado());
        if (p == Precedencia.SEGUNDO) assertEquals(CONFIRMADA, r.get(1).estado());
        var s = comprobar(f);
        if (r.get(1).estado() == RECHAZADA) sinEvento(s, r.get(1));
        else assertEquals(CONFIRMADA, r.get(1).estado());
    }
    @ParameterizedTest @EnumSource(Precedencia.class)
    void t08_unidadExactaFrenteAInhabilitacion_CA10_CA15(Precedencia p) throws Exception {
        // Cada categoría, nunca sustituir la unidad original por otra libre.
        for (var categoria : Recurso.Categoria.values()) {
            var f = new H3Fixture(p == Precedencia.LIBRE); f.capacidad(2);
            f.recurso(HERRAMIENTA, "M1", MULTIMETRO, true); f.recurso(HERRAMIENTA, "M2", MULTIMETRO, true);
            f.orden("A", MULTIMETRO); var a = f.propuesta();
            var clave = a.recursos().unidades().stream().filter(c -> c.categoria() == categoria).findFirst().orElseThrow();
            var r = carrera(f, p, () -> f.taller.cambiarHabilitacion(clave, false), () -> f.taller.confirmarInicio(a));
            unGanador(r, p); var s = comprobar(f);
            r.stream().filter(x -> x.estado() == RECHAZADA).forEach(x -> sinEvento(s, x));
            if (r.get(1).estado() == CONFIRMADA) {
                assertEquals(a.recursos(), s.dominio().asignaciones().get("A"));
                sinEvento(s, f.taller.cambiarHabilitacion(clave, true));
            } else assertTrue(s.dominio().asignaciones().isEmpty());
            comprobar(f);
        }
    }
    @ParameterizedTest @EnumSource(Precedencia.class)
    void t08_unidadAjenaMenorNoInvalida_CA10(Precedencia p) throws Exception {
        var f = new H3Fixture(p == Precedencia.LIBRE); f.capacidad(1);
        f.recurso(TECNICO, "T0", null, false); f.orden("A"); var a = f.propuesta();
        var r = carrera(f, p, () -> f.taller.cambiarHabilitacion(new Recurso.Clave(TECNICO, "T0"), true),
                () -> f.taller.confirmarInicio(a));
        r.forEach(x -> assertEquals(CONFIRMADA, x.estado(), x::diagnostico));
        assertEquals(a.recursos(), comprobar(f).dominio().asignaciones().get("A"));
    }
    @Test void t09_fachadaCompartidaNormalYAlias_H3D03() throws Exception {
        var f = new H3Fixture(true); var barrera = new CyclicBarrier(4);
        var referencias = new ArrayList<Future<ServicioTaller>>();
        for (int i = 0; i < 4; i++) {
            Path alias = i % 2 == 0 ? f.archivo : f.archivo.getParent().resolve("alias/../taller");
            referencias.add(enviar(() -> { barrera.await(10, TimeUnit.SECONDS); return TestContextos.servicio(alias); }));
        }
        var clientes = new ArrayList<ServicioTaller>();
        for (var referencia : referencias) { var t = obtener(referencia); assertSame(f.taller, t); clientes.add(t); }
        var registros = new ArrayList<Future<ResultadoOperacion<Orden>>>(); var salida = new CyclicBarrier(4);
        for (int i = 0; i < 4; i++) { final int id = i;
            registros.add(enviar(() -> { salida.await(10, TimeUnit.SECONDS); return clientes.get(id).registrarOrden("O" + id, "Alias", List.of()); }));
        }
        for (var registro : registros) ok(obtener(registro));
        assertEquals(4, comprobar(f).dominio().ordenes().size());
    }
    enum Commit { COMMIT_ANTES, COMMIT_DESPUES, ROLLBACK }
    @ParameterizedTest @EnumSource(Commit.class)
    void t11_saludCompartidaSinNuevasAperturas_PT06(Commit punto) throws Exception {
        var f = new H3Fixture(false); var c = detener(f, punto.name(), true);
        var incierto = enviar(() -> punto == Commit.ROLLBACK ? f.taller.consultar() : f.taller.registrarOrden("A", "Incierta", List.of()));
        esperarPunto(c, incierto, f.archivo);
        var hilos = List.of(new AtomicReference<Thread>(), new AtomicReference<Thread>());
        var anunciados = new CountDownLatch(2); var espera = new ArrayList<Future<ResultadoOperacion<?>>>();
        for (int i = 0; i < 2; i++) { final int n = i;
            espera.add(enviar(() -> { hilos.get(n).set(Thread.currentThread()); anunciados.countDown();
                return n == 0 ? f.taller.consultar() : f.taller.registrarOrden("B", "Bloqueada", List.of()); }));
        }
        try { esperar(anunciados, "solicitudes en espera"); hilos.forEach(h -> esperandoLock(h.get())); }
        finally { c.continuar.countDown(); }
        var r = obtener(incierto); assertEquals(PENDIENTE_VERIFICACION, r.estado()); assertNotNull(r.operacionId());
        for (var tarea : espera) { var bloqueada = obtener(tarea); assertEquals(PENDIENTE_VERIFICACION, bloqueada.estado());
            assertEquals(r.operacionId(), bloqueada.operacionId()); assertNull(bloqueada.valor()); }
        assertEquals(1, f.aperturas.get());
        assertEquals(punto == Commit.ROLLBACK ? 1 : 2, f.tecnicas.aperturas.get());
        assertEquals(punto == Commit.ROLLBACK ? 1 : 2, f.tecnicas.commits.get());
        assertEquals(0, f.tecnicas.rollbacks.get());
        // Sólo lectura auxiliar posoperaciones: no reconciliar ni desbloquear H4.
        try (var u = new UnidadTrabajoJdbc(f.url)) {
            var repo = u.repositorio(); var s = repo.cargar(TestContextos.abrir(f.archivo).identidad()); var h = repo.historial();
            assertEquals(punto == Commit.COMMIT_DESPUES ? 1 : 0, s.ordenes().size());
            assertEquals(punto == Commit.COMMIT_DESPUES ? 1 : 0, h.size());
            if (!h.isEmpty()) assertEquals(r.operacionId(), h.getFirst().operacionId()); u.rollback();
        }
        var sano = new H3Fixture(true); sano.orden("A"); comprobar(sano);
    }
}
