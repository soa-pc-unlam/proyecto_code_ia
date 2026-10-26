# ReparaBarrio — Diseño técnico H6

| Control documental | Valor |
|---|---|
| Versión | **0.1** |
| Fecha | **15/10/2026** |
| Estado | **PROPUESTA — PENDIENTE DE APROBACIÓN HUMANA** |
| Fase | SDD, P03: diseño técnico previo a implementación |
| Origen | `prompts/anexos/P03-D-H6.md` v0.1 y solicitud humana de generar exclusivamente este documento |
| Normativa | `AGENTS.md`; `SPEC.md` v0.2, D016 y §14; `PLAN.md` v0.2, PT-01…PT-08 y §8.7 |
| Autorización actual | Sólo documentación. Ninguna implementación, ejecución de escenarios ni operación Git |

## 1. Alcance, precedencia y línea base

H6 incorpora generación de entrada reproducible, ejecución concurrente ficticia mediante el núcleo real, contexto independiente, terminación informada, estadísticas funcionales y la pestaña Swing de simulación. Se conserva un único proyecto Maven, Java 21, H2/JDBC, JUnit y las reglas aprobadas. La interfaz del simulador pertenece a H6 según PLAN v0.2. H7 conserva empaquetado JAR, aceptación global e integración final; H8 conserva informe, rúbricas y mediciones académicas. La referencia de 100 órdenes se verifica funcionalmente en H6 y se integra a la aceptación global H7.

SPEC define producto; PLAN define arquitectura; las decisiones humanas posteriores delimitan la continuidad académica. Las menciones históricas a aprobación pendiente no sustituyen los cierres posteriores. `DECISIONES.md` conserva propuestas antiguas: las PT ratificadas de PLAN prevalecen en los temas ya resueltos.

H1–H3 están aceptados para sus alcances. H4 permanece **parcial**, con H4-T02/T03/T09 abiertos e intermitencia histórica H3-T05. El recorrido nominal y H5 tienen validación externa académica según `docs/H5-VALIDACION-EXTERNA-Y-CIERRE-ACADEMICO.md`: cinco pruebas focales sin fallos ni errores y recorrido humano Windows. No equivalen a regresión completa ni a recuperación avanzada aprobada. La referencia al commit `75d98bb` procede del prompt; no se verificó con Git.

El error H2 `90097-232` ocurrió en el entorno restringido y no reapareció en la selección externa H5. No se demuestra una causa universal ni se lo declara resuelto. H6 conservará intenciones, integridad, auditoría, capacidad y bloqueo. No reparará H4 ni usará memoria para presentar como validada su recuperación de archivo.

Quedan excluidos pausa/reanudación, salteos, prioridades alternativas, reservas anticipadas, editor de catálogo, red, recuperación automática de resultados inciertos y nuevos estados de orden. Sólo existen PENDIENTE, EN_CURSO, FINALIZADA, FALLIDA y CANCELADA. Los estados técnicos del escenario y sus causas no se persisten como estados de orden.

### 1.1 Trazabilidad de aceptación

| Requisitos / criterios | Componentes reales y propuestos | Escenario / pruebas futuras |
|---|---|---|
| RF-16, PA-10, CA-20; RNF-06 | ConfiguracionEscenario, GeneradorCarga, CargaEscenario | Igualdad de entrada completa; H6-T01 |
| RF-17, CA-21; RNF-03/08; INV-01/02/03/05/08 | MotorSimulacion → ServicioTaller → coordinador/JDBC reales | Solicitudes solapadas, trabajos independientes, herramienta única; T02/T03/T05 |
| RF-05/06/07, RN-02/08, INV-07, CA-19/24 | consultar, planificacion.propuesta, confirmarInicio exacta | Primera factible, rechazo y nueva consulta; T04/T12 |
| RF-18, PA-11, CA-22 | Hechos temporales confirmados, CalculadorEstadisticas | Poblaciones, intervalos, N/D, 100 órdenes; T06/T08/T11 |
| RF-19, INV-10, CA-23 | FabricaContextos, Almacen/ContextoTaller aislados | IDs coincidentes, propuestas cruzadas, escenarios sucesivos; T07 |
| RN-09, RNF-07, CA-24 | Misma fachada, dominio y Planificador | Fixtures equivalentes humano/automático; T12 |
| RF-15, RNF-05, CA-18/22 | PanelSimulacion y PresentadorSimulacion | Navegación, progreso, cinco contadores; T08/T09 |
| PT-08, CA-26 | ExecutorService, ScheduledExecutorService, separación de esperas | Saturación, cierre y trazas; T02/T05/T09 |
| PT-06, PLAN §8.7; límites RF-13/14 | ResultadoOperacion, ciclo e informe existentes | Suspensión segura e incertidumbre sin reintento; T11, regresión T10 |

## 2. Contratos observados y dependencias pendientes

Rutas Java relativas a `src/main/java/ar/edu/unlam/reparabarrio/`. Los nombres sin implementación actual se identifican como **propuestos**.

