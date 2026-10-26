package ar.edu.unlam.reparabarrio.arranque;
import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.persistencia.*;
import ar.edu.unlam.reparabarrio.aplicacion.puertos.RelojIntervalos;
import java.time.Clock;
import java.nio.file.*;
import java.util.UUID;
public final class FixtureTemporalH6 {
    private FixtureTemporalH6(){}
    public static ContextoTaller crear(Clock civil,RelojIntervalos reloj) throws Exception {
        var d=Files.createDirectory(Path.of("target/h6-"+UUID.randomUUID()).toAbsolutePath());
        var g=GuardiaInstancia.adquirir(d);
        var a=PersistenciaH2.prepararSimulacion(g,AutorizacionSimulacion.nueva(g,true));
        return ContextoTaller.componerSimulacion(a,c->{},civil,reloj);
    }
}
