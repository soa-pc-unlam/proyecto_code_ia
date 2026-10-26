# P02 - Prompt técnico de planificación | ReparaBarrio

| Control del documento | Valor |
|---|---|
| **Versión** | **0.2** |
| **Fecha** | **13/10/2026** |
| **Estado** | **APROBADO PARA EJECUCIÓN. NO EJECUTADO** |
| **Metodología y fase** | Spec-Driven Development (SDD), fase 2: Planificación |
| **Herramienta destinataria** | Codex CLI |
| **Documento de entrada principal** | `SPEC.md`, versión 0.2 aprobada, incluida la aclaración humana D016 |
| **Documento de salida solicitado** | `PLAN.md`, versión inicial preliminar |
| **Propósito** | Proponer y justificar un plan técnico implementable, trazable y verificable, sin generar código |

## VAMOS A DESARROLLAR

Vamos a planificar técnicamente **ReparaBarrio**, una aplicación de escritorio para talleres pequeños y cooperativas que coordina órdenes de reparación con recursos compartidos (técnicos, puestos y herramientas). Tendrá un **modo operativo**, con confirmación humana y datos persistentes, y un **modo de simulación**, con trabajos ficticios realmente concurrentes y datos aislados.

**Lea íntegramente `SPEC.md`** antes de formular el plan. Es la fuente funcional aprobada y prevalece para requisitos, reglas de negocio, estados, invariantes, criterios de aceptación, exclusiones y limitaciones. Consulte `AGENTS.md` como conjunto de instrucciones de trabajo aplicables; no modifique ninguno de estos documentos.

Genere un **plan**, no una implementación. Si detecta contradicciones, incertidumbres técnicas o restricciones inviables, expóngalas y recomiende una resolución fundamentada **pendiente de aprobación humana**. No modifique ni reinterprete silenciosamente `SPEC.md`.

## PROBLEMA Y OBJETIVO DE LA PLANIFICACIÓN

Definir una solución técnica que permita:

- Proponer la **primera orden factible por antigüedad**, mediante la secuencia aprobada y una selección determinista de recursos físicos.
- Mantener las propuestas **sin reserva**, y al confirmar revalidar la prioridad, el estado y las **unidades exactas** propuestas.
- Confirmar o rechazar de manera íntegra la asignación de recursos, la transición de estado y el historial, garantizando **exclusión mutua, atomicidad y durabilidad**.
- Recuperar datos operativos coherentes después de un cierre o error, conservando las reparaciones EN_CURSO y sus asignaciones para revisión humana.
- Ejecutar simulaciones realmente concurrentes sobre las **mismas reglas de negocio**, sin alterar la información operativa, con resultados y métricas definidos en `SPEC.md`.
- Obtener un MVP realizable en el plazo de la APO, fácil de probar, documentar y continuar por otros integrantes del grupo.

## DECISIONES TÉCNICAS Y RESTRICCIONES DE PARTIDA

- **Lenguaje y plataforma:** Java; aplicación local de escritorio para Windows, de **una sola instancia de ejecución** y sin servicios remotos obligatorios.
- **Arquitectura de partida aprobada para evaluar y justificar:** **monolito modular organizado en capas**, dentro de un único proyecto; módulos lógicos o paquetes, no microservicios ni proyectos separados salvo justificación excepcional.
- **Tecnologías de referencia:** Swing para la interfaz, Maven para construcción, JUnit 5 para pruebas, H2 embebida con JDBC para persistencia. Proponga versiones compatibles y justifique la elección; si alguna tecnología presenta un impedimento concreto, descríbalo y proponga una alternativa antes de sustituirla.
- **Persistencia prevista:** H2 basada en archivo para el modo operativo y H2 temporal/aislada para simulación. El estado operativo confirmado debe tener una **fuente de verdad definida**, sin dos copias mutables independientes que puedan divergir.
- **Estrategia concurrente de partida aprobada:** **coordinador único con una sección crítica de alcance reducido**, complementado con operaciones persistentes transaccionales. Codex debe evaluar su viabilidad, justificar el mecanismo Java elegido y explicar sus limitaciones. **No se retienen bloqueos del coordinador durante toda una reparación real o simulada.**
- **Catálogo D016:** los **tipos de herramientas** son iniciales y preconfigurados; el encargado registra **unidades físicas** de tipos existentes, pero no administra el catálogo de tipos desde la interfaz.
- **Reglas funcionales no negociables:** cinco estados de órdenes, confirmación humana en operativo y automática en simulación, primera factible por antigüedad, recursos exclusivos, propuestas sin reserva, revalidación de unidades exactas, sin salteos ni prioridades, y separación completa de datos entre modos.
- **Fuera del alcance:** redes multiusuario, distribución, microservicios, REST, autenticación, cobros, inventario de repuestos consumibles, optimización global y reservas futuras.

