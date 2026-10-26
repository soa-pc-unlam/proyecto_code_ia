package ar.edu.unlam.reparabarrio.aplicacion;

import ar.edu.unlam.reparabarrio.aplicacion.puertos.RelojIntervalos;
import java.util.*;

/** Bitácora confinada al coordinador. Sin E/S ni callbacks de observadores. */
public final class HechosConfirmados {
    public record Hecho(String contexto, long revision, EventoHistorial evento,
                        DescriptorOperacion descriptor, long nanos) { }
    public record Captura(InstantaneaTaller taller, List<Hecho> hechos, long nanos, String incompleta) {
        public Captura { hechos = List.copyOf(hechos); }
    }
    private final RelojIntervalos reloj;
    private final List<Hecho> hechos = new ArrayList<>();
    private String incompleta;
    HechosConfirmados(RelojIntervalos reloj) { this.reloj = Objects.requireNonNull(reloj); }
    void confirmar(String contexto, long revision, EventoHistorial evento, DescriptorOperacion descriptor) {
        try {
            long ahora = reloj.ahora();
            if (!hechos.isEmpty() && ahora - hechos.getLast().nanos() < 0)
                throw new IllegalStateException("Reloj de intervalos retrocedió");
            hechos.add(new Hecho(contexto, revision, evento, descriptor, ahora));
        } catch (RuntimeException fallo) {
            incompleta = "Evidencia temporal incompleta después de commit conocido: " + fallo;
        }
    }
    Captura capturar(InstantaneaTaller taller) {
        return new Captura(taller, hechos, reloj.ahora(), incompleta);
    }
}