| Existente | Contrato observado y consecuencia |
|---|---|
| `aplicacion/ServicioTaller.java` | Clase final, constructor de paquete. registrarOrden(String,String,Collection<TipoHerramienta>), registrarRecurso(Categoria,String,TipoHerramienta,boolean), consultar(), confirmarInicio(PropuestaInicio), finalizar(String), fallar(String), cancelar(String), cambiarHabilitacion(Clave,boolean). Devuelven ResultadoOperacion; no hay consultarPropuesta separado |
| `InstantaneaTaller` | revision, dominio, catalogo, historial, planificacion, revisionRecuperacion, revisiones; propuesta en `planificacion().propuesta()` y causas en `causas()` |
| `Planificador` / dominio | Primera pendiente factible por secuencia; unidades mínimas naturales al proponer, exactas al validar; contexto ajeno rechazado. Consultar no reserva. InstantaneaDominio valida completitud/exclusividad |
| `EventoHistorial` | Secuencia, operación ID, orden ID, acción, Instant y recursos. INICIO guarda asignación; FINALIZADA/FALLIDA guardan liberación. No contiene tiempo monotónico |
| `ResultadoOperacion` | CONFIRMADA, RECHAZADA, SIN_CAMBIO, ERROR_TECNICO, PENDIENTE_VERIFICACION; diagnóstico técnico y salud. CONFIRMADA con aviso posterior sigue siendo confirmación conocida |
| `ContextoTaller` | componer(Almacen,Consumer), identidad, ciclo, servicio, recuperacion, informe, close. Ciclos ABRIENDO/HABILITADO/BLOQUEADO/CERRANDO/CERRADO; capacidad depende de `almacen.guardia().vigente()`. componer invoca recuperación interna; no omitirla |
| `FabricaContextos` | abrir usa datos/operativo; crearAcademico sólo nombres h5-* nuevos; mapa único por directorio real; composición privada. Ninguna entrada pública H6/memoria |
| `PersistenciaH2.Almacen` | Constructor privado; URL privada; sólo emite unidades al propietario con capacidad vigente; preparar exige GuardiaInstancia y Solicitud. No admite hoy memoria por API soportada |
| `InicializadorBase` / `AutorizacionBase` | URL de archivo; permiso por ruta, huella, origen/destino, recuperación física y declaración. Verifica esquema v2 y marcador. No aceptar URL aportada por GUI ni simular autorización de archivo para memoria |
| `UnidadTrabajoJdbc` | Conexión privada por operación, SERIALIZABLE y autocommit false; mismos repositorios para negocio e infraestructura. No necesita reemplazar SQL para ejecutar negocio sobre H2 memoria |
| `ServicioTaller.ejecutar` | Coordinador justo → intención técnica → negocio/commit → cierre → unlock. Observador H3 de paquete entrega sólo tiempos de lock después del unlock y puede perder muestras: insuficiente para RF-18 |
| `interfaz` / arranque | PanelSimulacion es marcador; VentanaPrincipal lo construye directamente. PresentadorTaller posee cierre operativo y publica cerrado a vista; TrabajosSwing es FIFO de un hilo. ReparaBarrioApp compone H5 académico y debe incorporar el propietario H6 |

**Impedimento de composición identificado:** hoy no puede crearse un contexto de simulación en memoria mediante los contratos públicos sin adaptar arranque/persistencia/cierre. Los fixtures que usan reflexión no son una vía de producción. Se documenta H6-D01 para aprobación; no se sustituye silenciosamente por archivo ni se implementa la adaptación. El diseño posterior es condicional a esa decisión.

**Dependencia temporal identificada:** Clock.systemUTC está fijado en la composición y el historial carece de marcas monotónicas. El observador H3 no ofrece hechos confirmados de negocio. H6-D02 propone una ampliación aditiva para intervalos verificables. No existe actualmente el puerto Reloj sugerido por PLAN ni un flujo de eventos funcionales con esa precisión.

## 3. Arquitectura, composición y aislamiento propuestos

Dependencias: interfaz → simulacion → aplicación/metricas; aplicación → dominio/planificación/puertos; JDBC → puertos; arranque compone. El motor recibe una capacidad de escenario ya compuesta con su fachada, identidad y lectura de hechos, nunca la fachada operativa, repositorios, conexión ni selector global de base.

| Componente propuesto | Responsabilidad y ciclo |
|---|---|
| ConfiguracionEscenario / CargaEscenario | Valores inmutables validados; entrada completa generada antes de abrir ejecución |
| GeneradorCarga | Consumo secuencial de semilla, sin acceso a taller ni Swing |
| SesionSimulacion, compuesta en arranque | Propietaria exclusiva del contexto, ejecutores y captura final; descarta referencias al cerrar |
| MotorSimulacion | Registro de tareas/eventos, solicitudes automáticas por fachada, deduplicación, progreso y fin |
| Temporización interna del motor | ScheduledExecutorService para llegadas y avisos; espera de reparación en trabajadores fuera de locks/JDBC |
| HechosConfirmados / reloj de intervalos | Registro append-only por contexto de hechos confirmados; no decide disponibilidad |
| CalculadorEstadisticas / EstadisticasEscenario | Derivación pura desde hechos y captura coherentes, con revisión/período/unidades |
| PresentadorSimulacion / VistaSimulacion | Interacción en EDT, ejecución externa, agrupación de refrescos y generación |

### 3.1 Recomendación H6-D01: H2 en memoria con propietario protegido

Nueva entrada acotada `FabricaContextos.crearSimulacion` (propuesta, firma por definir al aprobar): recibe raíz validada y consentimiento de nueva ejecución descartable, sin aceptar URL/ruta de base externa. Genera nombre interno `rb_h6_<UUID>` y directorio exclusivo nuevo `target/h6-<UUID>` sólo para guardia/diagnóstico; no crea allí un archivo de negocio H2. Conserva validaciones de pertenencia y enlaces equivalentes a H5 y usa el mismo registro de propietarios por directorio real, sin segundo mapa mutable de fachadas.

La guardia de archivo se mantiene vigente durante toda la vida, incluso para memoria: evita relajar `Capacidad.vigente` o introducir propietarios sin guardia. Se emite un permiso específico de **creación de memoria** con ID, nombre interno, directorio de guardia y declaración de descarte, separado de AutorizacionBase de archivo. El clic Iniciar, tras mostrar el alcance, autoriza sólo esa nueva ejecución; no concede permisos sobre bases existentes ni modifica las autorizaciones H5.

PersistenciaH2 tendría una entrada interna para ese permiso. Construye únicamente URL `jdbc:h2:mem:<nombre-interno>` con parámetros internos equivalentes de bloqueo/escritura y sin DB_CLOSE_DELAY=-1. Abre una conexión ancla privada de vida, que no se usa para transacciones de negocio ni se comparte con trabajadores. Inicializa nuevo esquema v2, marcador, identidad UUID y catálogo MULTIMETRO/SOLDADOR/TALADRO reutilizando la lógica de creación extraída de InicializadorBase; audita estructura y agregado con los mismos contratos. No copia datos ni SQL de fixtures, no ejecuta migraciones, no adopta ni reanuda memoria previa. Ante inicialización parcial, cerrar y diagnosticar; otra ejecución usa otro nombre.

Almacen conserva guardia y capacidad y añade propiedad/cierre verificable del ancla. Las unidades siguen privadas por llamada mediante UnidadTrabajoJdbc. ContextoTaller.componer sigue reclamando capacidad e invocando el arranque/auditoría existentes antes de habilitar. Sólo se habilita si ciclo HABILITADO, informe válido y primera consulta confirmada. Una apertura bloqueada no se presenta como escenario vacío válido.

Orden de cierre ampliado: detener/drenar ejecutores → cerrar unidades residuales y sesión mediante contexto → cerrar/verificar ancla → liberar guardia → CERRADO → retirar propietario fuera del lock. La liberación actual de guardia en ContextoTaller debe delegar antes el cierre del ancla; no basta cerrarla después desde la GUI. Si el ancla no termina, retener propietario y guardia, informar cierre no verificado. No borrar directorios/lock ni bases anteriores automáticamente. Sin conexiones, la memoria se descarta; no promete conservación tras caída ni reapertura.

