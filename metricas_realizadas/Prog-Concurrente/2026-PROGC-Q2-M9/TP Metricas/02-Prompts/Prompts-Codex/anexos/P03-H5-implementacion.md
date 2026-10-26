# P03-H5 | Implementación integral del hito H5 | ReparaBarrio

| Control documental | Valor |
|---|---|
| Versión | **0.1 — APROBADA** |
| Fecha | 15/10/2026 |
| Estado | **APROBADO Y AUTORIZADO PARA IMPLEMENTACIÓN INTEGRAL H5** |
| Metodología | SDD, fase P03 — incremento H5 |
| Destinatario | Codex CLI, desde la raíz local **original** de `APO1-ReparaBarrio` |
| Diseño técnico aprobado | `IMPLEM-H5.md` **v0.1**, completo |
| Decisiones humanas obligatorias | `docs/APROBACION-DISENO-H5.md`: **H5-D01, H5-D02 y H5-D03** |
| Objeto | **Implementar H5 completo en una ejecución integral**, con pruebas y documentación de resultados |
| No incluido | Recuperación avanzada H4, motor H6, integración/paquete JAR H7 y métricas de informe H8 |
| Entregables futuros de Codex | Java Swing, arranque académico seguro, pruebas JUnit, procedimiento ejecutable Windows y `H5-REPORTE-IMPLEMENTACION.md` |

## 1. Objetivo y mandato de implementación

**ESTE PROMPT FUE APROBADO Y SU EJECUCIÓN AUTORIZADA POR EL RESPONSABLE EL 15/10/2026.** Implementá **todo el hito H5** de ReparaBarrio, **en una sola invocación de Codex**. Organizá internamente la implementación en etapas técnicas, probá y corregí los defectos de tu propio incremento, y continuá entre etapas **sin solicitar aprobaciones humanas intermedias**. Sólo detenete ante un impedimento material que implique cambiar la especificación, el diseño aprobado, las garantías de H1–H4, introducir una tecnología nueva no prevista o tocar datos reales.

El producto es una **aplicación de escritorio académica Java 21/Swing que se pueda iniciar y operar sin JUnit**, usando un archivo H2 nuevo descartable. La interfaz debe exponer las operaciones del taller realmente existentes, sin duplicar planificación, negocio, transacciones, concurrencia ni reconstruir estados optimistamente. Su objetivo es demostrar un recorrido funcional en un trabajo práctico que analiza **vibe coding con Codex** y la metodología **SDD**, no desplegar un sistema de producción.

**No confundas entregables:** `IMPLEM-H5.md` **ya existe y está aprobado**. No lo modifiques ni generes otro diseño. Esta ejecución debe producir **código real, pruebas, procedimiento operativo y reporte H5**, no sólo propuestas.

### 1.1. Lectura obligatoria y jerarquía

Antes de intervenir, leé íntegramente las versiones presentes en el repositorio original de `AGENTS.md`, `SPEC.md` v0.2, `PLAN.md` v0.2 (en especial módulos, §8.3, §8.7, PT-01–PT-08 y matriz de CA), `IMPLEM-H5.md` v0.1, `docs/APROBACION-DISENO-H5.md`, el prompt de diseño `prompts/anexos/P03-D-H5.md`, `docs/DECISION-ALCANCE-ACADEMICO-H4.md`, las evidencias del recorrido nominal H4 y `prompts/P03-implementacion.md` si está presente. Inspeccioná **los contratos del código actual**, no sólo los textos: `ServicioTaller`, `ResultadoOperacion`, `InstantaneaTaller`, `PropuestaInicio`, `ContextoTaller`, `FabricaContextos`, `GuardiaInstancia`, `InicializadorBase`, `AutorizacionBase`, `PersistenciaH2` y pruebas relacionadas.

Precedencia: **SPEC y decisiones humanas aprobadas → PLAN y alcance académico explícito → diseño H5 v0.1 y H5-D01/D02/D03 → este prompt integral sobre modalidad y verificaciones**. Ante un conflicto material imposible de resolver dentro de esos límites, **parar e informar**, sin sustituir normas por suposiciones.

