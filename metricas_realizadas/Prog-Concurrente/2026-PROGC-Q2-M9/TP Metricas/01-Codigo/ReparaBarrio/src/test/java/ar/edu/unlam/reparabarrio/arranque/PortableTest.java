package ar.edu.unlam.reparabarrio.arranque;

import ar.edu.unlam.reparabarrio.simulacion.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(45)
class PortableTest {
    @TempDir Path temporal;
    private ContextoEjecucion portable() throws Exception {
        return new ContextoEjecucion(temporal.toRealPath(),ContextoEjecucion.Modo.PORTABLE);
    }
    @Test void contratoExplicitoSinAlternativasSilenciosas() throws Exception {
        String ruta=temporal.toString();
        for (String[] args : new String[][]{{"--portable"},{"--raiz",ruta,"--portable"},
                {"--raiz",ruta,"--academico-nuevo","--otro"},
                {"--raiz",ruta,"--academico-nuevo","--portable","--portable"}})
            assertThrows(IllegalArgumentException.class,()->ReparaBarrioApp.procesarArgumentos(args));
        var normal=ReparaBarrioApp.procesarArgumentos(new String[]{"--raiz",ruta,"--academico-nuevo"});
        assertEquals(ContextoEjecucion.Modo.DESARROLLO,normal.modo());
        assertThrows(IllegalArgumentException.class,()->FabricaContextos.validarRaiz(normal));
        var portable=ReparaBarrioApp.procesarArgumentos(new String[]{"--raiz",ruta,"--academico-nuevo","--portable"});
        assertEquals(temporal.toRealPath(),FabricaContextos.validarRaiz(portable));
        assertFalse(Files.exists(temporal.resolve("pom.xml")));
        assertFalse(Files.exists(temporal.resolve("AGENTS.md")));
    }
    @Test void consentimientoTraversalSesionesYGuardiaSeConservan() throws Exception {
        var e=portable();
        assertThrows(IllegalArgumentException.class,()->ArranqueAcademico.crear(e,false));
        assertThrows(IllegalArgumentException.class,()->FabricaContextos.crearSimulacion(e,false));
        assertFalse(Files.exists(temporal.resolve("target")));
        for(String nombre:List.of("../fuera","h5-../fuera","h5-base;X","h5-"))
            assertThrows(IllegalArgumentException.class,()->FabricaContextos.crearAcademico(e,nombre,true));
        for(Path ruta:List.of(Path.of("."),temporal.resolve("base;X")))
            assertThrows(IllegalArgumentException.class,()->FabricaContextos.validarRaiz(new ContextoEjecucion(ruta,e.modo())));
        var a=ArranqueAcademico.crear(e,true);var b=ArranqueAcademico.crear(e,true);
        try(var ca=a.contexto();var cb=b.contexto()) {
            assertNotEquals(ca.identidad(),cb.identidad());
            assertThrows(java.io.IOException.class,()->GuardiaInstancia.adquirir(a.directorio()));
            assertThrows(FileAlreadyExistsException.class,()->FabricaContextos.crearAcademico(e,a.directorio().getFileName().toString(),true));
        }
    }
    @Test void sesionAcademicaPortableQuedaHabilitada() throws Exception {
        var sesion=ArranqueAcademico.crear(portable(),true);
        try(var contexto=sesion.contexto()) {
            assertEquals(ar.edu.unlam.reparabarrio.aplicacion.ContextoTaller.Ciclo.HABILITADO,contexto.ciclo(),()->String.valueOf(contexto.informe()));
        }
    }
    @Test void presentadorTransportaContextoPortableHastaMotorYFabrica() throws Exception {
        var finalizado=new java.util.concurrent.CompletableFuture<MotorSimulacion.Resultado>();
        var cerrado=new java.util.concurrent.CompletableFuture<Boolean>();
        var vista=new ar.edu.unlam.reparabarrio.interfaz.VistaSimulacion() {
            public void estado(boolean iniciar,String texto) { }
            public void diagnostico(String texto) { finalizado.completeExceptionally(new AssertionError(texto)); }
            public void mostrar(MotorSimulacion.Progreso p,ar.edu.unlam.reparabarrio.metricas.EstadisticasEscenario e,MotorSimulacion.Resultado r) {
                if(r!=null)finalizado.complete(r);
            }
        };
        var presentador=new ar.edu.unlam.reparabarrio.interfaz.PresentadorSimulacion(portable(),vista);
        var c=new ConfiguracionEscenario(9,0,1,ConfiguracionEscenario.inventario(1,1,0,0,0,true),
                List.of(1,0,0,0,0,0,0,0),ConfiguracionEscenario.Llegadas.PERIODICAS,0,0,0,0,0);
        try {
            javax.swing.SwingUtilities.invokeAndWait(()->presentador.iniciar(c));
            assertEquals(MotorSimulacion.Motivo.COMPLETADA,finalizado.get(20,TimeUnit.SECONDS).motivo());
        } finally {
            javax.swing.SwingUtilities.invokeAndWait(()->presentador.cerrar(cerrado::complete));
            assertTrue(cerrado.get(20,TimeUnit.SECONDS));
        }
    }
    @Test void simulacionPortableLlegaAFabricaSinMarcadores() throws Exception {
        var c=new ConfiguracionEscenario(9,2,1,ConfiguracionEscenario.inventario(1,1,0,0,0,true),
                List.of(1,0,0,0,0,0,0,0),ConfiguracionEscenario.Llegadas.PERIODICAS,0,0,0,0,0);
        try(var motor=MotorSimulacion.crear(portable(),c)) {
            var r=motor.iniciar().get(20,TimeUnit.SECONDS);
            assertEquals(MotorSimulacion.Motivo.COMPLETADA,r.motivo());
            assertEquals(2,r.registradas());
        }
        try(var dirs=Files.list(temporal.resolve("target"))) {
            assertTrue(dirs.anyMatch(p->p.getFileName().toString().startsWith("h6-")));
        }
    }
    @Test void targetArchivoNoSeSobrescribe() throws Exception {
        Path target=Files.writeString(temporal.resolve("target"),"conservar");
        assertThrows(java.io.IOException.class,()->FabricaContextos.crearSimulacion(portable(),true));
        assertEquals("conservar",Files.readString(target));
    }
    @Test void targetEnlazadoSeRechazaSiElEntornoPermiteEnlaces() throws Exception {
        Path destino=Files.createDirectory(temporal.resolve("destino"));
        try { Files.createSymbolicLink(temporal.resolve("target"),destino); }
        catch(java.io.IOException | UnsupportedOperationException ex) { Assumptions.abort("Enlaces no disponibles: "+ex); }
        assertThrows(IllegalArgumentException.class,()->FabricaContextos.crearSimulacion(portable(),true));
        try(var contenido=Files.list(destino)) { assertEquals(0,contenido.count()); }
    }
}
