package ar.edu.unlam.reparabarrio.persistencia;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.aplicacion.puertos.RepositorioTaller;
import ar.edu.unlam.reparabarrio.dominio.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/** Adaptador privado; el SQL no decide prioridad ni selección física. */
final class RepositorioTallerJdbc implements RepositorioTaller {
    private final Connection conexion;
    RepositorioTallerJdbc(Connection conexion) { this.conexion = conexion; }
    @FunctionalInterface private interface Fila { void leer(ResultSet rs) throws SQLException; }
    private void leer(String sql, Fila fila, Object... parametros) throws SQLException {
        try (var p = conexion.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) p.setObject(i + 1, parametros[i]);
            try (var rs = p.executeQuery()) { while (rs.next()) fila.leer(rs); }
        }
    }
    private void escribir(String sql, int cantidad, Object... parametros) throws SQLException {
        try (var p = conexion.prepareStatement(sql)) {
            for (int i = 0; i < parametros.length; i++) p.setObject(i + 1, parametros[i]);
            int filas = p.executeUpdate();
            if (cantidad >= 0 && filas != cantidad) throw new SQLException("Cantidad inesperada de filas: " + filas);
        }
    }
    private long contador(String columna) throws SQLException {
        long[] valor = {-1};
        leer("SELECT " + columna + " FROM contador WHERE singleton=1", rs -> valor[0] = rs.getLong(1));
        if (valor[0] < 0) throw new SQLException("Contador ausente o inválido");
        return valor[0];
    }
    @Override public long revision() throws SQLException { return contador("revision"); }
    @Override public long siguienteSecuencia() throws SQLException {
        escribir("UPDATE contador SET antiguedad=antiguedad+1 WHERE singleton=1", 1);
        return contador("antiguedad");
    }
    @Override public Set<TipoHerramienta> catalogo() throws SQLException {
        var tipos = EnumSet.noneOf(TipoHerramienta.class);
        leer("SELECT tipo FROM catalogo", rs -> tipos.add(TipoHerramienta.valueOf(rs.getString(1))));
        if (!tipos.equals(EnumSet.allOf(TipoHerramienta.class))) throw new SQLException("Catálogo incompatible con dominio");
        return Set.copyOf(tipos);
    }
    private static String tabla(Recurso.Categoria categoria) {
        return switch (categoria) { case TECNICO -> "tecnicos"; case PUESTO -> "puestos"; case HERRAMIENTA -> "herramientas"; };
    }
    @Override public InstantaneaDominio cargar(String contexto) throws SQLException {
        catalogo();
        var requisitos = new HashMap<String, Set<TipoHerramienta>>();
        leer("SELECT orden_id,tipo FROM requisitos", rs -> requisitos.computeIfAbsent(rs.getString(1),
                k -> EnumSet.noneOf(TipoHerramienta.class)).add(TipoHerramienta.valueOf(rs.getString(2))));
        var ordenes = new ArrayList<Orden>();
        leer("SELECT id,descripcion,secuencia,estado FROM ordenes ORDER BY secuencia", rs -> ordenes.add(new Orden(
                rs.getString(1), rs.getString(2), rs.getLong(3), requisitos.getOrDefault(rs.getString(1), Set.of()),
                EstadoOrden.valueOf(rs.getString(4)))));
        var recursos = new ArrayList<Recurso>();
        for (var categoria : Recurso.Categoria.values())
            leer("SELECT id,habilitado" + (categoria == Recurso.Categoria.HERRAMIENTA ? ",tipo" : "") + " FROM " + tabla(categoria) + " ORDER BY id",
                    rs -> recursos.add(new Recurso(new Recurso.Clave(categoria, rs.getString(1)),
                            categoria == Recurso.Categoria.HERRAMIENTA ? TipoHerramienta.valueOf(rs.getString(3)) : null, rs.getBoolean(2))));
        var herramientas = new HashMap<String, Map<TipoHerramienta, Recurso.Clave>>();
        leer("SELECT orden_id,tipo,herramienta_id FROM herramientas_activas", rs -> herramientas.computeIfAbsent(
                rs.getString(1), k -> new EnumMap<>(TipoHerramienta.class)).put(TipoHerramienta.valueOf(rs.getString(2)),
                new Recurso.Clave(Recurso.Categoria.HERRAMIENTA, rs.getString(3))));
        var activas = new HashMap<String, SeleccionRecursos>();
        leer("SELECT orden_id,tecnico_id,puesto_id FROM asignaciones", rs -> activas.put(rs.getString(1), new SeleccionRecursos(
                new Recurso.Clave(Recurso.Categoria.TECNICO, rs.getString(2)), new Recurso.Clave(Recurso.Categoria.PUESTO, rs.getString(3)),
                herramientas.getOrDefault(rs.getString(1), Map.of()))));
        // La construcción comprueba INV-01 a INV-05: cualquier agregado parcial es error técnico.
        return new InstantaneaDominio(contexto, ordenes, recursos, activas);
    }
    @Override public void registrar(Orden orden) throws SQLException {
        escribir("INSERT INTO ordenes(id,descripcion,secuencia,estado) VALUES(?,?,?,?)", 1,
                orden.id(), orden.descripcion(), orden.secuencia(), orden.estado().name());
        for (var tipo : orden.requisitos()) escribir("INSERT INTO requisitos(orden_id,tipo) VALUES(?,?)", 1, orden.id(), tipo.name());
    }
    @Override public void registrar(Recurso recurso) throws SQLException {
        if (recurso.tipo() == null) escribir("INSERT INTO " + tabla(recurso.clave().categoria()) + "(id,habilitado) VALUES(?,?)", 1,
                recurso.clave().id(), recurso.habilitado());
        else escribir("INSERT INTO herramientas(id,tipo,habilitado) VALUES(?,?,?)", 1,
                recurso.clave().id(), recurso.tipo().name(), recurso.habilitado());
    }
    @Override public void habilitar(Recurso.Clave clave, boolean habilitado) throws SQLException {
        escribir("UPDATE " + tabla(clave.categoria()) + " SET habilitado=? WHERE id=?", 1, habilitado, clave.id());
    }
    @Override public void estado(String id, EstadoOrden estado) throws SQLException {
        escribir("UPDATE ordenes SET estado=? WHERE id=?", 1, estado.name(), id);
    }
    @Override public void asignar(String id, SeleccionRecursos seleccion) throws SQLException {
        escribir("INSERT INTO asignaciones(orden_id,tecnico_id,puesto_id) VALUES(?,?,?)", 1,
                id, seleccion.tecnico().id(), seleccion.puesto().id());
        for (var e : seleccion.herramientas().entrySet()) escribir(
                "INSERT INTO herramientas_activas(orden_id,tipo,herramienta_id) VALUES(?,?,?)", 1, id, e.getKey().name(), e.getValue().id());
    }
    @Override public void liberar(String id) throws SQLException {
        escribir("DELETE FROM herramientas_activas WHERE orden_id=?", -1, id);
        escribir("DELETE FROM asignaciones WHERE orden_id=?", 1, id);
    }
    @Override public void evento(String op, String ordenId, String accion, Instant momento, Set<Recurso.Clave> recursos) throws SQLException {
        escribir("UPDATE contador SET revision=revision+1 WHERE singleton=1", 1);
        long secuencia = revision();
        escribir("INSERT INTO historial(secuencia,operacion_id,orden_id,accion,momento) VALUES(?,?,?,?,?)", 1,
                secuencia, op, ordenId, accion, momento.toString());
        for (var c : recursos) escribir("INSERT INTO historial_recursos(evento,categoria,id,tecnico_id,puesto_id,herramienta_id) VALUES(?,?,?,?,?,?)", 1,
                secuencia, c.categoria().name(), c.id(), c.categoria() == Recurso.Categoria.TECNICO ? c.id() : null,
                c.categoria() == Recurso.Categoria.PUESTO ? c.id() : null, c.categoria() == Recurso.Categoria.HERRAMIENTA ? c.id() : null);
    }
    @Override public List<EventoHistorial> historial() throws SQLException {
        var recursos = new HashMap<Long, Set<Recurso.Clave>>();
        leer("SELECT evento,categoria,id FROM historial_recursos", rs -> recursos.computeIfAbsent(rs.getLong(1), k -> new HashSet<>())
                .add(new Recurso.Clave(Recurso.Categoria.valueOf(rs.getString(2)), rs.getString(3))));
        var eventos = new ArrayList<EventoHistorial>();
        leer("SELECT secuencia,operacion_id,orden_id,accion,momento FROM historial ORDER BY secuencia", rs -> eventos.add(new EventoHistorial(
                rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), Instant.parse(rs.getString(5)), recursos.getOrDefault(rs.getLong(1), Set.of()))));
        return List.copyOf(eventos);
    }
}
