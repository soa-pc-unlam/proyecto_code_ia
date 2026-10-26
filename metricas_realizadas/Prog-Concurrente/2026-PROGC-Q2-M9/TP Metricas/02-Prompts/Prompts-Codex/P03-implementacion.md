# P03 - Prompt técnico de implementación | ReparaBarrio

| Control documental | Valor |
|---|---|
| **Versión del prompt** | **0.1** |
| **Fecha** | **13/10/2026** |
| **Estado** | **PRELIMINAR, pendiente de revisión y aprobación humana. NO EJECUTAR AÚN** |
| **Metodología y fase** | Spec-Driven Development (SDD), **fase 3: Implementación** |
| **Destinatario futuro** | Codex CLI, desde la raíz local de `APO1-ReparaBarrio` |
| **Definición funcional normativa** | `SPEC.md` v0.2 aprobada, incluida D016 y su cierre humano |
| **Plan técnico normativo** | `PLAN.md` v0.2 aprobada, con sección 8.7 de cierre humano |
| **Instrucciones aplicables** | `AGENTS.md` |
| **Hito propuesto para la primera ejecución** | **H1, exclusivamente**, después de verificar H0 y aprobar este prompt |
| **Resultado de la fase completa** | Implementación del MVP conforme a SPEC y PLAN, mediante incrementos verificables H1 a H7; **no** en una única ejecución obligatoria |
| **Fase posterior independiente** | P04: Validación integral contra `SPEC.md` |

## VAMOS A IMPLEMENTAR

Estamos desarrollando **ReparaBarrio**, aplicación local de escritorio para administrar órdenes de reparación que requieren recursos exclusivos compartidos: un técnico, un puesto de trabajo y cero o una unidad física por cada tipo de herramienta solicitado. La aplicación contará con un **modo operativo** con confirmación humana y persistencia, y un **modo de simulación realmente concurrente**, aislado del operativo y basado en las mismas reglas de negocio.

**Antes de trabajar, lea íntegramente `SPEC.md`, `PLAN.md` y `AGENTS.md`.** Respete la precedencia: `SPEC.md` define qué debe cumplir el producto; `PLAN.md` define la arquitectura y el orden de implementación aprobados. Ante una discrepancia no resoluble mediante la ratificación de PT-06 de la sección 8.7 de `PLAN.md`, **deténgase, informe la cuestión y no cambie ninguno de los documentos**.

La presente es una propuesta de prompt **principal de la fase 3**. Los hitos son recortes de ejecución dentro de esa fase, no nuevas fases de SDD ni sustitutos de los cuatro prompts principales de la cátedra.

## OBJETIVO DE LA IMPLEMENTACIÓN

Construir progresivamente el MVP completo aprobado, conforme a los hitos H1 a H7, demostrando cuanto antes la corrección de las reglas, la persistencia íntegra y la concurrencia efectiva. Cada incremento debe dejar artefactos coherentes y verificables, sin introducir funcionalidades fuera de alcance ni atribuirse entregables correspondientes a hitos posteriores.

El hito H0 (comprobación de JDK, Maven y disponibilidad/compatibilidad de dependencias) debe estar **verificado y autorizado por la persona responsable** antes de la primera ejecución que genere código. No instale JDK, Maven, frameworks ni herramientas por iniciativa propia.

---

## PROMPT PATTERN (PROMPT 3 - IMPLEMENTAR)

**Implementa el sistema utilizando:**

- **`SPEC.md` como definición de los requisitos.** Preserve los cinco estados de órdenes, sus transiciones, recursos exclusivos, primera pendiente factible por secuencia, unidades exactas, propuestas sin reservas, aislamiento de modos y las demás reglas aprobadas. No modifique la especificación para acomodarla al código.
- **`PLAN.md` como plan técnico.** Respete la arquitectura monolítica modular, el proyecto Maven único, Java/Swing, H2/JDBC, JUnit 5, las decisiones PT-01 a PT-08 y los hitos aprobados. No modifique el plan para justificar desviaciones.

