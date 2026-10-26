package ar.edu.unlam.reparabarrio.aplicacion;

import ar.edu.unlam.reparabarrio.aplicacion.puertos.*;
import ar.edu.unlam.reparabarrio.arranque.*;
import ar.edu.unlam.reparabarrio.persistencia.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;
import static ar.edu.unlam.reparabarrio.aplicacion.ResultadoOperacion.Estado.*;

@Timeout(30)
class HechosConfirmadosTest {
    private ContextoTaller crear(UnaryOperator<UnidadTrabajo.Fabrica> negocio, Clock civil, RelojIntervalos reloj) throws Exception {
        var d = Files.createDirectory(Path.of("target/h6-"+UUID.randomUUID()).toAbsolutePath());
        var g = GuardiaInstancia.adquirir(d);
        var a = PersistenciaH2.prepararSimulacion(g, AutorizacionSimulacion.nueva(g,true));
        var c = ContextoTaller.componerTemporal(a, x -> {},new CoordinadorTaller(),negocio,UnaryOperator.identity(),null,civil);
        c.servicio().habilitarHechos(reloj);
        assertEquals(ContextoTaller.Ciclo.HABILITADO,c.ciclo());
        return c;
    }
    private UnaryOperator<UnidadTrabajo.Fabrica> fallo(String fase) {
        var primerCierre=new AtomicBoolean(true);
        return f -> () -> {
            var u=f.abrir();
            return new UnidadTrabajo() {
                public RepositorioTaller repositorio(){return u.repositorio();}
                public RepositorioRecuperacion recuperacion(){return u.recuperacion();}
                public boolean terminada(){return u.terminada();}
                public void commit() throws Exception {
                    if(fase.equals("rollback"))throw new IllegalStateException("antes de commit");
                    u.commit();
                    if(fase.equals("incierto"))throw new IllegalStateException("commit sin respuesta");
                }
                public void rollback() throws Exception {u.rollback();}
                public void close() throws Exception {u.close();if(fase.equals("cierre") && primerCierre.getAndSet(false))throw new IllegalStateException("cierre posterior");}
            };
        };
    }
    @Test void commitInciertoNoInventaHechoNiReintenta() throws Exception {
        var lecturas=new AtomicInteger();
        try(var c=crear(fallo("incierto"),Clock.systemUTC(),lecturas::incrementAndGet)) {
            var r=c.servicio().registrarOrden("O1","H6",Set.of());
            assertEquals(PENDIENTE_VERIFICACION,r.estado());
            assertEquals(ContextoTaller.Ciclo.BLOQUEADO,c.ciclo());
            assertEquals(PENDIENTE_VERIFICACION,c.servicio().consultarHechos().estado());
            assertEquals(0,lecturas.get(),"Commit incierto no toma marca de confirmación");
        }
    }
    @Test void commitConCierreFallidoSigueConfirmado() throws Exception {
        try(var c=crear(fallo("cierre"),Clock.systemUTC(),System::nanoTime)) {
            var r=c.servicio().registrarOrden("O1","H6",Set.of());
            assertEquals(CONFIRMADA,r.estado());
            assertTrue(r.diagnostico().contains("Fallo al cerrar"));
            var s=c.servicio().consultarHechos();assertEquals(CONFIRMADA,s.estado());
            assertEquals(1,s.valor().hechos().size());assertEquals(r.operacionId(),s.valor().hechos().getFirst().evento().operacionId());
        }
    }
    @Test void relojCivilInyectadoYRechazoSinHecho() throws Exception {
        var nanos=new AtomicLong(10);
        var civil=Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC);
        try(var c=crear(UnaryOperator.identity(),civil,nanos::get)) {
            assertEquals(CONFIRMADA,c.servicio().registrarOrden("O1","H6",Set.of()).estado());
            nanos.set(20);
            assertEquals(RECHAZADA,c.servicio().registrarOrden("O1","H6",Set.of()).estado());
            var s=c.servicio().consultarHechos().valor();
            assertEquals(1,s.hechos().size());assertEquals(10,s.hechos().getFirst().nanos());
            assertEquals(civil.instant(),s.hechos().getFirst().evento().momento());
            assertEquals(s.taller().historial(),s.hechos().stream().map(HechosConfirmados.Hecho::evento).toList());
        }
    }
    @Test void falloTemporalPosteriorNoRevierteNegocio() throws Exception {
        var falla=new AtomicBoolean(true);
        try(var c=crear(UnaryOperator.identity(),Clock.systemUTC(),() -> {if(falla.get())throw new IllegalStateException("reloj");return 50;})) {
            assertEquals(CONFIRMADA,c.servicio().registrarOrden("O1","H6",Set.of()).estado());
            falla.set(false);
            var s=c.servicio().consultarHechos().valor();
            assertNotNull(s.incompleta());assertEquals(1,s.taller().dominio().ordenes().size());assertTrue(s.hechos().isEmpty());
        }
    }
    @Test void rollbackConocidoNoAgregaHechoNiEfecto() throws Exception {
        UnaryOperator<UnidadTrabajo.Fabrica> decorador=f -> () -> {
            var u=f.abrir();
            var repositorio=(RepositorioTaller)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{RepositorioTaller.class},(proxy,metodo,args)->{
                if(metodo.getName().equals("registrar"))throw new IllegalStateException("Falla antes de escritura");
                try{return metodo.invoke(u.repositorio(),args);}catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
            });
            return new UnidadTrabajo(){
                public RepositorioTaller repositorio(){return repositorio;}public RepositorioRecuperacion recuperacion(){return u.recuperacion();}
                public boolean terminada(){return u.terminada();}public void commit() throws Exception{u.commit();}public void rollback() throws Exception{u.rollback();}public void close() throws Exception{u.close();}
            };
        };
        try(var c=crear(decorador,Clock.systemUTC(),System::nanoTime)) {
            assertEquals(ERROR_TECNICO,c.servicio().registrarOrden("O1","H6",Set.of()).estado());
            var s=c.servicio().consultarHechos();assertEquals(CONFIRMADA,s.estado());assertTrue(s.valor().hechos().isEmpty());assertTrue(s.valor().taller().dominio().ordenes().isEmpty());
        }
    }
}