La diferencia con H5 es explícita: H5 crea archivo nuevo target/h5-* con AutorizacionBase por ruta/huella y conserva su evidencia; H6 propone memoria nueva y ancla, permiso específico y descarte al cerrar. No se llama abrir/abrirConInforme operativos ni crearAcademico para simular ese contrato. Esta adaptación necesita aprobación expresa por afectar la frontera protegida, aunque conserva negocio, SQL, auditoría e integridad.

## 4. Configuración y generación determinista

Configuración propuesta: versión de generador `H6-G1`, semilla long decimal, N órdenes entero ≥0 (valor de referencia 100), W trabajadores entero ≥1 (inicial 4, sin máximo funcional de cuatro), cantidades no negativas de técnicos/puestos/unidades por los tres tipos, habilitación inicial por unidad, mezcla de requisitos, patrón de llegadas, intervalos/duraciones y probabilidad de fallo ficticio. Cero recursos es válido para escenarios sin progreso; no crear unidades implícitas. W=1 es válido pero no acredita solapamiento de trabajos; los ensayos concurrentes requieren W≥2.

Mezcla: pesos enteros no negativos para los ocho subconjuntos posibles de los tres tipos, suma positiva. Cada requisito contiene cero/una unidad por tipo; un tipo reconocido sin unidades produce carga imposible válida. Llegadas: simultáneas (offset cero), periódicas (offset i×intervalo), o intervalos aleatorios enteros dentro de rango inclusivo. Duración ficticia por orden en milisegundos enteros, fija o uniforme inclusiva, mínimo 0. Fallos: entero en partes por diez mil de 0 a 10000, precomputado por orden. Sin cancelaciones automáticas ni edición de catálogo.

Validar rangos, IDs y longitudes admitidas por dominio/esquema, suma de pesos, productos/sumas de offsets, conversión de milisegundos a nanosegundos y capacidad de colecciones sin overflow. Rechazar antes de crear contexto si no es representable; informar capacidad de infraestructura insuficiente sin inventar un límite funcional arbitrario. La GUI ofrece configuraciones explícitas, no una entrada JDBC. Eventos futuros de habilitación no son necesarios en la primera versión de controles; el algoritmo de fin debe contabilizarlos si se aprueban posteriormente. Los tests focales de estadísticas sí admiten hechos de fuera de servicio parcial.

Generador secuencial con `java.util.Random`, orden de consumo documentado para H6-G1: por índice generar intervalo de llegada cuando corresponda, subconjunto por pesos, duración y resultado. Toda corrida usa exactamente ese orden, incluso valores fijos con una regla de consumo definida por versión. Materializar listas inmutables de recursos y órdenes (ID, descripción, requisitos ordenados canónicamente, offset, duración y resultado). IDs sintéticos T1…/P1…/M1…/S1…/D1…/O1… válidos; no codifican prioridad.

Igualdad reproducible compara todos esos campos y configuración normalizada, excluyendo UUID de ejecución/base, sesiones, marcas reales y secuencia asignada durante registros concurrentes. La prioridad efectiva será la secuencia real del núcleo, aunque llegadas empatadas se confirmen en distinto orden. Los offsets son fechas previstas relativas al comienzo, no promesas de puntualidad. Ningún trabajador consume aleatoriedad. Cambiar algoritmo futuro exige versión de carga nueva; conservar configuración, versión y entrada completa en evidencia autorizada posterior.

Referencias propuestas, **sin resultados ejecutados**: semilla 20261015, N=100, W=4, llegadas periódicas cada 10 ms, duraciones uniformes 200…400 ms, fallo 1000/10000, pesos 1 para cada subconjunto. Perfil suficiente: 4 técnicos/4 puestos y 4 unidades por tipo habilitadas. Limitado: 2 técnicos/2 puestos, M1/S1 habilitados, cero TALADRO; genera contención e imposibles. Son parámetros de ensayo, no métricas ni objetivos de rendimiento.

## 5. Motor concurrente y primera factible

Se propone un pool de **W trabajadores de reparación** que realiza el inicio, duración ficticia y cierre; un ExecutorService auxiliar de dos hilos para registros/consultas de control; un ScheduledExecutorService para publicar vencimientos. Estos últimos no cuentan como técnicos ni trabajadores de reparación. La separación evita que W reparaciones durmiendo impidan registrar llegadas, consultar o cerrar desde su propio trabajador. El scheduler no hace JDBC ni espera futures: entrega eventos al control. No se añade dependencia.

El registro interno del motor protege con un monitor breve: generación, fase, llegadas futuras/entregadas/en cola, operaciones admitidas, solicitudes en vuelo por orden, trabajadores activos, permisos W y versión de eventos relevantes. Nunca mantenerlo al llamar a fachada, consultar informe, publicar Swing o esperar. No hay locks de recursos; el único lock del taller sigue siendo su coordinador. Permisos son capacidad de infraestructura, no reservas de técnico/puesto/herramienta.

Secuencia propuesta:

1. Generar/validar carga, crear contexto sano, registrar recursos por fachada y comprobar cada resultado. Preparar observación temporal; al finalizar preparación fijar t0 y comenzar programación de llegadas. Recursos existen antes de t0.
2. Al vencer llegada, transferir atómicamente su ficha de futura a cola admitida antes de decrementar futuros. El control registra por registrarOrden; sólo CONFIRMADA cuenta como llegada registrada. Conservar resultado, hilo y tiempos del intento. Registros simultáneos pueden solaparse esperando el coordinador.
3. Cada registro/inicio/cierre confirmado y liberación de capacidad marca nueva versión relevante y solicita consulta agrupada. Puede haber hasta dos consultas de control en vuelo; agrupar ráfagas mediante bit pendiente. Sin sondeo JDBC periódico del motor.
4. De consulta confirmada tomar la propuesta real. Si existe capacidad libre, reclamar atómicamente ficha por `(generación,ordenId)` y permiso W antes de enviar al trabajador. Dos consultas de la misma orden no generan dos trabajos. No decidir prioridad desde la tabla ni generar propuesta de otra orden.
5. El trabajador llama confirmarInicio con el mismo valor exacto. RECHAZADA libera ficha/permiso; nueva consulta sólo si hubo versión relevante o para verificar una revisión distinta de la observada. Si se rechaza repetidamente en el mismo estado sin cambio explicativo, informar anomalía técnica y detener; no reintentar en bucle. Nunca cambiar unidades ni caducar por revisión global.
6. CONFIRMADA registra el hecho de inicio y habilita consulta nueva para que otra pendiente comience con capacidad independiente. En el mismo trabajador empieza la duración ficticia después del retorno confirmado. Espera hasta su deadline monotónico fuera del coordinador, de JDBC y del monitor del motor. No espera recursos. Registrar intervalo de trabajo y hilo; la espera representa reparación en curso.
7. Al vencer, el mismo trabajador envía exactamente una llamada finalizar o fallar según resultado precomputado. Ficha de cierre se reclama una vez; conserva EN_CURSO hasta cierre confirmado. Registrar resultado antes de liberar ficha/permiso. Cierre confirmado dispara nueva consulta. Un callback tardío no repite cierre.
8. Cuando se agota capacidad, las propuestas quedan sin reserva y sin trabajadores esperando recursos; al liberar permiso se consulta nuevamente. No confirmar para luego dejar una reparación sin trabajador disponible.