La finalidad de estas decisiones es orientar el diseño y limitar la complejidad. **No se solicita que las repita sin análisis**: explique por qué son compatibles con las garantías de `SPEC.md` y señale los riesgos, especialmente el costo de serializar confirmaciones mientras se confirma una transacción persistente.

---

## PROMPT PATTERN (PROMPT 2 - PLANIFICACIÓN)

### ARQUITECTURA

- Diseñe una arquitectura **monolítica modular en capas** para una aplicación de escritorio con un único núcleo de reglas de negocio compartido entre los dos modos.
- Defina los límites, las dependencias permitidas y la comunicación entre interfaz, casos de uso, planificación/asignación, dominio, persistencia y simulación. Evite dependencias circulares y reglas duplicadas.
- Explique cuál será la **fuente de verdad** del estado confirmado y cómo se reflejarán en memoria y en la interfaz los cambios persistidos, incluidos errores de conservación y reinicio.
- Justifique Swing, H2/JDBC, Maven y JUnit 5 o señale inconvenientes concretos y alternativas proporcionadas al MVP. No incorpore frameworks adicionales sin una necesidad verificable.
- Identifique las principales ventajas, limitaciones y riesgos de la solución frente a alternativas más complejas, sin ampliar el alcance aprobado.

### MÓDULOS

- Identifique los módulos **lógicos o paquetes** y explique qué responsabilidad tiene cada uno: dominio, aplicación/casos de uso, planificación, persistencia, interfaz, simulación y métricas si corresponde.
- Distinga los componentes compartidos por modo operativo y simulación de los adaptadores específicos de cada contexto.
- Especifique cómo se crearán contextos de datos independientes y cómo se impedirá que una simulación consulte o modifique accidentalmente datos operativos.
- Proponga límites claros de trabajo para que otro integrante del equipo pueda implementar o continuar un módulo sin depender de conocer toda la aplicación.

### FUNCIONES O CLASES PRINCIPALES

- Proponga las **entidades, interfaces y clases o servicios principales** con nombre sugerido, finalidad y operaciones públicas relevantes, **sin escribir cuerpos de métodos ni código**.
- Cubra registro y consulta de órdenes; alta y disponibilidad de recursos; catálogo inicial de tipos; primera orden factible; generación de propuesta; confirmación de inicio; cierre exitoso o fallido; cancelación; historial; recuperación; simulación y estadísticas.
- Distinga claramente **tipo de herramienta**, **unidad física**, **orden**, **propuesta no reservada**, **asignación confirmada** y **estado**.
- Para los contratos críticos, explique entradas, precondiciones, resultados de éxito, motivos de rechazo y efectos observables. Respete el criterio de selección natural, la secuencia de antigüedad y la validación de unidades exactas.

### RESPONSABILIDADES DE CADA COMPONENTE

