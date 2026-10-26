package ar.edu.unlam.reparabarrio.dominio;

import java.util.Objects;

/** La clave incluye categoría: un técnico y un puesto pueden tener el mismo ID. */
public record Recurso(Clave clave, TipoHerramienta tipo, boolean habilitado) {
    public enum Categoria { TECNICO, PUESTO, HERRAMIENTA }
    public enum Disponibilidad { DISPONIBLE, OCUPADO, FUERA_DE_SERVICIO }
    public record Clave(Categoria categoria, String id) {
        public Clave { Objects.requireNonNull(categoria); Identidad.validar(id); }
    }
    public Recurso {
        Objects.requireNonNull(clave);
        if ((clave.categoria() == Categoria.HERRAMIENTA) != (tipo != null))
            throw new IllegalArgumentException("Tipo requerido exclusivamente para herramienta");
    }
}
