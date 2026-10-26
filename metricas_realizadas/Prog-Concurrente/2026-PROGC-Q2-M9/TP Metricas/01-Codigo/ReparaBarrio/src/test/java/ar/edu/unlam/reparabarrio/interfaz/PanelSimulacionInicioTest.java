package ar.edu.unlam.reparabarrio.interfaz;

import ar.edu.unlam.reparabarrio.simulacion.MotorSimulacion;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Ruta de ActionEvent real, sin JDBC. No reproduce foco ni clic físico. */
class PanelSimulacionInicioTest {
    @Test void focoPorRatonNoDesplazaYTecladoConservaVisibilidadSinListenerDuplicado() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var panel=new PanelSimulacion();panel.setSize(360,260);PanelSimulacionLayoutTest.layout(panel);
            var boton=PanelSimulacionLayoutTest.componentes(panel,JButton.class).stream()
                .filter(b->"Iniciar escenario nuevo".equals(b.getText())).findFirst().orElseThrow();
            int listeners=boton.getFocusListeners().length;
            PanelSimulacion.enfocarVisible(panel);
            assertEquals(listeners,boton.getFocusListeners().length);
            assertTrue(boton.isFocusable());
            var viewport=((JScrollPane)panel.getComponent(0)).getViewport();
            var rect=SwingUtilities.convertRectangle(boton.getParent(),boton.getBounds(),viewport.getView());
            viewport.setViewPosition(new java.awt.Point(60,rect.y));
            var antes=viewport.getViewPosition();
            var raton=new java.awt.event.FocusEvent(boton,java.awt.event.FocusEvent.FOCUS_GAINED,false,null,java.awt.event.FocusEvent.Cause.MOUSE_EVENT);
            for(var listener:boton.getFocusListeners())listener.focusGained(raton);
            assertEquals(antes,viewport.getViewPosition());
            var teclado=new java.awt.event.FocusEvent(boton,java.awt.event.FocusEvent.FOCUS_GAINED,false,null,java.awt.event.FocusEvent.Cause.TRAVERSAL_FORWARD);
            for(var listener:boton.getFocusListeners())listener.focusGained(teclado);
            assertTrue(viewport.getViewRect().contains(rect));
            assertNotEquals(antes,viewport.getViewPosition());
        });
    }
    @Test void spinnerInvalidoInformaErrorSinIniciar() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var panel=new PanelSimulacion();
            // Un presentador sin ejecutor basta: la configuraci?n inválida debe detenerse antes de invocarlo.
            panel.conectar(new PresentadorSimulacion(panel,c->{throw new AssertionError("No debe crear corrida");},null,null));
            panel.detenerRefresco();
            var etiqueta=PanelSimulacionLayoutTest.componentes(panel,JLabel.class).stream()
                .filter(l->"Órdenes".equals(l.getText())).findFirst().orElseThrow();
            ((JSpinner.DefaultEditor)((JSpinner)etiqueta.getLabelFor()).getEditor()).getTextField().setText("inválido");
            var boton=PanelSimulacionLayoutTest.componentes(panel,JButton.class).stream()
                .filter(b->"Iniciar escenario nuevo".equals(b.getText())).findFirst().orElseThrow();
            boton.doClick(0);
            assertTrue(boton.isEnabled());
            assertTrue(PanelSimulacionLayoutTest.componentes(panel,JTextArea.class).stream()
                .anyMatch(a->a.getText().startsWith("Configuración inválida: Valor numérico inválido")));
        });
    }
    @Test void unaActivacionDelBotonCreaEIniciaUnaSolaCorridaDeOchoOrdenes() throws Exception {
        var ciclo=Executors.newSingleThreadExecutor();
        var creaciones=new AtomicInteger();var inicios=new AtomicInteger();
        var inicio=new CountDownLatch(1);var cierre=new CountDownLatch(1);
        var panel=new PanelSimulacion[1];var presentador=new PresentadorSimulacion[1];
        var boton=new JButton[1];
        try {
            SwingUtilities.invokeAndWait(()->{
                panel[0]=new PanelSimulacion();
                presentador[0]=new PresentadorSimulacion(panel[0],config->{
                    creaciones.incrementAndGet();assertEquals(8,config.ordenes());
                    return new PresentadorSimulacion.Corrida(){
                        public String identidad(){return "inicio-unico";}
                        public CompletableFuture<MotorSimulacion.Resultado> iniciar(){
                            inicios.incrementAndGet();inicio.countDown();return new CompletableFuture<>();
                        }
                        public MotorSimulacion.Progreso observar(){return new MotorSimulacion.Progreso("inicio-unico",1,0,0,0,0,0,null,null);}
                        public void solicitarSalida(){}
                        public void close(){}
                    };
                },ciclo,SwingUtilities::invokeLater);
                panel[0].conectar(presentador[0]);panel[0].detenerRefresco();
                var etiqueta=PanelSimulacionLayoutTest.componentes(panel[0],JLabel.class).stream()
                    .filter(l->"Órdenes".equals(l.getText())).findFirst().orElseThrow();
                ((JSpinner.DefaultEditor)((JSpinner)etiqueta.getLabelFor()).getEditor()).getTextField().setText("8");
                boton[0]=PanelSimulacionLayoutTest.componentes(panel[0],JButton.class).stream()
                    .filter(b->"Iniciar escenario nuevo".equals(b.getText())).findFirst().orElseThrow();
                assertTrue(boton[0].isEnabled());boton[0].doClick(0);
                assertFalse(boton[0].isEnabled());
            });
            assertTrue(inicio.await(5,TimeUnit.SECONDS),"No se invocó el inicio de la corrida");
            ciclo.submit(()->{}).get(5,TimeUnit.SECONDS);
            SwingUtilities.invokeAndWait(()->{
                assertEquals(1,creaciones.get());assertEquals(1,inicios.get());
                assertFalse(boton[0].isEnabled());
                assertTrue(PanelSimulacionLayoutTest.componentes(panel[0],JTextArea.class).stream()
                    .anyMatch(a->"Simulación en ejecución".equals(a.getText())));
            });
        } finally {
            SwingUtilities.invokeAndWait(()->{
                if(panel[0]!=null)panel[0].detenerRefresco();
                if(presentador[0]!=null)presentador[0].cerrar(ok->{if(ok)cierre.countDown();});
                else cierre.countDown();
            });
            try {assertTrue(cierre.await(5,TimeUnit.SECONDS),"No terminó el cierre de la corrida espía");}
            finally {ciclo.shutdown();assertTrue(ciclo.awaitTermination(5,TimeUnit.SECONDS));}
        }
    }
}
