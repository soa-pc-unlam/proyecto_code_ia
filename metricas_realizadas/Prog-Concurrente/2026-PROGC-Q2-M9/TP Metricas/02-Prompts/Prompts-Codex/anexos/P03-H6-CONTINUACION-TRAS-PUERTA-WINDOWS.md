# P03-H6 — Continuación de la implementación integral tras validación externa Windows

| Control documental | Valor |
|---|---|
| Fecha | 15/10/2026 |
| Versión | **0.1 — APROBADA, 15/10/2026** |
| Tipo | Instrucción suplementaria de continuidad SDD, **sin modificar el diseño ni ampliar el alcance autorizado** |
| Base | `prompts/anexos/P03-H6-implementacion.md` v0.1, **ya aprobado**; `IMPLEM-H6.md` v0.1; `docs/APROBACION-DISENO-H6.md` y auditoría H6 |
| Antecedentes | `H6-REPORTE-IMPLEMENTACION.md` (primera ejecución Codex) y `docs/H6-VALIDACION-EXTERNA-FASE1-2026-10-15.md` (verificación humana externa) |
| Estado del hito | H6 **parcial**, pendiente de generador, motor, estadísticas, Swing y aceptación externa |
| Uso | **Autorizada la continuación integral de H6 con Codex**, después de incorporar el anexo aprobado y registrar la evidencia externa en el proyecto local |

## 1. Mandato y precedencia

**Reanudar la implementación integral de H6 desde el código ya producido**, conforme a la autorización humana original `P03-H6-implementacion.md`, no reiniciar el proyecto. Esta instrucción documenta una evidencia posterior y el punto desde donde continuar; **no** autoriza cambios nuevos de alcance, rediseño de H4/H5, ni H7/H8.

Antes de modificar código, leer completamente `AGENTS.md`, `SPEC.md`, `PLAN.md`, `IMPLEM-H6.md`, `prompts/anexos/P03-H6-implementacion.md`, `docs/APROBACION-DISENO-H6.md`, la auditoría H6, `H6-REPORTE-IMPLEMENTACION.md`, el acta humana de validación externa H6 y el código actualmente presente. Ante contradicción prevalecen especificación y decisiones humanas ya aprobadas. Preservar cambios existentes, archivos de usuario y telemetría original. **No realizar operaciones Git, ni siquiera de lectura, si esa limitación operativa se mantiene establecida para esta ejecución.**

## 2. Hecho nuevo que permite la continuación

El responsable ejecutó desde PowerShell/IntelliJ en Windows la selección:

```text
ContextoSimulacionTest,RecorridoAcademicoNominalTest,CicloContextoTest,
GuardiaInstanciaTest,ArranqueAcademicoTest,IntegracionInterfazH5Test,
TransaccionesH2Test
```

Salida documental local `metricas/logs/h6/validacion-externa-H6-Windows.txt`:

```text
Tests run: 34, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
EXIT VALIDACION EXTERNA H6: 0
```

Las cinco pruebas de `ContextoSimulacionTest`, incluida la que había fallado dentro de Codex, **pasaron externamente con H2 real**, al igual que las regresiones focales de esta puerta. Por tanto, **la puerta temprana se considera acreditada externamente en su alcance definido** y **no se debe seguir bloqueando toda la implementación sólo porque reaparezca `90097-232` en el entorno de Codex sobre fixtures de archivo**, siempre que el problema sea el mismo y exista esta evidencia externa conservada. No afirmar causa raíz ni atribución ambiental comprobada.

Esta decisión **no autoriza omitir pruebas nuevas ni ignorar defectos de integridad**. Si aparece una falla nueva o una incompatibilidad material atribuible a H6, detener y documentar según el prompt original.

## 3. Estado de partida: conservar y completar

La primera ejecución dejó implementación inicial de H6-D01 y H6-D02 y detuvo las fases siguientes. Debe conservarse y auditarse esa base, **no reemplazarla ni reescribir de cero**:

- `FabricaContextos`, `AutorizacionSimulacion`, `PersistenciaH2`, `InicializadorBase`, `ContextoTaller`: composición y ciclo H2 en memoria, bajo las restricciones aprobadas.
- `ServicioTaller`, `HechosConfirmados`, `RelojIntervalos`: captura temporal confirmada aditiva, todavía incompleta respecto de los escenarios de inyección de fallas y estadísticas.
- `ContextoSimulacionTest`: cinco pruebas tempranas ya ejecutadas externamente; no reducir sus aserciones.
- `H6-REPORTE-IMPLEMENTACION.md`: documento histórico de primera etapa; preservar copia **antes de actualizar el reporte de implementación**.

