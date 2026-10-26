package ar.edu.unlam.reparabarrio.persistencia;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.aplicacion.puertos.UnidadTrabajo;
import ar.edu.unlam.reparabarrio.arranque.*;
import java.nio.file.*;
import java.sql.*;

/** JDBC privado, compuesto sólo con una guardia vigente y autorización específica. */
public final class PersistenciaH2 {
    private PersistenciaH2() { }
    public static final class Almacen {
        private final GuardiaInstancia guardia;
        private final String identidad, url;
        private ContextoTaller.Capacidad propietario;
        private UnidadTrabajo.AperturaIncompleta aperturaIncompleta;
        private Connection ancla;
        private Almacen(GuardiaInstancia guardia, String identidad, String url) {
            this.guardia = guardia; this.identidad = identidad; this.url = url;
        }
        public GuardiaInstancia guardia() { return guardia; }
        public String identidad() { return identidad; }
        public boolean incompleta() { return aperturaIncompleta != null; }
        public String diagnosticoApertura() { return aperturaIncompleta == null ? null : aperturaIncompleta.toString(); }
        public UnidadTrabajo residual(ContextoTaller.Capacidad capacidad) {
            if (capacidad == null || capacidad != propietario) throw new IllegalStateException("Capacidad de propietario requerida");
            return aperturaIncompleta.residual();
        }
        public synchronized void reclamar(ContextoTaller.Capacidad capacidad) {
            if (propietario != null || capacidad == null) throw new IllegalStateException("Almacén ya posee propietario");
            propietario = capacidad;
        }
        public UnidadTrabajo abrirUnidad(ContextoTaller.Capacidad capacidad) throws Exception {
            if (capacidad == null || capacidad != propietario || !capacidad.vigente()) throw new IllegalStateException("Capacidad no vigente");
            return new UnidadTrabajoJdbc(url);
        }
        /** Se llama bajo el coordinador y antes de liberar la guardia. */
        public void cerrarAncla(ContextoTaller.Capacidad capacidad) throws Exception {
            if (capacidad == null || capacidad != propietario) throw new IllegalStateException("Capacidad requerida");
            if (ancla != null) {
                ancla.close();
                if (!ancla.isClosed()) throw new IllegalStateException("Ancla no cerrada; guardia retenida");
            }
        }
    }
    public static Almacen prepararSimulacion(GuardiaInstancia guardia, AutorizacionSimulacion permiso) throws Exception {
        String url = permiso.consumir(guardia);
        guardia.emitirAlmacen();
        guardia.recordarBaseConocida();
        Connection ancla = DriverManager.getConnection(url, "sa", "");
        try {
            String id = InicializadorBase.prepararMemoria(ancla, permiso);
            var almacen = new Almacen(guardia, id, url + ";IFEXISTS=TRUE");
            almacen.ancla = ancla;
            return almacen;
        } catch (Exception apertura) {
            try { ancla.close(); } catch (Exception cierre) { apertura.addSuppressed(cierre); }
            boolean cerrada = false;
            try { cerrada = ancla.isClosed(); } catch (Exception estado) { apertura.addSuppressed(estado); }
            if (cerrada) throw apertura;
            var almacen = new Almacen(guardia, null, url + ";IFEXISTS=TRUE");
            almacen.ancla = ancla;
            almacen.aperturaIncompleta = new UnidadTrabajo.AperturaIncompleta(new UnidadTrabajoJdbc(ancla), apertura);
            return almacen;
        }
    }
    public static Almacen preparar(GuardiaInstancia guardia, InicializadorBase.Solicitud solicitud) throws Exception {
        return preparar(guardia, solicitud, fase -> { });
    }
    static Almacen preparar(GuardiaInstancia guardia, InicializadorBase.Solicitud solicitud, InicializadorBase.Frontera frontera) throws Exception {
        guardia.emitirAlmacen();
        try {
            String id = InicializadorBase.preparar(guardia, solicitud, frontera);
            return new Almacen(guardia, id, InicializadorBase.url(guardia.directorio().resolve("taller")) + ";IFEXISTS=TRUE");
        } catch (UnidadTrabajo.AperturaIncompleta e) {
            var almacen = new Almacen(guardia, null, InicializadorBase.url(guardia.directorio().resolve("taller")) + ";IFEXISTS=TRUE");
            almacen.aperturaIncompleta = e; return almacen;
        }
    }
    /** La firma histórica no expresa autorización: sólo recupera el propietario ya abierto. */
    @Deprecated public static ServicioTaller abrir(Path archivoBase) throws Exception {
        if (archivoBase == null || !archivoBase.isAbsolute() || archivoBase.toString().contains(";"))
            throw new IllegalArgumentException("Ruta operativa absoluta requerida");
        Path normal = archivoBase.normalize();
        if (!normal.endsWith(Path.of("datos", "operativo", "taller"))) throw new IllegalArgumentException("Ruta operativa fija requerida");
        Path raiz = normal.getParent().getParent().getParent().toRealPath();
        return FabricaContextos.existente(raiz).servicio();
    }
}
