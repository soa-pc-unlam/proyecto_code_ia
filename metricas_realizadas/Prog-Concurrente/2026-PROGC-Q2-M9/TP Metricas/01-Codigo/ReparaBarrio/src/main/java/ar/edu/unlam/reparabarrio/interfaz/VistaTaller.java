package ar.edu.unlam.reparabarrio.interfaz;

import ar.edu.unlam.reparabarrio.aplicacion.InstantaneaTaller;

/** Todos los callbacks se realizan exclusivamente en EDT. */
public interface VistaTaller {
    void captura(InstantaneaTaller captura);
    void estado(boolean acciones, String actividad, boolean vigente);
    void mensaje(String mensaje);
    void diagnostico(String diagnostico);
    boolean confirmar(String titulo, String detalle);
    void cerrado();
    default void advertenciaCierre(String texto) { mensaje(texto); }
}
