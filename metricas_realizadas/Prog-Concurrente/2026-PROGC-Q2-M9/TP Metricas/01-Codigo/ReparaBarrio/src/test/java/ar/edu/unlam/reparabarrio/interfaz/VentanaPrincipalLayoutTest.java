package ar.edu.unlam.reparabarrio.interfaz;

import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

class VentanaPrincipalLayoutTest {
    @Test void focoRealYSeleccionPersistenDespuesDeRefrescosDiferidos() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(),"Requiere sesión gráfica externa");
        var ventana=new JFrame[1];var panel=new PanelSimulacion[1];var campo=new JTextField[1];
        var foco=new java.util.concurrent.CountDownLatch(1);
        try {
            SwingUtilities.invokeAndWait(()->{
                panel[0]=new PanelSimulacion();ventana[0]=new JFrame();ventana[0].setContentPane(panel[0]);ventana[0].setSize(800,600);ventana[0].setVisible(true);
                campo[0]=PanelSimulacionLayoutTest.componentes(panel[0],JTextField.class).stream().filter(t->t.getText().equals("20261015")).findFirst().orElseThrow();
                campo[0].addFocusListener(new java.awt.event.FocusAdapter(){public void focusGained(java.awt.event.FocusEvent e){foco.countDown();}});campo[0].requestFocusInWindow();
            });
            org.junit.jupiter.api.Assumptions.assumeTrue(foco.await(3,java.util.concurrent.TimeUnit.SECONDS),"El entorno no concedió foco: PENDIENTE DE VALIDACIÓN EXTERNA WINDOWS");
            var posicion=new Point[1];
            SwingUtilities.invokeAndWait(()->{campo[0].select(2,5);var viewport=((JScrollPane)panel[0].getComponent(0)).getViewport();posicion[0]=viewport.getViewPosition();panel[0].mostrar(PanelSimulacionLayoutTest.captura(100),null,null);});
            SwingUtilities.invokeAndWait(()->{panel[0].mostrar(PanelSimulacionLayoutTest.captura(101),null,null);});
            SwingUtilities.invokeAndWait(()->{assertSame(campo[0],KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());assertEquals("261",campo[0].getSelectedText());assertEquals(posicion[0],((JScrollPane)panel[0].getComponent(0)).getViewport().getViewPosition());});
        } finally {SwingUtilities.invokeAndWait(()->{if(ventana[0]!=null)ventana[0].dispose();});}
    }
    @Test void cierreYResultadosAlcanzablesSinServiciosNiBase() throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(),"PENDIENTE DE VALIDACIÓN EXTERNA WINDOWS: requiere sesión gráfica");
        SwingUtilities.invokeAndWait(()->{
            var v=new VentanaPrincipal("fixture-layout",Path.of("target/h5-layout-sin-base"));
            try {
                var gc=v.getGraphicsConfiguration();var pantalla=gc.getBounds();var insets=Toolkit.getDefaultToolkit().getScreenInsets(gc);
                assertTrue(v.getWidth()<=pantalla.width-insets.left-insets.right);assertTrue(v.getHeight()<=pantalla.height-insets.top-insets.bottom);
                v.setVisible(true);
                for(var tam:java.util.List.of(new Dimension(1000,750),new Dimension(800,600),new Dimension(400,300))){
                    v.setSize(tam);v.validate();PanelSimulacionLayoutTest.layout(v);
                    var scroll=(JScrollPane)v.getContentPane();var viewport=scroll.getViewport();
                    for(String texto:java.util.List.of("Cerrar sesión de forma segura","Consultar primera factible / refrescar")){
                        var boton=PanelSimulacionLayoutTest.componentes(v,JButton.class).stream().filter(b->texto.equals(b.getText())).findFirst().orElseThrow();
                        var rect=SwingUtilities.convertRectangle(boton.getParent(),boton.getBounds(),viewport.getView());
                        ((JComponent)viewport.getView()).scrollRectToVisible(rect);assertTrue(viewport.getViewRect().contains(rect),()->texto+" "+rect+" / "+viewport.getViewRect());assertTrue(boton.isFocusable());
                    }
                    var bounds=v.getBounds();v.mensaje("Resultado ".repeat(100));v.diagnostico("Informe ".repeat(100));assertEquals(bounds,v.getBounds());
                }
                v.setExtendedState(Frame.MAXIMIZED_BOTH);v.setExtendedState(Frame.NORMAL);
            } finally {v.dispose();}
        });
    }
}
