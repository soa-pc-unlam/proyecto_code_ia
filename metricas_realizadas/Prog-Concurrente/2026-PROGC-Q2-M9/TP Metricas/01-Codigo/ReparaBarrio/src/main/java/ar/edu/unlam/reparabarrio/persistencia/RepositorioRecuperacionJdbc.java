package ar.edu.unlam.reparabarrio.persistencia;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.aplicacion.puertos.RepositorioRecuperacion;
import java.sql.*;
import java.time.Instant;
import java.util.*;

final class RepositorioRecuperacionJdbc implements RepositorioRecuperacion {
    private final Connection c;
    RepositorioRecuperacionJdbc(Connection c) { this.c = c; }
    private void escribir(String sql, Object... valores) throws SQLException {
        try (var p = c.prepareStatement(sql)) {
            for (int i = 0; i < valores.length; i++) p.setObject(i + 1, valores[i]);
            int filas = p.executeUpdate();
            if (filas != 1) throw new SQLException("Metadatos: cantidad de filas inesperada; esperadas=1; afectadas="
                    + filas + "; SQL=" + sql);
        }
    }
    private void revision() throws SQLException { escribir("UPDATE rb_contexto SET revision_tecnica=revision_tecnica+1 WHERE singleton=1"); }
    @Override public Datos leer() throws Exception {
        var hallazgos = new ArrayList<String>();
        InicializadorBase.validarEstructura(c, 2);
        InicializadorBase.verificarVersion(c, 2);
        InicializadorBase.verificarWriteDelay(c);
        Metadata metadata;
        try (var st = c.createStatement(); var r = st.executeQuery("SELECT base_id,frontera_v1,revision_tecnica,adopcion_pendiente FROM rb_contexto WHERE singleton=1")) {
            if (!r.next()) throw new SQLException("Identidad ausente");
            metadata = new Metadata(r.getString(1), r.getLong(2), r.getLong(3), r.getBoolean(4));
            UUID.fromString(metadata.baseId());
            if (r.next()) throw new SQLException("Identidad múltiple");
        }
        try (var st = c.createStatement(); var r = st.executeQuery("SELECT base_id,completo,origen,paso FROM rb_protocolo")) {
            if (!r.next() || !r.getBoolean(2) || !metadata.baseId().equals(r.getString(1))
                    || (r.getInt(3) == 0 && metadata.fronteraV1() != 0)
                    || r.getInt(4) != (r.getInt(3) == 0 ? InicializadorBase.sql("esquema.sql").size() + InicializadorBase.sql("migracion-v1-v2.sql").size() : InicializadorBase.sql("migracion-v1-v2.sql").size() + 2) || r.next())
                throw new SQLException("Protocolo incompleto o incompatible");
        }
        var sesiones = new ArrayList<Sesion>();
        try (var st = c.createStatement(); var r = st.executeQuery("SELECT id,abierta,base_id FROM rb_sesiones ORDER BY momento,id")) {
            while (r.next()) {
                UUID.fromString(r.getString(1));
                if (!metadata.baseId().equals(r.getString(3))) hallazgos.add("Sesión de base ajena");
                sesiones.add(new Sesion(r.getString(1), r.getBoolean(2)));
            }
        }
        var intentos = new ArrayList<Intento>();
        try (var st = c.createStatement(); var r = st.executeQuery("SELECT id,base_id,sesion_id,clase,descriptor,revision_base,resolucion,evento,diagnostico FROM rb_intenciones ORDER BY id")) {
            while (r.next()) intentos.add(new Intento(r.getString(1), r.getString(2), r.getString(3), r.getString(4),
                    DescriptorOperacion.decodificar(r.getString(5)), r.getLong(6), r.getString(7), (Long) r.getObject(8), r.getString(9)));
        }
        var revisiones = new HashMap<String, Revision>();
        try (var st = c.createStatement(); var r = st.executeQuery("SELECT orden_id,sesion_id,motivo,momento,cierre_id FROM rb_revisiones")) {
            while (r.next()) revisiones.put(r.getString(1), new Revision(r.getString(1), r.getString(2), r.getString(3), r.getString(4), r.getString(5)));
        }
        long antiguedad;
        try (var st = c.createStatement(); var r = st.executeQuery("SELECT antiguedad FROM contador")) {
            if (!r.next()) throw new SQLException("Contador ausente"); antiguedad = r.getLong(1);
            if (r.next()) hallazgos.add("Contador múltiple");
        }
        hallazgos.addAll(huérfanas(c));
        return new Datos(metadata, sesiones, intentos, revisiones, antiguedad, hallazgos);
    }
    /** Lee todas las relaciones, incluso filas omitidas por el mapeo del agregado. */
    static List<String> huérfanas(Connection c) throws SQLException {
        String[] consultas = {
            "SELECT COUNT(*) FROM requisitos r LEFT JOIN ordenes o ON o.id=r.orden_id LEFT JOIN catalogo t ON t.tipo=r.tipo WHERE o.id IS NULL OR t.tipo IS NULL",
            "SELECT COUNT(*) FROM herramientas_activas h LEFT JOIN asignaciones a ON a.orden_id=h.orden_id LEFT JOIN requisitos r ON r.orden_id=h.orden_id AND r.tipo=h.tipo LEFT JOIN herramientas u ON u.id=h.herramienta_id AND u.tipo=h.tipo WHERE a.orden_id IS NULL OR r.orden_id IS NULL OR u.id IS NULL",
            "SELECT COUNT(*) FROM historial_recursos r LEFT JOIN historial h ON h.secuencia=r.evento LEFT JOIN tecnicos t ON t.id=r.tecnico_id LEFT JOIN puestos p ON p.id=r.puesto_id LEFT JOIN herramientas u ON u.id=r.herramienta_id WHERE h.secuencia IS NULL OR NOT ((r.categoria='TECNICO' AND t.id IS NOT NULL AND t.id=r.id AND r.puesto_id IS NULL AND r.herramienta_id IS NULL) OR (r.categoria='PUESTO' AND p.id IS NOT NULL AND p.id=r.id AND r.tecnico_id IS NULL AND r.herramienta_id IS NULL) OR (r.categoria='HERRAMIENTA' AND u.id IS NOT NULL AND u.id=r.id AND r.tecnico_id IS NULL AND r.puesto_id IS NULL))",
            "SELECT COUNT(*) FROM historial h LEFT JOIN ordenes o ON o.id=h.orden_id WHERE h.orden_id IS NOT NULL AND o.id IS NULL"
        };
        var errores = new ArrayList<String>();
        for (int i = 0; i < consultas.length; i++) try (var st = c.createStatement(); var r = st.executeQuery(consultas[i])) {
            r.next(); if (r.getLong(1) != 0) errores.add("Relación huérfana/contradictoria " + i);
        }
        return errores;
    }
    @Override public void preparar(Intento i) throws SQLException {
        escribir("INSERT INTO rb_intenciones VALUES(?,?,?,?,?,?,?,?,?)", i.id(), i.baseId(), i.sesionId(), i.clase(),
                i.descriptor().codificar(), i.revisionBase(), "PENDIENTE", null, i.diagnostico());
    }
    @Override public void resolver(String id, String resolucion, Long evento, String diagnostico) throws SQLException {
        escribir("UPDATE rb_intenciones SET resolucion=?,evento=?,diagnostico=? WHERE id=? AND resolucion='PENDIENTE'", resolucion, evento, diagnostico, id);
    }
    @Override public void diagnosticar(String id, String diagnostico) throws SQLException {
        escribir("UPDATE rb_intenciones SET diagnostico=? WHERE id=?", diagnostico, id);
    }
    @Override public void abrirSesion(String id) throws SQLException {
        escribir("INSERT INTO rb_sesiones VALUES(?,(SELECT base_id FROM rb_contexto),TRUE,?)", id, Instant.now().toString());
    }
    @Override public void limpiarSesion(String id) throws SQLException { escribir("UPDATE rb_sesiones SET abierta=FALSE WHERE id=? AND abierta=TRUE", id); }
    @Override public void revisar(String orden, String sesion, String motivo) throws SQLException {
        try (var p = c.prepareStatement("SELECT cierre_id FROM rb_revisiones WHERE orden_id=?")) {
            p.setString(1, orden); try (var r = p.executeQuery()) { if (r.next()) return; }
        }
        escribir("INSERT INTO rb_revisiones VALUES(?,?,?,?,NULL)", orden, sesion, motivo, Instant.now().toString()); revision();
    }
    @Override public void resolverRevision(String orden, String cierreId) throws SQLException {
        try (var p = c.prepareStatement("UPDATE rb_revisiones SET cierre_id=? WHERE orden_id=? AND cierre_id IS NULL")) {
            p.setString(1, cierreId); p.setString(2, orden); if (p.executeUpdate() != 0) revision();
        }
    }
    @Override public void aceptarAdopcion(String referencia, String declaracion) throws SQLException {
        escribir("UPDATE rb_contexto SET adopcion_pendiente=FALSE,aceptacion=?,declaracion=? WHERE singleton=1 AND adopcion_pendiente=TRUE", referencia, declaracion); revision();
    }
}
