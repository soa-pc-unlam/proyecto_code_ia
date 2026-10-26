package ar.edu.unlam.reparabarrio.simulacion;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.arranque.FabricaContextos;
import ar.edu.unlam.reparabarrio.arranque.ContextoEjecucion;
import ar.edu.unlam.reparabarrio.dominio.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import static ar.edu.unlam.reparabarrio.aplicacion.ResultadoOperacion.Estado.*;

/** Propietario de UNA memoria nueva. Monitor breve -> liberar -> fachada.
 * Control separado de W reparaciones; scheduler sólo entrega fichas, nunca JDBC.
 * No contiene inventario ni decisiones de factibilidad. close se invoca fuera de EDT/pools. */
public final class MotorSimulacion implements AutoCloseable {
    public enum Motivo { COMPLETADA, SIN_PROGRESO, ERROR_TECNICO, SALIDA_SOLICITADA }
    public record Traza(String accion,String orden,String hilo,long inicio,long fin,String resultado) { }
    public record Resultado(Motivo motivo,CargaEscenario carga,HechosConfirmados.Captura captura,
                            long comienzo,long fin,int registradas,int noEntregadas,String diagnostico,List<Traza> trazas,
                            java.time.Instant comienzoCivil,java.time.Instant finCivil) {
        public Resultado {trazas=List.copyOf(trazas);}
        public Resultado(Motivo motivo,CargaEscenario carga,HechosConfirmados.Captura captura,long comienzo,long fin,int registradas,int noEntregadas,String diagnostico,List<Traza> trazas){this(motivo,carga,captura,comienzo,fin,registradas,noEntregadas,diagnostico,trazas,null,null);}
    }
    public record Progreso(String contexto,long version,long comienzo,int futuras,int registrosEnVuelo,int trabajadores,
                           int registradas,HechosConfirmados.Captura captura,String diagnostico,
                           java.time.Instant comienzoCivil,java.time.Instant observacionCivil) {
        public Progreso(String contexto,long version,long comienzo,int futuras,int registrosEnVuelo,int trabajadores,int registradas,HechosConfirmados.Captura captura,String diagnostico){this(contexto,version,comienzo,futuras,registrosEnVuelo,trabajadores,registradas,captura,diagnostico,null,null);}
    }
    @FunctionalInterface interface Espera {void esperar(String id,long millis) throws Exception;}
    private final ContextoTaller contexto;
    private final CargaEscenario carga;
    private final ExecutorService control=Executors.newFixedThreadPool(2,Thread.ofPlatform().name("h6-control-",0).factory());
    private final ExecutorService trabajos;
    private final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().name("h6-llegadas-",0).factory());
    private final Espera espera;
    private final java.util.function.BiConsumer<String,String> antesDeLlamada;
    private final Object monitor=new Object();
    private final Set<String> activos=new HashSet<>();
    private final Map<String,CargaEscenario.EntradaOrden> entradas=new HashMap<>();
    private final List<Traza> trazas=new ArrayList<>();
    private final List<Llegada> llegadas=new ArrayList<>();
    private final CompletableFuture<Resultado> fin=new CompletableFuture<>();
    private int futuras,registros,registradas,noEntregadas;
    private long version,t0;
    private java.time.Instant comienzoCivil;
    private boolean iniciada,preparando,bombeando,sucia,salida,terminal;
    private String error;
    private HechosConfirmados.Captura ultima;
    private final Map<String,Long> rechazos=new HashMap<>();
    private static final class Llegada {final CargaEscenario.EntradaOrden orden;boolean pendiente=true;ScheduledFuture<?> futuro;Llegada(CargaEscenario.EntradaOrden o){orden=o;}}

    /** Genera ANTES de abrir la única memoria nueva; no admite servicio/URL operativos. */
    public static MotorSimulacion crear(Path raiz,ConfiguracionEscenario configuracion) throws Exception {
        return crear(ContextoEjecucion.desarrollo(raiz),configuracion);
    }
    public static MotorSimulacion crear(ContextoEjecucion ejecucion,ConfiguracionEscenario configuracion) throws Exception {
        var carga=new GeneradorCarga().generar(configuracion);
        return new MotorSimulacion(FabricaContextos.crearSimulacion(ejecucion,true),carga,MotorSimulacion::esperarDuracion);
    }
    MotorSimulacion(ContextoTaller contexto,CargaEscenario carga,Espera espera) {
        this(contexto,carga,espera,(accion,id)->{});
    }
    // Control focal de tests antes de la fachada, siempre fuera de los locks del motor/núcleo.
    MotorSimulacion(ContextoTaller contexto,CargaEscenario carga,Espera espera,java.util.function.BiConsumer<String,String> antes) {
        this.contexto=contexto;this.carga=carga;this.espera=espera;this.antesDeLlamada=antes;
        trabajos=Executors.newFixedThreadPool(carga.configuracion().trabajadores(),Thread.ofPlatform().name("h6-reparacion-",0).factory());
        carga.ordenes().forEach(o -> entradas.put(o.id(),o));
    }
    private static void esperarDuracion(String id,long millis) throws InterruptedException {
        long comienzo=System.nanoTime(),duracion=Math.multiplyExact(millis,1_000_000L),restante=duracion;
        while(restante>0){TimeUnit.NANOSECONDS.sleep(restante);restante=duracion-(System.nanoTime()-comienzo);}
    }
    public String identidad(){return contexto.identidad();}
    public ContextoTaller.Ciclo ciclo(){return contexto.ciclo();}
    public CompletableFuture<Resultado> resultado(){return fin.copy();}
    public Progreso progreso(){synchronized(monitor){return new Progreso(identidad(),version,t0,futuras,registros,activos.size(),registradas,ultima,error,comienzoCivil,java.time.Instant.now());}}
    /** Refresco de presentación, sin afectar contabilidad ni provocar replanificación. */
    public Progreso observar(){
        synchronized(monitor){if(terminal || preparando || !iniciada)return progreso();}
        HechosConfirmados.Captura captura=null;
        if(contexto.ciclo()==ContextoTaller.Ciclo.HABILITADO){
            var r=contexto.servicio().consultarHechos();
            if(r.estado()==CONFIRMADA){captura=r.valor();if(captura.incompleta()!=null){fallarTecnico(captura.incompleta());solicitar();}}
            else {fallarTecnico("Refresco no acreditado: "+r);solicitar();}
        } else {fallarTecnico("Contexto no habilitado durante refresco");solicitar();}
        synchronized(monitor){if(!terminal && captura!=null && (ultima==null || captura.taller().revision()>=ultima.taller().revision()))ultima=captura;return progreso();}
    }
    public CompletableFuture<Resultado> iniciar() {
        synchronized(monitor){if(iniciada)throw new IllegalStateException("Escenario ya iniciado");iniciada=true;preparando=true;}
        control.execute(() -> {
            try {
                exigirSano();
                for(var r:carga.configuracion().recursos()) {
                    synchronized(monitor){if(salida || error!=null)break;}
                    exigir(llamar("ALTA",r.clave().id(),()->contexto.servicio().registrarRecurso(r.clave().categoria(),r.clave().id(),r.tipo(),r.habilitado())));
                }
                var base=contexto.servicio().consultarHechos();exigir(base);
                synchronized(monitor){ultima=base.valor();t0=ultima.nanos();comienzoCivil=java.time.Instant.now();}
                // Se contabilizan TODAS las llegadas antes de publicar el primer vencimiento.
                synchronized(monitor){
                    if(!salida && error==null)for(var o:carga.ordenes()){llegadas.add(new Llegada(o));futuras++;}
                    else noEntregadas=carga.ordenes().size();
                    for(var l:llegadas)l.futuro=scheduler.schedule(()->entregar(l),Math.max(0,l.orden.llegadaMs()*1_000_000L-(System.nanoTime()-t0)),TimeUnit.NANOSECONDS);
                }
            } catch(Exception e){fallarTecnico("Preparación: "+e);}
            finally {synchronized(monitor){preparando=false;version++;}solicitar();}
        });
        return resultado();
    }
    private void entregar(Llegada l) {
        synchronized(monitor){
            if(!l.pendiente)return;
            l.pendiente=false;futuras--;
            if(salida || error!=null){noEntregadas++;version++;return;}
            registros++;version++;
        }
        control.execute(() -> {
            try {
                boolean admitir;synchronized(monitor){admitir=error==null;}
                if(admitir){exigir(llamar("REGISTRO",l.orden.id(),()->contexto.servicio().registrarOrden(l.orden.id(),l.orden.descripcion(),l.orden.requisitos())));synchronized(monitor){registradas++;}}
                else synchronized(monitor){noEntregadas++;}
            } catch(Exception e){fallarTecnico("Registro: "+e);}
            finally{synchronized(monitor){registros--;version++;}solicitar();}
        });
    }
    private <T> ResultadoOperacion<T> llamar(String accion,String orden,Supplier<ResultadoOperacion<T>> operacion) {
        long inicio=System.nanoTime();String resultado="EXCEPCION";
        try{antesDeLlamada.accept(accion,orden);var r=operacion.get();resultado=r.estado()+":"+r.operacionId();return r;}
        finally{var traza=new Traza(accion,orden,Thread.currentThread().getName(),inicio,System.nanoTime(),resultado);synchronized(monitor){trazas.add(traza);}}
    }
    private void exigirSano(){if(contexto.ciclo()!=ContextoTaller.Ciclo.HABILITADO)throw new IllegalStateException("Contexto no habilitado: "+contexto.informe());}
    private void exigir(ResultadoOperacion<?> r){if(r.estado()!=CONFIRMADA)throw new IllegalStateException(r.toString());exigirSano();}
    private void solicitar(){
        synchronized(monitor){sucia=true;if(terminal || preparando || bombeando)return;bombeando=true;}
        control.execute(this::bombear);
    }
    private void bombear() {
        try {
            while(true){
                long v;boolean hayError;
                synchronized(monitor){sucia=false;v=version;hayError=error!=null;}
                HechosConfirmados.Captura captura=null;
                if(!hayError) {
                    var r=llamar("CONSULTA",null,()->contexto.servicio().consultarHechos());exigir(r);captura=r.valor();
                    if(captura.incompleta()!=null)throw new IllegalStateException(captura.incompleta());
                } else if(contexto.ciclo()==ContextoTaller.Ciclo.HABILITADO) {
                    try {var r=contexto.servicio().consultarHechos();if(r.estado()==CONFIRMADA)captura=r.valor();}
                    catch(RuntimeException e){synchronized(monitor){error+="; captura final no acreditada: "+e;}}
                }
                PropuestaInicio propuesta=null;Resultado terminado=null;
                synchronized(monitor){
                    if(captura!=null)ultima=captura;
                    if(version==v && !preparando){
                        if(error==null && !salida && captura!=null && activos.size()<carga.configuracion().trabajadores()) {
                            var p=captura.taller().planificacion().propuesta().orElse(null);
                            if(p!=null && activos.add(p.ordenId())){propuesta=p;version++;}
                        }
                        if(propuesta==null && futuras==0 && registros==0 && activos.isEmpty()) {
                            boolean enCurso=ultima!=null && ultima.taller().dominio().ordenes().stream().anyMatch(o->o.estado()==EstadoOrden.EN_CURSO);
                            if(enCurso && error==null)error="EN_CURSO sin obligación de trabajador";
                            if(error==null && !salida && ultima.taller().planificacion().propuesta().isPresent())error="Motor ocioso con propuesta factible";
                            var motivo=error!=null?Motivo.ERROR_TECNICO:salida?Motivo.SALIDA_SOLICITADA:
                                    ultima.taller().dominio().ordenes().stream().anyMatch(o->o.estado()==EstadoOrden.PENDIENTE)?Motivo.SIN_PROGRESO:Motivo.COMPLETADA;
                            long tf=error!=null || ultima==null?System.nanoTime():ultima.nanos();if(t0==0)t0=tf;
                            terminal=true;terminado=new Resultado(motivo,carga,ultima,t0,tf,registradas,noEntregadas,error,trazas,comienzoCivil,java.time.Instant.now());
                        }
                    } else sucia=true;
                }
                if(propuesta!=null){var p=propuesta;try{trabajos.execute(()->reparar(p));}catch(RejectedExecutionException e){synchronized(monitor){activos.remove(p.ordenId());version++;}throw e;}}
                if(terminado!=null){scheduler.shutdown();trabajos.shutdown();fin.complete(terminado);return;}
                synchronized(monitor){if(!sucia){bombeando=false;return;}}
            }
        } catch(Exception e){fallarTecnico("Control: "+e);synchronized(monitor){bombeando=false;}solicitar();}
        finally{synchronized(monitor){if(terminal)bombeando=false;}}
    }
    private void reparar(PropuestaInicio p) {
        boolean iniciadaConocida=false,cierreIntentado=false;
        try {
            // Una salida retira inicios todavía no admitidos; uno ya invocado se drena.
            synchronized(monitor){if(salida || error!=null)return;}
            var r=llamar("INICIO",p.ordenId(),()->contexto.servicio().confirmarInicio(p));
            if(r.estado()==RECHAZADA){
                var s=contexto.servicio().consultar();exigir(s);
                synchronized(monitor){Long anterior=rechazos.put(p.ordenId(),s.valor().revision());if(Objects.equals(anterior,s.valor().revision()))throw new IllegalStateException("Rechazo repetido sin cambio");}
                return;
            }
            iniciadaConocida=r.estado()==CONFIRMADA;exigir(r);
            synchronized(monitor){version++;}solicitar();
            var entrada=entradas.get(p.ordenId());long inicio=System.nanoTime();
            try{espera.esperar(p.ordenId(),entrada.duracionMs());}
            finally{synchronized(monitor){trazas.add(new Traza("TRABAJO",p.ordenId(),Thread.currentThread().getName(),inicio,System.nanoTime(),"duración ficticia"));}}
            exigirSano();cierreIntentado=true;
            exigir(llamar(entrada.falla()?"FALLIDA":"FINALIZADA",p.ordenId(),()->entrada.falla()?contexto.servicio().fallar(p.ordenId()):contexto.servicio().finalizar(p.ordenId())));
        } catch(Exception e){
            // Incidente previo al intento de cierre: sólo un fallo técnico con consulta sana.
            if(iniciadaConocida && !cierreIntentado && contexto.ciclo()==ContextoTaller.Ciclo.HABILITADO) {
                Thread.interrupted();
                var s=contexto.servicio().consultar();
                if(s.estado()==CONFIRMADA && contexto.ciclo()==ContextoTaller.Ciclo.HABILITADO)
                    try{exigir(llamar("FALLO_POR_INCIDENTE",p.ordenId(),()->contexto.servicio().fallar(p.ordenId())));}catch(Exception cierre){e.addSuppressed(cierre);}
            }
            fallarTecnico("Trabajador "+p.ordenId()+": "+e);
        } finally {synchronized(monitor){activos.remove(p.ordenId());version++;}solicitar();}
    }
    private void retirarFuturas(){
        for(var l:llegadas)if(l.pendiente){l.pendiente=false;if(l.futuro!=null)l.futuro.cancel(false);futuras--;noEntregadas++;}
    }
    private void fallarTecnico(String motivo){synchronized(monitor){if(error==null)error=motivo;retirarFuturas();version++;}}
    public void solicitarSalida(){boolean arrancada;synchronized(monitor){if(terminal)return;salida=true;retirarFuturas();version++;arrancada=iniciada;}if(arrancada)solicitar();}
    @Override public void close() throws Exception {
        if(javax.swing.SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Cierre fuera de EDT requerido");
        boolean arrancada;synchronized(monitor){arrancada=iniciada;}
        if(arrancada){solicitarSalida();fin.get();}
        scheduler.shutdown();trabajos.shutdown();control.shutdown();
        if(!scheduler.awaitTermination(10,TimeUnit.SECONDS) || !trabajos.awaitTermination(10,TimeUnit.SECONDS) || !control.awaitTermination(10,TimeUnit.SECONDS))
            throw new IllegalStateException("Ejecutores sin drenar; propietario retenido");
        contexto.close();
        if(contexto.ciclo()!=ContextoTaller.Ciclo.CERRADO)throw new IllegalStateException("Cierre no verificado");
    }
}
