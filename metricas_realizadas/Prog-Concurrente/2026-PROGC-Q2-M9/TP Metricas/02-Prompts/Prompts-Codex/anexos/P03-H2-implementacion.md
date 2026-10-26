# P03-H2 | Anexo técnico de implementación del hito H2 | ReparaBarrio

| Control documental | Valor |
|---|---|
| **Versión del anexo** | **0.1** |
| **Fecha** | **13/10/2026** |
| **Estado** | **APROBADO EN CONTENIDO. PENDIENTE DE AUTORIZACIÓN DE EJECUCIÓN** |
| **Metodología** | Spec-Driven Development (SDD), fase 3: Implementación, hito H2 |
| **Prompt principal** | `prompts/P03-implementacion.md` v0.1, aprobado y registrado en Git local |
| **Fuentes normativas** | `SPEC.md` v0.2 aprobada, `PLAN.md` v0.2 aprobada con adenda humana 8.7, `AGENTS.md` |
| **Punto de partida del código** | **H1 aprobado**, commit local `34edb1c`, Java 21, Maven y 17 pruebas JUnit 5 aprobadas |
| **Alcance solicitado** | **H2 exclusivamente: primer recorrido vertical durable y coordinador transaccional** |
| **Hitos no autorizados** | H3 a H7 y P04; H4 conserva la recuperación y reconciliación integral |
| **Resultado de la futura ejecución** | Código Java, esquema/recursos H2 estrictamente necesarios, dependencias Maven y pruebas de integración; informe final de resultados por consola |

## VAMOS A IMPLEMENTAR H2

Continuamos **ReparaBarrio**, aplicación Java local de escritorio para la administración de órdenes de reparación y recursos físicos exclusivos. Ya está aprobado y versionado **H1**, que contiene el dominio, el planificador, las propuestas inmutables sin reserva y las pruebas unitarias correspondientes. **No parta de cero ni reimplemente H1.**

**Antes de modificar el código, lea `SPEC.md`, `PLAN.md`, `AGENTS.md`, `prompts/P03-implementacion.md` y las clases/pruebas existentes relevantes de H1.** Aplique el orden normativo: `SPEC.md` define el comportamiento, `PLAN.md` define arquitectura e hitos, P03 establece las restricciones generales y este anexo limita el trabajo a H2. La interpretación humana de **PT-06**, ratificada en la sección 8.7 de `PLAN.md`, es parte de la planificación aprobada.

No altere `SPEC.md`, `PLAN.md` ni la documentación para hacer coincidir los requisitos con la implementación. Si descubre una incompatibilidad material que no pueda resolver respetando ambos, **deténgase y explique el conflicto**.

## PROBLEMA Y OBJETIVO DEL HITO

H1 demostró reglas de negocio sobre instantáneas en memoria, pero **aún no conserva operaciones reales**. H2 debe habilitar, sobre una base **H2 de archivo y JDBC**, el primer recorrido transaccional completo:

**Registrar recursos y una orden → consultar primera factible y obtener propuesta → confirmar unidades físicas exactas y cambiar a EN_CURSO → finalizar o fallar y liberar los recursos → consultar historial y estado persistido.**

También incluye cancelación de PENDIENTE, cambios de habilitación de recursos libres, secuencia de registro conservada, catálogo inicial aprobado y consultas coherentes. Cada operación efectiva se confirma **entera o no se confirma**. El éxito se informa **sólo después de un commit confirmado**. Un error de resultado desconocido se trata según PT-06 y **no** como rollback supuesto.

Esta entrega es un **núcleo transaccional demostrable sin interfaz gráfica**, no el MVP completo ni la prueba concluyente de concurrencia H3 o recuperación H4.

---

## PROMPT PATTERN (PROMPT 3 - IMPLEMENTAR, APLICADO A H2)

**Implementa el sistema utilizando:**

