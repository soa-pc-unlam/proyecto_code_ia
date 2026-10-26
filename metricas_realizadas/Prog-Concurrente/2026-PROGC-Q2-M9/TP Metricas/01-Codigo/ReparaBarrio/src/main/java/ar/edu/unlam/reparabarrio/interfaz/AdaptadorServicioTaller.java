package ar.edu.unlam.reparabarrio.interfaz;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import java.util.Set;

public final class AdaptadorServicioTaller implements PuertoTaller {
    private final ContextoTaller contexto;
    public AdaptadorServicioTaller(ContextoTaller contexto) { this.contexto = contexto; }
    public String identidad() { return contexto.identidad(); }
    public ResultadoOperacion<InstantaneaTaller> consultar() { return contexto.servicio().consultar(); }
    public ResultadoOperacion<?> registrarOrden(String id, String d, Set<TipoHerramienta> t) { return contexto.servicio().registrarOrden(id,d,t); }
    public ResultadoOperacion<?> registrarRecurso(Recurso.Categoria c,String id,TipoHerramienta t,boolean h) { return contexto.servicio().registrarRecurso(c,id,t,h); }
    public ResultadoOperacion<?> confirmarInicio(PropuestaInicio p) { return contexto.servicio().confirmarInicio(p); }
    public ResultadoOperacion<?> finalizar(String id) { return contexto.servicio().finalizar(id); }
    public ResultadoOperacion<?> fallar(String id) { return contexto.servicio().fallar(id); }
    public ResultadoOperacion<?> cancelar(String id) { return contexto.servicio().cancelar(id); }
    public ResultadoOperacion<?> cambiarHabilitacion(Recurso.Clave c,boolean h) { return contexto.servicio().cambiarHabilitacion(c,h); }
    public InformeRecuperacion informe() { return contexto.informe(); }
    public ContextoTaller.Ciclo ciclo() { return contexto.ciclo(); }
    public void cerrar() throws Exception { contexto.close(); }
}
