#ifndef METRICS_H
#define METRICS_H

// API pública
void startMetrics();
void pauseMetrics();
void resumeMetrics();
void stopMetrics();

// Opcional: pedir muestreo inmediato sin esperar el período
void printPerCoreTaskStats();

#endif // METRICS_H