Confirmá al comenzar que el repositorio local sigue sin remotos Git y que el trabajo se realiza sobre la rama local original, no sobre el clon `2026-PROGC-Q2-M9-publicacion`. El punto de control previo conocido es `df38e6a`; si el HEAD evolucionó legítimamente, no lo reviertas ni hagas limpieza destructiva. Preservá los archivos ajenos a H5, incluidos los tres archivos históricos sin seguimiento.

## 2. Funcionalidad H5: operación visual completa según diseño

Implementá **todos los apartados 3–10 de `IMPLEM-H5.md`**, sin recortarlos a una maqueta. Como matriz mínima obligatoria:

| Área | Comportamiento verificable | Fuente de verdad / límites |
|---|---|---|
| Ventana principal | Identidad de contexto, ruta académica, salud, actividad, última captura válida y **cinco contadores globales separados**; estado no acreditado visible como N/D | Contadores derivados de una única `InstantaneaTaller`; no de eventos de clic |
| Órdenes | Alta por ID, descripción, tipos del catálogo; tabla de cinco estados, detalle, filtro y motivos de espera | `registrarOrden`, `consultar`; secuencias y catálogo quedan en núcleo |
| Primera propuesta factible | Mostrar orden, recursos físicos exactos, requisitos y **todas las causas** de espera; no reservar con la consulta; advertencia sin reserva | `consultar().valor().planificacion()`; no crear otro planificador |
| Confirmación humana | Diálogo explícito, enviar **la misma** `PropuestaInicio` mostrada y aceptada; una sola mutación; revalidación final en servicio | `confirmarInicio(PropuestaInicio)`; sin salteos ni sustitución silenciosa |
| Cierre y transiciones | Finalizar o fallar EN_CURSO; cancelar PENDIENTE; confirmación de acción e ID; estados terminales no reabren | `finalizar`, `fallar`, `cancelar` con validación de dominio |
| Recursos | Alta de TECNICO/PUESTO/HERRAMIENTA, tipo sólo de catálogo para herramienta, habilitar/inhabilitar **libres**, disponibilidad derivada y titular de asignación | `registrarRecurso`, `cambiarHabilitacion`; no liberar manualmente |
| Historial/revisiones | Secuencia, hora, operación, acciones y claves; revisiones existentes con motivo/sesión/cierre, sin sexto estado | `InstantaneaTaller`, `InformeRecuperacion`; H4-D02 |
| Simulación | Sección visible **«Pendiente de H6»**, sin motor, datos, estadísticas o automatización ficticios | H6 será incremento independiente |
| Inicio y salida | Apertura académica descartable segura y cierre con drenaje, diagnóstico y sin bloquear EDT | H5-D01, contexto/guardia existentes |

El recorrido imprescindible es: **arrancar con base nueva → registrar recursos → registrar orden → consultar propuesta sin reserva → confirmarla explícitamente → observar EN_CURSO con asignación → finalizar → observar FINALIZADA y recursos disponibles → consultar historial → cerrar**. Además permitir dos órdenes EN_CURSO simultáneas si hay recursos independientes; no implica introducir motor H6.

No crear un sexto estado para «esperando», «en revisión» o «pendiente de verificación». No incorporar nuevos flujos de cobros, usuarios, catálogos editables, CRUD libre, red, repositorios alternativos ni salteo de prioridad.

## 3. Arquitectura Swing y concurrencia del EDT

Seguí el diseño de `IMPLEM-H5.md` §5–7 como guía de archivos y responsabilidades: vista Swing y paneles, puerto de presentación propio, adaptador delegador a `ServicioTaller`, presentador comprobable con dobles y ejecutor de trabajos de segundo plano. Los nombres precisos pueden adaptarse mínimamente sin cambiar contratos ni alcance; explicar desviaciones justificadas.

**Reglas obligatorias:**

