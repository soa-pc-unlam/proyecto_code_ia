package ar.edu.unlam.reparabarrio.aplicacion;

import ar.edu.unlam.reparabarrio.aplicacion.puertos.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import ar.edu.unlam.reparabarrio.planificacion.Planificador;
import java.time.Clock;
import java.util.*;
import static ar.edu.unlam.reparabarrio.aplicacion.ResultadoOperacion.Estado.*;

/** Única fachada de negocio. Lee, valida y escribe bajo el coordinador y una
 * transacción nueva. No expone conexiones ni el repositorio a sus clientes.
 */
public final class ServicioTaller {
    private final String contexto;
    private final ContextoTaller propietario;
    private final CoordinadorTaller coordinador;
    private final UnidadTrabajo.Fabrica fabrica;
    private final Clock reloj;
    private final Observador observador;
    private HechosConfirmados hechos;
    private final Planificador planificador = new Planificador();

    // H3-D04: observación interna, opcional y entregada únicamente después del unlock.
    @FunctionalInterface interface Observador { void recibir(Muestra muestra); }
    record Muestra(String contexto, String intentoId, long hiloId, String hiloNombre,
                   boolean adquirido, long esperaNanos, Long posesionNanos) { }

    ServicioTaller(ContextoTaller propietario, UnidadTrabajo.Fabrica fabrica, Clock reloj, Observador observador) {
        this.propietario = Objects.requireNonNull(propietario);
        this.contexto = propietario.identidad;
        this.coordinador = propietario.coordinador;
        this.fabrica = Objects.requireNonNull(fabrica);
        this.reloj = Objects.requireNonNull(reloj);
        this.observador = observador;
    }
    void habilitarHechos(RelojIntervalos reloj) { hechos = new HechosConfirmados(reloj); }

    private static final class Rechazo extends RuntimeException {
        Rechazo(String mensaje) { super(mensaje); }
    }
    private static final class Inconsistencia extends IllegalStateException {
        Inconsistencia(String mensaje, Throwable causa) { super(mensaje, causa); }
    }
    private static void exigir(boolean condicion, String motivo) {
        if (!condicion) throw new Rechazo(motivo);
    }
    private static Orden orden(InstantaneaDominio s, String id) {
        return s.ordenes().stream().filter(o -> o.id().equals(id)).findFirst()
                .orElseThrow(() -> new Rechazo("Orden inexistente"));
    }
    private static <T> T entrada(java.util.function.Supplier<T> validar) {
        try { return validar.get(); }
        catch (IllegalArgumentException | NullPointerException e) {
            throw new Rechazo(Objects.toString(e.getMessage(), "Entrada incompleta"));
        }
    }

    @FunctionalInterface private interface Operacion<T> {
        Efecto<T> ejecutar(RepositorioTaller r, InstantaneaDominio s, String id, RepositorioRecuperacion.Datos datos, DescriptorOperacion descriptor) throws Exception;
    }
    private record Efecto<T>(T valor, boolean cambio) { }

