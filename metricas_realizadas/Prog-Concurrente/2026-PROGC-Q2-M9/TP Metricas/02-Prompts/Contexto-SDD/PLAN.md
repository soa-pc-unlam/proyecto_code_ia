# ReparaBarrio — Plan técnico

| Control documental | Valor |
|---|---|
| Versión | 0.2 refinada |
| Fecha | 13/10/2026 |
| Estado | APROBADA por validación humana, con adenda de cierre posterior a R02 |
| Metodología y fase | Spec-Driven Development (SDD), fase 2: Planificación |
| Fuente funcional | SPEC.md v0.2 aprobada, incluida D016 y el cierre humano de su sección 14 |
| Solicitud | P02 v0.2, prompts/P02-planificacion.md, aprobado para ejecución de planificación |
| Refinamiento recibido | R02 v1.0, 13/10/2026, sobre PLAN.md v0.1 |
| Instrucciones | AGENTS.md |
| Entregable | Únicamente PLAN.md; no autoriza implementación |

Se propone un diseño para revisión, sin modificar requisitos ni reabrir PA-01 a PA-13. Las referencias históricas a aprobación pendiente y exclusión de planificación dentro de SPEC.md describen su fase anterior; prevalece el cierre humano de la sección 14 y la autorización de P02 para esta fase. No se generaron código, configuraciones, migraciones ni pruebas ejecutables; no se construyó ni ejecutó la aplicación. PT-01 a PT-08 están aprobadas por decisión humana en R02; versiones y parámetros requieren comprobación local futura. La validación humana final de PLAN permanece pendiente. No se hicieron consultas externas.

## 1. ARQUITECTURA

### 1.1 Recomendación y dependencias

Monolito modular en capas, en un único proyecto Maven y proceso Java. Swing y el simulador son adaptadores que solicitan casos de uso; ambos utilizan el mismo dominio, planificador, coordinador y contratos de persistencia. Cada contexto posee su propia base y su propio coordinador único. «Único» significa una autoridad de sincronización por conjunto de datos: compartir un bloqueo entre bases aisladas serializaría innecesariamente ambos modos.

Dependencias permitidas: interfaz → aplicación; simulación → aplicación y métricas; aplicación → dominio, planificación y puertos de persistencia; planificación → dominio; persistencia JDBC → puertos de aplicación y dominio; arranque → todos para componerlos. Dominio no depende de Swing, JDBC, simulación ni aplicación. Los puertos pertenecen a aplicación; JDBC los implementa. La aplicación no conoce clases JDBC concretas. No habrá dependencias circulares ni reglas de negocio en consultas SQL, pantallas o tareas ficticias: SQL incorpora restricciones defensivas, y el núcleo decide la regla funcional.

La comunicación utiliza comandos, resultados e instantáneas inmutables. La interfaz y el simulador no reciben repositorios, conexiones ni entidades mutables compartidas. Una consulta puede quedar antigua al mostrarse; confirma mediante un nuevo caso de uso, nunca mediante edición de la instantánea. El contexto figura explícitamente en las propuestas y resultados.

### 1.2 Fuente de verdad y conservación

La base H2 de cada contexto es la única fuente mutable de estado confirmado. Objetos de dominio cargados durante una transacción son locales y descartables; modelos Swing e instantáneas son proyecciones de lectura. No habrá inventario mutable paralelo en memoria ni caché utilizada para autorizar un inicio. La disponibilidad se derivará de dos hechos persistidos: habilitación de la unidad y existencia de asignación activa. Con asignación activa es OCUPADO; libre e inhabilitada es FUERA_DE_SERVICIO; libre y habilitada es DISPONIBLE. Esto evita persistir un indicador de ocupación independiente de las asignaciones.

El almacenamiento lógico incluirá órdenes, requisitos por tipo, catálogo, unidades por categoría, asignaciones activas, historial con recursos, contador de antigüedad, identificador interno único de operación conservado atómicamente junto a efecto/historial y metadatos de sesión/revisión. Estado, asignación activa e historial se confirman en una misma transacción. Las asignaciones concluidas quedan representadas en el historial, sin confundirse con las activas. No se eliminan órdenes ni recursos operativos ni se reciclan IDs. La secuencia se obtiene de un contador transaccional, junto con el registro e historial; un rechazo no confirma incremento. Se ordenan pendientes por esa secuencia, nunca por fecha o ID.

Después del commit confirmado se devuelve un resultado confirmado y se solicita una nueva instantánea. Los cambios en pantalla se realizan en EDT, sin conservar el bloqueo del coordinador. Si falla el refresco, el cambio ya confirmado sigue siendo válido: se informa «operación conservada; no se pudo actualizar la vista» y se permite recargar. Un error visual no se anuncia como rechazo del cambio persistido. Las instantáneas llevan una revisión del contexto para evitar que una respuesta tardía reemplace una más reciente; dicha revisión no caduca propuestas ni es condición global para confirmarlas.

### 1.3 Plataforma propuesta

| Elemento | Tecnología aprobada / versión de referencia por comprobar | Justificación y verificación pendiente |
|---|---|---|
| Java y Swing | JDK 21 LTS, Swing incluido en java.desktop | Plataforma estable, concurrencia estándar y GUI sin dependencia adicional. Verificar distribución y disponibilidad en Windows del equipo. |
| Maven | 3.9.9 | Un solo ciclo de construcción y dependencias explícitas. Referencias por comprobar: compiler plugin 3.13.0 y Surefire 3.5.2, sin rangos dinámicos. |
| JUnit | JUnit Jupiter 5.11.4, sólo pruebas | Pruebas parametrizadas, aserciones y límites temporales; mantener JUnit 5 solicitado. |
| H2/JDBC | H2 2.3.232, JDBC del JDK | Base embebida transaccional de archivo y bases en memoria; evita servidor y ORM. Verificar comportamiento real de commit, sincronización de archivo y recuperación. |

Son versiones concretas de referencia, no afirmaciones de ser las últimas ni una certificación de seguridad o compatibilidad ejecutada. H0 comprobará disponibilidad local, resolución de dependencias y compatibilidad. Si falta una versión, se documentará sustitución compatible, sin cambiar por cuenta propia las tecnologías aprobadas. No se descargará ni instalará nada en esta fase. La ejecución futura debe funcionar localmente sin red, aunque la preparación de dependencias Maven requiere disponibilidad previa o autorización para obtenerlas.

Para operativo se propone H2 de archivo, sin servidor ni AUTO_SERVER, con escritura síncrona al confirmar (parámetro WRITE_DELAY=0, sujeto a validación con la versión elegida). No se anunciará durabilidad basándose sólo en que JDBC devolvió commit: se comprobará la configuración efectiva y la recuperación por interrupción de proceso. Persisten límites de hardware, sistema de archivos y pérdida física del almacenamiento; no se promete inmunidad universal ante cortes eléctricos o destrucción del disco.

Una instancia de ejecución se impone antes de abrir H2 mediante bloqueo exclusivo FileChannel sobre un archivo fijo del proyecto, retenido hasta cerrar conexiones. Una segunda instancia muestra diagnóstico y termina sin operar. El bloqueo embebido de H2 actúa como segunda defensa, no como habilitación multiusuario. No se expone consola H2 ni edición externa de la base durante la ejecución.

Swing reduce preparación y dependencias frente a JavaFX, pero exige separar rigurosamente EDT y trabajo en segundo plano. JDBC ofrece límites de transacción visibles frente a ORM, a costa de SQL y mapeos manuales. H2 evita desplegar un servidor; un archivo JSON exigiría reconstruir atomicidad, recuperación y exclusividad. Microservicios, REST, bases remotas y múltiples proyectos añaden despliegue y coordinación sin necesidad del MVP y quedan descartados. No se identifica impedimento concreto para las tecnologías de referencia; cambiar una tecnología requerirá evidencia y aprobación; sustituir una versión no disponible por otra compatible se documentará conforme a PT-02.

## 2. MÓDULOS

