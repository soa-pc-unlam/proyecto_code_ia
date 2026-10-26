package ar.edu.unlam.reparabarrio.dominio;

public enum EstadoOrden {
    PENDIENTE, EN_CURSO, FINALIZADA, FALLIDA, CANCELADA;

    public boolean permite(EstadoOrden destino) {
        return this == PENDIENTE && (destino == EN_CURSO || destino == CANCELADA)
                || this == EN_CURSO && (destino == FINALIZADA || destino == FALLIDA);
    }
}
