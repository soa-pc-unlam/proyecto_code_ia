package ar.edu.unlam.reparabarrio.persistencia;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.aplicacion.puertos.RepositorioRecuperacion.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import org.junit.jupiter.api.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Verificación suplementaria de SQL/JDBC en memoria. No acredita recuperación de
 * archivo, guardia, caídas, ni sustituye H4-T01..T18 ante la limitación MXC.
 */
class ProtocoloJdbcH4Test {
    private Connection c;
    private String base, sesion;
    private RepositorioRecuperacionJdbc rr;
    private RepositorioTallerJdbc r;
    @BeforeEach void preparar() throws Exception {
        c = DriverManager.getConnection("jdbc:h2:mem:" + UUID.randomUUID() + ";WRITE_DELAY=0", "sa", "");
        base = UUID.randomUUID().toString(); sesion = UUID.randomUUID().toString();
        for (String recurso : List.of("protocolo.sql", "esquema.sql", "migracion-v1-v2.sql"))
            for (String ddl : InicializadorBase.sql(recurso)) ejecutar(ddl);
        ejecutar("INSERT INTO rb_protocolo VALUES(1,2,0,2,'" + base + "','TEST','AUSENTE','','',16,TRUE)");
        ejecutar("INSERT INTO rb_version VALUES(2)"); ejecutar("INSERT INTO contador VALUES(1,0,0)");
        for (var tipo : TipoHerramienta.values()) ejecutar("INSERT INTO catalogo VALUES('" + tipo.name() + "')");
        ejecutar("INSERT INTO rb_contexto VALUES(1,'" + base + "',2,0,0,FALSE,NULL,NULL)");
        c.setAutoCommit(false); rr = new RepositorioRecuperacionJdbc(c); r = new RepositorioTallerJdbc(c);
        rr.abrirSesion(sesion); c.commit();
    }
    @AfterEach void cerrar() throws Exception { c.close(); }
    private void ejecutar(String sql) throws SQLException { try (var st = c.createStatement()) { st.execute(sql); } }
    private long numero(String sql) throws SQLException { try (var st = c.createStatement(); var rs = st.executeQuery(sql)) { assertTrue(rs.next()); return rs.getLong(1); } }
    private String intencion(String accion, String orden, Set<Recurso.Clave> recursos) throws Exception {
        String id = UUID.randomUUID().toString();
        rr.preparar(new Intento(id, base, sesion, "NEGOCIO", new DescriptorOperacion(accion, orden,
                accion.equals("REGISTRO_ORDEN") ? "x" : null, Set.of(), null, null, null, recursos), r.revision(), "PENDIENTE", null, null));
        c.commit(); return id;
    }
    @Test void sqlV2ConfigEfectivaYCorrespondenciaLiteralAtomica() throws Exception {
        assertTrue(c.getMetaData().getDatabaseProductVersion().startsWith("2.3.232"));
        var id = intencion("REGISTRO_ORDEN", "A", Set.of());
        r.registrar(Orden.pendiente("A", "x", r.siguienteSecuencia(), Set.of())); r.evento(id, "A", "REGISTRO_ORDEN", Instant.EPOCH, Set.of());
        rr.resolver(id, "CONFIRMADA", r.revision(), null); c.commit();
        var datos = rr.leer(); assertEquals(id, datos.intentos().getFirst().id()); assertEquals(1L, datos.intentos().getFirst().evento());
        assertTrue(new AuditorCoherencia().auditar(r.cargar(base), r.historial(), r.revision(), datos, true).isEmpty());
        assertEquals(1, numero("SELECT COUNT(*) FROM historial h JOIN rb_intenciones i ON h.secuencia=i.evento AND h.operacion_id=i.id"));
        assertThrows(SQLException.class, () -> rr.preparar(datos.intentos().getFirst())); c.rollback();
        var cerrarDisponible = new java.util.concurrent.atomic.AtomicBoolean();
        var cerrada = new java.util.concurrent.atomic.AtomicBoolean();
        var conexionDudosa = (Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Connection.class}, (proxy, metodo, args) -> {
            if (metodo.getName().equals("isClosed")) return cerrada.get();
            if (metodo.getName().equals("close")) {
                if (!cerrarDisponible.get()) throw new SQLException("Cierre de arranque no acreditado", "08006");
                cerrada.set(true); return null;
            }
            throw new UnsupportedOperationException(metodo.getName());
        });
        var fallo = assertThrows(ar.edu.unlam.reparabarrio.aplicacion.puertos.UnidadTrabajo.AperturaIncompleta.class,
                () -> new ConexionPrivada(conexionDudosa).close());
        assertFalse(fallo.residual().terminada()); cerrarDisponible.set(true); fallo.residual().close(); assertTrue(fallo.residual().terminada());
    }
    @Test void rollbackNegocioConservaIntencionYNoContadorParcial() throws Exception {
        var id = intencion("REGISTRO_ORDEN", "A", Set.of());
        r.registrar(Orden.pendiente("A", "x", r.siguienteSecuencia(), Set.of())); r.evento(id, "A", "REGISTRO_ORDEN", Instant.EPOCH, Set.of());
        rr.resolver(id, "CONFIRMADA", r.revision(), null); c.rollback();
        assertEquals(0, numero("SELECT COUNT(*) FROM ordenes")); assertEquals(0, numero("SELECT antiguedad+revision FROM contador"));
        assertEquals("PENDIENTE", rr.leer().intentos().getFirst().resolucion());
        assertTrue(new AuditorCoherencia().auditar(r.cargar(base), r.historial(), r.revision(), rr.leer(), true).isEmpty());
        rr.resolver(id, "NO_CONFIRMADA", null, "Ausencia auditada"); c.commit();
        assertEquals("NO_CONFIRMADA", rr.leer().intentos().getFirst().resolucion());
    }
    @Test void sqlMigracionReemplazaRestriccionVersionSinReemplazarCatalogo() throws Exception {
        c.rollback(); c.setAutoCommit(true);
        // Matriz estructural v1/v2 de H2 real, suplementaria a los ensayos de archivo.
        for (String tabla : List.of("rb_revisiones", "rb_intenciones", "rb_sesiones", "rb_contexto", "rb_protocolo", "rb_version")) ejecutar("DROP TABLE " + tabla);
        ejecutar("CREATE TABLE rb_version(version INTEGER PRIMARY KEY CHECK(version=1))"); ejecutar("INSERT INTO rb_version VALUES(1)");
        InicializadorBase.validarEstructura(c, 1);
        for (String ddl : InicializadorBase.sql("protocolo.sql")) ejecutar(ddl);
        for (String ddl : InicializadorBase.sql("migracion-v1-v2.sql")) ejecutar(ddl);
        ejecutar("DROP TABLE rb_version"); ejecutar("CREATE TABLE rb_version(version INTEGER PRIMARY KEY CHECK(version=2))"); ejecutar("INSERT INTO rb_version VALUES(2)");
        InicializadorBase.validarEstructura(c, 2); assertEquals(3, numero("SELECT COUNT(*) FROM catalogo"));
        assertThrows(SQLException.class, () -> ejecutar("UPDATE rb_version SET version=1"));
    }
    @Test void lecturaEstructuralDetectaFilasQueElMapeoNoIncluye() throws Exception {
        c.commit(); ejecutar("SET REFERENTIAL_INTEGRITY FALSE");
        ejecutar("INSERT INTO requisitos VALUES('HUERFANA','SOLDADOR')");
        assertFalse(rr.leer().hallazgos().isEmpty());
        c.rollback();
    }
    @Test void resolucionAusenteOYaResueltaIdentificaDmlYCantidadSinOcultarFallo() throws Exception {
        String sql = "UPDATE rb_intenciones SET resolucion=?,evento=?,diagnostico=? WHERE id=? AND resolucion='PENDIENTE'";
        var ausente = assertThrows(SQLException.class, () -> rr.resolver(UUID.randomUUID().toString(), "ERROR_TECNICO", null, null));
        assertTrue(ausente.getMessage().contains("afectadas=0")); assertTrue(ausente.getMessage().contains(sql));
        c.rollback();
        var id = intencion("REGISTRO_ORDEN", "A", Set.of());
        rr.resolver(id, "RECHAZADA", null, null); c.commit();
        var resuelta = assertThrows(SQLException.class, () -> rr.resolver(id, "ERROR_TECNICO", null, null));
        assertTrue(resuelta.getMessage().contains("afectadas=0")); assertTrue(resuelta.getMessage().contains(sql));
        c.rollback();
        assertEquals("RECHAZADA", rr.leer().intentos().getFirst().resolucion());
        assertEquals(0, numero("SELECT COUNT(*) FROM historial"));
    }
}