| Paquete bajo ar.edu.unlam.reparabarrio | Responsabilidad y límite de entrega |
|---|---|
| dominio | Entidades, valores, cinco estados, reglas de transición y validación de asignación completa. Entrega independiente mediante casos unitarios, sin persistencia. |
| planificacion | Factibilidad, primera pendiente factible, causas de espera, orden natural y selección física. Recibe instantánea coherente; no escribe datos. |
| aplicacion | Fachada de casos de uso, coordinador, resultados, consultas, recuperación y contratos transaccionales. Única entrada a mutaciones. |
| aplicacion.puertos | Unidad de trabajo y repositorio transaccional, reloj e interfaz de lectura. Contratos compartidos con JDBC y pruebas. |
| persistencia | Conexiones privadas por operación, transacciones H2, mapeo, restricciones e inicialización del catálogo. No decide prioridades. |
| interfaz | Ventana con Órdenes, Recursos y Simulación; formularios, tablas, mensajes y adaptación al EDT. No confirma automáticamente en operativo. |
| simulacion | Generación reproducible, llegadas, solicitudes automáticas concurrentes y trabajos ficticios. Nunca recibe contexto operativo. |
| metricas | Estadísticas funcionales por escenario a partir de eventos confirmados e intervalos. No implementa mediciones académicas de CPU/memoria. |
| arranque | Punto de entrada, bloqueo de instancia, composición y cierre ordenado de contextos y ejecutores. |

Los módulos son paquetes lógicos del único proyecto Maven, no proyectos Maven separados. Se propone FabricaContextos: crea ContextoOperativo con ruta fija de archivo y ContextoSimulacion con nombre H2 en memoria nuevo por ejecución, identificado de forma única. Cada uno contiene su fachada, coordinador, repositorio, reloj y revisión. No habrá selector global mutable de base ni singleton de repositorio. La pantalla operativa mantiene su fachada; la pestaña simulación recibe sólo la fachada del escenario. Las propuestas incluyen ContextoId y se rechazan si pertenecen a otro contexto, aunque coincidan IDs de órdenes o recursos.

La base en memoria mantiene una conexión de vida del contexto, cerrada al descartar el escenario; no se usa una configuración que la preserve indefinidamente tras cerrar todas las conexiones. El catálogo preconfigurado se inicializa por separado en ambas bases. Los ejecutores se cierran antes de destruir el contexto; una respuesta tardía de un escenario anterior no actualiza el siguiente. Una prueba de aislamiento verificará rutas, conexiones e identidades además del contenido operativo antes/después.

Los límites anteriores permiten distribuir trabajo humano: dominio/planificación contra ejemplos; JDBC contra el contrato transaccional; Swing contra fachada e instantáneas; simulación contra fachada y eventos. Esa posibilidad de continuidad no implica ejecutar agentes ni implementar en paralelo en esta fase.

## 3. FUNCIONES O CLASES PRINCIPALES

### 3.1 Modelo y servicios sugeridos

| Nombre sugerido | Finalidad y operaciones públicas descritas |
|---|---|
| Orden, EstadoOrden | ID, descripción, secuencia, requisitos por tipo y estado: PENDIENTE, EN_CURSO, FINALIZADA, FALLIDA, CANCELADA. Consultar datos y validar transición. |
| TipoHerramienta, CatalogoTipos | Tipo reconocido independiente del inventario. Consultar tipos; inicialización privada del adaptador, sin alta/edición en interfaz. |
| Tecnico, Puesto, UnidadHerramienta | Unidades físicas identificables; herramienta referencia un tipo. Consultar identidad y habilitación. |
| DisponibilidadRecurso | DISPONIBLE, OCUPADO, FUERA_DE_SERVICIO como proyección del estado persistido. |
| PropuestaInicio | Valor inmutable con contexto, orden y unidades exactas; no reserva, no tiene vencimiento. |
| AsignacionConfirmada | Vincula una EN_CURSO con un técnico, un puesto y las herramientas requeridas; distinta de la propuesta. |
| EventoHistorial | Secuencia de cambio, orden, operación, momento y recursos; conserva inicio y liberación incluso tras quitar asignación activa. |
| ReglasOrden, ValidadorInvariantes | Validar datos, transiciones, completitud, exclusividad y coherencia recuperada. |
| OrdenNaturalId | Comparar IDs mediante un criterio total documentado; no depender de locale ni orden de consulta SQL. |
| Planificador | Evaluar factibilidad, encontrar primera factible, seleccionar unidades y explicar espera. |
| ServicioTaller | Registrar/consultar órdenes y recursos; consultar tipos, propuesta, tablero e historial; confirmar inicio; finalizar, fallar, cancelar y cambiar habilitación. |
| CoordinadorTaller | Ejecutar consultas coherentes y operaciones críticas del contexto bajo exclusión mutua. No ejecuta reparaciones. |
| UnidadTrabajo, RepositorioTaller | Abrir transacción, leer agregado coherente, conservar cambio completo, confirmar/revertir; acceso interno a aplicación. |
| UnidadTrabajoJdbc, RepositorioTallerJdbc | Implementar esos contratos con H2/JDBC, restricciones y comprobación de filas afectadas. |
| ServicioRecuperacion, InformeRecuperacion | Verificar base y sesión; señalar revisión y habilitar operación sólo si es coherente. |
| ConfiguracionEscenario, GeneradorCarga | Definir recursos, semilla, llegadas, requisitos, duraciones y resultados ficticios; generar toda la entrada antes de ejecutar. |
| MotorSimulacion, TrabajoSimulado | Iniciar escenario, emitir solicitudes, ejecutar duración ficticia fuera del coordinador, informar resultado, consultar terminación. |
| CalculadorEstadisticas, EstadisticasEscenario | Contadores, intervalos y fórmulas RF-18, con período y unidades explícitos. |
| VentanaPrincipal, PresentadorTaller | Solicitar casos de uso en segundo plano y mostrar instantáneas/resultados en EDT. |
| FabricaContextos, ContextoTaller, ReparaBarrioApp, GuardiaInstancia | Componer dependencias y ciclo de vida, abrir ventana y garantizar instancia local única. |

H1 requiere entidades/estados, propuesta, asignación, reglas, invariantes, OrdenNaturalId y Planificador. H2 añade fachada, coordinador, resultados/instantáneas, contratos transaccionales, JDBC y composición mínima; H3 reutiliza ese núcleo en pruebas concurrentes, sin motor ni GUI completos. H4 incorpora recuperación/guardia; H5 presentación; H6 motor/generador/métricas.

Los nombres representan responsabilidades comprobables, no una clase obligatoria por nombre. CatalogoTipos puede consolidarse con TipoHerramienta; TrabajoSimulado como tarea interna del motor; InformeRecuperacion y EstadisticasEscenario como resultados de sus servicios; FabricaContextos dentro de arranque. Evitar delegadores vacíos. Primera factible, selección exacta, invariantes, coordinación, persistencia, Swing y escenarios mantienen contratos y pruebas distinguibles; no concentrar todo en una clase monolítica.

### 3.2 Contratos críticos

| Operación | Entrada y precondiciones | Éxito y efectos | Rechazo o ausencia |
|---|---|---|---|
| Registrar orden | ID obligatorio, descripción no vacía, tipos reconocidos, cero o una unidad por tipo; ID no usado | PENDIENTE, secuencia única creciente y evento de registro en la misma transacción | Faltantes, duplicado, tipo desconocido o cantidad inválida. Tipo reconocido sin unidades no rechaza: queda pendiente con causa. |
| Alta de recurso | Categoría, ID único en ella, tipo existente si herramienta; habilitación inicial libre | Unidad conservada sin asignación; disponible o fuera de servicio | Duplicado/faltantes/tipo desconocido; no se admite alta OCUPADO. |
| Cambiar habilitación | Unidad libre, destino DISPONIBLE o FUERA_DE_SERVICIO | Cambia habilitación conservada, nunca crea asignación | Ocupado, inexistente o destino inválido. Mismo valor devuelve «sin cambio», no inventa evento efectivo. |
| Consultar propuesta | Contexto operativo coherente o escenario válido | Primera pendiente factible por secuencia, unidades físicas mínimas por categoría/tipo y aviso sin reserva | Sin factibles: resultado de consulta con causas; no error ni escritura. |
| Confirmar inicio | Propuesta del contexto, orden PENDIENTE, primera factible actual, conjunto exacto válido y unidades disponibles | EN_CURSO, asignación completa e historial confirmados juntos | Estado inválido, prioridad incumplida, propuesta incompatible/obsoleta o unidad exacta no elegible; no sustitución ni efectos. |
| Finalizar/fallar | ID de orden EN_CURSO y resultado solicitado | Estado terminal respectivo, eliminación de todas sus asignaciones activas, historial con liberación y resolución de revisión | Otro estado, cierre repetido/contradictorio o inconsistencia; no libera nada ajeno. |
| Cancelar | ID PENDIENTE | CANCELADA e historial; no modifica recursos | Otro estado o ID inexistente. |
| Recuperar | Guardia de instancia adquirida y base abierta | Instantánea validada; EN_CURSO intactas; revisión humana si sesión interrumpida | Error técnico o inconsistencia bloquea operaciones normales; no reparación automática de datos. |
| Ejecutar escenario | Configuración válida, fachada de simulación y entrada generada | Solicitudes y trabajos concurrentes; resultado normal o pendientes bloqueadas sin progreso | Configuración inválida antes de iniciar; fallos técnicos durante ejecución se informan separadamente de FALLIDA ficticia. |