- Establezca qué componente controla invariantes y transiciones, cuál decide la primera orden factible, cuál selecciona unidades, cuál coordina las operaciones críticas, cuál garantiza la conservación íntegra y cuál actualiza la interfaz.
- Separe las **reglas de dominio** de la lógica específica del modo operativo o de simulación, sin que la interfaz ni el simulador modifiquen directamente asignaciones o estados compartidos.
- Describa los límites de las operaciones transaccionales y cómo se comunica su resultado a quien solicita la operación.
- Señale qué contratos o pruebas permitirán implementar módulos en paralelo y realizar integraciones incrementales.

### MECANISMO DE CONCURRENCIA

- Proponga y justifique el **mecanismo concreto de sincronización Java** para un coordinador único con bloqueos breves (por ejemplo, evaluar `synchronized` frente a `ReentrantLock`), y la ejecución de tareas concurrentes (por ejemplo, `ExecutorService`). Elija mecanismos por necesidad, no por variedad académica.
- Identifique el **estado compartido**, las secciones críticas, el orden de validación y el **punto de linealización** de operaciones como registro, confirmación, cancelación, finalización, fallo y cambios de disponibilidad.
- Detalle cómo se coordinan **sincronización en memoria y transacciones H2** para que una confirmación sólo se comunique como exitosa después de conservar de forma íntegra estado, asignaciones e historial. Explique qué sucede ante excepciones, rollback y cierre inesperado, evitando divergencias entre memoria y base de datos.
- Evalúe la duración real de la sección crítica si incluye acceso a almacenamiento: manténgala limitada al procesamiento necesario y a la transacción corta; **nunca mantenga el bloqueo durante una reparación o un temporizador de simulación**. Si el bloqueo único pudiera perjudicar el rendimiento, documente la limitación y la alternativa, sin cambiar silenciosamente la estrategia aprobada.
- Describa las carreras **inicio/inicio, inicio/cancelación, finalización/fallo**, confirmación de propuestas obsoletas, doble liberación y registros concurrentes. Fundamente cómo se preservan los invariantes `INV-01` a `INV-10`.
- Diferencie los **técnicos como recursos del negocio** de los **hilos Java** y explique cómo demostrar actividad simultánea de reparaciones independientes, respetando la prioridad por primera factible en cada confirmación.
- Analice deadlock, starvation, retención parcial y el bloqueo de cabeza de cola **aceptado** por la política del MVP, sin afirmar garantías universales basadas sólo en pruebas finitas.
- Explique cómo ejecutar la simulación sin bloquear el **Event Dispatch Thread (EDT)** de Swing y cómo actualizar la pantalla de forma segura.

### MANEJO DE ERRORES

- Diferencie fallas de validación, recurso no disponible, propuesta obsoleta, prioridad incumplida, transición inválida, conflicto concurrente, error de persistencia, datos inconsistentes al recuperar y fallo de una tarea de simulación.
- Especifique qué operaciones devuelven un **rechazo de negocio** y cuáles un **error técnico**, con mensajes útiles para el encargado y detalles diagnósticos para desarrollo.
- Justifique cómo impedir resultados de éxito falsos, asignaciones parciales, dobles liberaciones y estados inconsistentes después de fallos.
- Defina una recuperación operativa que preserve órdenes EN_CURSO y sus recursos ocupados, señalando revisión humana, sin inventar estados adicionales ni liberar recursos automáticamente.

### ESTRUCTURA DE ARCHIVOS DEL PROYECTO

- Proponga un árbol concreto y sobrio de **paquetes, archivos Java, recursos, configuración y pruebas** dentro de un único proyecto Maven. Muestre dónde quedarán la interfaz, el coordinador, la persistencia, el simulador y el punto de entrada de la aplicación.
- Mantenga separados los datos persistentes operativos, los datos temporales de simulación y los registros académicos de prompts, tokens y evidencias.
- Aproveche `SPEC.md`, `AGENTS.md`, `prompts/`, `docs/`, `evidencias/`, `metricas/` y el historial Git **sin reorganizarlos ni modificarlos durante esta fase**.
- Indique cómo construir y ejecutar la futura aplicación en Windows, pero sólo **como procedimiento planificado**, sin ejecutarlo ahora.

