# ReparaBarrio — Propuesta técnica de implementación H4

| Control documental | Valor |
|---|---|
| Versión | 0.2 |
| Fecha | 14/10/2026 |
| Estado | **PROPUESTA REFINADA, PENDIENTE DE APROBACIÓN HUMANA** |
| Metodología | SDD, diseño previo dentro de la fase 3 |
| Hito | H4: recuperación y arranque |
| Solicitud | `prompts/anexos/P03-D-H4.md` v0.1 → `prompts/anexos/R04-H4.md` v0.1, leídos íntegramente; refinamiento exclusivo de este documento |
| Normativa | `SPEC.md` v0.2 con D016 y cierre humano; `PLAN.md` v0.2 con PT-01 a PT-08 y adenda humana 8.7 |
| Continuidad | H1/H2/H3 aprobados según sus cierres; diseño H3 v0.2 y su aprobación humana |
| Entregable | Únicamente `IMPLEM-H4.md`; no autoriza implementar H4 |

## 1. Alcance y trazabilidad

H4 debe habilitar un núcleo operativo recuperable sobre H2 de archivo. Conserva Java 21, Maven, JUnit, JDBC, H2 2.3.232, un `ReentrantLock(true)` por contexto, transacciones completas y los cinco estados de orden. No cambia prioridad, selección exacta, catálogo, identificadores de negocio ni reglas de cierre.

| Cobertura | Requisitos y decisiones | Evidencia futura exigida |
|---|---|---|
| Continuidad entre sesiones | CA-16; RF-02/10/13; RNF-04 | Reapertura en JVM independiente con catálogo, órdenes en cinco estados, IDs, secuencias, recursos, habilitación, asignaciones e historial conservados; siguiente registro con antigüedad mayor |
| Recuperación tras interrupción | CA-17; RF-13/14; RN-10; PA-08; PT-07 | Datos anteriores o cambio completo; EN_CURSO intactas y revisión asociada durable; ninguna liberación supuesta |
| Resultado incierto | CA-17; PT-06 y PLAN 8.7; INV-06 | ID disponible tras caída, reconciliación íntegra o ausencia acreditada; bloqueo mientras falte evidencia, sin reintento ciego |
| Instancia operativa única | Parte de CA-25; PT-07; PLAN 1.3/7/8.3 | Guardia FileChannel antes de H2, ruta estable y segunda JVM rechazada sin operaciones sobre la base |
| Coherencia y diagnóstico | RNF-03/04; CA-17 y CA-26 parcial | Auditoría de estados, asignaciones, contadores e historial; error informado y operaciones normales bloqueadas ante inconsistencias |
| Preservación de reglas | INV-01 a INV-09; RF-04/07/08/11 | Regresión de exclusividad, completitud, ausencia de reservas, prioridad, transiciones únicas y liberación propia |
| Aislamiento | INV-10; RF-19, cobertura de infraestructura | Recuperación de un contexto no usa conexiones ni salud de otro; no incorpora todavía el motor de simulación |

H5 conserva presentación Swing, revisión visible y operación humana en GUI. H6 conserva generación, motor, duraciones ficticias, escenarios y estadísticas; H7 conserva empaquetado y aceptación integral; H8 conserva mediciones académicas. H4 no acredita íntegramente CA-25, CA-23/24 ni CA-26, ni completa el MVP o P04.

| Observación aceptada | Tratamiento propuesto | Límite |
|---|---|---|
| O-H2-01 | Intención y salud durables, sesión y reconciliación por ID | Se cerrará sólo con pruebas entre procesos |
| O-H2-02 | JVM escritora y JVM recuperadora distintas | Una nueva conexión o la fachada del mapa no bastan |
| O-H2-03 / O-H3-04 | Ruta operativa fija, guardia, contexto propietario y composición controlada | H4-D01/D02 ratificadas para diseño; detalles sujetos a aprobación completa; no garantizar identidad universal para enlaces arbitrarios |
| O-H2-04 | Conservar carreras aprobadas en H3 como regresión | No reimplementar concurrencia ni atribuir pruebas nuevas a esta instancia |
| O-H2-05 | Cierre explícito y retiro seguro del registro de contextos | Caracterización de carga sigue pendiente; no optimizar consultas por suposición |
| O-H3-01 | Registrar uso obsoleto de `ThreadDeath` | Mantenimiento separado; no corregirlo automáticamente en H4 |
| O-H3-02 | Mantener observador opcional, desactivado normalmente y entrega no bloqueante poslock | No usarlo para durabilidad, reconciliación o decisiones |
| O-H3-03 | Ensayos finitos con parámetros y límites documentados | Ninguna garantía universal de ausencia de carreras/deadlock/starvation |
| O-H3-05 | Evidencias nuevas futuras en UTF-8 | Conservar sin reescritura los logs históricos UTF-16 |

## 2. Diagnóstico de la base inspeccionada

### 2.1 Mecanismos reutilizables

`ServicioTaller.ejecutar` adquiere interrumpiblemente el coordinador antes de abrir una unidad de trabajo, carga una instantánea validada, ejecuta el caso de uso y hace commit o rollback. Cierra la conexión y libera el lock en `finally`. Éxito se construye tras retorno de commit; fallo posterior de cierre conserva el éxito con aviso. La instrumentación H3 se entrega después del unlock.

`UnidadTrabajoJdbc` usa conexión privada, `TRANSACTION_SERIALIZABLE` y autocommit desactivado. La fábrica configura `WRITE_DELAY=0;LOCK_TIMEOUT=5000` y comprueba efectivamente WRITE_DELAY. LOCK_TIMEOUT no limita toda llamada JDBC ni la espera Java.

`RepositorioTallerJdbc.evento` incrementa `contador.revision` y escribe un `historial.operacion_id` único junto con el efecto de negocio. Todas las mutaciones efectivas actuales generan evento: orden, recurso, habilitación, inicio, cancelación y cierre. Ese ID sirve como testigo transaccional sin inventar otro registro del efecto. `contador.antiguedad` es transaccional y distinto de revisión.

`InstantaneaDominio` comprueba exclusividad, completitud, relación EN_CURSO/asignación, tipos y unidades habilitadas. Disponibilidad se deriva de asignaciones y habilitación. `Planificador` propone sin reservar y revalida primera factible, contexto y unidades exactas. Las restricciones SQL refuerzan unicidad y referencias; no deciden prioridades.

### 2.2 Faltantes y límites reales

1. `CoordinadorTaller.operacionIncierta` y diagnóstico sólo existen en memoria. El UUID de una operación no confirmada no queda en el historial; si el proceso desaparece, tampoco queda disponible para buscar su ausencia. Persistir sólo el diagnóstico después del error dejaría una ventana sin protección.
2. `PersistenciaH2.CONTEXTOS` es un mapa estático sin cierre ni retiro. Devuelve la fachada existente incluso si es incierta. Su identidad resuelve padre real y nombre del archivo base, pero no todos los alias del archivo físico, casos Windows, enlaces duros o reemplazos de base.
3. No hay FileChannel, sesión, señal durable de revisión humana ni servicio de reconciliación. El constructor público de `ServicioTaller` permite otra autoridad sobre el mismo archivo.
4. `inicializar` considera existente una base con cualquier tabla PUBLIC; sólo admite `rb_version=1`, y el CHECK de esa tabla impide actualizarla simplemente a 2. El DDL de creación se ejecuta antes de la transacción de semillas. No hay protocolo de migración ni de recuperación de inicialización incompleta.
5. Cargar agregado, historial y revisión por separado no audita su relación completa. El constructor del dominio no comprueba contador de antigüedad, continuidad del historial, correspondencia de cada cierre con su inicio ni falta de eventos. El mapeo puede omitir filas huérfanas si se desactivaron restricciones en un fixture; se requiere lectura estructural además del agregado.
6. `transicionar` conserva estado/liberación/evento, pero no resuelve marca de revisión porque no existe. `InstantaneaTaller` tampoco la expone.
7. Un rollback fallido de consulta provoca un ID sintético incierto en memoria. La estrategia durable debe cubrir fallos de lectura/infraestructura además de mutaciones, sin inventar eventos de negocio de consultas.
8. Error de lectura inconsistente con rollback conocido devuelve ERROR_TECNICO, pero no establece hoy un bloqueo permanente por inconsistencia. H4 debe distinguir entrada inválida del usuario de datos persistidos inválidos y bloquear estos últimos.

