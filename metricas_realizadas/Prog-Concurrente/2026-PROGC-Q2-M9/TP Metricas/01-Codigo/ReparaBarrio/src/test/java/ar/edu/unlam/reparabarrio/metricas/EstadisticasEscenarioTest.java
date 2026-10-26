package ar.edu.unlam.reparabarrio.metricas;
import ar.edu.unlam.reparabarrio.arranque.*;
import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;
import static ar.edu.unlam.reparabarrio.dominio.Recurso.Categoria.*;

@Timeout(30)
class EstadisticasEscenarioTest {
    private final AtomicLong nanos=new AtomicLong();
    private void ms(long t){nanos.set(t*1_000_000L);}
    private <T> T ok(ResultadoOperacion<T> r){assertEquals(ResultadoOperacion.Estado.CONFIRMADA,r.estado(),r::toString);return r.valor();}
    private void preparar(ContextoTaller c){ok(c.servicio().registrarRecurso(TECNICO,"T1",null,true));ok(c.servicio().registrarRecurso(PUESTO,"P1",null,true));}
    private EstadisticasEscenario calcular(ContextoTaller c,long inicio){var captura=ok(c.servicio().consultarHechos());return new CalculadorEstadisticas().calcular(captura,inicio,captura.nanos(),true);}
    @Test void intervalosExactosAltaTardiaHabilitacionParcialYRecorte() throws Exception {
        try(var c=FixtureTemporalH6.crear(Clock.systemUTC(),nanos::get)) {
            preparar(c);ms(10);ok(c.servicio().registrarOrden("O1","H6",Set.of()));
            ms(20);ok(c.servicio().confirmarInicio(ok(c.servicio().consultar()).planificacion().propuesta().orElseThrow()));
            ms(40);ok(c.servicio().finalizar("O1"));ms(50);ok(c.servicio().cambiarHabilitacion(new Recurso.Clave(TECNICO,"T1"),false));
            ms(60);ok(c.servicio().registrarRecurso(HERRAMIENTA,"M1",TipoHerramienta.MULTIMETRO,true));
            ok(c.servicio().registrarRecurso(HERRAMIENTA,"S1",TipoHerramienta.SOLDADOR,false));
            ms(80);ok(c.servicio().cambiarHabilitacion(new Recurso.Clave(TECNICO,"T1"),true));ms(100);
            var e=calcular(c,0);assertNull(e.incompleta());assertEquals(1,e.procesadas());assertEquals(10,e.esperaPromedioMs());assertEquals(0.1,e.duracionSegundos());
            var t=e.utilizacion().get(new Recurso.Clave(TECNICO,"T1"));assertEquals(70_000_000,t.habilitadoNanos());assertEquals(20_000_000,t.ocupadoNanos());assertEquals(2000.0/70,t.porcentaje(),1e-10);
            assertEquals(40_000_000,e.utilizacion().get(new Recurso.Clave(HERRAMIENTA,"M1")).habilitadoNanos());
            assertNull(e.utilizacion().get(new Recurso.Clave(HERRAMIENTA,"S1")).porcentaje());
            var recorte=calcular(c,15_000_000);assertEquals(55_000_000,recorte.utilizacion().get(new Recurso.Clave(TECNICO,"T1")).habilitadoNanos());
        }
    }
    @Test void abiertosCanceladasFallidasRelojCivilYPeriodoCero() throws Exception {
        var civil=new Clock(){long llamadas;public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId z){return this;}public Instant instant(){return Instant.ofEpochSecond(1000-llamadas++*100);}};
        try(var c=FixtureTemporalH6.crear(civil,nanos::get)) {
            preparar(c);var cero=calcular(c,0);assertNull(cero.esperaPromedioMs());assertEquals(0,cero.duracionSegundos());assertTrue(cero.utilizacion().values().stream().allMatch(u->u.porcentaje()==null));
            ms(10);ok(c.servicio().registrarOrden("O1","H6",Set.of()));ok(c.servicio().registrarOrden("O2","H6",Set.of()));ok(c.servicio().cancelar("O2"));
            ms(20);ok(c.servicio().confirmarInicio(ok(c.servicio().consultar()).planificacion().propuesta().orElseThrow()));ms(30);
            var abierta=calcular(c,0);assertEquals(10,abierta.esperaPromedioMs());assertEquals(0,abierta.procesadas());assertEquals(1,abierta.estados().get(EstadoOrden.CANCELADA));
            assertEquals(10_000_000,abierta.utilizacion().get(new Recurso.Clave(TECNICO,"T1")).ocupadoNanos());
            ms(40);ok(c.servicio().fallar("O1"));var cerrada=calcular(c,0);assertEquals(1,cerrada.procesadas());assertEquals(1,cerrada.estados().get(EstadoOrden.FALLIDA));assertEquals(10,cerrada.esperaPromedioMs());
            var captura=ok(c.servicio().consultarHechos());
            var incompleta=new HechosConfirmados.Captura(captura.taller(),captura.hechos().subList(1,captura.hechos().size()),captura.nanos(),null);
            var nd=new CalculadorEstadisticas().calcular(incompleta,0,captura.nanos(),true);assertNotNull(nd.incompleta());assertNull(nd.esperaPromedioMs());assertTrue(nd.utilizacion().isEmpty());
            assertNotNull(new CalculadorEstadisticas().calcular(captura,0,captura.nanos(),false).incompleta());
        }
    }
}