- **`SPEC.md` como definición de requisitos**. Preserve los cinco estados, la primera pendiente factible por secuencia, las unidades exactas, las propuestas sin reserva, el historial, las transiciones autorizadas, las reglas de disponibilidad y las garantías de conservación.
- **`PLAN.md` como plan técnico**. Respete el monolito modular Java/Maven, el núcleo reutilizado de H1, el coordinador único por contexto con `ReentrantLock(true)`, las transacciones H2/JDBC y las decisiones **PT-01 a PT-08**; materialice sólo la parte que corresponde a H2.

**Respeta estrictamente ambos documentos.** **No agregues funcionalidades que no estén especificadas.** **Genera todos los archivos necesarios para ejecutar y probar H2**, pero **no** los archivos de los hitos posteriores. No interprete «todos los archivos» como permiso para construir ahora la interfaz Swing, el simulador o el MVP entero.

## 1. ARQUITECTURA Y CÓDIGO EXISTENTE

1. **Conserve los paquetes y contratos útiles de H1** (`dominio`, `planificacion`, `InstantaneaDominio`, `Orden`, `Recurso`, `SeleccionRecursos`, `PropuestaInicio`, `Planificador`, `OrdenNaturalId`). Amplíelos sólo cuando haga falta integrar persistencia; no duplique el planificador en SQL ni invente una segunda fuente de verdad en memoria.
2. Agregue en forma proporcionada los componentes H2 previstos por PLAN: servicio/fachada de operaciones del taller, coordinador, resultados tipados, contratos transaccionales y repositorio/adaptador **H2/JDBC**. Es aceptable consolidar clases auxiliares pequeñas, manteniendo responsabilidades y pruebas independientes. No introduzca ORM, Spring, servidor, API REST, nuevos módulos Maven ni frameworks no aprobados.
3. **H2 es la fuente mutable de verdad.** Una `InstantaneaDominio` es una vista inmutable y descartable construida sobre una lectura coherente; no autoriza mutaciones con datos antiguos. Las propuestas son valores inmutables, no reservas.
4. Los métodos de negocio deben poder invocarse **sin Swing ni simulador**, desde pruebas o un cliente futuro. No fabrique ventanas, animaciones ni un `main` que aparente completar funcionalidades posteriores.
5. Mantenga **una conexión y una transacción por operación**, sin compartir `Connection` entre hilos. Separe puertos de aplicación e implementaciones JDBC. Toda consulta que necesite una visión coherente debe capturarse bajo el mismo coordinador y transacción de lectura.
6. **Configuración de almacenamiento inyectable y ruta estable**, sin rutas personales codificadas. Use archivos de prueba exclusivos en directorios temporales/descartables; nunca escriba en datos operativos preexistentes del usuario. No use `AUTO_SERVER`, ni publique H2 por red.

## 2. PERSISTENCIA, CATÁLOGO Y OPERACIONES MÍNIMAS

Diseñe sólo el esquema requerido para conservar correctamente: catálogo de tipos, órdenes y requisitos por tipo, unidades físicas de técnico/puesto/herramienta, habilitación, secuencia de antigüedad, asignaciones **activas** completas, historial de operaciones y recursos, y un identificador interno único por operación mutante para el tratamiento PT-06. Evite un campo mutable `ocupado` redundante: **OCUPADO se deriva de asignación activa**.

Reglas obligatorias:

- **Registro de orden:** ID textual obligatorio y no reutilizable; descripción no vacía; cero o una unidad de cada tipo reconocido; secuencia positiva, única y creciente **asignada transaccionalmente**. Un registro rechazado no confirma orden, historial ni avance de prioridad. Permita tipos reconocidos aunque todavía no haya unidades físicas.
- **Alta de recursos:** IDs únicos **dentro de cada categoría** y no reutilizables; herramientas asociadas a un tipo reconocido; no crear un recurso inicialmente ocupado. Admitir `T2` y un puesto `T2` como IDs distintos por ámbito. No transformar silenciosamente IDs.
- **Catálogo:** tipos iniciales `MULTIMETRO`, `SOLDADOR`, `TALADRO` (PT-05 y D016), sin unidades físicas ficticias. Inicialización **idempotente**, preservada al reabrir; el catálogo persistido y el `enum TipoHerramienta` de H1 no deben divergir ni actuar como fuentes contradictorias. No crear editor de tipos.
- **Propuesta:** seleccionar sobre instantánea transaccional la **primera PENDIENTE factible por secuencia** y las unidades libres de menor ID natural por categoría/tipo, reutilizando H1. Consultar propuesta **no** inserta asignaciones ni historial ni bloquea recursos después de terminar la consulta.
- **Confirmar inicio:** bajo el coordinador, leer el estado vigente y **revalidar** `PENDIENTE`, primera factible y las **mismas unidades físicas exactas** de la propuesta original, con categorías/tipos correctos y todas libres/habilitadas. No exigir que sigan siendo las menores disponibles; una unidad ajena disponible no invalida por sí sola. Persistir **EN_CURSO + todas las asignaciones + historial + ID de operación en una sola transacción**. Rechazos sin escrituras de negocio confirmadas; sin sustitución automática ni caducidad temporal.
- **Finalizar o fallar:** exclusivamente EN_CURSO; pasar a FINALIZADA o FALLIDA, liberar **sólo** sus asignaciones activas y conservar historial/ID de operación **en una sola transacción**. Repetir o contradecir el cierre se rechaza sin doble liberación.
- **Cancelar:** sólo PENDIENTE → CANCELADA con historial y transacción; no liberar recursos ajenos.
- **Habilitación:** permitir sólo cambios de recursos libres entre habilitado/inhabilitado; rechazar modificar recurso OCUPADO; no crear ni destruir asignaciones manualmente.
- **Consultas:** exponer instantánea consistente de órdenes, recursos, disponibilidad derivada, asignaciones activas, causas de espera e historial confirmado. No mostrar fragmentos de una operación inconclusa como estado válido.

Incluya restricciones SQL **defensivas** para claves, relaciones y exclusividad de cada unidad en asignaciones activas. La base ayuda a impedir doble asignación, pero **no sustituye la validación de completitud, prioridad y recursos exactos realizada por el dominio/aplicación**. Una excepción de SQL inesperada no debe camuflarse como simple rechazo de negocio.

## 3. COORDINACIÓN, ATOMICIDAD Y PT-06

- Para cada contexto de datos, use un **`ReentrantLock(true)`**. Orden obligatorio: adquirir coordinador → abrir conexión/transacción → capturar estado → validar → escribir → `commit` o `rollback` → cerrar recursos JDBC → liberar bloqueo en `finally`. No mantener el bloqueo al solicitar confirmación humana, renderizar ni esperar reparaciones.
- **Una sola operación crítica por contexto**. Todas las mutaciones pasan por la fachada/coordinador; no exponer un repositorio público que permita saltarse las reglas. Las pruebas de carreras múltiples y solapamiento de trabajos quedan principalmente para H3, pero la exclusión mutua y la atomicidad deben existir ya en H2.
- **Éxito confirmado:** informar éxito únicamente después de `commit` conocido y completo. **Rechazo de negocio:** comprobar antes de conservar cambios y devolver motivo, sin efectos persistentes. **Rollback comprobado:** devolver error técnico con ausencia comprobada de efectos. No equiparar automáticamente `SQLException` con rollback confirmado.
- **Commit o rollback incierto (PT-06):** identificar un resultado cuyo estado final no puede acreditarse, **no** declararlo éxito ni rechazo. Cada operación mutante tiene un ID interno único persistido atómicamente con sus efectos/historial, apto para posterior reconciliación. Mantener el **contexto afectado bloqueado para nuevas mutaciones**, sin reintentos a ciegas; exponer diagnóstico/resultado `PENDIENTE_VERIFICACION` o equivalente **como resultado de operación**, nunca como sexto estado de orden ni reserva. El otro contexto, si existiera y estuviera sano, no debe bloquearse.
- **Límite del hito:** H2 incluye el **bloqueo preventivo fail-closed** y la base de identificación durable para PT-06. La reconciliación completa ante caída/reinicio, el indicador de sesión, la revisión humana de EN_CURSO tras interrupción y la guardia `FileChannel` pertenecen a **H4**. No declare implementado CA-17 integral ni recuperación ante interrupciones porque una prueba de rollback haya pasado. Si no puede preservar la seguridad del bloqueo preventivo dentro del alcance H2, exponga explícitamente la limitación y no habilite uso operativo real.