**Respeta estrictamente ambos documentos.** Si surge una incompatibilidad o una dependencia no disponible, consigne el bloqueo antes de sustituir una decisión aprobada. En caso de ambigüedad, no invente requisitos ni interprete silenciosamente una excepción como un cambio de alcance.

**No agregues funcionalidades que no estén especificadas.** Quedan excluidos cobros, autenticación, microservicios, redes multiusuario, inventario consumible, edición del catálogo, optimización de prioridades, reservas futuras, salteos manuales, pausa/reanudación y demás exclusiones de `SPEC.md`.

**Genera todos los archivos necesarios para ejecutar el proyecto.** Aplique esta instrucción al **alcance autorizado de cada incremento**, hasta completar el MVP en H7. No genere pantallas falsas ni implementaciones simuladas para presentar como terminado un hito posterior. En H1 debe poderse **compilar y ejecutar las pruebas del núcleo**; la aplicación gráfica completa sólo será exigible cuando correspondan H5-H7.

---

## TECNOLOGÍAS Y DECISIONES APROBADAS

- **Java 21 LTS** como JDK de referencia, sujeto a comprobación efectiva del entorno H0.
- **Un único proyecto Maven**, paquetes lógicos, sin dividirlo en proyectos ni introducir frameworks adicionales injustificados.
- **JUnit 5** para pruebas; no utilizar `pytest`, que corresponde al ejemplo Python de la diapositiva de la cátedra.
- **Swing** para el modo operativo y la visualización de simulación cuando llegue el hito de interfaz; trabajo de fondo fuera del Event Dispatch Thread (EDT).
- **H2/JDBC** como única fuente mutable de datos confirmados, con transacciones y datos operativos persistentes; simulaciones en contextos separados y descartables, cuando lleguen sus hitos.
- **Un `ReentrantLock(true)` por contexto**, con transacciones dentro de la sección crítica, liberación segura y trabajos de reparación fuera del bloqueo, a partir de los hitos donde se implementa la coordinación.
- **`ExecutorService` y `ScheduledExecutorService`** para simulación posterior, con cuatro trabajadores como valor inicial configurable, no como límite funcional.
- **Catálogo de tipos** MULTIMETRO, SOLDADOR y TALADRO, sin precarga de unidades físicas ficticias y sin edición de tipos por el usuario.
- **IDs y orden natural total** según PT-04; tratamiento de **commit incierto** y reconciliación según PT-06 y la ratificación humana de la sección 8.7 del plan.

Las versiones exactas de Maven, H2, JUnit y plugins presentes en PLAN son **referencias sujetas a verificación**, no resultados ya comprobados. No altere dependencias aprobadas sin documentar primero la incompatibilidad y obtener autorización.

## CONTROL DEL ALCANCE POR EJECUCIÓN

**Alcance de la primera ejecución propuesta: H1 exclusivamente.** Esta selección es parte de la versión preliminar y **no constituye autorización para ejecutar Codex**. La persona responsable aprobará el prompt y confirmará H0 antes de lanzar la primera sesión.

El mismo P03 servirá como marco de implementación para los siguientes hitos. Cada nueva ejecución recibirá una **indicación explícita de hito autorizado** y se registrará de manera separada; no deberá inferirse permiso para avanzar automáticamente al siguiente hito porque el anterior terminó.