Las suites H1/H2/H3 contienen pruebas relevantes de reglas, rollback real, fallo antes/después del delegado de commit, exclusividad, carreras y lectura coherente. `reaperturaLimpiaMismaBase_CA16parcial` vuelve al mapa estático: no demuestra reinicio. `H3Fixture.durable` abre otra conexión, tampoco otra JVM. Los fixtures inyectados deben continuar exclusivos y nunca operar concurrentemente con una fachada normal alternativa.

Los cierres y auditorías registran 17 pruebas H1, 34 acumuladas H2 y 71 acumuladas H3 aprobadas en el entorno Windows del responsable. Son antecedentes documentales, no ejecuciones de esta propuesta. El cierre H3 registra fallos H2 90097-232 en el entorno interno de Codex y éxito externo; su causa raíz no está determinada.

## 3. Componentes y archivos previstos

Las rutas Java siguientes son relativas a `src/main/java/ar/edu/unlam/reparabarrio/`. Son cambios futuros sujetos a aprobación; no se crean ahora.

| Archivo previsto | Tipo | Responsabilidad verificable |
|---|---|---|
| `arranque/GuardiaInstancia.java` | Nuevo | Abrir archivo fijo, adquirir `tryLock`, conservar canal/lock y liberar después de cerrar conexiones |
| `arranque/FabricaContextos.java` | Nuevo | Resolver raíz explícita y ruta estable, componer un propietario por contexto; fixtures con raíz exclusiva |
| `aplicacion/ContextoTaller.java` | Nuevo | Propietario AutoCloseable de fachada, identidad, ciclo de vida y guardia; no otro inventario |
| `aplicacion/ServicioRecuperacion.java` | Nuevo | Orquestar auditoría, reconciliación, apertura de sesión y marcado de revisión bajo el coordinador |
| `aplicacion/InformeRecuperacion.java` | Nuevo | Resultado inmutable: identidad, salud, sesión, operaciones verificadas, revisiones y hallazgos; sin instantánea confirmada cuando no sea válida |
| `aplicacion/AuditorCoherencia.java` | Nuevo | Comprobar relaciones persistidas e historial contra dominio; función verificable sin UI/JDBC concreto |
| `aplicacion/puertos/RepositorioRecuperacion.java` | Nuevo | Lectura estructural y persistencia de sesión, intención, diagnóstico, resolución y revisión |
| `persistencia/RepositorioRecuperacionJdbc.java` | Nuevo | Implementar ese puerto en la conexión de cada unidad de trabajo, sin commits propios |
| `persistencia/InicializadorBase.java` | Nuevo, extraído de la fábrica actual | Distinguir creación, esquema completo, versión conocida y transición autorizada; validar WRITE_DELAY |
| `persistencia/PersistenciaH2.java` | Modificado | Integrar guardia/propietario y dejar de ser una composición operativa desprotegida; conservar compatibilidad según decisión de sección 8 |
| `persistencia/UnidadTrabajoJdbc.java` y `aplicacion/puertos/UnidadTrabajo.java` | Modificados | Proveer repositorio de recuperación en la misma transacción; contabilizar cierre sin compartir conexiones |
| `aplicacion/ServicioTaller.java` | Modificado | Protocolo durable alrededor de operaciones efectivas, control de ciclo/salud, resolución transaccional de revisión al cerrar |
| `aplicacion/CoordinadorTaller.java` | Modificado | Proteger salud, operación activa, ciclo y diagnóstico del mismo contexto; conservar lock justo |
| `aplicacion/InstantaneaTaller.java` | Modificado | Añadir información inmutable de revisión asociada por orden; no otro estado de orden |
| `src/main/resources/persistencia/esquema.sql` | Modificado futuro | Esquema nuevo con metadatos H4; mantener tablas/reglas de negocio |
| `src/main/resources/persistencia/migracion-v1-v2.sql` | Nuevo futuro | Evolución explícita y verificable v1→v2; procedimiento aprobado y autorización específica antes de tocar base operativa |

El puerto de recuperación puede incluir los valores pequeños de sesión/intención/revisión como tipos anidados; no crear una clase delegadora por cada campo. `RepositorioTaller` y su adaptador conservan operaciones de negocio y el testigo en historial; sólo se ampliarían si la auditoría requiere datos actualmente inaccesibles. No extraer el oráculo de JUnit como dependencia de producción.

Pruebas nuevas previstas bajo `src/test/java/...`: `arranque/GuardiaInstanciaTest`, `aplicacion/CicloContextoTest`, `persistencia/RecuperacionH4Test` y auxiliar `persistencia/ProcesoH4`. Adaptar fixtures H2/H3 para cierre y para la composición ratificada; conservar sus casos y aserciones semánticas. No cambiar dominio ni planificación H1, catálogo, POM o versiones por defecto. No crear ventana, simulador, estadísticas o JAR ejecutable final; el auxiliar multiproceso es exclusivamente de prueba.

## 4. Identidad, guardia y ciclo de vida

### 4.1 Identidad del almacenamiento y de las propuestas

La fábrica operativa recibe una raíz absoluta explícita y verificada del proyecto, no usa el directorio accidental de ejecución. Propone base fija `datos/operativo/taller` y guardia fija `datos/operativo/instancia.lock`. Resuelve raíz/directorio mediante `toRealPath`, valida permanencia dentro del proyecto y rechaza rutas con semicolon o sintaxis que pueda introducir parámetros JDBC. No ofrece un selector libre de base operativa.

Separar clave del almacenamiento de identidad lógica: la primera es la ubicación real permitida y la segunda un UUID de base persistido en metadatos. Se conserva String para contexto en los contratos existentes; la fachada normal usaría ese UUID, sin normalizar IDs de órdenes/recursos. Una base distinta tiene UUID distinto. Una copia física de base no debe publicarse como otro contexto operativo concurrente: su importación/movimiento queda fuera del MVP y exige revisión. No deducir identidad del PID ni inventar un UUID diferente en cada sesión.

La guardia del directorio real elimina alias normalizables de la ruta operativa. En Windows se verifica la equivalencia de rutas con APIs del sistema de archivos, sin convertir todo el texto a minúsculas. Se propone rechazar enlaces simbólicos del archivo de base y ubicaciones alternativas, documentando límites ante enlaces duros/reemplazos manuales. No afirmar protección contra edición externa hostil o bases copiadas arbitrariamente; la vía normal no las admite. Las pruebas pueden usar múltiples raíces temporales exclusivas, cada una con su guardia.

La identidad UUID reemplaza la ruta actualmente expuesta como contexto según H4-D02 ratificada. Se mantienen los campos String de propuestas, resultados y dominio, así como IDs sensibles a mayúsculas y catálogo aprobado. Propuestas antiguas con contexto de ruta no se convierten ni aceptan automáticamente: pertenecen a la identidad anterior y se solicita una propuesta nueva. Una propuesta H4 con UUID de la misma base conserva su identidad al reabrir; un cambio de sesión no la caduca. No se recuperan solicitudes en memoria ni se reenvían automáticamente; toda presentación revalida contexto, primera factible y unidades exactas, sin comparación de revisión global ni tiempo. Fixtures obtendrán el UUID desde su propietario y comprobarán estabilidad entre sesiones y diferencia entre bases; las rutas permanecen sólo como datos de almacenamiento/diagnóstico.

### 4.2 Guardia y propietario

Adquirir `FileChannel.tryLock` exclusivo antes de abrir H2, también durante migración y recuperación. Archivo de guardia permanente: no borrarlo al cerrar, porque otra instancia podría bloquear otro archivo con igual nombre. Tratar lock nulo, `OverlappingFileLockException`, permiso insuficiente y otros errores con diagnóstico distinto; ninguna ruta de fallo debe continuar abriendo la base.

La segunda JVM termina el intento de apertura con diagnóstico de instancia ocupada y sin inicializar, recuperar, modificar o consultar H2. En H4 se devuelve informe técnico al llamador; H5 lo presentará. PID/fecha eventualmente escritos en la guardia sólo ayudan al diagnóstico: no autorizan a romper un lock. H2 sin AUTO_SERVER mantiene su defensa secundaria.

La fábrica registra un `ContextoTaller` propietario, no sólo `ServicioTaller`. Aperturas simultáneas de la misma ubicación dentro de la JVM devuelven el mismo propietario/fachada. Registro y apertura se protegen por sincronización de composición; no mantener esa sincronización mientras se llama a una fachada. No adquirir el registro desde una transacción JDBC. Retiro después del cierre se hace por identidad del propietario para no borrar una apertura nueva.

Ciclo interno: ABRIENDO, HABILITADO, BLOQUEADO, CERRANDO y CERRADO. Son estados técnicos del contexto, nunca valores de `EstadoOrden`. Toda llamada comprueba ciclo y salud bajo el coordinador. Referencias antiguas permanecen cerradas y no pueden operar tras abrir un nuevo propietario.

