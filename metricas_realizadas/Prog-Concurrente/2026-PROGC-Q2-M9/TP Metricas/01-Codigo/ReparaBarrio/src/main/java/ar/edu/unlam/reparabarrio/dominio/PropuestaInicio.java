package ar.edu.unlam.reparabarrio.dominio;

import java.util.Objects;

/** Lectura inmutable SIN RESERVA y sin vencimiento. */
public record PropuestaInicio(String contexto, String ordenId, SeleccionRecursos recursos) {
    public PropuestaInicio { Identidad.validar(contexto); Identidad.validar(ordenId); Objects.requireNonNull(recursos); }
}