| Hito | Alcance aprobado en PLAN | Momento de ejecución |
|---|---|---|
| **H1** | Dominio mínimo, cinco estados, requisitos, disponibilidad derivable sobre instantáneas, planificador, orden natural total, invariantes y pruebas unitarias | **Primera ejecución**, sólo tras aprobación humana de P03/H0 |
| **H2** | Recorrido durable sobre H2: registro, recursos, secuencia, propuesta, confirmación, cancelación, cierre, historial y coordinación transaccional | Sólo tras revisión y autorización de H1 |
| **H3** | Pruebas y demostraciones de carreras, exclusividad y reparaciones independientes superpuestas sobre núcleo H2 real | Sólo tras revisión y autorización de H2 |
| **H4** | Instancia única, recuperación, revisión humana y reconciliación de commit incierto | Autorización posterior |
| **H5** | Interfaz operativa Swing mínima y luego completa | Autorización posterior |
| **H6** | Simulación aislada, generación reproducible, estadísticas e interfaz | Autorización posterior |
| **H7** | Integración y empaquetado del MVP completo, verificación técnica previa y evidencia por completar | Autorización posterior |

**No adelante archivos ni funcionalidades de H2-H7 durante H1**, salvo infraestructura mínima imprescindible para compilar y ejecutar las pruebas de H1 (por ejemplo, `pom.xml`). Evite crear contratos ficticios o repositorios en memoria que simulen persistencia confirmada.

## INSTRUCCIONES PARTICULARES PARA LA PRIMERA EJECUCIÓN: H1

### 1. Resultado funcional esperado

Implemente el **núcleo mínimo de dominio y planificación**, con nombres y límites coherentes con los paquetes `dominio` y `planificacion` de `PLAN.md`, y las utilidades estrictamente necesarias para probarlos:

- Estados de orden y transiciones válidas/invalidas, sin introducir estados adicionales para espera o revisión.
- Identidad de órdenes, unidades de técnicos, puestos, herramientas y tipos de herramienta; requisitos válidos de una orden, incluida ausencia de herramientas especiales y prohibición de más de una unidad del mismo tipo por orden.
- Reglas de factibilidad basadas en disponibilidad de recursos de una **instantánea de entrada**, nunca mediante reservas o asignaciones persistentes inventadas.
- Primera orden **factible** por secuencia creciente de registro; una orden anterior inviable permite considerar la siguiente, una primera viable no puede saltearse al confirmar en hitos posteriores.
- Selección determinista de unidades concretas libres por categoría/tipo con el comparador natural total de PT-04, incluidos `T2`, `T02`, `T10`, `TEC-3`, `TEC-12`, `AUX` y sufijos numéricos de longitud grande.
- Propuesta de inicio **inmutable y sin reserva** que identifique la orden y las unidades exactas elegidas, sin efectuar mutaciones. Los mecanismos de confirmación transaccional son de H2.
- Validaciones e invariantes que resulten comprobables en el dominio sin almacenamiento, sin atribuir al H1 garantías de atomicidad durable que sólo se verificarán a partir de H2.

### 2. Organización y archivos permitidos en H1

Cree únicamente los archivos esenciales para un proyecto Maven **compilable y comprobable con JUnit 5** y el código de `dominio`/`planificacion` necesario para H1. Respete el paquete base `ar.edu.unlam.reparabarrio` y las dependencias entre capas del plan.

Puede crear el `pom.xml` mínimo de Maven y archivos de prueba bajo `src/test/java`; puede agregar recursos de pruebas sólo si están justificados. No genere JDBC, esquema SQL, motor de simulación, interfaz Swing, bases de datos, guardia de instancia, empaquetado definitivo ni servicios de operación que fingieran persistencia. No imponga un `main` gráfico en H1 sólo para afirmar que ya existe una aplicación operativa.

Los nombres del árbol de PLAN son orientativos: consolide clases pequeñas si resulta razonable, manteniendo contratos distinguibles y capacidad de prueba. No cree decenas de archivos vacíos o clases delegadoras sin función verificable.

### 3. Pruebas técnicas del incremento

Cree y ejecute pruebas **JUnit 5** adecuadas al alcance H1: transición permitida/rechazada, validación de requisitos, orden de antigüedad, primera factible, selección exacta determinista, propuesta no reservada, tipos conocidos, orden natural con empates/mezclas/casos límite, transitividad, antisimetría y consistencia con igualdad.