H4-D01 ratifica una única composición operativa protegida. La fábrica pública recibe la raíz del proyecto y devuelve el propietario; los clientes sólo reciben su fachada, informes y cierre. `PersistenciaH2.abrir(Path)` se conserva como adaptador obsoleto: admite exclusivamente la ruta operativa fija verificada, delega en esa fábrica y devuelve la misma fachada. Rechaza rutas alternativas antes de JDBC; nunca crea una base ni migra por esta firma, que no expresa autorización. Si falta creación, migración o aceptación de adopción, informa la condición y remite al contrato explícito de apertura. No conserva un segundo mapa/autoridad y no devuelve servicios bloqueados como recién habilitados.

Se retira el constructor público inyectable de `ServicioTaller`: los constructores quedan con visibilidad de paquete en `aplicacion`, incluida la variante con observador. `ContextoTaller`, en ese mismo paquete, crea el servicio únicamente con una capacidad de propietario vigente. La capacidad es un objeto final con emisión privada, ligado a identidad, coordinador, guardia y ciclo; no basta un booleano o una ruta aportada por el cliente. El ensamblado usa una entrada controlada de `ContextoTaller` que adquiere/verifica esa capacidad antes de aceptar los puertos JDBC. Las operaciones comprueban su vigencia bajo el coordinador. Adaptadores JDBC y repositorios concretos siguen sin acceso público para clientes; no se ofrece una fábrica pública arbitraria de conexiones de negocio. La protección cubre las vías soportadas del proyecto, no reflexión hostil ni acceso externo al archivo.

La ayuda inyectable de pruebas se ubica en `src/test` dentro de `aplicacion`, donde puede usar constructores de paquete, y se comparte con tests de persistencia mediante una ayuda pública sólo de test. Obtiene propietario/guardia para una raíz exclusiva `target/h4-*`; decora los puertos del único propietario antes de publicar la fachada. No abre un segundo coordinador sobre la misma base ni permite raíces operativas. Los fixtures v1 se preparan con conexiones cerradas y luego se abren/adoptan por ese propietario. Lecturas independientes del oráculo se realizan con escritores detenidos y sin publicar otra fachada. Estas adaptaciones futuras preservan el propósito de las 71 pruebas H1/H2/H3.

### 4.3 Arranque y cierre

Orden de arranque:

1. Resolver raíz/rutas y adquirir guardia.
2. Clasificar archivos sin publicar fachada; aplicar el flujo de autorización, copia y apertura de sección 7.1 antes de cualquier apertura que pueda escribir. Nunca crear una base vacía automáticamente sobre un archivo operativo esperado que desapareció.
3. Auditar estructura, agregado, historial, contadores y metadatos; leer sesión anterior e intenciones no resueltas.
4. Reconciliar las intenciones según sección 5. Si no hay evidencia suficiente, conservar bloqueo y ofrecer sólo diagnóstico/recuperación.
5. Si la sesión previa quedó abierta, registrar revisión para todas las EN_CURSO actuales; conservar revisiones anteriores. En base heredada sin sesión, aplicar adopción conservadora ratificada H4-D04 según sección 7.1.
6. En una transacción de infraestructura registrar sesión nueva abierta, UUID de sesión y revisiones; confirmar y releer si su resultado es incierto. No habilitar hasta verificarlo.
7. Publicar fachada e informe con datos verificados. EN_CURSO con revisión no vuelve inconsistente el contexto: siguen ocupadas y los casos normales respetan sus asignaciones.

Cierre explícito idempotente: detener admisión de solicitudes, esperar fuera del coordinador las operaciones ya admitidas, tomar el coordinador, comprobar ausencia de transacciones activas y cerrar conexiones residuales. Sólo si la salud es verificable y no hay intenciones pendientes se confirma marca de sesión limpia mediante conexión privada final, se cierra esa conexión y se libera guardia. Retirar propietario del registro después. Una carrera cierre/mutación produce operación completada antes del drenaje o rechazo técnico por cierre; nunca escritura posterior a liberación de guardia.

No ejecutar `SHUTDOWN` desde una conexión alternativa mientras existan operaciones activas. No esperar futures ni diálogos bajo el lock. Shutdown hook puede intentar cierre acotado, pero no sustituye `close` ni permite afirmar cierre limpio ante terminación forzada. Si un cierre vence su presupuesto o falla una conexión que podría seguir activa, mantener guardia mientras viva el proceso; no habilitar otro propietario. Un contexto bloqueado puede cerrarse conservando sesión abierta/intención pendiente; jamás marcarlo limpio para ocultar incertidumbre.

Cierre limpio conserva EN_CURSO y revisiones anteriores. No crea revisión nueva por mera existencia de reparaciones abiertas. Si falla después de marcar limpio pero antes de liberar guardia, los datos ya drenados son coherentes; no se infiere avance físico de reparación. Fallo de marca limpia mantiene diagnóstico y exige verificación al siguiente arranque.

## 5. Salud durable y reconciliación

### 5.1 Metadatos propuestos en H2

H2 sigue siendo la única fuente mutable de datos confirmados. No se agrega archivo JSON, journal paralelo de negocio ni dependencia nueva. Propuesta lógica:

| Conjunto | Información conservada |
|---|---|
| Metadatos de contexto | UUID de base, versión de protocolo, sesión actual/anterior y abierta/limpia |
| Operaciones de infraestructura | ID único, UUID de base/sesión, clase (negocio/diagnóstico de lectura/arranque/cierre), descriptor, revisión base, fase, diagnóstico y resolución verificada; no registrar cada consulta normal |
| Revisión humana | Orden, sesión interrumpida detectada, motivo y momento; pendiente o resolución vinculada al cierre confirmado |

Descriptor de negocio: acción, orden/recurso afectado, entradas exactas relevantes y recursos propuestos, sin serialización Java de objetos. Para alta conserva descripción, requisitos y habilitación solicitada; para cambio de habilitación conserva destino. Permite contrastar testigo y efecto, no volver a ejecutar la orden. Un solo intento activo por contexto por la serialización; resoluciones históricas conservan su ID para consulta. No hay API de reenvío automático.

**OD-H4-01 — correspondencia exacta.** Un único UUID textual `I` se genera por intento mutante y se usa, sin regenerarlo, como clave primaria de intención, `ResultadoOperacion.operacionId` y `historial.operacion_id` cuando hay cambio efectivo. El puerto recibe `I` explícitamente; los repositorios no generan un segundo UUID. Una colisión se rechaza antes de negocio; no se reutiliza un ID resuelto. Para cada mutación H4 efectiva debe existir exactamente un evento con ID `I`, y su resolución `CONFIRMADA` conserva la secuencia de ese evento como referencia verificable. La intención previa y el evento son filas distintas con el mismo ID; la resolución actualiza esa intención, no crea un nuevo intento. Claves únicas y referencia al evento desde la resolución refuerzan la relación; la auditoría exige intención para eventos del protocolo H4. Los eventos heredados v1 conservan sus IDs y se identifican como anteriores a la adopción, sin fabricarles intenciones retroactivas.

El descriptor y la revisión previa se congelan antes del negocio. La transacción de preparación de intención lee revisión y, para cierre, el conjunto activo exacto bajo el coordinador, sin escrituras funcionales; persiste la intención y se cierra antes de abrir negocio. Se revalida ese conjunto en negocio. Para alta se verifica además descripción/requisitos/habilitación, que el evento actual no contiene. Entradas imposibles de representar se rechazan antes de persistir intención y sin abrir negocio; no se exige durable el motivo de esos rechazos. Una resolución sin efecto almacena causa conocida (`RECHAZADA`, `SIN_CAMBIO` o error con rollback acreditado), sin referencia a evento. Si esa causa no sobrevivió a la caída, la recuperación sólo acredita `NO_CONFIRMADA`; no inventa el motivo del rechazo ni una respuesta perdida. Diagnósticos de lectura e infraestructura usan IDs propios y nunca el espacio semántico de un evento de negocio.

Metadatos no incrementan `contador.antiguedad` ni el historial de cambios efectivos. Mantener revisión del historial actual y añadir revisión de recuperación para señales asociadas; `InstantaneaTaller` podrá llevar ambas en un valor de versión. H5 deberá comparar ese valor para respuestas tardías. No insertar acciones nuevas en el historial funcional que invaliden su semántica ni el oráculo H3.

### 5.2 Protocolo antes de cualquier posible efecto

