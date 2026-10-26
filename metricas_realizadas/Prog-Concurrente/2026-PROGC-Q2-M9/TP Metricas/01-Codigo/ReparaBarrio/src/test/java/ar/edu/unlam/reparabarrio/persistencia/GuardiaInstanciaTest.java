package ar.edu.unlam.reparabarrio.persistencia;

import ar.edu.unlam.reparabarrio.aplicacion.TestContextos;
import ar.edu.unlam.reparabarrio.arranque.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(90)
class GuardiaInstanciaTest {
    @Test void h4T04_segundaJvmRechazadaAntesJdbcAliasYMuerteLiberaGuardia() throws Exception {
        Path raiz = TestContextos.nuevaRaiz();
        try (var a = new ProcesosH4(raiz, "guardia")) {
            a.esperar("FRONTERA=GUARDIA");
            String antes = InicializadorBase.huella(raiz.resolve("taller"));
            assertThrows(Exception.class, () -> GuardiaInstancia.adquirir(raiz.resolve(".")));
            try (var b = new ProcesosH4(raiz, "verificar")) {
                assertNotEquals(0, b.finalizar()); assertTrue(b.salida.stream().anyMatch(s -> s.contains("INSTANCIA_OCUPADA")), b.salida::toString);
            }
            assertEquals(antes, InicializadorBase.huella(raiz.resolve("taller"))); assertEquals("AUSENTE", antes);
            a.terminar();
        }
        try (var g = GuardiaInstancia.adquirir(raiz)) { assertTrue(g.vigente()); }
        assertTrue(Files.exists(raiz.resolve("instancia.lock")));
    }
    @Test void h4T04_cierreLimpioGuardiaPermiteOtraJvm() throws Exception {
        Path raiz = TestContextos.nuevaRaiz();
        try (var a = new ProcesosH4(raiz, "guardia")) { a.esperar("FRONTERA=GUARDIA"); a.liberar(); assertEquals(0, a.finalizar()); }
        try (var b = new ProcesosH4(raiz, "guardia")) { b.esperar("FRONTERA=GUARDIA"); b.liberar(); assertEquals(0, b.finalizar()); }
    }
    @Test void h4T05_guardiaIdempotenteYComposicionRechazaRutasAjenasSinJdbc() throws Exception {
        Path raiz = TestContextos.nuevaRaiz(); var g = GuardiaInstancia.adquirir(raiz);
        assertThrows(Exception.class, () -> GuardiaInstancia.adquirir(raiz));
        assertFalse(g.baseConocida()); g.recordarBaseConocida(); assertTrue(g.baseConocida()); g.close(); g.close();
        var solicitud = TestContextos.solicitud(raiz.resolve("taller"));
        try (var otra = GuardiaInstancia.adquirir(raiz)) {
            var error = assertThrows(IllegalStateException.class, () -> InicializadorBase.preparar(otra, solicitud, s -> { }));
            assertTrue(error.getMessage().contains("BASE_CONOCIDA_AUSENTE"), error::toString);
        }
        assertTrue(Files.exists(raiz.resolve("instancia.lock")));
        assertThrows(Exception.class, () -> TestContextos.validar(Path.of("datos/operativo/taller").toAbsolutePath()));
        assertThrows(Exception.class, () -> PersistenciaH2.abrir(raiz.resolve("taller")));
        assertThrows(Exception.class, () -> FabricaContextos.validarRaiz(raiz));
        assertThrows(Exception.class, () -> FabricaContextos.validarRaiz(Path.of(".")));
        assertEquals("AUSENTE", InicializadorBase.huella(raiz.resolve("taller")));
    }
}
