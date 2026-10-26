package ar.edu.unlam.reparabarrio.aplicacion.puertos;

/** Una instancia privada por operación; nunca se comparte entre hilos. */
public interface UnidadTrabajo extends AutoCloseable {
    /** La apertura no pudo configurar/cerrar su conexión: el propietario debe retenerla. */
    final class AperturaIncompleta extends Exception {
        private final UnidadTrabajo residual;
        public AperturaIncompleta(UnidadTrabajo residual, Exception causa) {
            super("Conexión parcialmente abierta no terminada", causa); this.residual = java.util.Objects.requireNonNull(residual);
        }
        public UnidadTrabajo residual() { return residual; }
    }
    RepositorioTaller repositorio();
    RepositorioRecuperacion recuperacion();
    /** Un cierre que lanza no acredita terminación salvo confirmación del adaptador. */
    default boolean terminada() { return false; }
    void commit() throws Exception;
    void rollback() throws Exception;
    @Override void close() throws Exception;
    @FunctionalInterface interface Fabrica { UnidadTrabajo abrir() throws Exception; }
}
