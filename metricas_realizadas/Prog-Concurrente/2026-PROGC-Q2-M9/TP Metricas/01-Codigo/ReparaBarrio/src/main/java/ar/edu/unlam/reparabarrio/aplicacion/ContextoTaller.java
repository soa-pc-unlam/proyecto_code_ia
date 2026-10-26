package ar.edu.unlam.reparabarrio.aplicacion;

import ar.edu.unlam.reparabarrio.aplicacion.puertos.*;
import ar.edu.unlam.reparabarrio.persistencia.PersistenciaH2;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.*;

/** Propietario/capacidad ligado a guardia, identidad y coordinador.
 * Lock justo -> conexión privada -> cierre -> unlock. Retiro del registro fuera del lock.
 */
public final class ContextoTaller implements AutoCloseable {
    public enum Ciclo { ABRIENDO, HABILITADO, BLOQUEADO, CERRANDO, CERRADO }
    final CoordinadorTaller coordinador;
    final UnidadTrabajo.Fabrica infraestructura;
    final String identidad;
    public static final class Capacidad {
        private final ContextoTaller propietario;
        private Capacidad(ContextoTaller propietario) { this.propietario = propietario; }
        public boolean corresponde(ContextoTaller propietario) { return this.propietario == propietario; }
        public boolean vigente() { return propietario.almacen.guardia().vigente() && propietario.ciclo != Ciclo.CERRADO; }
    }
    private final Capacidad capacidad = new Capacidad(this);
    private final PersistenciaH2.Almacen almacen;
    private final java.util.function.Consumer<ContextoTaller> retiro;
    private final ServicioTaller servicio;
    private final ServicioRecuperacion recuperacion;
    final List<UnidadTrabajo> residuales = new ArrayList<>();
    volatile Ciclo ciclo = Ciclo.ABRIENDO;
    String sesion;
    InformeRecuperacion informe;
    private ContextoTaller(PersistenciaH2.Almacen almacen, Consumer<ContextoTaller> retiro,
                           CoordinadorTaller coordinador, UnaryOperator<UnidadTrabajo.Fabrica> negocio,
                           UnaryOperator<UnidadTrabajo.Fabrica> infraestructura, ServicioTaller.Observador observador, Clock relojCivil) {
        if (!almacen.guardia().vigente()) throw new IllegalStateException("Guardia no vigente");
        this.almacen = almacen; this.retiro = retiro; this.identidad = almacen.identidad(); this.coordinador = coordinador;
        almacen.reclamar(capacidad);
        UnidadTrabajo.Fabrica unidades = () -> almacen.abrirUnidad(capacidad);
        this.infraestructura = infraestructura.apply(unidades);
        servicio = new ServicioTaller(this, negocio.apply(unidades), relojCivil, observador);
        recuperacion = new ServicioRecuperacion(this);
    }
    public static ContextoTaller componer(PersistenciaH2.Almacen almacen, Consumer<ContextoTaller> retiro) {
        return componerPrueba(almacen, retiro, new CoordinadorTaller(), UnaryOperator.identity(), UnaryOperator.identity(), null);
    }
    public static ContextoTaller componerSimulacion(PersistenciaH2.Almacen almacen, Consumer<ContextoTaller> retiro,
                                                    RelojIntervalos reloj) {
        return componerSimulacion(almacen, retiro, Clock.systemUTC(), reloj);
    }
    public static ContextoTaller componerSimulacion(PersistenciaH2.Almacen almacen, Consumer<ContextoTaller> retiro,
                                                    Clock civil, RelojIntervalos reloj) {
        var contexto = componerTemporal(almacen, retiro, new CoordinadorTaller(), UnaryOperator.identity(),
                UnaryOperator.identity(), null, Objects.requireNonNull(civil));
        contexto.servicio.habilitarHechos(reloj);
        return contexto;
    }
    static ContextoTaller componerPrueba(PersistenciaH2.Almacen almacen, Consumer<ContextoTaller> retiro,
                                         CoordinadorTaller coordinador, UnaryOperator<UnidadTrabajo.Fabrica> negocio,
                                         UnaryOperator<UnidadTrabajo.Fabrica> infraestructura, ServicioTaller.Observador observador) {
        return componerTemporal(almacen, retiro, coordinador, negocio, infraestructura, observador, Clock.systemUTC());
    }
    static ContextoTaller componerTemporal(PersistenciaH2.Almacen almacen, Consumer<ContextoTaller> retiro,
                                         CoordinadorTaller coordinador, UnaryOperator<UnidadTrabajo.Fabrica> negocio,
                                         UnaryOperator<UnidadTrabajo.Fabrica> infraestructura, ServicioTaller.Observador observador, Clock civil) {
        var c = new ContextoTaller(almacen, retiro, coordinador, negocio, infraestructura, observador, civil);
        if (almacen.incompleta()) {
            c.residuales.add(almacen.residual(c.capacidad));
            c.bloquear(null, "Apertura incompleta; guardia retenida: " + almacen.diagnosticoApertura());
        } else c.recuperacion.recuperar();
        return c;
    }
    public String identidad() { return identidad; }
    public Ciclo ciclo() { return ciclo; }
    public ServicioTaller servicio() { return servicio; }
    public ServicioRecuperacion recuperacion() { return recuperacion; }
    public InformeRecuperacion informe() {
        coordinador.bloqueo.lock(); try { return informe; } finally { coordinador.bloqueo.unlock(); }
    }
    boolean vigente() { return almacen.guardia().vigente() && ciclo != Ciclo.CERRADO && ciclo != Ciclo.CERRANDO; }
    void bloquear(String id, String motivo) {
        ciclo = Ciclo.BLOQUEADO; coordinador.operacionIncierta = id; coordinador.diagnostico = motivo;
        var operaciones = new HashMap<String, String>(informe == null ? Map.of() : informe.operaciones());
        if (id != null) operaciones.putIfAbsent(id, "PENDIENTE_VERIFICACION");
        informe = new InformeRecuperacion(identidad, ciclo, sesion, operaciones,
                informe == null ? Map.of() : informe.revisiones(), List.of(motivo), null, informe == null ? -1 : informe.revision());
    }
    void cerrarUnidad(UnidadTrabajo u) throws Exception {
        try { u.close(); }
        catch (Exception e) { if (!u.terminada()) residuales.add(u); throw e; }
    }
    @FunctionalInterface interface Transaccion<T> { T ejecutar(UnidadTrabajo u) throws Exception; }
    <T> T infraestructura(boolean escritura, Transaccion<T> accion) throws Exception {
        if (!residuales.isEmpty() || !almacen.guardia().vigente()) throw new IllegalStateException("Conexión dudosa activa/guardia inválida");
        UnidadTrabajo u;
        try { u = this.infraestructura.abrir(); }
        catch (UnidadTrabajo.AperturaIncompleta e) { residuales.add(e.residual()); throw e; }
        boolean commitIntentado = false;
        boolean rollbackIntentado = false;
        try {
            T valor = accion.ejecutar(u);
            if (escritura) { commitIntentado = true; u.commit(); } else { rollbackIntentado = true; u.rollback(); }
            return valor;
        } catch (Exception e) {
            if (!commitIntentado && !rollbackIntentado) try { u.rollback(); } catch (Exception r) { e.addSuppressed(r); }
            throw e;
        } finally { cerrarUnidad(u); }
    }
    @Override public void close() throws Exception {
        if (!coordinador.bloqueo.tryLock(10, TimeUnit.SECONDS)) throw new IllegalStateException("Cierre vencido; guardia retenida");
        boolean retirado = false;
        try {
            if (ciclo == Ciclo.CERRADO) return;
            boolean sana = ciclo == Ciclo.HABILITADO;
            ciclo = Ciclo.CERRANDO;
            for (var u : new ArrayList<>(residuales)) {
                try { u.close(); if (u.terminada()) residuales.remove(u); }
                catch (Exception e) { if (u.terminada()) residuales.remove(u); else throw new IllegalStateException("Conexión posiblemente activa; guardia retenida", e); }
            }
            if (!residuales.isEmpty()) throw new IllegalStateException("Conexión posiblemente activa; guardia retenida");
            if (sana && sesion != null) {
                try { infraestructura(true, u -> {
                    var datos = u.recuperacion().leer();
                    if (datos.intentos().stream().anyMatch(i -> i.resolucion().equals("PENDIENTE")))
                        throw new IllegalStateException("Intenciones pendientes: no se marca sesión limpia");
                    u.recuperacion().limpiarSesion(sesion); return null;
                }); }
                catch (Exception e) { bloquear(null, "Marca limpia no verificada: " + e); if (!residuales.isEmpty()) throw e; }
            }
            try { almacen.cerrarAncla(capacidad); }
            catch (Exception e) { bloquear(null, "Cierre de ancla no verificado; guardia retenida: " + e); throw e; }
            almacen.guardia().close(); ciclo = Ciclo.CERRADO; retirado = true;
            informe = new InformeRecuperacion(identidad, ciclo, sesion, informe.operaciones(), informe.revisiones(), informe.hallazgos(), null, informe.revision());
        } finally { coordinador.bloqueo.unlock(); if (retirado) retiro.accept(this); }
    }
}