Confirmar no compara la propuesta con una nueva selección de unidades mínimas. Comprueba contexto, integridad del conjunto (exactamente un técnico/puesto, tipos requeridos sin faltantes, extras ni duplicados), estado, primera factible y disponibilidad de esas unidades. Una unidad ajena recién habilitada puede cambiar la siguiente propuesta sin invalidar la anterior. No hay rechazo por tiempo ni por diferencia de revisión global.

Catálogo inicial aprobado por PT-05 conforme a D016: MULTIMETRO (multímetro), SOLDADOR (soldador) y TALADRO (taladro). Son tipos no consumibles de uso exclusivo; inicialmente pueden tener cero unidades físicas. La selección queda cerrada en R02. El encargado registra sólo unidades de tipos conocidos; no se precargan unidades ficticias como inventario real. Se cargan al crear la base y se conservan, sin reemplazar silenciosamente un catálogo existente al reiniciar.

PT-04 aprobada: IDs obligatorios, no vacíos, únicos por ámbito de entidad/categoría y contexto según SPEC.md; sin reutilización operativa. Preservar identidad textual exacta, sensible a mayúsculas: T2 y t2 son diferentes. Rechazar espacios externos y sólo blancos; nunca recortar ni normalizar silenciosamente. No imponer formato T01.

Contrato de comparación: una única clave lexicográfica global formada por prefijo (texto anterior al sufijo decimal final o ID completo sin sufijo), presencia de sufijo (sin sufijo antes que con sufijo para igual prefijo), valor numérico si existe y texto original como desempate. Sufijo decimal: dígitos 0 a 9; comparación textual ordinal sensible a mayúsculas, sin locale. Valores numéricos sin desbordamiento para cualquier longitud admitida. Prefijos distintos y IDs sin sufijo participan en esa misma clave; no alternar comparadores por parejas.

Exigir totalidad, antisimetría, transitividad y comparación cero si y sólo si hay igualdad textual. Ejemplos: T02 precede a T2 por desempate textual del valor 2; ambos preceden a T10. TEC-3 precede a TEC-12; AUX participa sin sufijo; T2 precede a t2 por prefijo ordinal. T999999999999999999999 precede a T1000000000000000000000 sin desbordamiento. Mecanismo concreto y pruebas se diseñarán al implementar, sin reabrir esta decisión.

Las mutaciones distinguen éxito confirmado, rechazo de negocio, error técnico con rollback conocido y resultado incierto pendiente de reconciliación por ID interno; la ausencia de propuesta sigue siendo resultado de consulta. Resultado incierto es conocimiento de la operación y salud del contexto, no reserva ni sexto estado de orden.

## 4. RESPONSABILIDADES DE CADA COMPONENTE

| Garantía | Autoridad | Defensa adicional / comunicación |
|---|---|---|
| Transiciones y datos válidos | ReglasOrden | Restricciones de estado, claves y referencias en H2; UI sólo valida para ayudar. |
| Primera factible y unidades mínimas | Planificador y OrdenNaturalId | Casos compartidos entre modos; pruebas de orden total y determinismo. |
| Completitud, exactitud y exclusividad al iniciar | Aplicación dentro del CoordinadorTaller | ValidadorInvariantes y unicidad por unidad en asignaciones activas. |
| Conservación íntegra | UnidadTrabajoJdbc | Una conexión y transacción por operación; no commits parciales en repositorios. |
| Cierre y liberación | ServicioTaller con reglas y transacción | Borrar asignaciones sólo de esa orden; comprobar conjunto y filas; conservar recursos en evento. |
| Recuperación | ServicioRecuperacion | Verifica todas las invariantes relevantes antes de habilitar acciones. |
| Automatización ficticia | MotorSimulacion | Únicamente llamadas a fachada; resultados ficticios definidos por entrada. |
| Estadísticas | CalculadorEstadisticas | Eventos confirmados del contexto, reloj monotónico e intervalos coherentes. |
| Presentación | PresentadorTaller | EDT, proyecciones inmutables y control de respuestas tardías. |

Cada mutación delimita una transacción: registro con secuencia/historial; alta; habilitación; inicio con asignación/historial; cancelación/historial; cierre con estado/liberación/historial/revisión. Inicializar catálogo y marcar sesión/revisión también son transacciones de infraestructura. Una consulta lee una instantánea completa bajo el mismo coordinador y transacción de lectura; ningún lector ve una mitad del inicio.

Las restricciones propuestas son claves únicas de orden y recurso por categoría, secuencia única, requisitos únicos por orden/tipo, referencias a catálogo/orden/unidad y unicidad de recurso en asignaciones activas. Las restricciones SQL por sí solas no prueban completitud ni prioridad: aplicación las valida y recuperación las audita. Las tablas de técnicos/puestos/herramientas separadas permiten referencias claras sin claves polimórficas ambiguas.

El resultado distingue éxito conservado, rechazo de negocio, consulta sin candidato, error técnico y resultado incierto pendiente de reconciliación. No se invocan callbacks ni código Swing dentro de transacciones. Para integración incremental se congelan primero estos contratos, fixtures de negocio y resultados; JDBC debe superar el contrato de rollback/commit y la fachada el de transiciones antes de integrar pantallas o simulación. Los eventos para refresco no sustituyen al historial persistido: son avisos para volver a leer.

## 5. MECANISMO DE CONCURRENCIA

### 5.1 Coordinador y sección crítica

PT-03 aprobada: un ReentrantLock(true) por contexto, adquisición interrumpible cuando proceda y liberación garantizada en finally. Permite gestionar cierre/interrupciones y medir espera separadamente del tiempo de posesión. synchronized sería suficiente para exclusión mutua y tendría liberación automática, pero ofrece menor control de adquisición interrumpible y no una política justa configurable. La elección requiere liberar siempre en finally en la futura implementación. La justicia puede reducir adelantamientos de hilos, con costo de rendimiento; no garantiza que una orden obtenga recursos ni elimina starvation del negocio. No se necesita un lock por recurso, un semáforo por técnico ni un abanico de mecanismos académicos.

Estado compartido: filas persistidas, revisión del contexto, salud del contexto y ciclo de sesión. El registro del motor (llegadas, solicitudes pendientes, trabajos en vuelo y terminación) es estado aparte, protegido en el motor; nunca se mantiene su bloqueo al llamar al coordinador. Los ejecutores y modelos Swing tampoco son inventarios del negocio.

Orden fijo: adquisición del coordinador → apertura de transacción/conexión → lectura coherente → validación → escritura → commit/rollback → cierre de conexión → liberación del coordinador. Ningún código JDBC adquiere luego otro coordinador. Se prohíbe abarcar dos contextos en una transacción y esperar futures, EDT, temporizadores o trabajadores bajo el coordinador.

En confirmación se valida: contexto y forma de propuesta; existencia y PENDIENTE; primera factible por secuencia sobre estado actual; correspondencia de tipos/categorías y disponibilidad de unidades exactas; completitud y restricciones; escritura conjunta y commit. Para cerrar se valida EN_CURSO y su asignación íntegra antes de liberar. Un registro valida duplicados y requisitos antes de asignar secuencia transaccional. Consultas recopilan datos bajo bloqueo y procesan/renderizan fuera de él; ninguna conexión se comparte concurrentemente entre hilos.

El punto de linealización de todas las mutaciones exitosas (registro, inicio, cancelación, cierre/fallo, alta y disponibilidad) es el commit confirmado mientras el coordinador sigue adquirido. Rechazos se deciden sobre la lectura validada bajo exclusión; no tienen commit de negocio. Las consultas se linealizan en la captura coherente. La serialización incluye consultas que capturan datos, evitando estados parciales y permitiendo razonar sobre la prioridad al commit.

La sección crítica contiene sólo el trabajo transaccional imprescindible, sin duración máxima garantizada: contiene lecturas, validación, SQL y escritura durable. Disco lento puede detener confirmaciones, registros y consultas del contexto. No incluye duración de una reparación, generación completa del escenario, espera ficticia, diálogo humano ni renderizado. Con 100 órdenes se medirá espera del lock, duración de transacción y latencia de consulta, sin inventar umbrales. Si resulta insuficiente, revisar primero consultas/índices y volumen de lectura; separar lecturas mediante aislamiento adecuado o usar control optimista/versionado constituye una alternativa futura pendiente de aprobación, no una sustitución automática de la estrategia.

