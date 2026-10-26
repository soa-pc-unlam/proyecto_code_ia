package ar.edu.unlam.reparabarrio.aplicacion;

import ar.edu.unlam.reparabarrio.aplicacion.puertos.*;
import ar.edu.unlam.reparabarrio.persistencia.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(90)
class CicloContextoTest {
    @AfterEach void cerrar() throws Exception { TestContextos.cerrarTodos(); }
    @Test void h4T05_propietarioUnicoAliasYCierreReferenciasAntiguas() throws Exception {
        Path base = TestContextos.nuevaRaiz().resolve("taller"); var cuenta = new AtomicInteger();
        var contexto = TestContextos.abrir(base, fabrica -> () -> { cuenta.incrementAndGet(); return fabrica.abrir(); }, UnaryOperator.identity(), new CoordinadorTaller(), null, TestContextos.solicitud(base));
        var pool = Executors.newFixedThreadPool(4);
        try {
            var barrera = new CyclicBarrier(4); var futures = new ArrayList<Future<ContextoTaller>>();
            for (int i = 0; i < 4; i++) futures.add(pool.submit(() -> { barrera.await(10, TimeUnit.SECONDS); return TestContextos.abrir(base.getParent().resolve("alias/../taller")); }));
            for (var futuro : futures) assertSame(contexto, futuro.get(30, TimeUnit.SECONDS));
            if (System.getProperty("os.name").startsWith("Windows")) {
                Path alias = Path.of(base.getParent().toString().toUpperCase(Locale.ROOT));
                assertTrue(Files.isSameFile(base.getParent(), alias));
                assertSame(contexto, TestContextos.abrir(alias.resolve("taller")));
            }
            var fachada = contexto.servicio(); var escritura = pool.submit(() -> fachada.registrarOrden("A", "Carrera cierre", List.of()));
            var cierre = pool.submit(() -> { contexto.close(); return null; });
            var r = escritura.get(30, TimeUnit.SECONDS); cierre.get(30, TimeUnit.SECONDS);
            assertTrue(Set.of(ResultadoOperacion.Estado.CONFIRMADA, ResultadoOperacion.Estado.ERROR_TECNICO).contains(r.estado()));
            contexto.close(); int antes = cuenta.get();
            assertEquals("REFERENCIA_CERRADA", fachada.consultar().tecnico().codigo()); assertNull(fachada.consultar().valor());
            assertEquals(ResultadoOperacion.Estado.ERROR_TECNICO, fachada.registrarOrden("B", "Vieja", List.of()).estado()); assertEquals(antes, cuenta.get());
            var nuevo = TestContextos.abrir(base); assertNotSame(contexto, nuevo); assertEquals(contexto.identidad(), nuevo.identidad());
            assertEquals(ResultadoOperacion.Estado.CONFIRMADA, nuevo.servicio().registrarOrden("C", "Nueva", List.of()).estado());
        } finally { pool.shutdownNow(); assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS)); }
    }
    @Test void h4T05_h4T18_fabricaPublicaProtegidaEnRaizDescartable() throws Exception {
        Path raiz = TestContextos.nuevaRaiz(); Files.writeString(raiz.resolve("pom.xml"), "fixture"); Files.writeString(raiz.resolve("AGENTS.md"), "fixture");
        Path directorio = raiz.resolve("datos/operativo"); Files.createDirectories(directorio); Path base = directorio.resolve("taller");
        var a = new AutorizacionBase(UUID.randomUUID().toString(), base, "AUSENTE", 0, 2, null, true, false, "Creación explícita exclusivamente bajo target/h4");
        var solicitud = new InicializadorBase.Solicitud(InicializadorBase.Modo.CREAR_NUEVA, a);
        try (var c = ar.edu.unlam.reparabarrio.arranque.FabricaContextos.abrir(raiz, solicitud)) {
            assertSame(c, ar.edu.unlam.reparabarrio.arranque.FabricaContextos.abrir(raiz.resolve("."), solicitud));
            assertSame(c.servicio(), PersistenciaH2.abrir(base));
            assertEquals(ResultadoOperacion.Estado.CONFIRMADA, c.servicio().registrarOrden("A", "Protegida", List.of()).estado());
        }
        assertThrows(Exception.class, () -> PersistenciaH2.abrir(base));
    }
}