No se espera que dos órdenes diferentes reciban propuestas válidas simultáneas: dos consultas pueden ver la misma primera factible. La nueva propuesta después de cada inicio permite superponer trabajos, aun cuando las transacciones se serializan. Solicitudes concurrentes verificables incluyen registros/consultas/inicios/cierres en hilos distintos; no basta una animación ni dos contadores. Los ensayos conservarán intervalos de llamadas, además de los de reparación.

Si falla el envío al pool, no confirmar inicio; revertir sólo la ficha local. Si ya hubo inicio y falla temporización/tarea, no perder silenciosamente la obligación de cierre. Supervisar futures y excepciones. Conforme a PLAN §6, una excepción de tarea conocida con contexto coherente permite **un** intento de fallar por fachada, rotulado cierre por incidente técnico (no fallo ficticio configurado). Si no se acredita coherencia, o el resultado es incierto, detener escenario con asignación intacta y diagnóstico; no convertir la excepción en un éxito supuesto.

## 6. Terminación, progreso e incertidumbre

Fases técnicas propuestas: preparación, ejecución, drenaje, terminada, detenida por error y cerrando/cerrada. Motivo terminal separado: completada, pendientes sin progreso, error técnico o salida solicitada. Nunca son valores de EstadoOrden. Ausencia momentánea de propuesta durante EN_CURSO es espera normal.

Para decidir fin, el control captura bajo su monitor contadores y versión v, libera monitor, obtiene consulta confirmada y salud del contexto, y vuelve a verificar **misma v** y ausencia de toda operación/evento admitido. La propia consulta de terminación tiene ficha distinguida para no contarse a sí misma. Si cambió v descarta decisión y vuelve por el evento pendiente. El motor es el único cliente mutante de su contexto; presentación sólo consulta.

Condición común de quiescencia: cero llegadas futuras, cero entregas/colas aún no registradas, cero solicitudes/confirmaciones/cierres y tareas en vuelo, cero trabajadores con obligación de cierre, cero temporizaciones o acciones futuras útiles, y captura confirmada con cero EN_CURSO. Un evento que vence está contabilizado antes de retirarse del scheduler; no hay ventana de contador cero con entrega aún invisible.

- Fin normal: quiescencia y ninguna PENDIENTE. N=0 también termina tras captura vacía, con duración observada.
- Fin sin progreso: quiescencia, PENDIENTE>0 y ninguna propuesta factible. Congelar tF, causas completas por ID de la captura final y contadores. Las pendientes siguen PENDIENTE y sin asignaciones; informar «escenario terminado con pendientes sin posibilidad de progreso».
- Quiescencia con propuesta factible: debe despacharse; si no hay permiso pero tampoco trabajadores, es defecto de contabilidad, no bloqueo del negocio.
- EN_CURSO sin trabajador/obligación de cierre o scheduler perdido: error técnico, nunca fin por falta de recursos.

Argumento de terminación nominal: la carga es finita; cada llegada se entrega una vez; cada orden admite como máximo un inicio y un cierre efectivo; cada duración es finita; rechazos sólo generan trabajo tras cambios relevantes finitos. Con ejecutores y JDBC que progresan, se consumen las llegadas y cierres, y la comprobación final termina o informa pendientes imposibles. No demuestra una cota temporal cuando SO/JDBC no retornan. Los timeouts de aceptación fallan el ensayo con pilas/fichas, sin reclasificarlos como bloqueo de pendientes.

| Situación | Tratamiento |
|---|---|
| RECHAZADA de negocio | Cero efecto funcional de ese intento; nueva consulta por cambio pertinente, sin contar como FALLIDA |
| SIN_CAMBIO | No sumar hecho ni progreso ficticio; inesperado para inicio/cierre automático merece diagnóstico |
| FALLIDA ficticia confirmada | Terminal de negocio normal, libera recursos y suma procesadas |
| ERROR_TECNICO | Detener admisión y supervisar obligaciones conocidas; no suponer rollback sólo por nombre |
| Contexto BLOQUEADO | Detener mutaciones, temporizadores de nuevas acciones y consultas automáticas; conservar diagnóstico/captura anterior |
| PENDIENTE_VERIFICACION o excepción sin resultado | No repetir mutación, no finalizar/fallar para compensar, no recuperar automáticamente; datos actuales N/D y último confirmado histórico |
| CONFIRMADA con aviso posterior | Conservar éxito y hecho conocido; si salud quedó bloqueada, detener. El aviso no revierte el negocio |

Las operaciones admitidas en carrera al bloqueo recibirán los controles existentes de fachada. Detener no significa interrumpir JDBC ni liberar recursos manualmente. La recuperación interna de composición se conserva; el motor nunca invoca recuperar/aceptarAdopcion para reparar una corrida.

## 7. Estadísticas derivadas de hechos confirmados

### 7.1 Adaptación temporal H6-D02

Proponer puerto pequeño de tiempo monotónico inyectable, implementado normalmente por System.nanoTime, y Clock para fecha/hora. Inyectar ambos al componer el contexto; hoy sólo existe Clock en el constructor interno del servicio. El hecho temporal propuesto contiene contexto, secuencia/revisión funcional, operación ID, acción, entidad, recursos exactos y marca monotónica. Capturar marca inmediatamente después del commit de negocio que retornó correctamente, **dentro de la operación serializada**; añadir sólo hechos de cambios confirmados. Los intervalos describen cambios conocidos tras retorno de commit, no el instante físico interno de H2 ni tiempo CPU. La precisión efectiva depende del reloj/SO, aunque se almacenen nanosegundos.

