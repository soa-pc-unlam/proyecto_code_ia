package ar.edu.unlam.reparabarrio.persistencia;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.arranque.GuardiaInstancia;
import ar.edu.unlam.reparabarrio.dominio.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import static ar.edu.unlam.reparabarrio.persistencia.H4Fixture.ok;
import static org.junit.jupiter.api.Assertions.*;

@Timeout(60)
class CoherenciaH4Test {
    @AfterEach void cerrar() throws Exception { TestContextos.cerrarTodos(); }
    @ParameterizedTest @ValueSource(strings={"HISTORIAL_OMITIDO", "HISTORIAL_DUPLICADO", "CIERRE_SIN_INICIO", "CONTADOR_REVISION", "CONTADOR_ANTIGUEDAD", "ASIGNACION_INCOMPLETA", "ASIGNACION_HUERFANA", "REQUISITO_HUERFANO", "RECURSO_HISTORICO_HUERFANO", "REVISION_CONTRADICTORIA", "TESTIGO_AJENO", "RESOLUCION_SIN_EVENTO", "EVENTO_SIN_INTENCION", "DESCRIPTOR", "CATALOGO", "HABILITACION_OCUPADA", "SCHEMA"})
    void h4T11_h4T16_auditoriaBloqueaCadaIncoherenciaSinAutocorregir(String variante) throws Exception {
        Path base;
        try (var f = new H4Fixture()) {
            base = f.base; f.capacidad(1); f.herramienta("M", TipoHerramienta.MULTIMETRO, true); f.herramienta("D", TipoHerramienta.TALADRO, true);
            f.orden("A", TipoHerramienta.MULTIMETRO, TipoHerramienta.TALADRO); ok(f.servicio.confirmarInicio(f.propuesta()));
        }
        // Todas las conexiones de negocio cerradas; corrupción explícita sólo del fixture.
        try (var g = GuardiaInstancia.adquirir(base.getParent()); var c = DriverManager.getConnection(InicializadorBase.url(base) + ";IFEXISTS=TRUE", "sa", ""); var st = c.createStatement()) {
            st.execute("SET REFERENTIAL_INTEGRITY FALSE");
            for (String sentencia : switch (variante) {
                case "HISTORIAL_OMITIDO" -> List.of("DELETE FROM historial_recursos WHERE evento=(SELECT MAX(secuencia) FROM historial)", "DELETE FROM historial WHERE secuencia=(SELECT MAX(secuencia) FROM historial)");
                case "HISTORIAL_DUPLICADO" -> List.of("INSERT INTO historial SELECT (SELECT MAX(secuencia)+1 FROM historial),'" + UUID.randomUUID() + "','A','REGISTRO_ORDEN',momento FROM historial WHERE accion='REGISTRO_ORDEN'", "UPDATE contador SET revision=revision+1");
                case "CIERRE_SIN_INICIO" -> List.of("UPDATE historial SET accion='FINALIZADA' WHERE accion='INICIO'");
                case "CONTADOR_REVISION" -> List.of("UPDATE contador SET revision=0");
                case "CONTADOR_ANTIGUEDAD" -> List.of("UPDATE contador SET antiguedad=2");
                case "ASIGNACION_INCOMPLETA" -> List.of("DELETE FROM herramientas_activas WHERE tipo='TALADRO'");
                case "ASIGNACION_HUERFANA" -> List.of("DELETE FROM asignaciones WHERE orden_id='A'");
                case "REQUISITO_HUERFANO" -> List.of("INSERT INTO requisitos VALUES('AJENA','SOLDADOR')");
                // Una sola fila: mover todas colisionaría entre alta e inicio de la misma unidad.
                case "RECURSO_HISTORICO_HUERFANO" -> List.of("UPDATE historial_recursos SET evento=999 WHERE categoria='HERRAMIENTA' AND id='M' AND evento=(SELECT MAX(secuencia) FROM historial)");
                case "REVISION_CONTRADICTORIA" -> List.of("INSERT INTO rb_revisiones SELECT 'A',id,'fixture','2026-10-14T00:00:00Z',(SELECT operacion_id FROM historial WHERE accion='REGISTRO_ORDEN') FROM rb_sesiones FETCH FIRST 1 ROW ONLY");
                case "TESTIGO_AJENO" -> List.of("UPDATE historial SET operacion_id='" + UUID.randomUUID() + "' WHERE accion='INICIO'");
                case "RESOLUCION_SIN_EVENTO" -> List.of("UPDATE rb_intenciones SET evento=999 WHERE id=(SELECT operacion_id FROM historial WHERE accion='INICIO')");
                case "EVENTO_SIN_INTENCION" -> List.of("DELETE FROM rb_intenciones WHERE id=(SELECT operacion_id FROM historial WHERE accion='INICIO')");
                case "DESCRIPTOR" -> List.of("UPDATE rb_intenciones SET descriptor='INVALIDO' WHERE id=(SELECT operacion_id FROM historial WHERE accion='INICIO')");
                case "CATALOGO" -> List.of("DELETE FROM catalogo WHERE tipo='SOLDADOR'");
                case "HABILITACION_OCUPADA" -> List.of("UPDATE tecnicos SET habilitado=FALSE");
                default -> List.of("ALTER TABLE tecnicos ADD columna_ajena INTEGER");
            }) st.execute(sentencia);
            if (variante.equals("RECURSO_HISTORICO_HUERFANO")) {
                try (var r = st.executeQuery("SELECT COUNT(*) FROM historial_recursos WHERE evento=999")) {
                    assertTrue(r.next()); assertEquals(1, r.getLong(1), "La corrupción debe alcanzar el auditor");
                }
            }
        }
        String antes = OraculoH4.filas(base);
        try {
            var contexto = TestContextos.abrir(base);
            assertEquals(ContextoTaller.Ciclo.BLOQUEADO, contexto.informe().ciclo(), contexto.informe()::toString);
            var r = contexto.servicio().consultar(); assertEquals(ResultadoOperacion.Estado.ERROR_TECNICO, r.estado()); assertNull(r.valor());
        } catch (Exception e) {
            // Fallo antes de componer: versión/esquema/metadatos inválidos, ninguna fachada publicada.
            assertTrue(variante.equals("SCHEMA") || variante.equals("DESCRIPTOR") || variante.equals("RESOLUCION_SIN_EVENTO"), e::toString);
        }
        assertEquals(antes, OraculoH4.filas(base));
    }
    @Test void h4T10_versionDesconocidaNoSeRecrea() throws Exception {
        Path base;
        try (var f = new H4Fixture()) { base = f.base; f.orden("A"); }
        try (var g = GuardiaInstancia.adquirir(base.getParent()); var c = DriverManager.getConnection(InicializadorBase.url(base) + ";IFEXISTS=TRUE", "sa", ""); var st = c.createStatement()) {
            st.execute("DROP TABLE rb_version"); st.execute("CREATE TABLE rb_version(version INTEGER)"); st.execute("INSERT INTO rb_version VALUES(99)");
        }
        String antes = OraculoH4.filas(base);
        assertThrows(SQLException.class, () -> TestContextos.abrir(base)); assertEquals(antes, OraculoH4.filas(base));
    }
    @Test void h4T10_archivoIlegibleConservadoSinReemplazo() throws Exception {
        Path base = TestContextos.nuevaRaiz().resolve("taller"); byte[] ilegible = "No es una base H2".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Files.write(Path.of(base + ".mv.db"), ilegible);
        assertThrows(Exception.class, () -> TestContextos.abrir(base)); assertArrayEquals(ilegible, Files.readAllBytes(Path.of(base + ".mv.db")));
    }
    @Test void h4T10_perdidaConexionLecturaBloqueaYRestauracionExigeAuditoria() throws Exception {
        try (var f = new H4Fixture()) {
            f.orden("A"); f.negocio.set(new H4Fixture.Control("APERTURA")); var r = f.servicio.consultar();
            assertEquals(ResultadoOperacion.Estado.ERROR_TECNICO, r.estado()); assertNull(r.valor());
            // Una lectura fallida por acceso no permite presentar datos; recuperación explícita completa.
            f.negocio.set(null); assertEquals(ContextoTaller.Ciclo.HABILITADO, f.contexto.recuperacion().recuperar().ciclo()); f.vista();
        }
    }
}
