package ar.edu.unlam.reparabarrio.aplicacion;

import ar.edu.unlam.reparabarrio.aplicacion.puertos.*;
import ar.edu.unlam.reparabarrio.arranque.GuardiaInstancia;
import ar.edu.unlam.reparabarrio.persistencia.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.*;

/** Única composición inyectable, sólo en tests y raíces acreditadas target/h4-*. */
public final class TestContextos {
    private static final Map<Path, ContextoTaller> CONTEXTOS = new HashMap<>();
    public record Muestra(String contexto, String intentoId, long hiloId, String hiloNombre,
                          boolean adquirido, long esperaNanos, Long posesionNanos) { }
    public static final class ContadoresFase {
        public final java.util.concurrent.atomic.AtomicInteger aperturas = new java.util.concurrent.atomic.AtomicInteger(),
                commits = new java.util.concurrent.atomic.AtomicInteger(), rollbacks = new java.util.concurrent.atomic.AtomicInteger(),
                cierres = new java.util.concurrent.atomic.AtomicInteger();
        public UnidadTrabajo.Fabrica decorar(UnidadTrabajo.Fabrica fabrica) {
            return () -> {
                aperturas.incrementAndGet(); var real = fabrica.abrir();
                return new UnidadTrabajo() {
                    public RepositorioTaller repositorio() { return real.repositorio(); }
                    public RepositorioRecuperacion recuperacion() { return real.recuperacion(); }
                    public boolean terminada() { return real.terminada(); }
                    public void commit() throws Exception { commits.incrementAndGet(); real.commit(); }
                    public void rollback() throws Exception { rollbacks.incrementAndGet(); real.rollback(); }
                    public void close() throws Exception { cierres.incrementAndGet(); real.close(); }
                };
            };
        }
        public void reiniciar() { aperturas.set(0); commits.set(0); rollbacks.set(0); cierres.set(0); }
    }
    private TestContextos() { }
    public static Path nuevaRaiz() throws Exception {
        Path target = Path.of("target").toAbsolutePath(); Files.createDirectories(target);
        return Files.createTempDirectory(target, "h4-");
    }
    public static Path validar(Path archivo) throws Exception {
        if (!archivo.isAbsolute() || archivo.toString().contains(";") || !archivo.normalize().getFileName().toString().equals("taller"))
            throw new IllegalArgumentException("Fixture fijo absoluto requerido");
        Path real = archivo.normalize().getParent().toRealPath();
        Path target = Path.of("target").toAbsolutePath().toRealPath();
        Path relativa = target.relativize(real);
        if (!real.startsWith(target) || relativa.getNameCount() < 1 || !relativa.getName(0).toString().startsWith("h4-"))
            throw new IllegalArgumentException("Fixture fuera de target/h4-*");
        if (Files.isSymbolicLink(archivo) || Files.isSymbolicLink(Path.of(archivo + ".mv.db"))) throw new IllegalArgumentException("Base enlazada");
        return real.resolve("taller");
    }
    public static InicializadorBase.Solicitud solicitud(Path archivo) throws Exception {
        archivo = validar(archivo);
        try (var guardia = GuardiaInstancia.adquirir(archivo.getParent())) { return solicitud(archivo, guardia); }
    }
    private static InicializadorBase.Solicitud solicitud(Path archivo, GuardiaInstancia guardia) throws Exception {
        if (!guardia.vigente() || !guardia.directorio().equals(archivo.getParent()))
            throw new IllegalStateException("Huella de fixture requiere exclusión del almacenamiento");
        String huella = InicializadorBase.huella(archivo); boolean nueva = huella.equals("AUSENTE");
        var a = new AutorizacionBase(UUID.randomUUID().toString(), archivo, huella, nueva ? 0 : 2,
                2, null, true, false, "Autorizo exclusivamente fixture descartable H4, incluyendo recuperación física y sesión");
        return new InicializadorBase.Solicitud(nueva ? InicializadorBase.Modo.CREAR_NUEVA : InicializadorBase.Modo.ABRIR_EXISTENTE, a);
    }
    public static synchronized ContextoTaller abrir(Path archivo) throws Exception {
        // Reutilizar el propietario antes de inspeccionar bytes de un H2 posiblemente abierto.
        var existente = CONTEXTOS.get(validar(archivo));
        if (existente != null) return existente;
        return abrir(archivo, UnaryOperator.identity(), UnaryOperator.identity(), new CoordinadorTaller(), null, null);
    }
    public static synchronized ContextoTaller abrir(Path archivo, UnaryOperator<UnidadTrabajo.Fabrica> negocio,
                                                   UnaryOperator<UnidadTrabajo.Fabrica> infraestructura,
                                                   CoordinadorTaller coordinador, Consumer<Muestra> observador,
                                                   InicializadorBase.Solicitud solicitud) throws Exception {
        Path base = validar(archivo); var existente = CONTEXTOS.get(base);
        if (existente != null) return existente;
        var guardia = GuardiaInstancia.adquirir(base.getParent());
        try {
            var almacen = PersistenciaH2.preparar(guardia, solicitud == null ? solicitud(base, guardia) : solicitud);
            var c = ContextoTaller.componerPrueba(almacen, cerrado -> retirar(base, cerrado), coordinador, negocio, infraestructura,
                    observador == null ? null : m -> observador.accept(new Muestra(m.contexto(), m.intentoId(), m.hiloId(), m.hiloNombre(), m.adquirido(), m.esperaNanos(), m.posesionNanos())));
            CONTEXTOS.put(base, c); return c;
        } catch (Exception e) { guardia.close(); throw e; }
    }
    private static synchronized void retirar(Path p, ContextoTaller c) { CONTEXTOS.remove(p, c); }
    public static ServicioTaller servicio(Path p) throws Exception { return abrir(p).servicio(); }
    public static void cerrarTodos() throws Exception {
        List<ContextoTaller> contextos;
        synchronized (TestContextos.class) { contextos = List.copyOf(CONTEXTOS.values()); }
        Exception error = null;
        for (var c : contextos) try { c.close(); } catch (Exception e) { if (error == null) error = e; else error.addSuppressed(e); }
        if (error != null) throw error;
    }
}