Se elige intención previa durable en una transacción H2 separada, bajo el mismo coordinador y antes de abrir la transacción de negocio. A continuación se abre la unidad de trabajo de negocio. Nunca mantener dos transacciones abiertas simultáneamente ni añadir commits dentro del repositorio de negocio.

1. Generar UUID de intento. Confirmar intención con descriptor y revisión base usando conexión de infraestructura; cerrar esa conexión. Si su commit es incierto, no comenzar negocio: bloquear e inspeccionar después la intención. Una sesión abierta ya conservada obliga a auditar aunque fallen escrituras posteriores de diagnóstico.
2. Abrir conexión de negocio, leer, validar y ejecutar exactamente las escrituras actuales. Intención no reserva unidades ni genera orden válida. Un rechazo puede dejar sólo metadatos técnicos, nunca efecto funcional.
3. Efecto efectivo y testigo `historial.operacion_id=I` se confirman juntos. Marcar resolución de la intención `I` en esa misma transacción de negocio cuando haya cambio, con referencia a la secuencia del evento y utilizando su repositorio de recuperación. La marca tiene el mismo destino atómico que el negocio.
4. Rechazo, SIN_CAMBIO o error con rollback conocido: cerrar conexión de negocio y confirmar resolución técnica sin efecto en otra transacción. No incrementar prioridad ni revisión funcional.
5. Commit incierto o rollback no comprobado: conservar intención pendiente, bloquear memoria bajo el lock y no declarar resultado final. Intentar enriquecer diagnóstico sólo después de cerrar la conexión dudosa; si el almacenamiento no responde, la intención previa sigue disponible. No hacer rollback posterior para pretender que demuestra ausencia de commit.

Las consultas normales siguen el protocolo específico de sección 5.5: no requieren intención previa por lectura. Nunca introducen un evento funcional. La observación H3 mantiene ID de intento nulo para consultas normales; su muestra no es evidencia durable ni controla la recuperación.

Si falla el commit de resolución técnica tras rollback conocido, el negocio sigue acreditadamente sin efecto, pero no habilitar otra operación hasta reconciliar salud. Si falla sólo diagnóstico después de commit confirmado de negocio, preservar CONFIRMADA con aviso; no degradar la operación a rechazo o incertidumbre de negocio. Salud técnica puede permanecer bloqueada hasta verificar metadatos. Estas diferencias deben probarse expresamente.

Marcas de sesión/revisión son operaciones de infraestructura con identidad propia y verificación de valores esperados ante commit incierto. Una sesión previa abierta garantiza arranque conservador aun cuando no pueda guardarse diagnóstico. No usar recursivamente el protocolo de intenciones para escribir su propia intención: ante fallo de esa escritura, el negocio aún no comenzó, y el arranque audita sesión/estructura y presencia de esa intención antes de habilitar.

Costo: la intención añade una transacción durable previa por intento mutante; rechazos y SIN_CAMBIO añaden resolución separada. No se añaden esas escrituras a cada lectura según la recomendación pendiente de sección 5.5. No se fijan metas de latencia sin medir.

### 5.3 Reconciliación sin repetir comandos

Servicio de recuperación adquiere el mismo coordinador, exige guardia válida y ausencia de conexión/transacción dudosa activa. Puede ejecutarse al arrancar o mediante llamada explícita posterior. No crea otra fachada ni borra salud por abrir una conexión nueva.

Leer una transacción coherente y completa con configuración validada, identidad esperada, sesión, intenciones, agregado, historial y datos estructurales. La auditoría completa es condición previa a resolver. Para cada ID:

| Evidencia | Resolución |
|---|---|
| Un único testigo con ID exactamente `I`, descriptor coincidente, resolución atómica vinculada al evento y cadena completa coherente | Operación íntegra confirmada; conservar ID y evidencia, informar confirmación reconciliada sin repetir efecto |
| Intención `I` comprobada, lectura completa y válida sin testigo `I`, sin efectos atribuibles, revisión base compatible y transacción dudosa terminada | Ausencia acreditada de confirmación; registrar operación no confirmada, sin llamarla rechazo de negocio si no se conoce esa causa |
| Intento de lectura sin efecto funcional y auditoría completa, conexión anterior terminada | Salud verificable recuperada; no inventar resultado de consulta ya perdida |
| Testigo incompatible, efecto sin testigo, esquema desconocido, contador/historial contradictorio o identidad inesperada | Inconsistencia: mantener bloqueo y diagnóstico para revisión humana |
| Base inaccesible, lectura parcial/fallida o conexión anterior potencialmente activa | Resultado aún indeterminado: mantener bloqueo, sin éxito ni rechazo definitivo |

No exigir que una orden siga en el estado inmediatamente posterior a una operación histórica: cierres posteriores confirmados pueden explicar su evolución. Auditar la cadena hasta el estado actual. Si existen escrituras posteriores pese a una intención no resuelta, auditar su procedencia y no interpretar mera ausencia de ID como prueba suficiente.

La ausencia se acredita sólo después de recuperación H2 completa, con identidad/versión esperadas, guardia, ningún escritor/conexión dudosa activo y lectura estructural íntegra. Comparar revisión base, contadores, filas y cadena del historial para descartar efectos sin testigo. Una resolución `CONFIRMADA` sin evento, evento H4 sin intención o ID incompatible bloquea por inconsistencia. Una lectura parcial, archivo inaccesible o terminación no acreditada conserva incertidumbre. No correlacionar por timestamp ni sólo por orden: varios intentos pueden referirse a la misma orden, y eventos de recursos tienen `orden_id` nulo. Si no puede guardarse una resolución ya acreditada, el informe distingue resultado conocido de bloqueo técnico de metadatos; no vuelve incierto el negocio.

Confirmar resolución de metadatos y releerla si es incierta antes de habilitar. Acreditar ausencia nunca equivale a reconstruir una transacción ni compensar un commit. El usuario podrá emitir una solicitud nueva después de recibir resolución; no se reenvía automáticamente la antigua. Los métodos de negocio actuales generan UUID nuevos, por lo que no ofrecen idempotencia universal ante repetición arbitraria del cliente: H4 evita duplicación por recuperación y mantiene rechazos por estado/ID. No prometer entrega exactamente una vez a sistemas externos.

### 5.4 Auditoría de coherencia

Verificar todas las tablas relevantes y sus referencias, sin confiar exclusivamente en mapeos que descartan filas. Comprobar esquema/versiones, catálogo cerrado, valores obligatorios, IDs/texto válido, unicidad, requisitos y tipos, asignaciones completas exclusivamente de EN_CURSO, habilitación de ocupadas y recursos exclusivos. INV-01 a INV-05 y INV-09 deben cumplirse antes de publicar datos.

Contador de antigüedad igual al máximo de secuencia de órdenes (cero en base vacía), sin registros eliminados ni secuencias duplicadas; historial con secuencia coherente y revisión igual al último evento. Reconstruir por historial altas, salidas únicas de PENDIENTE/EN_CURSO, conjuntos de INICIO y liberación exacta en FINALIZADA/FALLIDA; comparar estados, asignaciones activas y disponibilidad actuales. Comprobar prioridad histórica donde existan hechos suficientes según el modelo aprobado, sin inventar tiempos físicos.

El historial heredado no guarda descripción/requisitos completos ni habilitación inicial en REGISTRO_RECURSO. Esos campos se contrastan con las tablas conservadas; la habilitación inicial puede inferirse del primer cambio posterior o del valor final si no hubo cambios, como hace el oráculo H3, explicitando el límite. No afirmar detección de toda alteración deliberada ni integridad criptográfica. Falta de evento, acción desconocida, cierre sin inicio, recursos liberados distintos o contradicción no se corrigen automáticamente.

Un diagnóstico incluye contexto, sesión, ID de operación, fase, invariante, entidad/recurso y causa técnica/SQLState cuando exista. En bloqueo, consultas normales no llevan instantánea ni propuesta; diagnóstico no expone un agregado no auditado como confirmado. Fallos de entrada siguen siendo rechazos normales; fallos de persistencia incoherente bloquean contexto.

### 5.5 OD-H4-04 — consultas sin intención durable por llamada

