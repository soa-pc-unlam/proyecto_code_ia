package ar.edu.unlam.reparabarrio.interfaz;

import ar.edu.unlam.reparabarrio.simulacion.*;
import ar.edu.unlam.reparabarrio.metricas.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.*;

/** Controles y proyecciones propios; no recibe la fachada operativa. */
public final class PanelSimulacion extends JPanel implements VistaSimulacion {
    private final JTextField semilla=new JTextField("20261015",13);
    private final JSpinner numero=entero(100,0),trabajadores=entero(4,1),fallos=entero(1000,0);
    private final JSpinner intervaloMin=tiempo(10),intervaloMax=tiempo(10),duracionMin=tiempo(200),duracionMax=tiempo(400);
    private final JComboBox<ConfiguracionEscenario.Llegadas> llegadas=new JComboBox<>(ConfiguracionEscenario.Llegadas.values());
    private final JSpinner[] cantidades=new JSpinner[5],habilitados=new JSpinner[5],pesos=new JSpinner[8];
    private final JButton iniciar=new JButton("Iniciar escenario nuevo");
    private final JTextArea actividad=texto("Sin simulación"),progreso=texto("Llegadas: 0"),contadores=texto("Cinco estados: N/D"),estadisticas=texto("Estadísticas: N/D"),periodo=texto("Período observado: N/D");
    private final JTextArea diagnostico=new JTextArea(),historial=new JTextArea();
    private final DefaultTableModel ordenes=modelo("Orden","Secuencia","Estado","Herramientas requeridas","Causa de espera");
    private final DefaultTableModel recursos=modelo("Categoría","Unidad","Tipo","Disponibilidad","Ocupado por","Utilización (%)");
    private final java.util.List<JComponent> controles=new ArrayList<>();
    private PresentadorSimulacion presentador;
    private final javax.swing.Timer refresco=new javax.swing.Timer(250,e->{if(presentador!=null)presentador.refrescar();});
    private static DefaultTableModel modelo(String... columnas){return new DefaultTableModel(columnas,0){public boolean isCellEditable(int f,int c){return false;}};}
    private static JSpinner entero(int n,int min){return new JSpinner(new SpinnerNumberModel(n,min,Integer.MAX_VALUE-8,1));}
    private static JSpinner tiempo(long n){return new JSpinner(new SpinnerNumberModel(n,0L,Long.MAX_VALUE,1L));}
    private static long valor(JSpinner s){
        try{s.commitEdit();}catch(java.text.ParseException error){throw new IllegalArgumentException("Valor numérico inválido",error);}
        return ((Number)s.getValue()).longValue();
    }
    private static JTextArea texto(String valor){
        var a=new JTextArea(valor,0,48){
            // BoxLayout necesita la altura de TODAS las líneas antes de asignar bounds.
            // Medir la vista Swing al ancho natural de columnas evita truncar texto largo.
            public Dimension getPreferredSize(){
                var bordes=getInsets();int ancho=getColumns()*getColumnWidth()+bordes.left+bordes.right;
                var vista=getUI().getRootView(this);vista.setSize(ancho-bordes.left-bordes.right,Float.MAX_VALUE);
                return new Dimension(ancho,(int)Math.ceil(vista.getPreferredSpan(javax.swing.text.View.Y_AXIS))+bordes.top+bordes.bottom);
            }
        };a.setEditable(false);a.setLineWrap(true);a.setWrapStyleWord(true);a.setOpaque(false);sinAutoscroll(a);return a;
    }
    private static void sinAutoscroll(JTextArea a){
        var caret=new javax.swing.text.DefaultCaret(){protected void adjustVisibility(Rectangle r){/* La proyección no cambia la navegación. */}};
        caret.setUpdatePolicy(javax.swing.text.DefaultCaret.NEVER_UPDATE);a.setCaret(caret);
        // Sólo navegación explícita por teclado desplaza hacia la posición de lectura.
        a.addKeyListener(new java.awt.event.KeyAdapter(){public void keyReleased(java.awt.event.KeyEvent e){
            try{var r=a.modelToView2D(a.getCaretPosition());if(r!=null)a.scrollRectToVisible(r.getBounds());}catch(javax.swing.text.BadLocationException imposible){throw new IllegalStateException(imposible);}
        }});
    }
    private static JPanel grupo(String titulo){var p=new JPanel(new GridBagLayout());p.setBorder(BorderFactory.createTitledBorder(titulo));p.setAlignmentX(LEFT_ALIGNMENT);return p;}
    private static void celda(JPanel p,Component c,int x,int y){var g=new GridBagConstraints();g.gridx=x;g.gridy=y;g.anchor=GridBagConstraints.WEST;g.insets=new Insets(3,6,3,6);p.add(c,g);}
    private void campo(JPanel p,String etiqueta,JComponent c){int fila=p.getComponentCount()/2;var l=new JLabel(etiqueta);l.setLabelFor(c);celda(p,l,0,fila);celda(p,c,1,fila);controles.add(c);}
    public PanelSimulacion() {
        super(new BorderLayout(4,4));llegadas.setSelectedItem(ConfiguracionEscenario.Llegadas.PERIODICAS);
        var configuracion=new JPanel();configuracion.setLayout(new BoxLayout(configuracion,BoxLayout.Y_AXIS));
        configuracion.add(new JLabel("Simulación ficticia independiente en memoria; se descarta al cerrar. H4 parcial."));
        var fila=grupo("Parámetros generales");campo(fila,"Semilla",semilla);campo(fila,"Órdenes",numero);campo(fila,"Trabajadores",trabajadores);campo(fila,"Fallos / 10000",fallos);configuracion.add(fila);
        var inventario=grupo("Inventario");String[] nombres={"Técnicos","Puestos","Multímetros","Soldadores","Taladros"};
        celda(inventario,new JLabel("Categoría"),0,0);celda(inventario,new JLabel("Total"),1,0);celda(inventario,new JLabel("Habilitados"),2,0);
        for(int i=0;i<5;i++){cantidades[i]=entero(4,0);habilitados[i]=entero(4,0);celda(inventario,new JLabel(nombres[i]),0,i+1);
            var total=new JLabel(nombres[i]+" total");total.setLabelFor(cantidades[i]);var hab=new JLabel(nombres[i]+" habilitados");hab.setLabelFor(habilitados[i]);
            cantidades[i].getAccessibleContext().setAccessibleName(total.getText());habilitados[i].getAccessibleContext().setAccessibleName(hab.getText());
            celda(inventario,cantidades[i],1,i+1);celda(inventario,habilitados[i],2,i+1);controles.add(cantidades[i]);controles.add(habilitados[i]);}configuracion.add(inventario);
        var mezcla=grupo("Pesos de requisitos (M: multímetro, S: soldador, D: taladro)");String[] etiquetas={"sin herramientas","M","S","MS","D","MD","SD","MSD"};for(int i=0;i<8;i++){pesos[i]=entero(1,0);campo(mezcla,"Peso "+etiquetas[i],pesos[i]);}configuracion.add(mezcla);
        var tiempos=grupo("Tiempos");campo(tiempos,"Llegadas",llegadas);campo(tiempos,"Intervalo mínimo (ms)",intervaloMin);campo(tiempos,"Intervalo máximo (ms)",intervaloMax);campo(tiempos,"Duración mínima (ms)",duracionMin);campo(tiempos,"Duración máxima (ms)",duracionMax);configuracion.add(tiempos);
        configuracion.add(iniciar);configuracion.add(actividad);configuracion.add(progreso);configuracion.add(contadores);configuracion.add(estadisticas);configuracion.add(periodo);
        var tablas=new JTabbedPane();tablas.addTab("Órdenes simuladas",new JScrollPane(new JTable(ordenes)));tablas.addTab("Recursos y utilización",new JScrollPane(new JTable(recursos)));historial.setEditable(false);diagnostico.setEditable(false);sinAutoscroll(historial);sinAutoscroll(diagnostico);tablas.addTab("Historial",new JScrollPane(historial));tablas.addTab("Detalle técnico",new JScrollPane(diagnostico));
        var contenido=new JPanel(new BorderLayout(4,4));contenido.add(configuracion,BorderLayout.NORTH);contenido.add(tablas,BorderLayout.CENTER);add(new JScrollPane(contenido),BorderLayout.CENTER);
        // Sólo un cambio real de foco desplaza el control al área visible; nunca un refresco.
        enfocarVisible(contenido);
        iniciar.setEnabled(false);iniciar.addActionListener(e->{if(presentador==null)return;try{presentador.iniciarPreparacion(configuracion());}catch(RuntimeException error){reemplazar(actividad,"Configuración inválida: "+error.getMessage());}});
    }
    private static final Object FOCO_VISIBLE=new Object();
    static void enfocarVisible(Container raiz){
        for(Component c:raiz.getComponents()){
            if(c instanceof JComponent j && j.getClientProperty(FOCO_VISIBLE)==null){
                j.putClientProperty(FOCO_VISIBLE,Boolean.TRUE);
                j.addFocusListener(new java.awt.event.FocusAdapter(){
                    public void focusGained(java.awt.event.FocusEvent e){
                        // El ratón ya alcanz? el control: no moverlo durante presión/liberación.
                        // Tab/Shift+Tab y foco programático conservan el desplazamiento accesible.
                        if(e.getCause()!=java.awt.event.FocusEvent.Cause.MOUSE_EVENT)
                            j.scrollRectToVisible(new Rectangle(j.getSize()));
                    }
                });
            }
            if(c instanceof Container hijo)enfocarVisible(hijo);
        }
    }
    public void conectar(PresentadorSimulacion p){presentador=p;iniciar.setEnabled(true);refresco.start();}
    public void detenerRefresco(){refresco.stop();}
    private java.util.function.Supplier<ConfiguracionEscenario> configuracion(){
        int[] total=new int[5],hab=new int[5];for(int i=0;i<5;i++){total[i]=(int)valor(cantidades[i]);hab[i]=(int)valor(habilitados[i]);if(hab[i]>total[i])throw new IllegalArgumentException("Habilitados supera total");}
        var mezcla=new ArrayList<Integer>();for(var p:pesos)mezcla.add((int)valor(p));
        long seed=Long.parseLong(semilla.getText()),imin=valor(intervaloMin),imax=valor(intervaloMax),dmin=valor(duracionMin),dmax=valor(duracionMax);
        int n=(int)valor(numero),w=(int)valor(trabajadores),tasa=(int)valor(fallos);var patron=(ConfiguracionEscenario.Llegadas)llegadas.getSelectedItem();
        // Sólo valores capturados en EDT. Construcción potencialmente grande en propietario H6.
        return ()->{
        var inventario=ConfiguracionEscenario.inventario(total[0],total[1],total[2],total[3],total[4],false);
        var unidades=new ArrayList<Recurso>();int[] indice=new int[5];
        for(var r:inventario){int k=r.clave().categoria()==Recurso.Categoria.TECNICO?0:r.clave().categoria()==Recurso.Categoria.PUESTO?1:r.tipo()==TipoHerramienta.MULTIMETRO?2:r.tipo()==TipoHerramienta.SOLDADOR?3:4;unidades.add(new Recurso(r.clave(),r.tipo(),++indice[k]<=hab[k]));}
        return new ConfiguracionEscenario(seed,n,w,unidades,mezcla,patron,imin,imax,dmin,dmax,tasa);
        };
    }
    public void estado(boolean permitir,String texto){
        iniciar.setEnabled(permitir);controles.forEach(c->c.setEnabled(permitir));reemplazar(actividad,texto);
        if(texto.startsWith("Preparando")){ordenes.setRowCount(0);recursos.setRowCount(0);historial.setText("");reemplazar(contadores,"Cinco estados vigentes: N/D");reemplazar(estadisticas,"Estadísticas vigentes: N/D");reemplazar(periodo,"Período observado: N/D");reemplazar(progreso,"Preparando carga nueva");}
    }
    private static String formato(Double n){return n==null?"N/D":String.format(Locale.ROOT,"%.3f",n);}
    public void mostrar(MotorSimulacion.Progreso p,EstadisticasEscenario s,MotorSimulacion.Resultado resultado){
        var posiciones=new LinkedHashMap<JViewport,Point>();guardarViewports(this,posiciones);
        var selecciones=new LinkedHashMap<JTable,java.util.List<Object>>();guardarSelecciones(this,selecciones);
        try { actualizar(p,s,resultado); }
        finally {
            selecciones.forEach((tabla,ids)->{tabla.getSelectionModel().clearSelection();for(int i=0;i<tabla.getRowCount();i++)if(ids.contains(claveFila(tabla,i)))tabla.addRowSelectionInterval(i,i);});
            posiciones.forEach(JViewport::setViewPosition);
        }
    }
    private static void guardarViewports(Container c,Map<JViewport,Point> posiciones){for(Component h:c.getComponents()){if(h instanceof JViewport v)posiciones.put(v,v.getViewPosition());if(h instanceof Container hijo)guardarViewports(hijo,posiciones);}}
    private static Object claveFila(JTable t,int fila){return java.util.List.of(t.getValueAt(fila,0),t.getValueAt(fila,1));}
    private static void guardarSelecciones(Container c,Map<JTable,java.util.List<Object>> selecciones){for(Component h:c.getComponents()){if(h instanceof JTable t){var ids=new ArrayList<Object>();for(int fila:t.getSelectedRows())ids.add(claveFila(t,fila));selecciones.put(t,ids);}if(h instanceof Container hijo)guardarSelecciones(hijo,selecciones);}}
    private static void reemplazar(JTextArea area,String texto){if(area.getText().equals(texto))return;int punto=area.getCaret().getDot(),marca=area.getCaret().getMark();area.setText(texto);int n=area.getDocument().getLength();area.setCaretPosition(Math.min(marca,n));area.moveCaretPosition(Math.min(punto,n));}
    private void actualizar(MotorSimulacion.Progreso p,EstadisticasEscenario s,MotorSimulacion.Resultado resultado){
        reemplazar(progreso,"Contexto simulado: "+p.contexto()+" | registradas: "+p.registradas()+" | futuras: "+p.futuras()+" | registros en vuelo: "+p.registrosEnVuelo()+" | trabajadores: "+p.trabajadores());
        if(p.captura()==null)return;var c=p.captura();boolean vigente=s!=null && s.incompleta()==null;
        var texto=new StringBuilder();for(var e:EstadoOrden.values())texto.append(e).append(": ").append(vigente?s.estados().get(e):"N/D").append("   ");reemplazar(contadores,texto.toString());
        reemplazar(estadisticas,"Procesadas (FINALIZADA + FALLIDA): "+(vigente?s.procesadas():"N/D")+" | espera promedio iniciadas (ms): "+formato(s==null?null:s.esperaPromedioMs())+" | duración (s): "+formato(s==null?null:s.duracionSegundos()));
        reemplazar(periodo,"Período [comienzo, captura): "+(c.nanos()-p.comienzo())+" ns | revisión "+c.taller().revision()+" | inicio UTC "+p.comienzoCivil()+" | observación UTC "+p.observacionCivil()+(vigente?"":" | histórica / incompleta"));
        ordenes.setRowCount(0);for(var o:c.taller().dominio().ordenes())ordenes.addRow(new Object[]{o.id(),o.secuencia(),o.estado(),o.requisitos(),String.join("; ",c.taller().planificacion().causas().getOrDefault(o.id(),java.util.List.of()))});
        recursos.setRowCount(0);for(var r:c.taller().dominio().recursos()) {
            var u=s==null?null:s.utilizacion().get(r.clave());String titular=c.taller().dominio().asignaciones().entrySet().stream().filter(a->a.getValue().unidades().contains(r.clave())).map(Map.Entry::getKey).findFirst().orElse("");
            recursos.addRow(new Object[]{r.clave().categoria(),r.clave().id(),r.tipo(),c.taller().dominio().disponibilidad(r.clave()),titular,formato(u==null?null:u.porcentaje())});
        }
        var eventos=new StringBuilder();for(var e:c.taller().historial())eventos.append(e.secuencia()).append(" | ").append(e.momento()).append(" | ").append(e.accion()).append(" | ").append(Objects.toString(e.ordenId(),"")).append(" | ").append(e.recursos()).append('\n');reemplazar(historial,eventos.toString());
        if(resultado!=null && resultado.motivo()==MotorSimulacion.Motivo.SALIDA_SOLICITADA)diagnostico("Carga no entregada: "+resultado.noEntregadas());
    }
    public void diagnostico(String texto){diagnostico.append(texto+"\n");}
}