### 5.2 Excepciones, commit y durabilidad

PT-06 aprobada distingue atomicidad («todo o nada») de conocimiento inmediato del resultado. Error anterior a commit con rollback confirmado significa operación no conservada, sin efectos parciales. Rollback fallido no acredita ausencia de efectos. Commit confirmado permite éxito; error posterior de cierre de conexión o refresco no revierte lo conservado.

Excepción durante commit, pérdida de conexión o interrupción puede dejar desconocido si se confirmó; no comunicar éxito ni rechazo definitivo por suposición. Cada mutación utiliza un ID interno único conservado atómicamente junto a efecto/historial. No es reserva ni estado de orden. Su disponibilidad para reconciliación incluso tras caída del proceso y asociación con diagnóstico se concretarán/verificarán en H4, sin sustituir H2 como fuente de verdad.

Mientras el resultado sea incierto, marcar salud bajo el coordinador y bloquear nuevas mutaciones/reintentos a ciegas sólo del contexto afectado: «resultado pendiente de verificación». Liberar lock en finally; no retenerlo mientras se espera recuperar almacenamiento. El otro contexto sano e independiente puede continuar. Consultas no presentan datos no verificados como confirmados; diagnóstico y reconciliación permanecen accesibles.

Al recuperar acceso, reconciliar por ID y auditar estado, asignaciones e historial: operación íntegra confirmada implica éxito; ausencia acreditada de confirmación con almacenamiento coherente implica operación no confirmada. Mera ausencia del ID en lectura fallida/incompleta no basta. Indeterminación o inconsistencia conserva bloqueo con diagnóstico para revisión. No compensar ni borrar cambios confirmados para fingir rollback.

**Discrepancia textual residual para validación humana separada de SPEC.md:** RF-13 e INV-06 podrían leerse como cero cambios ante cualquier error de conservación; RNF-04, PA-08 y CA-17 reiteran ausencia de cambios parciales. PT-06 aprueba todo o nada con resultado inicialmente desconocido, que no se clasifica como rechazo. No se declara resuelta una lectura normativa más fuerte ni se reabre PT-06. Se requiere confirmación humana separada de esta interpretación en SPEC antes de cerrar su aceptación normativa; SPEC.md no se modifica.

H2 deberá confirmar de forma durable el conjunto antes de éxito y recuperar transacciones confirmadas o revertir incompletas. Los experimentos futuros interrumpirán un proceso auxiliar en puntos antes/durante/después de commit, sobre bases de prueba, y validarán ausencia de estados parciales. Una prueba de rollback inyectada antes del commit no sustituye las de recuperación real.

### 5.3 Carreras e invariantes

| Invariante | Argumento y caso verificable |
|---|---|
| INV-01 | Inicio valida libres bajo coordinador y unicidad de asignación activa. Dos inicios por una herramienta se serializan; el segundo no puede poseerla mientras esté ocupada. |
| INV-02 | Validación del conjunto exacto, cardinalidades y tipos antes de escritura conjunta; EN_CURSO sin conjunto completo bloquea recuperación. |
| INV-03 | Propuestas son valores de lectura y espera no escribe asignaciones. Un rechazo por falta de herramienta no ocupa técnico ni puesto. |
| INV-04 | Ocupación derivada de asignación activa; habilitación de ocupados rechazada; toda asignación debe apuntar a EN_CURSO y unidad habilitada. |
| INV-05 | Se revalida estado dentro de transacción. Inicio/inicio e inicio/cancelación producen una única salida; finalización/fallo produce un único cierre. |
| INV-06 | Validaciones no escriben; transacción revierte errores previos a commit. Auditoría trata resultado incierto como se describe, sin inventar rechazo íntegro. |
| INV-07 | Primera factible se recalcula por secuencia al confirmar; habilitar una anterior puede invalidar prioridad de propuesta posterior. |
| INV-08 | Sólo cierre EN_CURSO elimina su conjunto activo y guarda liberación. Un cierre repetido no puede liberar recursos ya reasignados a otra orden. |
| INV-09 | Recuperación conserva estados/asignaciones y señaliza revisión; cerrar o fallar resuelve la señal dentro de su transacción. |
| INV-10 | Contextos y bases distintos, sin conexiones globales; propuestas ajenas rechazadas, motor sin acceso a fachada operativa. |

Registros concurrentes adquieren el mismo coordinador y contador transaccional: secuencias únicas crecientes al confirmar. No se exige coincidencia con el instante en que el usuario comenzó a escribir. Propuestas obsoletas se rechazan por estado, prioridad o unidad exacta; ninguna carrera autoriza sustituir unidades. En una carrera cierre/inicio, un nuevo inicio observa el conjunto ocupado o ya liberado íntegramente, nunca su liberación parcial.

No hay espera circular por recursos del taller porque no hay adquisiciones parciales ni locks por unidad. El riesgo restante es invertir locks del motor/EDT/JDBC: se evita mediante orden fijo, ausencia de callbacks bajo lock y prohibición de esperar trabajos bajo él. Se establecerán límites de espera JDBC y de apagado en pruebas. Esta argumentación y pruebas finitas no constituyen una prueba universal de ausencia de deadlock.

Una orden insatisfacible puede permanecer pendiente indefinidamente en operativo. Una anterior no factible deja examinar posteriores; una primera factible que el encargado no inicia bloquea las posteriores aun con recursos independientes. Es el bloqueo de cabeza de cola aceptado por RN-08/PA-13, no se añade salteo. Justicia de hilos no resuelve esta política ni garantiza tiempos de espera acotados.

### 5.4 Simulación realmente concurrente y EDT

GeneradorCarga construye toda la entrada con semilla antes de lanzarla: evita consumo de aleatoriedad dependiente del intercalado. Las llegadas previstas se programan con ScheduledExecutorService. Un ExecutorService de tamaño fijo (configuración inicial aprobada: cuatro trabajadores modificables para referencia de 100 órdenes) procesa solicitudes concurrentes y ejecuta trabajos ficticios fuera del coordinador. No existe relación uno a uno entre técnico y hilo; el técnico es una unidad del inventario y el pool es infraestructura.

Tras un inicio conservado, el trabajo mantiene únicamente su asignación del negocio; el hilo espera la duración ficticia fuera de cualquier lock y luego solicita finalizar/fallar. Las solicitudes no se quedan esperando recursos dentro de esos trabajadores: ante rechazo se devuelve control y el motor vuelve a consultar cuando hay registro/liberación/habilitación pertinente. Se deduplican solicitudes en vuelo por orden y se agrupan avisos para evitar una tormenta de reintentos. La primera factible siempre se decide en el núcleo; solicitudes simultáneas pueden compartir propuesta y sólo una prosperar. Después del inicio de la anterior, una consulta nueva puede iniciar la siguiente y ambos trabajos superponerse.

Cuatro trabajadores no son límite funcional ni equivalen a cantidad de técnicos. El pool puede limitar simultaneidad máxima respecto de recursos abundantes, pero no debe limitarla a uno. La verificación exige al menos dos tareas ficticias en hilos distintos con intervalos superpuestos y dos EN_CURSO con unidades distintas. También se registran solicitudes superpuestas, no solamente fotogramas de una animación. La programación de llegadas usa un ejecutor distinto para que trabajadores ocupados en duraciones no impidan nuevos registros.

Para terminar, el motor evalúa conjuntamente: llegadas futuras, acciones de disponibilidad configuradas, solicitudes en vuelo, EN_CURSO y factibilidad de pendientes. Sólo declara bloqueo cuando no quedan trabajos ni solicitudes capaces de iniciar/cerrar, no hay acciones futuras útiles y ninguna pendiente es factible. Las pendientes imposibles conservan PENDIENTE con explicación. Una instantánea tomada mientras queda una confirmación en vuelo no permite declarar terminación. Llegadas futuras no se descartan sólo porque el taller esté momentáneamente vacío. No hay pausa, reanudación ni gestión avanzada.

Operaciones Swing, incluso consultas JDBC, van a un ejecutor de segundo plano; EDT sólo valida formularios ligeros y actualiza modelos. SwingUtilities.invokeLater presenta resultados inmutables; un Timer Swing puede solicitar refresco periódico, sin ejecutar SQL en EDT. Se agrupan refrescos para evitar acumulación y se verifica contexto/revisión al aplicar cada respuesta. La interfaz mantiene navegación durante la simulación y muestra cinco contadores separados. Ningún diálogo humano conserva el coordinador; la confirmación operativa nace exclusivamente de una acción del encargado.

