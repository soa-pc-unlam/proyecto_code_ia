package ar.edu.unlam.reparabarrio.interfaz;

import ar.edu.unlam.reparabarrio.aplicacion.InstantaneaTaller;
import ar.edu.unlam.reparabarrio.dominio.*;
import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.util.*;

public final class PanelRecursos extends JPanel {
    private final DefaultTableModel modelo=new DefaultTableModel(new String[]{"Categoría","ID","Tipo","Disponibilidad","Orden titular"},0) {public boolean isCellEditable(int r,int c){return false;}};
    private final JTable tabla=new JTable(modelo);
    private final JComboBox<Recurso.Categoria> categoria=new JComboBox<>(Recurso.Categoria.values());
    private final JComboBox<TipoHerramienta> tipo=new JComboBox<>();
    private final JTextField id=new JTextField(12);
    private final JCheckBox habilitado=new JCheckBox("Habilitado",true);
    private final JButton alta=new JButton("Registrar recurso"), rehabilitar=new JButton("Rehabilitar"), inhabilitar=new JButton("Poner fuera de servicio");
    private PresentadorTaller presentador;
    private boolean acciones;
    public PanelRecursos() {
        super(new BorderLayout()); var form=new JPanel(); form.add(categoria); form.add(new JLabel("ID exacto")); form.add(id); form.add(tipo); form.add(habilitado); form.add(alta); add(form,BorderLayout.NORTH);
        tabla.setAutoCreateRowSorter(true); tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); add(new JScrollPane(tabla),BorderLayout.CENTER);
        var sur=new JPanel(); sur.add(rehabilitar); sur.add(inhabilitar); sur.add(new JLabel("Ocupados: sólo finalizar/fallar su orden libera recursos.")); add(sur,BorderLayout.SOUTH);
        categoria.addActionListener(e -> tipo.setEnabled(categoria.getSelectedItem()==Recurso.Categoria.HERRAMIENTA)); tipo.setEnabled(false);
        tabla.getSelectionModel().addListSelectionListener(e -> seleccion());
        alta.addActionListener(e -> presentador.registrarRecurso((Recurso.Categoria)categoria.getSelectedItem(),id.getText(),categoria.getSelectedItem()==Recurso.Categoria.HERRAMIENTA?(TipoHerramienta)tipo.getSelectedItem():null,habilitado.isSelected()));
        rehabilitar.addActionListener(e -> cambiar(true)); inhabilitar.addActionListener(e -> cambiar(false)); acciones(false);
    }
    public void conectar(PresentadorTaller p) {presentador=p;}
    private Recurso.Clave clave() {int r=tabla.getSelectedRow();return r<0?null:new Recurso.Clave((Recurso.Categoria)tabla.getValueAt(r,0),(String)tabla.getValueAt(r,1));}
    private void cambiar(boolean h) {var c=clave();if(c!=null)presentador.habilitar(c,h);}
    private void seleccion() {int r=tabla.getSelectedRow();boolean libre=acciones && r>=0 && tabla.getValueAt(r,3)!=Recurso.Disponibilidad.OCUPADO;rehabilitar.setEnabled(libre);inhabilitar.setEnabled(libre);}
    public void acciones(boolean h) {acciones=h;alta.setEnabled(h);seleccion();}
    public void mostrar(InstantaneaTaller s) {
        var anterior=clave(); modelo.setRowCount(0);
        s.dominio().recursos().stream().sorted(Comparator.comparing((Recurso r)->r.clave().categoria()).thenComparing(r->r.clave().id())).forEach(r -> {
            String titular=s.dominio().asignaciones().entrySet().stream().filter(e->e.getValue().unidades().contains(r.clave())).map(Map.Entry::getKey).findFirst().orElse("");
            modelo.addRow(new Object[]{r.clave().categoria(),r.clave().id(),r.tipo(),s.dominio().disponibilidad(r.clave()),titular});
        });
        for(int r=0;r<tabla.getRowCount();r++) if(anterior!=null && anterior.categoria()==tabla.getValueAt(r,0) && anterior.id().equals(tabla.getValueAt(r,1)))tabla.setRowSelectionInterval(r,r);
        var elegido=tipo.getSelectedItem();tipo.removeAllItems();s.catalogo().stream().sorted().forEach(tipo::addItem);tipo.setSelectedItem(elegido==null?tipo.getItemCount()>0?tipo.getItemAt(0):null:elegido);seleccion();
    }
}