| Estrategia | Garantía y límite | Costo y fronteras de fallo |
|---|---|---|
| Intención previa y resolución posterior para cada lectura (v0.1) | Conserva el ID exacto incluso si el proceso cae antes de guardar diagnóstico; no conserva por sí sola la respuesta ni añade evidencia de negocio | Dos commits técnicos por consulta además de lectura/rollback/cierre; mayor trabajo de disco y posesión del coordinador, fallos nuevos de intención/resolución capaces de bloquear una lectura sana |
| Sesión abierta durable, consulta estrictamente sin escrituras, bloqueo en memoria y diagnóstico durable sólo ante fallo | Conserva PT-06 para mutaciones mediante sus intenciones; al reiniciar exige auditoría por sesión abierta aunque se haya perdido el ID de una lectura fallida. No reconstruye la respuesta de esa lectura | Una transacción de lectura normal; escritura técnica sólo ante fallo y cuando sea seguro abrirla. Menos fronteras de fallo y costo esperado, sin afirmar cifras ni latencia medida |

**Recomendación para v0.2, pendiente de aprobación humana adicional:** adoptar la segunda estrategia. RF-05/10 y PLAN 3.2 no exigen persistir consultas; una lectura que nunca ejecuta SQL mutante ni commit de negocio no tiene efecto funcional cuyo resultado deba localizarse por testigo. La sesión debe estar confirmada abierta antes de admitir cualquier consulta y mantenerse abierta si hay problemas técnicos. El camino de lectura recibe sólo capacidades de lectura del repositorio y ejecuta SELECT; `Connection.setReadOnly` es defensa adicional, no prueba suficiente. No usar esta optimización para comandos SIN_CAMBIO: antes de validar podrían producir efecto y siguen teniendo intención mutante.

Consulta sana: adquirir coordinador, comprobar salud/ciclo, abrir unidad privada, capturar instantánea, rollback conocido y cierre acreditado, devolver valor y liberar. Fallo de rollback: asignar ID técnico en memoria, devolver PENDIENTE_VERIFICACION sin valor y bloquear cualquier operación normal; tras terminar la conexión, intentar conservar diagnóstico con ese mismo ID. Fallo de cierre con rollback ya conocido: efecto de lectura nulo conocido, pero bloquear técnicamente mientras no se acredite terminación de conexión y devolver ERROR_TECNICO sin valor. No liberar guardia ni marcar sesión limpia con conexión potencialmente activa. Si el almacenamiento impide guardar diagnóstico, la sesión previamente abierta obliga al siguiente proceso a auditar antes de habilitar. Si el proceso cae antes de persistir el ID técnico, el informe declara que éste no se recuperó y se identifica la sesión; no inventa un ID histórico. Con conexión terminada, auditoría completa y resolución técnica verificada puede habilitarse de nuevo, sin reconstruir ni reintentar la lectura perdida.

La lectura fallida no se reconcilia como mutación confirmada. Un fallo posterior a la entrega poslock del observador mantiene su tratamiento H3 y no cambia la salud transaccional. Esta recomendación modifica el detalle de v0.1, no PT-06; requiere aceptar expresamente que se conserva una señal durable de sesión para lecturas, pero no necesariamente su ID individual tras caída inmediata. Si se exige conservar cada ID de lectura, se deberá ratificar la primera alternativa antes de implementar. Las métricas H3 permanecen descartables y nunca acreditan cierre, rollback ni commit.

### 5.6 OD-H4-05 — contratos de salud y resultados

Los estados existentes de `ResultadoOperacion` conservan significado. Se propone diagnóstico estructurado aditivo (código técnico, fase, causa, salud posterior y durabilidad de diagnóstico), con adaptación de construcción para los clientes actuales. `InformeRecuperacion` separa ciclo/salud técnica, resultado por intento, sesión y revisiones humanas; el UUID de base puede estar ausente si no se pudo leer. La revisión técnica de instantánea se añade conservando `revision` funcional y sin caducar propuestas. No convertir salud en un estado de orden.

| Situación | Resultado/informe | Mutaciones y consultas permitidas |
|---|---|---|
| Coherente con EN_CURSO revisables | HABILITADO con revisiones pendientes e instantánea verificada | Todas las operaciones normales sujetas a reglas; finalizar/fallar resuelven marca, aceptar aviso no; consultas/propuestas admitidas |
| Resultado pendiente de verificación | PENDIENTE_VERIFICACION, ID mutante o técnico y sin valor normal | Ninguna mutación ni consulta normal; sólo diagnóstico, reconciliación y cierre conservador |
| Inconsistencia bloqueante | ERROR_TECNICO con código INCONSISTENCIA e invariantes afectados | Sólo informe/diagnóstico, auditoría controlada y cierre; no reparar negocio ni publicar instantánea/propuesta |
| Segunda instancia ocupada | Informe de apertura INSTANCIA_OCUPADA; no crea propietario/fachada | Ningún acceso H2, consulta o recuperación por ese intento; sólo diagnóstico de guardia |
| Referencia cerrada | ERROR_TECNICO con código REFERENCIA_CERRADA | Ninguna operación ni apertura JDBC por la referencia vieja; cierre idempotente e información local de ciclo; nueva apertura sólo por fábrica |
| Negocio confirmado y fallo técnico posterior | CONFIRMADA con el mismo ID/valor y aviso, informe con negocio confirmado y diagnóstico fallido | No repetir negocio. Si no hay duda de conexión/metadatos requeridos, continúa normal; si la hay, bloqueo técnico, sólo diagnóstico/reconciliación/cierre hasta verificar |

Un error de metadatos o cierre posterior a commit conocido jamás cambia CONFIRMADA a RECHAZADA o PENDIENTE_VERIFICACION de negocio. Una causa técnica desconocida no se informa como inconsistencia probada. Diagnóstico local accesible no implica acceso JDBC: bajo bloqueo, las lecturas de auditoría se realizan únicamente por recuperación, con guardia y conexiones anteriores terminadas.

## 6. Revisión humana de EN_CURSO

Tras sesión inesperadamente abierta y recuperación coherente, persistir necesidad de revisión en una transacción de infraestructura para cada EN_CURSO. Marca idempotente por orden/interrupción; reinicios sucesivos conservan pendientes sin duplicar ni eliminar su origen. Conservar asignación y ocupación incluso si la operación de inicio fue reconciliada como confirmada tras caída.

La marca no es sexto estado, reserva, fallo automático ni impedimento global por sí misma. Con base coherente se pueden consultar datos, administrar recursos libres y operar según las reglas actuales. No habilitar manualmente recursos ocupados ni cancelar EN_CURSO.

`finalizar` o `fallar` existentes validan EN_CURSO y conjunto completo, cambian estado, liberan exclusivamente recursos propios, guardan evento y resuelven revisión **en la misma transacción**. Rollback conserva tanto asignación como marca. Commit incierto bloquea contexto hasta reconciliar estado, liberación, historial y resolución conjuntamente. Doble cierre no libera recursos reasignados. Reconocer un aviso, cerrar la aplicación o abrir limpiamente no resuelve revisión.

La instantánea verificada incluye mapa inmutable de revisión por orden. Informe de recuperación distingue «contexto coherente con revisión humana pendiente» de «contexto bloqueado técnicamente». H4 ofrece esos contratos y prueba las transiciones por fachada; H5 implementará presentación y acciones humanas.

## 7. Evolución del esquema, alternativas y riesgos

### 7.1 Compatibilidad de base existente

Proponer versión 2 con metadatos H4, conservando todos los datos funcionales v1. No ejecutar DDL automáticamente ante versión desconocida o estructura incompleta. Una base nueva tiene inicialización identificable; una base existente ilegible no se reemplaza por otra vacía.

**OD-H4-02 — autorización por base.** H4-D03 aprueba el diseño de evolución con condición, no una migración operativa. Antes de una conexión H2 sobre el original existente, se permite inspección local de existencia, rutas, permisos, tamaños y huellas sin escribir ni recuperar el archivo. Bajo guardia y sin conexiones activas, un diagnóstico JDBC podrá usar sólo un modo realmente de lectura sin creación (`IFEXISTS` y configuración de sólo lectura cuya efectividad deberá verificarse en fixtures); si requiere recuperación/escrituras o no puede garantizarse, no abrir el original y emitir informe limitado. Una autorización de inspección sobre copia o de recuperación física debe explicitar esas escrituras. No usar `PersistenciaH2.inicializar` como inspección: hoy podría crear tablas/semillas y H2 podría recuperar al abrir. En esta ejecución sólo se leen documentos/fuentes, no archivos de base.

