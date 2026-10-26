package ar.edu.unlam.reparabarrio.simulacion;
import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.arranque.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static ar.edu.unlam.reparabarrio.aplicacion.ResultadoOperacion.Estado.*;
import static ar.edu.unlam.reparabarrio.dominio.Recurso.Categoria.*;

@Timeout(30)
class AislamientoSimulacionTest {
    private final Path raiz=Path.of("").toAbsolutePath().normalize();
    private <T>T ok(ResultadoOperacion<T> r){assertEquals(CONFIRMADA,r.estado(),r::toString);return r.valor();}
    private List<String> decisiones(ContextoTaller c) {
        assertEquals(ContextoTaller.Ciclo.HABILITADO,c.ciclo(),()->c.informe().toString());var s=c.servicio();
        ok(s.registrarRecurso(TECNICO,"T2",null,true));ok(s.registrarRecurso(TECNICO,"T1",null,false));ok(s.registrarRecurso(PUESTO,"P1",null,true));ok(s.registrarRecurso(HERRAMIENTA,"M1",TipoHerramienta.MULTIMETRO,false));
        ok(s.registrarOrden("A","Imposible inicialmente",Set.of(TipoHerramienta.MULTIMETRO)));ok(s.registrarOrden("B","Factible",Set.of()));ok(s.registrarOrden("C","Posterior",Set.of()));
        var p=ok(s.consultar()).planificacion().propuesta().orElseThrow();assertEquals("B",p.ordenId());
        ok(s.cambiarHabilitacion(new Recurso.Clave(HERRAMIENTA,"M1"),true));var antes=ok(s.consultar());assertEquals(RECHAZADA,s.confirmarInicio(p).estado());assertEquals(antes,ok(s.consultar()));
        var a=ok(s.consultar()).planificacion().propuesta().orElseThrow();assertEquals("A",a.ordenId());ok(s.confirmarInicio(a));ok(s.fallar("A"));
        var b=ok(s.consultar()).planificacion().propuesta().orElseThrow();assertEquals("T2",b.recursos().tecnico().id());
        ok(s.cambiarHabilitacion(new Recurso.Clave(TECNICO,"T1"),true));assertEquals("T1",ok(s.consultar()).planificacion().propuesta().orElseThrow().recursos().tecnico().id());
        ok(s.confirmarInicio(b));assertEquals(RECHAZADA,s.confirmarInicio(b).estado());ok(s.finalizar("B"));ok(s.cancelar("C"));
        var fin=ok(s.consultar());assertTrue(fin.dominio().asignaciones().isEmpty());
        return fin.historial().stream().map(e->e.accion()+":"+e.ordenId()+":"+e.recursos()).toList();
    }
    @Test void prioridadYCambioAjenoPreservanSeleccionExactaEnMemoria() throws Exception {
        try(var c=FabricaContextos.crearSimulacion(raiz,true)){decisiones(c);}
    }
    @Test void equivalenciaConArchivoNuevoYAislamientoOperativoDeFixture() throws Exception {
        try(var operativo=FabricaContextos.crearAcademico(raiz,"h5-h6-equivalencia-"+UUID.randomUUID(),true)) {
            var decisionesOperativas=decisiones(operativo);var antes=ok(operativo.servicio().consultar());
            try(var a=FabricaContextos.crearSimulacion(raiz,true);var b=FabricaContextos.crearSimulacion(raiz,true)) {
                assertEquals(decisionesOperativas,decisiones(a));assertEquals(decisionesOperativas,decisiones(b));
                assertEquals(antes,ok(operativo.servicio().consultar()));assertNotEquals(a.identidad(),b.identidad());assertNotEquals(a.identidad(),operativo.identidad());
            }
            assertEquals(antes,ok(operativo.servicio().consultar()));
        }
    }
    @Test void motoresSimultaneosYSucesivosTienenPropietariosNuevos() throws Exception {
        var config=new ConfiguracionEscenario(1,10,2,ConfiguracionEscenario.inventario(2,2,0,0,0,true),List.of(1,0,0,0,0,0,0,0),ConfiguracionEscenario.Llegadas.SIMULTANEAS,0,0,2,2,0);
        String anterior;
        try(var a=MotorSimulacion.crear(raiz,config);var b=MotorSimulacion.crear(raiz,config)) {
            anterior=a.identidad();assertNotEquals(anterior,b.identidad());var fa=a.iniciar();var fb=b.iniciar();
            for(var r:List.of(fa.get(20,TimeUnit.SECONDS),fb.get(20,TimeUnit.SECONDS))){assertEquals(MotorSimulacion.Motivo.COMPLETADA,r.motivo());assertEquals(10,r.registradas());assertEquals(10,r.captura().taller().dominio().ordenes().size());}
        }
        try(var nueva=MotorSimulacion.crear(raiz,config)){assertNotEquals(anterior,nueva.identidad());assertNull(nueva.progreso().captura());assertEquals(MotorSimulacion.Motivo.COMPLETADA,nueva.iniciar().get(20,TimeUnit.SECONDS).motivo());}
    }
}
