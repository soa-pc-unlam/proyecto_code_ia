package ar.edu.unlam.reparabarrio.interfaz;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import java.util.Set;

/** Capacidad limitada de presentación; no entrega JDBC ni recuperación mutante. */
public interface PuertoTaller {
    String identidad();
    ResultadoOperacion<InstantaneaTaller> consultar();
    ResultadoOperacion<?> registrarOrden(String id, String descripcion, Set<TipoHerramienta> tipos);
    ResultadoOperacion<?> registrarRecurso(Recurso.Categoria categoria, String id, TipoHerramienta tipo, boolean habilitado);
    ResultadoOperacion<?> confirmarInicio(PropuestaInicio propuesta);
    ResultadoOperacion<?> finalizar(String id);
    ResultadoOperacion<?> fallar(String id);
    ResultadoOperacion<?> cancelar(String id);
    ResultadoOperacion<?> cambiarHabilitacion(Recurso.Clave clave, boolean habilitado);
    InformeRecuperacion informe();
    ContextoTaller.Ciclo ciclo();
    void cerrar() throws Exception;
}
