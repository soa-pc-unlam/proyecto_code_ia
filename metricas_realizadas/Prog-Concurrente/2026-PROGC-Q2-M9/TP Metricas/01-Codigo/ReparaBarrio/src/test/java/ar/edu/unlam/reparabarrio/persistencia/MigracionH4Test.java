package ar.edu.unlam.reparabarrio.persistencia;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.arranque.GuardiaInstancia;
import ar.edu.unlam.reparabarrio.dominio.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.stream.*;
import static ar.edu.unlam.reparabarrio.persistencia.H4Fixture.ok;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(180)
class MigracionH4Test {
    @AfterEach void cerrar() throws Exception { TestContextos.cerrarTodos(); }
    static Path v1() throws Exception {
        Path base = TestContextos.nuevaRaiz().resolve("taller"); TestContextos.validar(base);
        try (var guardia = GuardiaInstancia.adquirir(base.getParent());
             var c = DriverManager.getConnection(InicializadorBase.url(base), "sa", "");
             var in = MigracionH4Test.class.getResourceAsStream("/persistencia/esquema-v1.sql"); var st = c.createStatement()) {
            assertNotNull(in);
            for (String sql : new String(in.readAllBytes(), StandardCharsets.UTF_8).split(";")) if (!sql.isBlank()) st.execute(sql);
            c.setAutoCommit(false); st.executeUpdate("INSERT INTO rb_version VALUES(1)"); st.executeUpdate("INSERT INTO contador VALUES(1,0,0)");
            for (var tipo : TipoHerramienta.values()) st.executeUpdate("INSERT INTO catalogo VALUES('" + tipo.name() + "')");
            var r = new RepositorioTallerJdbc(c);
            var t = new Recurso(new Recurso.Clave(Recurso.Categoria.TECNICO, "T"), null, true);
            var p = new Recurso(new Recurso.Clave(Recurso.Categoria.PUESTO, "P"), null, true);
            var m = new Recurso(new Recurso.Clave(Recurso.Categoria.HERRAMIENTA, "M"), TipoHerramienta.MULTIMETRO, true);
            var d = new Recurso(new Recurso.Clave(Recurso.Categoria.HERRAMIENTA, "D"), TipoHerramienta.TALADRO, true);
            var s = new Recurso(new Recurso.Clave(Recurso.Categoria.HERRAMIENTA, "S"), TipoHerramienta.SOLDADOR, false);
            for (var recurso : List.of(t, p, m, d, s)) { r.registrar(recurso); r.evento(UUID.randomUUID().toString(), null, "REGISTRO_RECURSO", Instant.now(), Set.of(recurso.clave())); }
            for (String id : List.of("A", "B")) {
                r.registrar(Orden.pendiente(id, "v1 " + id, r.siguienteSecuencia(), id.equals("A") ? Set.of(TipoHerramienta.MULTIMETRO, TipoHerramienta.TALADRO) : Set.of()));
                r.evento(UUID.randomUUID().toString(), id, "REGISTRO_ORDEN", Instant.now(), Set.of());
            }
            var seleccion = new SeleccionRecursos(t.clave(), p.clave(), Map.of(m.tipo(), m.clave(), d.tipo(), d.clave()));
            r.estado("A", EstadoOrden.EN_CURSO); r.asignar("A", seleccion); r.evento(UUID.randomUUID().toString(), "A", "INICIO", Instant.now(), seleccion.unidades());
            c.commit();
        }
        return base;
    }
    static AutorizacionBase permiso(Path base, boolean reanudar) throws Exception {
        String id = UUID.randomUUID().toString();
        return new AutorizacionBase(id, base, InicializadorBase.huella(base), 1, 2,
                base.getParent().resolve("datos/preservacion/h4").resolve(id), true, reanudar, "Autorizo migrar sólo fixture v1 H4 y auditar copia");
    }
    private static String contenido(Path base) throws Exception {
        return OraculoH4.filas(base);
    }
    @Test void h4T13_adopcionV1PreservaTodoSinReseedNiIdsRetroactivos() throws Exception {
        Path base = v1(); String funcional = contenido(base); var a = permiso(base, true);
        try (var f = new H4Fixture(base, new InicializadorBase.Solicitud(InicializadorBase.Modo.MIGRAR_V1, a))) {
            assertEquals(funcional, contenido(base)); assertEquals(a.huella(), InicializadorBase.huella(a.preservacion().resolve("taller")));
            assertEquals(0, f.numero("SELECT COUNT(*) FROM rb_intenciones"));
            assertEquals(8, f.numero("SELECT frontera_v1 FROM rb_contexto"));
            assertEquals(ContextoTaller.Ciclo.BLOQUEADO, f.contexto.informe().ciclo());
            assertEquals("ADOPCION_PENDIENTE", f.contexto.informe().hallazgos().getFirst());
            assertEquals(ResultadoOperacion.Estado.ERROR_TECNICO, f.servicio.consultar().estado());
            var informe = f.contexto.informe();
            var aceptada = f.contexto.recuperacion().aceptarAdopcion(informe.referencia(), informe.revision(), "Acepto informe de fixture y su procedencia desconocida");
            assertEquals(ContextoTaller.Ciclo.HABILITADO, aceptada.ciclo(), aceptada::toString);
            assertNull(f.vista().revisiones().get("A").cierreId()); assertEquals(2, f.numero("SELECT COUNT(*) FROM herramientas_activas"));
            ok(f.servicio.finalizar("A")); f.orden("C"); assertEquals(3, f.numero("SELECT secuencia FROM ordenes WHERE id='C'"));
            assertNotNull(f.vista().revisiones().get("A").cierreId());
        }
    }
    static Stream<Arguments> fronteras() throws Exception {
        var args = new ArrayList<Arguments>();
        for (boolean migrar : List.of(false, true)) {
            int cantidad = migrar ? InicializadorBase.sql("migracion-v1-v2.sql").size() + 2 : InicializadorBase.sql("esquema.sql").size() + InicializadorBase.sql("migracion-v1-v2.sql").size();
            for (String fase : List.of("TABLA_MARCADOR", "MARCADOR", "ANTES_PUBLICAR", "PUBLICADO")) args.add(Arguments.of(migrar, fase));
            for (int i = 0; i < cantidad; i++) { args.add(Arguments.of(migrar, "DDL_" + i)); args.add(Arguments.of(migrar, "PASO_" + i)); }
        }
        return args.stream();
    }
    @ParameterizedTest @MethodSource("fronteras")
    void h4T13_interrupcionRealCadaPasoInicializacionMigracion(boolean migrar, String frontera) throws Exception {
        Path base = migrar ? v1() : TestContextos.nuevaRaiz().resolve("taller");
        String funcional = migrar ? contenido(base) : null;
        var a = migrar ? permiso(base, true) : new AutorizacionBase(UUID.randomUUID().toString(), base, "AUSENTE", 0, 2, null, true, true, "Creación/reanudación específica fixture nuevo H4");
        try (var hijo = new ProcesosH4(base.getParent(), migrar ? "migrar" : "inicializar", frontera, a.id(), a.huella(), migrar ? a.preservacion().toString() : "")) {
            hijo.esperar("FRONTERA=" + frontera); hijo.terminar();
        }
        String uuid = null;
        // Oráculo independiente autorizado del fixture, después de confirmar muerte del escritor.
        try (var c = DriverManager.getConnection(InicializadorBase.url(base) + ";IFEXISTS=TRUE", "sa", ""); var st = c.createStatement(); var rs = st.executeQuery("SELECT base_id FROM rb_protocolo")) { if (rs.next()) uuid = rs.getString(1); }
        try (var f = new H4Fixture(base, new InicializadorBase.Solicitud(InicializadorBase.Modo.REANUDAR, a))) {
            if (uuid != null) assertEquals(uuid, f.contexto.identidad());
            assertEquals(2, f.numero("SELECT version FROM rb_version")); assertEquals(1, f.numero("SELECT COUNT(*) FROM rb_protocolo WHERE completo=TRUE"));
            if (migrar) { assertEquals(funcional, contenido(base)); assertEquals(a.huella(), InicializadorBase.huella(a.preservacion().resolve("taller"))); }
            else { assertEquals(0, f.numero("SELECT COUNT(*) FROM ordenes")); assertEquals(3, f.numero("SELECT COUNT(*) FROM catalogo")); }
            assertEquals(migrar ? ContextoTaller.Ciclo.BLOQUEADO : ContextoTaller.Ciclo.HABILITADO, f.contexto.ciclo(), () -> f.contexto.informe().toString());
        }
    }
    @Test void h4T17_sinAutorizacionObsoletaODenegadaNoAbreNiEscribeOriginal() throws Exception {
        Path base = v1(); String bytes = InicializadorBase.huella(base); var a = permiso(base, false);
        assertThrows(NullPointerException.class, () -> new InicializadorBase.Solicitud(InicializadorBase.Modo.MIGRAR_V1, null));
        try (var g = GuardiaInstancia.adquirir(base.getParent())) {
            var falsa = new AutorizacionBase(a.id(), a.archivoBase(), "OBSOLETA", 1, 2, a.preservacion(), true, false, "fixture");
            assertThrows(IllegalArgumentException.class, () -> InicializadorBase.preparar(g, new InicializadorBase.Solicitud(InicializadorBase.Modo.MIGRAR_V1, falsa), s -> { }));
        }
        assertEquals(bytes, InicializadorBase.huella(base)); assertFalse(Files.exists(a.preservacion()));
    }
    @Test void h4T17_copiaFallidaAbortaSinModificarOriginal() throws Exception {
        Path base = v1(); var a = permiso(base, false); Files.createDirectories(a.preservacion().getParent());
        Files.writeString(a.preservacion(), "obstáculo de test"); String antes = InicializadorBase.huella(base);
        try (var g = GuardiaInstancia.adquirir(base.getParent())) {
            assertThrows(Exception.class, () -> InicializadorBase.preparar(g, new InicializadorBase.Solicitud(InicializadorBase.Modo.MIGRAR_V1, a), s -> { }));
        }
        assertEquals(antes, InicializadorBase.huella(base));
    }
    @ParameterizedTest @ValueSource(strings={"PERMISO", "MARCADOR", "COPIA", "CONTENIDO", "UUID", "PASO"})
    void h4T17_reanudacionNoAcreditadaBloqueaSinReparar(String variante) throws Exception {
        Path base = v1(); var a = permiso(base, true);
        final var inicial = a;
        var alcanzada = new java.util.concurrent.atomic.AtomicBoolean();
        try (var g = GuardiaInstancia.adquirir(base.getParent())) {
            var error = assertThrows(SQLException.class, () -> InicializadorBase.preparar(g, new InicializadorBase.Solicitud(InicializadorBase.Modo.MIGRAR_V1, inicial), fase -> { if (fase.equals("MARCADOR")) { alcanzada.set(true); throw new SQLException("Interrupción controlada"); } }));
            assertTrue(alcanzada.get(), () -> "No alcanzó marcador autorizado: " + error);
        }
        if (variante.equals("PERMISO")) a = new AutorizacionBase(a.id(), base, a.huella(), 1, 2, a.preservacion(), true, false, "No autorizo reanudar");
        else if (variante.equals("COPIA")) Files.writeString(a.preservacion().resolve("taller.mv.db"), "Copia alterada de test");
        else try (var c = DriverManager.getConnection(InicializadorBase.url(base) + ";IFEXISTS=TRUE", "sa", ""); var st = c.createStatement()) {
            st.executeUpdate(switch (variante) {
                case "MARCADOR" -> "UPDATE rb_protocolo SET autorizacion='AJENA'";
                case "CONTENIDO" -> "UPDATE ordenes SET descripcion='ALTERADA' WHERE id='A'";
                case "UUID" -> "UPDATE rb_protocolo SET base_id='INVALIDO'";
                default -> "UPDATE rb_protocolo SET paso=999";
            });
        }
        String antes = contenido(base); final var permiso = a;
        try (var g = GuardiaInstancia.adquirir(base.getParent())) {
            assertThrows(Exception.class, () -> InicializadorBase.preparar(g, new InicializadorBase.Solicitud(InicializadorBase.Modo.REANUDAR, permiso), s -> { }));
        }
        assertEquals(antes, contenido(base));
    }
    @Test void h4T18_aceptacionObsoletaRechazadaYNoResuelveRevision() throws Exception {
        Path base = v1(); var a = permiso(base, true);
        try (var f = new H4Fixture(base, new InicializadorBase.Solicitud(InicializadorBase.Modo.MIGRAR_V1, a))) {
            var informe = f.contexto.informe();
            assertEquals(ContextoTaller.Ciclo.BLOQUEADO, f.contexto.recuperacion().aceptarAdopcion("OBSOLETO", informe.revision(), "acepto").ciclo());
            assertEquals(1, f.numero("SELECT COUNT(*) FROM rb_contexto WHERE adopcion_pendiente=TRUE"));
            informe = f.contexto.recuperacion().recuperar();
            assertEquals(ContextoTaller.Ciclo.HABILITADO, f.contexto.recuperacion().aceptarAdopcion(informe.referencia(), informe.revision(), "Acepto fixture").ciclo());
            assertNull(f.vista().revisiones().get("A").cierreId()); assertEquals(1, f.numero("SELECT COUNT(*) FROM asignaciones"));
        }
    }
    @Test void h4T17_abandonoTrasPreservacionNoAutorizaTransformacionSinMarcador() throws Exception {
        Path base = v1(); var a = permiso(base, true); String antes = InicializadorBase.huella(base);
        try (var hijo = new ProcesosH4(base.getParent(), "migrar", "PRESERVACION", a.id(), a.huella(), a.preservacion().toString())) {
            hijo.esperar("FRONTERA=PRESERVACION"); hijo.terminar();
        }
        assertEquals(antes, InicializadorBase.huella(base)); assertEquals(antes, InicializadorBase.huella(a.preservacion().resolve("taller")));
        try (var g = GuardiaInstancia.adquirir(base.getParent())) {
            assertThrows(Exception.class, () -> InicializadorBase.preparar(g, new InicializadorBase.Solicitud(InicializadorBase.Modo.REANUDAR, a), s -> { }));
        }
        assertEquals(1, version(base));
    }
    private static long version(Path base) throws Exception {
        try (var c = DriverManager.getConnection(InicializadorBase.url(base) + ";IFEXISTS=TRUE", "sa", ""); var st = c.createStatement(); var r = st.executeQuery("SELECT version FROM rb_version")) { r.next(); return r.getLong(1); }
    }
}
