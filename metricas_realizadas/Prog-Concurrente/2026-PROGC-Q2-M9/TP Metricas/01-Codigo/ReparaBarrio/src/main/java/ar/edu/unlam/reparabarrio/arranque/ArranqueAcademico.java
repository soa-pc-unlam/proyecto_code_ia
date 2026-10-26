package ar.edu.unlam.reparabarrio.arranque;

import ar.edu.unlam.reparabarrio.aplicacion.ContextoTaller;
import java.nio.file.Path;
import java.util.UUID;

public final class ArranqueAcademico {
    public record Sesion(ContextoTaller contexto, Path directorio) { }
    private ArranqueAcademico() { }
    public static Sesion crear(Path raiz, boolean consentimiento) throws Exception {
        return crear(ContextoEjecucion.desarrollo(raiz), consentimiento);
    }
    public static Sesion crear(ContextoEjecucion ejecucion, boolean consentimiento) throws Exception {
        String nombre = "h5-" + UUID.randomUUID();
        var contexto = FabricaContextos.crearAcademico(ejecucion, nombre, consentimiento);
        return new Sesion(contexto, FabricaContextos.validarRaiz(ejecucion).resolve("target").resolve(nombre));
    }
}
