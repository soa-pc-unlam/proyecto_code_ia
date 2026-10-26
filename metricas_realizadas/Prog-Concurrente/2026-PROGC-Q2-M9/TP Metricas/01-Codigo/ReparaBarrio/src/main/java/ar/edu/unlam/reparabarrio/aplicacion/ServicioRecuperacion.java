package ar.edu.unlam.reparabarrio.aplicacion;

import ar.edu.unlam.reparabarrio.aplicacion.puertos.RepositorioRecuperacion.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/** Reconciliación por evidencia completa. Nunca vuelve a ejecutar una solicitud. */
public final class ServicioRecuperacion {
    private final ContextoTaller contexto;
    ServicioRecuperacion(ContextoTaller contexto) { this.contexto = contexto; }
    private record Lectura(Datos datos, InstantaneaDominio dominio, List<EventoHistorial> eventos, long revision, String referencia) { }
    private Lectura auditar() throws Exception {
        return contexto.infraestructura(false, u -> {
            var datos = u.recuperacion().leer(); var r = u.repositorio(); var dominio = r.cargar(contexto.identidad);
            var eventos = r.historial(); long revision = r.revision();
            if (!datos.metadata().baseId().equals(contexto.identidad)) throw new IllegalStateException("Identidad inesperada");
            var errores = new AuditorCoherencia().auditar(dominio, eventos, revision, datos, true);
            if (!errores.isEmpty()) throw new IllegalStateException(String.join("; ", errores));
            String referencia = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    (dominio.toString() + eventos + datos).getBytes(StandardCharsets.UTF_8)));
            return new Lectura(datos, dominio, eventos, revision, referencia);
        });
    }
    public InformeRecuperacion recuperar() {
        var c = contexto; c.coordinador.bloqueo.lock();
        try {
            if (!c.vigente()) return c.informe;
            if (!c.residuales.isEmpty()) { c.bloquear(c.coordinador.operacionIncierta, "Conexión posiblemente activa; recuperación bloqueada"); return c.informe; }
            Lectura l = auditar();
            final Lectura inicial = l;
            if (l.datos.intentos().stream().anyMatch(i -> i.resolucion().equals("PENDIENTE"))) {
                c.infraestructura(true, u -> {
                    for (var i : inicial.datos.intentos()) if (i.resolucion().equals("PENDIENTE"))
                        u.recuperacion().resolver(i.id(), i.clase().equals("NEGOCIO") ? "NO_CONFIRMADA" : "VERIFICADA", null, "Auditoría completa; conexión anterior terminada");
                    return null;
                }); l = auditar();
            }
            final Lectura auditada = l;
            List<Sesion> abiertas = l.datos.sesiones().stream().filter(Sesion::abierta).filter(s -> !s.id().equals(c.sesion)).toList();
            if (!abiertas.isEmpty() || l.datos.metadata().adopcionPendiente()) {
                c.infraestructura(true, u -> {
                    var rr = u.recuperacion(); String origen;
                    if (abiertas.isEmpty()) { origen = UUID.randomUUID().toString(); rr.abrirSesion(origen); }
                    else origen = abiertas.getFirst().id();
                    for (var o : auditada.dominio.ordenes()) if (o.estado() == EstadoOrden.EN_CURSO)
                        rr.revisar(o.id(), origen, auditada.datos.metadata().adopcionPendiente() ? "PROCEDENCIA_V1_DESCONOCIDA" : "SESION_INTERRUMPIDA");
                    for (var s : abiertas) rr.limpiarSesion(s.id());
                    if (abiertas.isEmpty()) rr.limpiarSesion(origen);
                    return null;
                }); l = auditar();
            }
            if (l.datos.metadata().adopcionPendiente()) {
                c.ciclo = ContextoTaller.Ciclo.BLOQUEADO; c.coordinador.operacionIncierta = null;
                c.coordinador.diagnostico = "ADOPCION_PENDIENTE";
                return publicar(l, List.of("ADOPCION_PENDIENTE"));
            }
            if (c.sesion == null) {
                String nueva = UUID.randomUUID().toString();
                c.infraestructura(true, u -> { u.recuperacion().abrirSesion(nueva); return null; });
                c.sesion = nueva; l = auditar();
            }
            if (l.datos.sesiones().stream().noneMatch(s -> s.id().equals(c.sesion) && s.abierta())) throw new IllegalStateException("Sesión abierta no verificada");
            c.ciclo = ContextoTaller.Ciclo.HABILITADO; c.coordinador.operacionIncierta = null; c.coordinador.diagnostico = null;
            return publicar(l, List.of());
        } catch (Exception e) { c.bloquear(c.coordinador.operacionIncierta, "Recuperación no verificada: " + e); return c.informe; }
        finally { c.coordinador.bloqueo.unlock(); }
    }
    private InformeRecuperacion publicar(Lectura l, List<String> hallazgos) {
        var operaciones = new HashMap<String, String>(); l.datos.intentos().forEach(i -> operaciones.put(i.id(), i.resolucion()));
        var c = contexto;
        c.informe = new InformeRecuperacion(c.identidad, c.ciclo, c.sesion, operaciones, l.datos.revisiones(), hallazgos, l.referencia, l.revision);
        return c.informe;
    }
    public InformeRecuperacion aceptarAdopcion(String referencia, long revision, String declaracion) {
        var c = contexto; c.coordinador.bloqueo.lock();
        try {
            if (!c.vigente() || !c.residuales.isEmpty()) throw new IllegalStateException("Capacidad no vigente");
            Lectura l = auditar();
            if (!l.datos.metadata().adopcionPendiente() || !Objects.equals(referencia, l.referencia) || revision != l.revision
                    || declaracion == null || declaracion.isBlank()) throw new IllegalArgumentException("Informe obsoleto/declaración ausente");
            c.infraestructura(true, u -> { u.recuperacion().aceptarAdopcion(referencia, declaracion); return null; });
            return recuperar();
        } catch (Exception e) { c.bloquear(null, "Adopción no verificada: " + e); return c.informe; }
        finally { c.coordinador.bloqueo.unlock(); }
    }
}
