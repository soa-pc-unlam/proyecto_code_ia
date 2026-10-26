package ar.edu.unlam.reparabarrio.persistencia;

import ar.edu.unlam.reparabarrio.aplicacion.TestContextos;
import ar.edu.unlam.reparabarrio.arranque.GuardiaInstancia;
import org.junit.jupiter.api.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Ensayo dirigido del motor sin servicios, auditor, inicializador ni repositorios.
 * No sustituye T02/T03/T09: aísla commit/rollback/reapertura sobre archivo entre JVM.
 */
@Timeout(180)
class ArchivoH2H4Test {
    @Test void commitRollbackYSesionReferenciadaSobreArchivoEntreTresJvm() throws Exception {
        Path raiz = TestContextos.nuevaRaiz();
        for (String fase : new String[]{"crear", "recuperar", "verificar"})
            try (var p = new ProcesosH4(raiz, "jdbc-h2", fase)) {
                assertEquals(0, p.finalizar(), () -> fase + " " + p.salida);
                assertEquals("2", p.valor("ASIGNACIONES="));
            }
    }
    static void proceso(Path base, String fase) throws Exception {
        TestContextos.validar(base);
        try (var guardia = GuardiaInstancia.adquirir(base.getParent())) {
            String url = InicializadorBase.url(base) + (fase.equals("crear") ? "" : ";IFEXISTS=TRUE");
            try (var c = DriverManager.getConnection(url, "sa", "")) {
                System.out.println("H4-JDBC fase=" + fase + " url=" + c.getMetaData().getURL());
                if (fase.equals("crear")) {
                    ejecutar(c, "CREATE TABLE sesiones(id VARCHAR PRIMARY KEY, abierta BOOLEAN)");
                    ejecutar(c, "CREATE TABLE asignaciones(id VARCHAR PRIMARY KEY)");
                    ejecutar(c, "CREATE TABLE revisiones(id VARCHAR PRIMARY KEY REFERENCES asignaciones(id), sesion VARCHAR REFERENCES sesiones(id))");
                    ejecutar(c, "CREATE TABLE intenciones(id VARCHAR PRIMARY KEY, sesion VARCHAR REFERENCES sesiones(id))");
                }
                c.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE); c.setAutoCommit(false);
                estado(c, fase + " APERTURA");
                if (fase.equals("crear")) {
                    ejecutar(c, "INSERT INTO sesiones VALUES('A',TRUE)");
                    ejecutar(c, "INSERT INTO asignaciones VALUES('CURSO1'),('CURSO2')");
                    estado(c, "ANTES_COMMIT"); c.commit(); estado(c, "DESPUES_COMMIT");
                    c.rollback();
                } else if (fase.equals("recuperar")) {
                    assertEquals(2, numero(c, "SELECT COUNT(*) FROM asignaciones"));
                    ejecutar(c, "INSERT INTO revisiones VALUES('CURSO1','A')");
                    estado(c, "ANTES_ROLLBACK_INYECTADO"); c.rollback(); estado(c, "DESPUES_ROLLBACK");
                    assertEquals(2, numero(c, "SELECT COUNT(*) FROM asignaciones"));
                    assertEquals(0, numero(c, "SELECT COUNT(*) FROM revisiones"));
                    assertEquals(1, numero(c, "SELECT COUNT(*) FROM sesiones WHERE abierta=TRUE"));
                    ejecutar(c, "INSERT INTO revisiones VALUES('CURSO1','A'),('CURSO2','A')");
                    ejecutar(c, "UPDATE sesiones SET abierta=FALSE WHERE id='A'");
                    String sesion = UUID.randomUUID().toString();
                    try (var p = c.prepareStatement("INSERT INTO sesiones VALUES(?,TRUE)")) { p.setString(1, sesion); assertEquals(1, p.executeUpdate()); }
                    estado(c, "ANTES_COMMIT_RECUPERACION"); c.commit();
                } else {
                    assertEquals(2, numero(c, "SELECT COUNT(*) FROM revisiones"));
                    assertEquals(1, numero(c, "SELECT COUNT(*) FROM sesiones WHERE abierta=TRUE"));
                    ejecutar(c, "INSERT INTO intenciones SELECT 'I',id FROM sesiones WHERE abierta=TRUE");
                    c.commit();
                }
                assertEquals(2, numero(c, "SELECT COUNT(*) FROM asignaciones"));
                estado(c, fase + " PREVIO_CIERRE"); c.rollback();
                System.out.println("ASIGNACIONES=2");
            }
        }
    }
    private static void ejecutar(Connection c, String sql) throws SQLException {
        try (var st = c.createStatement()) { int filas = st.executeUpdate(sql); System.out.println("H4-JDBC SQL=" + sql + " afectadas=" + filas); }
    }
    private static long numero(Connection c, String sql) throws SQLException {
        try (var st = c.createStatement(); var r = st.executeQuery(sql)) { assertTrue(r.next()); return r.getLong(1); }
    }
    private static void estado(Connection c, String fase) throws SQLException {
        for (String sql : new String[]{"SELECT READONLY()", "SELECT * FROM sesiones ORDER BY id", "SELECT * FROM asignaciones ORDER BY id",
                "SELECT * FROM revisiones ORDER BY id", "SELECT * FROM intenciones ORDER BY id"}) {
            try (var st = c.createStatement(); var r = st.executeQuery(sql)) {
                StringBuilder datos = new StringBuilder(); int filas = 0;
                while (r.next()) { filas++; datos.append('['); for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) datos.append(r.getString(i)).append(','); datos.append(']'); }
                System.out.println("H4-JDBC fase=" + fase + " SQL=" + sql + " filas=" + filas + " datos=" + datos);
            }
        }
    }
}
