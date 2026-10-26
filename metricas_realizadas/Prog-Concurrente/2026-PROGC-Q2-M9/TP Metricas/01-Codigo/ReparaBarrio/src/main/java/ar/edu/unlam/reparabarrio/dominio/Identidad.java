package ar.edu.unlam.reparabarrio.dominio;

public final class Identidad {
    private Identidad() { }
    public static String validar(String id) {
        if (id == null || id.isBlank() || !id.equals(id.strip()))
            throw new IllegalArgumentException("ID obligatorio, sin espacios externos");
        return id;
    }
}
