package ar.edu.unlam.reparabarrio.interfaz;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** Sólo EDT. Ningún propietario puede disponer la ventana mientras el otro vive. */
public final class CierreConjunto {
    private final Runnable cerrarOperativo,disponer;
    private final Consumer<Consumer<Boolean>> cerrarSimulacion;
    private boolean operativo,simulacion,dispuesta;
    public CierreConjunto(Runnable operativo,Consumer<Consumer<Boolean>> simulacion,Runnable disponer){this.cerrarOperativo=operativo;this.cerrarSimulacion=simulacion;this.disponer=disponer;}
    public void solicitar(){edt();cerrarSimulacion.accept(ok->{edt();if(ok)simulacion=true;comprobar();});cerrarOperativo.run();}
    public void operativoCerrado(){edt();operativo=true;comprobar();}
    private void comprobar(){if(operativo && simulacion && !dispuesta){dispuesta=true;disponer.run();}}
    private static void edt(){if(!SwingUtilities.isEventDispatchThread())throw new IllegalStateException("Se requiere EDT");}
}