Acumular en una bitácora interna append-only por contexto y operación, sin callbacks externos, I/O ni esperas bajo coordinador. Lectura inmutable de hechos junto a revisión de captura en una consulta aditiva de simulación, bajo el mismo coordinador. No modificar el esquema ni reemplazar historial; cada hecho debe corresponder a su evento confirmado. El cálculo no autoriza negocio. Validar continuidad y correspondencia antes de presentar métricas vigentes. La bitácora no usa colas descartables del observador H3.

Si falla el registro temporal después de commit, conservar CONFIRMADA y advertir estadísticas incompletas; detener escenario técnicamente, sin repetir negocio ni inventar intervalos. Si el commit es incierto no añadir hecho confirmado ni reconstruirlo desde retornos de otros hilos. En esa corrida mostrar sólo últimos hechos conocidos como parciales/históricos; N/D donde falte evidencia. La memoria del escenario no promete reconstrucción temporal tras caída. Operativo puede mantener la observación funcional desactivada sin cambiar sus resultados ni contratos existentes.

Esta ampliación propuesta afecta composición y servicio: requiere aprobación H6-D02. Usar marcas al retorno del adaptador evita modificar núcleo, pero puede invertir orden entre hilos e incluir demora de cierre/publicación; no satisface la precisión serializada de PLAN §8.2. Usar sólo Instant persistido tampoco soporta ajustes del reloj. No se adopta ninguna de esas alternativas como si fuera equivalente.

### 7.2 Período, poblaciones y fórmulas

t0: comienzo de simulación después de recursos y antes de programar llegadas. tF: decisión de terminación, salida o detención técnica, después de drenar operaciones admitidas que puedan aportar hechos conocidos. Durante ejecución t es una captura monotónica coherente. Intervalos semiabiertos recortados a `[t0,t)`; al terminar `[t0,tF)`. Fecha/hora de comienzo/fin se muestra con zona para lectura humana; diferencias usan sólo reloj monotónico. Internamente nanosegundos; UI expresa ms y segundos con precisión de presentación explícita. Calcular antes de redondear, nunca sumar porcentajes redondeados.

| Indicador | Definición y casos límite |
|---|---|
| Cinco contadores | Cantidades de PENDIENTE, EN_CURSO, FINALIZADA, FALLIDA, CANCELADA en una única captura confirmada. Siempre separados; filtro de tabla no cambia población |
| Procesadas | FINALIZADA + FALLIDA. Excluye CANCELADA, PENDIENTE y EN_CURSO |
| Espera por iniciada i | inicio confirmado i − registro confirmado i, en ms; ambos del mismo contexto/reloj. Incluye espera de recursos, capacidad de infraestructura y transacciones hasta el inicio conocido |
| Espera promedio | suma de esperas / número de órdenes que tuvieron INICIO confirmado, incluyendo las aún EN_CURSO o luego fallidas. N/D si no hay inicios; no asignar esperas a pendientes/canceladas |
| Duración | tF − t0 en s (o t − t0 mientras corre); excluye preparación/generación y posterior descarte de contexto |
| Habilitado H(r) | Longitud de unión de intervalos en que recurso r existe y está habilitado dentro del período. OCUPADO también está habilitado; excluir FUERA_DE_SERVICIO y tiempo anterior a alta |
| Ocupado O(r) | Suma de intervalos INICIO → FINALIZADA/FALLIDA confirmados que incluyen r, recortados al período; intervalo abierto hasta t en captura parcial |
| Utilización por unidad | 100 × O(r)/H(r); N/D si H(r)=0, incluso en escenario de duración cero. Verificar 0≤O≤H; si falla, error de evidencia, sin truncar a 100 para ocultarlo |
| Bloqueadas informativas | Cantidad/IDs de pendientes con causas; distinguir falta de unidad de prioridad. En fin sin progreso enumerar todas las pendientes imposibles. No sumar como sexto estado |

Con recursos precargados, su habilitación inicial se conoce desde configuración confirmada y vale a t0; el historial REGISTRO_RECURSO no lleva ese booleano. La bitácora temporal debe asociar el hecho confirmado con la habilitación efectiva del alta y registrar cambios posteriores confirmados. No inferirlo desde el último refresco. Los tests incluirán fuera de servicio parcial aunque la UI inicial sólo configure habilitación de partida. Canceladas normalmente serán cero en ejecución automática; el calculador soporta hechos válidos de cancelación para comprobar población sin añadir un control automático nuevo.

El intervalo de trabajo ficticio es distinto de ocupación: comienza tras retorno de inicio y termina antes de solicitar cierre; ocupación puede incluir demoras de despacho/cierre. No usar duración configurada como ocupación real. La observación de solicitudes y de lock es diagnóstico de concurrencia; CPU, RAM, tiempos experimentales y tokens académicos pertenecen a H8 y no sustituyen RF-18.

## 8. Swing y propiedad de ciclo de vida

Propuesta visual, sin códigos técnicos extensos como contenido principal:

```text
Simulación — datos ficticios independientes | H4 parcial
Semilla [20261015]  Órdenes [100]  Trabajadores [4]
Técnicos [ ] Puestos [ ]  MULTIMETRO [ ] SOLDADOR [ ] TALADRO [ ]
Habilitación inicial / mezcla de requisitos / llegadas / duraciones / fallos
[Iniciar escenario nuevo]   Estado y avisos de descarte
Llegadas registradas / previstas | solicitudes activas | trabajos en curso
PENDIENTE | EN_CURSO | FINALIZADA | FALLIDA | CANCELADA
Procesadas | espera promedio (ms) | duración (s) | período observado
Órdenes y causas    Recursos y utilización (%)    Historial / resultados
Detalle técnico desplegable | última captura y vigencia
```

No incluir botón de pausa/reanudación, salto ni cierre manual de órdenes de simulación. Nueva ejecución tras terminar cierra primero contexto anterior y conserva resultado final inmutable; crea nuevos motor, bitácora y ejecutores. No reabre la memoria anterior ni resetea colecciones mutables compartidas. Una corrida activa por pestaña; dos escenarios simultáneos se ejercen en pruebas de aislamiento de infraestructura, sin añadir gestión avanzada de corridas a UI.

