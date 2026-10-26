package ar.edu.unlam.reparabarrio.interfaz;
import ar.edu.unlam.reparabarrio.simulacion.*;
import ar.edu.unlam.reparabarrio.metricas.*;
import org.junit.jupiter.api.*;
import javax.swing.SwingUtilities;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class PresentadorSimulacionTest {
    private final ConfiguracionEscenario config=new ConfiguracionEscenario(1,0,1,List.of(),Collections.nCopies(8,1),ConfiguracionEscenario.Llegadas.SIMULTANEAS,0,0,0,0,0);
    private static void edt(Runnable r) throws Exception{SwingUtilities.invokeAndWait(r);}
    private static Runnable siguiente(BlockingQueue<Runnable> cola) throws Exception{var r=cola.poll(5,TimeUnit.SECONDS);assertNotNull(r,"No llegó publicación");return r;}
    private static final class Vista implements VistaSimulacion {
        final List<String> estados=new ArrayList<>();final List<String> identidades=new ArrayList<>();
        public void estado(boolean iniciar,String texto){assertTrue(SwingUtilities.isEventDispatchThread());estados.add(texto);}
        public void mostrar(MotorSimulacion.Progreso p,EstadisticasEscenario e,MotorSimulacion.Resultado r){assertTrue(SwingUtilities.isEventDispatchThread());identidades.add(p.contexto());}
        public void diagnostico(String texto){assertTrue(SwingUtilities.isEventDispatchThread());}
    }
    private final class Corrida implements PresentadorSimulacion.Corrida {
        final String id;final CompletableFuture<MotorSimulacion.Resultado> f=new CompletableFuture<>();int cierres;boolean falloCierre;
        Corrida(String id){this.id=id;}
        public String identidad(){assertFalse(SwingUtilities.isEventDispatchThread());return id;}
        public CompletableFuture<MotorSimulacion.Resultado> iniciar(){assertFalse(SwingUtilities.isEventDispatchThread());return f;}
        public MotorSimulacion.Progreso observar(){assertFalse(SwingUtilities.isEventDispatchThread());return new MotorSimulacion.Progreso(id,1,0,0,0,0,0,null,null);}
        public void solicitarSalida(){assertFalse(SwingUtilities.isEventDispatchThread());}
        void completar(){f.complete(new MotorSimulacion.Resultado(MotorSimulacion.Motivo.COMPLETADA,new GeneradorCarga().generar(config),null,0,0,0,0,null,List.of()));}
        public void close(){assertFalse(SwingUtilities.isEventDispatchThread());cierres++;if(falloCierre)throw new IllegalStateException("fallo inyectado");completar();}
    }
    @Test void dobleInicioRefrescoAgrupadoYRespuestaViejaNoActualizaNueva() throws Exception {
        var cola=new LinkedBlockingQueue<Runnable>();var vista=new Vista();var executor=Executors.newSingleThreadExecutor();
        var a=new Corrida("A");var b=new Corrida("B");var aperturas=new AtomicInteger();
        var p=new PresentadorSimulacion(vista,c->aperturas.getAndIncrement()==0?a:b,executor,cola::add);
        try {
            edt(()->{p.iniciar(config);p.iniciar(config);});edt(siguiente(cola)); // apertura A; solicita lectura
            var antigua=siguiente(cola);edt(()->{p.refrescar();p.refrescar();});assertEquals(1,aperturas.get());
            a.completar();edt(siguiente(cola)); // final, invalida lectura retenida
            edt(()->p.iniciar(config));edt(siguiente(cola));edt(siguiente(cola));
            assertEquals(List.of("B"),vista.identidades);edt(antigua);assertEquals(List.of("B"),vista.identidades);assertEquals(1,a.cierres);
            var cerrado=new AtomicBoolean();edt(()->p.cerrar(cerrado::set));edt(siguiente(cola));assertTrue(cerrado.get());assertEquals(1,b.cierres);
        } finally {executor.shutdown();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
    }
    @Test void aperturaTardiaYCierreFallidoConservanPropietarioSinBloquearEdt() throws Exception {
        var cola=new LinkedBlockingQueue<Runnable>();var vista=new Vista();var executor=Executors.newSingleThreadExecutor();
        var entro=new CountDownLatch(1);var liberar=new CountDownLatch(1);var a=new Corrida("A");a.falloCierre=true;
        var p=new PresentadorSimulacion(vista,c->{assertFalse(SwingUtilities.isEventDispatchThread());entro.countDown();assertTrue(liberar.await(5,TimeUnit.SECONDS));return a;},executor,cola::add);
        try {
            edt(()->p.iniciar(config));assertTrue(entro.await(5,TimeUnit.SECONDS));var vivo=new AtomicBoolean();var cerrado=new AtomicBoolean();
            edt(()->{vivo.set(true);p.cerrar(cerrado::set);});assertTrue(vivo.get());liberar.countDown();
            edt(siguiente(cola));edt(siguiente(cola));assertFalse(cerrado.get());assertTrue(vista.identidades.isEmpty());
            a.falloCierre=false;edt(()->p.cerrar(cerrado::set));
            // Puede estar encolado el final antiguo; ninguna entrega vuelve a activar la vista.
            while(!cerrado.get())edt(siguiente(cola));assertEquals(2,a.cierres);assertTrue(vista.identidades.isEmpty());
        } finally {liberar.countDown();executor.shutdown();assertTrue(executor.awaitTermination(5,TimeUnit.SECONDS));}
    }
    @Test void ventanaSoloDisponeConAmbosPropietariosCerrados() throws Exception {
        var disposiciones=new AtomicInteger();var op=new AtomicInteger();var callback=new AtomicReference<java.util.function.Consumer<Boolean>>();
        var c=new CierreConjunto(op::incrementAndGet,callback::set,disposiciones::incrementAndGet);
        edt(()->{c.solicitar();c.operativoCerrado();});assertEquals(0,disposiciones.get());edt(()->callback.get().accept(false));assertEquals(0,disposiciones.get());
        edt(c::solicitar);edt(()->callback.get().accept(true));assertEquals(1,disposiciones.get());edt(c::operativoCerrado);assertEquals(1,disposiciones.get());
    }
}
