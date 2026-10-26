# R02 - Refinamiento del plan técnico | ReparaBarrio

| Control documental | Valor |
|---|---|
| **Versión del prompt** | **1.0** |
| **Fecha** | **13/10/2026** |
| **Estado** | **APROBADO PARA EJECUCIÓN. NO EJECUTADO** |
| **Metodología y fase** | Spec-Driven Development (SDD), fase 2: Planificación, primer refinamiento |
| **Destinatario** | Codex CLI |
| **Fuente funcional normativa** | `SPEC.md` v0.2 aprobada, con D016 y validación humana de la sección 14 |
| **Plan de partida** | `PLAN.md` v0.1 generado por P02 v0.2 |
| **Único archivo que puede modificarse** | `PLAN.md` |
| **Resultado esperado** | `PLAN.md` v0.2, todavía pendiente de validación humana final |

## VAMOS A REFINAR EL PLAN

Estamos desarrollando **ReparaBarrio**, aplicación local de escritorio en Java para gestionar órdenes de reparación con técnicos, puestos y herramientas compartidos. Tiene un modo operativo con confirmación humana y persistencia, y un modo de simulación concurrente con datos aislados. La fase de especificación SDD ya fue aprobada.

**Lea íntegramente `SPEC.md`, `PLAN.md` y las instrucciones aplicables de `AGENTS.md` antes de modificar el plan.** `SPEC.md` tiene prioridad normativa para el comportamiento del producto. Este refinamiento **no solicita generar un plan nuevo**: aplique cambios localizados a `PLAN.md` v0.1, conservando sus apartados, los identificadores existentes y las decisiones técnicas que siguen siendo correctas.

Las decisiones humanas **PT-01 a PT-08 ya fueron aprobadas**, con las precisiones detalladas a continuación. No las mantenga etiquetadas como pendientes de aprobación. Cuando existan **comprobaciones experimentales todavía no realizadas** o detalles de implementación realmente abiertos, indíquelos como pendientes de verificación, sin convertirlos en una reapertura de las decisiones aprobadas.

## OBJETIVO DEL REFINAMIENTO

Consolidar en `PLAN.md` las decisiones PT-01 a PT-08, precisar la semántica de las operaciones persistentes de resultado incierto y el comparador natural total, y **reorganizar el desarrollo en incrementos verificables que demuestren temprano el núcleo concurrente real**. Mantener los mismos requisitos del MVP y la cobertura de `RF-01` a `RF-19`, `RNF-01` a `RNF-08`, `RN-01` a `RN-10`, `INV-01` a `INV-10` y `CA-01` a `CA-26`.

La planificación debe ser **específica, viable y apta para que otro integrante retome el proyecto**. Evite proponer clases, abstracciones o herramientas adicionales cuando no aporten una responsabilidad comprobable.

---

## PROMPT PATTERN (REFINAMIENTO DEL PROMPT 2 - PLANIFICACIÓN)

### ARQUITECTURA

**PT-01, APROBADA.** Mantenga la arquitectura de **monolito modular en capas**, en un único proyecto Maven, con Java y Swing, un núcleo funcional compartido, H2/JDBC como fuente de verdad de datos confirmados y **un coordinador independiente por contexto de datos** (operativo y simulación). Preserve el aislamiento completo entre ambos modos: no comparten repositorio mutable, conexiones, bloqueos ni asignaciones; sí comparten reglas y contratos. No introduzca microservicios, servidor remoto, redes multiusuario ni duplicación de reglas funcionales.

**PT-02, APROBADA CON VERIFICACIÓN DEL ENTORNO.** Mantenga Java/JDK 21 LTS, Maven, Swing, H2/JDBC y JUnit 5 como referencias técnicas. Conserve las versiones específicas recomendadas en PLAN v0.1 únicamente como **versiones de referencia por comprobar** (disponibilidad local, resolución de dependencias y compatibilidad antes de implementar). No asegure que están instaladas, probadas o que son las más recientes. Si una versión no está disponible, el futuro equipo deberá documentar una sustitución compatible, sin alterar por cuenta propia las tecnologías decididas.

La arquitectura seguirá evaluando riesgos y compromisos ya descritos en v0.1. No repita capítulos completos si solamente necesita precisar su estado de aprobación.

### MÓDULOS

Conserve los límites lógicos entre dominio, planificación, aplicación, persistencia, interfaz, simulación, métricas y arranque. **No confunda módulos lógicos con proyectos Maven separados.** Identifique oportunidades para simplificar o consolidar clases/servicios pequeños cuando la separación no aporte contratos, reutilización o pruebas relevantes. No concentre todo el comportamiento en una clase monolítica ni debilite la posibilidad de relevo entre integrantes.