1. EDT: sólo entrada/captura de valores, diálogos, modelos y renderización; toda operación del servicio, acceso H2/JDBC, `informe()`, huellas/guardia/apertura/cierre fuera del EDT. Nunca bloquear con `Future.get`, `join`, `awaitTermination`, `sleep` o locks del núcleo dentro del EDT. Publicar resultados exclusivamente por `SwingUtilities.invokeLater` o equivalente correcto.
2. Presentación sin SQL, sin nuevas transacciones ni locks por técnico/herramienta; no mantener monitores Swing mientras el servicio muta. Una sola mutación en vuelo por ventana, deduplicación sin reenvíos al pulsar doble, teclado o confirmaciones repetidas; no cancelar una mutación JDBC ya iniciada.
3. Agrupar refrescos: una consulta en curso, a lo sumo una solicitud adicional pendiente; sin temporizador periódico. Tras mutar, consulta **completa** antes de rehabilitar acciones; aplicar captura de órdenes/recursos/contadores atómicamente en EDT. La vista no supone CONFIRMADA porque se pulsó un botón.
4. Una propuesta confirmada por el usuario es un objeto inmutable separado de la planificación actual. Su validez de negocio la decide la revalidación existente; un cambio visual de fila/revisión no autoriza reemplazarla por otra.
5. Respuestas tardías: comprobar generación de ventana/apertura, identidad del propietario, secuencia de petición y revisiones funcional/técnica antes de aplicar captura. **El mismo UUID de base tras reapertura no implica la misma generación.** Un resultado ya confirmado de mutación no se convierte en rechazo por una lectura posterior fallida.
6. Mensajería diferenciada según `ResultadoOperacion`: `CONFIRMADA`, `SIN_CAMBIO`, `RECHAZADA`, `ERROR_TECNICO`, `PENDIENTE_VERIFICACION`, excepciones inesperadas, y `CONFIRMADA` con advertencia técnica posterior. Ante incertidumbre o contexto `BLOQUEADO`, retirar propuestas y mutaciones accionables, conservar diagnóstico/ID/entrada; jamás reintentar automáticamente.
7. Cierre: detener admisión, drenar mutaciones ya iniciadas y cerrar contexto/guardia **fuera de EDT**. No `System.exit`, no eliminar `instancia.lock`, no abandonar guardias, no limpiar archivos de base y no anunciar «cierre limpio» sólo por retorno de `close()`. Si falla el cierre, mostrar advertencia y retener un estado seguro, no forzar la continuación.

## 4. Arranque académico descartable — H5-D01

Se autoriza **exclusivamente** la adaptación mínima y necesaria para crear una base **nueva** bajo una ruta única y validada `target/h5-*` de la raíz real del proyecto, posterior a una acción humana explícita en la GUI o en su flujo de inicio. Toda autorización es específica de esta ruta nueva y del modo `CREAR_NUEVA` existente. Reutilizar los mecanismos actuales `FabricaContextos`, `GuardiaInstancia`, `AutorizacionBase`, `InicializadorBase`, `PersistenciaH2`, auditoría y propietario/contexto único. Respetar `recuperacionFisica=true` requerido por el contrato cuando corresponda, sin extender permisos a archivos preexistentes.

- Validar raíz, pertenencia, enlaces, inexistencia de archivo H2 y ausencia de restos; rechazar antes que reusar/reparar. Guardar el diagnóstico de intento parcial, no reiniciarlo ni sobrescribirlo.
- **Prohibido abrir, leer por JDBC, migrar, reanudar, copiar, renombrar, borrar o inicializar** cualquier base real, incluida `datos/operativo`. No acceder a dicha ruta para «verificar» si hay datos. No ofrecer selector libre de archivos operativos ni permitir parámetros JDBC arbitrarios.
- No crear un proyecto ficticio con `pom.xml`/`AGENTS.md` copiados ni usar helpers `TestContextos` de test como implementación de producción. Usar un único registro de propietario y la guardia actual.
- Cada sesión académica nueva recibe un directorio nuevo; no reabrir desde GUI por defecto. No ejecutar `mvn clean` mientras esa sesión esté abierta ni borrar el directorio al salir; `target` es explícitamente descartable y puede ser limpiado cuando **no** haya procesos usándolo.
- Si la fábrica actual no permite una variante acotada de apertura nueva sin debilitar autorizaciones H4, **detenerse y explicar la incompatibilidad material**; no saltar auditoría, guardia, intención o control de procedencia para mostrar una ventana funcional.