## 4. ARCHIVOS PERMITIDOS Y LÍMITES DEL HITO

Puede modificar o agregar **únicamente** `pom.xml`, clases Java necesarias de `src/main/java`, pruebas de `src/test/java` y recursos SQL/configuración inicial esenciales de `src/main/resources` o `src/test/resources`. La referencia de H2 para el plan es **H2 2.3.232**; mantenga JUnit 5 y Java 21 ya configurados, y verifique la compatibilidad real. Si no se puede resolver H2 o un plugin, informe el bloqueo **sin cambiar de versión o tecnología por su cuenta**.

No implemente ahora: guardia de segunda instancia, recuperación integral y reconciliación de H4; interfaz Swing de H5; motor concurrente, escenarios y métricas de H6; empaquetado/distribución final H7; ni validación integral P04. Tampoco genere repositorios simulados como sustituto del H2 real, ni datos de taller ficticios precargados como si fueran operativos.

**Proteja los documentos y la trazabilidad:** no modificar `SPEC.md`, `PLAN.md`, `AGENTS.md`, `DECISIONES.md`, `prompts/`, `docs/`, `evidencias/`, `metricas/`, archivos de consignas/rúbricas ni `.git` o su configuración. No realizar `git add`, `commit`, `push`, cambios de rama, configuración de remotos ni conexión con GitHub. No eliminar cachés Maven, archivos ajenos ni datos operativos; no instalar herramientas. La invocación externa de Codex conservará su propio JSONL y medición de tokens.

## 5. PRUEBAS EXIGIDAS PARA DAR POR IMPLEMENTADO H2

Implemente **pruebas JUnit 5 de integración contra H2 real**, preferentemente con bases **de archivo separadas por prueba** y sin usar datos operativos. Ejecute la batería H1 previa y las nuevas pruebas, **si las dependencias están accesibles**; si el sandbox impide descargar o ejecutar, informe qué se compiló y qué no, sin inventar resultados y sin repetir acciones bloqueadas.

Mínimos verificables:

1. **Regresión H1:** conservar las 17 pruebas existentes y su comportamiento; añadir pruebas nuevas si detecta un caso de integración no cubierto.
2. **Registro y secuencia:** orden y recurso válidos/inválidos, catálogos y ámbitos de IDs, duplicados históricos, tipos sin unidades; un rechazo no deja filas/eventos ni secuencia confirmada indebida.
3. **Primera factible y propuesta sin reserva:** orden anterior imposible permite proponer la siguiente; una primera factible sin confirmar impide iniciar una posterior (PA-13/CA-19); consultar repetidamente no muta nada.
4. **Inicio atómico:** verificar tras commit el conjunto completo de asignaciones, estado EN_CURSO e historial; intentar una unidad exacta ocupada, inhabilitada, de tipo incorrecto, faltante o una propuesta desactualizada produce rechazo sin estado parcial. Una nueva unidad menor **ajena** no invalida por sí sola una propuesta originalmente válida.
5. **Rollback real:** inyectar fallo dentro de la transacción **antes del commit** después de haber intentado escribir parte del agregado; demostrar con una **conexión nueva** que no persistieron transiciones, asignaciones, historial ni incremento confirmado no debido. No confundir esta prueba con la recuperación H4.
6. **Cierre y cancelación:** cerrar FINALIZADA y FALLIDA con liberación completa/historial, rechazar dobles cierres sin liberar recursos reasignados; cancelar sólo PENDIENTE; habilitación de libre versus ocupado.
7. **Persistencia básica:** cerrar conexiones y reabrir limpiamente la **misma base de prueba**, comprobar órdenes, secuencias, unidades, asignaciones, catálogo e historial. Reapertura limpia de H2 **no acredita** recuperación tras interrupción ni guardia de instancia H4.
8. **Protección PT-06:** comprobar al menos la clasificación de resultado incierto mediante fallo simulado del adaptador (sin falsear que H2 confirmó o revirtió), bloqueo de nuevas mutaciones del contexto y no-reintento automático. Dejar expresamente para H4 la reconciliación e inyección real de caída del proceso.
9. **Lecturas coherentes:** reconstruir una instantánea de dominio válida y validar que el estado persistido no contiene asignaciones incompletas ni unidades activas duplicadas; casos de rechazo no producen hechos parciales observables.

