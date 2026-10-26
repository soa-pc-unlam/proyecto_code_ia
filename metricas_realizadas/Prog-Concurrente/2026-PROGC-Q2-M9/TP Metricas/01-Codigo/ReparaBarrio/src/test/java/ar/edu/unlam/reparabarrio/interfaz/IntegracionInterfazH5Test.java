package ar.edu.unlam.reparabarrio.interfaz;

import ar.edu.unlam.reparabarrio.arranque.*;
import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static ar.edu.unlam.reparabarrio.aplicacion.ResultadoOperacion.Estado.*;
import static ar.edu.unlam.reparabarrio.dominio.Recurso.Categoria.*;

@Timeout(90)
class IntegracionInterfazH5Test {
    private static <T> T confirmado(ResultadoOperacion<T> r){assertEquals(CONFIRMADA,r.estado(),r::toString);return r.valor();}
    @Test void archivoNuevoDosEnCursoCierresHistorialYGuardia_T01_T02_T04_T10_T11_T12() throws Exception {
        var sesion=ArranqueAcademico.crear(Path.of("").toAbsolutePath(),true);
        System.out.println("H5 fixture="+sesion.directorio());
        try(var c=sesion.contexto()) {
            assertEquals(ContextoTaller.Ciclo.HABILITADO,c.ciclo(),()->c.informe().toString());
            assertThrows(java.io.IOException.class,()->GuardiaInstancia.adquirir(sesion.directorio()));
            var p=new AdaptadorServicioTaller(c);
            for(int n=1;n<=2;n++){confirmado(p.registrarRecurso(TECNICO,"T"+n,null,true));confirmado(p.registrarRecurso(PUESTO,"P"+n,null,true));}
            confirmado(p.registrarRecurso(HERRAMIENTA,"M1",TipoHerramienta.MULTIMETRO,true));
            confirmado(p.registrarOrden("IMPOSIBLE","Sin taladro",Set.of(TipoHerramienta.TALADRO)));
            confirmado(p.registrarOrden("A","Primera",Set.of(TipoHerramienta.MULTIMETRO)));confirmado(p.registrarOrden("B","Segunda",Set.of()));confirmado(p.registrarOrden("C","Cancelación",Set.of()));
            var antes=confirmado(p.consultar());var exacta=antes.planificacion().propuesta().orElseThrow();assertEquals("A",exacta.ordenId());assertEquals(antes,confirmado(p.consultar()));assertTrue(antes.dominio().asignaciones().isEmpty());assertFalse(antes.planificacion().causas().get("IMPOSIBLE").isEmpty());
            assertEquals(RECHAZADA,p.confirmarInicio(new PropuestaInicio(c.identidad(),"B",exacta.recursos())).estado());
            confirmado(p.confirmarInicio(exacta));confirmado(p.confirmarInicio(confirmado(p.consultar()).planificacion().propuesta().orElseThrow()));
            var simultaneas=confirmado(p.consultar());assertEquals(2,VentanaPrincipal.contar(simultaneas).get(EstadoOrden.EN_CURSO));assertEquals(2,simultaneas.dominio().asignaciones().size());assertEquals(RECHAZADA,p.cambiarHabilitacion(exacta.recursos().tecnico(),false).estado());
            confirmado(p.finalizar("A"));confirmado(p.fallar("B"));confirmado(p.cancelar("C"));var fin=confirmado(p.consultar());
            var contadores=VentanaPrincipal.contar(fin);assertEquals(Map.of(EstadoOrden.PENDIENTE,1L,EstadoOrden.EN_CURSO,0L,EstadoOrden.FINALIZADA,1L,EstadoOrden.FALLIDA,1L,EstadoOrden.CANCELADA,1L),contadores);
            assertTrue(fin.dominio().asignaciones().isEmpty());assertEquals(14,fin.historial().size());assertEquals(RECHAZADA,p.finalizar("A").estado());assertEquals(fin,confirmado(p.consultar()));
            confirmado(p.cambiarHabilitacion(exacta.recursos().tecnico(),false));assertEquals(Recurso.Disponibilidad.FUERA_DE_SERVICIO,confirmado(p.consultar()).dominio().disponibilidad(exacta.recursos().tecnico()));
            confirmado(p.cambiarHabilitacion(exacta.recursos().tecnico(),true));c.close();assertEquals(ContextoTaller.Ciclo.CERRADO,c.ciclo());assertTrue(c.informe().hallazgos().isEmpty(),()->c.informe().toString());
        }
        try(var g=GuardiaInstancia.adquirir(sesion.directorio())){assertTrue(g.vigente());assertTrue(g.baseConocida());}
        assertTrue(Files.exists(sesion.directorio().resolve("instancia.lock")));
    }
    @Test void presentadorRealEntregaCapturaYAltaFueraDeEdt_T12() throws Exception {
        var s=ArranqueAcademico.crear(Path.of("").toAbsolutePath(),true);
        try(var contexto=s.contexto()) {
            assertEquals(ContextoTaller.Ciclo.HABILITADO,contexto.ciclo(),()->contexto.informe().toString());
            var trabajos=new ArrayDeque<Runnable>();var entregas=new ArrayDeque<Runnable>();var vista=new PresentadorTallerTest.Vista();
            var p=new PresentadorTaller(new AdaptadorServicioTaller(contexto),vista,contexto.identidad(),trabajos::add,entregas::add,()->{});
            PresentadorTallerTest.edt(p::refrescar);trabajos.remove().run();PresentadorTallerTest.edt(entregas.remove());
            PresentadorTallerTest.edt(()->p.registrarOrden("GUI","Alta por presentador",Set.of()));trabajos.remove().run();PresentadorTallerTest.edt(entregas.remove());trabajos.remove().run();PresentadorTallerTest.edt(entregas.remove());
            assertEquals("GUI",vista.captura.dominio().ordenes().getFirst().id());assertTrue(vista.habilitada);
        }
    }
}