Crear un punto de entrada de aplicación fuera de JUnit y un procedimiento Windows real de compilación/ejecución que incluya en classpath el JAR H2 disponible, verificando su ruta efectiva. No instalar servicios remotos ni suponer recursos inexistentes.

## 5. Recuperación H4 conocida y límite de H5-D02

H4 quedó **parcial** tras la decisión académica: una prueba de recorrido nominal aprobada en Windows, recuperación avanzada T02/T03/T09 pendiente y antecedente H3-T05 intermitente. H5 debe **mostrar correctamente** el estado y el diagnóstico que puede entregar el núcleo, pero no resolver por sí misma transacciones inciertas ni marcar recuperaciones como verificadas.

Presentar revisiones persistidas, última información válida y diagnóstico de salud. `finalizar` o `fallar` una EN_CURSO **únicamente** bajo contexto habilitado y tras autorización/revalidación de servicio. No exponer `ServicioRecuperacion.recuperar()` ni `aceptarAdopcion()` mediante controles de «recuperar/desbloquear» en el recorrido nominal. No eliminar claves foráneas, registros de intención, auditorías, guardias o comprobaciones de procedencia.

Una captura aparentemente correcta no demuestra recuperación posterior a caída. No declarar CA-16/CA-17 conformes, ni reescribir las pruebas antiguas para hacerlo. Si un error conocido de H4 bloquea el recorrido nominal nuevo, informar qué falla antes de considerar alguna intervención que traspase esta autorización.

## 6. Empaquetado y límite de aceptación — H5-D03

H5 **sí** debe contar con un mecanismo probado para abrir una ventana operable en Windows desde el proyecto Maven/Java 21, sin ejecutar JUnit. Proveer comando exacto y/o script simple de PowerShell que configure classpath de Java y H2 desde rutas reales, registre la línea de ejecución y no requiera Maven instalado globalmente para que IntelliJ pueda compilar.

El **JAR distribuible** y su prueba como artefacto autónomo pasan a **H7**. En H5 la referencia CA-25 es **PARCIAL**: se puede comprobar arranque/cierre local y guardia de instancia sobre la **misma base**, pero nunca afirmar cumplimiento integral de CA-25 mientras falte el JAR. Dos bases académicas diferentes pueden abrirse simultáneamente sin violar exclusividad *por archivo*: no reportarlo como defecto.

No modificar SPEC/PLAN para eliminar CA-25, ni adicionar dependencias/plug-ins Maven innecesarios. No implementar H6/H7.

## 7. Pruebas obligatorias y criterios de aceptación del incremento

Crear tests JUnit 5 finitos, deterministas en lo posible, sin bibliotecas nuevas de mocks ni sustitutos en memoria para demostrar durabilidad. Usar fixtures de archivo H2 **sólo** bajo `target/h5-*` (o rutas internas de test descartables claramente identificadas, también bajo `target`) y dobles de interfaz/puerto para probar la presentación sin GUI real. No interferir con bases o procesos externos. Ajustar clase/paquete según estructura real.

### Matriz de ensayos H5

| Caso | Acreditación mínima y evidencia |
|---|---|
| H5-T01 | Alta de orden, recurso y lectura; cinco contadores separados incluso cuando valen 0; captura coherente |
| H5-T02 | Primera factible, causas de espera completas y propuesta sin reserva; filtros/selección no saltean prioridad |
| H5-T03 | Diálogo con rechazo/cancelación sin inicio; aceptación envía **propuesta exacta** una sola vez |
| H5-T04 | Inicio, asignación EN_CURSO, cierre, liberación, fallo/cancelación válidos y estados terminales intactos |
| H5-T05 | EDT libre mientras servicio queda bloqueado por latch controlado; toda modificación de vista en EDT |
| H5-T06 | Doble clic/teclado/ráfaga no generan múltiples operaciones; refrescos se agrupan sin cola infinita |
| H5-T07 | Lecturas y mutaciones con respuestas tardías, revisiones cruzadas y reapertura de misma base/otra generación; descartar captura vieja sin perder resultado confirmado |
| H5-T08 | Todos los estados de `ResultadoOperacion`, diagnóstico técnico nulo o existente, y confirmación con fallo posterior de refresco/cierre; sin éxito supuesto |
| H5-T09 | BLOQUEADO/PENDIENTE_VERIFICACION retira acciones, conserva entrada/diagnóstico y no reintenta mutación |
| H5-T10 | Arranque sólo a nueva ruta segura `target/h5-*`, rechazo de enlaces/restos/ruta conocida y no acceso a `datos/operativo`; reutilización de guardia/propietario |
| H5-T11 | Segunda apertura de **misma** base rechazada; apertura de base distinta independiente; salida drena operación iniciada, sin liberar guardia antes de tiempo |
| H5-T12 | Integración sobre H2 de archivo nueva: registro → propuesta → confirmación → dos EN_CURSO cuando hay recursos → finalización/fallo → historial/cierre |
| H5-T13 | Recorrido visual manual Windows, navegación, presentación de causas, cinco contadores, marcador H6, cierre no bloqueante y comando probado; guardar checklist separado |
| H5-T14 | Regresión nominal académica H4 y selección H2/H3 pertinentes; reportar defectos anteriores sin bajar aserciones ni repetir suite completa interminablemente en MXC |

