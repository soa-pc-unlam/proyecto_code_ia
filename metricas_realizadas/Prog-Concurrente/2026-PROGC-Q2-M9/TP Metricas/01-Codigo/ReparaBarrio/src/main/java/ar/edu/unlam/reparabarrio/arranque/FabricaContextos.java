package ar.edu.unlam.reparabarrio.arranque;

import ar.edu.unlam.reparabarrio.aplicacion.ContextoTaller;
import ar.edu.unlam.reparabarrio.persistencia.*;
import java.nio.file.*;
import java.util.*;

/** Registro de propietarios. Nunca se mantiene su monitor al llamar a una fachada. */
public final class FabricaContextos {
    private static final Map<Path, ContextoTaller> CONTEXTOS = new HashMap<>();
    public record Apertura(ContextoTaller contexto, String codigo, String diagnostico) { }
    private FabricaContextos() { }
    /** H6-D01: sólo memoria nueva; ningún argumento permite seleccionar base o URL. */
    public static ContextoTaller crearSimulacion(Path raiz, boolean consentimiento) throws Exception {
        return crearSimulacion(ContextoEjecucion.desarrollo(raiz), consentimiento);
    }
    public static ContextoTaller crearSimulacion(ContextoEjecucion ejecucion, boolean consentimiento) throws Exception {
        Path raiz = ejecucion.raiz();
        if (!consentimiento) throw new IllegalArgumentException("Creación de simulación no consentida");
        Path real = validarRaiz(ejecucion);
        if (!raiz.normalize().equals(real)) throw new IllegalArgumentException("Raíz real requerida");
        for (Path p = raiz; p != null; p = p.getParent())
            if (Files.isSymbolicLink(p)) throw new IllegalArgumentException("Raíz enlazada");
        Path target = real.resolve("target");
        if (Files.isSymbolicLink(target)) throw new IllegalArgumentException("Target enlazado");
        Files.createDirectories(target);
        if (!target.toRealPath().equals(target)) throw new IllegalArgumentException("Target no real");
        Path directorio = Files.createDirectory(target.resolve("h6-" + UUID.randomUUID())).toRealPath();
        return componerSimulacion(directorio);
    }
    private static synchronized ContextoTaller componerSimulacion(Path directorio) throws Exception {
        var guardia = GuardiaInstancia.adquirir(directorio);
        PersistenciaH2.Almacen almacen;
        try {
            almacen = PersistenciaH2.prepararSimulacion(guardia, AutorizacionSimulacion.nueva(guardia, true));
        } catch (Exception e) { guardia.close(); throw e; }
        // Desde este punto el almacén conserva cualquier conexión residual y su guardia.
        var contexto = ContextoTaller.componerSimulacion(almacen, cerrado -> retirar(directorio, cerrado), System::nanoTime);
        CONTEXTOS.put(directorio, contexto);
        return contexto;
    }
    /** H5-D01: sólo directorio NUEVO, creado atómicamente por esta entrada.
     * No inspecciona la ruta operativa. Conserva el único registro de propietarios. */
    public static ContextoTaller crearAcademico(Path raiz, String nombre, boolean consentimiento) throws Exception {
        return crearAcademico(ContextoEjecucion.desarrollo(raiz), nombre, consentimiento);
    }
    public static ContextoTaller crearAcademico(ContextoEjecucion ejecucion, String nombre, boolean consentimiento) throws Exception {
        Path raiz = ejecucion.raiz();
        if (!consentimiento) throw new IllegalArgumentException("Creación descartable no consentida");
        Path real = validarRaiz(ejecucion);
        if (nombre == null || !nombre.matches("h5-[A-Za-z0-9-]+"))
            throw new IllegalArgumentException("Nombre académico inválido");
        if (!raiz.normalize().equals(real)) throw new IllegalArgumentException("Raíz real sin enlaces requerida");
        for (Path p = raiz; p != null; p = p.getParent())
            if (Files.isSymbolicLink(p)) throw new IllegalArgumentException("Raíz enlazada");
        Path target = real.resolve("target");
        if (Files.isSymbolicLink(target)) throw new IllegalArgumentException("Target enlazado");
        Files.createDirectories(target);
        if (!target.toRealPath().equals(target)) throw new IllegalArgumentException("Target no real");
        Path directorio = target.resolve(nombre);
        Files.createDirectory(directorio); // Rechaza restos, enlaces y sesiones conocidas, sin abrirlos.
        Path base = directorio.resolve("taller");
        var permiso = new AutorizacionBase("H5-" + UUID.randomUUID(), base, "AUSENTE", 0, 2,
                null, true, false, "Creación humana explícita de sesión académica descartable nueva");
        try {
            return componer(directorio, new InicializadorBase.Solicitud(InicializadorBase.Modo.CREAR_NUEVA, permiso));
        } catch (Exception e) {
            try { Files.writeString(directorio.resolve("apertura-fallida.txt"), e.toString(), StandardOpenOption.CREATE_NEW); }
            catch (Exception diagnostico) { e.addSuppressed(diagnostico); }
            throw e;
        }
    }
    public static Apertura abrirConInforme(Path raiz, InicializadorBase.Solicitud solicitud) {
        try { var c = abrir(raiz, solicitud); return new Apertura(c, c.ciclo().name(), null); }
        catch (Exception e) { return new Apertura(null, e.getMessage() != null && e.getMessage().contains("INSTANCIA_OCUPADA") ? "INSTANCIA_OCUPADA" : "APERTURA_NO_VERIFICADA", e.toString()); }
    }
    public static ContextoTaller abrir(Path raiz, InicializadorBase.Solicitud solicitud) throws Exception {
        Path real = validarRaiz(raiz);
        Path directorio = real.resolve("datos/operativo");
        if (Files.isSymbolicLink(real.resolve("datos")) || Files.isSymbolicLink(directorio)) throw new IllegalArgumentException("Enlace no admitido");
        Files.createDirectories(directorio);
        return componer(directorio, solicitud);
    }
    public static Path validarRaiz(Path raiz) throws Exception {
        return validarRaiz(ContextoEjecucion.desarrollo(raiz));
    }
    public static Path validarRaiz(ContextoEjecucion ejecucion) throws Exception {
        Path raiz = ejecucion.raiz();
        if (raiz == null || !raiz.isAbsolute() || raiz.toString().contains(";")) throw new IllegalArgumentException("Raíz absoluta sin parámetros JDBC requerida");
        Path real = raiz.toRealPath();
        if (ejecucion.modo() == ContextoEjecucion.Modo.PORTABLE) {
            if (!Files.isDirectory(real) || !raiz.normalize().equals(real))
                throw new IllegalArgumentException("Raíz portable real requerida");
            for (Path p = raiz; p != null; p = p.getParent())
                if (Files.isSymbolicLink(p)) throw new IllegalArgumentException("Raíz enlazada");
        }
        if (ejecucion.modo() == ContextoEjecucion.Modo.DESARROLLO &&
                (!Files.isRegularFile(real.resolve("pom.xml")) || !Files.isRegularFile(real.resolve("AGENTS.md"))))
            throw new IllegalArgumentException("Raíz de proyecto no acreditada");
        return real;
    }
    private static synchronized ContextoTaller componer(Path directorio, InicializadorBase.Solicitud solicitud) throws Exception {
        Path real = directorio.toRealPath();
        if (Files.isSymbolicLink(real.resolve("taller.mv.db"))) throw new IllegalArgumentException("Base enlazada no admitida");
        var existente = CONTEXTOS.get(real);
        if (existente != null) {
            if (existente.ciclo() == ContextoTaller.Ciclo.CERRADO) CONTEXTOS.remove(real, existente);
            else if (existente.ciclo() == ContextoTaller.Ciclo.CERRANDO) throw new IllegalStateException("CONTEXTO_CERRANDO");
            else return existente;
        }
        var guardia = GuardiaInstancia.adquirir(real);
        try {
            var almacen = PersistenciaH2.preparar(guardia, solicitud);
            var contexto = ContextoTaller.componer(almacen, cerrado -> retirar(real, cerrado));
            CONTEXTOS.put(real, contexto); return contexto;
        } catch (Exception e) { guardia.close(); throw e; }
    }
    private static synchronized void retirar(Path ruta, ContextoTaller propietario) { CONTEXTOS.remove(ruta, propietario); }
    public static synchronized ContextoTaller existente(Path raiz) throws Exception {
        Path ruta = validarRaiz(raiz).resolve("datos/operativo").toRealPath();
        var c = CONTEXTOS.get(ruta);
        if (c == null) throw new IllegalStateException("Apertura explícita/autorización requerida");
        if (c.ciclo() != ContextoTaller.Ciclo.HABILITADO) throw new IllegalStateException("Contexto no habilitado: " + c.ciclo());
        return c;
    }
}