PresentadorSimulacion confina interacción al EDT y valida forma ligera. Captura configuración inmutable y deshabilita Iniciar antes de encolar creación para impedir doble clic/teclado. Generación, apertura, recursos, consultas, informes, métricas costosas, drenaje y close fuera de EDT. El scheduler sólo solicita refresco; una consulta en vuelo y un bit adicional agrupan avisos. Actualizar mediante invokeLater con snapshot inmutable. No get/join/awaitTermination/sleep/JDBC en EDT.

Cada entrega lleva token de propietario, generación, identidad de contexto, secuencia de petición y revisiones funcional/técnica; rechazar entregas antiguas, cruzadas o posteriores al cierre aun con IDs iguales. La captura funcional y estadística debe corresponder a la misma revisión; no mezclar contadores recientes con métricas viejas. Coalescer renderizados no descarta hechos. Durante fallo mostrar última captura histórica y contadores/métricas actuales N/D cuando no se acrediten.

La integración mínima en VentanaPrincipal sustituye construcción directa del marcador por panel/presentador H6 y mantiene cabecera/contadores operativos ligados al operativo. La pestaña tiene su propia cabecera y resultados. ReparaBarrioApp compone el propietario H6 sin pasar su servicio operativo al motor. No reutilizar TrabajosSwing operativo como pool de simulación.

**Cierre conjunto:** actualmente PresentadorTaller.cerrar termina llamando VistaTaller.cerrado/dispose. Se propone una coordinación de cierre en la composición de ventana (decorador de vista/callback), que sólo permite dispose cuando ambos propietarios cerraron. El presentador operativo conserva su secuencia y reglas; si hace falta un callback aditivo de cierre, limitarlo a esa notificación, con regresión H5. Botón salir y windowClosing pasan primero por esa coordinación, también si llega una apertura H6 tardía.

Salida humana: invalidar generación y detener nuevas llegadas/inicios; retirar llegadas aún no registradas; drenar registros/inicios admitidos y las reparaciones ya iniciadas con sus duraciones/cierres normales mientras contexto esté sano. No interrumpir JDBC ni cancelar una orden por cerrar ventana. Informar resultado «interrumpida por salida», con carga no entregada separada de órdenes registradas; no presentarlo como corrida completa. Ante bloqueo, no forzar cierres. Apagar scheduler y pools al drenar, obtener captura final cuando sea posible, cerrar contexto fuera de EDT y comprobar ciclo/informe. Si close falla retener ventana/propietario en diagnóstico y permitir solicitar nuevamente sólo cierre seguro. Sin System.exit ni liberación manual de guardia. Una demora JDBC no tiene cota universal; mantener navegación y aviso visible.

## 9. Implementación integral posterior y archivos

Sólo después de aprobar diseño, H6-D01/D02 y emitir el prompt integral de implementación, realizar en orden:

1. Contratos y contexto aislado: permiso memoria, creación/auditoría v2 y ancla; cierre y capacidad; reloj/hechos confirmados con composición controlable. Verificar aislamiento antes del motor. Detener ante necesidad de eludir H4 o alterar reglas/SQL.
2. Generador: validación y entrada H6-G1 completa, fixtures deterministas y perfiles de 100.
3. Motor: llegadas, pools, fichas, prioridad real, duración fuera de locks, cierres y quiescencia; supervisar fallos antes de agregar UI.
4. Estadísticas: hechos sin pérdidas, fórmulas, precisión, N/D y capturas coherentes.
5. Swing: controles/progreso/resultados, respuestas tardías y coordinación de cierre, sin reescribir presentación operativa.
6. Pruebas/evidencia: focales H6, regresión núcleo/H5 pertinente y recorrido Windows real; conservar fallos y límites. Auditar incremento y detener, sin H7/H8 automático.

| Archivos reales a adaptar en ejecución posterior | Cambio acotado propuesto |
|---|---|
| `arranque/FabricaContextos.java` | Entrada memoria protegida y mismo registro/retiro |
| `persistencia/PersistenciaH2.java`, `InicializadorBase.java` | Permiso memoria/ancla; reutilización de inicialización/auditoría; sin URL libre |
| `aplicacion/ContextoTaller.java` | Cierre de ancla antes de guardia e inyección de tiempo/hechos |
| `aplicacion/ServicioTaller.java` | Captura confirmada monotónica y consulta aditiva coherente, sin cambios de reglas/resultados existentes |
| `interfaz/PanelSimulacion.java`, `VentanaPrincipal.java`, `arranque/ReparaBarrioApp.java` | Pestaña, composición y cierre conjunto |
| `interfaz/PresentadorTaller.java` | Sólo si no basta decorador de vista, notificación aditiva de cierre; no automatización operativa |

Nuevos propuestos bajo el mismo prefijo Java: `persistencia/AutorizacionSimulacion.java`; `aplicacion/puertos/RelojIntervalos.java`; `aplicacion/HechosConfirmados.java` (valores y registro interno consolidables); `simulacion/ConfiguracionEscenario.java`, `CargaEscenario.java`, `GeneradorCarga.java`, `MotorSimulacion.java`; `arranque/SesionSimulacion.java`; `metricas/CalculadorEstadisticas.java`, `EstadisticasEscenario.java`; `interfaz/PresentadorSimulacion.java`, `VistaSimulacion.java`. Trabajo y temporizadores pueden ser tipos internos del motor. Evitar delegadores vacíos.

Pruebas propuestas bajo `src/test/java/ar/edu/unlam/reparabarrio/`: `arranque/ContextoSimulacionTest`, `simulacion/GeneradorCargaTest`, `MotorSimulacionTest`, `AislamientoSimulacionTest`, `metricas/EstadisticasEscenarioTest`, `interfaz/PresentadorSimulacionTest`, `IntegracionSimulacionH6Test`, `aplicacion/HechosConfirmadosTest`. Fixtures de referencia bajo `src/test/resources/escenarios/`. Reporte y evidencia H6 se crearían sólo en la futura fase autorizada, preservando históricos.

Conservar dominio, Planificador, esquema/protocolo/migración SQL, puertos de negocio JDBC, restricciones, POM/dependencias, SPEC, PLAN, diseños/decisiones/prompts previos. No se prevé modificar UnidadTrabajoJdbc ni AutorizacionBase de archivo. Si los cambios de composición requieren más superficie que la descrita, exponer impedimento antes de programar fuera de alcance.

## 10. Pruebas de aceptación propuestas

