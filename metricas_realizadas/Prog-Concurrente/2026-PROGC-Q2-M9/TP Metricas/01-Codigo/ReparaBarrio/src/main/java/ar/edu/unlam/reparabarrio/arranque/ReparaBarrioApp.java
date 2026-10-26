package ar.edu.unlam.reparabarrio.arranque;

import ar.edu.unlam.reparabarrio.interfaz.*;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.nio.file.Path;

/** Entrada de desarrollo H5, sin JUnit ni apertura operativa implícita. */
public final class ReparaBarrioApp {
    private ReparaBarrioApp() { }
    public static void main(String[] args) throws Exception {
        var ejecucion=procesarArgumentos(args);
        Path raiz=FabricaContextos.validarRaiz(ejecucion); // main, fuera de EDT
        var validado=new ContextoEjecucion(raiz,ejecucion.modo());
        SwingUtilities.invokeLater(() -> new Inicio(validado).setVisible(true));
    }
    public static ContextoEjecucion procesarArgumentos(String[] args) {
        if((args.length!=3 && args.length!=4) || !args[0].equals("--raiz") || !args[2].equals("--academico-nuevo") || (args.length==4 && !args[3].equals("--portable")))
            throw new IllegalArgumentException("Uso: --raiz <ruta absoluta> --academico-nuevo [--portable]");
        return new ContextoEjecucion(Path.of(args[1]),args.length==4 ? ContextoEjecucion.Modo.PORTABLE : ContextoEjecucion.Modo.DESARROLLO);
    }

    private static final class Inicio extends JFrame {
        private final Path raiz;
        private final ContextoEjecucion ejecucion;
        private final JButton crear=new JButton("Crear sesión académica descartable");
        private final JTextArea mensaje=new JTextArea(9,80);
        private boolean abriendo, salir;
        private Inicio(ContextoEjecucion ejecucion) {
            super("ReparaBarrio — arranque académico H5"); this.ejecucion=ejecucion; this.raiz=ejecucion.raiz();
            setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
            mensaje.setEditable(false);mensaje.setLineWrap(true);mensaje.setWrapStyleWord(true);
            mensaje.setText("Se creará una base NUEVA bajo "+raiz.resolve("target")+"/h5-ID-UNICO.\nDatos descartables: mvn clean puede eliminarlos. No ejecutar clean con una sesión abierta.\nNo se reabren sesiones anteriores. H4 es parcial; recuperación avanzada no aprobada.");
            add(new JScrollPane(mensaje),BorderLayout.CENTER);add(crear,BorderLayout.SOUTH);pack();setLocationRelativeTo(null);
            crear.addActionListener(e -> abrir());addWindowListener(new WindowAdapter(){public void windowClosing(WindowEvent e){salir=true;if(!abriendo)dispose();else mensaje.append("\nCierre solicitado; se espera la apertura y se cerrará su propietario fuera del EDT.");}});
        }
        private void abrir() {
            if(abriendo || salir)return;
            if(JOptionPane.showConfirmDialog(this,"¿Crear una nueva sesión descartable en target/h5-*?\nNo ejecutar mvn clean mientras esté abierta.","Crear sesión académica",JOptionPane.OK_CANCEL_OPTION)!=JOptionPane.OK_OPTION)return;
            abriendo=true;crear.setEnabled(false);mensaje.append("\nAbriendo…");
            var trabajos=new TrabajosSwing();
            trabajos.ejecutar(() -> {
                try {
                    var sesion=ArranqueAcademico.crear(ejecucion,true);
                    var puerto=new AdaptadorServicioTaller(sesion.contexto());
                    String identidad=puerto.identidad();
                    trabajos.publicar(() -> {
                        // Una apertura tardía nunca convierte el cierre solicitado en ventana operativa.
                        if(salir) {
                            var v=new VistaTaller() {
                                public void captura(ar.edu.unlam.reparabarrio.aplicacion.InstantaneaTaller s) { }
                                public void estado(boolean a,String t,boolean v){mensaje.append("\n"+t);}
                                public void mensaje(String t){mensaje.append("\n"+t);}
                                public void diagnostico(String t){mensaje.append("\n"+t);}
                                public boolean confirmar(String t,String d){return false;}
                                public void cerrado(){dispose();}
                            };
                            var p=new PresentadorTaller(puerto,v,identidad,trabajos);
                            // Permite volver a pedir cierre si éste retiene el propietario.
                            crear.setText("Solicitar cierre seguro");crear.setEnabled(true);
                            for(var listener:crear.getActionListeners())crear.removeActionListener(listener);
                            crear.addActionListener(e -> p.cerrar());p.cerrar();return;
                        }
                        var ventana=new VentanaPrincipal(identidad,sesion.directorio());
                        var p=new PresentadorTaller(puerto,ventana,identidad,trabajos);
                        ventana.conectar(p);ventana.conectarSimulacion(ejecucion);ventana.setVisible(true);dispose();p.refrescar();
                    });
                } catch(Exception e) {
                    trabajos.publicar(() -> {abriendo=false;mensaje.append("\nApertura no acreditada: "+e+"\nSe conserva cualquier intento parcial; no se reusará.");crear.setEnabled(!salir);if(salir)dispose();});
                    trabajos.terminar();
                }
            });
        }
    }
}