    private <T> ResultadoOperacion<T> mutar(java.util.function.Supplier<DescriptorOperacion> descriptor, Operacion<T> operacion) {
        return ejecutar(true, descriptor, operacion);
    }
    private <T> ResultadoOperacion<T> ejecutar(boolean mutante, java.util.function.Supplier<DescriptorOperacion> proveedor, Operacion<T> operacion) {
        String id = mutante ? UUID.randomUUID().toString() : null;
        long t0 = observador == null ? 0 : System.nanoTime();
        try { coordinador.bloqueo.lockInterruptibly(); }
        catch (InterruptedException e) {
            long fin = observador == null ? 0 : System.nanoTime();
            Thread.currentThread().interrupt();
            observar(id, false, fin - t0, null);
            return resultado(ERROR_TECNICO, id, null, "Interrumpida antes de abrir transacción");
        }
        long t1 = observador == null ? 0 : System.nanoTime();
        UnidadTrabajo u = null;
        boolean commitIntentado = false;
        boolean commitConfirmado = false;
        boolean rollbackIntentado = false;
        String avisoCierre = null;
        boolean preparada = false;
        boolean auditoriaCompleta = false;
        ResultadoOperacion<T> salida;
        DescriptorOperacion descriptor = null;
        try {
            try {
                if (!propietario.vigente()) return resultado(ERROR_TECNICO, null, null, "REFERENCIA_CERRADA");
                // Tampoco se muestran lecturas inciertas como estado confirmado (PLAN 5.2).
                if (coordinador.operacionIncierta != null)
                    return resultado(PENDIENTE_VERIFICACION, coordinador.operacionIncierta, null, coordinador.diagnostico);
                if (propietario.ciclo != ContextoTaller.Ciclo.HABILITADO)
                    return resultado(ERROR_TECNICO, null, null, coordinador.diagnostico);
                if (mutante) {
                    descriptor = entrada(proveedor);
                    var original = descriptor;
                    try {
                        propietario.infraestructura(true, tecnica -> {
                            var r = tecnica.repositorio();
                            var d = original;
                            if (Set.of("FINALIZADA", "FALLIDA").contains(d.accion())) {
                                var activas = r.cargar(contexto).asignaciones().get(d.entidad());
                                d = d.conRecursos(activas == null ? Set.of() : activas.unidades());
                            }
                            tecnica.recuperacion().preparar(new RepositorioRecuperacion.Intento(id, contexto, propietario.sesion,
                                    "NEGOCIO", d, r.revision(), "PENDIENTE", null, null));
                            return null;
                        }); preparada = true;
                    } catch (Exception e) {
                        bloquear(id, e);
                        return error(PENDIENTE_VERIFICACION, id, "Intención no verificada; negocio no iniciado: " + e, e, "INTENCION");
                    }
                }
                try { u = fabrica.abrir(); }
                catch (UnidadTrabajo.AperturaIncompleta e) { propietario.residuales.add(e.residual()); throw e; }
                var r = u.repositorio();
                InstantaneaDominio s;
                try { s = r.cargar(contexto); }
                catch (IllegalArgumentException | NullPointerException e) {
                    // Una instantánea almacenada inválida es un defecto técnico, no entrada del usuario.
                    throw new Inconsistencia("Datos persistidos inconsistentes", e);
                }
                RepositorioRecuperacion.Datos datos;
                try { datos = u.recuperacion().leer(); }
                catch (IllegalArgumentException | NullPointerException e) { throw new Inconsistencia("Metadatos persistidos inválidos", e); }
                var errores = new AuditorCoherencia().auditar(s, r.historial(), r.revision(), datos, true);
                if (!errores.isEmpty()) throw new Inconsistencia(String.join("; ", errores), null);
                auditoriaCompleta = true;
                var efecto = operacion.ejecutar(r, s, id, datos, descriptor);
                if (mutante && efecto.cambio()) {
                    u.recuperacion().resolver(id, "CONFIRMADA", r.revision(), null);
                    if (Set.of("FINALIZADA", "FALLIDA").contains(descriptor.accion()))
                        u.recuperacion().resolverRevision(descriptor.entidad(), id);
                    // Leer la evidencia dentro de la misma transacción ANTES del commit.
                    // Tras commit sólo se toma la marca y se agrega en memoria.
                    EventoHistorial evento = hechos == null ? null : r.historial().getLast();
                    long revision = hechos == null ? 0 : r.revision();
                    commitIntentado = true;
                    u.commit();
                    commitConfirmado = true;
                    if (hechos != null) hechos.confirmar(contexto, revision, evento,
                            descriptor.conRecursos(evento.recursos()));
                    salida = resultado(CONFIRMADA, id, efecto.valor(), "Operación conservada");
                } else {
                    rollbackIntentado = true;
                    u.rollback();
                    salida = resultado(mutante ? SIN_CAMBIO : CONFIRMADA, id, efecto.valor(),
                            mutante ? "Sin cambio" : "Lectura coherente");
                }
            } catch (Exception e) {
                if ((commitIntentado && !commitConfirmado) || rollbackIntentado) {
                    // Un rollback posterior NO demuestra que el commit que falló no ocurrió.
                    bloquear(id, e);
                    salida = error(PENDIENTE_VERIFICACION, coordinador.operacionIncierta, coordinador.diagnostico, e, mutante ? "NEGOCIO" : "LECTURA");
                } else {
                    boolean revertido = u == null;
                    if (u != null) try { u.rollback(); revertido = true; }
                    catch (Exception rollback) { e.addSuppressed(rollback); }
                    if (!revertido) {
                        bloquear(id, e);
                        salida = error(PENDIENTE_VERIFICACION, coordinador.operacionIncierta, coordinador.diagnostico, e, mutante ? "NEGOCIO" : "LECTURA");
                    } else {
                        if (e instanceof Inconsistencia) propietario.bloquear(null, "INCONSISTENCIA: " + e);
                        else if ((!mutante || !auditoriaCompleta) && !(e instanceof Rechazo)) propietario.bloquear(null, "Lectura no verificada: " + e);
                        salida = error(e instanceof Rechazo ? RECHAZADA : ERROR_TECNICO, id,
                                e instanceof Inconsistencia ? "INCONSISTENCIA: " + e : e.toString(), e, mutante ? "NEGOCIO" : "LECTURA");
                    }
                }
            } finally {
                    if (u != null) try { propietario.cerrarUnidad(u); }
                    catch (Exception e) {
                        // El cierre no revierte un commit confirmado. Se conserva diagnóstico.
                        avisoCierre = "Fallo al cerrar conexión: " + e;
                        if (!u.terminada()) propietario.bloquear(coordinador.operacionIncierta, avisoCierre);
                    }
            }
            if (mutante && preparada && salida.estado() != CONFIRMADA && salida.estado() != PENDIENTE_VERIFICACION) {
                final String causa = salida.estado().name();
                try { propietario.infraestructura(true, tecnica -> { tecnica.recuperacion().resolver(id, causa, null, null); return null; }); }
                catch (Exception e) { propietario.bloquear(id, "Resolución técnica no verificada: " + e); avisoCierre = Objects.toString(avisoCierre, "") + "; Resolución técnica no verificada: " + e; }
            }
            if (salida.estado() == PENDIENTE_VERIFICACION && propietario.residuales.isEmpty()) {
                boolean durable = diagnosticar(salida.operacionId(), mutante, salida.diagnostico());
                salida = new ResultadoOperacion<>(salida.estado(), contexto, salida.operacionId(), null, salida.diagnostico(),
                        new ResultadoOperacion.DiagnosticoTecnico("RESULTADO_INCIERTO", mutante ? "NEGOCIO" : "LECTURA", salida.diagnostico(), propietario.ciclo, durable, salida.tecnico().sqlState()));
            }
            if (!mutante && avisoCierre != null && salida.estado() != PENDIENTE_VERIFICACION) {
                propietario.bloquear(null, avisoCierre);
                salida = resultado(ERROR_TECNICO, null, null, avisoCierre);
            }
            if (avisoCierre != null) return new ResultadoOperacion<>(salida.estado(), salida.contexto(), salida.operacionId(), salida.valor(), salida.diagnostico() + "; " + avisoCierre,
                    new ResultadoOperacion.DiagnosticoTecnico("FALLO_TECNICO_POSTERIOR", "CIERRE_RESOLUCION", avisoCierre, propietario.ciclo, false));
            return salida;
        } catch (Error fatal) {
            propietario.bloquear(id, "Interrupción fatal durante la operación; exige auditoría: " + fatal.getClass().getName());
            throw fatal;
        } finally {
            long t2 = observador == null ? 0 : System.nanoTime();
            coordinador.bloqueo.unlock();
            if (observador != null) observar(id, true, t1 - t0, t2 - t1);
        }
    }
    private boolean diagnosticar(String id, boolean mutante, String mensaje) {
        try {
            propietario.infraestructura(true, u -> {
                if (mutante) u.recuperacion().diagnosticar(id, mensaje);
                else u.recuperacion().preparar(new RepositorioRecuperacion.Intento(id, contexto, propietario.sesion, "LECTURA",
                        new DescriptorOperacion("LECTURA", null, null, Set.of(), null, null, null, Set.of()),
                        u.repositorio().revision(), "PENDIENTE", null, mensaje));
                return null;
            });
            return true;
        } catch (Exception e) { coordinador.diagnostico += "; Diagnóstico no durable: " + e; return false; }
    }
    private void observar(String id, boolean adquirido, long espera, Long posesion) {
        if (observador == null) return;
        boolean interrumpido = Thread.currentThread().isInterrupted();
        try {
            var hilo = Thread.currentThread();
            observador.recibir(new Muestra(contexto, id, hilo.threadId(), hilo.getName(), adquirido, espera, posesion));
        } catch (Throwable fallo) {
            if (fallo instanceof VirtualMachineError fatal) throw fatal;
            if (fallo instanceof ThreadDeath fatal) throw fatal;
            // Diagnóstico descartable: nunca reemplaza el resultado transaccional.
        } finally {
            if (interrumpido) Thread.currentThread().interrupt();
            else Thread.interrupted();
        }
    }
    private void bloquear(String id, Exception e) {
        propietario.bloquear(id == null ? UUID.randomUUID().toString() : id, "Resultado pendiente de verificación: " + e + "; SQLState=" + sqlState(e));
    }
    private static String sqlState(Throwable e) {
        var vistos = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        for (Throwable actual = e; actual != null && vistos.add(actual); actual = actual.getCause())
            if (actual instanceof java.sql.SQLException sql) return sql.getSQLState();
        return null;
    }
    private <T> ResultadoOperacion<T> error(ResultadoOperacion.Estado estado, String id, String mensaje, Exception e, String fase) {
        var base = this.<T>resultado(estado, id, null, mensaje);
        return new ResultadoOperacion<>(estado, contexto, id, null, mensaje,
                new ResultadoOperacion.DiagnosticoTecnico(base.tecnico().codigo(), fase, mensaje, propietario.ciclo, false, sqlState(e)));
    }
    private <T> ResultadoOperacion<T> resultado(ResultadoOperacion.Estado estado, String id, T valor, String mensaje) {
        String codigo = mensaje != null && mensaje.contains("REFERENCIA_CERRADA") ? "REFERENCIA_CERRADA"
                : mensaje != null && mensaje.contains("INCONSISTENCIA") ? "INCONSISTENCIA"
                : estado == PENDIENTE_VERIFICACION ? "RESULTADO_INCIERTO" : estado.name();
        return new ResultadoOperacion<>(estado, contexto, id, valor, mensaje,
                new ResultadoOperacion.DiagnosticoTecnico(codigo, "NEGOCIO", mensaje, propietario.ciclo, false));
    }

