package ar.edu.unlam.reparabarrio.simulacion;

import ar.edu.unlam.reparabarrio.dominio.*;
import java.util.*;

public record CargaEscenario(String version, ConfiguracionEscenario configuracion, List<EntradaOrden> ordenes) {
    public record EntradaOrden(String id,String descripcion,Set<TipoHerramienta> requisitos,long llegadaMs,long duracionMs,boolean falla) {
        public EntradaOrden {var canonicos=EnumSet.noneOf(TipoHerramienta.class);canonicos.addAll(requisitos);requisitos=Collections.unmodifiableSet(canonicos);}
    }
    public CargaEscenario {ordenes=List.copyOf(ordenes);}
}
