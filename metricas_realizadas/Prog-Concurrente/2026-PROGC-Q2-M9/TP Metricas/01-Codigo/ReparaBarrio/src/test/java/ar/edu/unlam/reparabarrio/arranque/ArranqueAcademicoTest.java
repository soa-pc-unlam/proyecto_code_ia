package ar.edu.unlam.reparabarrio.arranque;

import ar.edu.unlam.reparabarrio.aplicacion.ContextoTaller;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(45)
class ArranqueAcademicoTest {
    private final Path raiz=Path.of("").toAbsolutePath();
    private String nombre(){return "h5-test-"+UUID.randomUUID();}
    @Test void exigeConsentimientoYNombreAcotadoAntesDeCrear_T10() throws Exception {
        String nuevo=nombre();assertThrows(IllegalArgumentException.class,()->FabricaContextos.crearAcademico(raiz,nuevo,false));assertFalse(Files.exists(raiz.resolve("target").resolve(nuevo)));
        for(String s:new String[]{"../fuera","h5-../fuera","operativo","h5-base;IFEXISTS=FALSE","h5-"})assertThrows(IllegalArgumentException.class,()->FabricaContextos.crearAcademico(raiz,s,true));
        assertThrows(IllegalArgumentException.class,()->FabricaContextos.crearAcademico(Path.of("."),nombre(),true));
    }
    @Test void rechazaRestosYArchivoConocidoSinLeerNiModificar_T10() throws Exception {
        Path target=raiz.resolve("target");Files.createDirectories(target);String n=nombre();Path d=Files.createDirectory(target.resolve(n));
        var bytes=new byte[]{1,2,3};Files.write(d.resolve("taller.mv.db"),bytes);Files.writeString(d.resolve("instancia.lock"),"BASE_CONOCIDA");
        assertThrows(FileAlreadyExistsException.class,()->FabricaContextos.crearAcademico(raiz,n,true));assertArrayEquals(bytes,Files.readAllBytes(d.resolve("taller.mv.db")));assertEquals("BASE_CONOCIDA",Files.readString(d.resolve("instancia.lock")));
    }
    @Test void rechazaEnlaceDeDirectorioNuevo_T10() throws Exception {
        Path target=raiz.resolve("target");Files.createDirectories(target);Path destino=Files.createDirectory(target.resolve(nombre()));String n=nombre();Path enlace=target.resolve(n);
        try{Files.createSymbolicLink(enlace,destino);}catch(java.io.IOException|UnsupportedOperationException e){Assumptions.assumeTrue(false,"Enlaces no disponibles: "+e);}
        assertThrows(FileAlreadyExistsException.class,()->FabricaContextos.crearAcademico(raiz,n,true));assertTrue(Files.isSymbolicLink(enlace));
    }
    @Test void basesDistintasIndependientesYGuardiaMismaRuta_T11() throws Exception {
        var a=ArranqueAcademico.crear(raiz,true);var b=ArranqueAcademico.crear(raiz,true);
        try(var ca=a.contexto();var cb=b.contexto()) {
            assertNotEquals(a.directorio(),b.directorio());assertNotSame(ca,cb);assertNotEquals(ca.identidad(),cb.identidad());
            assertThrows(java.io.IOException.class,()->GuardiaInstancia.adquirir(a.directorio()));assertThrows(java.io.IOException.class,()->GuardiaInstancia.adquirir(b.directorio()));
            assertThrows(FileAlreadyExistsException.class,()->FabricaContextos.crearAcademico(raiz,a.directorio().getFileName().toString(),true));
        }
    }
}