    public ResultadoOperacion<Orden> registrarOrden(String id, String descripcion, Collection<TipoHerramienta> tipos) {
        return mutar(() -> {
            var o = Orden.pendiente(id, descripcion, 1, tipos);
            return new DescriptorOperacion("REGISTRO_ORDEN", id, descripcion, o.requisitos(), null, null, null, Set.of());
        }, (r, s, op, datos, d) -> {
            // Toda validación y duplicado ANTES del incremento transaccional.
            var validada = Orden.pendiente(d.entidad(), d.descripcion(), 1, d.requisitos());
            exigir(s.ordenes().stream().noneMatch(o -> o.id().equals(id)), "ID de orden ya registrado");
            exigir(r.catalogo().containsAll(validada.requisitos()), "Tipo desconocido");
            var nueva = Orden.pendiente(d.entidad(), d.descripcion(), r.siguienteSecuencia(), d.requisitos());
            r.registrar(nueva);
            r.evento(op, id, "REGISTRO_ORDEN", reloj.instant(), Set.of());
            return new Efecto<>(nueva, true);
        });
    }
    public ResultadoOperacion<Recurso> registrarRecurso(Recurso.Categoria categoria, String id,
                                                       TipoHerramienta tipo, boolean habilitado) {
        return mutar(() -> {
            var recurso = new Recurso(new Recurso.Clave(categoria, id), tipo, habilitado);
            return new DescriptorOperacion("REGISTRO_RECURSO", id, null, Set.of(), categoria, tipo, habilitado, Set.of(recurso.clave()));
        }, (r, s, op, datos, d) -> {
            var nuevo = entrada(() -> new Recurso(new Recurso.Clave(categoria, id), tipo, habilitado));
            exigir(s.recursos().stream().noneMatch(x -> x.clave().equals(nuevo.clave())), "ID de recurso ya registrado");
            exigir(tipo == null || r.catalogo().contains(tipo), "Tipo desconocido");
            r.registrar(nuevo);
            r.evento(op, null, "REGISTRO_RECURSO", reloj.instant(), Set.of(nuevo.clave()));
            return new Efecto<>(nuevo, true);
        });
    }
    public ResultadoOperacion<InstantaneaTaller> consultar() {
        return leer((r, s, datos) -> {
            return new Efecto<>(new InstantaneaTaller(r.revision(), s, r.catalogo(), r.historial(), planificador.proponer(s),
                    datos.metadata().revisionTecnica(), datos.revisiones()), false);
        });
    }
    /** Captura funcional y temporal del mismo orden serializado, sin derivar disponibilidad. */
    public ResultadoOperacion<HechosConfirmados.Captura> consultarHechos() {
        return leer((r, s, datos) -> {
            if (hechos == null) throw new IllegalStateException("Captura temporal no habilitada");
            var taller = new InstantaneaTaller(r.revision(), s, r.catalogo(), r.historial(), planificador.proponer(s),
                    datos.metadata().revisionTecnica(), datos.revisiones());
            return new Efecto<>(hechos.capturar(taller), false);
        });
    }
    @FunctionalInterface private interface Consulta<T> {
        Efecto<T> ejecutar(RepositorioLectura r, InstantaneaDominio s, RepositorioRecuperacion.Datos datos) throws Exception;
    }
    private <T> ResultadoOperacion<T> leer(Consulta<T> consulta) {
        return ejecutar(false, null, (r, s, id, datos, d) -> consulta.ejecutar(r, s, datos));
    }
    public ResultadoOperacion<PropuestaInicio> confirmarInicio(PropuestaInicio propuesta) {
        return mutar(() -> new DescriptorOperacion("INICIO", Objects.requireNonNull(propuesta).ordenId(), null, Set.of(),
                null, null, null, propuesta.recursos().unidades()), (r, s, op, datos, d) -> {
            entrada(() -> { Objects.requireNonNull(propuesta); planificador.validarPropuesta(s, propuesta); return propuesta; });
            r.estado(propuesta.ordenId(), EstadoOrden.EN_CURSO);
            r.asignar(propuesta.ordenId(), propuesta.recursos());
            r.evento(op, propuesta.ordenId(), "INICIO", reloj.instant(), propuesta.recursos().unidades());
            return new Efecto<>(propuesta, true);
        });
    }
    public ResultadoOperacion<Orden> finalizar(String id) { return transicionar(id, EstadoOrden.FINALIZADA); }
    public ResultadoOperacion<Orden> fallar(String id) { return transicionar(id, EstadoOrden.FALLIDA); }
    public ResultadoOperacion<Orden> cancelar(String id) { return transicionar(id, EstadoOrden.CANCELADA); }
    private ResultadoOperacion<Orden> transicionar(String id, EstadoOrden destino) {
        return mutar(() -> new DescriptorOperacion(destino.name(), Identidad.validar(id), null, Set.of(), null, null, null, Set.of()), (r, s, op, datos, d) -> {
            var nueva = entrada(() -> orden(s, id).transicionar(destino));
            var activas = s.asignaciones().get(id);
            r.estado(id, destino);
            if (activas != null) r.liberar(id);
            r.evento(op, id, destino.name(), reloj.instant(), activas == null ? Set.of() : activas.unidades());
            return new Efecto<>(nueva, true);
        });
    }
    public ResultadoOperacion<Recurso> cambiarHabilitacion(Recurso.Clave clave, boolean habilitado) {
        return mutar(() -> new DescriptorOperacion(habilitado ? "HABILITACION" : "INHABILITACION", Objects.requireNonNull(clave).id(), null,
                Set.of(), clave.categoria(), null, habilitado, Set.of(clave)), (r, s, op, datos, d) -> {
            exigir(clave != null, "Falta recurso");
            var anterior = s.recursos().stream().filter(x -> x.clave().equals(clave)).findFirst()
                    .orElseThrow(() -> new Rechazo("Recurso inexistente"));
            exigir(s.disponibilidad(clave) != Recurso.Disponibilidad.OCUPADO, "Recurso ocupado");
            var nuevo = new Recurso(clave, anterior.tipo(), habilitado);
            if (anterior.habilitado() == habilitado) return new Efecto<>(nuevo, false);
            r.habilitar(clave, habilitado);
            r.evento(op, null, habilitado ? "HABILITACION" : "INHABILITACION", reloj.instant(), Set.of(clave));
            return new Efecto<>(nuevo, true);
        });
    }
}
