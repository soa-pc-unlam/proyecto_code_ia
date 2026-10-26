package ar.edu.unlam.reparabarrio.simulacion;
import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.arranque.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import ar.edu.unlam.reparabarrio.metricas.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(60)
class MotorSimulacionTest {
    private final Path raiz=Path.of("").toAbsolutePath().normalize();
    private ConfiguracionEscenario config(int n,int w,int m,List<Integer> pesos,long intervalo,long duracion) {
        return new ConfiguracionEscenario(20261015,n,w,ConfiguracionEscenario.inventario(2,2,m,1,0,true),pesos,
                ConfiguracionEscenario.Llegadas.PERIODICAS,intervalo,intervalo,duracion,duracion,0);
    }
    private List<Integer> solo(int mascara){var p=new ArrayList<>(Collections.nCopies(8,0));p.set(mascara,1);return p;}
    @Test void dosTrabajosSolapadosYConsultasMientrasTrabajan() throws Exception {
        var dentro=new CountDownLatch(2);var liberar=new CountDownLatch(1);
        var contexto=FabricaContextos.crearSimulacion(raiz,true);
        var carga=new GeneradorCarga().generar(config(2,2,0,solo(0),0,50));
        var registrosDentro=new CountDownLatch(2);var registrosLiberar=new CountDownLatch(1);
        try(var motor=new MotorSimulacion(contexto,carga,(id,ms)->{dentro.countDown();assertTrue(liberar.await(10,TimeUnit.SECONDS));},(accion,id)->{
            if(accion.equals("REGISTRO")){registrosDentro.countDown();try{if(!registrosLiberar.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Timeout de test");}catch(InterruptedException e){throw new IllegalStateException(e);}}
        })) {
            var f=motor.iniciar();
            try{assertTrue(registrosDentro.await(10,TimeUnit.SECONDS));}finally{registrosLiberar.countDown();}
            try {
                assertTrue(dentro.await(10,TimeUnit.SECONDS),()->motor.progreso().toString());
                var s=contexto.servicio().consultar().valor();
                assertEquals(2,s.dominio().asignaciones().size());assertEquals(2,s.dominio().ordenes().stream().filter(o->o.estado()==EstadoOrden.EN_CURSO).count());
                assertFalse(f.isDone());
            } finally {liberar.countDown();}
            var r=f.get(15,TimeUnit.SECONDS);assertEquals(MotorSimulacion.Motivo.COMPLETADA,r.motivo(),r::toString);
            var trabajos=r.trazas().stream().filter(t->t.accion().equals("TRABAJO")).toList();
            assertEquals(2,trabajos.size());assertNotEquals(trabajos.get(0).hilo(),trabajos.get(1).hilo());
            assertTrue(Math.max(trabajos.get(0).inicio(),trabajos.get(1).inicio())<Math.min(trabajos.get(0).fin(),trabajos.get(1).fin()));
            var registros=r.trazas().stream().filter(t->t.accion().equals("REGISTRO")).toList();
            assertEquals(2,registros.size());assertNotEquals(registros.get(0).hilo(),registros.get(1).hilo());
            assertTrue(Math.max(registros.get(0).inicio(),registros.get(1).inicio())<Math.min(registros.get(0).fin(),registros.get(1).fin()));
        }
        assertEquals(ContextoTaller.Ciclo.CERRADO,contexto.ciclo());
    }
    @Test void herramientaUnicaNoSolapaNiReservaParcialYAvanza() throws Exception {
        try(var m=MotorSimulacion.crear(raiz,config(8,4,1,solo(1),0,5))) {
            var r=m.iniciar().get(20,TimeUnit.SECONDS);assertEquals(MotorSimulacion.Motivo.COMPLETADA,r.motivo(),r::toString);
            replay(r);var e=new CalculadorEstadisticas().calcular(r.captura(),r.comienzo(),r.fin(),true);
            assertNull(e.incompleta());assertEquals(8,e.procesadas());
            boolean ocupada=false;for(var h:r.captura().hechos())if(h.evento().recursos().contains(new Recurso.Clave(Recurso.Categoria.HERRAMIENTA,"M1"))) {
                switch(h.evento().accion()){case "INICIO" -> {assertFalse(ocupada);ocupada=true;}case "FINALIZADA" -> {assertTrue(ocupada);ocupada=false;}default -> {}}
            }assertFalse(ocupada);
        }
    }
    @Test void ceroImposiblesYFuturasNoTerminanAntes() throws Exception {
        for(int n:new int[]{0,5})try(var m=MotorSimulacion.crear(raiz,config(n,1,0,solo(1),30,0))) {
            var r=m.iniciar().get(20,TimeUnit.SECONDS);assertEquals(n==0?MotorSimulacion.Motivo.COMPLETADA:MotorSimulacion.Motivo.SIN_PROGRESO,r.motivo());
            assertEquals(n,r.registradas());assertEquals(n,r.captura().taller().dominio().ordenes().size());
            assertTrue(r.captura().taller().dominio().asignaciones().isEmpty());
            if(n>0){assertTrue(r.fin()-r.comienzo()>=120_000_000L);assertTrue(r.captura().taller().planificacion().causas().values().stream().allMatch(v->v.stream().anyMatch(s->s.contains("MULTIMETRO"))));}
        }
    }
    @Test void pendienteNoRetieneTecnicoNiPuestoMientrasHerramientaEstaOcupada() throws Exception {
        var dentro=new CountDownLatch(1);var liberar=new CountDownLatch(1);var c=FabricaContextos.crearSimulacion(raiz,true);
        var registrosDentro=new CountDownLatch(2);var registrosLiberar=new CountDownLatch(1);
        try(var m=new MotorSimulacion(c,new GeneradorCarga().generar(config(2,2,1,solo(1),0,0)),(id,ms)->{dentro.countDown();assertTrue(liberar.await(10,TimeUnit.SECONDS));},(accion,id)->{
            if(accion.equals("REGISTRO")){registrosDentro.countDown();try{if(!registrosLiberar.await(10,TimeUnit.SECONDS))throw new IllegalStateException("Timeout de registro");}catch(InterruptedException e){throw new IllegalStateException(e);}}
        })) {
            var f=m.iniciar();try {
                try{assertTrue(registrosDentro.await(10,TimeUnit.SECONDS));}finally{registrosLiberar.countDown();}
                assertTrue(dentro.await(10,TimeUnit.SECONDS));
                var s=c.servicio().consultar().valor();
                assertEquals(1,s.dominio().asignaciones().size());
                assertEquals(1,s.dominio().ordenes().stream().filter(o->o.estado()==EstadoOrden.PENDIENTE).count());
                assertEquals(Recurso.Disponibilidad.DISPONIBLE,s.dominio().disponibilidad(new Recurso.Clave(Recurso.Categoria.TECNICO,"T2")));
                assertEquals(Recurso.Disponibilidad.DISPONIBLE,s.dominio().disponibilidad(new Recurso.Clave(Recurso.Categoria.PUESTO,"P2")));assertFalse(f.isDone());
            } finally {liberar.countDown();}
            assertEquals(MotorSimulacion.Motivo.COMPLETADA,f.get(15,TimeUnit.SECONDS).motivo());
        }
    }
    @Test void incidenteDeTrabajoHaceUnSoloFalloConSaludAcreditadaYTerminaTecnico() throws Exception {
        var c=FabricaContextos.crearSimulacion(raiz,true);
        try(var m=new MotorSimulacion(c,new GeneradorCarga().generar(config(1,1,0,solo(0),0,0)),(id,ms)->{throw new IllegalStateException("Incidente ficticio de infraestructura");})) {
            var r=m.iniciar().get(15,TimeUnit.SECONDS);assertEquals(MotorSimulacion.Motivo.ERROR_TECNICO,r.motivo());
            assertEquals(EstadoOrden.FALLIDA,r.captura().taller().dominio().ordenes().getFirst().estado());
            assertEquals(1,r.trazas().stream().filter(t->t.accion().equals("FALLO_POR_INCIDENTE")).count());
            assertTrue(r.captura().taller().dominio().asignaciones().isEmpty());
            assertNotNull(new CalculadorEstadisticas().calcular(r.captura(),r.comienzo(),r.fin(),false).incompleta());
        }
    }
    @Test void salidaDrenaTrabajoYRetiraLlegadasFuturas() throws Exception {
        var dentro=new CountDownLatch(1);var liberar=new CountDownLatch(1);
        var c=FabricaContextos.crearSimulacion(raiz,true);
        try(var m=new MotorSimulacion(c,new GeneradorCarga().generar(config(10,1,0,solo(0),1000,0)),(id,ms)->{dentro.countDown();assertTrue(liberar.await(10,TimeUnit.SECONDS));})) {
            var f=m.iniciar();try{assertTrue(dentro.await(10,TimeUnit.SECONDS));m.solicitarSalida();assertFalse(f.isDone());}finally{liberar.countDown();}
            var r=f.get(15,TimeUnit.SECONDS);assertEquals(MotorSimulacion.Motivo.SALIDA_SOLICITADA,r.motivo());assertEquals(9,r.noEntregadas());assertEquals(EstadoOrden.FINALIZADA,r.captura().taller().dominio().ordenes().getFirst().estado());
        }
    }
    @Test void perfilesCienOrdenesReales() throws Exception {
        for(boolean limitado:new boolean[]{false,true})try(var m=MotorSimulacion.crear(raiz,ConfiguracionEscenario.referencia(limitado))) {
            var r=m.iniciar().get(45,TimeUnit.SECONDS);assertEquals(limitado?MotorSimulacion.Motivo.SIN_PROGRESO:MotorSimulacion.Motivo.COMPLETADA,r.motivo(),r::toString);
            assertEquals(100,r.registradas());replay(r);
            var e=new CalculadorEstadisticas().calcular(r.captura(),r.comienzo(),r.fin(),true);assertNull(e.incompleta(),e::toString);
            assertEquals(0,e.estados().get(EstadoOrden.EN_CURSO));assertEquals(100,e.estados().values().stream().mapToLong(Long::longValue).sum());
            Path log=Path.of("metricas/logs/h6/continuacion-100-"+(limitado?"limitado-":"suficiente-")+UUID.randomUUID()+".txt");
            Files.writeString(log,"Entrada completa: "+r.carga()+"\nResultado: "+r.motivo()+"\nEstadísticas: "+e+"\nHechos: "+r.captura().hechos()+"\nTrazas: "+r.trazas(),StandardOpenOption.CREATE_NEW);
        }
    }
    /** Oráculo por replay: secuencia real, recursos disponibles y primera factible sin usar Planificador. */
    private void replay(MotorSimulacion.Resultado resultado) {
        var pendientes=new LinkedHashMap<String,Set<TipoHerramienta>>();var ocupadas=new HashSet<Recurso.Clave>();
        var asignadas=new HashMap<String,Set<Recurso.Clave>>();var recursos=resultado.carga().configuracion().recursos();
        for(var h:resultado.captura().hechos()) {
            var e=h.evento();
            if(e.accion().equals("REGISTRO_ORDEN"))pendientes.put(e.ordenId(),h.descriptor().requisitos());
            if(e.accion().equals("INICIO")) {
                String primera=pendientes.entrySet().stream().filter(o->{
                    var libres=recursos.stream().filter(r->r.habilitado()&&!ocupadas.contains(r.clave())).toList();
                    return libres.stream().anyMatch(r->r.clave().categoria()==Recurso.Categoria.TECNICO) && libres.stream().anyMatch(r->r.clave().categoria()==Recurso.Categoria.PUESTO)
                            && o.getValue().stream().allMatch(t->libres.stream().anyMatch(r->r.tipo()==t));
                }).map(Map.Entry::getKey).findFirst().orElseThrow();
                assertEquals(primera,e.ordenId());pendientes.remove(primera);
                for(var clave:e.recursos())assertTrue(ocupadas.add(clave));asignadas.put(primera,e.recursos());
            }
            if(Set.of("FINALIZADA","FALLIDA").contains(e.accion())){assertEquals(asignadas.remove(e.ordenId()),e.recursos());e.recursos().forEach(r->assertTrue(ocupadas.remove(r)));}
        }
        assertTrue(ocupadas.isEmpty());assertTrue(asignadas.isEmpty());
    }
}
