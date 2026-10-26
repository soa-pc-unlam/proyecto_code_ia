# P03-H4 | Implementación COMPLETA del hito H4 | ReparaBarrio

| Control documental | Valor |
|---|---|
| Versión | 0.2 — sustitución de la propuesta parcial H4-I1 |
| Fecha | 14/10/2026 |
| Estado | **PROMPT DE IMPLEMENTACIÓN COMPLETA, LISTO PARA REVISIÓN Y EJECUCIÓN POR EL RESPONSABLE; AÚN NO EJECUTADO** |
| Metodología | SDD, fase P03; diseño previo H4 v0.2 aprobado; una ejecución integral con controles técnicos internos |
| Destinatario | Codex CLI, desde la raíz local de `APO1-ReparaBarrio` |
| Fuente técnica de implementación | `IMPLEM-H4.md` **v0.2**, íntegro |
| Ratificación humana | `docs/APROBACION-DISENO-H4.md`: **H4-D01 a H4-D06**, incluida **estrategia B** (§5.5) |
| Alcance | **TODO H4**: guardia, contexto, identidad, esquema v2, migración segura, auditoría, sesiones, intenciones, reconciliación, revisión EN_CURSO y pruebas H4-T01…H4-T18 |
| Entrega | Código Java/SQL, fixtures y pruebas, cambios explicados y evidencias verificables; **no generar otro diseño `IMPLEM-H4.md`** |

## 1. Instrucción principal: implementar H4 entero, no sólo H4-I1

**IMPLEMENTÁ COMPLETAMENTE H4 EN ESTA MISMA EJECUCIÓN.** El responsable sustituyó expresamente el proceso previamente propuesto de cinco ejecuciones con autorizaciones humanas intermedias por **una ejecución integral**. Podés organizar internamente el trabajo en H4-I1 a H4-I5 para gestionar dependencias, compilación y pruebas, **pero NO te detengas al finalizar cada bloque ni pidas nuevas aprobaciones entre ellos**. Esos son controles técnicos internos, no cinco entregas. Tu objetivo es terminar H4 y ofrecer evidencia suficiente para una única auditoría humana final, antes de iniciar H5.

**No confundas el entregable:** `IMPLEM-H4.md` v0.2 YA EXISTE y constituye el diseño aprobado. No lo generes ni lo edites. En esta ejecución debés producir **la implementación real** (Java, SQL, pruebas y auxiliares imprescindibles), no otro documento de diseño.

Antes de cambiar código, leé íntegramente, si están presentes: `AGENTS.md`, `SPEC.md` v0.2, `PLAN.md` v0.2 (especialmente 5.2, 8.3 y 8.7), `IMPLEM-H4.md` v0.2, `docs/APROBACION-DISENO-H4.md`, `docs/Auditoria_Diseno_IMPLEM-H4_v0.2_2026-10-14.md`, `prompts/P03-implementacion.md` y este prompt. Inspeccioná código/SQL/pruebas de H1/H2/H3 y sus cierres. Si falta el diseño aprobado, la constancia de H4-D06 o hay un conflicto normativo **material** que no puedas solucionar respetando lo aprobado, detenete e informá el bloqueo; no lo suplas por una suposición.

La precedencia es: `SPEC.md` y decisiones humanas aprobadas → `PLAN.md` con PT-01…PT-08 y adenda 8.7 → diseño H4 v0.2 y constancia de H4-D01…H4-D06 → este prompt sobre **modalidad de ejecución integral**. Esta instrucción actual cambia la frecuencia de supervisión humana, **no** los contratos técnicos ni las autorizaciones sobre datos operativos.

## 2. Resultado funcional exigido de H4

Implementá lo aprobado en `IMPLEM-H4.md` v0.2 y sus secciones 3–10; la enumeración siguiente es un **índice de cobertura, no una reducción de su contenido**:

1. **Guardia/identidad/propietario:** `FileChannel.tryLock` previo a cualquier apertura operativa H2; ruta de guardia estable, archivo no borrado; una sola instancia entre JVM y propietario por contexto en JVM; composición pública segura, cierre idempotente, referencias viejas inoperantes, protección frente a rutas/alias admitidos; UUID persistente de base independiente de UUID de sesión. No afirmar protección ante manipulación externa hostil.
2. **Esquema e inicialización:** base nueva v2; detección estricta de existente, ilegible, desconocida o intermedia; evolución v1→v2 con metadatos, marcador de migración, pre/postcondiciones, preservación íntegra y reanudación sólo ante evidencias/permiso suficientes. Sin reseed de catálogo, pérdida de eventos, reutilización de IDs o generación de otro UUID al reanudar. No presumir atomicidad del DDL H2.
3. **Autorización y adopción:** diseñar el contrato ejecutable de autorización *específica por base* antes de cualquier escritura sobre base operativa existente, sin conceder permiso por defecto. Preservación verificada y adopción conservadora de v1; aceptación explícita del informe auditado por contrato local, sin agregar todavía Swing/CLI final. **Desarrollar y probar este código sólo con bases descartables de test**; no autorizar ni efectuar una migración real.
4. **Auditoría y sesiones:** auditoría estructural y funcional de tablas completas, invariantes, secuencias, catálogo, asignaciones, ocupación e historial; detectar incoherencias/filas huérfanas sin autocorregir. Sesión durable abierta/limpia, reinicios y cierres controlados, diagnóstico seguro y bloqueo de operaciones normales ante datos no verificados.
5. **Intenciones y commits inciertos:** cada mutación potencialmente efectiva prepara previamente intención durable bajo el coordinador y fuera de la transacción de negocio; no mantener dos transacciones simultáneas. Un **mismo UUID `I`** debe relacionar intención, `ResultadoOperacion.operacionId`, `historial.operacion_id` y resolución atómica cuando haya cambio; preservar efectos completos o anteriores, nunca cambios parciales. Reconciliar desde otra JVM sin repetir comandos, sin inventar rollback, sin confundir fallo de diagnóstico posterior con fracaso del commit ya confirmado.
6. **Estrategia B de consultas (H4-D06):** consultas normales de negocio **estrictamente no mutantes**, sin intención durable por cada lectura; sesión abierta durable, control de rollback/cierre, bloqueo y auditoría ante fallo. El identificador técnico de una lectura puede perderse ante caída inmediata si no se había persistido diagnóstico; no inventarlo después. Las mutaciones, incluso las que puedan resultar SIN_CAMBIO tras validación, **sí** requieren intención previa conforme al diseño.
7. **Revisión humana EN_CURSO:** caída con órdenes abiertas conserva estados EN_CURSO, asignaciones y recursos OCUPADO; marca de revisión humana durable asociada a cada orden, idempotente y sin sexto estado. Sólo `finalizar`/`fallar` válidos resuelven la revisión atómicamente con estado, liberación y evento. No liberar por suposición ni permitir cancelación de EN_CURSO.
8. **Aislamiento y regresión:** conservar un único H2/JDBC como fuente de verdad, la primera orden factible, secuencia/identidades, selección exacta, `ReentrantLock(true)` y toda garantía H1/H2/H3. No añadir arquitectura paralela, catálogo distinto, nuevas tecnologías o UI/simulador de H5/H6.

Los nombres de clases y archivos de la sección 3 del diseño son la guía. Evitá abstracciones no justificadas, pero no omitas contratos obligatorios sólo por simplificar. Si debés tomar una decisión interna menor no especificada, elegí la alternativa conservadora y documentala al final. No cambies unilateralmente principios aprobados.

## 3. Modalidad de ejecución: controles internos, sin cinco idas y vueltas

Organizá tu propia ejecución en esta secuencia orientativa, ajustando el orden cuando haya dependencias técnicas:

- **Preparación:** confirmar que estás en este repositorio local, inspeccionar `git status --short` sin limpiar archivos del usuario y documentar la línea base histórica H1/H2/H3: **71 pruebas aprobadas anteriormente** (no fingir que se ejecutaron ahora). Intentar línea base Maven si el entorno lo permite.
- **Infraestructura:** guardia y propietario/ciclo/identidad, pruebas focales; no habilitar acceso operativo parcialmente protegido.
- **Persistencia y recuperación:** esquema v2, inicialización, migración/preservación/autoridad sobre fixtures, auditoría y sesión durable, pruebas focales.
- **Integración transaccional:** intención mutante, UUID `I`, testigo/resolución, reconciliación, estrategia B, revisión EN_CURSO y manejo coherente de salud/resultados, pruebas focales.
- **Aceptación del código:** auxiliares multiproceso, matriz H4-T01…H4-T18, suite completa, inspección de cambios y reporte de faltantes.

**Continuá automáticamente entre estos pasos dentro de la misma invocación** mientras no haya bloqueo material. Corregí los defectos detectados por tus propias pruebas dentro del alcance H4 y repetí las verificaciones pertinentes. No realices un commit ni pases a H5. Si encontrás una dificultad no bloqueante, documentala y seguí. Ante riesgo de daño a datos operativos o necesidad de cambiar un contrato normativo aprobado, detenete sin intervenir esos datos.

## 4. Seguridad estricta del desarrollo

- **No tocar `datos/operativo` ni ninguna otra base operativa real**: ni abrir JDBC, ni leer datos, ni crear, migrar, copiar, restaurar, borrar, renombrar, reparar o inicializar. Esta orden prohíbe aun la migración aparentemente segura; sólo habilita **escribir el código que eventualmente la realizaría tras una autorización futura separada**.
- Las pruebas, fallos inyectados, corrupción controlada y terminación de JVM auxiliares utilizan **exclusivamente raíces propias de ensayo bajo `target/h4-*`**, cuya pertenencia se valida antes de abrir, copiar o limpiar. Las autorizaciones usadas en tests deben ser explícitas y estar circunscritas al fixture, jamás a una base real. No matar procesos ajenos.
- No sobrescribir un archivo existente sin necesidad, no borrar datos del usuario, no ejecutar comandos Git destructivos y no modificar documentos normativos o aprobados: `SPEC.md`, `PLAN.md`, `IMPLEM-H4.md`, `AGENTS.md`, `DECISIONES.md`, prompts, auditorías y aprobaciones. No generar ni reemplazar `IMPLEM-H4.md`.
- No usar `git add`, `git commit`, `git push`, `git remote add`, ni conexiones al GitHub grupal `https://github.com/UNLAM-PROG-C/2026-PROGC-Q2-M9/`. No crear remotos. Trabajar sólo en el repositorio local y preservar los tres archivos históricos sin seguimiento y cualquier otro cambio previo.
- No incorporar nuevas dependencias/frameworks ni alterar JDK/Maven globalmente. Usar Java 21, Maven, H2 2.3.232, JDBC y JUnit 5. No modificar el POM salvo que haya un impedimento técnico imprescindible y se explique; preferir cero cambios en POM.
- Los callbacks/observadores de H3 no deciden durabilidad, no bloquean bajo el coordinador y no se vuelven necesarios para negocio. No colocar duración física/simulación/GUI bajo el lock. Sin H5 (Swing), H6 (motor), H7 ni H8.

## 5. Pruebas y criterio honesto de finalización

**Objetivo: implementar y probar todos los escenarios imprescindibles H4-T01 a H4-T18 de la sección 9.2 del diseño**, además de preservar las 71 pruebas H1/H2/H3. Los escenarios incluyen realmente: cierre/reapertura con JVM independientes; caída de una JVM auxiliar; segunda instancia rechazada antes de JDBC; inicialización/migración interrumpida en fixtures; commit incierto antes/durante/después; testigo `I` correcto; lectura B y fallo de rollback; revisión EN_CURSO; corrupción/inconsistencia; y aislamiento de contextos. No sustituir estos ensayos por sólo nuevas conexiones en la misma JVM.

