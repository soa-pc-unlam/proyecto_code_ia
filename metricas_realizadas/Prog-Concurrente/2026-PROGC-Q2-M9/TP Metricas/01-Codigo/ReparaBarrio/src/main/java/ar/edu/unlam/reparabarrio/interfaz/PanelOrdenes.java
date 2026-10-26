package ar.edu.unlam.reparabarrio.interfaz;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import javax.swing.*;
import javax.swing.table.*;
import javax.swing.event.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

public final class PanelOrdenes extends JPanel {
    private final DefaultTableModel modelo = new DefaultTableModel(new String[]{"Secuencia","ID","Descripción","Requisitos","Estado","Espera / revisión"},0) {
        public boolean isCellEditable(int r,int c) { return false; }
    };
    private final JTable tabla=new JTable(modelo);
    private final JTextArea detalle=new JTextArea(6,60), propuesta=new JTextArea(5,60), historial=new JTextArea();
    private final JTextField id=new JTextField(10), descripcion=new JTextField(24), filtro=new JTextField(12);
    private final JComboBox<String> estados=new JComboBox<>(new String[]{"Todos","PENDIENTE","EN_CURSO","FINALIZADA","FALLIDA","CANCELADA"});
    private final JPanel tipos=new JPanel(new FlowLayout(FlowLayout.LEFT));
    private final Map<TipoHerramienta,JCheckBox> requisitos=new EnumMap<>(TipoHerramienta.class);
    private final JButton alta=new JButton("Registrar orden"), inicio=new JButton("Confirmar inicio"), finalizar=new JButton("Finalizar"), fallar=new JButton("Informar fallo"), cancelar=new JButton("Cancelar pendiente");
    private InstantaneaTaller captura;
    private boolean acciones;
    private PresentadorTaller presentador;
    public PanelOrdenes() {
        super(new BorderLayout(4,4));
        var superior=new JPanel(new GridLayout(0,1));
        var formulario=new JPanel(); formulario.add(new JLabel("ID exacto")); formulario.add(id); formulario.add(new JLabel("Descripción")); formulario.add(descripcion); formulario.add(alta);
        superior.add(formulario); superior.add(tipos);
        var filtros=new JPanel(); filtros.add(new JLabel("Filtrar ID")); filtros.add(filtro); filtros.add(estados); superior.add(filtros); add(superior,BorderLayout.NORTH);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); tabla.setAutoCreateRowSorter(true);
        var centro=new JSplitPane(JSplitPane.VERTICAL_SPLIT,new JScrollPane(tabla),new JScrollPane(detalle)); centro.setResizeWeight(.65);
        var inferior=new JPanel(new BorderLayout());
        var botones=new JPanel(); for(var b:List.of(inicio,finalizar,fallar,cancelar)) botones.add(b);
        inferior.add(botones,BorderLayout.NORTH); inferior.add(new JScrollPane(propuesta),BorderLayout.CENTER);
        var sub=new JTabbedPane(); sub.addTab("Órdenes y asignación",centro); sub.addTab("Historial y revisiones",new JScrollPane(historial));
        add(sub,BorderLayout.CENTER); add(inferior,BorderLayout.SOUTH);
        for(var area:List.of(detalle,propuesta,historial)) { area.setEditable(false); area.setLineWrap(true); area.setWrapStyleWord(true); }
        tabla.getSelectionModel().addListSelectionListener(e -> seleccion());
        filtro.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e){filtrar();} public void removeUpdate(DocumentEvent e){filtrar();} public void changedUpdate(DocumentEvent e){filtrar();}
        }); estados.addActionListener(e -> filtrar());
        alta.addActionListener(e -> { var elegidos=EnumSet.noneOf(TipoHerramienta.class); requisitos.forEach((t,b)->{if(b.isSelected())elegidos.add(t);}); presentador.registrarOrden(id.getText(),descripcion.getText(),elegidos); });
        inicio.addActionListener(e -> presentador.confirmarInicio());
        finalizar.addActionListener(e -> transicion(EstadoOrden.FINALIZADA)); fallar.addActionListener(e -> transicion(EstadoOrden.FALLIDA)); cancelar.addActionListener(e -> transicion(EstadoOrden.CANCELADA));
        acciones(false);
    }
    public void conectar(PresentadorTaller p) { presentador=p; }
    private String seleccionId() { int r=tabla.getSelectedRow(); return r<0?null:(String)modelo.getValueAt(tabla.convertRowIndexToModel(r),1); }
    private void transicion(EstadoOrden destino) { var s=seleccionId(); if(s!=null)presentador.transicionar(s,destino); }
    private void filtrar() {
        @SuppressWarnings("unchecked") var sorter=(TableRowSorter<DefaultTableModel>)tabla.getRowSorter();
        String texto=filtro.getText(), estado=(String)estados.getSelectedItem();
        sorter.setRowFilter(new RowFilter<>() { public boolean include(Entry<? extends DefaultTableModel,? extends Integer> e) {
            return e.getStringValue(1).contains(texto) && ("Todos".equals(estado)||e.getStringValue(4).equals(estado));
        }}); seleccion();
    }
    public void mostrar(InstantaneaTaller s) {
        String seleccion=seleccionId(); captura=s; modelo.setRowCount(0);
        s.dominio().ordenes().stream().sorted(Comparator.comparingLong(Orden::secuencia)).forEach(o -> modelo.addRow(new Object[]{o.secuencia(),o.id(),o.descripcion(),o.requisitos(),o.estado(),s.planificacion().causas().getOrDefault(o.id(),List.of())+" "+Objects.toString(s.revisiones().get(o.id()),"")}));
        for(int r=0;r<tabla.getRowCount();r++) if(Objects.equals(tabla.getValueAt(r,1),seleccion))tabla.setRowSelectionInterval(r,r);
        for(var t:s.catalogo()) if(!requisitos.containsKey(t)) {var b=new JCheckBox(t.name()); requisitos.put(t,b); tipos.add(b);}
        tipos.revalidate(); tipos.repaint();
        var p=s.planificacion().propuesta();
        String ordenPropuesta=p.flatMap(pr->s.dominio().ordenes().stream().filter(o->o.id().equals(pr.ordenId())).findFirst()).map(o->"Orden "+o.id()+" | secuencia "+o.secuencia()+" | requisitos "+o.requisitos()+"\n").orElse("");
        propuesta.setText("Sin reserva. La confirmación revalida prioridad y unidades exactas.\n"+ordenPropuesta+p.map(Object::toString).orElse(s.dominio().ordenes().stream().anyMatch(o->o.estado()==EstadoOrden.PENDIENTE)?"No hay orden factible":"No hay órdenes pendientes")+"\nCausas completas: "+s.planificacion().causas());
        var texto=new StringBuilder("Historial (hora local "+java.time.ZoneId.systemDefault()+")\n");
        s.historial().stream().sorted(Comparator.comparingLong(EventoHistorial::secuencia)).forEach(e -> texto.append(e.secuencia()).append(" | ").append(e.momento().atZone(java.time.ZoneId.systemDefault())).append(" | ").append(e).append('\n'));
        texto.append("\nRevisiones (cierreId nulo: pendiente; presente: resuelta)\n"); s.revisiones().forEach((k,v)->texto.append(k).append(" | ").append(v).append('\n')); historial.setText(texto.toString()); seleccion();
    }
    public void acciones(boolean h) { acciones=h; alta.setEnabled(h); inicio.setEnabled(h && captura!=null && captura.planificacion().propuesta().isPresent()); seleccion(); }
    private void seleccion() {
        String id=seleccionId(); var o=captura==null?null:captura.dominio().ordenes().stream().filter(x->x.id().equals(id)).findFirst().orElse(null);
        finalizar.setEnabled(acciones && o!=null && o.estado()==EstadoOrden.EN_CURSO); fallar.setEnabled(finalizar.isEnabled()); cancelar.setEnabled(acciones && o!=null && o.estado()==EstadoOrden.PENDIENTE);
        detalle.setText(o==null?"Seleccione una orden":o+"\nAsignación activa: "+captura.dominio().asignaciones().get(o.id())+"\nCausas: "+captura.planificacion().causas().getOrDefault(o.id(),List.of())+"\nRevisión: "+captura.revisiones().get(o.id()));
    }
}