Los identificadores H5-T01…T14 se proponen **para ordenar evidencia**, no crean requisitos nuevos ni sustituyen los CA de SPEC. Documentar cada prueba realmente ejecutada con comando, cantidad, errores y límites. Un caso puede abarcar varios métodos JUnit; **no declarar 14/14 si no se ejercitaron**.

Al finalizar una operación confirmada, consultar de nuevo antes de mostrar un estado vigente. Verificar que **un error al refrescar no repita la mutación ni niegue su commit conocido**. Las pruebas de concurrencia de UI no sustituyen evidencia del motor H6 ni demuestran ausencia universal de carreras.

### Protocolo de validación en entorno MXC/Windows

1. Comprobar compilación de las clases Java 21 y tests; ejecutar las pruebas focales que efectivamente permita el entorno.
2. Si MXC produce el error conocido H2 `90097-232 READONLY=true`, **no inferir automáticamente que todo fallo procede de MXC**. Distinguir resultados de pruebas sin H2 de las que efectivamente requieren archivo, reportar bloqueo del entorno y **preparar comando reproducible para PowerShell de Windows**. No modificar permisos, dependencia, política de sandbox o pruebas sólo para ocultarlo, ni repetir indefinidamente.
3. Si aparecen fallos reales en tests focales, corregir dentro de H5, volver a probar focalmente y explicar causas. No introducir un bucle de ejecuciones de suite global por fallos H4 ya conocidos.
4. Guardar evidencias textuales en rutas de logs previstas **sin publicar ni calcular métricas agregadas ahora**. Los JSONL de Codex los captura el comando CLI externo y se analizan posteriormente.
5. No atribuir a esta ejecución pruebas que sólo se ejecutaron históricamente ni afirmar validación visual Windows desde MXC; si no hay GUI disponible, dejar checklist manual listo para el usuario.

## 8. Secuencia integral de ejecución: sin autorizaciones parciales

Organizá **una única implementación integral** con controles técnicos internos:

1. **Inspección y línea base:** fuentes vigentes, salud del repositorio, restricciones, API real, línea base nominal H4, archivos preexistentes.
2. **Arranque académico protegido:** entrada H5-D01 e integración con registro/guardia/propietario; probar base descartable y no acceso a rutas operativas.
3. **Puerto y presentador:** adaptador fino, ejecutor asíncrono, deduplicación, estado de la GUI, captura, errores, generación y respuestas tardías; pruebas sin UI.
4. **Interfaz:** ventana con tres secciones, primero flujo Órdenes y luego Recursos, historial y revisiones; rotulación H6. Comportamiento con teclado/diálogos, cinco contadores.
5. **Integración/validación:** pruebas focales, recorrido académico real desde ventana cuando posible y regresión seleccionada; reunir evidencias y corregir defectos de H5 detectados.
6. **Entrega:** procedimiento Windows de ejecución, checklist manual, reporte e inventario de cambios, fallos y pendientes; detenerse para auditoría humana.

