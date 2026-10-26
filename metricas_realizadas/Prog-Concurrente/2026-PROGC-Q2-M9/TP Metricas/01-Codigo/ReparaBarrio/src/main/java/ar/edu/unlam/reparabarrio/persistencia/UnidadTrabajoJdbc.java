package ar.edu.unlam.reparabarrio.persistencia;

import ar.edu.unlam.reparabarrio.aplicacion.puertos.*;
import java.sql.*;

final class UnidadTrabajoJdbc implements UnidadTrabajo {
    private final Connection conexion;
    private final RepositorioTaller repositorio;
    UnidadTrabajoJdbc(Connection residual) {
        conexion = residual; repositorio = new RepositorioTallerJdbc(residual);
    }
    UnidadTrabajoJdbc(String url) throws Exception {
        var nueva = DriverManager.getConnection(url, "sa", "");
        conexion = nueva;
        repositorio = new RepositorioTallerJdbc(conexion);
        try {
            nueva.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
            nueva.setAutoCommit(false);
        } catch (SQLException e) {
            try { nueva.close(); } catch (SQLException cierre) { e.addSuppressed(cierre); }
            if (!terminada()) throw new UnidadTrabajo.AperturaIncompleta(this, e);
            throw e;
        }
    }
    @Override public RepositorioTaller repositorio() { return repositorio; }
    @Override public RepositorioRecuperacion recuperacion() { return new RepositorioRecuperacionJdbc(conexion); }
    @Override public boolean terminada() {
        try { return conexion.isClosed(); } catch (SQLException e) { return false; }
    }
    @Override public void commit() throws SQLException { conexion.commit(); }
    @Override public void rollback() throws SQLException { conexion.rollback(); }
    @Override public void close() throws SQLException { conexion.close(); }
}
