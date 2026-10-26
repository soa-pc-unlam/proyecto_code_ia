package ar.edu.unlam.reparabarrio.arranque;

import java.nio.file.Path;
import java.util.Objects;

/** Política explícita de raíz; no concede consentimiento para crear sesiones. */
public record ContextoEjecucion(Path raiz, Modo modo) {
    public enum Modo { DESARROLLO, PORTABLE }
    public ContextoEjecucion {
        Objects.requireNonNull(modo);
    }
    public static ContextoEjecucion desarrollo(Path raiz) {
        return new ContextoEjecucion(raiz, Modo.DESARROLLO);
    }
}
