package ar.edu.unlam.reparabarrio.metricas;
import ar.edu.unlam.reparabarrio.dominio.*;
import java.util.*;

/** Tiempos de cambios conocidos después de commit, no duraciones configuradas. */
public record EstadisticasEscenario(long revision,long comienzo,long fin,Map<EstadoOrden,Long> estados,
        long procesadas,Double esperaPromedioMs,Double duracionSegundos,Map<String,Double> esperasMs,
        Map<Recurso.Clave,Utilizacion> utilizacion,String incompleta) {
    public record Utilizacion(long ocupadoNanos,long habilitadoNanos,Double porcentaje) { }
    public EstadisticasEscenario {estados=Map.copyOf(estados);esperasMs=Map.copyOf(esperasMs);utilizacion=Map.copyOf(utilizacion);}
}
