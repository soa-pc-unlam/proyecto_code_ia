# ReparaBarrio - Instrucciones para Codex

## Alcance
- Trabajar exclusivamente dentro de este proyecto local.
- No modificar archivos de otros proyectos.
- No configurar repositorios remotos de Git.
- No ejecutar git push, pull, fetch ni publicar contenido.
- No crear pull requests ni realizar operaciones en GitHub.
- No realizar acciones externas sin autorizacion explicita.

## Metodologia
- Aplicar Spec-Driven Development (SDD).
- Respetar las fases: Especificacion, Planificacion, Implementacion y Validacion.
- No generar codigo durante la fase de especificacion.
- No implementar funcionalidades fuera del alcance aprobado.
- Conservar la trazabilidad de los prompts y refinamientos.

## Tecnologia
- Lenguaje principal: Java.
- Construccion: Maven.
- Pruebas: JUnit.
- Evitar dependencias innecesarias.

## Calidad
- Priorizar la correctitud de la concurrencia.
- Documentar mecanismos de sincronizacion y recursos compartidos.
- Identificar riesgos de race conditions, deadlocks y starvation.
- No afirmar que una prueba fue exitosa sin haberla ejecutado.
- Informar archivos modificados, resultados y limitaciones.

## Documentacion
- Respetar las especificaciones aprobadas.
- No modificar las consignas y rubricas originales.
- Registrar las decisiones relevantes.
- No inventar metricas ni resultados de pruebas.
