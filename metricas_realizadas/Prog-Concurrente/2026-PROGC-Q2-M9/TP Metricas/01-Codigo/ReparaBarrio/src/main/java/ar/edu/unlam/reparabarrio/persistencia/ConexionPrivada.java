package ar.edu.unlam.reparabarrio.persistencia;

import ar.edu.unlam.reparabarrio.aplicacion.puertos.UnidadTrabajo;
import java.sql.*;

/** También el arranque conserva el handle si close no acredita terminación. */
final class ConexionPrivada implements AutoCloseable {
    final Connection conexion;
    ConexionPrivada(Connection conexion) { this.conexion = conexion; }
    @Override public void close() throws Exception {
        try { conexion.close(); }
        catch (SQLException e) {
            boolean terminada;
            try { terminada = conexion.isClosed(); } catch (SQLException consulta) { e.addSuppressed(consulta); terminada = false; }
            if (!terminada) throw new UnidadTrabajo.AperturaIncompleta(new UnidadTrabajoJdbc(conexion), e);
            throw e;
        }
    }
}
