package ar.edu.unlam.reparabarrio.arranque;

import ar.edu.unlam.reparabarrio.interfaz.VentanaPrincipal;
import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

/** Smoke ejecutable sin JUnit: main real y cierre previo al consentimiento.
 * No acredita inspección visual humana ni recorrido JDBC. No crea una sesión. */
public final class VerificarArranqueH5 {
    public static void main(String[] args) throws Exception {
        ReparaBarrioApp.main(new String[]{"--raiz",args[0],"--academico-nuevo"});
        var ventanas=new AtomicInteger();
        SwingUtilities.invokeAndWait(() -> {
            for(var w:Window.getWindows())if(w instanceof JFrame f && f.isShowing()) {
                if(!f.getTitle().contains("arranque académico"))throw new AssertionError(f.getTitle());
                ventanas.incrementAndGet();System.out.println("H5 MAIN_REAL ventana de consentimiento visible; aún sin crear base");
                f.dispatchEvent(new WindowEvent(f,WindowEvent.WINDOW_CLOSING));
                if(f.isDisplayable())throw new AssertionError("No cerró arranque");
            }
            // Construcción real de componentes: sólo presentación, rótulo inequívoco sin base.
            var v=new VentanaPrincipal("VERIFICACIÓN SIN BASE",Path.of(args[0]).resolve("target/h5-SIN-BASE"));
            if(v.getContentPane().getComponentCount()!=3)throw new AssertionError("Composición incompleta");v.dispose();
        });
        if(ventanas.get()!=1)throw new AssertionError("No se observó una ventana de arranque");
        System.out.println("H5 SMOKE_OK main y componentes Swing, cierre sin JUnit; no es validación visual ni funcional H2");
    }
}
