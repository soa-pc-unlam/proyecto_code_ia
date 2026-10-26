# P03-H3 | Implementación del hito H3 | ReparaBarrio

| Control documental | Valor |
|---|---|
| Versión | 0.1 |
| Fecha | 13/10/2026 |
| Estado | APROBADO EN CONTENIDO POR REVISIÓN HUMANA. NO EJECUTADO. EJECUCIÓN PENDIENTE DE AUTORIZACIÓN EXPRESA |
| Metodología | Spec-Driven Development (SDD), fase 3, implementación incremental |
| Destinatario | Codex CLI, desde la raíz local de `APO1-ReparaBarrio` |
| Hito autorizado, sólo después de una autorización de ejecución separada | H3: concurrencia real sobre el núcleo H2 |
| Diseño detallado aprobado | `IMPLEM-H3.md` v0.2, con constancia en `docs/APROBACION-DISENO-H3.md` |

## VAMOS A IMPLEMENTAR H3

Implementá exclusivamente el **hito H3 de ReparaBarrio**: demostrar concurrencia real sobre el núcleo ya implementado de dominio, planificación y persistencia H2/JDBC, conservando sus reglas y garantías. No estás autorizado a implementar otros hitos ni el MVP completo en esta ejecución.

**Antes de modificar archivos**, leé íntegramente `SPEC.md`, `PLAN.md`, `IMPLEM-H3.md`, `AGENTS.md` si existe, `prompts/P03-implementacion.md` y las constancias de aprobación y auditoría del diseño H3. Revisá también el código y las pruebas H1/H2 pertinentes. `SPEC.md` define requisitos; `PLAN.md`, decisiones y alcance por hito; `IMPLEM-H3.md` v0.2 es la propuesta de diseño detallado aprobada para esta implementación. El P03 principal aporta las reglas generales de la fase; sus instrucciones particulares históricas de H1 no amplían ni reemplazan el alcance de H3.

Implementá **siguiendo ese diseño aprobado**, incluyendo las ratificaciones H3-D03 y H3-D04. Conservá las partes existentes que ya cumplen su contrato. Elegí los detalles de programación y organización interna que el diseño deje abiertos, justificando cualquier desviación relevante. **No cambies silenciosamente** requisitos, contratos, semántica de prioridad, decisiones aprobadas o tecnologías. Si encontrás una incompatibilidad material que necesite alterar `SPEC.md`, `PLAN.md` o `IMPLEM-H3.md`, detenete, explicala y solicitá revisión humana antes de continuar.

## Resultado esperado

- Código y pruebas JUnit 5 indispensables para acreditar las propiedades concurrentes de H3 sobre **la base H2 de archivo real**, no sobre una sustitución ficticia en memoria.
- Cumplimiento de los escenarios **imprescindibles** y verificaciones de la sección 5.2 de `IMPLEM-H3.md` v0.2, con especial atención a CA-07, CA-08, CA-09, CA-13, CA-14, consultas coherentes y control de invariantes. Demostrá reparaciones efectivamente superpuestas, no sólo hilos creados.
- Verificación de las decisiones H3-D03/H3-D04, con instrumentación mínima, opcional e inocua cuando corresponda, y preservación de los mecanismos transaccionales aprobados.
- Conservación y ejecución de la regresión H1/H2 (34 pruebas aprobadas como línea base histórica). No atribuyas a esta ejecución pruebas que no hayas ejecutado efectivamente.
- Tratamiento explícito de las observaciones no bloqueantes del documento de auditoría del diseño. Para T07 y T12, respetá su carácter complementario e informá si se ejecutaron o quedaron pendientes.

## Límites de intervención

Usá como guía de archivos, responsabilidades y secuencia la sección 3 y la sección 7 de `IMPLEM-H3.md`. Modificá producción sólo en la medida aprobada o para corregir defectos de concurrencia demostrados, explicando su causa y comprobación. No introduzcas una segunda fuente de verdad, no prolongues el lock durante el trabajo simulado y no sustituyas prioridades ni propuestas exactas por otras políticas.

**No implementes H4** (recuperación entre procesos, guardia y reconciliación durable), **H5** (interfaz Swing), **H6** (motor y estadísticas de simulación), ni H7/H8. No agregues frameworks o dependencias sin necesidad justificada y aprobación. No modifiques `SPEC.md`, `PLAN.md`, `IMPLEM-H3.md`, documentos de aprobación, prompts ni auditorías para hacerlos coincidir con el código.

No ejecutes `git add`, `git commit`, `git push`, configuraciones de remotos ni operaciones sobre el repositorio GitHub del grupo. Trabajá únicamente en el proyecto local y conservá los archivos ajenos a H3.

## Verificación y cierre de la ejecución

Ejecutá las pruebas específicas y la regresión completa Maven que permita el entorno. Guardá y comunicá el **comando exacto**, resultado, cantidad de pruebas y errores reales. Si el entorno de Codex impide compilar o ejecutar algo, informalo como **NO VERIFICADO** y no lo declares aprobado: la ejecución externa se hará posteriormente en la terminal local del usuario.

Al terminar, informá brevemente:

1. Archivos creados y modificados, con su finalidad y cualquier desviación respecto de `IMPLEM-H3.md`.
2. Cobertura obtenida por criterio/ensayo imprescindible; pruebas complementarias ejecutadas u omitidas.
3. Evidencia reproducible: comandos, resultados, fallos y limitaciones, incluidos tiempos de espera/posesión medidos sólo si hubo instrumentación activa.
4. Defectos detectados/corregidos y pendientes, especialmente los que impedirían aprobar H3.
5. Confirmación de que no avanzaste a otros hitos ni realizaste commits.

**Detenete al finalizar H3.** El código resultante queda pendiente de revisión, auditoría y aprobación humana; esta ejecución no autoriza avanzar a H4.