| Base detectada | Flujo propuesto antes de habilitar |
|---|---|
| Nueva, ausencia comprobada y creación solicitada explícitamente | Autorización de apertura con modo CREAR_NUEVA y ruta fija; inicializar v2 con UUID nuevo, protocolo identificable y sesión; auditar. Una base antes conocida que falta no se clasifica como nueva |
| v1 íntegra | Informe previo con ruta/huella, versión, auditoría disponible y límite de procedencia; solicitar autorización específica v1→v2 y preservación. Tras autorización y copia verificada, migrar, auditar y mantener adopción pendiente hasta aceptación explícita |
| Migración interrumpida reconocida | Bloqueo de arranque; conservar original/copia, contrastar protocolo, pasos y autorización. Reanudar sólo dentro del permiso específico vigente y con precondiciones acreditadas; si faltan, solicitar decisión específica sin ejecutar DDL |
| Ilegible, desconocida o intermedia sin procedencia acreditada | Informe limitado y bloqueo; ninguna recreación, reseed ni migración automática. Cualquier recuperación sobre copia, restauración o intervención del original requiere alcance humano específico |

La autorización se solicita después del informe previo y antes de toda escritura del original: identifica ruta canónica, huella del conjunto preservado, versión origen/destino, alcance de recuperación física si necesaria, destino de copia y permiso de reanudación. Se representa en el contrato de arranque como autorización explícita del responsable, no como un booleano por defecto, ratificación del diseño ni existencia de v1. Ante rechazo o abandono previo no se migra ni se abre sesión; se libera guardia tras cerrar cualquier acceso de inspección. Si ya comenzó la transformación autorizada, abandono deja intermedio bloqueado, conserva copias/diagnóstico y no implica restauración automática.

Preservación propuesta dentro de `datos/preservacion/h4/<id-de-autorizacion>/`, sin sobrescritura: guardia adquirida, exclusión de escritor, todas las conexiones cerradas, inventario completo de archivos H2 persistentes de la base (incluidos artefactos de recuperación presentes), copia byte a byte, tamaños y SHA-256 cotejados tras cerrar y forzar archivos de destino. Fallo o falta de espacio aborta antes de transformar. No basta copiar sólo un archivo mientras H2 escribe. La huella acredita igualdad de bytes, no coherencia lógica: validar una copia de trabajo derivada, conservando intacta la copia de preservación; si la apertura requiere recuperación H2, realizarla únicamente sobre la copia de trabajo dentro del alcance autorizado. Si no se puede acreditar integridad recuperable, bloquear antes del DDL del original. Ubicación y procedimiento quedan sujetos a aprobación del diseño completo y permiso concreto, nunca se ejecutan ahora.

La migración v1→v2 requiere reemplazar la restricción `version=1`, crear metadatos y persistir identidad estable. DDL no se supondrá atómico junto con DML. El primer paso autorizado registra un marcador durable de protocolo, autorización, origen/destino, UUID elegido y referencia/huella de preservación; se valida su presencia antes de cambios siguientes. Si queda creada su tabla sin fila, sólo se admite esa frontera exacta comprobada contra el esquema y la autorización, no se deduce una migración válida. Cada paso tiene pre/postcondición de estructura, restricciones y contenido, se relee lo realmente persistido y después se registra avance. Caída entre DDL y marcador exige reconocer exactamente la postcondición; `IF NOT EXISTS` no basta para afirmar compatibilidad. No modificar filas funcionales ni IDs/eventos/contadores; comparar contenido completo contra v1 recuperada y auditada. Publicar versión final 2 sólo tras todas las estructuras y auditorías, y comprobar el marcador final incluso si `rb_version` ya dice 2. Verificar DDL concreto H2 2.3.232 en fixtures al implementar.

Reanudación segura exige misma base, copia preservada comprobada, protocolo conocido, autorización vigente para continuar, ningún escritor y contenido funcional conservado. Paso inesperado, marcador contradictorio, UUID diferente, base alterada, copia ausente o imposibilidad de leer bloquean; no se completan con suposiciones, no se vuelve a generar UUID y no se restaura automáticamente. El procedimiento no promete reparar cualquier esquema parcial ni certificar cortes físicos de disco. Restaurar una copia sobre el original es otra acción que requiere autorización específica.

H4-D04 ratifica que v1 sin sesión tiene procedencia desconocida: no reconstruir UUID de intento perdido ni certificar PT-06 retroactivo. Tras migración autorizada se marcan todas las EN_CURSO para revisión y se registra adopción pendiente, conservando estados/asignaciones; se persiste la última revisión funcional v1 como frontera que distingue eventos heredados de eventos sujetos a intención H4. Sin GUI H4, `ServicioRecuperacion` propone una llamada explícita de aceptación que recibe referencia/huella y revisión del informe auditado más declaración del responsable; su composición no la invoca automáticamente. Se comprueba vigencia del informe bajo guardia/coordinador, se confirma aceptación durable y se relee ante resultado incierto antes de abrir sesión normal. Puede ejercerse por un llamador local autorizado; no requiere diseñar consola o Swing. Aceptar adopción habilita sólo si la base es coherente y resuelta técnicamente; no resuelve revisiones ni sustituye evidencia de negocio. Rechazo/abandono posterior conserva v2 y adopción pendiente, sin volver a v1. Los fixtures v1 siguen exclusivos y pueden aportar aceptación explícita de test, sin presentarse como datos operativos.

### 7.2 Alternativas consideradas

| Alternativa | Evaluación |
|---|---|
| Persistir diagnóstico sólo al detectar excepción | Insuficiente: caída inmediata o disco inaccesible pierde ID/señal; se elige intención previa |
| Intención en la misma transacción de negocio | Insuficiente para identificar un intento revertido tras caída; conserva sólo operaciones confirmadas |
| Journal externo forzado con FileChannel | Podría conservar intento, pero agrega dos almacenamientos a reconciliar y protocolo de truncado/escritura; se prefiere metadata H2 manteniendo fuente única |
| Usar sólo lock embebido H2 | No cumple guardia previa aprobada PT-07; conservarlo como defensa adicional |
| UUID por sesión como contexto | Rechazaría propuestas por cambio de sesión y confundiría identidad de datos; UUID persistido de base y sesión separados |
| Liberar automáticamente EN_CURSO al reiniciar | Contradice RF-14/INV-09; se conserva asignación hasta cierre humano válido |
| Conexión JDBC compartida permanente | Añade recurso mutable entre hilos; mantener conexiones privadas y propietario de guardia sin conexión de negocio compartida |

Riesgos compartidos: registro de propietarios, guardia/canal, filas H2, sesión, intención, salud, revisión técnica y referencias a fachada. Orden de operación: coordinador → conexión privada → lectura/escritura → commit/rollback → cierre → unlock. La guardia se mantiene durante toda la vida; no se adquiere esperando bajo una transacción. Composición y retiro no invierten orden con coordinador; no se ejecutan callbacks de usuario bajo él.

Una intención previa añade latencia de disco y más fronteras de fallo. Transacciones se ejecutan secuencialmente y se cierran antes de abrir la siguiente para evitar bloqueos H2 entre conexiones propias. Una conexión dudosa no cerrada impide reconciliar/soltar guardia. Interrupción de hilo no garantiza cancelación JDBC; los presupuestos de prueba/cierre no autorizan declarar rollback ni liberar recursos físicos.

Justicia del lock no garantiza progreso de órdenes imposibles ni elimina bloqueo de primera factible no iniciada. Mantener RN-08/PA-13. Ensayos finitos no prueban ausencia universal de race conditions, deadlocks o starvation. WRITE_DELAY=0 y pruebas de caída de proceso no certifican pérdida física del disco ni todos los cortes eléctricos.

## 8. Ratificaciones incorporadas y asuntos antes de programar

No se propone cambiar PT-01 a PT-08. R04-H4 ratifica las siguientes decisiones exclusivamente para diseño; los detalles refinados y el documento completo siguen pendientes de aprobación:

| Decisión / estado humano | Alcance incorporado | Precisión propuesta y límite |
|---|---|---|
| H4-D01 APROBADA | Propietario, guardia previa y única vía operativa; bases exclusivas de test | Sección 4.2: adaptador heredado protegido, constructor de paquete y ayuda sólo de test; no habilita programación |
| H4-D02 APROBADA | UUID persistido de base distinto de sesión, tipos/reglas conservados | Sección 4.1: propuestas de ruta anteriores no se convierten; fixtures usan UUID del propietario |
| H4-D03 APROBADA CON CONDICIÓN | Evolución controlada v1→v2 y preservación ante interrupción | Sección 7.1: autorización específica por base antes de escribir, preservación verificable y reanudación limitada; sin permiso automático sobre datos operativos |
| H4-D04 APROBADA | Procedencia v1 desconocida, adopción conservadora y revisión EN_CURSO | Secciones 6/7.1: aceptación explícita por contrato sin GUI; no reconstruye hechos ni resuelve revisión |
| H4-D05 APROBADA | Informes/diagnósticos/instantáneas aditivos y negocio confirmado conservado | Sección 5.6: separar salud, conocimiento del resultado y revisión humana; fallo técnico posterior no degrada éxito |

