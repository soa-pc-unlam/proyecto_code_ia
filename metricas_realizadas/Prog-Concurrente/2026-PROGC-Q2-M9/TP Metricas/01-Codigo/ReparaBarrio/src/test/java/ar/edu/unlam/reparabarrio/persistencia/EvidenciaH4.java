package ar.edu.unlam.reparabarrio.persistencia;

import ar.edu.unlam.reparabarrio.aplicacion.puertos.UnidadTrabajo;
import java.lang.reflect.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.ConcurrentHashMap;

/** Diagnóstico optativo de fixtures. Misma conexión, sin commit/rollback propios.
 * No hace SELECT después de terminar la transacción: iniciaría otra en H2.
 * La siguiente apertura observa el resultado persistido; el oráculo sigue siendo independiente.
 */
final class EvidenciaH4 {
    private static final AtomicLong UNIDADES = new AtomicLong();
    private static final Map<String, List<List<String>>> ULTIMAS = new ConcurrentHashMap<>();
    private EvidenciaH4() { }
    static UnidadTrabajo decorar(UnidadTrabajo unidad) throws Exception {
        if (!Boolean.getBoolean("h4.evidencia")) return unidad;
        // Acceso exclusivamente de test, sin ampliar la capacidad JDBC productiva.
        var campo = UnidadTrabajoJdbc.class.getDeclaredField("conexion"); campo.setAccessible(true);
        var c = (Connection) campo.get(unidad);
        String etiqueta = "u=" + UNIDADES.incrementAndGet() + " url=" + c.getMetaData().getURL();
        registrar(etiqueta, "APERTURA autoCommit=" + c.getAutoCommit() + " aislamiento=" + c.getTransactionIsolation());
        mostrar(c, etiqueta + " APERTURA");
        var proxy = (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (p, m, a) -> {
            String nombre = m.getName();
            if (Set.of("commit", "rollback").contains(nombre)) mostrar(c, etiqueta + " " + nombre + " ANTES");
            try {
                Object valor = m.invoke(c, a);
                if (nombre.equals("prepareStatement") && a[0] instanceof String sql)
                    return sentencia((PreparedStatement) valor, sql, etiqueta);
                if (Set.of("commit", "rollback", "close").contains(nombre)) registrar(etiqueta, nombre + " RETORNO");
                return valor;
            } catch (InvocationTargetException e) {
                registrar(etiqueta, nombre + " FALLO " + e.getCause()); throw e.getCause();
            }
        });
        // Un adaptador sobre el mismo handle; no abre otra conexión ni otro propietario.
        return new UnidadTrabajoJdbc(proxy);
    }
    private static PreparedStatement sentencia(PreparedStatement real, String sql, String etiqueta) {
        var uuids = new TreeMap<Integer, String>();
        return (PreparedStatement) Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(), new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
            try {
                Object valor = m.invoke(real, a);
                if (m.getName().equals("clearParameters")) uuids.clear();
                if (Set.of("setObject", "setString").contains(m.getName()) && a.length >= 2) {
                    int indice = (Integer) a[0]; uuids.remove(indice);
                    if (a[1] instanceof String s && s.length() == 36)
                        try { UUID.fromString(s); uuids.put(indice, s); } catch (IllegalArgumentException ignorada) { }
                }
                if (m.getName().equals("executeUpdate")) registrar(etiqueta, "DML afectadas=" + valor + " SQL=" + sql + " UUIDs=" + uuids);
                return valor;
            } catch (InvocationTargetException e) {
                registrar(etiqueta, "SQL=" + sql + " UUIDs=" + uuids + " FALLO " + e.getCause()); throw e.getCause();
            }
        });
    }
    static void mostrar(Connection c, String fase) {
        if (!Boolean.getBoolean("h4.evidencia")) return;
        // Sólo claves/estados de fixtures, sin descripciones ni diagnósticos del usuario.
        String[] consultas = {
            "SELECT READONLY()",
            "SELECT base_id,revision_tecnica FROM rb_contexto ORDER BY singleton",
            "SELECT id,base_id,abierta FROM rb_sesiones ORDER BY id",
            "SELECT id,sesion_id,resolucion,evento FROM rb_intenciones ORDER BY id",
            "SELECT orden_id,sesion_id,cierre_id FROM rb_revisiones ORDER BY orden_id",
            "SELECT orden_id,tecnico_id,puesto_id FROM asignaciones ORDER BY orden_id",
            "SELECT antiguedad,revision FROM contador ORDER BY singleton"
        };
        for (String sql : consultas) try (var st = c.createStatement(); var r = st.executeQuery(sql)) {
            var filas = new ArrayList<List<String>>();
            while (r.next()) {
                var fila = new ArrayList<String>();
                for (int i = 1; i <= r.getMetaData().getColumnCount(); i++) fila.add(r.getString(i));
                filas.add(fila);
            }
            var anteriores = ULTIMAS.put(c.getMetaData().getURL() + "|" + sql, filas);
            var nuevas = new ArrayList<>(filas);
            var retiradas = new ArrayList<List<String>>();
            if (anteriores != null) { nuevas.removeAll(anteriores); retiradas.addAll(anteriores); retiradas.removeAll(filas); }
            registrar(fase, "SQL=" + sql + " filas=" + filas.size() + " nuevas=" + nuevas + " retiradas=" + retiradas);
        } catch (SQLException e) { registrar(fase, "SQL=" + sql + " FALLO " + e); }
    }
    static void registrar(String fase, String detalle) {
        if (Boolean.getBoolean("h4.evidencia")) {
            System.out.println("H4-EVIDENCIA pid=" + ProcessHandle.current().pid() + " fase=" + fase + " " + detalle);
            System.out.flush();
        }
    }
}
