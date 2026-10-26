package ar.edu.unlam.reparabarrio.persistencia;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import static ar.edu.unlam.reparabarrio.persistencia.H4Fixture.ok;
import static org.junit.jupiter.api.Assertions.*;

/** Suplemento del código real de servicios/JDBC. No reemplaza los tests entre JVM. */
class CorreccionH4MemoriaTest {
    private Connection mantener;
    private Path archivo;
    private String jdbc, identidad;
    @BeforeEach void preparar() throws Exception {
        archivo = TestContextos.nuevaRaiz().resolve("taller");
        identidad = UUID.randomUUID().toString(); jdbc = "jdbc:h2:mem:" + identidad + ";WRITE_DELAY=0";
        mantener = DriverManager.getConnection(jdbc, "sa", "");
        try (var st = mantener.createStatement()) {
            for (var recurso : List.of("protocolo.sql", "esquema.sql", "migracion-v1-v2.sql"))
                for (var ddl : InicializadorBase.sql(recurso)) st.execute(ddl);
            int pasos = InicializadorBase.sql("esquema.sql").size() + InicializadorBase.sql("migracion-v1-v2.sql").size();
            st.execute("INSERT INTO rb_protocolo VALUES(1,2,0,2,'" + identidad + "','TEST','AUSENTE','',''," + pasos + ",TRUE)");
            st.execute("INSERT INTO rb_version VALUES(2)"); st.execute("INSERT INTO contador VALUES(1,0,0)");
            for (var tipo : TipoHerramienta.values()) st.execute("INSERT INTO catalogo VALUES('" + tipo.name() + "')");
            st.execute("INSERT INTO rb_contexto VALUES(1,'" + identidad + "',2,0,0,FALSE,NULL,NULL)");
        }
    }
    @AfterEach void cerrar() throws Exception { mantener.close(); }
    private H4Fixture abrir(H4Fixture.Control fallo) throws Exception { return new H4Fixture(archivo, jdbc, identidad, fallo); }
    private void interrumpida() throws Exception {
        try (var f = abrir(null)) {
            assertEquals(ContextoTaller.Ciclo.HABILITADO, f.contexto.ciclo(), f.contexto.informe()::toString);
            f.escenario(); assertEquals(2, f.numero("SELECT COUNT(*) FROM asignaciones"));
            // Sesión abierta conservada, sin fingir una caída real en este suplemento.
            f.infraestructura.set(new H4Fixture.Control("RR_LIMPIARSESION")); f.contexto.close();
            assertEquals(1, f.numero("SELECT COUNT(*) FROM rb_sesiones WHERE abierta=TRUE"));
        }
    }
    @Test void primeraFactibleTrasRecuperacionConservaDosAsignaciones() throws Exception {
        interrumpida();
        try (var f = abrir(null)) {
            assertEquals(ContextoTaller.Ciclo.HABILITADO, f.contexto.ciclo(), f.contexto.informe()::toString);
            assertEquals(2, f.vista().revisiones().size()); f.orden("NUEVA_SIN_HERRAMIENTAS");
            var p = f.propuesta(); assertEquals("NUEVA_SIN_HERRAMIENTAS", p.ordenId());
            assertEquals("T3", p.recursos().tecnico().id()); assertEquals("P3", p.recursos().puesto().id());
        }
    }
    @Test void recursoHistoricoHuerfanoEsSqlValidoYBloqueaAuditor() throws Exception {
        try (var f = abrir(null)) {
            f.capacidad(1); f.herramienta("M", TipoHerramienta.MULTIMETRO, true);
            f.orden("A", TipoHerramienta.MULTIMETRO); ok(f.servicio.confirmarInicio(f.propuesta()));
            try (var c = f.conexion(); var st = c.createStatement()) {
                st.execute("SET REFERENTIAL_INTEGRITY FALSE");
                assertEquals(1, st.executeUpdate("UPDATE historial_recursos SET evento=999 WHERE categoria='HERRAMIENTA' AND id='M' AND evento=(SELECT MAX(secuencia) FROM historial)"));
            }
            assertEquals(1, f.numero("SELECT COUNT(*) FROM historial_recursos WHERE evento=999"));
            var informe = f.contexto.recuperacion().recuperar();
            assertEquals(ContextoTaller.Ciclo.BLOQUEADO, informe.ciclo(), informe::toString);
            assertTrue(informe.hallazgos().stream().anyMatch(h -> h.contains("huérfana")), informe::toString);
            assertNull(f.servicio.consultar().valor());
            assertEquals(1, f.numero("SELECT COUNT(*) FROM historial_recursos WHERE evento=999"));
        }
    }
    @Test void rollbackCierreConRevisionNoEsCommitIncierto() throws Exception {
        interrumpida();
        try (var f = abrir(null)) {
            assertEquals(ContextoTaller.Ciclo.HABILITADO, f.contexto.ciclo(), f.contexto.informe()::toString);
            f.negocios.reiniciar(); var control = new H4Fixture.Control("RR_RESOLVERREVISION"); f.negocio.set(control);
            var resultado = f.servicio.finalizar("CURSO1");
            assertTrue(control.usado.get(), resultado::diagnostico);
            assertEquals(ResultadoOperacion.Estado.ERROR_TECNICO, resultado.estado(), resultado::diagnostico);
            assertEquals(0, f.negocios.commits.get()); assertEquals(1, f.negocios.rollbacks.get());
            assertEquals(2, f.numero("SELECT COUNT(*) FROM asignaciones"));
            assertEquals(2, f.numero("SELECT COUNT(*) FROM rb_revisiones WHERE cierre_id IS NULL"));
        }
    }
    @Test void revisionFallidaNoLimpiaSesionYReintentoPersisteAmbasMarcas() throws Exception {
        interrumpida();
        var control = new H4Fixture.Control("RR_REVISAR");
        try (var f = abrir(control)) {
            assertTrue(control.usado.get(), f.contexto.informe()::toString);
            assertEquals(ContextoTaller.Ciclo.BLOQUEADO, f.contexto.ciclo());
            assertEquals(2, f.numero("SELECT COUNT(*) FROM asignaciones"));
            assertEquals(0, f.numero("SELECT COUNT(*) FROM rb_revisiones"));
            assertEquals(1, f.numero("SELECT COUNT(*) FROM rb_sesiones WHERE abierta=TRUE"));
            f.infraestructura.set(null); var informe = f.contexto.recuperacion().recuperar();
            assertEquals(ContextoTaller.Ciclo.HABILITADO, informe.ciclo(), informe::toString);
            assertEquals(2, f.vista().revisiones().size());
        }
    }
}
