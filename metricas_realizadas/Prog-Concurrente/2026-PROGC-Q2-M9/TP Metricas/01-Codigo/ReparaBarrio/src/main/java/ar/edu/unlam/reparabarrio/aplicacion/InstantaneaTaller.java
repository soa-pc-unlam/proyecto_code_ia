package ar.edu.unlam.reparabarrio.aplicacion;

import ar.edu.unlam.reparabarrio.dominio.*;
import ar.edu.unlam.reparabarrio.planificacion.Planificador;
import java.util.*;

public record InstantaneaTaller(long revision, InstantaneaDominio dominio,
                               Set<TipoHerramienta> catalogo, List<EventoHistorial> historial,
                               Planificador.Resultado planificacion, long revisionRecuperacion,
                               Map<String, ar.edu.unlam.reparabarrio.aplicacion.puertos.RepositorioRecuperacion.Revision> revisiones) {
    public InstantaneaTaller(long revision, InstantaneaDominio dominio, Set<TipoHerramienta> catalogo,
                            List<EventoHistorial> historial, Planificador.Resultado planificacion) {
        this(revision, dominio, catalogo, historial, planificacion, 0, Map.of());
    }
    public InstantaneaTaller { catalogo = Set.copyOf(catalogo); historial = List.copyOf(historial); revisiones = Map.copyOf(revisiones); }
}
