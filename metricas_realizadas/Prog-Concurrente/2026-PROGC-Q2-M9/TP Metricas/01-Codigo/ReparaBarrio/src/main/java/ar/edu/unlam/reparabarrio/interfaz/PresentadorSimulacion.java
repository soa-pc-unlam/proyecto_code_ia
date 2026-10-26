package ar.edu.unlam.reparabarrio.interfaz;

import ar.edu.unlam.reparabarrio.simulacion.*;
import ar.edu.unlam.reparabarrio.metricas.*;
import java.nio.file.Path;
import ar.edu.unlam.reparabarrio.arranque.ContextoEjecucion;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** Interacción confinada al EDT. Un ejecutor propio posee apertura/cálculo/cierre.
 * El token de generación y la identidad de corrida filtran entregas tardías. */
public final class PresentadorSimulacion {
    public interface Corrida extends AutoCloseable {
        String identidad();
        CompletableFuture<MotorSimulacion.Resultado> iniciar();
        MotorSimulacion.Progreso observar();
        void solicitarSalida();
        void close() throws Exception;
    }
    @FunctionalInterface public interface Fabrica {Corrida crear(ConfiguracionEscenario c) throws Exception;}
    private final VistaSimulacion vista;
    private final Fabrica fabrica;
    private final ExecutorService ciclo;
    private final Consumer<Runnable> publicar;
    private Corrida corrida; // Sólo ejecutor de ciclo; nunca se usa desde EDT.
    private long generacion,peticion,aplicada,revision=-1,tecnica=-1;
    private String identidad;
    private boolean activa,abriendo,leyendo,pendiente,cerrando,cierreEnCurso,cerrada;
    public PresentadorSimulacion(Path raiz,VistaSimulacion vista) {
        this(ContextoEjecucion.desarrollo(raiz),vista);
    }
    public PresentadorSimulacion(ContextoEjecucion ejecucion,VistaSimulacion vista) {
        this(vista,c -> adaptar(MotorSimulacion.crear(ejecucion,c)),Executors.newSingleThreadExecutor(Thread.ofPlatform().name("h6-propietario-",0).factory()),SwingUtilities::invokeLater);
    }
    public PresentadorSimulacion(VistaSimulacion vista,Fabrica fabrica,ExecutorService ciclo,Consumer<Runnable> publicar) {
        this.vista=vista;this.fabrica=fabrica;this.ciclo=ciclo;this.publicar=publicar;
    }
    private static Corrida adaptar(MotorSimulacion m){return new Corrida(){
        public String identidad(){return m.identidad();}public CompletableFuture<MotorSimulacion.Resultado> iniciar(){return m.iniciar();}
        public MotorSimulacion.Progreso observar(){return m.observar();}public void solicitarSalida(){m.solicitarSalida();}public void close() throws Exception{m.close();}
    };}
    private static void edt(){if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Se requiere EDT");}
    private boolean vigente(long g){return !cerrando && !cerrada && g==generacion;}
    private void encolar(Runnable r){try{ciclo.execute(r);}catch(RejectedExecutionException e){if(!ciclo.isShutdown())throw e;}}
    public void iniciar(ConfiguracionEscenario c) {
        iniciarPreparacion(()->c);
    }
    public void iniciarPreparacion(java.util.function.Supplier<ConfiguracionEscenario> preparacion) {
        edt();if(activa || abriendo || cerrando || cerrada)return;
        abriendo=true;activa=true;identidad=null;revision=-1;tecnica=-1;aplicada=0;long g=++generacion;
        vista.estado(false,"Preparando una simulación nueva; datos ficticios independientes");
        encolar(() -> {
            try {
                if(corrida!=null){corrida.close();corrida=null;}
                var c=preparacion.get();corrida=fabrica.crear(c);var duena=corrida;String id=duena.identidad();
                var f=duena.iniciar();
                publicar.accept(() -> {if(!vigente(g))return;abriendo=false;identidad=id;vista.estado(false,"Simulación en ejecución");refrescar();});
                f.whenComplete((r,e)->encolar(()-> {
                    var estadisticas=r==null || r.captura()==null?null:new CalculadorEstadisticas().calcular(r.captura(),r.comienzo(),r.fin(),r.motivo()!=MotorSimulacion.Motivo.ERROR_TECNICO);
                    publicar.accept(()-> {
                        if(!vigente(g) || !Objects.equals(id,identidad))return;
                        activa=false;abriendo=false;leyendo=false;pendiente=false;
                        // Invalida cualquier refresco de la corrida ya terminada.
                        aplicada=++peticion;
                        if(e!=null){vista.diagnostico(e.toString());vista.estado(true,"Resultado no acreditado; ver diagnóstico");return;}
                        if(r.captura()!=null && (!Objects.equals(id,r.captura().taller().dominio().contexto()) || r.captura().taller().revision()<revision || r.captura().taller().revisionRecuperacion()<tecnica)) {
                            vista.diagnostico("Resultado final ajeno o anterior: no se publica");vista.estado(true,"Resultado no acreditado; vigentes N/D");return;
                        }
                        if(r.captura()!=null)vista.mostrar(new MotorSimulacion.Progreso(id,Long.MAX_VALUE,r.comienzo(),0,0,0,r.registradas(),r.captura(),r.diagnostico(),r.comienzoCivil(),r.finCivil()),estadisticas,r);
                        if(r.diagnostico()!=null)vista.diagnostico(r.diagnostico());
                        vista.estado(true,switch(r.motivo()){case COMPLETADA->"Escenario completado";case SIN_PROGRESO->"Terminado con pendientes sin posibilidad de progreso";case ERROR_TECNICO->"Detenido por error técnico; vigentes N/D";case SALIDA_SOLICITADA->"Interrumpido por salida; carga no entregada: "+r.noEntregadas();});
                    });
                }));
            } catch(Exception e){publicar.accept(()->{if(!vigente(g))return;abriendo=false;activa=false;vista.diagnostico(e.toString());vista.estado(true,"Apertura/cierre no acreditados; propietario conservado si existe");});}
        });
    }
    public void refrescar(){
        edt();if(!activa || abriendo || cerrando || cerrada)return;
        if(leyendo){pendiente=true;return;}leyendo=true;pendiente=false;long g=generacion,n=++peticion;String id=identidad;
        encolar(()-> {
            MotorSimulacion.Progreso p=null;EstadisticasEscenario s=null;Exception error=null;
            try{p=corrida.observar();if(p.captura()!=null)s=new CalculadorEstadisticas().calcular(p.captura(),p.comienzo(),p.captura().nanos(),p.diagnostico()==null);}
            catch(Exception e){error=e;}
            var progreso=p;var estadisticas=s;var fallo=error;
            publicar.accept(()-> {
                if(!vigente(g) || !activa || n<aplicada)return;
                leyendo=false;
                if(fallo!=null)vista.diagnostico("Refresco no acreditado: "+fallo);
                else if(progreso!=null && Objects.equals(id,progreso.contexto()) && Objects.equals(id,identidad)) {
                    var c=progreso.captura();
                    if(c==null || Objects.equals(id,c.taller().dominio().contexto()) && c.taller().revision()>=revision && c.taller().revisionRecuperacion()>=tecnica){
                        aplicada=n;if(c!=null){revision=c.taller().revision();tecnica=c.taller().revisionRecuperacion();}vista.mostrar(progreso,estadisticas,null);
                    }
                }
                if(pendiente)refrescar();
            });
        });
    }
    /** El callback recibe éxito verificado. Ante fallo se permite repetir sólo cierre. */
    public void cerrar(Consumer<Boolean> terminado){
        edt();if(cerrada){terminado.accept(true);return;}if(cierreEnCurso)return;
        cerrando=true;cierreEnCurso=true;generacion++;pendiente=false;vista.estado(false,"Cerrando simulación: drenando obligaciones conocidas");
        encolar(()-> {
            Exception error=null;try{if(corrida!=null){corrida.solicitarSalida();corrida.close();corrida=null;}}catch(Exception e){error=e;}
            var fallo=error;
            publicar.accept(()->{cierreEnCurso=false;if(fallo!=null){vista.diagnostico("Cierre no verificado; propietario retenido: "+fallo);vista.estado(false,"Cierre pendiente; solicite nuevamente cierre seguro");terminado.accept(false);}
                else {cerrada=true;ciclo.shutdown();vista.estado(false,"Simulación cerrada");terminado.accept(true);}});
        });
    }
}
