package ar.edu.unlam.reparabarrio.interfaz;
import ar.edu.unlam.reparabarrio.simulacion.*;
import ar.edu.unlam.reparabarrio.metricas.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import org.junit.jupiter.api.*;
import javax.swing.SwingUtilities;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Presentación real + memoria real; no abre JFrame ni sustituye recorrido humano Windows. */
@Timeout(30)
class IntegracionSimulacionH6Test {
    @Test void corridaNuevaYCierreActivoConH2RealFueraDeEdt() throws Exception {
        var terminado=new CountDownLatch(1);var enCurso=new CountDownLatch(1);var cerrado=new CountDownLatch(1);
        var identidades=new HashSet<String>();var error=new AtomicReference<Throwable>();var fase=new AtomicInteger();
        var vista=new VistaSimulacion(){
            public void estado(boolean iniciar,String texto){try{assertTrue(SwingUtilities.isEventDispatchThread());if(texto.equals("Escenario completado"))terminado.countDown();}catch(Throwable e){error.set(e);}}
            public void mostrar(MotorSimulacion.Progreso p,EstadisticasEscenario s,MotorSimulacion.Resultado r){try{
                assertTrue(SwingUtilities.isEventDispatchThread());identidades.add(p.contexto());if(s!=null)assertNull(s.incompleta(),s::toString);
                if(fase.get()==1 && p.captura()!=null && p.captura().taller().dominio().ordenes().stream().anyMatch(o->o.estado()==EstadoOrden.EN_CURSO))enCurso.countDown();
            }catch(Throwable e){error.set(e);}}
            public void diagnostico(String s){error.set(new AssertionError(s));}
        };
        var p=new PresentadorSimulacion(Path.of("").toAbsolutePath().normalize(),vista);
        var base=new ConfiguracionEscenario(1,3,2,ConfiguracionEscenario.inventario(2,2,0,0,0,true),List.of(1,0,0,0,0,0,0,0),ConfiguracionEscenario.Llegadas.SIMULTANEAS,0,0,5,5,0);
        try {
            SwingUtilities.invokeAndWait(()->p.iniciar(base));assertTrue(terminado.await(10,TimeUnit.SECONDS));
            fase.set(1);var larga=new ConfiguracionEscenario(1,3,2,base.recursos(),base.pesos(),base.llegadas(),0,0,500,500,0);
            SwingUtilities.invokeAndWait(()->p.iniciar(larga));
            // No hay Timer Swing en este fixture: solicitar capturas con latidos explícitos.
            var timer=new javax.swing.Timer(20,e->p.refrescar());SwingUtilities.invokeAndWait(timer::start);
            try{assertTrue(enCurso.await(10,TimeUnit.SECONDS));}finally{SwingUtilities.invokeAndWait(timer::stop);}
            SwingUtilities.invokeAndWait(()->{assertEquals(2,identidades.size());p.cerrar(ok->{if(!ok)error.set(new AssertionError("Cierre no acreditado"));cerrado.countDown();});});
            assertTrue(cerrado.await(10,TimeUnit.SECONDS));assertNull(error.get(),()->Objects.toString(error.get()));
        } finally {
            SwingUtilities.invokeAndWait(()->p.cerrar(ok->cerrado.countDown()));assertTrue(cerrado.await(10,TimeUnit.SECONDS));
        }
    }
}
