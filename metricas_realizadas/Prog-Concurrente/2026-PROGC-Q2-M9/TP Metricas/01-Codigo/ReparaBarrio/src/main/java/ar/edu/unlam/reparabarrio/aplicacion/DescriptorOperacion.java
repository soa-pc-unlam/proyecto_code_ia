package ar.edu.unlam.reparabarrio.aplicacion;

import ar.edu.unlam.reparabarrio.dominio.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Descriptor exacto versionado, sin serialización Java ni posibilidad de reenviar comandos. */
public record DescriptorOperacion(String accion, String entidad, String descripcion, Set<TipoHerramienta> requisitos,
                                  Recurso.Categoria categoria, TipoHerramienta tipo, Boolean habilitado,
                                  Set<Recurso.Clave> recursos) {
    public DescriptorOperacion {
        Objects.requireNonNull(accion); requisitos = Set.copyOf(requisitos); recursos = Set.copyOf(recursos);
    }
    public DescriptorOperacion conRecursos(Set<Recurso.Clave> unidades) {
        return new DescriptorOperacion(accion, entidad, descripcion, requisitos, categoria, tipo, habilitado, unidades);
    }
    private static String campo(String s) {
        return s == null ? "~" : Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }
    private static String valor(String s) {
        return s.equals("~") ? null : new String(Base64.getUrlDecoder().decode(s), StandardCharsets.UTF_8);
    }
    public String codificar() {
        var campos = new ArrayList<>(List.of("1", campo(accion), campo(entidad), campo(descripcion),
                campo(categoria == null ? null : categoria.name()), campo(tipo == null ? null : tipo.name()),
                campo(habilitado == null ? null : habilitado.toString()),
                campo(String.join(",", requisitos.stream().map(Enum::name).sorted().toList()))));
        recursos.stream().sorted(Comparator.comparing((Recurso.Clave c) -> c.categoria().name()).thenComparing(Recurso.Clave::id))
                .forEach(c -> { campos.add(campo(c.categoria().name())); campos.add(campo(c.id())); });
        return String.join(".", campos);
    }
    public static DescriptorOperacion decodificar(String texto) {
        String[] c = texto.split("\\.", -1);
        if (c.length < 8 || !c[0].equals("1") || c.length % 2 != 0) throw new IllegalArgumentException("Descriptor incompatible");
        var tipos = EnumSet.noneOf(TipoHerramienta.class);
        String req = valor(c[7]); if (!req.isEmpty()) for (String t : req.split(",")) tipos.add(TipoHerramienta.valueOf(t));
        var recursos = new HashSet<Recurso.Clave>();
        for (int i = 8; i < c.length; i += 2)
            if (!recursos.add(new Recurso.Clave(Recurso.Categoria.valueOf(valor(c[i])), valor(c[i + 1]))))
                throw new IllegalArgumentException("Descriptor con recurso duplicado");
        String cat = valor(c[4]), tipo = valor(c[5]), hab = valor(c[6]);
        if (hab != null && !Set.of("true", "false").contains(hab)) throw new IllegalArgumentException("Habilitación inválida");
        var d = new DescriptorOperacion(valor(c[1]), valor(c[2]), valor(c[3]), tipos,
                cat == null ? null : Recurso.Categoria.valueOf(cat), tipo == null ? null : TipoHerramienta.valueOf(tipo),
                hab == null ? null : Boolean.valueOf(hab), recursos);
        if (!d.codificar().equals(texto)) throw new IllegalArgumentException("Descriptor no canónico");
        return d;
    }
}
