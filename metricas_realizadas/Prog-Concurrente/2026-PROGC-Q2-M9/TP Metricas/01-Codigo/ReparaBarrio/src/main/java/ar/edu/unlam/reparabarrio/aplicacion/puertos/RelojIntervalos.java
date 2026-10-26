package ar.edu.unlam.reparabarrio.aplicacion.puertos;

/** Sólo diferencias dentro de una misma vida de contexto; nunca fecha civil. */
@FunctionalInterface
public interface RelojIntervalos { long ahora(); }