### ESTRATEGIA DE PRUEBAS

- Proponga una matriz que vincule los criterios `CA-01` a `CA-26`, los invariantes `INV-01` a `INV-10` y los principales módulos con tipos de prueba, escenarios y evidencia esperada.
- Incluya pruebas unitarias de reglas/estados, pruebas de integración con H2/JDBC, pruebas de concurrencia con coordinación controlada, simulaciones de contención, persistencia/recuperación, aislamiento entre modos y pruebas básicas de interfaz Swing.
- Para operaciones simultáneas, considere **barreras o sincronización de arranque**, repeticiones y tiempos máximos de espera. Distinga lo que puede verificarse de forma determinista de aquello que sólo puede explorarse bajo carga.
- Prevea escenarios de referencia con **100 órdenes**, métricas funcionales del simulador y mediciones académicas de CPU, memoria y tiempo. No invente resultados ni concluya ausencia universal de deadlocks por la mera superación de una prueba.
- Defina criterios de salida por módulo y por hito para detectar errores antes de integrar toda la aplicación.

---

## ORDEN DE IMPLEMENTACIÓN Y CONTINUIDAD DEL EQUIPO

- Proponga hitos **pequeños, dependientes y verificables**, priorizando núcleo y reglas de concurrencia, persistencia transaccional, interfaz operativa, simulación y evidencias académicas; ajuste el orden si una dependencia técnica lo exige.
- Para cada hito, especifique entregables futuros, dependencias, pruebas o criterios de salida, riesgos y condiciones para continuar. Identifique los puntos desde los cuales otro compañero podría retomar el trabajo.
- Diferencie **imprescindible para aprobar el MVP** de mejoras cosméticas o de conveniencia que se puedan postergar **sin omitir requisitos aprobados**.
- Mantenga como hito independiente la preparación de registros de prompts/refinamientos/tokens, métricas experimentales, rúbricas y documentación para el informe de la APO.

## RESULTADO SOLICITADO DE LA FASE 2: PLANIFICACIÓN

**Genere y guarde únicamente `PLAN.md` en la raíz del proyecto**. Incluya control documental con **versión inicial 0.1**, **fecha 13/10/2026**, **estado PRELIMINAR pendiente de revisión humana**, fase SDD 2 y referencia explícita a `SPEC.md` v0.2 aprobada (con D016) y a este P02 v0.2.

El documento debe contener:

1. Los **ocho apartados del Prompt Pattern** en el orden indicado.
2. Una **arquitectura recomendada y justificada**, componentes, responsabilidades, contratos y estructura propuesta de paquetes/archivos.
3. Una explicación **específica y verificable** de sincronización, transacciones, fuente de verdad, recuperación y concurrencia de la simulación.
4. Una **matriz de trazabilidad** a `RF`, `RNF`, `RN`, `INV` y `CA`, sin renumerar ni alterar requisitos de `SPEC.md`.
5. Hitos de implementación con dependencias y criterios de salida, además de riesgos, alternativas descartadas y **decisiones técnicas pendientes de aprobación humana**.

**IMPORTANTE:** No genere código Java ni cuerpos de métodos, pseudocódigo ejecutable, scripts, tests ejecutables, archivos de configuración, migraciones ni diagramas en archivos separados. **No implemente todavía ninguna parte de la aplicación.** No modifique `SPEC.md`, `AGENTS.md`, `DECISIONES.md`, prompts, evidencias, métricas, consignas ni otros archivos. No ejecute construcciones ni pruebas. No cree commits ni configure remotos ni interactúe con GitHub.

Revise la coherencia de `PLAN.md` con la especificación **sin modificarla**. Si alguna decisión de diseño entra en conflicto con `SPEC.md`, registre el conflicto y la recomendación correspondiente para revisión humana. Termine con un resumen breve de arquitectura, decisiones, riesgos y pendientes; **no incluya tokens ni consumo de IA en `PLAN.md`**, porque se registran en archivos externos de Codex CLI.
