package ar.edu.unlam.reparabarrio.persistencia;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.aplicacion.puertos.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static ar.edu.unlam.reparabarrio.aplicacion.ResultadoOperacion.Estado.*;
import static ar.edu.unlam.reparabarrio.dominio.Recurso.Categoria.*;
import static ar.edu.unlam.reparabarrio.dominio.TipoHerramienta.*;

/** CA-01/02/03/05/06/10/11/12/15/19; CA-16/17 parcialmente.
 * Archivos exclusivos bajo target; sin carreras H3 ni recuperación de caída H4.
 */
class TransaccionesH2Test {
    private Path archivo;
    private String url;
    private ServicioTaller taller;
    private final TestContextos.ContadoresFase tecnicas = new TestContextos.ContadoresFase();
    @BeforeEach void preparar() throws Exception {
        var target = Path.of("target").toAbsolutePath();
        Files.createDirectories(target);
        archivo = Files.createTempDirectory(target, "h4-h2-").resolve("taller");
        url = "jdbc:h2:file:" + archivo.toString().replace('\\', '/') + ";WRITE_DELAY=0;LOCK_TIMEOUT=5000";
        taller = TestContextos.abrir(archivo, java.util.function.UnaryOperator.identity(), tecnicas::decorar,
                new CoordinadorTaller(), null, TestContextos.solicitud(archivo)).servicio();
        assertEquals(ContextoTaller.Ciclo.HABILITADO, TestContextos.abrir(archivo).ciclo(), () -> "Precondición H2: " + taller.consultar().diagnostico());
        assertEquals(3, tecnicas.aperturas.get()); assertEquals(1, tecnicas.commits.get());
        assertEquals(2, tecnicas.rollbacks.get()); assertEquals(3, tecnicas.cierres.get()); tecnicas.reiniciar();
    }
    @AfterEach void cerrarContextos() throws Exception { TestContextos.cerrarTodos(); }
    private <T> T confirmado(ResultadoOperacion<T> r) {
        assertEquals(CONFIRMADA, r.estado(), r::diagnostico);
        return r.valor();
    }
    private InstantaneaTaller vista() { return confirmado(taller.consultar()); }
    private PropuestaInicio propuesta() { return vista().planificacion().propuesta().orElseThrow(); }
    private void recurso(Recurso.Categoria categoria, String id, TipoHerramienta tipo) {
        confirmado(taller.registrarRecurso(categoria, id, tipo, true));
    }
    private void basicos() { recurso(TECNICO, "T2", null); recurso(PUESTO, "P2", null); }
    private void registrar(String id, TipoHerramienta... tipos) {
        confirmado(taller.registrarOrden(id, "Reparación", List.of(tipos)));
    }
    private void sinEfectos(java.util.function.Supplier<ResultadoOperacion<?>> intento) {
        var antes = vista();
        assertEquals(RECHAZADA, intento.get().estado());
        assertEquals(antes, vista());
    }
    private long numero(String sql) throws Exception {
        try (var c = DriverManager.getConnection(url, "sa", ""); var st = c.createStatement(); var rs = st.executeQuery(sql)) {
            assertTrue(rs.next()); return rs.getLong(1);
        }
    }
    @Test void registroValidacionCatalogoAmbitosYSecuencia_CA01_CA02() throws Exception {
        assertEquals(Set.of(MULTIMETRO, SOLDADOR, TALADRO), vista().catalogo());
        assertTrue(vista().dominio().recursos().isEmpty());
        recurso(TECNICO, "T2", null); recurso(PUESTO, "T2", null);
        registrar("O1", TALADRO); // Tipo conocido sin unidad: registro permitido.
        sinEfectos(() -> taller.registrarOrden("O1", "Duplicada", List.of()));
        sinEfectos(() -> taller.registrarOrden(" O2", "x", List.of()));
        sinEfectos(() -> taller.registrarOrden("O2", " ", List.of()));
        sinEfectos(() -> taller.registrarOrden("O2", "x", List.of(TALADRO, TALADRO)));
        sinEfectos(() -> taller.registrarOrden("O2", "x", Arrays.asList((TipoHerramienta) null)));
        sinEfectos(() -> taller.registrarRecurso(TECNICO, "T2", null, false));
        sinEfectos(() -> taller.registrarRecurso(HERRAMIENTA, "H", null, true));
        sinEfectos(() -> taller.registrarRecurso(PUESTO, "P", TALADRO, true));
        sinEfectos(() -> taller.registrarRecurso(TECNICO, " T3", null, true));
        registrar("O2");
        assertEquals(List.of(1L, 2L), vista().dominio().ordenes().stream().map(Orden::secuencia).toList());
        assertEquals(2, numero("SELECT antiguedad FROM contador"));
        confirmado(taller.cancelar("O1"));
        sinEfectos(() -> taller.registrarOrden("O1", "Reutilizada", List.of()));
        confirmado(taller.cambiarHabilitacion(new Recurso.Clave(TECNICO, "T2"), false));
        sinEfectos(() -> taller.registrarRecurso(TECNICO, "T2", null, true));
    }
    @Test void primeraFactibleSinReservasNiSalteos_CA03_CA06_CA19() {
        basicos(); registrar("A", TALADRO); registrar("B"); registrar("C");
        var antes = vista(); var p = propuesta();
        assertEquals("B", p.ordenId());
        assertEquals(List.of("Sin unidad disponible de TALADRO"), antes.planificacion().causas().get("A"));
        assertEquals(List.of("Bloqueada por primera factible B"), antes.planificacion().causas().get("C"));
        assertEquals(antes, vista()); assertEquals(p, propuesta());
        assertTrue(vista().dominio().asignaciones().isEmpty());
        sinEfectos(() -> taller.confirmarInicio(new PropuestaInicio(p.contexto(), "C", p.recursos())));
        recurso(HERRAMIENTA, "D", TALADRO);
        sinEfectos(() -> taller.confirmarInicio(p));
        assertEquals("A", propuesta().ordenId());
    }
    @Test void inicioCompletoYMenorAjenaNoInvalida_CA05_CA10_CA15() throws Exception {
        basicos(); recurso(HERRAMIENTA, "D", TALADRO); recurso(HERRAMIENTA, "M", MULTIMETRO);
        registrar("A", TALADRO, MULTIMETRO);
        var p = propuesta();
        recurso(TECNICO, "T1", null); // No sustituye la unidad original.
        assertEquals("T1", propuesta().recursos().tecnico().id());
        var inicio = taller.confirmarInicio(p); confirmado(inicio);
        var s = vista();
        assertEquals(EstadoOrden.EN_CURSO, s.dominio().ordenes().getFirst().estado());
        assertEquals(p.recursos(), s.dominio().asignaciones().get("A"));
        for (var clave : p.recursos().unidades()) assertEquals(Recurso.Disponibilidad.OCUPADO, s.dominio().disponibilidad(clave));
        var evento = s.historial().getLast();
        assertEquals("INICIO", evento.accion()); assertEquals(inicio.operacionId(), evento.operacionId());
        assertEquals(p.recursos().unidades(), evento.recursos());
        assertEquals(1, numero("SELECT COUNT(*) FROM asignaciones"));
        assertEquals(2, numero("SELECT COUNT(*) FROM herramientas_activas"));
        sinEfectos(() -> taller.confirmarInicio(p));
        sinEfectos(() -> taller.cambiarHabilitacion(p.recursos().tecnico(), false));
        sinEfectos(() -> taller.cambiarHabilitacion(p.recursos().puesto(), true));
        sinEfectos(() -> taller.cancelar("A"));
    }
    @Test void exactasInhabilitadasFaltantesTipoIncorrectoYContexto_CA10() {
        basicos(); recurso(TECNICO, "T3", null); recurso(HERRAMIENTA, "D", TALADRO); recurso(HERRAMIENTA, "M", MULTIMETRO);
        registrar("A", TALADRO); var p = propuesta();
        sinEfectos(() -> taller.confirmarInicio(new PropuestaInicio("AJENO", "A", p.recursos())));
        sinEfectos(() -> taller.confirmarInicio(new PropuestaInicio(p.contexto(), "A", new SeleccionRecursos(
                p.recursos().tecnico(), p.recursos().puesto(), Map.of()))));
        sinEfectos(() -> taller.confirmarInicio(new PropuestaInicio(p.contexto(), "A", new SeleccionRecursos(
                p.recursos().tecnico(), p.recursos().puesto(), Map.of(TALADRO, new Recurso.Clave(HERRAMIENTA, "M"))))));
        sinEfectos(() -> taller.confirmarInicio(new PropuestaInicio(p.contexto(), "A", new SeleccionRecursos(
                new Recurso.Clave(TECNICO, "INEXISTENTE"), p.recursos().puesto(), p.recursos().herramientas()))));
        confirmado(taller.cambiarHabilitacion(p.recursos().tecnico(), false));
        assertEquals("T3", propuesta().recursos().tecnico().id());
        sinEfectos(() -> taller.confirmarInicio(p));
        confirmado(taller.cancelar("A"));
        sinEfectos(() -> taller.confirmarInicio(p));
    }
    @Test void unidadExactaOcupadaNoSeSustituye_CA10() {
        basicos(); recurso(TECNICO, "T3", null); recurso(PUESTO, "P3", null);
        registrar("A"); registrar("B"); var a = propuesta(); confirmado(taller.confirmarInicio(a));
        var b = propuesta();
        sinEfectos(() -> taller.confirmarInicio(new PropuestaInicio(b.contexto(), "B", a.recursos())));
        confirmado(taller.confirmarInicio(b));
        assertEquals(2, vista().dominio().asignaciones().size()); // Sin trabajo superpuesto H3.
    }
    @ParameterizedTest @EnumSource(value = EstadoOrden.class, names = {"FINALIZADA", "FALLIDA"})
    void cierreLiberaSoloPropiasYRechazaRepeticiones_CA11(EstadoOrden destino) {
        basicos(); recurso(HERRAMIENTA, "D", TALADRO); registrar("A", TALADRO); registrar("B", TALADRO);
        var p = propuesta(); confirmado(taller.confirmarInicio(p));
        var cierre = destino == EstadoOrden.FINALIZADA ? taller.finalizar("A") : taller.fallar("A");
        confirmado(cierre);
        var s = vista(); assertTrue(s.dominio().asignaciones().isEmpty());
        assertEquals(destino, s.dominio().ordenes().getFirst().estado());
        assertEquals(p.recursos().unidades(), s.historial().getLast().recursos());
        assertEquals(destino.name(), s.historial().getLast().accion());
        confirmado(taller.confirmarInicio(propuesta()));
        sinEfectos(() -> taller.finalizar("A")); sinEfectos(() -> taller.fallar("A")); sinEfectos(() -> taller.cancelar("A"));
        assertEquals(p.recursos(), vista().dominio().asignaciones().get("B"));
    }
    @Test void cancelacionYHabilitacionLibre_CA02_CA12() {
        basicos(); registrar("A"); registrar("B");
        sinEfectos(() -> taller.finalizar("A")); sinEfectos(() -> taller.fallar("A"));
        confirmado(taller.cancelar("A"));
        assertEquals(EstadoOrden.CANCELADA, vista().dominio().ordenes().getFirst().estado());
        assertTrue(vista().historial().getLast().recursos().isEmpty());
        sinEfectos(() -> taller.cancelar("A")); sinEfectos(() -> taller.cancelar("NO_EXISTE"));
        var clave = new Recurso.Clave(PUESTO, "P2");
        confirmado(taller.cambiarHabilitacion(clave, false));
        assertEquals(Recurso.Disponibilidad.FUERA_DE_SERVICIO, vista().dominio().disponibilidad(clave));
        var antes = vista(); assertEquals(SIN_CAMBIO, taller.cambiarHabilitacion(clave, false).estado()); assertEquals(antes, vista());
        assertTrue(vista().planificacion().propuesta().isEmpty());
        confirmado(taller.cambiarHabilitacion(clave, true));
        confirmado(taller.confirmarInicio(propuesta()));
        sinEfectos(() -> taller.cambiarHabilitacion(clave, false));
    }
    @Test void reaperturaLimpiaMismaBase_CA16parcial() throws Exception {
        basicos(); recurso(HERRAMIENTA, "D", TALADRO); registrar("A", TALADRO); registrar("B");
        confirmado(taller.confirmarInicio(propuesta()));
        var antes = vista();
        assertTrue(Files.exists(Path.of(archivo + ".mv.db")));
        // Ninguna conexión permanente: cada consulta vuelve a abrir el archivo.
        try (var u = new UnidadTrabajoJdbc(url)) {
            assertEquals(antes.dominio(), u.repositorio().cargar(antes.dominio().contexto()));
            assertEquals(antes.historial(), u.repositorio().historial());
            assertEquals(antes.catalogo(), u.repositorio().catalogo());
            u.rollback();
        }
        taller = TestContextos.servicio(archivo);
        assertEquals(antes, vista());
        registrar("C"); assertEquals(3L, vista().dominio().ordenes().getLast().secuencia());
    }

