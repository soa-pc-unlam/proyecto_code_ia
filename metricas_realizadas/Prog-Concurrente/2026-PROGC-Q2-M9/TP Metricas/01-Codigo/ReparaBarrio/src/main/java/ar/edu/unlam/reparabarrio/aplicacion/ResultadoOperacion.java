package ar.edu.unlam.reparabarrio.aplicacion;

/** El resultado incierto pertenece a la operación, nunca al estado de una orden. */
public record ResultadoOperacion<T>(Estado estado, String contexto, String operacionId,
                                    T valor, String diagnostico, DiagnosticoTecnico tecnico) {
    public enum Estado { CONFIRMADA, RECHAZADA, SIN_CAMBIO, ERROR_TECNICO, PENDIENTE_VERIFICACION }
    public record DiagnosticoTecnico(String codigo, String fase, String causa, ContextoTaller.Ciclo salud,
                                     boolean diagnosticoDurable, String sqlState) {
        public DiagnosticoTecnico(String codigo, String fase, String causa, ContextoTaller.Ciclo salud, boolean diagnosticoDurable) {
            this(codigo, fase, causa, salud, diagnosticoDurable, null);
        }
    }
    public ResultadoOperacion(Estado estado, String contexto, String operacionId, T valor, String diagnostico) {
        this(estado, contexto, operacionId, valor, diagnostico, null);
    }
}
