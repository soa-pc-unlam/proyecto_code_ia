package ar.edu.unlam.reparabarrio.persistencia;

import ar.edu.unlam.reparabarrio.arranque.GuardiaInstancia;
import java.util.UUID;

/** Capacidad de creación única; no acepta nombres JDBC ni archivos existentes. */
public final class AutorizacionSimulacion {
    private final GuardiaInstancia guardia;
    private final String id = "H6-" + UUID.randomUUID();
    private final String nombre = "rb_h6_" + UUID.randomUUID().toString().replace("-", "");
    private boolean consumida;
    private AutorizacionSimulacion(GuardiaInstancia guardia) { this.guardia = guardia; }
    public static AutorizacionSimulacion nueva(GuardiaInstancia guardia, boolean consentimiento) throws Exception {
        if (!consentimiento || !guardia.vigente() || guardia.baseConocida()
                || !guardia.directorio().getFileName().toString().matches("h6-[0-9a-f-]{36}"))
            throw new IllegalArgumentException("Memoria nueva consentida con guardia exclusiva requerida");
        try (var entradas = java.nio.file.Files.list(guardia.directorio())) {
            if (entradas.anyMatch(p -> !p.getFileName().toString().equals("instancia.lock")))
                throw new IllegalArgumentException("Directorio de simulación no vacío");
        }
        return new AutorizacionSimulacion(guardia);
    }
    synchronized String consumir(GuardiaInstancia solicitante) {
        if (consumida || solicitante != guardia || !guardia.vigente())
            throw new IllegalStateException("Permiso memoria no vigente");
        consumida = true;
        return "jdbc:h2:mem:" + nombre + ";WRITE_DELAY=0;LOCK_TIMEOUT=5000";
    }
    String id() { return id; }
}