La estructura de módulos debe seguir siendo suficientemente explícita para permitir pruebas independientes y una integración incremental. Evite crear archivos adicionales durante este refinamiento.

### FUNCIONES O CLASES PRINCIPALES

**PT-04, APROBADA.** Precise el contrato de identificadores y del **orden natural total, determinista y transitivo**:

- Los IDs son obligatorios, no vacíos y únicos según su ámbito de entidad/categoría y contexto, respetando `SPEC.md`. No se reutilizan IDs operativos.
- Se distingue entre mayúsculas y minúsculas (`T2` y `t2` son diferentes).
- Se rechazan IDs con espacios externos; no se recortan ni normalizan silenciosamente de modo que cambie su identidad. No imponga sin autorización un formato único como `T01`.
- Dentro de un prefijo comparable, los sufijos decimales se ordenan por **valor numérico** (`T2` precede a `T10`), sin desbordamiento.
- IDs diferentes cuyo sufijo tiene el mismo valor (`T2`, `T02`) **no se consideran iguales**: desempate textual estable. Los IDs sin sufijo y los identificadores con prefijos diferentes también participan de un único orden consistente.
- Especifique a nivel de contrato un criterio de comparación que preserve **totalidad, antisimetría, transitividad y consistencia con igualdad textual** incluso con identificadores mixtos. No mezcle comparadores por pares que puedan crear ciclos. El mecanismo concreto de código y sus pruebas se diseñarán al implementar, no aquí.
- Mantenga ejemplos representativos: `T2`, `T02`, `T10`, `TEC-3`, `TEC-12`, `AUX`, mayúsculas/minúsculas y sufijos numéricos grandes.

**PT-05, APROBADA.** El catálogo inicial preconfigurado de tipos será **MULTIMETRO, SOLDADOR y TALADRO**, como tipos no consumibles y exclusivos. Se admite iniciar con cero unidades físicas de cualquiera de ellos. El encargado puede dar de alta **unidades** de tipos conocidos, pero no editar los tipos desde la interfaz del MVP. No precargue unidades físicas ficticias como si fueran inventario real. Los tipos iniciales se conservan al reiniciar y no sustituyen datos ya existentes.

Revise la lista sugerida de clases y deje explícito cuáles son esenciales para los primeros incrementos y cuáles pueden consolidarse posteriormente **sin perder contratos ni trazabilidad**.

### RESPONSABILIDADES DE CADA COMPONENTE

Mantenga separados: regla de primera factible, selección de unidades exactas, validación de invariantes, coordinación concurrente, persistencia transaccional, adaptación Swing y ejecución de escenarios ficticios. Preserve los resultados diferenciados: éxito confirmado, rechazo de negocio, ausencia de propuesta, error técnico y **resultado incierto pendiente de reconciliación**. La GUI y el simulador nunca modifican directamente la base ni las asignaciones.

### MECANISMO DE CONCURRENCIA

**PT-03, APROBADA.** Mantenga un `ReentrantLock(true)` por contexto, con adquisición interrumpible cuando proceda y liberación garantizada en `finally`. Todas las mutaciones de ese contexto y las lecturas que requieran instantánea coherente se coordinan con transacciones H2. La sección crítica contiene el trabajo transaccional imprescindible, **sin garantizar duración máxima**: una operación de disco lenta puede retrasar consultas y confirmaciones. Nunca incluye la reparación simulada, espera artificial, interacción humana ni renderizado Swing. La justicia de adquisición del lock **no equivale** a la prioridad por antigüedad de órdenes y no asegura ausencia de starvation del negocio.

Mantenga el análisis de inicio/inicio, inicio/cancelación, finalización/fallo, confirmaciones obsoletas, reserva íntegra, no duplicación y no doble liberación. La confirmación conserva estado, recursos exactos e historial en una transacción; el éxito sólo se comunica luego de persistencia confirmada.

**PT-06, APROBADA.** Precise y refleje coherentemente en arquitectura, contratos, manejo de errores y pruebas este tratamiento del **resultado incierto de commit**:

1. Un error antes del commit con rollback confirmado significa operación no conservada, sin efectos parciales.
2. Una excepción durante commit, pérdida de conexión o interrupción puede dejar **desconocido para la aplicación** si se confirmó la transacción. No se debe comunicar éxito ni rechazo definitivo por mera suposición.
3. Se utiliza un **identificador interno único de operación**, conservado atómicamente junto a su efecto/historial, para comprobar su resultado tras recuperar acceso al almacenamiento. No es una reserva ni un sexto estado de orden.
4. Mientras el resultado permanece incierto, el **contexto afectado** no admite nuevas mutaciones ni reintentos a ciegas; se informa «resultado pendiente de verificación» o equivalente. El otro contexto, si permanece sano e independiente, no necesita bloquearse por esta causa.
5. La reconciliación valida estado, asignaciones e historial: si encuentra la operación íntegra y confirmada, informa éxito; si acredita que no se confirmó y el almacenamiento es coherente, informa ausencia de confirmación; si no puede determinarlo o detecta inconsistencia, conserva el bloqueo y presenta diagnóstico para revisión.
6. Diferencie claramente **atomicidad del cambio** («todo o nada») de **conocimiento inmediato del resultado**. No pretenda garantizar por software que toda excepción significa rollback.

**Atención a consistencia normativa:** si alguna frase de `SPEC.md` (en particular `RF-13` o `INV-06`) pudiera interpretarse como una garantía más fuerte frente a resultados inciertos, **no la reescriba ni declare resuelta la contradicción de forma silenciosa**. Indique en `PLAN.md` la interpretación aprobada de PT-06 y señale cualquier discrepancia textual residual que requiera validación humana separada de la especificación. No modifique `SPEC.md`.

**PT-08, APROBADA.** Mantenga `ExecutorService` para trabajos concurrentes y `ScheduledExecutorService` para llegadas programadas. Cuatro trabajadores son **configuración inicial y modificable**, no un límite funcional ni una equivalencia con cantidad de técnicos. No mantenga bloqueos durante esperas ficticias. La simulación debe probar dos trabajos simultáneos independientes, contención por recursos compartidos, ausencia de reservas parciales, cierre sin progreso y aislamiento de contextos. Preserve EDT libre de tareas JDBC y esperas pesadas.

### MANEJO DE ERRORES

**PT-07, APROBADA.** Mantenga `FileChannel` como guardia de **una sola instancia operativa**, con ruta estable y coherente con la base H2. Al reiniciar, audite integridad y continuidad; las órdenes EN_CURSO conservan sus asignaciones y, tras interrupción inesperada, requieren revisión humana conforme a `SPEC.md`. No las libere ni cierre automáticamente.

Incorpore en la tabla de errores la situación PT-06, diferenciando con precisión: rechazo de negocio, rollback conocido, commit confirmado, commit incierto, corrupción/inconsistencia, fallo posterior de refresco visual y error ficticio de simulación. Un fallo visual tras commit exitoso no revierte lo conservado.

### ESTRUCTURA DE ARCHIVOS DEL PROYECTO

Mantenga un **único proyecto Maven** con paquetes lógicos, recursos, pruebas y datos locales. Conserve el árbol propuesto como orientación, pero distinga:

- Clases/archivos imprescindibles para comenzar con un núcleo verificable.
- Componentes que se incorporarán después en hitos sucesivos.
- Posibles clases consolidables, siempre sin perder separación de responsabilidades ni pruebas.

No duplique implementaciones para operativo y simulación. No cree físicamente ninguno de estos archivos en esta fase.

### ESTRATEGIA DE PRUEBAS

Conserve y, cuando sea necesario, precise la matriz **CA-01 a CA-26** y su correspondencia con RF/RNF/RN/INV. No elimine pruebas de recuperación, concurrencia, aislamiento ni interfaz para reducir tamaño.

- Para PT-04: pruebas de orden total, antisimetría, transitividad, empate textual, IDs mixtos, grandes sufijos, mayúsculas/minúsculas y espacios externos.
- Para PT-06: rollback conocido, interrupción antes/después de commit, resultado incierto, reconciliación por ID, ausencia de duplicaciones y bloqueo de contexto hasta verificación.
- Para PT-03/PT-08: carreras con barreras, trabajos realmente superpuestos, contención, tiempo de bloqueo, no deadlock observado bajo ensayos acotados y ejecución EDT segura.
- Para PT-07: segunda instancia rechazada, recuperación con EN_CURSO y señal de revisión humana.
- Para PT-02: comprobación real de versiones y dependencias antes de implementar; no presente resultados que aún no existen.

Diferencie pruebas automáticas futuras, pruebas manuales Windows y mediciones académicas de CPU, memoria y tiempo. No invente mediciones ni atribuya pruebas ejecutadas a esta fase.