Ejecute `mvn test` o el wrapper **solamente si está disponible y autorizado en el entorno ya verificado**. No instale herramientas ni descargue componentes inesperados de forma autónoma. Si una dependencia necesaria no está disponible, informe el bloqueo y evite anunciar pruebas exitosas.

Estas pruebas son controles técnicos **durante la implementación**; no sustituyen el futuro **P04**, que validará integralmente los requisitos y criterios de aceptación de `SPEC.md`.

### 4. Criterios de salida y entrega de H1

Al finalizar el hito, entregue un resumen textual de:

1. **Archivos creados o modificados** y responsabilidad de cada uno.
2. **Requisitos, reglas e invariantes abordados**, con referencias a sus identificadores reales en `SPEC.md`; distinga cobertura parcial de completa.
3. **Comandos ejecutados y resultados efectivamente obtenidos** (compilación, cantidad de pruebas, aprobadas/fallidas, fallos o bloqueos).
4. **Aspectos aún no implementados**, identificando explícitamente H2-H7 sin presentarlos como cumplidos.
5. **Decisiones o desviaciones que requieren aprobación humana** antes de continuar.
6. **Siguiente hito recomendado**, sin ejecutarlo ni preparar automáticamente archivos de H2.

Preserve el estado existente del repositorio. Un fallo de prueba se comunica con evidencia: no modifique requisitos ni esconda pruebas para conseguir resultados favorables.

## RESTRICCIONES OPERATIVAS Y DE TRAZABILIDAD

- Trabajo **exclusivamente local**, dentro de `APO1-ReparaBarrio`; no acceder, configurar, clonar, modificar ni publicar el repositorio GitHub del grupo. `git remote -v` debe permanecer vacío.
- **No crear commits, cambiar ramas, hacer push ni staging automático**; la persona responsable inspeccionará los cambios y decidirá qué registrar.
- No tocar `SPEC.md`, `PLAN.md`, `AGENTS.md`, `DECISIONES.md`, `prompts/`, `docs/CIERRE-FASE1.md`, `docs/CIERRE-FASE2.md`, `evidencias/`, `metricas/`, consignas ni rúbricas. Los logs JSONL y contadores de tokens se registrarán **fuera del documento y del código** mediante la invocación controlada de Codex CLI.
- No modificar archivos de datos operativos existentes; las pruebas no deben interactuar con una base real del usuario. Nunca usar rutas de otros proyectos de IntelliJ.
- No agregar dependencias, frameworks, servicios externos ni funcionalidades no aprobadas.
- No ejecutar P04 durante P03 ni afirmar validación integral de todos los criterios de aceptación al terminar H1.
- Antes de modificar un archivo preexistente de código, inspeccione su finalidad y conserve el trabajo ajeno. Si encuentra un conflicto material, informe y deténgase sin reemplazar contenido por suposición.
- No prometer ausencia universal de deadlocks ni determinismo del intercalado concurrente basándose en pruebas finitas; documentar mediciones sólo cuando existan.

## RESULTADO SOLICITADO A CODEX PARA H1

**Implementar únicamente H1**, produciendo el código y la configuración mínimos para compilar y ejecutar las pruebas del núcleo de ReparaBarrio. No generar un informe extenso dentro del repositorio ni un documento `SPEC`/`PLAN` nuevo. Resumir en la respuesta final lo hecho, lo comprobado y lo pendiente. **Detenerse al completar H1 o ante un bloqueo que requiera aprobación**, sin avanzar a H2.

---

**Nota de control humano:** este P03 v0.1 es sólo un **borrador para revisión**. No enviarlo a Codex ni guardarlo como aprobado hasta verificar H0 y recibir aprobación expresa. Su ejecución, cuando se autorice, se registrará en un JSONL independiente y tendrá revisión de cambios y métricas de tokens antes del commit local.