**No pedir aprobaciones tras cada etapa** dentro de la misma ejecución; continuá si está dentro del diseño aprobado. Si un bloqueo material impide operar sin modificar reglas de negocio, recuperación H4, SQL/integridad o autorización sobre datos reales, detenete con explicación y estado de trabajo; **no inventes una implementación degradada ni declarés éxito**.

## 9. Límites estrictos de intervención

- **Prohibido cualquier acceso operativo real** a `datos/operativo` y a bases de usuario: nada de JDBC, lectura del contenido, migración, reanudación, reparación, restauración, copias o borrado. Sólo bases nuevas de prueba en `target/h5-*`, con ruta real validada y autorización explícita por fixture.
- **Prohibido** cambiar `SPEC.md`, `PLAN.md`, `AGENTS.md`, `IMPLEM-H5.md`, `docs/APROBACION-DISENO-H5.md`, prompts aprobados, decisiones previas, informes históricos, pruebas existentes H1–H4 para esconder defectos, ni registros históricos de métricas. La descripción del problema no autoriza alterar integridad del núcleo.
- Sólo crear/modificar archivos de H5 justificables por el diseño (paquete `interfaz`, arranque y adaptación mínima de `FabricaContextos`, nuevos tests, recurso de arranque y reporte); no reescribir dominio/coordinador/servicio/SQL ni cambiar clases H1–H4 salvo error de compilación introducido por la propia interfaz y sin alterar contratos. Ante necesidad real de cambio de negocio, detenerse.
- Java 21, Maven existente, H2/JDBC y JUnit 5; no nuevos frameworks ni dependencias salvo autorización posterior. Preferir cero cambios `pom.xml` y evitar IDE/configuración global.
- **No `git add`, `git commit`, `git push`, cambios de ramas o remotos, ni operaciones sobre GitHub del grupo o su PR**. El clon del grupo es exclusivamente de publicación, no entorno de desarrollo; preservarlo.
- No borrar o sobrescribir archivos ajenos a H5, logs de Codex, evidencia histórica, sesiones de base ajenas, `target` completo ni procesos en marcha. No matar JVM desconocidas.
- No implementar recuperación avanzada H4, simulación H6, empaquetado JAR final H7 ni cálculos, rúbricas o informes cuantitativos H8. Las mediciones de Codex se recaban mediante los JSONL externos, sin inventar ni sumar intervención de ChatGPT como tokens de Codex.

## 10. Entregables y reporte final de Codex

Al terminar, crear **`H5-REPORTE-IMPLEMENTACION.md`** con estos apartados:

1. Resultado general: COMPLETO, PARCIAL o BLOQUEADO, diferenciando código generado de evidencia acreditada y señalando **CA-25 parcial** por JAR diferido.
2. Archivos nuevos y modificados, principales contratos efectivos, y desviaciones mínimas respecto de `IMPLEM-H5.md` v0.1 y H5-D01/D02/D03.
3. Comando preciso para compilar y **abrir la GUI realmente en Windows** desde la raíz original; dependencias y rutas, tiempo de vida de la base descartable, sin JUnit.
4. Matriz H5-T01…T14 con estado **PASÓ / FALLÓ / NO EJECUTADO / BLOQUEADO POR ENTORNO**, comando y evidencia, sin convertir una expectativa en resultado.
5. Checklist de inspección visual manual Windows que el usuario pueda completar en una sola sesión y registrar con capturas; no marcarlo ejecutado si Codex no lo hizo.
6. Resultados compilación, pruebas focales, regresión relevante y límites MXC vs. Windows; advertencias, fallos conocidos H4 y observación H3-T05 sin inventar ausencia universal de carreras.
7. Garantías de seguridad de acceso a bases, cierre/guardia/EDT, dobles de eventos, incertidumbre y propuestas exactas, con pruebas que las apoyan y aspectos no comprobados.
8. Pendientes reales, limitaciones y cualquier decisión humana futura indispensable. No efectuar commits ni iniciar H6/H7.

**Detenete al finalizar H5.** La generación de código no implica aprobación final: el responsable realizará comprobación Windows, auditoría del incremento y eventual commit local por separado. No prepares nuevas solicitudes de implementación ni avances al siguiente hito sin autorización.