---

## ORDEN DE IMPLEMENTACIÓN Y CONTINUIDAD DEL EQUIPO

Refine `PLAN.md` para dar prioridad a un **primer recorrido vertical ejecutable, pequeño y demostrable**, sin aumentar el alcance ni sacrificar persistencia. Evite un cronograma en el que haya que construir toda la GUI o todo el simulador antes de poder verificar el núcleo concurrente.

Proponga una secuencia revisada de hitos compatible con las dependencias reales:

1. Verificación de entorno, decisiones aprobadas y contratos indispensables.
2. Núcleo mínimo de dominio, estados, factibilidad, prioridad y selección determinista, con pruebas unitarias.
3. Persistencia H2 mínima y operaciones completas de registro, confirmación y cierre, con pruebas de transacción y rollback. **No trate un inventario sólo en memoria como demostración suficiente de la atomicidad durable aprobada.**
4. **Demostración temprana de concurrencia:** dos inicios independientes válidos, competencia por una herramienta única, doble inicio rechazado y dos reparaciones EN_CURSO superpuestas, sobre el núcleo transaccional real y con pruebas controladas, aun sin interfaz completa.
5. Recuperación, resguardo de instancia y reconciliación PT-06.
6. Interfaz operativa Swing mínima y luego completa.
7. Simulación aislada, generación reproducible, terminación y métricas, reutilizando el núcleo; interfaz del simulador.
8. Integración, matriz de aceptación completa y evidencias académicas, incluidos tokens e informe.

Puede conservar los nombres H0 a H8 originales y reorganizar el contenido o explicar los cambios de hitos, siempre con **dependencias, criterios de salida, evidencia verificable, riesgos y puntos de relevo**. No elimine requisitos del MVP ni adelante fases SDD: todo lo anterior describe trabajo **futuro**, no autoriza crear código ahora.

En particular, diferencie el **primer incremento demostrable** del **MVP completo**. Indique qué puede ejecutarse y probarse después de cada incremento, qué todavía no está implementado y cuál es el siguiente hito mínimo para otro integrante.

## RESULTADO SOLICITADO DEL REFINAMIENTO R02

**Actualice exclusivamente `PLAN.md`** desde la versión 0.1 a **versión 0.2 refinada**, con fecha **13/10/2026** y estado **PRELIMINAR, pendiente de validación humana final**. Mantenga como fuente `SPEC.md` v0.2 aprobada y registre **R02 v1.0** como refinamiento recibido. Conserve los ocho apartados principales, su orden y la trazabilidad existente; no reescriba innecesariamente las secciones correctas.

En el documento actualizado:

1. Marque **PT-01 a PT-08 como APROBADAS**, especificando en PT-02 que sólo falta verificar versiones y compatibilidad local, y en PT-08 que el tamaño inicial del pool es configurable.
2. Registre la decisión humana, el apartado afectado y la justificación para cada PT. Distinga con claridad **decisiones ya cerradas** de **validaciones empíricas futuras** y de cualquier conflicto auténtico con `SPEC.md`.
3. Refine únicamente lo necesario para integrar PT-04, PT-06, la simplificación proporcionada de clases y los hitos de concurrencia temprana.
4. Mantenga la cobertura de **RF-01 a RF-19, RNF-01 a RNF-08, RN-01 a RN-10, INV-01 a INV-10 y CA-01 a CA-26**, con sus identificadores originales, evitando reinterpretar la especificación.
5. Agregue al final un apartado breve **«Registro de refinamiento R02»** que describa cambios realmente aplicados, decisiones aprobadas, riesgos que permanecen y validaciones pendientes. No afirme haber implementado ni probado nada.
6. Si encuentra alguna incompatibilidad no resuelta, explíquela sin inventar una decisión adicional ni alterar `SPEC.md`; no sustituya silenciosamente la política acordada.

**RESTRICCIONES:** No genere ni modifique ningún archivo distinto de `PLAN.md`. No escriba código Java, cuerpos de métodos, tests ejecutables, `pom.xml`, SQL, scripts, configuraciones o documentación auxiliar. No implemente la aplicación ni inicie la fase 3 de SDD. No ejecute pruebas, construcciones o herramientas de instalación. No modifique `SPEC.md`, `AGENTS.md`, `DECISIONES.md`, prompts, evidencias, métricas, consignas o rúbricas. No cree commits, no configure remotos y no interactúe con GitHub. No incluya contadores de tokens en `PLAN.md`: la medición se realizará externamente con Codex CLI.
