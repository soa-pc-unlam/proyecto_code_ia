package ar.edu.unlam.reparabarrio.interfaz;

import ar.edu.unlam.reparabarrio.aplicacion.InstantaneaTaller;
import ar.edu.unlam.reparabarrio.dominio.EstadoOrden;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;
import java.util.*;

public final class VentanaPrincipal extends JFrame implements VistaTaller {
    private final PanelOrdenes ordenes=new PanelOrdenes();
    private final PanelRecursos recursos=new PanelRecursos();
    private final JLabel actividad=new JLabel("Sin lectura confirmada"), captura=new JLabel("Última captura: N/D");
    private final Map<EstadoOrden,JLabel> contadores=new EnumMap<>(EstadoOrden.class);
    private final JTextArea mensajes=new JTextArea(5,80), diagnostico=new JTextArea(4,80);
    private InstantaneaTaller ultima;
    private PresentadorTaller presentador;
    private final PanelSimulacion simulacion=new PanelSimulacion();
    private PresentadorSimulacion presentadorSimulacion;
    private final CierreConjunto cierre=new CierreConjunto(()->{if(presentador!=null)presentador.cerrar();}, callback->{
        simulacion.detenerRefresco();if(presentadorSimulacion==null)callback.accept(true);else presentadorSimulacion.cerrar(callback);
    },this::dispose);
    public VentanaPrincipal(String identidad, Path ruta) {
        super("ReparaBarrio — prototipo académico — H4 parcial");
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);var contenido=new JPanel(new BorderLayout(5,5));
        var norte=new JPanel(new GridLayout(0,1)); norte.add(new JLabel("Contexto: "+identidad+" | "+ruta)); norte.add(new JLabel("Datos descartables en target; no ejecutar mvn clean con la sesión abierta.")); norte.add(actividad); norte.add(captura);
        var totales=new JPanel();for(var e:EstadoOrden.values()){var l=new JLabel(e+": N/D  ");contadores.put(e,l);totales.add(l);}norte.add(totales);contenido.add(norte,BorderLayout.NORTH);
        var tabs=new JTabbedPane();tabs.addTab("Órdenes",ordenes);tabs.addTab("Recursos",recursos);tabs.addTab("Simulación",simulacion);contenido.add(tabs,BorderLayout.CENTER);
        var sur=new JPanel(new BorderLayout());var botones=new JPanel(new GridBagLayout());var refrescar=new JButton("Consultar primera factible / refrescar");var salir=new JButton("Cerrar sesión de forma segura");salir.setMnemonic(KeyEvent.VK_C);var posicion=new GridBagConstraints();posicion.anchor=GridBagConstraints.WEST;posicion.gridx=0;posicion.gridy=0;botones.add(refrescar,posicion);posicion.gridy=1;botones.add(salir,posicion);sur.add(botones,BorderLayout.NORTH);
        var informacion=new JTabbedPane();informacion.addTab("Resultados",new JScrollPane(mensajes));informacion.addTab("Salud / último informe",new JScrollPane(diagnostico));sur.add(informacion,BorderLayout.CENTER);contenido.add(sur,BorderLayout.SOUTH);
        // Viewport de seguridad: conserva el pie cuando la ventana no contiene su mínimo natural.
        var seguridad=new JPanel(new BorderLayout()){
            public Dimension getPreferredSize(){var p=contenido.getPreferredSize();var v=getParent();if(v instanceof JViewport){p.width=Math.max(contenido.getMinimumSize().width,v.getWidth());p.height=Math.max(contenido.getMinimumSize().height,v.getHeight());}return p;}
        };seguridad.add(contenido);setContentPane(new JScrollPane(seguridad));PanelSimulacion.enfocarVisible(contenido);
        for(var a:java.util.List.of(mensajes,diagnostico)){a.setEditable(false);a.setLineWrap(true);a.setWrapStyleWord(true);((javax.swing.text.DefaultCaret)a.getCaret()).setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);}
        refrescar.addActionListener(e -> presentador.refrescar());salir.addActionListener(e -> cierre.solicitar());addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){cierre.solicitar();}});
        var pantalla=getGraphicsConfiguration().getBounds();var margenes=Toolkit.getDefaultToolkit().getScreenInsets(getGraphicsConfiguration());
        var util=new Rectangle(pantalla.x+margenes.left,pantalla.y+margenes.top,pantalla.width-margenes.left-margenes.right,pantalla.height-margenes.top-margenes.bottom);
        var preferido=contenido.getPreferredSize();var minimo=salir.getPreferredSize();
        setMinimumSize(new Dimension(Math.min(minimo.width,util.width),Math.min(minimo.height+getInsets().top,util.height)));
        setSize(Math.min(preferido.width,util.width),Math.min(preferido.height,util.height));setLocation(util.x+(util.width-getWidth())/2,util.y+(util.height-getHeight())/2);
    }
    public void conectar(PresentadorTaller p){presentador=p;ordenes.conectar(p);recursos.conectar(p);}
    public void conectarSimulacion(Path raiz){conectarSimulacion(ar.edu.unlam.reparabarrio.arranque.ContextoEjecucion.desarrollo(raiz));}
    public void conectarSimulacion(ar.edu.unlam.reparabarrio.arranque.ContextoEjecucion ejecucion){presentadorSimulacion=new PresentadorSimulacion(ejecucion,simulacion);simulacion.conectar(presentadorSimulacion);}
    public void captura(InstantaneaTaller s){ultima=s;ordenes.mostrar(s);recursos.mostrar(s);captura.setText("Última captura válida: "+java.time.ZonedDateTime.now()+" | revisión funcional="+s.revision()+" técnica="+s.revisionRecuperacion());}
    public static Map<EstadoOrden,Long> contar(InstantaneaTaller s){var m=new EnumMap<EstadoOrden,Long>(EstadoOrden.class);for(var e:EstadoOrden.values())m.put(e,s.dominio().ordenes().stream().filter(o->o.estado()==e).count());return Collections.unmodifiableMap(m);}
    public void estado(boolean acciones,String texto,boolean vigente){actividad.setText(texto+(vigente?" | captura vigente":" | captura anterior; vigentes N/D"));ordenes.acciones(acciones);recursos.acciones(acciones);var m=ultima==null?null:contar(ultima);contadores.forEach((e,l)->l.setText(e+": "+(vigente && m!=null?m.get(e):"N/D")+"  "));}
    public void mensaje(String texto){mensajes.append(texto+"\n\n");}
    public void diagnostico(String texto){diagnostico.append(texto+"\n\n");}
    public boolean confirmar(String titulo,String detalle){var area=new JTextArea(detalle,10,65);area.setEditable(false);area.setLineWrap(true);area.setWrapStyleWord(true);return JOptionPane.showOptionDialog(this,new JScrollPane(area),titulo,JOptionPane.DEFAULT_OPTION,JOptionPane.QUESTION_MESSAGE,null,new String[]{"Confirmar "+titulo,"Volver"},"Volver")==0;}
    public void cerrado(){cierre.operativoCerrado();}
    public void advertenciaCierre(String texto){JOptionPane.showMessageDialog(this,texto,"Cierre con advertencias",JOptionPane.WARNING_MESSAGE);}
}
