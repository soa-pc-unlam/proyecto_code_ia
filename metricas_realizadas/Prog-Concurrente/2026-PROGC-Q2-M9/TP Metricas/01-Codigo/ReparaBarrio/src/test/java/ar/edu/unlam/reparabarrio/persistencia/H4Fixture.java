package ar.edu.unlam.reparabarrio.persistencia;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.aplicacion.puertos.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;

/** Infraestructura y negocio instrumentados por separado; ningún fallo cambia de fase. */
final class H4Fixture implements AutoCloseable {
    final Path base;
    final AtomicReference<Control> negocio = new AtomicReference<>(), infraestructura = new AtomicReference<>();
    final Contadores negocios = new Contadores(), tecnicas = new Contadores();
    final AtomicInteger conexiones = new AtomicInteger();
    final ContextoTaller contexto;
    final ServicioTaller servicio;
    private String jdbc;
    static final class Contadores {
        final AtomicInteger aperturas = new AtomicInteger(), commits = new AtomicInteger(), rollbacks = new AtomicInteger(), cierres = new AtomicInteger();
        void reiniciar() { aperturas.set(0); commits.set(0); rollbacks.set(0); cierres.set(0); }
    }
    static final class Control {
        final String punto;
        final AtomicBoolean usado = new AtomicBoolean();
        final boolean error, siempre;
        final Consumer<String> frontera;
        Control(String punto) { this(punto, true, false, s -> { }); }
        Control(String punto, boolean error, boolean siempre, Consumer<String> frontera) {
            this.punto = punto; this.error = error; this.siempre = siempre; this.frontera = frontera;
        }
        void pasar(String actual) throws SQLException {
            if (!punto.equals(actual) || (!siempre && !usado.compareAndSet(false, true))) return;
            EvidenciaH4.registrar("CONTROL", "punto=" + actual + " lanza=" + error);
            frontera.accept(actual);
            if (error) throw new SQLException("Fallo H4 " + actual, "H4001");
        }
    }
    H4Fixture() throws Exception { this(TestContextos.nuevaRaiz().resolve("taller"), null); }
    H4Fixture(Path base, InicializadorBase.Solicitud solicitud) throws Exception {
        this(base, solicitud, null);
    }
    H4Fixture(Path base, InicializadorBase.Solicitud solicitud, Control falloInfraestructura) throws Exception {
        this.base = TestContextos.validar(base);
        jdbc = InicializadorBase.url(this.base) + ";IFEXISTS=TRUE";
        infraestructura.set(falloInfraestructura);
        contexto = TestContextos.abrir(this.base, decorar(negocio, negocios, conexiones), decorar(infraestructura, tecnicas, conexiones),
                new CoordinadorTaller(), null, solicitud);
        servicio = contexto.servicio(); tecnicas.reiniciar();
        if (falloInfraestructura == null && (solicitud == null || solicitud.modo() == InicializadorBase.Modo.ABRIR_EXISTENTE))
            assertEquals(ContextoTaller.Ciclo.HABILITADO, contexto.ciclo(), () -> "Precondición del escenario: " + contexto.informe());
    }
    /** Sólo apoyo transaccional en memoria: conserva guardia de fixture y código real,
     * pero NO acredita inicialización de archivo ni caída de JVM. No expone otra API operativa. */
    H4Fixture(Path base, String jdbc, String identidad, Control fallo) throws Exception {
        if (!jdbc.equals("jdbc:h2:mem:" + identidad + ";WRITE_DELAY=0"))
            throw new IllegalArgumentException("Suplemento exclusivamente en memoria");
        this.base = TestContextos.validar(base); this.jdbc = jdbc; infraestructura.set(fallo);
        var guardia = ar.edu.unlam.reparabarrio.arranque.GuardiaInstancia.adquirir(this.base.getParent());
        try {
            var constructor = PersistenciaH2.Almacen.class.getDeclaredConstructor(
                    ar.edu.unlam.reparabarrio.arranque.GuardiaInstancia.class, String.class, String.class);
            constructor.setAccessible(true);
            var almacen = constructor.newInstance(guardia, identidad, jdbc);
            var componer = Arrays.stream(ContextoTaller.class.getDeclaredMethods())
                    .filter(m -> m.getName().equals("componerPrueba")).findFirst().orElseThrow();
            componer.setAccessible(true);
            contexto = (ContextoTaller) componer.invoke(null, almacen, (Consumer<ContextoTaller>) c -> { },
                    new CoordinadorTaller(), decorar(negocio, negocios, conexiones), decorar(infraestructura, tecnicas, conexiones), null);
            servicio = contexto.servicio(); tecnicas.reiniciar();
        } catch (Exception e) { guardia.close(); throw e; }
    }
    static UnaryOperator<UnidadTrabajo.Fabrica> decorar(AtomicReference<Control> control, Contadores cuenta, AtomicInteger conexiones) {
        return fabrica -> () -> {
            cuenta.aperturas.incrementAndGet(); punto(control, "APERTURA");
            assertEquals(0, conexiones.get(), "Dos unidades simultáneas en el mismo propietario");
            UnidadTrabajo abierta = fabrica.abrir();
            UnidadTrabajo real;
            try { real = EvidenciaH4.decorar(abierta); }
            catch (Exception e) { throw new UnidadTrabajo.AperturaIncompleta(abierta, e); }
            conexiones.incrementAndGet(); var cerrada = new AtomicBoolean();
            return new UnidadTrabajo() {
                public RepositorioTaller repositorio() { return proxy(RepositorioTaller.class, real.repositorio(), control, ""); }
                public RepositorioRecuperacion recuperacion() { return proxy(RepositorioRecuperacion.class, real.recuperacion(), control, "RR_"); }
                public void commit() throws Exception {
                    cuenta.commits.incrementAndGet(); punto(control, "COMMIT_ANTES");
                    punto(control, "COMMIT_INVOCADO"); real.commit(); punto(control, "COMMIT_DESPUES");
                }
                public void rollback() throws Exception { cuenta.rollbacks.incrementAndGet(); punto(control, "ROLLBACK"); real.rollback(); }
                public void close() throws Exception {
                    cuenta.cierres.incrementAndGet();
                    try { punto(control, "CIERRE_ANTES"); real.close(); punto(control, "CIERRE_DESPUES"); }
                    finally { if (real.terminada() && cerrada.compareAndSet(false, true)) conexiones.decrementAndGet(); }
                }
                public boolean terminada() { return real.terminada(); }
            };
        };
    }
    private static void punto(AtomicReference<Control> c, String punto) throws SQLException { var control = c.get(); if (control != null) control.pasar(punto); }
    @SuppressWarnings("unchecked") private static <T> T proxy(Class<T> tipo, T real, AtomicReference<Control> c, String prefijo) {
        return (T) Proxy.newProxyInstance(tipo.getClassLoader(), new Class<?>[]{tipo}, (p, m, a) -> {
            String punto = prefijo + m.getName().toUpperCase(Locale.ROOT);
            punto(c, punto + "_ANTES");
            Object resultado;
            try { resultado = m.invoke(real, a); } catch (InvocationTargetException e) { throw e.getCause(); }
            if (punto.equals("RR_PREPARAR") && a[0] instanceof RepositorioRecuperacion.Intento i
                    && c.get() != null && !c.get().error) { System.out.println("INTENTO=" + i.id()); System.out.flush(); }
            punto(c, punto); return resultado;
        });
    }
    static <T> T ok(ResultadoOperacion<T> r) {
        if (r.estado() != ResultadoOperacion.Estado.CONFIRMADA)
            EvidenciaH4.registrar("RESULTADO", "contexto=" + r.contexto() + " intento=" + r.operacionId()
                    + " estado=" + r.estado() + " tecnico=" + r.tecnico());
        assertEquals(ResultadoOperacion.Estado.CONFIRMADA, r.estado(), r::diagnostico); return r.valor();
    }
    InstantaneaTaller vista() { return ok(servicio.consultar()); }
    PropuestaInicio propuesta() { return vista().planificacion().propuesta().orElseThrow(); }
    void capacidad(int cantidad) {
        for (int i = 1; i <= cantidad; i++) {
            ok(servicio.registrarRecurso(Recurso.Categoria.TECNICO, "T" + i, null, true));
            ok(servicio.registrarRecurso(Recurso.Categoria.PUESTO, "P" + i, null, true));
        }
    }
    void orden(String id, TipoHerramienta... tipos) { ok(servicio.registrarOrden(id, "H4 " + id, List.of(tipos))); }
    void herramienta(String id, TipoHerramienta tipo, boolean habilitada) { ok(servicio.registrarRecurso(Recurso.Categoria.HERRAMIENTA, id, tipo, habilitada)); }
    long numero(String sql) throws Exception {
        try (var c = conexion(); var st = c.createStatement(); var r = st.executeQuery(sql)) { assertTrue(r.next()); return r.getLong(1); }
    }
    Connection conexion() throws Exception { TestContextos.validar(base); return DriverManager.getConnection(jdbc, "sa", ""); }
    void evidencia(String fase) throws Exception {
        if (!Boolean.getBoolean("h4.evidencia")) return;
        EvidenciaH4.registrar(fase, "base=" + base + " contexto=" + contexto.identidad()
                + " sesion=" + contexto.informe().sesionId() + " ciclo=" + contexto.ciclo());
        try (var c = conexion()) { EvidenciaH4.mostrar(c, fase + " INDEPENDIENTE"); }
    }
    void verificarSesionPersistida() throws Exception {
        String sesion = contexto.informe().sesionId(); assertNotNull(sesion);
        try (var c = conexion(); var p = c.prepareStatement("SELECT base_id,abierta FROM rb_sesiones WHERE id=?")) {
            p.setString(1, sesion);
            try (var r = p.executeQuery()) {
                assertTrue(r.next(), "Sesión publicada ausente al reabrir: " + sesion);
                assertEquals(contexto.identidad(), r.getString(1)); assertTrue(r.getBoolean(2)); assertFalse(r.next());
            }
        }
    }
    void escenario() {
        capacidad(3); herramienta("M", TipoHerramienta.MULTIMETRO, true); herramienta("D", TipoHerramienta.TALADRO, true); herramienta("S", TipoHerramienta.SOLDADOR, false);
        orden("FINAL"); ok(servicio.confirmarInicio(propuesta())); ok(servicio.finalizar("FINAL"));
        orden("FALLO"); ok(servicio.confirmarInicio(propuesta())); ok(servicio.fallar("FALLO"));
        orden("CANCEL"); ok(servicio.cancelar("CANCEL"));
        orden("CURSO1", TipoHerramienta.MULTIMETRO, TipoHerramienta.TALADRO); ok(servicio.confirmarInicio(propuesta()));
        orden("CURSO2"); ok(servicio.confirmarInicio(propuesta())); orden("PENDIENTE", TipoHerramienta.SOLDADOR);
    }
    /** Oráculo de filas independiente: no usa el auditor ni la huella funcional de producción. */
    String filas() throws Exception { return OraculoH4.filas(base); }
    @Override public void close() throws Exception { negocio.set(null); infraestructura.set(null); contexto.close(); }
}
