package ar.edu.unlam.reparabarrio.aplicacion.puertos;

import ar.edu.unlam.reparabarrio.aplicacion.*;
import ar.edu.unlam.reparabarrio.dominio.*;
import java.time.Instant;
import java.util.*;

/** Puerto interno de aplicación: los clientes reciben sólo ServicioTaller y valores. */
public interface RepositorioTaller extends RepositorioLectura {
    long siguienteSecuencia() throws Exception;
    void registrar(Orden orden) throws Exception;
    void registrar(Recurso recurso) throws Exception;
    void habilitar(Recurso.Clave clave, boolean habilitado) throws Exception;
    void estado(String ordenId, EstadoOrden estado) throws Exception;
    void asignar(String ordenId, SeleccionRecursos recursos) throws Exception;
    void liberar(String ordenId) throws Exception;
    void evento(String operacionId, String ordenId, String accion, Instant momento,
                Set<Recurso.Clave> recursos) throws Exception;
}