Todos los casos siguientes están **NO EJECUTADOS en esta solicitud de diseño**. JUnit 5 con H2 real para integración; dobles sólo para tiempo, despacho y presentación focales. Latches/barreras de test fuera del lock, controlador fuera de pools bajo ensayo, futures y limpieza con presupuestos explícitos; timeout falla el caso y conserva fichas/pilas/diagnóstico, nunca significa pendientes normales. Reportar comando, salida, entorno efectivo, carga, resultados y Surefire; Windows externo queda para implementación/aceptación. Pruebas finitas no acreditan ausencia universal de carreras/deadlock/starvation ni durabilidad de H4.

| ID | Precondiciones y estímulo | Invariantes / evidencia y herramientas | Límite de acreditación |
|---|---|---|---|
| H6-T01 | Configuración H6-G1 idéntica, dos generaciones; N=0/100, llegadas empatadas/aleatorias, recursos y fallos extremos | JUnit compara todos los campos de carga; variar semilla con fixture conocido, validar entradas inválidas/overflow sin abrir contexto | Igualdad de entrada; no igualdad de intercalados/antigüedad efectiva ni tiempos reales |
| H6-T02 | W≥2, dos técnicos/puestos y herramientas disjuntas, duraciones positivas; control de test mantiene trabajos abiertos hasta observar ambos | Barreras/latches y trazas con hilos distintos: max(iniciosTrabajo)<min(finesTrabajo); captura dos EN_CURSO disjuntas. Forzar registro/consulta concurrentes y verificar intervalos de llamadas solapados antes del coordinador | La captura sola no basta; ensayo controlado complementado con motor real sin barreras. No mide CPU |
| H6-T03 | Dos órdenes requieren M1 única, técnicos/puestos suficientes, llegadas concurrentes | H2/historial y oráculo independiente: ningún intervalo de M1 compartido, pendientes sin asignación ni reserva de T/P, siguiente avanza tras cierre | Contención conservando prioridad; propuesta de posterior construida para rechazo se identifica como inválida de test |
| H6-T04 | A antigua imposible, B/C factibles; nueva llegada y luego cierres; variante habilita A por fachada de test entre propuesta/confirmación | Registrar consultas por cambio; replay independiente por secuencia y recursos confirma primera factible al commit. Rechazo B si A ya factible; unidad ajena menor no invalida exacta vigente | Cambio de habilitación controlado de test no agrega edición manual al simulador |
| H6-T05 | Cero recursos/imposibles; variante taller vacío hoy con llegada futura; variante inicio/registro/cierre retenido antes de completarse; W lleno | Scheduler controlable, latches, H2: no fin prematuro con ficha/cola/futuro/EN_CURSO; al agotarse eventos informa causas y termina sin hilos colgados ni sexto estado; capacidad vuelve a disparar consulta | No se interpreta timeout como fin normal; no prueba terminación con JDBC permanentemente detenido |
| H6-T06 | Hechos confirmados y reloj monotónico controlable; cero inicios, período cero, recurso siempre fuera de servicio y fuera de servicio parcial, abierta/cerrada y cancelada | JUnit con valores de fixture exactos verifica sumas de intervalos, promedio sólo iniciadas, procesadas=F+fallidas, N/D y recortes; saltar Clock de pared no cambia intervalos | Aritmética focal; H2 real T11 comprueba origen confirmado. Valores esperados de fixture no son métricas del producto |
| H6-T07 | Operativo de ensayo nuevo y dos contextos H6, mismos O1/T1/P1/M1; simultáneos y luego nueva corrida | H2 real: UUID, coordinador, propietarios y URLs internas distintos; propuesta cruzada rechazada; captura/filas operativas idénticas antes/después sin acciones humanas intermedias; conexiones antiguas cerradas y nuevo escenario vacío | Operativo de fixture exclusivamente local, nunca inspeccionar datos reales. Comparación de contenido no sustituye revisión de conexiones/rutas |
| H6-T08 | Ambos perfiles de referencia de §4 con N=100, configuración/semilla conservadas | Motor real, trazas y captura final; cada registro/inicio/cierre coherente, fórmulas reproducibles desde hechos; Windows registra navegación/consultas/actualizaciones visibles durante corrida | Parámetros propuestos; ningún resultado atribuido hasta ejecutar. Sin metas de CPU/latencia inventadas |
| H6-T09 | Presentador con despacho/puerto controlables; abrir tarde, doble inicio, ráfaga de refrescos, cierre activo, cierre fallido y nueva ejecución | JUnit exige llamadas bloqueantes fuera de EDT y vista en EDT; latch bloquea servicio mientras evento EDT responde; generaciones invertidas se descartan; integra cierre conjunto y nueva instancia sin memoria reutilizada. Checklist Windows y capturas separadas | Dobles no acreditan H2 ni usabilidad real; cierre operativo no puede disponer ventana con H6 vivo |
| H6-T10 | Línea base existente y nuevas adaptaciones de composición | Selección futura: DominioTest, PlanificadorTest, OrdenNaturalIdTest, TransaccionesH2Test, ConcurrenciaH3Test/CoordinadorConcurrenciaTest pertinentes, CicloContextoTest/GuardiaInstanciaTest, RecorridoAcademicoNominalTest, PresentadorTallerTest, ArranqueAcademicoTest e IntegracionInterfazH5Test. Guardar selector exacto y resultados | No declarar suite global ni H4-T02/T03/T09 resueltos. Si aparece 90097-232 registrar fase/precondición y validar externamente sin bajar aserciones; no atribuir causa demostrada |
| H6-T11 | Decoradores focales sobre única composición H2 real exclusiva: commit conocido, rollback, commit incierto, cierre posterior fallido y bitácora fallida | Hecho sólo para commit conocido, mismo ID/evento y orden serializado; sin duplicados ni muestras perdidas; incertidumbre detiene sin reintento/recuperación; CONFIRMADA posterior conserva efecto aunque métricas queden incompletas | Inyección no acredita recuperación tras caída ni durabilidad de archivo; excepciones visuales no se convierten en FALLIDA ficticia |
| H6-T12 | Mismos fixtures de propuesta/inicio/cierre sobre contexto operativo de ensayo y simulación | JUnit parametrizado por composición/adaptador: mismas decisiones, selección, rechazo exacto/prioridad y liberación; comparar valores funcionales excluyendo identidades/tiempos propios | Equivalencia de reglas, no igualdad temporal; automatización sólo H6 |

Criterio de salida futuro: evidencia de CA-20…24 en alcance H6, concurrencia/contención y terminación acreditadas, fórmulas/aislamiento y EDT/cierre verificados, regresiones focales sin defecto nuevo material oculto, reporte que conserve límites H4 y revisión humana del incremento. Un éxito de memoria no valida durabilidad de archivo. H6 no se aprueba mediante este documento.

