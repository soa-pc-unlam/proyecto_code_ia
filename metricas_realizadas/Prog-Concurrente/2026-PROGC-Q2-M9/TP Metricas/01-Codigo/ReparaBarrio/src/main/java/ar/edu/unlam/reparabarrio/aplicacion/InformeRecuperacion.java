package ar.edu.unlam.reparabarrio.aplicacion;

import ar.edu.unlam.reparabarrio.aplicacion.puertos.RepositorioRecuperacion.Revision;
import java.util.*;

public record InformeRecuperacion(String baseId, ContextoTaller.Ciclo ciclo, String sesionId,
                                  Map<String, String> operaciones, Map<String, Revision> revisiones,
                                  List<String> hallazgos, String referencia, long revision) {
    public InformeRecuperacion {
        operaciones = Map.copyOf(operaciones); revisiones = Map.copyOf(revisiones); hallazgos = List.copyOf(hallazgos);
    }
}
