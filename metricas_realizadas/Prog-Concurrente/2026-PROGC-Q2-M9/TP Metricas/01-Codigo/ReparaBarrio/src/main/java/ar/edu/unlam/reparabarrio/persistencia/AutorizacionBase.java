package ar.edu.unlam.reparabarrio.persistencia;

import java.nio.file.Path;
import java.util.Objects;

/** Consentimiento específico: ruta/bytes, alcance y destino; nunca se concede por defecto. */
public record AutorizacionBase(String id, Path archivoBase, String huella, int origen, int destino,
                               Path preservacion, boolean recuperacionFisica, boolean reanudacion,
                               String declaracion) {
    public AutorizacionBase {
        Objects.requireNonNull(id); Objects.requireNonNull(archivoBase); Objects.requireNonNull(huella);
        if (id.isBlank() || declaracion == null || declaracion.isBlank()) throw new IllegalArgumentException("Autorización explícita requerida");
        if (!id.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("ID de autorización inválido");
    }
}
