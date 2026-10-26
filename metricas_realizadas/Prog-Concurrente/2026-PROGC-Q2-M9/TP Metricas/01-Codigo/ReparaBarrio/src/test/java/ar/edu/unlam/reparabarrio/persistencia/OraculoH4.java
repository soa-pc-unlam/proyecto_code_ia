package ar.edu.unlam.reparabarrio.persistencia;

import ar.edu.unlam.reparabarrio.aplicacion.TestContextos;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;

/** Filas completas ordenadas; no depende de recuperación/auditor/huellas de producción. */
final class OraculoH4 {
    static String filas(Path base) throws Exception {
        TestContextos.validar(base); var filas = new ArrayList<String>();
        String url = "jdbc:h2:file:" + base.toString().replace('\\', '/') + ";IFEXISTS=TRUE;WRITE_DELAY=0;LOCK_TIMEOUT=5000";
        try (var c = DriverManager.getConnection(url, "sa", "")) {
            c.setAutoCommit(false);
            for (String tabla : List.of("catalogo", "contador", "ordenes", "requisitos", "tecnicos", "puestos", "herramientas", "asignaciones", "herramientas_activas", "historial", "historial_recursos"))
                try (var st = c.createStatement(); var r = st.executeQuery("SELECT * FROM " + tabla)) {
                    while (r.next()) {
                        StringBuilder fila = new StringBuilder(tabla);
                        for (int col = 1; col <= r.getMetaData().getColumnCount(); col++) {
                            String valor = r.getString(col);
                            fila.append('|').append(valor == null ? "~" : Base64.getEncoder().encodeToString(valor.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                        }
                        filas.add(fila.toString());
                    }
                }
            c.rollback();
        }
        Collections.sort(filas);
        return Base64.getEncoder().encodeToString(String.join("\n", filas).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