    enum Fallo { REGISTRO, ASIGNACION, EVENTO, COMMIT_ANTES, COMMIT_DESPUES, ROLLBACK, CIERRE }
    private ServicioTaller conFallo(Fallo fallo, AtomicInteger aperturas, AtomicInteger commits, AtomicInteger rollbacks) throws Exception {
        TestContextos.abrir(archivo).close();
        var falloDisponible = new java.util.concurrent.atomic.AtomicBoolean(true);
        taller = TestContextos.abrir(archivo, ignorada -> () -> {
            aperturas.incrementAndGet();
            var real = new UnidadTrabajoJdbc(url);
            var repo = (RepositorioTaller) Proxy.newProxyInstance(RepositorioTaller.class.getClassLoader(), new Class<?>[]{RepositorioTaller.class},
                    (proxy, metodo, args) -> {
                        Object valor;
                        try { valor = metodo.invoke(real.repositorio(), args); }
                        catch (InvocationTargetException e) { throw e.getCause(); }
                        if ((fallo == Fallo.REGISTRO && metodo.getName().equals("registrar")
                                || fallo == Fallo.ASIGNACION && metodo.getName().equals("asignar")
                                || fallo == Fallo.EVENTO && metodo.getName().equals("evento")) && falloDisponible.compareAndSet(true, false))
                            throw new SQLException("Fallo inyectado DESPUES de escritura real");
                        return valor;
                    });
            return new UnidadTrabajo() {
                public RepositorioTaller repositorio() { return repo; }
                public RepositorioRecuperacion recuperacion() { return real.recuperacion(); }
                public boolean terminada() { return real.terminada(); }
                public void commit() throws Exception {
                    commits.incrementAndGet();
                    if (fallo == Fallo.COMMIT_ANTES && falloDisponible.compareAndSet(true, false)) throw new SQLException("Commit simulado incierto antes del delegate");
                    real.commit();
                    if (fallo == Fallo.COMMIT_DESPUES && falloDisponible.compareAndSet(true, false)) throw new SQLException("Commit simulado incierto después del delegate");
                }
                public void rollback() throws Exception {
                    rollbacks.incrementAndGet();
                    if (fallo == Fallo.ROLLBACK && falloDisponible.compareAndSet(true, false)) throw new SQLException("Rollback simulado incierto");
                    real.rollback();
                }
                public void close() throws Exception {
                    real.close();
                    if (fallo == Fallo.CIERRE && falloDisponible.compareAndSet(true, false)) throw new SQLException("Cierre simulado después de commit confirmado");
                }
            };
        }, tecnicas::decorar, new CoordinadorTaller(), null, TestContextos.solicitud(archivo)).servicio();
        tecnicas.reiniciar();
        return taller;
    }
    @ParameterizedTest @EnumSource(value = Fallo.class, names = {"REGISTRO", "ASIGNACION", "EVENTO"})
    void rollbackRealTrasEscriturasConConexionNueva_CA17parcial(Fallo fallo) throws Exception {
        basicos(); recurso(HERRAMIENTA, "D", TALADRO); registrar("A", TALADRO);
        var antes = vista();
        var propuestaAnterior = propuesta();
        var commits = new AtomicInteger(); var rollbacks = new AtomicInteger();
        var defectuoso = conFallo(fallo, new AtomicInteger(), commits, rollbacks);
        var resultado = fallo == Fallo.REGISTRO ? defectuoso.registrarOrden("B", "x", List.of()) : defectuoso.confirmarInicio(propuestaAnterior);
        assertEquals(ERROR_TECNICO, resultado.estado()); assertEquals(0, commits.get()); assertEquals(1, rollbacks.get());
        assertEquals(antes, vista()); // Abre una conexión nueva sobre H2 de archivo.
        assertEquals(0, numero("SELECT COUNT(*) FROM asignaciones"));
        assertEquals(0, numero("SELECT COUNT(*) FROM herramientas_activas"));
        assertEquals(1, numero("SELECT antiguedad FROM contador"));
        assertEquals(0, numero("SELECT COUNT(*) FROM historial WHERE operacion_id='" + resultado.operacionId() + "'"));
        registrar("B"); assertEquals(2L, vista().dominio().ordenes().getLast().secuencia());
    }
    @ParameterizedTest @EnumSource(value = Fallo.class, names = {"COMMIT_ANTES", "COMMIT_DESPUES", "ROLLBACK"})
    void incertidumbreBloqueaSinReintentarYOtroContextoSigue_CA17parcial(Fallo fallo) throws Exception {
        var aperturas = new AtomicInteger(); var commits = new AtomicInteger(); var rollbacks = new AtomicInteger();
        var defectuoso = conFallo(fallo, aperturas, commits, rollbacks);
        var resultado = fallo == Fallo.ROLLBACK ? defectuoso.consultar() : defectuoso.registrarOrden("I", "x", List.of());
        assertEquals(PENDIENTE_VERIFICACION, resultado.estado()); assertNull(resultado.valor());
        assertNotNull(resultado.operacionId());
        assertEquals(1, aperturas.get());
        assertEquals(fallo == Fallo.ROLLBACK ? 0 : 1, commits.get());
        assertEquals(fallo == Fallo.ROLLBACK ? 1 : 0, rollbacks.get());
        var bloqueada = defectuoso.registrarOrden("OTRA", "x", List.of());
        assertEquals(PENDIENTE_VERIFICACION, bloqueada.estado()); assertEquals(resultado.operacionId(), bloqueada.operacionId());
        assertEquals(PENDIENTE_VERIFICACION, defectuoso.consultar().estado());
        assertEquals(1, aperturas.get()); // Sin nueva transacción, reintento ni rollback ciego.
        assertEquals(fallo == Fallo.ROLLBACK ? 1 : 2, tecnicas.aperturas.get());
        assertEquals(fallo == Fallo.ROLLBACK ? 1 : 2, tecnicas.commits.get()); assertEquals(0, tecnicas.rollbacks.get());
        if (fallo == Fallo.COMMIT_DESPUES) {
            assertEquals(1, numero("SELECT COUNT(*) FROM ordenes WHERE id='I'"));
            assertEquals(1, numero("SELECT COUNT(*) FROM historial WHERE operacion_id='" + resultado.operacionId() + "'"));
        } else assertEquals(0, numero("SELECT COUNT(*) FROM ordenes"));
        var otro = TestContextos.servicio(TestContextos.nuevaRaiz().resolve("taller"));
        confirmado(otro.registrarOrden("I", "Independiente", List.of()));
    }
    @Test void falloDeCierreNoConvierteCommitConfirmadoEnRechazo() throws Exception {
        var commits = new AtomicInteger();
        var defectuoso = conFallo(Fallo.CIERRE, new AtomicInteger(), commits, new AtomicInteger());
        var resultado = defectuoso.registrarOrden("A", "x", List.of());
        confirmado(resultado); assertEquals(1, commits.get());
        assertTrue(resultado.diagnostico().contains("Fallo al cerrar conexión"));
        assertEquals(1, numero("SELECT COUNT(*) FROM historial WHERE operacion_id='" + resultado.operacionId() + "'"));
    }
    @Test void restriccionesSQLDefiendenExclusividadYReferencias() throws Exception {
        basicos(); registrar("A"); registrar("B"); confirmado(taller.confirmarInicio(propuesta()));
        try (var c = DriverManager.getConnection(url, "sa", "")) {
            c.setAutoCommit(false);
            try (var p = c.prepareStatement("INSERT INTO asignaciones VALUES('B','T2','P2')")) {
                assertThrows(SQLException.class, p::executeUpdate);
            }
            try (var p = c.prepareStatement("INSERT INTO herramientas_activas VALUES('A','TALADRO','NO_EXISTE')")) {
                assertThrows(SQLException.class, p::executeUpdate);
            }
            c.rollback();
        }
        assertEquals(Set.of("A"), vista().dominio().asignaciones().keySet());
    }
}
