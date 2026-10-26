package ar.edu.unlam.reparabarrio.interfaz;

import java.util.concurrent.*;
import javax.swing.SwingUtilities;

/** Un trabajador FIFO. Nunca se interrumpe una mutación; cerrar se encola detrás. */
public final class TrabajosSwing {
    private final ExecutorService ejecutor = Executors.newSingleThreadExecutor(r -> new Thread(r, "h5-taller"));
    public void ejecutar(Runnable trabajo) { ejecutor.execute(trabajo); }
    public void publicar(Runnable entrega) { SwingUtilities.invokeLater(entrega); }
    public void terminar() { ejecutor.shutdown(); }
}
