package ar.edu.unlam.reparabarrio.interfaz;
import ar.edu.unlam.reparabarrio.simulacion.MotorSimulacion;
import ar.edu.unlam.reparabarrio.metricas.EstadisticasEscenario;
public interface VistaSimulacion {
    void estado(boolean iniciar,String texto);
    void mostrar(MotorSimulacion.Progreso progreso,EstadisticasEscenario estadisticas,MotorSimulacion.Resultado resultado);
    void diagnostico(String texto);
}