## 6. MANEJO DE ERRORES

| Clase | Tratamiento y mensaje orientativo | Diagnóstico / efectos |
|---|---|---|
| Validación | Rechazo de negocio: «Falta descripción», «ID ya registrado», «Tipo desconocido» | Campo y motivo; cero cambios, sin sustituir registros. |
| Recurso exacto no disponible | Rechazo: «M01 está ocupado/fuera de servicio; solicite nueva propuesta» | Unidad, contexto y disponibilidad observada. |
| Propuesta obsoleta | Rechazo: «La orden ya no está pendiente» o detalle de unidad | Sin caducidad temporal ni sustitución silenciosa. |
| Prioridad incumplida | Rechazo: «Debe iniciar la primera factible A antes de B» | Secuencias y causa; no acción de salteo. |
| Transición inválida | Rechazo: «Sólo puede cancelar pendientes» / «La orden ya fue cerrada» | Estado actual; ninguna doble liberación. |
| Conflicto concurrente | Si otra operación válida cambió estado/unidad, rechazo de negocio | Restricción SQL inesperada pese al coordinador es error técnico/inconsistencia, no un rechazo normal que oculte un defecto. |
| Persistencia con rollback conocido | Error técnico: «No se conservó la operación; datos anteriores intactos» | Causa JDBC, SQLState y operación interna; sin detalles SQL en pantalla. |
| Commit confirmado | Éxito: «Operación conservada» | Efecto/historial íntegros; comunicar sólo después de persistencia confirmada. |
| Commit incierto / fallo de rollback | Resultado incierto: «Resultado pendiente de verificación» | Bloquear mutaciones del contexto afectado hasta reconciliación por ID; sin reintento ciego ni éxito/rechazo supuesto. |
| Fallo visual tras commit confirmado | «Operación conservada; no se pudo actualizar la vista» | Recargar en segundo plano, sin revertir ni repetir mutación. |
| Recuperación inconsistente | Error técnico: «Datos inconsistentes; no se habilitan operaciones normales» | Invariante, orden/unidad y contexto; sin liberar ni corregir automáticamente. |
| Fallo ficticio configurado | Cierre normal de negocio FALLIDA | Recursos liberados e historial por reglas comunes; cuenta en procesadas. |
| Excepción de tarea simulada | Error técnico del escenario visible | Supervisión recoge excepciones de futures. Si hay inicio confirmado, intenta cierre FALLIDA por fachada sólo con base coherente; si no puede conservarlo, aborta con asignación intacta y error. No lo cuenta como fallo ficticio exitosamente cerrado. |

El arranque toma la guardia de instancia, abre la base, verifica versión de esquema y coherencia (estados, requisitos, exclusividad, correspondencia, secuencia e historial), y sólo luego permite operar. Se propone un indicador persistido de sesión abierta/cierre limpio: marcar abierto antes de habilitar UI; marcar limpio al detener solicitudes, terminar transacciones y cerrar ordenadamente. Si quedó abierto, las EN_CURSO se marcan para revisión humana en una transacción y permanecen ocupadas. Cierre limpio también conserva EN_CURSO, pero no deduce interrupción inesperada. Si falla marcar sesión o revisión, no se habilita operación normal.

La revisión es información asociada persistida y visible; nunca sexto estado. No se resuelve por aceptar un aviso: únicamente finalización o fallo existentes la resuelven. No hay reconstrucción automática de asignaciones desde suposiciones físicas. Un archivo ilegible, esquema desconocido o historial que contradice estado impide operación normal y muestra diagnóstico, conservando el archivo para inspección humana. Los registros diagnósticos futuros permanecen locales y separados del historial funcional y de evidencias académicas.

## 7. ESTRUCTURA DE ARCHIVOS DEL PROYECTO

Árbol futuro orientativo del único proyecto Maven; no se crean archivos en esta fase. Imprescindibles H1: dominio/planificación y pruebas; H2: pom, JDBC, esquema, catálogo, fachada/coordinador y composición mínima; H3: pruebas concurrentes del núcleo real. Posteriores H4: guardia/recuperación; H5: interfaz operativa; H6: simulación/métricas/interfaz; H7: empaquetado. Las consolidaciones de 3.1 aplican también al árbol, sin perder contratos. Nombres de clases son sugeridos y se pueden consolidar sin alterar contratos.

```text
APO1-ReparaBarrio/
  AGENTS.md, SPEC.md, PLAN.md, DECISIONES.md   (documentación existente preservada)
  prompts/                                  (prompts y refinamientos)
  docs/                                     (informe y decisiones futuras aprobadas)
  evidencias/                               (capturas y validaciones futuras)
  metricas/                                 (mediciones académicas futuras)
  pom.xml                                   (futuro único proyecto Maven)
  src/main/java/ar/edu/unlam/reparabarrio/
    arranque/ ReparaBarrioApp.java, FabricaContextos.java, GuardiaInstancia.java
    dominio/ Orden.java, EstadoOrden.java, TipoHerramienta.java,
             CatalogoTipos.java, Tecnico.java, Puesto.java, UnidadHerramienta.java,
             DisponibilidadRecurso.java, PropuestaInicio.java,
             AsignacionConfirmada.java, EventoHistorial.java,
             ReglasOrden.java, ValidadorInvariantes.java
    planificacion/ Planificador.java, OrdenNaturalId.java
    aplicacion/ ServicioTaller.java, CoordinadorTaller.java, ContextoTaller.java,
                ServicioRecuperacion.java, InformeRecuperacion.java,
                ResultadoOperacion.java, InstantaneaTaller.java
    aplicacion/puertos/ UnidadTrabajo.java, RepositorioTaller.java, Reloj.java
    persistencia/ UnidadTrabajoJdbc.java, RepositorioTallerJdbc.java,
                  InicializadorBase.java
    interfaz/ VentanaPrincipal.java, PresentadorTaller.java,
              PanelOrdenes.java, PanelRecursos.java, PanelSimulacion.java
    simulacion/ ConfiguracionEscenario.java, GeneradorCarga.java,
                MotorSimulacion.java, TrabajoSimulado.java
    metricas/ CalculadorEstadisticas.java, EstadisticasEscenario.java
  src/main/resources/
    catalogo-tipos.properties                (precarga aprobada, sin editor UI)
    persistencia/esquema.sql                 (futuro esquema inicial versionado)
  src/test/java/ar/edu/unlam/reparabarrio/
    dominio/ ReglasOrdenTest.java
    planificacion/ PlanificadorTest.java, OrdenNaturalIdTest.java
    aplicacion/ CoordinadorConcurrenciaTest.java, EquivalenciaModosTest.java
    persistencia/ TransaccionesH2Test.java, RecuperacionH2Test.java
    simulacion/ SimulacionTest.java, AislamientoTest.java
    metricas/ EstadisticasTest.java
    interfaz/ PresentadorSwingTest.java
  src/test/resources/ escenarios/            (fixtures, incluida referencia de 100)
  datos/operativo/                           (H2 operativo y guardia de instancia)
  datos/temporales/                          (sólo bases auxiliares de pruebas)
  logs/                                     (diagnóstico local futuro)
  target/                                   (compilación y reportes futuros)
```

La simulación usa memoria H2 por ejecución, sin archivo dentro de operativo. Las pruebas de recuperación usan archivos exclusivos temporales dentro del proyecto, nunca la base real. La ruta de datos se resuelve desde la raíz del proyecto y se valida, evitando crear bases distintas según el directorio accidental de arranque. No se reorganizan documentación, consignas, rúbricas, registros académicos ni historial Git. Los artefactos de ejecución no deberán incorporarse al historial; esa configuración se realizará recién al implementar y con el alcance aprobado.

Procedimiento futuro en Windows, no ejecutado: verificar JDK 21 y Maven, comprobar dependencias locales, situarse en la raíz y ejecutar `mvn verify`; empaquetar un JAR con dependencias y Main-Class mediante Maven (plugin de ensamblado 3.7.1 como versión de referencia por comprobar) y ejecutar `java -jar target/reparabarrio-0.1.0-jar-with-dependencies.jar`. No requiere IDE, servidor ni conexión durante ejecución. Verificar previamente nombre final, ruta de base y funcionamiento local en el equipo aprobado. Un instalador nativo se puede postergar; la ejecución por JAR es el procedimiento MVP.

## 8. ESTRATEGIA DE PRUEBAS