Pendientes concretos: aprobación del diseño v0.2 y sus mecanismos de correspondencia, API, preservación/reanudación y aceptación; decisión adicional sobre la recomendación de consultas sin intención por llamada (5.5); autorización posterior separada de implementación. Cada migración de base operativa y cualquier recuperación que escriba exige además permiso específico de 7.1. No volver a solicitar ratificación de los cinco principios ya aprobados ni interpretar estas precisiones propuestas como aprobadas por R04.

**Asunto normativo residual separado:** PLAN 5.2 conserva la discrepancia textual RF-13/INV-06; PLAN 8.7 y `docs/CIERRE-FASE2.md` ratifican la interpretación PT-06. H4 aplica esa decisión humana: todo o nada, incertidumbre sin clasificación definitiva y reconciliación. No presenta PT-06 como decisión técnica reabierta ni declara cerrada por cuenta propia la aceptación textual residual de SPEC. Si cátedra/responsable exige aclaración de SPEC, debe aprobarse separadamente antes de modificarla; se transfiere como asunto de validación normativa, especialmente al cierre H7/P04.

Los encabezados históricos de auditorías/diseño anteriores que dicen aprobación pendiente no anulan los cierres humanos posteriores. La estrategia SDD menciona un nombre orientativo distinto para diseño H4; la solicitud actual fija `IMPLEM-H4.md` en raíz y prevalece, sin modificar la estrategia. No se detecta necesidad de un sexto estado, otra tecnología o nueva política de asignación.

## 9. Verificación futura

### 9.1 Infraestructura y evidencia

Todas las bases de ensayo serán exclusivas bajo `target/h4-*` dentro del proyecto, con raíz/guardia propias y comprobación de pertenencia antes de cualquier preparación o limpieza. Nunca usar `datos/operativo` real para interrupciones, corrupción o migración experimental. No realizar esas pruebas ahora.

Auxiliar de prueba lanzado mediante ProcessBuilder con Java del entorno y classpath de pruebas/dependencias locales. Proceso A crea/abre y escribe; proceso B distinto verifica recuperación sólo después de confirmar terminación de A. Registrar PID, sesión, ruta y salida; no usar el mapa estático como reinicio. Coordinar fronteras con señales por stdin/stdout u otro canal local de prueba, sin dependencia remota. Drenar stdout/stderr para no bloquear al hijo; timeouts y cierre de procesos en finally.

Controlador nunca necesita consultar fachada para liberar una pausa detenida bajo lock. Terminar forzadamente sólo el PID auxiliar propietario del fixture, esperar muerte y comprobar liberación de guardia antes de B. No inferir el punto exacto dentro del commit desde un sleep. Para «durante commit», combinar ventana real de terminación mientras JDBC está ejecutando commit con registro de frontera; si no puede acreditarse intercepción interna, documentar incertidumbre temporal y aceptar sólo estado anterior/completo, sin llamarlo ensayo determinista del interior de commit. Inyección antes/después del delegado es evidencia complementaria del tratamiento de comunicación y no sustituye la caída real.

Oráculo de prueba independiente del auditor de producción: comparar tablas, metadatos, contadores, eventos y conjuntos de recursos con resultados permitidos del escenario. Con escritores detenidos, leer desde otra conexión/JVM y reconstruir transiciones. No validar sólo que abrir no lanzó excepción ni usar la misma función de producción como única aserción.

### 9.2 Pruebas imprescindibles

| ID propuesto | Escenario | Aserciones y trazabilidad |
|---|---|---|
| H4-T01 | A cierra limpiamente, B abre; cinco estados, cero/varias herramientas, recurso fuera de servicio | Igual contenido funcional, catálogo, historial/IDs y asignaciones; secuencia siguiente mayor, sin reutilización; sin revisión nueva por cierre limpio. CA-16 |
| H4-T02 | Caída con sesión abierta y varias EN_CURSO; B y después C recuperan | Asignaciones/ocupación intactas, revisión durable idempotente, cinco estados, pendientes sin reservas; otros recursos libres permanecen elegibles. CA-17/INV-09 |
| H4-T03 | Revisión resuelta por finalizar/fallar y carrera de cierres con reasignación | Resolución, estado/liberación/evento atómicos; rollback conserva marca; doble cierre no libera nueva asignación. CA-17 con regresión CA-11/14 |
| H4-T04 | A mantiene guardia, B intenta abrir misma raíz y alias normalizable | B rechazado antes de conexión H2, sin cambios de sesión/negocio; tras cierre o muerte de A, nuevo proceso abre. PT-07/CA-25 parcial |
| H4-T05 | Aperturas simultáneas en JVM, aliases Windows admitidos, referencias antiguas y cierre concurrente | Propietario/coordinador único, cierre idempotente, ninguna escritura con guardia liberada; referencia antigua cerrada; rutas ajenas/enlaces no admitidos rechazados |
| H4-T06 | Caída antes de intención, después de intención y después de escrituras reales sin commit | Sin efecto funcional o transacción revertida, ID de intención cuando ya se conservó; ausencia acreditada sólo tras auditoría completa; sin contador parcial. CA-17/PT-06 |
| H4-T07 | Interrupción alrededor de commit y después de commit antes de respuesta | Cambio anterior o completo; ID/testigo/evento y asignación consistentes; confirmación reconciliada sin repetición; EN_CURSO revisadas por sesión interrumpida |
| H4-T08 | Commit que lanza antes/después del delegado real; rollback fallido; pérdida de conexión | PENDIENTE_VERIFICACION con mismo ID, llamadas posteriores sin transacción normal ni valor; recuperación obtiene confirmado/no confirmado o sigue bloqueada según evidencia |
| H4-T09 | Fallos de metadata: intención, resolución, diagnóstico, marca abierta/limpia y revisión | No iniciar negocio sin intención confirmada; no habilitar sesión/revisión no verificada; éxito de negocio conocido preservado; señal previa sobrevive si no puede guardarse diagnóstico nuevo |
| H4-T10 | Base inaccesible/ilegible, versión desconocida e incoherencias controladas | Bloqueo y diagnóstico, sin recreación/reparación automática; ID ausente en lectura fallida no resuelve; restaurar acceso permite sólo nueva auditoría |
| H4-T11 | Historial omitido/duplicado/incompatible, contador incorrecto, asignación incompleta o huérfana, revisión contradictoria | Auditoría detecta cada variante y no publica instantánea/propuesta confirmada; corruptar sólo fixtures con conexiones cerradas |
| H4-T12 | Lectura sana, rollback fallido, cierre incompleto y caída antes de guardar diagnóstico | Sana sin escrituras de intención/resolución/evento; fallo bloquea llamadas sin valor, ID técnico durable si pudo guardarse y sesión abierta como señal si no; no liberar guardia con conexión posiblemente activa; B audita antes de habilitar sin inventar ID/respuesta perdidos. Según decisión adicional 5.5 |
| H4-T13 | Nueva base y adopción v1; interrupción en cada paso de inicialización/migración autorizada | Datos H1/H2/H3 preservados, UUID estable, esquema final validado; estado intermedio bloqueado; no reseed de catálogo ni reemplazo de archivo |
| H4-T14 | Contexto auxiliar sano mientras otro queda incierto | Ninguna conexión/salud/marca cruzada; otro contexto progresa; propuestas ajenas rechazadas. INV-10 parcial, sin motor H6 |
| H4-T15 | Regresión H1/H2/H3 completa tras adaptar composición/cierre | Conservar casos de reglas, carreras, prioridad, exactitud, rollback y observador; ningún ajuste de aserción encubre pérdida de garantía |
| H4-T16 | Correspondencia de intento/testigo/resolución y varios intentos para misma orden/recurso | Igualdad literal `I` entre intención, resultado, evento y resolución; secuencia referenciada exacta y descriptor completo. IDs distintos entre intentos; rechazo/SIN_CAMBIO sin evento ni incremento funcional; eventos de recurso con orden nula. Colisión rechazada, testigo ajeno/marca sin evento/evento H4 sin intención bloquean. Verificar desde B tras caídas, sin inferencias por tiempo/orden |
| H4-T17 | Autorización denegada/ausente/abandonada, copia fallida y reanudación no acreditada | Ningún DDL, sesión o alteración del original sin autorización específica; bytes/huellas del original iguales antes/después de denegación. Copia completa inmutable anterior al cambio; fallos de copia abortan. Marcador perdido/incompatible, alteración funcional o permiso insuficiente bloquean; reanudación válida conserva UUID y datos |
| H4-T18 | Adopción sin GUI y matriz de salud 5.6 | Sin aceptación no hay operación normal; informe obsoleto rechazado; aceptación durable no elimina revisión EN_CURSO. Instancia ocupada sin H2, referencia cerrada sin JDBC, inconsistencia sin instantánea, CONFIRMADA con fallo posterior conservada y permisos coherentes con salud |

