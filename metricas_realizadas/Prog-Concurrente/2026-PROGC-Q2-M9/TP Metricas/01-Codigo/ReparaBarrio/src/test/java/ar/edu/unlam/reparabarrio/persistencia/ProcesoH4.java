package ar.edu.unlam.reparabarrio.persistencia;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import java.nio.file.*;
import java.util.*;
import static ar.edu.unlam.reparabarrio.persistencia.H4Fixture.ok;

/** Sólo auxiliar JUnit: jamás apunta a una base operativa. Señales, no sleeps. */
public final class ProcesoH4 {
    private static void señal(String punto, boolean pausa) {
        System.out.println("FRONTERA=" + punto); System.out.flush();
        if (pausa) try { if (System.in.read() == -1) throw new IllegalStateException("Controlador abandonó"); }
        catch (Exception e) { throw new RuntimeException(e); }
    }
    public static void main(String[] args) throws Exception {
        Path base = TestContextos.validar(Path.of(args[0]).resolve("taller"));
        System.out.println("PID=" + ProcessHandle.current().pid()); System.out.flush();
        if (args[1].equals("jdbc-h2")) { ArchivoH2H4Test.proceso(base, args[2]); return; }
        if (args[1].equals("guardia")) {
            try (var g = ar.edu.unlam.reparabarrio.arranque.GuardiaInstancia.adquirir(base.getParent())) { señal("GUARDIA", true); }
            return;
        }
        if (args[1].equals("inicializar") || args[1].equals("migrar")) {
            boolean migrar = args[1].equals("migrar");
            var a = new AutorizacionBase(args[3], base, args[4], migrar ? 1 : 0, 2,
                    migrar ? Path.of(args[5]) : null, true, true, "Autorización del controlador sobre fixture H4");
            var s = new InicializadorBase.Solicitud(migrar ? InicializadorBase.Modo.MIGRAR_V1 : InicializadorBase.Modo.CREAR_NUEVA, a);
            try (var g = ar.edu.unlam.reparabarrio.arranque.GuardiaInstancia.adquirir(base.getParent())) {
                InicializadorBase.preparar(g, s, fase -> { if (fase.equals(args[2])) señal(fase, true); });
            } return;
        }
        try (var f = new H4Fixture(base, null)) {
            System.out.println("UUID=" + f.contexto.identidad());
            f.evidencia("PROCESO " + args[1] + " RECUPERADO");
            if (args[1].equals("verificar")) {
                System.out.println("FILAS=" + f.filas());
                System.out.println("REVISIONES=" + f.numero("SELECT COUNT(*) FROM rb_revisiones WHERE cierre_id IS NULL"));
                System.out.println("ASIGNACIONES=" + f.numero("SELECT COUNT(*) FROM asignaciones"));
                System.out.println("OPERACIONES=" + f.contexto.informe().operaciones());
                System.out.println("ESTADO=" + f.contexto.informe().ciclo());
                if (args.length > 2 && args[2].equals("siguiente")) {
                    ok(f.servicio.registrarOrden("SIGUIENTE", "Tras reinicio", List.of()));
                    System.out.println("SIGUIENTE=" + f.numero("SELECT secuencia FROM ordenes WHERE id='SIGUIENTE'"));
                }
                return;
            }
            if (args[1].equals("limpio") || args[1].equals("caida")) {
                f.escenario(); f.evidencia("PROCESO " + args[1] + " PREVIO_CIERRE_O_CAIDA");
                System.out.println("FILAS=" + f.filas()); System.out.flush();
                if (args[1].equals("caida")) señal("ABIERTA", true);
                return;
            }
            if (args[1].equals("lectura")) {
                f.orden("A"); System.out.println("ANTERIOR=" + f.filas());
                f.negocio.set(new H4Fixture.Control("ROLLBACK"));
                f.infraestructura.set(new H4Fixture.Control("RR_PREPARAR_ANTES", false, false, fase -> señal("ANTES_DIAGNOSTICO_LECTURA", true)));
                f.servicio.consultar(); return;
            }
            String accion = args[2], frontera = args[3];
            f.capacidad(1); f.herramienta("M", TipoHerramienta.MULTIMETRO, true); f.herramienta("D", TipoHerramienta.TALADRO, true);
            f.orden("A", TipoHerramienta.MULTIMETRO, TipoHerramienta.TALADRO);
            PropuestaInicio propuesta = f.propuesta();
            if (accion.equals("CIERRE")) ok(f.servicio.confirmarInicio(propuesta));
            System.out.println("ANTERIOR=" + f.filas()); System.out.flush();
            var control = new H4Fixture.Control(frontera, false, false, fase -> señal(fase, !fase.equals("COMMIT_INVOCADO")));
            if (frontera.startsWith("RR_") || frontera.equals("INTENCION_DURABLE"))
                f.infraestructura.set(frontera.equals("INTENCION_DURABLE")
                        ? new H4Fixture.Control("COMMIT_DESPUES", false, false, fase -> señal("INTENCION_DURABLE", true)) : control);
            else f.negocio.set(control);
            var r = switch (accion) {
                case "REGISTRO" -> f.servicio.registrarOrden("B", "H4 B", List.of());
                case "INICIO" -> f.servicio.confirmarInicio(propuesta);
                case "CIERRE" -> f.servicio.finalizar("A");
                default -> throw new IllegalArgumentException("Acción no admitida");
            };
            System.out.println("RESULTADO=" + r.estado()); System.out.println("INTENTO=" + r.operacionId());
            System.out.println("POSTERIOR=" + f.filas()); System.out.flush(); señal("RESPUESTA", true);
        }
    }
}