### 8.1 Matriz de trazabilidad y evidencia futura

U: unitaria; I: integración H2/JDBC; C: concurrencia controlada; S: escenario concurrente; V: interfaz/manual Windows; R: interrupción/reapertura. Todas son pruebas propuestas y pendientes, sin resultados atribuidos.

| CA | RF / RNF / RN vinculados | INV | Módulos, prueba y evidencia esperada |
|---|---|---|---|
| CA-01 | RF-01, RF-02, RF-10; RN-04 | INV-05, INV-06, INV-07 | Dominio/aplicación/JDBC, U/I/C: datos válidos e inválidos, tipos sin unidades, duplicados incluso finales y registros con barrera; filas e historial, secuencias únicas crecientes y rechazos sin efectos. |
| CA-02 | RF-03, RF-04, RF-06; RN-01, RN-05, RN-07 | INV-04, INV-06 | Recursos/JDBC, U/I: cada categoría, IDs coincidentes entre categorías, duplicados y fuera de servicio; inventario recuperable y exclusión de candidatas. |
| CA-03 | RF-02, RF-05, RF-07; RN-01, RN-02, RN-04 | INV-06, INV-07 | Planificador/coordinador, U/I: A imposible, B/C factibles; habilitar A después de proponer B; propuesta B y rechazo íntegro posterior. |
| CA-04 | RF-05, RF-06; RNF-06; RN-03, RN-05 | INV-03 | OrdenNaturalId/planificador, U/I: T2/T02/T10, TEC-3/TEC-12, AUX, mayúsculas/minúsculas, sufijos grandes y mezcla; misma propuesta y base sin cambios, antisimetría/transitividad/totalidad, empate textual y consistencia con igualdad. CA-01/02 verifican rechazo de espacios externos sin normalización. |
| CA-05 | RF-07, RF-10; RNF-03; RN-04, RN-06 | INV-01, INV-02, INV-04 | Aplicación/JDBC, I/C: inicio con varias herramientas, cero herramientas y lecturas concurrentes; una asignación íntegra y ningún lector parcial. |
| CA-06 | RF-07, RF-09; RNF-08; RN-01, RN-03 | INV-03, INV-06 | Planificador/aplicación, U/I: herramienta faltante; motivo y técnico/puesto libres, comparación antes/después. |
| CA-07 | RF-07, RF-08, RF-17; RNF-03; RN-04 | INV-01, INV-05 | Coordinador, C/I: dos solicitudes misma propuesta liberadas por barrera; un commit de inicio y un rechazo. |
| CA-08 | RF-07, RF-17; RNF-03; RN-02, RN-06 | INV-01, INV-03, INV-07 | Coordinador/simulador, C/S: herramienta única; trazas de inicios compatibles, no posesión simultánea ni reserva parcial. |
| CA-09 | RF-05, RF-07, RF-17; RN-02, RN-09 | INV-01, INV-07 | Coordinador/simulador, C/S: recursos independientes y nueva propuesta tras rechazo; dos EN_CURSO e intervalos de trabajo superpuestos. |
| CA-10 | RF-04, RF-06, RF-07; RNF-06; RN-04, RN-05 | INV-06, INV-07 | Aplicación/planificación, I: ocupar/deshabilitar exacta, cancelar orden, habilitar anterior; rechazos sin sustitución. Habilitar sólo unidad ajena menor: propuesta antigua aceptada. |
| CA-11 | RF-08, RF-10, RF-11; RN-06 | INV-05, INV-08 | Dominio/JDBC, U/I: finalizar/fallar y repetir/reabrir; terminal único, liberación completa e historial. |
| CA-12 | RF-08, RF-10, RF-12; RN-03, RN-08 | INV-05, INV-06 | Dominio/aplicación, U/I: cancelar cada estado; sólo PENDIENTE cambia, recursos ajenos intactos. |
| CA-13 | RF-07, RF-08, RF-12; RNF-03; RN-04 | INV-02, INV-05 | Coordinador, C/I: inicio y cancelación con arranque simultáneo y ambos órdenes forzados; una salida válida. |
| CA-14 | RF-07, RF-08, RF-11; RNF-03; RN-06 | INV-01, INV-05, INV-08 | Coordinador, C/I: finalizar/fallar mientras otra inicia; un terminal y liberación única, nunca liberar nueva asignación. |
| CA-15 | RF-04, RF-11; RN-07 | INV-04 | Recursos/coordinador, U/I/C: cambios de ocupado para cada categoría y rehabilitación libre; asignación intacta hasta cierre. |
| CA-16 | RF-02, RF-10, RF-13; RNF-04; RN-04 | INV-04, INV-08, INV-09 | JDBC/recuperación, I/R: cierre limpio/reapertura con cinco estados, IDs y catálogo; instantáneas iguales y siguiente secuencia mayor. |
| CA-17 | RF-13, RF-14; RNF-04; RN-10 | INV-06, INV-09 | JDBC/recuperación, I/R: rollback conocido, interrupciones antes/durante/después de commit, pérdida de conexión, resultado incierto, reconciliación por ID sin duplicaciones, bloqueo hasta verificación y corrupción controlada; estado completo o anterior, revisión EN_CURSO, bloqueo ante inconsistencia. |
| CA-18 | RF-06, RF-09, RF-15; RNF-05; RN-03 | INV-03 | Interfaz, V/U: tres secciones, causas diferenciadas, aviso sin reserva y cinco contadores; capturas y checklist durante escenario. |
| CA-19 | RF-05, RF-07; RN-02, RN-08 | INV-07 | Planificación/aplicación/UI, U/I/V: A factible sin confirmar y B independiente; rechazo B y ausencia de salteo. |
| CA-20 | RF-16; RNF-06, RNF-07; RN-09 | INV-10 | Generador, U/S: comparar entrada completa con misma semilla/configuración, incluyendo fallos y llegadas; no comparar intercalados reales como igualdad obligatoria. |
| CA-21 | RF-16, RF-17; RNF-03, RNF-08; RN-09 | INV-01, INV-02, INV-03, INV-07 | Motor/coordinador, C/S: suficiente, alta contención e imposibles; trazas de hilos y solapamiento, invariantes tras cambios y terminación sin progreso. |
| CA-22 | RF-15, RF-18; RNF-05 | INV-04 | Métricas/interfaz, U/S/V: 100 órdenes suficientes/limitadas, sin inicios, denominador cero y fuera de servicio parcial; fórmulas con reloj controlado, N/D y navegación visible. |
| CA-23 | RF-19; RNF-07; RN-09 | INV-10 | Contextos/JDBC/motor, I/S: IDs iguales entre modos, propuesta cruzada y dos escenarios consecutivos; contenido operativo antes/después idéntico. |
| CA-24 | RF-07, RF-11, RF-17; RNF-07; RN-09 | INV-01, INV-02, INV-05, INV-08 | Núcleo/adaptadores, U/I: mismos fixtures de propuesta/inicio/cierre para ambas fachadas; decisiones equivalentes, diferente origen humano/automático. |
| CA-25 | RNF-01, RNF-02 | — | Arranque/GUI/empaquetado, V: Windows sin servicios remotos, JAR local y segunda instancia rechazada; checklist y versiones efectivas. |
| CA-26 | RNF-03, RNF-08 | INV-03 | Coordinador/motor/documentación, C/S/revisión: recursos compartidos, orden de locks, timeouts y límites; ausencia de reservas en espera y evidencia finita explícita. |

La matriz cubre RF-01 a RF-19, RNF-01 a RNF-08, RN-01 a RN-10, INV-01 a INV-10 y CA-01 a CA-26 conservando sus identificadores. Añade relaciones técnicas sin sustituir las referencias normativas de SPEC.md. D016 se verifica en CA-01/CA-02/CA-16/CA-18: catálogo precargado conservado, altas sólo de unidades de tipos existentes y ausencia de editor de tipos.

### 8.2 Diseño de verificación

Las pruebas unitarias utilizan fixtures pequeños y reloj controlado. Verifican todas las transiciones permitidas y rechazadas, requisitos cero/uno por tipo, conjunto exacto sin extras y causas de espera. El comparador prueba transitividad con ternas mixtas, igualdad, grandes sufijos y desempates. Integración verifica commit, rollback, filas afectadas, historial, restricciones y reapertura sobre H2 real, con los mismos parámetros previstos para operativo. Una base en memoria no reemplaza pruebas de archivo.