- Usá un proceso hijo Java de pruebas controlado (p. ej., `ProcessBuilder`) con PIDs, señales/fronteras identificables, timeouts y limpieza segura; **sin basarte sólo en sleeps para afirmar intercepción durante commit**. Si una ventana física de commit no puede interceptarse determinísticamente, indicá el límite y combiná pruebas de caída real con fallos instrumentados antes/después del delegado. El oráculo de comprobación debe ser independiente de la implementación de recuperación.
- Adaptá fixtures e inyección H2/H3 para la nueva composición protegida sin suprimir casos ni relajar aserciones por conveniencia. Separá los fallos/contadores de fase de negocio frente a infraestructura y recuperación. Mantener exactas las garantías que verifican los tests viejos.
- Ejecutá pruebas focales al implementar y, al terminar, intentá **la regresión completa Maven** con el comando disponible del repositorio (`mvn -B -ntp clean test`, o su wrapper si existe) y JDK 21. Reportá comando, número real de tests, fallos, errores, omisiones y código de salida.
- El sandbox Codex/MXC ya mostró un posible error H2 `90097-232` por entorno de acceso/archivo aun cuando la suite pasó externamente en Windows. Si aparece, **no lo trates automáticamente como defecto de implementación ni como suite aprobada**. Conservá logs y ejecutá las verificaciones técnicas posibles; explicá qué deberá correr el responsable fuera de Codex. No descargues dependencias ni cambies permisos globales sin autorización.
- Si no pudiste ejecutar un caso, marcálo **NO VERIFICADO**; si falló, **FALLÓ**; si pasó realmente, **APROBADO**. **Un código de salida cero de Codex nunca implica pruebas aprobadas.** No informes H4 como terminado si faltan mecanismos principales o tests esenciales. Distinguí implementado de verificado.

## 6. Informe final exigido (al terminar la única ejecución)

Respondé con:

1. **Resultado general H4:** COMPLETO / PARCIAL / BLOQUEADO. Si es parcial, explicar exactamente qué falta y por qué. No afirmar aprobación humana.
2. **Archivos creados y modificados:** rutas, responsabilidad, alcance y cualquier desviación del diseño v0.2; dejar constancia de archivos normativos intactos y bases operativas no tocadas.
3. **Matriz de cobertura H4-T01…H4-T18**: por cada grupo indicar IMPLEMENTADO, APROBADO, FALLÓ o NO VERIFICADO con evidencia reproducible, sin condensar ausencias bajo frases vagas; mencionar regreso de 71 tests históricos y total real de la suite nueva si pudo ejecutarse.
4. **Seguridad y durabilidad:** evidencias para guardia entre JVM, identidad, autorización de migración sólo en test, sesiones/EN_CURSO, intención UUID `I`, reconciliación, estrategia B, bloqueo seguro y regresión de concurrencia.
5. **Comandos y errores reproducibles:** Maven y JVM, códigos de salida, pruebas no ejecutadas, límites del sandbox y acciones exactas que el responsable debe verificar externamente en PowerShell Windows.
6. **Riesgos y pendientes:** sólo problemas demostrados, dudas que impiden dar por conforme un requisito, limitaciones temporales y decisiones técnicas menores adoptadas. Confirmar que **no hiciste commits, no tocaste base operativa, no ejecutaste otros hitos ni operaciones remotas**.

**PUNTO DE PARADA:** terminá al implementar todo H4 o al encontrar un bloqueo material explícito. No pases a H5; quedará una única revisión humana final del código y evidencias antes de aprobar H4 y comenzar H5. No solicites aprobación humana entre subetapas internas de H4.
