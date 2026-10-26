package ar.edu.unlam.reparabarrio.aplicacion.puertos;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import java.util.*;

/** Metadatos en la misma conexión; ningún método confirma por su cuenta. */
public interface RepositorioRecuperacion {
    record Metadata(String baseId, long fronteraV1, long revisionTecnica, boolean adopcionPendiente) { }
    record Sesion(String id, boolean abierta) { }
    record Intento(String id, String baseId, String sesionId, String clase, DescriptorOperacion descriptor,
                   long revisionBase, String resolucion, Long evento, String diagnostico) { }
    record Revision(String ordenId, String sesionId, String motivo, String momento, String cierreId) { }
    record Datos(Metadata metadata, List<Sesion> sesiones, List<Intento> intentos,
                 Map<String, Revision> revisiones, long antiguedad, List<String> hallazgos) {
        public Datos {
            sesiones = List.copyOf(sesiones); intentos = List.copyOf(intentos);
            revisiones = Map.copyOf(revisiones); hallazgos = List.copyOf(hallazgos);
        }
    }
    Datos leer() throws Exception;
    void preparar(Intento intento) throws Exception;
    void resolver(String id, String resolucion, Long evento, String diagnostico) throws Exception;
    void diagnosticar(String id, String diagnostico) throws Exception;
    void abrirSesion(String id) throws Exception;
    void limpiarSesion(String id) throws Exception;
    void revisar(String orden, String sesion, String motivo) throws Exception;
    void resolverRevision(String orden, String cierreId) throws Exception;
    void aceptarAdopcion(String referencia, String declaracion) throws Exception;
}