Relacionar los tests de H2 con **CA-01/02/03/05/06/10/11/12/15/19** según la cobertura realmente implementada, **CA-16 parcialmente** (reapertura normal) y **CA-17 parcialmente** (rollback y prevención); no anunciar CA-07/08/09/13/14/21 ni CA-16/17 completos antes de H3/H4. Mantener los identificadores RF/RN/INV originales y reconocer qué evidencia sigue faltando.

## 6. ORDEN DE TRABAJO RECOMENDADO (H2)

Desarrolle en incrementos pequeños dentro de esta única autorización, con pruebas que detecten pronto errores estructurales:

1. Extender `pom.xml` con **H2/JDBC**, inicializar esquema y catálogo sobre una base temporal de prueba. Comprobar conexión y reinicialización idempotente.
2. Implementar contratos de repositorio/unidad de trabajo, cargas de instantánea e historial coherentes, con tests de lectura/escritura mínimos.
3. Agregar fachada y coordinador; registrar órdenes/recursos con secuencia y validación completa, y consultar primera factible sobre H2.
4. Implementar confirmación atómica, cierre/fallo, cancelación y habilitación, con pruebas de rollback y rechazo sin efectos.
5. Incorporar identificadores de operación, clasificación y bloqueo preventivo PT-06; completar pruebas de integración y volver a ejecutar la regresión H1.

**No considere aprobado H2 sólo porque compila.** Tampoco pase a H3 automáticamente. Si hay un fallo material de pruebas, informe el caso y su causa; no lo oculte desactivando pruebas, degradando invariantes o sustituyendo JDBC por una colección en memoria.

**Disciplina de ejecución:** evite relecturas repetitivas de los mismos documentos, barridos recursivos innecesarios y comandos ya rechazados por la política del sandbox. No borre ni reconstruya cachés Maven para intentar desbloquear dependencias. Si un comando de compilación o descarga queda bloqueado por la seguridad del entorno, registre ese impedimento una vez, preserve los archivos creados y deje la verificación restante para la terminal del usuario con autorización. No convierta un error de infraestructura en un cambio funcional o tecnológico.

## 7. RESULTADO SOLICITADO Y PUNTO DE DETENCIÓN

Al finalizar, **deténgase en H2** e informe por consola, sin crear documentos auxiliares:

1. Archivos creados/modificados y motivo, indicando reutilización o cambios en H1.
2. Operaciones de negocio realmente implementadas, RF/RN/INV y CA cubiertos **completa o parcialmente**.
3. Comandos de Maven/pruebas realmente ejecutados, número de tests/fallos/errores, evidencia de commit y rollback, o impedimentos explícitos (incluidas limitaciones de red/sandbox).
4. Resultado de PT-06 en H2: qué protección preventiva ya funciona y qué queda pendiente de H4.
5. Riesgos/defectos conocidos, eventuales discrepancias y decisiones que requieren revisión humana.
6. Qué seguirá en H3, **sin implementarlo ni ejecutar su prompt**.

**No realice commit ni modificación documental.** La persona responsable verificará cambios, pruebas y tokens mediante el JSONL externo, decidirá si hace falta una corrección localizada y autorizará de forma separada el cierre de H2.

---

**Nota de autorización:** este anexo P03-H2 v0.1 fue **aprobado en contenido por la persona responsable el 13/10/2026**. Su aprobación permite guardarlo en `prompts/anexos/P03-H2-implementacion.md` y registrarlo en Git exclusivamente local, sin alterar el P03 principal. **La ejecución de Codex para H2 requiere autorización expresa separada** y su propio registro de tokens. Hasta entonces, no ejecutar Codex, instalar dependencias ni modificar código. En ningún caso se tocará el GitHub del grupo sin permiso explícito posterior.
