package ar.edu.unlam.reparabarrio.persistencia;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.aplicacion.puertos.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Ayuda compartida de ambas suites H3. Siempre archivo exclusivo bajo target.
 * Inyección exclusiva: nunca publica una fachada normal alternativa del fixture.
 */
public final class H3Fixture {
    public final Path archivo;
    public final String url;
    public final AtomicInteger aperturas = new AtomicInteger();
    public final TestContextos.ContadoresFase tecnicas = new TestContextos.ContadoresFase();
    public final AtomicReference<Control> control = new AtomicReference<>();
    public final UnidadTrabajo.Fabrica fabrica;
    public final ServicioTaller taller;

    public static final class Control {
        public final String punto;
        public final boolean error;
        public final CountDownLatch dentro = new CountDownLatch(1), continuar = new CountDownLatch(1);
        private final AtomicBoolean usado = new AtomicBoolean();
        public Control(String punto, boolean error) { this.punto = punto; this.error = error; }
        void detener(String actual) throws Exception {
            if (!punto.equals(actual) || !usado.compareAndSet(false, true)) return;
            dentro.countDown();
            esperar(continuar, "liberación de " + punto);
            if (error) throw new SQLException("Fallo H3 en " + punto);
        }
    }

    public H3Fixture(boolean normal) throws Exception { this(normal, new CoordinadorTaller(), null); }
    public H3Fixture(boolean normal, CoordinadorTaller coordinador, java.util.function.Consumer<TestContextos.Muestra> observador) throws Exception {
        Path target = Path.of("target").toAbsolutePath();
        Files.createDirectories(target);
        archivo = Files.createTempDirectory(target, "h4-h3-").resolve("taller");
        url = "jdbc:h2:file:" + archivo.toString().replace('\\', '/') + ";WRITE_DELAY=0;LOCK_TIMEOUT=5000";
        fabrica = () -> {
            aperturas.incrementAndGet();
            Connection real = DriverManager.getConnection(url, "sa", "");
            try {
                real.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                real.setAutoCommit(false);
                // Interceptar el primer DELETE real permite detener un cierre entre deletes.
                Connection conexion = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(),
                        new Class<?>[]{Connection.class}, (p, m, a) -> {
                            Object valor = invocar(real, m, a);
                            if (m.getName().equals("prepareStatement") && a[0] instanceof String sql
                                    && sql.startsWith("DELETE FROM herramientas_activas")) {
                                PreparedStatement stmt = (PreparedStatement) valor;
                                return Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),
                                        new Class<?>[]{PreparedStatement.class}, (q, n, b) -> {
                                            Object salida = invocar(stmt, n, b);
                                            if (n.getName().equals("executeUpdate")) punto("DELETE_HERRAMIENTAS");
                                            return salida;
                                        });
                            }
                            return valor;
                        });
                var repoReal = new RepositorioTallerJdbc(conexion);
                var repo = (RepositorioTaller) Proxy.newProxyInstance(RepositorioTaller.class.getClassLoader(),
                        new Class<?>[]{RepositorioTaller.class}, (p, m, a) -> {
                            Object valor = invocar(repoReal, m, a);
                            punto(m.getName());
                            return valor;
                        });
                return new UnidadTrabajo() {
                    public RepositorioTaller repositorio() { return repo; }
                    public RepositorioRecuperacion recuperacion() { return new RepositorioRecuperacionJdbc(real); }
                    public boolean terminada() { try { return real.isClosed(); } catch (SQLException e) { return false; } }
                    public void commit() throws Exception {
                        punto("COMMIT_ANTES"); real.commit(); punto("COMMIT_DESPUES");
                    }
                    public void rollback() throws Exception { punto("ROLLBACK"); real.rollback(); }
                    public void close() throws Exception { real.close(); punto("CIERRE"); }
                };
            } catch (Exception e) { real.close(); throw e; }
        };
        var propietario = TestContextos.abrir(archivo, normal ? java.util.function.UnaryOperator.identity() : ignorada -> fabrica,
                tecnicas::decorar, coordinador, observador, TestContextos.solicitud(archivo));
        assertEquals(ContextoTaller.Ciclo.HABILITADO, propietario.ciclo(), () -> "Precondición H3: " + propietario.informe());
        assertEquals(3, tecnicas.aperturas.get()); assertEquals(1, tecnicas.commits.get());
        assertEquals(2, tecnicas.rollbacks.get()); assertEquals(3, tecnicas.cierres.get()); tecnicas.reiniciar();
        taller = propietario.servicio();
    }
    private static Object invocar(Object destino, Method metodo, Object[] args) throws Throwable {
        try { return metodo.invoke(destino, args); }
        catch (InvocationTargetException e) { throw e.getCause(); }
    }
    private void punto(String nombre) throws Exception {
        var c = control.get(); if (c != null) c.detener(nombre);
    }
    public static <T> T ok(ResultadoOperacion<T> r) {
        assertEquals(ResultadoOperacion.Estado.CONFIRMADA, r.estado(), r::diagnostico); return r.valor();
    }
    public InstantaneaTaller vista() { return ok(taller.consultar()); }
    public PropuestaInicio propuesta() { return vista().planificacion().propuesta().orElseThrow(); }
    public void recurso(Recurso.Categoria categoria, String id, TipoHerramienta tipo, boolean habilitado) {
        ok(taller.registrarRecurso(categoria, id, tipo, habilitado));
    }
    public void capacidad(int n) {
        for (int i = 1; i <= n; i++) {
            recurso(Recurso.Categoria.TECNICO, "T" + i, null, true);
            recurso(Recurso.Categoria.PUESTO, "P" + i, null, true);
        }
    }
    public void orden(String id, TipoHerramienta... tipos) {
        ok(taller.registrarOrden(id, "Reparación H3", List.of(tipos)));
    }
    public void durable(InstantaneaTaller esperado) throws Exception {
        try (var u = new UnidadTrabajoJdbc(url)) {
            var r = u.repositorio();
            assertEquals(esperado.dominio(), r.cargar(esperado.dominio().contexto()), archivo.toString());
            assertEquals(esperado.historial(), r.historial());
            assertEquals(esperado.revision(), r.revision());
            assertEquals(esperado.catalogo(), r.catalogo());
            u.rollback();
        }
        try (var c = DriverManager.getConnection(url, "sa", ""); var st = c.createStatement();
             var rs = st.executeQuery("SELECT antiguedad FROM contador")) {
            assertTrue(rs.next());
            assertEquals(esperado.dominio().ordenes().stream().mapToLong(Orden::secuencia).max().orElse(0), rs.getLong(1));
        }
    }
    public static void esperar(CountDownLatch latch, String detalle) throws InterruptedException {
        assertTrue(latch.await(10, TimeUnit.SECONDS), () -> diagnostico(detalle));
    }
    public static <T> T obtener(Future<T> f) throws Exception {
        try { return f.get(10, TimeUnit.SECONDS); }
        catch (TimeoutException e) { fail(diagnostico("Future vencido"), e); return null; }
    }
    public static void esperarPunto(Control c, Future<?> escritor, Path archivo) throws Exception {
        long fin = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!c.dentro.await(10, TimeUnit.MILLISECONDS)) {
            if (escritor.isDone()) fail("No alcanzó " + c.punto + " en " + archivo
                    + "; escritor terminó: " + obtener(escritor));
            if (System.nanoTime() >= fin) fail(diagnostico("Punto " + c.punto + " no alcanzado en " + archivo));
        }
    }
    public static String diagnostico(String detalle) {
        var s = new StringBuilder(detalle);
        Thread.getAllStackTraces().forEach((t, pila) -> {
            s.append('\n').append(t.getName()).append(' ').append(t.getState());
            for (var marco : pila) s.append("\n  ").append(marco);
        });
        return s.toString();
    }
    /** OD-H3-02: requiere pila real en lockInterruptibly, no mero anuncio previo. */
    public static void esperandoLock(Thread hilo) {
        long fin = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < fin) {
            if (hilo != null && hilo.getState() == Thread.State.WAITING
                    && Arrays.stream(hilo.getStackTrace()).anyMatch(x -> x.getMethodName().equals("lockInterruptibly"))) return;
            Thread.onSpinWait();
        }
        fail(diagnostico("Hilo no observado esperando coordinador"));
    }
}
