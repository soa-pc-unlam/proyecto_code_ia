package ar.edu.unlam.reparabarrio.interfaz;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import ar.edu.unlam.reparabarrio.planificacion.Planificador;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import javax.swing.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;
import static org.junit.jupiter.api.Assertions.*;
import static ar.edu.unlam.reparabarrio.aplicacion.ResultadoOperacion.Estado.*;

@Timeout(15)
class PresentadorTallerTest {
    static void edt(Runnable r) throws Exception { SwingUtilities.invokeAndWait(r); }
    static InstantaneaTaller captura(long f,long t) {
        var dominio=new InstantaneaDominio("BASE",List.of(Orden.pendiente("A","Reparación",1,List.of())),List.of(),Map.of());
        return new InstantaneaTaller(f,dominio,Set.of(TipoHerramienta.TALADRO),List.of(),new Planificador.Resultado(Optional.of(propuesta()),Map.of("Z",List.of("Sin técnico","Sin puesto"))),t,Map.of());
    }
    static PropuestaInicio propuesta() { return new PropuestaInicio("BASE","A",new SeleccionRecursos(new Recurso.Clave(Recurso.Categoria.TECNICO,"T1"),new Recurso.Clave(Recurso.Categoria.PUESTO,"P1"),Map.of())); }
    static class Puerto implements PuertoTaller {
        InstantaneaTaller captura=captura(1,1); ResultadoOperacion.Estado resultado=CONFIRMADA;
        ContextoTaller.Ciclo ciclo=ContextoTaller.Ciclo.HABILITADO;
        ResultadoOperacion.DiagnosticoTecnico tecnico; int lecturas,mutaciones,cierres; PropuestaInicio recibida;
        boolean fallaLectura,fallaMutacion,fallaCierre,advertencia; Runnable alConsultar=()->{};
        void fuera(){assertFalse(SwingUtilities.isEventDispatchThread());}
        public String identidad(){fuera();return "BASE";}
        public ResultadoOperacion<InstantaneaTaller> consultar(){fuera();lecturas++;alConsultar.run();if(fallaLectura)throw new IllegalStateException("Lectura fallida");return new ResultadoOperacion<>(CONFIRMADA,"BASE","L",captura,null);}
        ResultadoOperacion<?> mutar(){fuera();mutaciones++;if(fallaMutacion)throw new IllegalStateException("Sin resultado");return new ResultadoOperacion<>(resultado,"BASE","OP",null,"Motivo íntegro",tecnico);}
        public ResultadoOperacion<?> confirmarInicio(PropuestaInicio p){recibida=p;return mutar();}
        public ResultadoOperacion<?> registrarOrden(String i,String d,Set<TipoHerramienta> t){return mutar();}
        public ResultadoOperacion<?> registrarRecurso(Recurso.Categoria c,String i,TipoHerramienta t,boolean h){return mutar();}
        public ResultadoOperacion<?> finalizar(String i){return mutar();} public ResultadoOperacion<?> fallar(String i){return mutar();} public ResultadoOperacion<?> cancelar(String i){return mutar();}
        public ResultadoOperacion<?> cambiarHabilitacion(Recurso.Clave c,boolean h){return mutar();}
        public InformeRecuperacion informe(){fuera();return new InformeRecuperacion("BASE",ciclo,"S",Map.of(),Map.of(),advertencia?List.of("Marca limpia no verificada"):List.of(),null,1);}
        public ContextoTaller.Ciclo ciclo(){fuera();return ciclo;}
        public void cerrar(){fuera();cierres++;if(fallaCierre)throw new IllegalStateException("Guardia retenida");ciclo=ContextoTaller.Ciclo.CERRADO;}
    }
    static class Vista implements VistaTaller {
        InstantaneaTaller captura; boolean habilitada,vigente,acepta=true,cerrada; int renderizados,dialogos; Runnable duranteDialogo=()->{};
        final List<String> mensajes=new ArrayList<>(),diagnosticos=new ArrayList<>();
        void enEdt(){assertTrue(SwingUtilities.isEventDispatchThread());}
        public void captura(InstantaneaTaller s){enEdt();captura=s;renderizados++;}
        public void estado(boolean a,String t,boolean v){enEdt();habilitada=a;vigente=v;}
        public void mensaje(String t){enEdt();mensajes.add(t);}
        public void diagnostico(String t){enEdt();diagnosticos.add(t);}
        public boolean confirmar(String t,String d){enEdt();dialogos++;duranteDialogo.run();return acepta;}
        public void cerrado(){enEdt();cerrada=true;}
    }
    static class Control {
        Puerto puerto=new Puerto(); Vista vista=new Vista();
        Deque<Runnable> trabajos=new ArrayDeque<>(),entregas=new ArrayDeque<>();
        PresentadorTaller p=new PresentadorTaller(puerto,vista,"BASE",trabajos::add,entregas::add,()->{});
        void pedir() throws Exception {edt(p::refrescar);}
        void trabajo(){trabajos.remove().run();}
        void entrega() throws Exception {edt(entregas.remove());}
        void leer() throws Exception{pedir();trabajo();entrega();}
        void drenar() throws Exception {int limite=20;while(!trabajos.isEmpty()||!entregas.isEmpty()){assertTrue(limite-->0);if(!trabajos.isEmpty())trabajo();if(!entregas.isEmpty())entrega();}}
    }
    @Test void capturaContadoresYCausasCompletas_T01_T02() throws Exception {
        var c=new Control();c.leer();assertSame(c.puerto.captura,c.vista.captura);assertTrue(c.vista.habilitada);
        var contadores=VentanaPrincipal.contar(c.vista.captura);assertEquals(5,contadores.size());assertEquals(1L,contadores.get(EstadoOrden.PENDIENTE));for(var e:EstadoOrden.values())if(e!=EstadoOrden.PENDIENTE)assertEquals(0L,contadores.get(e));
        assertEquals(List.of("Sin técnico","Sin puesto"),c.vista.captura.planificacion().causas().get("Z"));assertEquals(0,c.puerto.mutaciones);
    }
    @Test void cancelarDialogoYEnviarObjetoExactoUnaVez_T03_T06() throws Exception {
        var c=new Control();c.leer();var exacta=c.vista.captura.planificacion().propuesta().orElseThrow();
        c.vista.acepta=false;edt(c.p::confirmarInicio);assertEquals(0,c.trabajos.size());
        c.vista.acepta=true;c.vista.duranteDialogo=()->c.p.confirmarInicio();
        edt(()->{c.p.confirmarInicio();c.p.confirmarInicio();c.p.registrarOrden("B","x",Set.of());});
        assertEquals(1,c.trabajos.size());c.trabajo();c.entrega();assertSame(exacta,c.puerto.recibida);assertEquals(1,c.puerto.mutaciones);assertFalse(c.vista.habilitada);
        c.drenar();assertTrue(c.vista.habilitada);
    }
    @Test void agrupaRafagaDeRefrescos_T06() throws Exception {
        var c=new Control();edt(()->{for(int i=0;i<100;i++)c.p.refrescar();});assertEquals(1,c.trabajos.size());c.drenar();assertEquals(2,c.puerto.lecturas);assertTrue(c.trabajos.isEmpty());
    }
    @ParameterizedTest @EnumSource(ResultadoOperacion.Estado.class)
    void resultadosConTecnicoNuloSinReintentos_T08_T09(ResultadoOperacion.Estado estado) throws Exception {
        var c=new Control();c.leer();c.puerto.resultado=estado;edt(()->c.p.registrarOrden("B","x",Set.of()));c.drenar();
        assertEquals(1,c.puerto.mutaciones);assertTrue(c.vista.mensajes.stream().anyMatch(s->s.contains("OP")));assertTrue(c.vista.diagnosticos.stream().anyMatch(s->s.contains("Motivo íntegro")));
        if(estado==PENDIENTE_VERIFICACION){assertFalse(c.vista.habilitada);assertFalse(c.vista.vigente);assertEquals(1,c.puerto.lecturas);edt(()->{c.p.refrescar();c.p.confirmarInicio();});assertTrue(c.trabajos.isEmpty());}
    }
    @Test void confirmadaConAvisoTecnicoBloqueado_T08_T09() throws Exception {
        var c=new Control();c.leer();c.puerto.tecnico=new ResultadoOperacion.DiagnosticoTecnico("CIERRE","POST_COMMIT","Conexión dudosa",ContextoTaller.Ciclo.BLOQUEADO,false,"X");
        edt(()->c.p.registrarOrden("B","x",Set.of()));c.drenar();assertFalse(c.vista.habilitada);assertEquals(1,c.puerto.lecturas);assertTrue(c.vista.mensajes.stream().anyMatch(t->t.contains("Operación conservada")));assertTrue(c.vista.diagnosticos.stream().anyMatch(t->t.contains("POST_COMMIT")));
    }
    @Test void commitConocidoNoSeNiegaPorRefrescoFallido_T08() throws Exception {
        var c=new Control();c.leer();edt(()->c.p.registrarOrden("B","x",Set.of()));c.trabajo();c.entrega();c.puerto.fallaLectura=true;c.drenar();
        assertEquals(1,c.puerto.mutaciones);assertFalse(c.vista.habilitada);assertSame(c.puerto.captura,c.vista.captura);assertTrue(c.vista.mensajes.stream().anyMatch(t->t.contains("Operación conservada OP; no se pudo actualizar")));
        c.puerto.fallaLectura=false;c.leer();assertTrue(c.vista.habilitada);assertEquals(1,c.puerto.mutaciones);
    }
    @Test void excepcionSinResultadoNoReintenta_T09() throws Exception {
        var c=new Control();c.leer();c.puerto.fallaMutacion=true;edt(()->c.p.registrarOrden("B","x",Set.of()));c.drenar();assertEquals(1,c.puerto.mutaciones);assertFalse(c.vista.habilitada);assertEquals(1,c.puerto.lecturas);
    }
    @Test void capturaBloqueadaNoRehabilita_T09() throws Exception {
        var c=new Control();c.puerto.ciclo=ContextoTaller.Ciclo.BLOQUEADO;c.leer();assertNull(c.vista.captura);assertFalse(c.vista.habilitada);edt(c.p::refrescar);assertTrue(c.trabajos.isEmpty());
    }
    @Test void descartaRevisionesCruzadasYRecuperaConNuevaLectura_T07() throws Exception {
        var c=new Control();c.leer();var anterior=c.vista.captura;c.puerto.captura=captura(2,0);c.pedir();c.trabajo();c.entrega();assertSame(anterior,c.vista.captura);assertFalse(c.vista.vigente);assertEquals(1,c.trabajos.size());
        c.puerto.captura=captura(2,2);c.drenar();assertEquals(2,c.vista.captura.revision());assertTrue(c.vista.vigente);
    }
    @Test void entregaViejaMismaBaseNoActualizaOtraGeneracion_T07() throws Exception {
        var vieja=new Control();vieja.pedir();vieja.trabajo();edt(vieja.p::cerrar);
        var nueva=new Control();nueva.puerto.captura=captura(3,3);nueva.leer();vieja.entrega();vieja.drenar();assertEquals(0,vieja.vista.renderizados);assertEquals(3,nueva.vista.captura.revision());
    }
    @Test void lecturaViejaTrasNuevaNoRetrocedeNiARevisionIgual_T07() throws Exception {
        var c=new Control();c.pedir();c.trabajo();Runnable vieja=c.entregas.peek();c.entrega();c.pedir();c.trabajo();c.entrega();int antes=c.vista.renderizados;edt(vieja);assertEquals(antes,c.vista.renderizados);
    }
    @Test void dialogoNoPuedeEnviarTrasCerrarVentana_T07() throws Exception {
        var c=new Control();c.leer();c.vista.duranteDialogo=c.p::cerrar;edt(c.p::confirmarInicio);c.drenar();assertEquals(0,c.puerto.mutaciones);assertTrue(c.vista.cerrada);
    }
    @Test void cierreDrenaMutacionConservaResultadoYPermiteRepetirSiFalla_T11_T08() throws Exception {
        var c=new Control();c.leer();edt(()->{c.p.registrarOrden("B","x",Set.of());c.p.cerrar();c.p.refrescar();});assertEquals(2,c.trabajos.size());
        c.trabajo();assertEquals(1,c.puerto.mutaciones);assertEquals(0,c.puerto.cierres);c.entrega();assertTrue(c.vista.mensajes.stream().anyMatch(s->s.contains("Operación conservada")));
        c.puerto.fallaCierre=true;c.drenar();assertFalse(c.vista.cerrada);assertFalse(c.vista.habilitada);c.puerto.fallaCierre=false;c.puerto.advertencia=true;edt(c.p::cerrar);c.drenar();assertTrue(c.vista.cerrada);assertTrue(c.vista.mensajes.stream().anyMatch(s->s.contains("Marca limpia")));
    }
    @Test void cierreRetiraLecturaNoIniciada_T11() throws Exception {
        var c=new Control();c.pedir();edt(c.p::cerrar);c.drenar();assertEquals(0,c.puerto.lecturas);assertEquals(1,c.puerto.cierres);edt(c.p::cerrar);assertTrue(c.trabajos.isEmpty());
    }
    @Test void edtLibreMientrasPuertoEsperaLatch_T05() throws Exception {
        var puerto=new Puerto();var vista=new Vista();var entro=new CountDownLatch(1);var liberar=new CountDownLatch(1);var recibido=new CountDownLatch(1);
        puerto.alConsultar=()->{entro.countDown();try{assertTrue(liberar.await(5,TimeUnit.SECONDS));}catch(InterruptedException e){throw new AssertionError(e);}};
        var ejecutor=Executors.newSingleThreadExecutor();
        var p=new PresentadorTaller(puerto,vista,"BASE",ejecutor,r->SwingUtilities.invokeLater(()->{r.run();recibido.countDown();}),()->{});
        try{edt(p::refrescar);assertTrue(entro.await(5,TimeUnit.SECONDS));var ping=new CountDownLatch(1);SwingUtilities.invokeLater(ping::countDown);assertTrue(ping.await(5,TimeUnit.SECONDS));assertEquals(1,liberar.getCount());liberar.countDown();assertTrue(recibido.await(5,TimeUnit.SECONDS));edt(()->assertTrue(vista.habilitada));}
        finally{liberar.countDown();ejecutor.shutdown();assertTrue(ejecutor.awaitTermination(5,TimeUnit.SECONDS));}
    }
    @ParameterizedTest @EnumSource(ResultadoOperacion.Estado.class)
    void todosResultadosConDetalleTecnicoExistente_T08(ResultadoOperacion.Estado estado) throws Exception {
        var c=new Control();c.leer();c.puerto.resultado=estado;c.puerto.tecnico=new ResultadoOperacion.DiagnosticoTecnico("CODIGO","FASE","Causa completa",ContextoTaller.Ciclo.HABILITADO,true,"S1");
        edt(()->c.p.registrarOrden("B","x",Set.of()));c.drenar();assertEquals(1,c.puerto.mutaciones);assertTrue(c.vista.diagnosticos.stream().anyMatch(t->t.contains("Causa completa")&&t.contains("S1")));
    }
    @Test void transicionesGuiSoloValidasConIdConfirmado_T04() throws Exception {
        var c=new Control();var seleccion=propuesta().recursos();
        var ordenes=List.of(Orden.pendiente("A","Pendiente",1,List.of()),new Orden("B","En curso",2,Set.of(),EstadoOrden.EN_CURSO),new Orden("C","Final",3,Set.of(),EstadoOrden.FINALIZADA));
        var recursos=List.of(new Recurso(seleccion.tecnico(),null,true),new Recurso(seleccion.puesto(),null,true));
        c.puerto.captura=new InstantaneaTaller(1,new InstantaneaDominio("BASE",ordenes,recursos,Map.of("B",seleccion)),Set.of(),List.of(),new Planificador.Resultado(Optional.empty(),Map.of()));c.leer();
        edt(()->{c.p.transicionar("C",EstadoOrden.FALLIDA);c.p.transicionar("A",EstadoOrden.FINALIZADA);c.p.transicionar("B",EstadoOrden.CANCELADA);c.p.habilitar(seleccion.tecnico(),false);});assertEquals(0,c.trabajos.size());
        c.vista.acepta=false;edt(()->c.p.transicionar("A",EstadoOrden.CANCELADA));assertEquals(0,c.trabajos.size());
        c.vista.acepta=true;edt(()->c.p.transicionar("A",EstadoOrden.CANCELADA));c.drenar();assertEquals(1,c.puerto.mutaciones);
        edt(()->c.p.transicionar("B",EstadoOrden.FINALIZADA));c.drenar();assertEquals(2,c.puerto.mutaciones);
        edt(()->c.p.transicionar("B",EstadoOrden.FALLIDA));c.drenar();assertEquals(3,c.puerto.mutaciones);
    }
    @Test void revisionesInvalidasNoProducenBucleAutomatico_T07() throws Exception {
        var c=new Control();c.leer();c.puerto.captura=captura(0,0);c.pedir();c.drenar();assertEquals(3,c.puerto.lecturas);assertFalse(c.vista.vigente);assertTrue(c.trabajos.isEmpty());
    }
}
