package ar.edu.unlam.reparabarrio.planificacion;

import ar.edu.unlam.reparabarrio.dominio.Identidad;
import java.math.BigInteger;
import java.util.Comparator;

/** PT-04: clave global (prefijo, presencia, valor, texto), ordinal y sin locale. */
public final class OrdenNaturalId implements Comparator<String> {
    private record Clave(String prefijo, boolean sufijo, BigInteger valor, String texto) { }
    private static Clave clave(String id) {
        Identidad.validar(id);
        int inicio = id.length();
        while (inicio > 0 && id.charAt(inicio - 1) >= '0' && id.charAt(inicio - 1) <= '9') inicio--;
        return new Clave(id.substring(0, inicio), inicio < id.length(),
                inicio < id.length() ? new BigInteger(id.substring(inicio)) : BigInteger.ZERO, id);
    }
    @Override public int compare(String izquierda, String derecha) {
        var a = clave(izquierda); var b = clave(derecha);
        int c = a.prefijo.compareTo(b.prefijo);
        if (c == 0) c = Boolean.compare(a.sufijo, b.sufijo);
        if (c == 0) c = a.valor.compareTo(b.valor);
        if (c == 0) c = a.texto.compareTo(b.texto);
        return c;
    }
}