No tocar `SPEC`, `PLAN`, `AGENTS`, decisiones ni diseños aprobados. No operar sobre bases preexistentes. El estado H4 continúa **PARCIAL** y el modo operativo H5 sigue vigente.

## 4. Continuación técnica integral

Seguir la secuencia original sin pedir autorizaciones intermedias ordinarias:

1. **Completar H6-D02 y sus pruebas focales restantes:** inyección conjunta de `Clock` civil y reloj monotónico; captura y orden de hechos por commit conocido, rollback, commit incierto, bitácora incompleta, consulta coherente y preservación del resultado de negocio. Completar detalles pendientes H6-D01 que sean necesarios para la utilización segura sin rediseño amplio.
2. **Generador reproducible `H6-G1`:** mismo `seed` + configuración generan idéntica **entrada completa**. No exigir intercalados idénticos de trabajadores; probar N=0, N=100, validaciones, mezcla, llegadas, duraciones y fallos configurados.
3. **Motor realmente concurrente:** trabajadores independientes, operaciones reales de `ServicioTaller`, selección de primera factible, recursos exclusivos, operaciones sin reservas parciales y terminación comprobada por quiescencia/ausencia de progreso. Duraciones y scheduler fuera del coordinador/JDBC; no usar `Thread.sleep` como prueba de solapamiento por sí solo.
4. **Estadísticas funcionales:** cinco estados; procesadas = `FINALIZADA + FALLIDA`; espera de iniciadas; duración del escenario en reloj monotónico; utilización por unidad como ocupación confirmada/intervalo habilitado; `N/D` cuando no hay denominador o evidencia completa. No calcular las métricas académicas de la entrega.
5. **Interfaz Swing:** sustituir el marcador «pendiente de H6» por configuración, ejecución y visualización del simulador; todo JDBC, cómputo y cierre fuera del EDT; contención de respuestas tardías, doble clic y propietarios; proteger el cierre tanto del modo operativo como del simulado.
6. **Verificación integral:** pruebas H6-T01…T12 con evidencia por familia; dos perfiles de **100 órdenes** —suficiente y limitado— si el entorno lo permite; regresiones focales de H1–H5. Documentar precisamente resultados en Windows pendientes de ejecución externa.

No inventar cifras de 100 órdenes si no se ejecutaron. No atribuir pruebas unitarias del planificador al motor H6. No perseguir repetidamente el viejo fallo 90097 en sandbox para obtener un falso verde: cuando reaparezca, registrar el caso y proporcionar comando de validación externa; continuar con pruebas seguras que no requieran ese fixture si los contratos lo permiten, y detenerse ante una duda material de integridad.

## 5. Pruebas, límites de intervención y costos

- La evidencia de 34/34 pruebas externas **no reemplaza** las nuevas pruebas H6 ni autoriza modificar reglas o protecciones. Mantener íntegra la puerta H6 original y ofrecer comando reproducible para repetirla externamente tras cambios sensibles.
- Diferenciar compilación, pruebas unitarias, integración, GUI real y resultados de Windows; `BUILD SUCCESS` de una selección no es aprobación global del hito.
- No borrar ni sobrescribir logs/JSONL preexistentes. Guardar logs adicionales con nombres nuevos bajo `metricas/logs/h6/`.
- No usar `mvn clean` mientras hay contexto/GUI activo, ni tocar directorios de bases H2 activas. No crear ni borrar bases operativas.
- No hacer staging, commit, push, pull, reset, checkout ni otros comandos Git; el responsable realiza el control de versiones.
- Si un defecto **nuevo y material** de integridad exige relajar H4 o rediseñar dependencias fuera del alcance, detenerse y emitir informe. No encadenar múltiples intentos de corrección costosos.
- No ejecutar H7/H8 ni evaluar ahora métricas académicas (tokens/CPU/RAM). Conservar los registros para su cómputo posterior.

## 6. Entregables de continuidad

- Generador, motor, estadísticas, integración Swing y tests H6; reutilización y completitud de D01/D02.
- `H6-REPORTE-IMPLEMENTACION.md` **actualizado**, sin borrar ni falsear el antecedente de bloqueo; incluir cronología «primera ejecución → validación externa 34/34 → continuación», archivos, pruebas, H6-T01…T12, perfiles de 100, límites y comandos de Windows.
- Evidencia nueva identificable en `metricas/logs/h6/` y resultado final resumido en salida de Codex.
- No declarar «H6 aceptado»: su validación y aceptación finales corresponderán al responsable humano.

**ESTADO DE ESTE ANEXO: APROBADO POR EL RESPONSABLE HUMANO (15/10/2026).** Se autoriza reanudar la implementación integral de H6 desde el estado actual, exclusivamente dentro del alcance y las restricciones documentadas. La aceptación final de H6 permanece pendiente de verificación humana.
