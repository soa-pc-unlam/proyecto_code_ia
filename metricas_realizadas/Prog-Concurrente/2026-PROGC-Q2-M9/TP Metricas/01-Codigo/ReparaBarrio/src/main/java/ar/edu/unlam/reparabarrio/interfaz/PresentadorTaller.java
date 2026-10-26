package ar.edu.unlam.reparabarrio.interfaz;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.function.*;
import javax.swing.SwingUtilities;
import static ar.edu.unlam.reparabarrio.aplicacion.ResultadoOperacion.Estado.*;

/** Estado confinado al EDT; ningún monitor de presentación rodea al núcleo.
 * Cada presentador representa un propietario y una generación, incluso con igual UUID.
 * Las revisiones sólo filtran capturas; jamás invalidan una propuesta aceptada. */
public final class PresentadorTaller {
    private final PuertoTaller puerto;
    private final VistaTaller vista;
    private final Executor trabajador;
    private final Consumer<Runnable> publicar;
    private final Runnable terminar;
    private final String identidad;
    private final Object propietario = new Object();
    private final java.util.concurrent.atomic.AtomicBoolean salidaSolicitada = new java.util.concurrent.atomic.AtomicBoolean();
    private long generacion = 1, peticion, aplicada;
    private InstantaneaTaller ultima;
    private PropuestaInicio propuesta;
    private boolean lectura, pendiente, mutando, dialogo, cerrando, cierreEnCurso, vigente, incierto;
    private String commitConocido;
    private boolean conciliandoRevisiones;
    private boolean cerrado;
    public PresentadorTaller(PuertoTaller p, VistaTaller v, String identidad, TrabajosSwing trabajos) {
        this(p,v,identidad,trabajos::ejecutar,trabajos::publicar,trabajos::terminar);
    }
    public PresentadorTaller(PuertoTaller p,VistaTaller v,String identidad,Executor e,Consumer<Runnable> publicar,Runnable terminar) {
        this.puerto=p; this.vista=v; this.identidad=identidad; this.trabajador=e; this.publicar=publicar; this.terminar=terminar;
    }
    private static void edt() { if (!SwingUtilities.isEventDispatchThread()) throw new IllegalStateException("Se requiere EDT"); }
    private boolean activo(long g, Object dueno) { return !cerrando && g==generacion && dueno==propietario; }
    private boolean habilitado() { return vigente && !incierto && !mutando && !lectura && !cerrando && !dialogo; }
    private void estado(String actividad) { vista.estado(habilitado(),actividad+(incierto?" | salud no habilitada: consultar diagnóstico":vigente?" | salud HABILITADO":" | salud vigente N/D"),vigente); }
    public void refrescar() {
        edt(); if (cerrando || incierto) return;
        if (lectura || mutando) { pendiente=true; return; }
        lectura=true; pendiente=false; long g=generacion, n=++peticion; Object dueno=propietario;
        estado("Consultando");
        trabajador.execute(() -> {
            if(salidaSolicitada.get()) return; // Sólo se omite lectura aún no iniciada.
            ResultadoOperacion<InstantaneaTaller> r=null; InformeRecuperacion i=null; ContextoTaller.Ciclo c=null; Exception error=null;
            try { r=puerto.consultar(); c=puerto.ciclo(); i=puerto.informe(); } catch (Exception e) { error=e; }
            var resultado=r; var informe=i; var ciclo=c; var fallo=error;
            publicar.accept(() -> {
                if (!activo(g,dueno)) return;
                if (n<aplicada) return;
                lectura=false;
                if (fallo!=null) {
                    vigente=false; propuesta=null; pendiente=false;
                    if(commitConocido!=null) vista.mensaje("Operación conservada "+commitConocido+"; no se pudo actualizar la vista");
                    vista.mensaje("Lectura no acreditada. Ver detalle técnico. Puede solicitar una nueva consulta; no se repetirá la mutación.");
                    vista.diagnostico("Fallo de lectura: "+fallo);
                }
                else if (resultado==null || !Objects.equals(identidad,resultado.contexto())) desconocido(new IllegalStateException("Resultado ausente o contexto de respuesta ajeno"));
                else if (resultado.estado()==CONFIRMADA && resultado.valor()!=null && ciclo==ContextoTaller.Ciclo.HABILITADO) {
                    var s=resultado.valor();
                    if (!Objects.equals(identidad,s.dominio().contexto())) desconocido(new IllegalStateException("Captura ajena"));
                    else if (n>=aplicada && (ultima==null || s.revision()>=ultima.revision() && s.revisionRecuperacion()>=ultima.revisionRecuperacion())) {
                        aplicada=n; ultima=s; vigente=true; conciliandoRevisiones=false; propuesta=s.planificacion().propuesta().orElse(null); vista.captura(s);
                    } else { vigente=false; propuesta=null; pendiente=!conciliandoRevisiones; conciliandoRevisiones=true; vista.mensaje("Captura antigua o revisiones cruzadas; se requiere nueva lectura"); }
                } else { vigente=false; propuesta=null; if(commitConocido!=null)vista.mensaje("Operación conservada "+commitConocido+"; no se pudo actualizar la vista"); informar(resultado); if(ciclo!=ContextoTaller.Ciclo.HABILITADO) incierto=true; }
                if (informe!=null) vista.diagnostico("Último informe: "+informe);
                estado(vigente ? "Lectura confirmada" : "Última captura anterior / N/D vigente");
                if (pendiente && !incierto) refrescar();
            });
        });
    }
    private void desconocido(Exception e) {
        incierto=true; vigente=false; propuesta=null; pendiente=false;
        vista.mensaje("No se pudo determinar el resultado desde la interfaz. No se reintentará la mutación. Ver detalle técnico.");
        vista.diagnostico("Excepción sin resultado: "+e);
    }
    private void informar(ResultadoOperacion<?> r) {
        String texto=switch(r.estado()) {
            case CONFIRMADA -> "Operación conservada";
            case SIN_CAMBIO -> "Sin cambio";
            case RECHAZADA -> "Rechazada";
            case ERROR_TECNICO -> "Error técnico; estado vigente no acreditado";
            case PENDIENTE_VERIFICACION -> "Resultado pendiente de verificación";
        };
        String motivo=r.estado()==RECHAZADA || r.estado()==SIN_CAMBIO ? Objects.toString(r.diagnostico(), "") : "Ver diagnóstico completo en Salud / último informe.";
        vista.mensaje(texto+" | operación="+r.operacionId()+" | contexto="+r.contexto()+"\n"+motivo);
        if(r.diagnostico()!=null) vista.diagnostico("Operación "+r.operacionId()+": "+r.diagnostico());
        if (r.tecnico()!=null) vista.diagnostico("Detalle de operación: "+r.tecnico());
        if (r.estado()==PENDIENTE_VERIFICACION || r.tecnico()!=null && r.tecnico().salud()!=ContextoTaller.Ciclo.HABILITADO) incierto=true;
        if (r.estado()==ERROR_TECNICO || incierto) { vigente=false; propuesta=null; }
    }
    private void mutar(Supplier<ResultadoOperacion<?>> comando) {
        edt(); if(!habilitado()) return;
        mutando=true; vigente=false; propuesta=null; estado("Operación en curso");
        long g=generacion; Object dueno=propietario;
        trabajador.execute(() -> {
            ResultadoOperacion<?> r=null; Exception error=null;
            try { r=comando.get(); } catch(Exception e) { error=e; }
            var resultado=r; var fallo=error;
            publicar.accept(() -> {
                if(!activo(g,dueno)) {
                    if(cerrando && dueno==propietario) {
                        if(fallo!=null) { vista.mensaje("Resultado de operación durante cierre no determinado; ver detalle técnico."); vista.diagnostico(fallo.toString()); }
                        else if(resultado!=null && Objects.equals(identidad,resultado.contexto())) informar(resultado);
                    }
                    return;
                }
                mutando=false;
                if(fallo!=null) desconocido(fallo);
                else if (resultado==null || !Objects.equals(identidad,resultado.contexto())) desconocido(new IllegalStateException("Resultado ausente o contexto ajeno"));
                else { if(resultado.estado()==CONFIRMADA)commitConocido=resultado.operacionId(); informar(resultado); }
                estado("Resultado recibido; captura anterior");
                if(!incierto) refrescar();
            });
        });
    }
    public void registrarOrden(String id,String descripcion,Set<TipoHerramienta> tipos) {
        var copia=Set.copyOf(tipos); mutar(() -> puerto.registrarOrden(id,descripcion,copia));
    }
    public void registrarRecurso(Recurso.Categoria c,String id,TipoHerramienta t,boolean h) { mutar(() -> puerto.registrarRecurso(c,id,t,h)); }
    private boolean aceptar(String titulo,String detalle) {
        edt(); if(!habilitado()) return false;
        long g=generacion; dialogo=true; estado("Confirmación humana");
        boolean acepta;
        try { acepta=vista.confirmar(titulo,detalle); } finally { dialogo=false; }
        estado("Confirmación terminada");
        return acepta && g==generacion && habilitado();
    }
    public void confirmarInicio() {
        edt(); var exacta=propuesta;
        if(exacta!=null && aceptar("Iniciar reparación",exacta+"\nSin reserva. Se revalidan prioridad y unidades exactas."))
            mutar(() -> puerto.confirmarInicio(exacta));
    }
    public void transicionar(String id,EstadoOrden destino) {
        edt(); if(ultima==null || !habilitado()) return;
        var orden=ultima.dominio().ordenes().stream().filter(o -> o.id().equals(id)).findFirst().orElse(null);
        if(orden==null || !orden.estado().permite(destino) || destino==EstadoOrden.EN_CURSO) return;
        if(aceptar(destino.name(),"Orden exacta: "+id+"\nAcción: "+destino)) mutar(() -> switch(destino) {
            case FINALIZADA -> puerto.finalizar(id); case FALLIDA -> puerto.fallar(id); case CANCELADA -> puerto.cancelar(id);
            default -> throw new IllegalArgumentException("Acción no expuesta");
        });
    }
    public void habilitar(Recurso.Clave clave,boolean h) {
        edt(); if(ultima==null || !habilitado() || ultima.dominio().disponibilidad(clave)==Recurso.Disponibilidad.OCUPADO) return;
        if(aceptar(h?"Rehabilitar":"Poner fuera de servicio",clave.toString())) mutar(() -> puerto.cambiarHabilitacion(clave,h));
    }
    public void cerrar() {
        edt(); if(cierreEnCurso || cerrado) return;
        salidaSolicitada.set(true);
        cerrando=true; cierreEnCurso=true; generacion++; pendiente=false; vigente=false; propuesta=null; estado("Cerrando: drenando trabajo admitido");
        trabajador.execute(() -> {
            Exception error=null; InformeRecuperacion informe=null; ContextoTaller.Ciclo ciclo=null;
            try { puerto.cerrar(); ciclo=puerto.ciclo(); informe=puerto.informe(); } catch(Exception e) { error=e; }
            var fallo=error; var i=informe; var c=ciclo;
            publicar.accept(() -> {
                cierreEnCurso=false;
                if(fallo!=null || c!=ContextoTaller.Ciclo.CERRADO) {
                    vista.mensaje("Cierre no verificado; propietario retenido. Puede solicitar otra vez el cierre seguro. Ver detalle técnico.");
                    vista.diagnostico("Cierre: "+fallo+" | ciclo="+c);
                    estado("Cierre no verificado"); return;
                }
                vista.diagnostico("Informe final (no acredita por sí solo cierre limpio): "+i);
                if(i!=null && !i.hallazgos().isEmpty()) vista.advertenciaCierre("Advertencias al cerrar: "+i.hallazgos());
                cerrado=true; terminar.run(); vista.cerrado();
            });
        });
    }
}