Concurrencia utiliza CountDownLatch o CyclicBarrier para arrancar solicitudes, y puntos de control de prueba antes de adquirir el lock o confirmar, sin introducir esperas de producción. Medir espera y posesión del lock sin prometer cota; acreditar intervalos de trabajo superpuestos mediante barreras/trazas fuera del lock y no deadlock observado sólo en ensayos acotados. Se fuerzan ambos órdenes de carreras y se comprueban invariantes sobre instantáneas coherentes después de cada operación confirmada. Se proponen 50 repeticiones iniciales y máximo de 10 segundos por caso controlado, ajustables al entorno y registrados como parámetros de ensayo, no metas de rendimiento del producto. Un timeout falla la prueba y captura hilos/operaciones; nunca se interpreta como pendiente normal. Se evita usar solamente sleeps para pretender demostrar una carrera.

Para recuperación se ejecutará un proceso Java auxiliar sobre base exclusiva de prueba y se lo terminará en puntos controlados; el siguiente proceso comprobará todo el agregado y la marca de sesión. La inyección de fallo de SQL/commit permite verificar comunicación y reconciliación. Corruptar datos deliberadamente se limita a fixtures descartables. PT-06 ya define el contrato de CA-17; ejecución pendiente y discrepancia normativa de 5.2 para validación separada. Verificar éxito reconciliado, ausencia acreditada y bloqueo mantenido ante indeterminación; sin duplicación por reintento y con otro contexto sano operativo.

Escenarios de referencia: 100 órdenes con recursos suficientes, 100 con recursos limitados/alta contención, mezcla de tipos reconocidos sin unidades y trabajos imposibles, y casos sin inicios o sin tiempo habilitado. Configuración y semilla se conservan en evidencia futura. Se reportan máximos de EN_CURSO y solapamientos para actividad concurrente, rechazos y pendientes por causa; no se exige idéntico resultado temporal entre corridas. La exploración bajo carga complementa las carreras controladas y no prueba universalmente deadlock/starvation.

Métricas funcionales: procesadas = FINALIZADA + FALLIDA; espera = inicio − registro de cada iniciada; promedio sólo iniciadas o N/D; duración desde comienzo hasta terminación; utilización por unidad = suma de intervalos ocupados / suma de intervalos habilitados en el período × 100, excluyendo FUERA_DE_SERVICIO y N/D si denominador cero. Se recortan intervalos abiertos al instante de observación, con reloj monotónico para diferencias y fecha/hora para historial. Para no perder eventos, el cálculo utiliza historial confirmado y hechos temporales del contexto, no sólo avisos de refresco que podrían agruparse. Timestamps para intervalos se capturan dentro de la operación serializada y sólo se incorporan si se confirma; se documentará su precisión respecto del commit. La duración ficticia empieza después del éxito de inicio; no incluye espera del lock como trabajo previo.

Las mediciones académicas futuras de CPU, memoria y tiempo se mantienen separadas: documentar Windows/JDK/hardware, carga/semilla, herramienta (JFR/JDK o monitor local), calentamiento, repeticiones, unidades y variación; guardar observaciones reales en metricas/ y evidencias/. No se fijan porcentajes ni resultados hipotéticos. Umbrales de interfaz/rendimiento deberán aprobarse después de la línea base; los requisitos cualitativos no se sustituyen por cifras inventadas.

Swing se prueba mediante presentador con fachada controlada y comprobación de actualizaciones en EDT, respuestas tardías y mensajes. La validación manual Windows verifica formularios, confirmación humana, revisión tras interrupción, navegación durante 100 órdenes y actualización de estadísticas. Una prueba de presentador no demuestra por sí sola usabilidad de la ventana real.

### 8.3 Hitos, continuidad y criterios de salida

| Hito | Entregables futuros y dependencias | Criterio de salida / condición para continuar | Riesgo y punto para retomar |
|---|---|---|---|
| H0 — revisión técnica | Validación final PLAN, contratos indispensables y comprobación real JDK/Maven/dependencias; PT ya aprobadas | Evidencia de disponibilidad, resolución y compatibilidad; sustituciones de versiones documentadas. No iniciar fase 3 sin autorización | Riesgo de entorno; relevo con contratos y verificaciones pendientes. Sin aplicación ejecutable; siguiente H1. |
| H1 — núcleo | Dominio mínimo, estados, factibilidad, prioridad, selección e invariantes; depende H0 | U de CA-01 a CA-06, CA-10 a CA-12 y CA-19 en nivel unitario; propiedades PT-04 | Comparador mixto; relevo con fixtures. Ejecutable por pruebas unitarias; faltan persistencia/adaptadores. Siguiente H2. |
| H2 — primer recorrido vertical durable | H2 de archivo en fixtures, catálogo, registro de órdenes/recursos, secuencia, propuesta, confirmación, cancelación y cierre/historial; coordinador y bloqueo preventivo PT-06; depende H1 | I de transacciones/rollback y lectura coherente, CA-01/02/05/06/10/11/12/15/19 según alcance; registro → propuesta → inicio → cierre, éxito sólo tras commit | SQL parcial/fuente duplicada; relevo con fachada y fixtures JDBC. Primer incremento demostrable sin GUI; faltan concurrencia completa y recuperación. Siguiente H3. |
| H3 — concurrencia temprana | Trabajadores controlados sobre núcleo H2 transaccional real, sin GUI/motor completos; depende H2 | CA-07/08/09: doble inicio rechazado, competencia por herramienta única sin reservas parciales, dos inicios independientes válidos con prioridad/nueva propuesta y dos reparaciones EN_CURSO superpuestas. CA-13/14 y lecturas CA-05; trazas/barreras e invariantes, CA-26 parcial | Doble liberación/latencia; relevo con pruebas/trazas. Demostración durable, no inventario sólo en memoria; faltan recuperación, interfaz y escenarios completos. Siguiente H4. |
| H4 — recuperación y arranque | FileChannel/ruta estable, sesión, auditoría y reconciliación; depende H3 | CA-16/17 y segunda instancia de CA-25 sobre archivo; EN_CURSO intactas con revisión, bloqueo ante inconsistencia y reconciliación sin duplicaciones | Interrupciones y disponibilidad del ID; relevo con matriz de fallos. Núcleo recuperable, falta GUI/simulador; revisión normativa residual separada. Siguiente H5. |
| H5 — interfaz operativa | Primero registro/propuesta/confirmación/cierre mínimos; después Recursos, cancelación, tablero/historial/revisión y mensajes completos; depende H4 | CA-18/19 en operativo y CA-25 Windows; cinco contadores y JDBC/esperas fuera de EDT | Respuestas tardías; relevo con presentador/checklist. Operativo utilizable, falta simulador y aceptación global. Siguiente H6. |
| H6 — simulación | Contexto aislado, generación reproducible, motor configurable, terminación, métricas y luego interfaz del simulador; depende H4/H5 | CA-20 a CA-24: simultaneidad, contención, sin reservas, cierre sin progreso, aislamiento y fórmulas/N/D; EDT seguro | Saturación/fin prematuro; relevo con semillas/configuración. Ambos modos ejecutables; falta integración completa/distribución. Siguiente H7. |
| H7 — integración MVP | Empaquetado, Windows, referencia de 100 y matriz completa; depende H5/H6 | CA-01 a CA-26 con evidencias/limitaciones; validación humana de discrepancia normativa residual; ningún requisito omitido | Almacenamiento/EDT; relevo con defectos/procedimiento local. MVP completo sólo tras toda la aceptación. Siguiente H8. |
| H8 — entrega académica | Prompts/refinamientos, mediciones CPU/memoria/tiempo, medición externa de tokens e informe; depende H7 para medir | Evidencias reales, entorno/unidades/repeticiones y límites; consignas/rúbricas intactas | Evidencia incompleta; relevo por índice local futuro de artefactos. Entrega verificable, sin resultados inventados. |

Se conservan H0 a H8: H2 incorpora operaciones completas, H3 demuestra concurrencia temprana, H5 avanza de GUI mínima a completa y H6 incorpora interfaz del simulador. H2 es primer incremento vertical demostrable; H3 demuestra temprano concurrencia durable; ninguno equivale al MVP completo H7. Todo es trabajo futuro.

Los hitos no asignan plazos inventados: el equipo debe contrastarlos con la fecha real de APO. El orden introduce persistencia antes de cerrar concurrencia porque el éxito depende del commit; probar sólo un inventario en memoria y agregar H2 después ocultaría el riesgo principal. Se pueden preparar interfaz o generador contra contratos estables, pero no integrarlos como cumplidos antes de validar transacciones y recuperación. Cada relevo entrega contratos, fixtures, pruebas ejecutadas en esa fase futura, defectos pendientes y decisión necesaria para continuar.