Parametrizar fronteras críticas al menos en registro, inicio con varias herramientas y cierre con liberación. Registro verifica antigüedad; inicio verifica completitud/ocupación; cierre verifica liberación y resolución de revisión. También cubrir recurso/habilitación/cancelación para testigo y reconciliación. Una prueba de sólo registro no basta para todos los invariantes.

**OD-H4-03 — adaptación de regresión verificable.** Las 71 pruebas previas son 17 H1 + 17 H2 + 37 H3, según cierre H3, no resultados H4. Conservar sus escenarios y aserciones de datos, estados, recursos, prioridad, rollback, historial, igualdad ID/evento, bloqueo sin aperturas nuevas y entrega poslock. `H3Fixture` y `TransaccionesH2Test.conFallo` deben decorar la unidad de negocio del mismo propietario; la fachada normal anterior no puede coexistir con una inyectada. En `CoordinadorConcurrenciaTest`, inyectar observador y controles durante la composición de test, sin construir otro servicio sobre el archivo.

Separar contadores y fallos por fase (infraestructura/negocio/recuperación): las aserciones actuales de una apertura o un rollback se mantienen exactas para la fase de negocio; añadir aserciones exactas de la infraestructura prevista, sin sustituir igualdad por «al menos una». COMMIT_ANTES/DESPUES y pausas tras escritura real siguen interceptando el commit/escritura de negocio, no la intención previa. Bloqueo posterior debe producir cero aperturas nuevas en todas las fases normales. Cambiar expectativas de ruta String al UUID obtenido del propietario conserva igualdad exacta; añadir estabilidad/diferencia de identidad. Cerrar todos los propietarios en limpieza y conservar raíces exclusivas. No eliminar casos ni suprimir inyecciones para lograr regresión. Una prueba que falle exige determinar pérdida de garantía o cambio contractual expresamente documentado; no relajar aserciones por conveniencia.

### 9.3 Complementarias y límites

Complementarias: repetición probabilística de ventanas reales de commit, variantes de alias/enlaces no admitidos, degradación prolongada de disco y caracterización de latencia añadida por intenciones, carga del núcleo con 100 órdenes. No sustituyen imprescindibles ni escenarios H6. Si revelan un defecto de integridad, debe resolverse antes del cierre aunque sean complementarias.

Proponer diez segundos por espera controlada, presupuesto mayor explícito para inicio de JVM/migración y presupuesto global de suite, ajustables al Windows real. Registrar parámetros y repeticiones; no usarlos como objetivos de rendimiento. Timeout falla ensayo y conserva diagnóstico: PID, señales, fase, operación, hilos, excepciones y ruta. No borrar bases ni matar procesos ajenos para hacer pasar una prueba.

Evidencia futura: comando exacto, código de salida, JDK/Maven/H2/Windows efectivos, reportes Surefire, parámetros, PIDs distintos, cronología de fronteras, IDs/intenciones, snapshots antes/después y consultas independientes. Conservar logs anteriores, emitir nuevos en UTF-8 y separar diagnóstico de recuperación, historial funcional y métricas académicas. No fijar cantidad total futura de pruebas antes de implementarlas.

Ejecutar posteriormente pruebas H4 dirigidas y regresión `mvn.cmd -B -ntp clean test` en entorno autorizado con dependencias disponibles. Si reaparece H2 90097-232 u otra restricción interna, informar qué ejecución falló y solicitar evidencia externa cuando corresponda; no inferir validación desde compilación o éxitos históricos. No descargar dependencias ni alterar permisos/configuraciones globales automáticamente.

## 10. Secuencia posterior y criterio de salida

1. Revisión y aprobación humana del diseño v0.2 y decisión adicional sobre consultas de 5.5, conservando H4-D01 a H4-D05 ya ratificadas. Aprobación del diseño y autorización de implementación son actos separados; preparar prompt posterior con alcance H4 y rutas aprobadas. Permiso de migrar cada base operativa es otro acto específico.
2. En ejecución posterior autorizada, registrar línea base H1/H2/H3 y entorno. Conservar cualquier fallo y distinguir causa previa de regresión H4.
3. Implementar guardia, ruta estable, propietario y cierre; verificar instancia única y carreras apertura/cierre sin publicar base desprotegida.
4. Añadir esquema nuevo e inicialización/migración autorizada; probar interrupciones de infraestructura sobre fixtures v1/nuevos antes de adoptar datos operativos.
5. Implementar auditoría estructural/funcional, informes y bloqueo de arranque; verificar escenarios coherentes e inconsistentes.
6. Incorporar intención durable mutante y correspondencia exacta de testigo/resolución; integrar consultas según decisión humana sobre 5.5, fallos de metadatos y conexiones dudosas. Comprobar primero fallos instrumentados, luego JVM independientes y caídas reales.
7. Incorporar sesión y revisión asociada, integración transaccional con cierres existentes y exposición inmutable; comprobar reinicios sucesivos y cierres contradictorios.
8. Ejecutar matriz imprescindible y regresión, recoger evidencia multiproceso y límites; auditoría y aprobación humana de H4. Transferir pendiente textual residual a validación normativa y continuidad de UI/simulador a H5/H6, sin iniciarlos.

Cierre futuro exige CA-16/17 y guardia de CA-25 parcial con evidencia real, reconciliación sin duplicaciones, bloqueo ante inconsistencia, revisión EN_CURSO conservada/resuelta por transiciones existentes, regresión aprobada y ninguna incompatibilidad material sin decisión humana. No declarar aplicación operativa completa, H7 ni P04 aprobados sólo por H4.

## 11. Procedencia y límites de esta instancia

La propuesta v0.1 registra inspección local del prompt H4 íntegro, AGENTS, SPEC y PLAN; fuentes de dominio, planificación, aplicación, puertos y persistencia; esquema y POM; pruebas H1/H2/H3 y fixtures; cierres H1/H2/H3 y fase 2, auditorías pertinentes y aprobación/diseño H3, junto con estrategia SDD por hitos. Se conserva esa procedencia histórica; las lecturas efectuadas en R04 se precisan a continuación. Las conclusiones de diagnóstico son estáticas y los resultados históricos se atribuyen a sus cierres/auditorías.

Registro histórico: P03-D-H4 v0.1 → IMPLEM-H4 v0.1, 13/10/2026. Refinamiento: R04-H4 v0.1 → IMPLEM-H4 v0.2, 14/10/2026, por instrucción explícita del usuario. Se leyeron íntegramente R04, el documento de partida, P03-D-H4, AGENTS, SPEC y PLAN y se contrastaron las precisiones con ServicioTaller, PersistenciaH2, UnidadTrabajoJdbc, RepositorioTallerJdbc, esquema, contratos, fixtures y referencias de pruebas, auditorías y cierres H2/H3. Las inspecciones son estáticas.

R04 aporta las ratificaciones humanas H4-D01 a H4-D05, con condición específica de D03. Los detalles de v0.2 son propuestas para nueva auditoría: OD-H4-01 (5.1–5.3, T16), OD-H4-02 (7.1, T13/T17/T18), OD-H4-03 (4.1–4.2 y regresión 9.2), OD-H4-04 (5.5, T12) y OD-H4-05 (5.6, T18). Se conserva alcance, diagnóstico, trazabilidad, auditoría, riesgos y matriz anterior con ajustes pertinentes. La alternativa de consultas queda pendiente de aprobación adicional; diseño completo, programación y migraciones reales no están autorizados. No se registran métricas nuevas ni se asignan números globales de DECISIONES.

Único archivo modificado en este refinamiento: `IMPLEM-H4.md`, de v0.1 a v0.2. No se implementaron código, SQL, pruebas o configuraciones; no se modificaron otros documentos. No se ejecutaron compilaciones, Maven/JUnit, migraciones, caídas, pruebas de implementación ni verificaciones experimentales de durabilidad, ni se operó sobre bases reales. No se realizaron commits, cambios de rama, conexiones remotas ni operaciones externas. Esta propuesta no valida una implementación H4 ni acredita las garantías que propone verificar; los 71 tests son antecedentes H1/H2/H3.

La ejecución termina con este documento. Su aprobación y cualquier implementación requieren autorización humana posterior y separada.
