package ar.edu.unlam.reparabarrio.aplicacion.puertos;

import ar.edu.unlam.reparabarrio.aplicacion.EventoHistorial;
import ar.edu.unlam.reparabarrio.dominio.*;
import java.util.*;

/** Capacidad de estrategia B: no declara ninguna escritura ni commit. */
public interface RepositorioLectura {
    InstantaneaDominio cargar(String contexto) throws Exception;
    Set<TipoHerramienta> catalogo() throws Exception;
    List<EventoHistorial> historial() throws Exception;
    long revision() throws Exception;
}