Imprescindible: todos los requisitos aprobados, cinco estados, prioridad, unidades exactas, durabilidad/recuperación, GUI utilizable, simulación concurrente aislada, estadísticas y evidencia de validación. Postergable: temas visuales, iconos personalizados, animaciones, instalador nativo, exportaciones de conveniencia y refinamientos de presentación. Ninguna postergación permite omitir un CA. No se agregan cobros, prioridades, reservas, edición del catálogo, red ni pausa/reanudación.

### 8.4 Registro de decisiones aprobadas y riesgos pendientes

PT son identificadores internos de PLAN; no renumeran requisitos ni decisiones existentes. Fuente humana: R02 v1.0. Verificaciones empíricas pendientes no reabren decisiones.

| ID / estado | Decisión humana / apartados afectados | Justificación y verificación futura |
|---|---|---|
| PT-01 APROBADA | Monolito en capas, único Maven, núcleo común/H2/coordinador por contexto; 1, 2, 4, 7 | Complejidad proporcionada; verificar aislamiento de repositorios mutables, conexiones, locks y asignaciones. |
| PT-02 APROBADA | JDK 21 LTS, Maven, Swing, H2/JDBC, JUnit 5; 1.3, 7, 8.3 | Sólo resta comprobar versiones de referencia, disponibilidad/resolución/compatibilidad; documentar sustituciones compatibles sin cambiar tecnologías por cuenta propia. |
| PT-03 APROBADA | ReentrantLock(true), transacciones bajo lock, adquisición interrumpible/finally; 4, 5, 8 | Coherencia y exclusividad; medir latencia sin cota ni garantía de starvation del negocio. |
| PT-04 APROBADA | Identidad textual sensible a mayúsculas, espacios externos rechazados, orden total; 3.2, 8 | Evitar ciclos/empates falsos; mecanismo y pruebas de propiedades/mezclas/sufijos grandes futuros. |
| PT-05 APROBADA | MULTIMETRO, SOLDADOR, TALADRO; 3.2, 7, 8 | Concretar D016, tipos exclusivos no consumibles, cero unidades permitido; verificar conservación sin reemplazo, sin editor ni unidades ficticias. |
| PT-06 APROBADA | Resultado incierto, ID atómico, bloqueo por contexto y reconciliación; 1.2, 3.2, 4, 5.2, 6, 8 | Atomicidad distinta de conocimiento; no inferir rollback de excepción. Discrepancia textual residual para validación separada de SPEC, no reapertura de PT. |
| PT-07 APROBADA | Guardia operativa FileChannel/ruta estable, auditoría y continuidad EN_CURSO con revisión; 1.3, 6, 7, 8 | Evitar segunda instancia/liberación por suposición; verificar recuperación y revisión humana en Windows sin cierre automático. |
| PT-08 APROBADA | ExecutorService/ScheduledExecutorService, cuatro trabajadores iniciales configurables; 5.4, 8 | Trabajo superpuesto sin equivalencia técnico/hilo ni lock durante espera; parámetros de ensayo/mediciones pendientes, sin límite funcional de cuatro. |

Revisión documental: se contrastaron las propuestas con los cinco estados y transiciones, D016, prioridad por secuencia, propuestas sin reserva, validación exacta sin caducidad, aislamiento, fórmulas y criterios CA. No se identifica necesidad de ampliar el alcance ni sustituir tecnologías. Quedan explícitas la semántica de confirmación incierta y las concreciones de orden natural/IDs para evitar reinterpretaciones silenciosas. La duración real del lock, recuperación H2, compatibilidad de versiones y usabilidad permanecen sin validación experimental. Esta revisión textual no equivale a pruebas ejecutadas.

### 8.5 Cierre de planificación

Se recomienda un monolito Java/Swing con núcleo compartido, H2/JDBC como fuente de verdad, contextos aislados y un coordinador por contexto que serializa transacciones completas. Las reparaciones y temporizadores se ejecutan fuera del bloqueo; la interfaz se actualiza en EDT desde instantáneas. Los riesgos principales son latencia de almacenamiento, resultado incierto de commit, comparador no transitivo y detección prematura de fin de simulación. PT-01 a PT-08 ya están aprobadas; restan validación humana final de PLAN, revisión normativa separada de 5.2 y comprobaciones empíricas. R02 no autoriza fase 3. El único archivo actualizado en este refinamiento es PLAN.md; no se ejecutaron construcciones ni pruebas.

### 8.6 Registro de refinamiento R02

R02 v1.0 recibido el 13/10/2026 sobre PLAN.md v0.1 generado por P02 v0.2. Resultado: v0.2 refinada, PRELIMINAR, pendiente de validación humana final. Fuente: SPEC.md v0.2 aprobada, con D016 y sección 14.

Cambios aplicados: PT-01 a PT-08 aprobadas con justificación/apartados; versiones conservadas como referencias por comprobar; identidad y clave total de IDs precisadas; PT-06 integrado en arquitectura, contratos, errores y pruebas; clases imprescindibles, posteriores y consolidables distinguidas; H2 vertical durable completo y H3 concurrente temprano antes de GUI/simulador completos. Se conservan los ocho apartados, su orden y cobertura RF-01 a RF-19, RNF-01 a RNF-08, RN-01 a RN-10, INV-01 a INV-10 y CA-01 a CA-26.

Riesgos restantes: latencia de disco/lock, indeterminación/recuperación del commit, comparador, saturación del pool, fin prematuro y respuestas Swing tardías. Validaciones pendientes: entorno/dependencias, propiedades del orden, transacciones/carreras, recuperación/segunda instancia, aislamiento, EDT/usabilidad y mediciones académicas externas. Discrepancia RF-13/INV-06 señalada en 5.2 para validación humana separada; no se declara resuelta ni se agrega una decisión.

Sólo se actualizó PLAN.md; no se implementó código, crearon archivos auxiliares ni ejecutaron pruebas, construcciones, instalaciones o mediciones.

### 8.7 Cierre humano de la fase 2 (posterior a R02)

**Ratificación del 13/10/2026.** La persona responsable del proyecto revisó `PLAN.md` v0.2 refinado y **aprobó la planificación de la fase 2 de SDD**, incluidas las decisiones técnicas PT-01 a PT-08. La aprobación no significa que las tecnologías, pruebas, escenarios ni garantías hayan sido verificados experimentalmente: esas tareas siguen pendientes de los hitos previstos.

**Interpretación normativa aprobada de PT-06 respecto de RF-13, INV-06, PA-08 y CA-17.** Un rechazo definitivo de negocio o un fallo con rollback comprobado no deja cambios atribuibles a la operación rechazada. Una operación transaccional conserva **todos** los efectos previstos o **ninguno**, sin resultados parciales; si el commit está confirmado, se conservan todos. Si una interrupción o excepción impide determinar si hubo commit, la aplicación no informa éxito ni rechazo definitivo: registra el **resultado incierto**, bloquea nuevas mutaciones del contexto afectado y exige reconciliación mediante un identificador interno de operación y auditoría de datos. Sólo tras comprobar el resultado y la coherencia puede comunicarse éxito confirmado o ausencia acreditada de confirmación y reanudarse la operación normal. No se reintenta a ciegas. El resultado incierto no constituye un sexto estado de la orden ni autoriza reservas parciales.

Esta ratificación **resuelve a nivel de decisión humana de planificación** la observación textual pendiente descrita en los apartados 5.2, 8.3, 8.4, 8.5 y 8.6. Esos apartados conservan el registro histórico de la situación **antes** de esta adenda. La interpretación se documenta sin alterar el texto aprobado de `SPEC.md`. Si una revisión posterior de la cátedra exigiera aclaración textual adicional de la especificación, requerirá aprobación explícita antes de modificarla.

**Procedencia.** El archivo `evidencias/PLAN-R02-candidato.md` debe conservar la versión 0.2 producida por Codex **sin cambios humanos**; este `PLAN.md` sólo modifica el campo de estado y agrega la presente adenda de aprobación humana. El original P02 v0.1 se conserva en `evidencias/PLAN-P02-original.md`. `docs/CIERRE-FASE2.md` recoge el cierre y las métricas externas.

**Alcance de la autorización.** La fase 2 queda documentalmente cerrada. **No autoriza por sí sola implementar código, instalar dependencias, ejecutar Codex para fase 3 ni interactuar con el repositorio GitHub del grupo**. Antes de la fase 3 corresponde comprobar el entorno H0 y aprobar el prompt de implementación correspondiente.