## 11. Riesgos, alternativas y decisiones humanas necesarias

| Riesgo | Mitigación propuesta / límite |
|---|---|
| Pool saturado o autoespera | Reservar sólo capacidad de infraestructura antes de iniciar; control/llegadas separados de trabajadores; no esperar tareas del propio pool ni recursos físicos |
| Fin prematuro / despertar perdido | Fichas antes de publicar eventos; versión de actividad y quiescencia doblemente comprobada; incluir cola, confirmación y obligación de cierre |
| Tormenta de reintentos / starvation | Consultas agrupadas por cambios confirmados; no sondeo ni retry de rechazo en estado idéntico. Carga finita y primera factible conservada; justicia del lock no garantiza plazo por orden |
| Deadlock por inversión | Monitor del motor liberado antes de fachada; coordinador → conexión privada; jamás esperar EDT/future/temporizador bajo lock |
| Datos cruzados | Capacidad H6 sin operativo, nombres internos nuevos, UUID por base, mismo registro de propietarios y rechazo de propuestas ajenas |
| Reloj / pérdida temporal | Monotónico inyectable y hechos completos confirmados; historial y bitácora correlacionados; fallo de métrica visible, sin alterar resultado del commit |
| Retención de asignaciones | Obligación por inicio confirmado y cierre único; ante incertidumbre no liberar por suposición. Datos descartables no autorizan saltar bloqueo |
| EDT o entrega antigua | Trabajo fuera de EDT, coalescencia de vistas y tokens de propietario/generación/revisión; cierre conjunto |
| H4 parcial | No reparar/reconciliar automáticamente; abortar sesión bloqueada, conservar diagnóstico y distinguir nominal de recuperación avanzada |

| Alternativa sustantiva | Ventajas | Costes / contratos afectados | Recomendación |
|---|---|---|---|
| H2 memoria nueva con guardia y ancla | Alinea PLAN, aislamiento y descarte al cerrar; sin inventario paralelo | Requiere permiso memoria, inicialización acotada, Almacen/ancla y cierre ContextoTaller; no ofrece reapertura ni evidencia durable después de caída | Elegir sólo con H6-D01 aprobada |
| H2 archivo descartable nuevo target/h6-* | Reutiliza protocolo por ruta/huella y facilita inspección posterior | Requiere nueva entrada autorizada (crearAcademico sólo admite h5-*), conserva archivos, recuperación física y riesgo ambiental; se aparta de preferencia PLAN | Alternativa para decisión humana si se rechaza memoria; no aplicar fallback automático |
| Temporizadores para toda duración sin trabajador retenido | Evita pool ocupado en esperas, más escalable | Cambia evidencia de tareas en hilos distintos y detalle PLAN §5.4; exige otro modelo de admisión/cierre | Conservar trabajadores con espera externa y control separado en v0.1 |
| Marcas temporales sólo en adaptador o Swing | Menos cambios al núcleo | Retornos pueden invertirse; refrescos pueden perder hechos; no asegura precisión PLAN §8.2 | Rechazar como fuente de RF-18; H6-D02 |

**H6-D01 — composición de simulación (pendiente).** Aprobar la adaptación de §3.1 para memoria H2 nueva, permiso específico, directorio nuevo de guardia dentro del proyecto, mismo propietario/capacidad/auditoría y cierre del ancla previo a liberar guardia. Recomendación: aprobar. Es indispensable porque los contratos actuales sólo componen archivo. Si no se aprueba, detener implementación y decidir expresamente la alternativa de archivo; no ampliar H5-D01 por inferencia.

**H6-D02 — hechos temporales confirmados (pendiente).** Aprobar puerto monotónico, inyección en composición y bitácora interna/consulta aditiva de §7.1, sin modificar estados, reglas, SQL ni semántica de resultados. Recomendación: aprobar. Es indispensable para cumplir origen confirmado, precisión serializada y cálculo sin pérdidas; no reutilizar el observador H3 descartable para ese fin.

No se reabren PT, PA ni decisiones H5. Pool auxiliar, controles mínimos y cierre conjunto son detalles técnicos sometidos a revisión integral del diseño, sin requerir preguntas individuales. Cualquier incompatibilidad nueva que exija tocar reglas, integridad o recuperación avanzada será impedimento material y deberá documentarse antes de continuar. En esta ejecución sólo se deja constancia de las dos dependencias: no se altera contrato alguno.

## 12. Fuentes, trazabilidad de esta entrega y detención

Fuentes documentales inspeccionadas: prompt P03-D-H6 íntegro en UTF-8; AGENTS, SPEC, PLAN, DECISIONES; diseños IMPLEM-H3/H4/H5; aprobación H5 y decisión académica H4; reporte H5 y checklist Windows; cierre externo académico H5; reportes H4 de implementación, corrección, intento 4 y alcance académico. Se distingue siempre diseño histórico de contrato efectivo actual y resultado documental previo de ejecución nueva.

Inspección estática de código: ServicioTaller, ContextoTaller, fachada/resultados/instantáneas/historial, Planificador y dominio, FabricaContextos, GuardiaInstancia, PersistenciaH2, InicializadorBase, AutorizacionBase, UnidadTrabajoJdbc y puertos; ventana, presentador, puerto, trabajos Swing, panel marcador y main H5. Revisión de pruebas pertinentes H1–H5 y sus casos existentes, particularmente ConcurrenciaH3Test, nominal académico, transacciones H2, presentación/integración H5, ciclo y recuperación H4. Los nombres de pruebas existentes en §10 son referencias de selección futura; no resultados de esta sesión.

Registro SDD: solicitud autorizada de diseño P03-D-H6 → análisis de fuentes y contratos → propuesta v0.1 en este archivo. Se identifican H6-D01 y H6-D02 para auditoría/aprobación humana. No se inventan métricas, cobertura ni pruebas exitosas. No se ejecutaron compilación, JUnit, GUI, JDBC ni escenarios; no se abrió ninguna base ni se ejecutó Git. Único archivo creado: `IMPLEM-H6.md`. No se modificaron código, pruebas, SQL, scripts, POM, normativa, decisiones ni prompts originales.

La entrega termina aquí. La aprobación humana del diseño no equivale a aceptación de H6 ni a autorización de implementación: se requiere el posterior prompt integral expresamente aprobado. H4 continúa parcial; H7/H8 y la aceptación global siguen pendientes.
