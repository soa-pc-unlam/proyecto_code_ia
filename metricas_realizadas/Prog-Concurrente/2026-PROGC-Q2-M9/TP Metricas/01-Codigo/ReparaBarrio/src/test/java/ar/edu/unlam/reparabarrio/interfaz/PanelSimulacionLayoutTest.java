package ar.edu.unlam.reparabarrio.interfaz;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import ar.edu.unlam.reparabarrio.planificacion.Planificador;
import ar.edu.unlam.reparabarrio.simulacion.MotorSimulacion;
import org.junit.jupiter.api.Test;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Geometría en EDT, sin JDBC. No acredita escalado ni interacción humana. */
class PanelSimulacionLayoutTest {
    static void layout(Container c){invalidar(c);disponer(c);}
    private static void invalidar(Container c){for(Component h:c.getComponents())if(h instanceof Container hijo)invalidar(hijo);c.invalidate();}
    private static void disponer(Container c){c.doLayout();for(Component h:c.getComponents())if(h instanceof Container hijo)disponer(hijo);}
    static <T> java.util.List<T> componentes(Container c,Class<T> tipo){var r=new ArrayList<T>();for(Component h:c.getComponents()){if(tipo.isInstance(h))r.add(tipo.cast(h));if(h instanceof Container hijo)r.addAll(componentes(hijo,tipo));}return r;}
    @Test void todosLosEditoresAlcanzablesConEtiquetasYScrollEnAmbosEjes() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var p=new PanelSimulacion();
            assertEquals(25,componentes(p,JSpinner.class).size());
            var etiquetas=componentes(p,JLabel.class).stream().map(JLabel::getText).toList();
            for(String nombre:java.util.List.of("sin herramientas","M","S","MS","D","MD","SD","MSD"))assertTrue(etiquetas.contains("Peso "+nombre));
            for(var tam:java.util.List.of(new Dimension(1200,850),new Dimension(800,600),new Dimension(360,260))){
                p.setSize(tam);layout(p);var scroll=(JScrollPane)p.getComponent(0);var v=scroll.getViewport();
                var campos=new ArrayList<JComponent>(componentes(p,JSpinner.class));campos.add(componentes(p,JTextField.class).getFirst());campos.addAll(componentes(p,JComboBox.class));campos.add(componentes(p,JButton.class).stream().filter(b->b.getText().startsWith("Iniciar")).findFirst().orElseThrow());
                for(var campo:campos){assertTrue(campo.getWidth()>0 && campo.getHeight()>0);var rect=SwingUtilities.convertRectangle(campo.getParent(),campo.getBounds(),v.getView());((JComponent)v.getView()).scrollRectToVisible(rect);assertTrue(v.getViewRect().contains(rect),()->"No alcanzable: "+rect+" en "+v.getViewRect());}
                var spinners=componentes(p,JSpinner.class);for(int i=0;i<spinners.size();i++)for(int j=i+1;j<spinners.size();j++)assertFalse(SwingUtilities.convertRectangle(spinners.get(i).getParent(),spinners.get(i).getBounds(),p).intersects(SwingUtilities.convertRectangle(spinners.get(j).getParent(),spinners.get(j).getBounds(),p)));
            }
        });
    }
    static MotorSimulacion.Progreso captura(int n){
        var ordenes=new ArrayList<Orden>();for(int i=1;i<=n;i++)ordenes.add(new Orden("O"+i,"Descripción",i,Set.of(),EstadoOrden.PENDIENTE));
        var d=new InstantaneaDominio("layout",ordenes,java.util.List.of(),Map.of());
        var t=new InstantaneaTaller(n,d,Set.of(TipoHerramienta.values()),java.util.List.of(),new Planificador().proponer(d));
        return new MotorSimulacion.Progreso("layout",n,0,0,0,0,n,new HechosConfirmados.Captura(t,java.util.List.of(),100,null),null);
    }
    @Test void refrescoConservaValoresSeleccionYPuntosDeScroll() throws Exception {
        SwingUtilities.invokeAndWait(()->{
            var p=new PanelSimulacion();p.setSize(500,400);layout(p);p.mostrar(captura(100),null,null);layout(p);
            var tabla=componentes(p,JTable.class).getFirst();tabla.setRowSelectionInterval(20,20);
            var campo=componentes(p,JTextField.class).stream().filter(t->t.getText().equals("20261015")).findFirst().orElseThrow();campo.setText("12345");campo.select(1,4);
            var exterior=((JScrollPane)p.getComponent(0)).getViewport();exterior.setViewPosition(new Point(60,200));var punto=exterior.getViewPosition();
            var foco=KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
            p.mostrar(captura(101),null,null);layout(p);
            assertEquals("12345",campo.getText());assertEquals("234",campo.getSelectedText());assertEquals("O21",tabla.getValueAt(tabla.getSelectedRow(),0));assertEquals(punto,exterior.getViewPosition());assertSame(foco,KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner());
            p.diagnostico("Diagnóstico extenso ".repeat(200));assertEquals(punto,exterior.getViewPosition());
        });
    }
    @Test void textosDeProgresoCompletosConservanSeleccionYNoDesplazanAlRefrescar() throws Exception {
        var panel=new PanelSimulacion[1];var posicion=new Point[1];var progreso=new JTextArea[1];
        SwingUtilities.invokeAndWait(()->{
            panel[0]=new PanelSimulacion();panel[0].setSize(500,400);layout(panel[0]);
            var base=captura(100);var largo=new MotorSimulacion.Progreso("contexto-"+"extenso".repeat(60),100,0,0,0,0,100,base.captura(),null);
            panel[0].mostrar(largo,null,null);layout(panel[0]);
            progreso[0]=componentes(panel[0],JTextArea.class).stream().filter(a->a.getText().startsWith("Contexto simulado:")).findFirst().orElseThrow();
            try {var fin=progreso[0].modelToView2D(progreso[0].getDocument().getLength());assertNotNull(fin);assertTrue(fin.getMaxY()<=progreso[0].getHeight(),()->"Texto cortado: "+fin+" / "+progreso[0].getSize());}catch(javax.swing.text.BadLocationException e){throw new AssertionError(e);}
            progreso[0].select(0,8);var v=((JScrollPane)panel[0].getComponent(0)).getViewport();v.setViewPosition(new Point(20,100));posicion[0]=v.getViewPosition();
            panel[0].mostrar(largo,null,null);
        });
        SwingUtilities.invokeAndWait(()->{assertEquals("Contexto",progreso[0].getSelectedText());assertEquals(posicion[0],((JScrollPane)panel[0].getComponent(0)).getViewport().getViewPosition());});
    }
}
