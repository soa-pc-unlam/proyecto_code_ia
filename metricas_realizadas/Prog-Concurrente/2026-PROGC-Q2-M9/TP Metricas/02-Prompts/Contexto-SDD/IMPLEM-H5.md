# ReparaBarrio — Diseño de implementación H5

| Control documental | Valor |
|---|---|
| Versión | 0.1 |
| Fecha | 15/10/2026 |
| Estado | **PROPUESTA — PENDIENTE DE REVISIÓN Y APROBACIÓN HUMANA** |
| Fase | SDD, P03: diseño previo de H5; implementación no autorizada |
| Solicitud | `prompts/anexos/P03-D-H5.md` v0.1, leído íntegramente |
| Rectores | `AGENTS.md`, `SPEC.md` v0.2 incluida §14/D016; `PLAN.md` v0.2 incluida ratificación §8.7/PT-01–PT-08 |
| Alcance humano posterior | `docs/DECISION-ALCANCE-ACADEMICO-H4.md` y contexto de continuación aprobado en P03-D-H5 |
| Único entregable de esta ejecución | Este archivo, en la raíz local |

## 1. Alcance, precedencia y evidencia de partida

Se propone una interfaz Swing operativa académica sobre Java 21, Maven y el núcleo H2/JDBC existente. Primero registro → propuesta → confirmación humana → inicio → finalización; después recursos, fallo, cancelación, tablero, historial y revisión. Las tres secciones serán Órdenes, Recursos y Simulación; esta última sólo indicará «Pendiente de H6». No habrá motor, carga, estadísticas de escenarios ni automatización de reparaciones.

H1–H3 tienen aprobación en sus alcances. H4 está **parcialmente implementado; recuperación avanzada no aprobada**. La dependencia H4 de PLAN §8.3 se conserva: la decisión académica permite continuar H5 con limitaciones explícitas, sin convertir esa continuidad en cumplimiento de CA-16/CA-17. La ratificación PLAN §8.7 resuelve la interpretación normativa de PT-06; no demuestra su implementación completa.

Hay una diferencia temporal documental que debe conservarse: `H4-REPORTE-ALCANCE-ACADEMICO.md` relata el fallo anterior en MXC por H2 90097-232 y pide validación externa. El archivo posterior `metricas/logs/P03-H4-recorrido-academico-Windows.txt`, inspeccionado como texto, contiene los siete marcadores `NOMINAL paso=1` a `7 OK`, **1 prueba, 0 fallos, 0 errores, 0 omitidas, BUILD SUCCESS**, finalizado el 14/10/2026. P03-D-H5 ratifica ese nominal y refiere el commit local `df38e6a`; aquí no se ejecutó Git para certificar ese commit ni se repitió la prueba. El nominal no prueba reapertura/caída, recuperación avanzada ni interfaz visual.

Se examinaron los contratos de aplicación, dominio, planificación, arranque y persistencia; las pruebas `RecorridoAcademicoNominalTest`, `TransaccionesH2Test`, `ConcurrenciaH3Test`, `RecuperacionH4Test`, `CicloContextoTest` y `TestContextos`; `docs/CIERRE-H3.md` y los reportes H4 de implementación, corrección, intento 4 y alcance académico. Los fallos históricos T02/T03/T09 y la intermitencia H3-T05 siguen abiertos según esos documentos. No se atribuye causa raíz única ni pérdida física irreversible demostrada.

Esta ejecución sólo inspecciona texto y produce diseño. No ejecuta compilación, pruebas, GUI, JDBC ni procesos auxiliares; no modifica Java, SQL, pruebas, POM, SPEC, PLAN, prompts, métricas o historia. No hace commits ni operaciones remotas. Las verificaciones de las secciones siguientes son futuras.

## 2. Trazabilidad y frontera de aceptación

| Incremento H5 | Referencias normativas exactas | Núcleo existente y evidencia relacionada | Salida futura H5 / límite |
|---|---|---|---|
| Registrar órdenes | CU-01; RF-01/02/10; RN-04; INV-05/06/07; CA-01; PA-01/03/04/07 | `registrarOrden`; validación, secuencia e historial transaccionales; nominal y `TransaccionesH2Test.registroValidacionCatalogoAmbitosYSecuencia_CA01_CA02` | Formulario, catálogo consultado y resultado conservado visible. No generar secuencias en UI |
| Proponer y explicar espera | CU-03/07; RF-05/06/09; RN-01/02/03/05/08; INV-03/07; CA-03/04/06/18/19; PA-02/06/13 | `consultar().valor().planificacion()` y `Planificador.proponer` | Primera factible con unidades, aviso sin reserva, causas completas; no seleccionar otra para inicio |
| Confirmar inicio | CU-04; RF-07/08/13; RN-04/06/08; INV-01/02/05/06/07; CA-05/07/08/09/10/19 | `confirmarInicio`; revalidación exacta/commit; H3 aprobado en su alcance | Acción humana explícita sobre la propuesta mostrada; rechazos motivados; no caducidad ni sustitución |
| Finalizar, fallar, cancelar | CU-05/06; RF-08/10/11/12; RN-06; INV-05/08; CA-11/12/13/14 | `finalizar`, `fallar`, `cancelar`; pruebas H2/H3 y nominal | Acciones por selección de ID, resultado y refresco coherente; ningún cierre automático |
| Alta y habilitación de recursos | CU-02; RF-03/04; RN-01/07; INV-01/04/06; CA-02/15; D016/PT-05 | `registrarRecurso`, `cambiarHabilitacion`, disponibilidad derivada | Tres categorías, sólo tipos existentes, libres habilitables/inhabilitables; sin editor de tipos |
| Tablero e historial | CU-07; RF-09/10/15; RNF-05; INV-03/04/08; CA-18; PA-07/09 | Instantánea, asignaciones, causas e historial inmutables | Cinco contadores globales separados, detalle y navegación. CA-18 sólo en operativo en H5; parte durante simulación pendiente H6 |
| Salud y revisión humana | CU-08; RF-13/14; RNF-04; RN-10; INV-06/09; CA-16/17; PA-08; PT-06/07 | Ciclo, informe, revisiones y cierres existentes; H4 parcial | Presentar señal/diagnóstico y bloquear seguro. No declarar CA-16/17 conformes ni reparar recuperación desde UI |
| Escritorio y ejecución local | RF-15; RNF-01/02/05/07; CA-18/25; PT-01/02; PLAN §§2/3/8.3 | Maven/Java 21 existentes, sin `main` ni Swing | Arranque local y cierre controlado Windows. Empaquetado final y aceptación global quedan H7 |
| Corrección concurrente transversal | RNF-03/06/08; INV-01–09; CA-07–10/13/14/26; PT-03/04/06 | Un coordinador justo por contexto, JDBC privado y planificador común | No duplicar mecanismos. EDT separado, deduplicación y respuestas tardías probadas; pruebas finitas no garantizan ausencia universal |
| Fuera de H5 | CU-09; RF-16–19 y parte de RF-15/RNF-05 durante simulación; RN-09; INV-10; CA-20–24 | No hay motor/generador/métricas completos | H6: contexto aislado, carga, concurrencia ficticia, terminación y estadísticas. H7: referencia integral de 100, distribución y matriz completa; H8: mediciones académicas |

Las cinco transiciones admitidas siguen siendo: registro a PENDIENTE; PENDIENTE a EN_CURSO o CANCELADA; EN_CURSO a FINALIZADA o FALLIDA. Los tres estados terminales no se reabren. Espera, revisión y salud del contexto son información adicional. La GUI no modifica invariantes ni autoriza saltos manuales.

## 3. Diagnóstico e integración con APIs reales

Las rutas Java de las tablas siguientes son relativas a `src/main/java/ar/edu/unlam/reparabarrio/`. Los nombres propuestos posteriormente no son APIs existentes.

| Ruta / tipo existente | Contratos disponibles | Uso propuesto |
|---|---|---|
| `aplicacion/ServicioTaller.java` | Clase `final`, constructor no público; `registrarOrden(String,String,Collection<TipoHerramienta>)` → `ResultadoOperacion<Orden>`; `registrarRecurso(Recurso.Categoria,String,TipoHerramienta,boolean)` → resultado de recurso | Adaptador de presentación delega sin SQL ni validaciones funcionales propias |
| Mismo servicio | `consultar()` → `ResultadoOperacion<InstantaneaTaller>`; `confirmarInicio(PropuestaInicio)` → resultado de propuesta; `finalizar(String)`, `fallar(String)`, `cancelar(String)` → resultado de orden; `cambiarHabilitacion(Recurso.Clave,boolean)` → resultado de recurso | No existe método separado `consultarPropuesta`, `tablero` o `historial`; todos se obtienen de `consultar` |
| `aplicacion/InstantaneaTaller.java` | `revision`, `dominio`, `catalogo`, `historial`, `planificacion`, `revisionRecuperacion`, `revisiones` | Captura completa; contadores, filtros y modelos derivados de una sola captura |
| `dominio/InstantaneaDominio.java` | `contexto`, `ordenes`, `recursos`, `asignaciones`; `disponibilidad(Clave)` | Disponibilidad derivada, no bandera mutable Swing. Valores copiados e invariantes del agregado |
| `dominio/Orden.java`, `Recurso.java` | Orden: ID, descripción, secuencia, requisitos, estado. Recurso: clave con categoría/ID, tipo y habilitación; tres disponibilidades | Mostrar IDs exactos sensibles a mayúsculas; no recortar espacios silenciosamente; categoría forma parte de identidad |
| `dominio/PropuestaInicio.java`, `SeleccionRecursos.java` | Contexto, orden y selección exacta: técnico, puesto, mapa tipo → clave física; `unidades()` | Retener el mismo valor que vio/aceptó el humano; no reconstruirlo desde celdas ni editar unidades |
| `planificacion/Planificador.java` | `Resultado(Optional<PropuestaInicio>,Map<String,List<String>> causas)`; `proponer`, `validarPropuesta` | La aplicación lo usa dentro de su coordinación. UI muestra sus causas, sin ejecutar otra política de prioridad |
| `aplicacion/EventoHistorial.java` | Secuencia, operación ID, orden ID (puede ser nulo para recursos), acción, `Instant`, conjunto de claves | Ordenar por secuencia; acción INICIO conserva asignación, FINALIZADA/FALLIDA conserva liberación. No inventar eventos separados de liberación |
| `aplicacion/ResultadoOperacion.java` | `estado`, `contexto`, `operacionId`, `valor`, `diagnostico`, `tecnico`; estados CONFIRMADA, RECHAZADA, SIN_CAMBIO, ERROR_TECNICO, PENDIENTE_VERIFICACION | No confundir resultado con estado de orden. Diagnóstico técnico contiene código, fase, causa, salud, durabilidad de diagnóstico y SQLState; tolerar `tecnico == null` por constructor compatible |
| `aplicacion/ContextoTaller.java` | `identidad`, `ciclo`, `servicio`, `recuperacion`, `informe`, `close`; ciclos ABRIENDO/HABILITADO/BLOQUEADO/CERRANDO/CERRADO | Arranque posee contexto; presentación recibe capacidad limitada. `informe()` adquiere lock: también fuera de EDT |
| `aplicacion/InformeRecuperacion.java` | Base ID, ciclo, sesión ID, operaciones y resoluciones, revisiones, hallazgos, referencia y revisión | Mostrar diagnóstico conservado; no sustituye una instantánea funcional actual |
| `aplicacion/ServicioRecuperacion.java` | `recuperar()` y `aceptarAdopcion(String,long,String)` | Existen, pero hacen trabajo técnico y escrituras; no son consultas inocuas. No se expondrán como botones de desbloqueo/migración en H5 nominal |
| `aplicacion/puertos/RepositorioRecuperacion.java` | `Revision(ordenId,sesionId,motivo,momento,cierreId)`; puertos para intención/sesión/revisión/adopción | Se muestran valores de revisión ya incluidos en DTO; ninguna escritura directa desde Swing |
| `aplicacion/puertos/RepositorioLectura.java`, `RepositorioTaller.java`, `UnidadTrabajo.java` | Carga/catálogo/historial/revisión; escrituras internas; fábrica de unidades, commit/rollback/close y `terminada()` | Mantener internos; lectura estrategia B no concede commit a cliente. Una conexión privada por operación |
| `arranque/FabricaContextos.java` | `abrirConInforme(Path,Solicitud)` → `Apertura(contexto,codigo,diagnostico)`; `abrir`, `validarRaiz`, `existente` | Raíz absoluta real con POM/AGENTS; usa siempre `<raíz>/datos/operativo/taller`, registro de propietarios y guardia. Apertura puede retornar contexto BLOQUEADO o contexto nulo con código |
| `arranque/GuardiaInstancia.java` | `adquirir(Path)`, `vigente`, `close`; `instancia.lock` permanente | Adquirir antes de JDBC, retener hasta cierre comprobado; segunda instancia rechazada, no borrar lock |
| `persistencia/PersistenciaH2.java` | `preparar(GuardiaInstancia,Solicitud)` → almacén; `ContextoTaller.componer` reclama capacidad. `abrir(Path)` histórico/deprecado sólo recupera propietario existente | No usar API deprecada para crear bases; no componer un segundo propietario por la misma ruta |
| `persistencia/InicializadorBase.java`, `AutorizacionBase.java` | Modos CREAR_NUEVA/ABRIR_EXISTENTE/MIGRAR_V1/REANUDAR; `Solicitud(modo,autorizacion)`; `huella(Path)`; permiso por ruta, huella, origen/destino y declaración | Sólo creación de nueva descartable en propuesta por defecto. `recuperacionFisica=true` es exigida incluso para crear. No fabricar permisos para archivos reales |

El servicio conserva intención técnica antes del negocio; un rechazo puede agregar metadatos técnicos sin efectos funcionales. Tras commit confirmado puede adjuntar un fallo posterior de cierre/resolución. Por ello ni `CONFIRMADA` sola habilita todos los controles, ni un diagnóstico técnico prueba rollback. Consultas en un contexto incierto no entregan un agregado válido: retornan incertidumbre sin valor.

Faltantes reales: paquete Swing, punto de entrada, puerto sustituible para pruebas de presentación, arranque académico fuera de la ruta operativa real y procedimiento de ejecución H5. No hace falta agregar métodos al servicio para propuesta/tablero/historial ni un sexto estado. La recuperación avanzada existe como código, pero carece de aceptación integral: no se reemplaza con una implementación en la vista.

## 4. Pantallas y recorrido humano

Ventana única con título ReparaBarrio, indicación visible «Prototipo académico — H4 parcial», ruta descartable e identidad del contexto. Cabecera con salud, actividad y última lectura confirmada; cinco contadores **PENDIENTE, EN_CURSO, FINALIZADA, FALLIDA, CANCELADA**, incluyendo ceros. Son totales del contexto, independientes del filtro de tabla; no se agrupan terminales. Si falta lectura confirmada se muestran «N/D», nunca ceros supuestos.

**Órdenes.** Tabla de secuencia, ID, descripción, requisitos, estado y nota de espera/revisión. Orden inicial por secuencia. Filtro visual por estado/ID que no altera la prioridad. Detalle por selección con asignación activa y causas completas. Formulario de alta: ID textual, descripción y selección múltiple de tipos del catálogo consultado (una casilla por tipo, ninguna válida); sin cantidad, secuencia editable ni unidades físicas. Mensajes de entrada no vacía son ayuda; unicidad, requisitos y transición las decide la fachada.

Panel de propuesta distinto de la selección de tabla: botón «Consultar primera factible», orden y secuencia, técnico, puesto, herramientas por tipo e ID, y texto permanente «Sin reserva. La confirmación revalida prioridad y unidades exactas». Sin candidato: «No hay orden factible» con causas de pendientes, o «No hay órdenes pendientes» si corresponde. Causas originales del planificador se muestran completas, distinguiendo «Sin técnico/puesto/unidad disponible de …» de «Bloqueada por primera factible X». No descomponer causas por heurísticas de texto para decidir negocio.

«Confirmar inicio» abre diálogo con la propuesta exacta y botones «Iniciar reparación» / «Volver»; aceptar es la única fuente de confirmación. Ninguna selección de fila construye una propuesta alternativa. La navegación, el filtro o la recepción de un refresco no aceptan el diálogo. Mientras esté abierto se congela su contenido; la propuesta aceptada se conserva tal cual, aunque una nueva lectura seleccione otra unidad. El núcleo decide si sigue válida. Un cambio de revisión global, paso del tiempo o unidad ajena recién disponible no la invalida por sí solo. Al cerrar/cambiar contexto se invalida la interacción; nunca se envía al contexto nuevo.

Recorrido: alta → resultado confirmado → consulta completa → propuesta solicitada → aceptación humana → envío único de `confirmarInicio` → resultado → nueva consulta → visualización EN_CURSO/asignación → selección y «Finalizar reparación» → confirmación humana de ID/acción → resultado y nueva captura. No se muestra EN_CURSO por haber hecho clic. Los formularios se limpian sólo después de éxito conocido; ante rechazo/error se conserva entrada.

Acciones complementarias: «Cancelar pendiente» sólo para PENDIENTE; «Informar fallo» y «Finalizar» sólo para EN_CURSO. Se solicita confirmación con ID y acción; los estados finales son de consulta. No se agrega campo motivo de fallo obligatorio porque el método actual sólo acepta ID. La deshabilitación visual guía al usuario y no sustituye la revalidación del servicio.

Historial y revisiones como detalles dentro de Órdenes, evitando nuevas secciones principales. Historial: secuencia, momento con zona/unidad legibles, orden o recurso, acción, unidades y operación ID en detalle. Revisión: orden, motivo, momento, sesión de origen y cierre si existe. Pendiente si `cierreId == null`; resuelta si presente. No marcar resuelta al leer/aceptar aviso. La finalización/fallo existente la resuelve transaccionalmente sólo cuando el contexto está habilitado.

**Recursos.** Tabla de categoría, ID, tipo si corresponde, disponibilidad derivada y orden titular activa si la hay. Alta con categoría/ID, tipo del catálogo exclusivamente para herramienta y habilitación inicial. Ningún alta OCUPADO. Acciones «Poner fuera de servicio» y «Rehabilitar» para libres; ocupado muestra el motivo de indisponibilidad del control. El servicio verifica nuevamente. Sin borrar, renombrar, editar tipos o liberar manualmente.

**Simulación.** Texto «Pendiente de H6» y breve explicación; sin botones de inicio, temporizador, métricas ni valores ficticios. El ejecutor de tareas Swing de H5 es infraestructura de presentación, no motor de simulación ni evidencia de CA-21.

## 5. Responsabilidades y archivos futuros

Todos estos son cambios propuestos para una autorización posterior, no realizados aquí. Bajo la raíz Java antes indicada:

| Archivo futuro | Responsabilidad |
|---|---|
| `interfaz/VentanaPrincipal.java` | Composición de pestañas, cabecera y cierre solicitado; vista sin acceso JDBC |
| `interfaz/PanelOrdenes.java`, `PanelRecursos.java`, `PanelSimulacion.java` | Formularios/tablas/detalles y marcador H6; modelos no editables. Modelos de tabla internos para evitar clases vacías |
| `interfaz/VistaTaller.java` | Contrato pequeño de presentación: entradas/eventos y renderizado/mensajes; implementado por ventana y doble de prueba |
| `interfaz/PresentadorTaller.java` | Estado de interacción en EDT, comandos, deduplicación, generación de ventana/contexto y aplicación de respuestas |
| `interfaz/PuertoTaller.java` | Contrato propio del adaptador con las operaciones usadas de ServicioTaller; permite fachada controlada en pruebas sin heredar clase final |
| `interfaz/AdaptadorServicioTaller.java` | Delegación al servicio existente, conserva tipos de resultados y DTO; ninguna regla/SQL |
| `interfaz/TrabajosSwing.java` | Un ejecutor de aplicación en segundo plano, publicación vía `invokeLater`, agrupación de consultas y drenaje; ejecutor inyectable en tests |
| `arranque/ReparaBarrioApp.java` | `main`, argumentos/raíz autorizada, composición, apertura y cierre fuera de EDT, creación de ventana en EDT |
| `arranque/ArranqueAcademico.java` | Creación exclusiva de directorio nuevo descartable y solicitud explícita; validar ruta real. No reutiliza `TestContextos` de test |
| `arranque/FabricaContextos.java` (modificación mínima propuesta) | Entrada explícita para nueva base académica bajo `target/h5-*`, reutilizando el registro y composición guardia/almacén/propietario existentes |
| `src/test/java/.../interfaz/PresentadorTallerTest.java`, `IntegracionInterfazH5Test.java`; `.../arranque/ArranqueAcademicoTest.java` | Pruebas descritas en §10; usar infraestructura de prueba existente sólo desde tests |
| `H5-REPORTE-IMPLEMENTACION.md` y evidencias nuevas locales (futuros) | Cambios reales, comandos/resultados, checklist visual y límites; preservar históricos |

La nueva entrada de fábrica aceptaría únicamente creación nueva acreditada bajo la raíz real `target/h5-*`, nunca ruta arbitraria de archivo ni alternativa para migrar/recuperar bases reales. Mismo mapa de propietarios y retirada, sin segundo registro mutable. Es una adaptación de arranque requerida por la frontera académica, sometida a aprobación en §12. No se propone modificar dominio, servicio, coordinación, SQL, SPEC o PLAN. No se agrega dependencia Swing, ORM, biblioteca de mocks ni plugin de ejecución. Si la implementación descubre necesidad de modificar integridad o contratos de negocio, eso es impedimento material: detener y documentar.

## 6. Concurrencia, EDT y trabajos pendientes

Se propone `ExecutorService` de un trabajador para solicitudes operativas de esta ventana y cierre secuencial, con entrega por `SwingUtilities.invokeLater`. Un solo trabajador simplifica el orden de respuestas y no reduce la coexistencia de órdenes EN_CURSO ni cambia el coordinador. H6 tendrá sus ejecutores concurrentes según PLAN/PT-08. `SwingWorker` por acción es alternativa válida, pero añade coordinación entre respuestas y usa un pool compartido innecesario en H5; no se elige como mecanismo adicional.

EDT captura campos/selecciones en valores inmutables, valida forma ligera, actualiza modelos, muestra diálogos y registra intención de cierre. Encola sin bloquear. Toda llamada al servicio, `informe()`, apertura, huella, sistema de archivos de arranque, recuperación y cierre va fuera de EDT. No usar `Future.get`, `join`, `awaitTermination`, `close` de ejecutor ni espera de lock en EDT. El trabajador no lee componentes Swing: recibe la entrada capturada. No invocar aplicación dentro de un monitor/modelo Swing ni esperar EDT bajo el coordinador; retorno completo antes de publicar UI. No `invokeAndWait` en producción.

Un comando mutante en vuelo por ventana: se deshabilitan acciones mutantes antes de encolarlo y se rechaza un segundo evento equivalente incluso si proviene de teclado/doble clic. La navegación y lectura del último modelo siguen disponibles con indicador «Operación en curso». Se permite a lo sumo una consulta en vuelo y una petición de refresco pendiente agrupada; no acumular una cola de clics ni refrescos periódicos. En H5 bastan refresco inicial, manual y posterior a operación; el temporizador se difiere hasta necesitarlo en H6. Los cambios posteriores al aceptar diálogo vuelven a comprobarse en negocio.

Cada trabajo lleva generación local de ventana/apertura, identidad de contexto, número monotónico de solicitud y tipo de trabajo. La generación cambia también al reabrir la **misma base**: su UUID puede seguir igual y no identifica al propietario nuevo. En EDT se descarta respuesta si generación/identidad no corresponde, ventana cerrada o cierre iniciado. Para tablas se conservan revisiones funcional y de recuperación: no aplicar retrocesos en ninguna, y ante combinación contradictoria retirar vigencia y solicitar lectura nueva; revisiones iguales son válidas. El número de solicitud impide que una lectura anterior sobrescriba una posterior aun a igual revisión. Estas reglas sólo ordenan presentación, nunca son precondiciones de negocio ni caducidad de propuestas.

Una respuesta de comando no se pierde por haber llegado una consulta más reciente: sus mensajes se correlacionan al comando activo; las revisiones filtran capturas, no convierten resultados confirmados en rechazo. Incertidumbre/error del contexto prevalece sobre cualquier refresco de la misma generación y retira propuestas/permiso de mutar.

No ofrecer «Cancelar operación JDBC». Se pueden retirar consultas aún no iniciadas y trabajo visual pendiente. Una operación mutante ya iniciada debe terminar y entregar resultado; interrumpir un hilo o cancelar un `Future` no prueba rollback ni ausencia de commit. En cierre normal se detiene admisión, se descartan consultas pendientes, se deja terminar comando activo y se encola cierre. Si una apertura termina cuando ya se pidió cerrar, se cierra ese contexto en segundo plano y no se crea ventana operativa tardía.

Recursos compartidos: estado de presentación confinado a EDT; colas con sincronización del ejecutor; filas/salud/sesiones bajo coordinador; registro de propietarios en fábrica. No agregar locks por técnico/herramienta ni callbacks Swing al observador bajo una transacción. Riesgo de deadlock por esperar EDT mientras éste espera trabajador evitado por publicación asíncrona; disco lento puede demorar trabajador/cierre. La justicia del lock no garantiza ausencia de starvation, ni el ejecutor puede resolver prioridad humana o una orden imposible.

## 7. Resultados, consistencia y errores

Después de cada mutación terminada, consultar una instantánea completa antes de rehabilitar acciones normales. No combinar órdenes de una captura con recursos/contadores de otra. Contadores y tablas se reemplazan juntos en EDT; preservar selección por clave si existe, no por índice de fila ordenada. La propuesta aceptada en diálogo se guarda separada de la nueva planificación y nunca se sustituye silenciosamente.

| Resultado real | Mensaje y efecto visual |
|---|---|
| CONFIRMADA en mutación | «Operación conservada», ID y acción; luego refrescar. No inferir liberación/estado desde el clic |
| CONFIRMADA en consulta | Mostrar captura coherente; sin propuesta es ausencia normal, no rechazo |
| SIN_CAMBIO | «Sin cambio»; no sumar eventos ni contadores locales; refrescar |
| RECHAZADA | Motivo íntegro de negocio y orientación para consultar de nuevo; no normalizar silenciosamente ni repetir comando |
| ERROR_TECNICO | Mensaje legible y detalle técnico accesible; retirar propuesta y suspender mutaciones. Una lectura posterior confirmada y ciclo HABILITADO pueden rehabilitar; si bloqueado, permanecer en diagnóstico |
| PENDIENTE_VERIFICACION | «Resultado pendiente de verificación», operación ID, fase/salud y durabilidad del diagnóstico si disponible. Sin éxito ni rechazo definitivo; detener consultas automáticas y mutaciones, sin reintento |
| CONFIRMADA con aviso técnico posterior | Conservar el éxito ya conocido y mostrar advertencia. Verificar ciclo/informe fuera de EDT; no permitir que el refresco o cierre fallido conviertan ese commit en rechazo |
| Excepción inesperada sin resultado del adaptador | No afirmar ausencia de efectos. «No se pudo determinar el resultado desde la interfaz»; suspender mutaciones, conservar entrada y diagnóstico. No inventar un estado del núcleo ni reintentar |

Si un comando fue confirmado pero falla el refresco: «Operación conservada; no se pudo actualizar la vista». Mantener la última captura como **anterior/no vigente**, sin propuesta accionable. Recargar sólo es consulta, no repetición de mutación. En incertidumbre/bloqueo se muestra diagnóstico y última captura rotulada histórica, o se vacían tablas si nunca hubo una válida; contadores vigentes N/D. Una consulta vieja exitosa no elimina el bloqueo visual posterior.

Presentar resumen comprensible y detalle expandible con código/fase/SQLState/operación/contexto y diagnóstico original completo, evitando volcar SQL o stacktrace en el mensaje principal. No afirmar rollback conocido por el mero nombre ERROR_TECNICO: usar la evidencia del resultado y salud existentes. No capturar fallos para informar FALLIDA de negocio, pues ese cierre sólo puede nacer de acción humana explícita.

## 8. Arranque y salida en Windows

Por defecto, **sesión académica nueva de archivo**, creada sólo tras una acción explícita «Crear sesión académica descartable» que muestra alcance y ubicación dentro de este proyecto. La aprobación futura de implementación deberá autorizar esta creación local. Crear directorio único bajo `<raíz real>/target/h5-<id>`; comprobar pertenencia, componentes sin enlaces, ausencia de inventario H2 y ausencia de base conocida; base fija `taller`. No aceptar parámetros JDBC ni selector de archivo arbitrario. Declarar que `target` es descartable y un `mvn clean` puede eliminarlo; no ejecutar clean con la app abierta ni borrar automáticamente al salir.

La fábrica académica propuesta toma guardia del directorio, genera `AutorizacionBase` específica (ID válido, ruta absoluta, huella AUSENTE, origen 0/destino 2, sin preservación, recuperación física requerida por contrato, reanudación false y declaración de creación descartable), arma `Solicitud(CREAR_NUEVA,...)` y usa composición protegida actual. Ante restos de intento parcial, huella distinta o base conocida ausente, rechazar: no reiniciar, reanudar ni migrar. Una sesión nueva posterior conserva el directorio anterior para inspección y usa otro nuevo tras decisión explícita, sin borrar evidencia.

No se llama `FabricaContextos.abrir` con la raíz real de este proyecto en el recorrido por defecto: tocaría `datos/operativo`. Tampoco se fabrican POM/AGENTS ficticios para hacer pasar una carpeta temporal por proyecto. La reapertura de una base explícitamente autorizada requiere permiso específico de ruta/huella y flujo ABRIR_EXISTENTE, independiente de este predeterminado; se propone dejarla fuera de H5 académico inicial, sin bloquear el recorrido nuevo. Una autorización global para H5 no se interpreta como permiso de migrar o recuperar una base real.

Orden: validar raíz/argumentos → mostrar arranque sin controles operativos → decisión de nueva sesión → trabajo de apertura con guardia, inicialización y auditoría existentes → recibir ciclo/informe → primera `consultar` confirmada → habilitar UI. No usar `.abrir` deprecado ni eliminar auditoría/intenciones para evitar bloqueos. Una segunda instancia sobre el **mismo directorio** debe recibir INSTANCIA_OCUPADA; dos sesiones descartables distintas no comparten datos y no infringen la guardia por base. Al fallar apertura conservar código y diagnóstico, sin mostrar un taller vacío válido.

Procedimiento futuro de desarrollo local, no ejecutado aquí: desde raíz con JDK 21, Maven y dependencias H2/JUnit ya disponibles, `mvn -o -B -ntp compile`; después `java -cp "target/classes;<ruta absoluta local al h2-2.3.232.jar>" ar.edu.unlam.reparabarrio.arranque.ReparaBarrioApp --raiz "<raíz absoluta del proyecto>" --academico-nuevo`. Los argumentos son contrato **propuesto** del nuevo main. La ruta del JAR se documentará usando la ubicación real del repositorio Maven local, sin suponer que sea siempre `.m2` predeterminada. Java classpath Windows usa punto y coma. No requiere ejecutar JUnit para operar ni servicios remotos. No modificar POM para un plugin de lanzamiento; JAR distribuible con dependencias/Main-Class sigue pendiente de H7 conforme PLAN.

Salida: `DO_NOTHING_ON_CLOSE`, indicar «Cerrando», invalidar generación, detener admisión/refrescos y drenar tarea activa fuera de EDT; después `ContextoTaller.close()` y terminación del ejecutor. No finalizar/fallar órdenes por cerrar ventana. El contexto puede conservar EN_CURSO. Su cierre tiene `tryLock` de 10 segundos, puede retener guardia y puede llegar a CERRADO con hallazgos de marca limpia no verificada: inspeccionar ciclo e informe después y no anunciar «cierre limpio» sólo porque `close` retornó.

Si falla o demora el cierre, mantener ventana de diagnóstico y opción de volver a solicitar cierre seguro del mismo propietario; no crear otro contexto ni liberar guardia a mano. No usar `System.exit`/cancelación forzada para fingir cierre correcto. Un cierre forzado externo tiene las limitaciones H4 documentadas y no forma parte del recorrido nominal aprobado. No se promete una cota universal de apagado JDBC.

## 9. H4: revisión representable y límites de seguridad

H4-T02 (continuidad/metadatos/asignaciones), T03 (revisión y carrera de cierres tras rollback) y T09 (revisiones incompletas tras fallo/reintento) siguen sin cierre. La GUI no acredita recuperación por mostrar una captura lógicamente coherente: podría ser una frontera anterior. No ocultar fallos de sesión ni cambiar integridad/FK/SQL, ni usar memoria como operativo alternativo.

Puede representarse hoy: marcas persistidas en `InstantaneaTaller.revisiones`, campos de `Revision`, operaciones/hallazgos de `InformeRecuperacion`, ciclo y resultados inciertos. Una EN_CURSO con revisión conserva asignación/ocupación. Si el contexto está HABILITADO y la captura confirmada es válida, el humano puede finalizar/fallar por los métodos normales; no hay botón «Revisión aceptada» ni liberación por reconocimiento.

Ante BLOQUEADO/incertidumbre/sesión incoherente, deshabilitar todas las mutaciones, incluso los cierres de reparación, conservar diagnóstico y ofrecer salida segura. `consultar` puede estar bloqueado también: no insistir periódicamente. `informe()` se obtiene fuera de EDT como diagnóstico, sin SQL de recuperación. Si su información quedó antigua, rotularla como último informe, no como reconstrucción actual del taller.

`ServicioRecuperacion.recuperar()` existe y puede reconciliar/abrir sesiones/escribir revisiones; `aceptarAdopcion` trata procedencia v1. Su existencia no demuestra corrección avanzada y no autoriza exponerlas como «Reintentar» genérico. Se propone **no invocarlas explícitamente desde controles H5**, manteniendo sólo el arranque protegido que ya recupera como parte del ciclo. No puede impedirse esa recuperación interna saltando la composición. Migración/adopción/reanudación, corrección manual de datos y un flujo fiable de resolución técnica guiada requerirían alcance y validación específicos posteriores; no son capacidades garantizadas por esta propuesta. Falta un caso de uso validado que autorice reparar arbitrariamente una sesión inconsistente; no se inventa tal API.

## 10. Pruebas y evidencias futuras

No se ejecutaron pruebas en esta fase. Las siguientes verificaciones se realizarían sólo tras autorización de implementación; resultados esperados no son resultados observados.

| Nivel / caso | Verificación y evidencia futura |
|---|---|
| Presentador con PuertoTaller controlado | Flujo completo, cinco contadores incluyendo ceros, catálogo sin unidades aceptable, causas intactas, selección/filtros sin alterar primera factible; captura de llamadas a vista y servicio |
| EDT y segundo plano | Doble de fachada exige `!isEventDispatchThread`; vista/modelos exigen EDT. Bloquear fachada mediante latch y enviar evento EDT que debe procesarse antes de liberarla. Comprobar también apertura, informe y cierre fuera de EDT |
| Confirmación humana y duplicados | Ninguna llamada al abrir/cancelar diálogo; aceptar conserva propuesta exacta, doble clic produce un envío; nueva unidad ajena no sustituye propuesta. Cancelar/finalizar/fallar sólo envían ID/acción aceptados |
| Resultados y refresco | Todos los estados del resultado, técnico nulo, commit confirmado + error de cierre, fallo de refresco, excepción inesperada y propuesta ausente; incertidumbre no dispara reintentos y retira mutaciones |
| Respuestas tardías | Ejecutores/publicadores controlados invierten entrega: lectura anterior tras nueva, mismo UUID con generación nueva, revisiones técnica/funcional, bloqueo tras lectura, apertura después de cerrar ventana; ninguna respuesta antigua modifica vista nueva |
| Cierre/cancelación de tareas | Rechazar nuevos comandos, quitar consulta sin iniciar, drenar mutación iniciada sin cancelarla, retener diagnóstico ante close fallido; no liberar recursos del taller por cerrar GUI |
| Integración H2 focal | Base nueva de archivo bajo target/h5-*, guardia y composición real; alta recursos/órdenes, propuesta sin reserva, inicio/cierre/cancelación/fallo, captura e historial, sin acceso a datos reales. Adaptador real/presentador con vista de prueba; no requiere ventana para afirmar integración |
| Arranque y guardia | Validación de rutas/enlaces/restos/base conocida, mismo directorio rechazado por segunda guardia, identidad/propietario único, referencias cerradas. Para prueba interproceso usar sólo proceso propio sobre descartable tras autorización futura, sin matar otras JVM |
| Regresión focal existente | Nominal académico, H2 funcional y carreras H3 pertinentes; registrar selección, comandos, salida y reportes. H3-T05 si falla se informa sin bajar repeticiones/aserciones. No prometer que recuperación H4 o suite global pasará |

Los tests de presentación no necesitan heredar ServicioTaller ni mocks externos: puerto propio y ejecución controlada permiten verificar contratos. Usar latches/barreras y esperas acotadas sólo en tests, sin sleeps como prueba de causalidad; timeout falla y conserva diagnóstico. Fixtures en memoria sirven para proyección aislada, no sustituyen archivo ni prueban durabilidad. Pruebas de fallos visuales/H4 simulados acreditan bloqueo de la GUI, no reparación real de H4. No excluir ni debilitar tests históricos para anunciar éxito global.

Checklist manual Windows futuro, con evidencia visual separada de JUnit:

1. Registrar versiones reales Java/Maven/Windows, comando, ruta descartable y resultado de arranque; verificar que no se accede a `datos/operativo` real.
2. Navegar por Órdenes/Recursos/Simulación, comprobar texto H6, lectura de mensajes largos, selección y teclado; observar cinco contadores separados sin agrupar FALLIDA/CANCELADA.
3. Crear recursos suficientes y una herramienta fuera de servicio; registrar una orden antigua con tipo sin unidades elegibles y posteriores factibles, incluida una sin herramientas. Verificar secuencia, causas y primera factible.
4. Consultar dos veces sin mutar: misma propuesta y unidades disponibles. Abrir confirmación y volver: ningún inicio. Seleccionar posterior: no existe iniciar/saltar esa orden; la propuesta sigue siendo primera factible (CA-19).
5. Aceptar propuesta exacta y comprobar EN_CURSO/asignación. Iniciar después la siguiente con recursos independientes y observar dos EN_CURSO; esto acredita operación GUI, no motor H6.
6. Con una propuesta mostrada, cambiar habilitación de una unidad exacta libre y confirmar: rechazo del núcleo, sin sustitución/reservas. En otro caso habilitar sólo unidad ajena menor y confirmar la propuesta original válida: no rechazo por revisión ni reemplazo silencioso. Con antigua inicialmente imposible que se vuelve factible, confirmar posterior debe rechazar prioridad.
7. Finalizar una y fallar otra, cancelar una pendiente; verificar los cinco estados/contadores, recursos liberados sólo por cierre e historial. Repeticiones no producen segundo cierre; ocupado no admite habilitación manual.
8. En pruebas controladas de fallo, observar diagnóstico, retirada de propuesta y mutaciones bloqueadas; revisión adicional nunca sexto estado. No inducir corrupción en base real ni reabrir investigación avanzada como requisito de este checklist.
9. Durante consulta demorada, navegar/mover ventana sin congelación; cerrar con trabajo activo y verificar drenaje, ciclo final y advertencias. Tras cerrar, ningún refresco tardío reaparece. Segunda instancia sobre mismo fixture se rechaza conservando datos.

Guardar en evidencia futura logs completos, códigos de salida, reportes Surefire y capturas/checklist con resultado por punto y limitaciones. No inferir usabilidad desde tests del presentador ni rendimiento de 100 órdenes desde esta lista; CA-22/RNF-05 bajo simulación se verifican en H6/H7. No registrar métricas hipotéticas, tokens o tiempos de esta ejecución en este documento.

## 11. Alternativas, riesgos y pasos posteriores

| Decisión propuesta | Alternativa examinada | Razón / riesgo pendiente |
|---|---|---|
| Presentador, puerto y adaptador fino | Vista llama directamente ServicioTaller final | Pruebas controladas y EDT verificables sin ampliar API de negocio; mantener adaptador pequeño para evitar duplicación |
| Un trabajador operativo y refresco agrupado | SwingWorker por acción / pool operativo paralelo | Orden simple, sin colas de mutaciones humanas; no demuestra carga H6. JDBC lento puede retrasar consultas, pero EDT permanece libre |
| Captura completa tras resultado | Actualización optimista de filas/contadores | Evita éxito supuesto y mezclas; coste de lectura pendiente de observar, sin umbrales inventados |
| Propuesta aceptada exacta separada | Recalcular propuesta antes de confirmar | Preserva CA-10 y aceptación humana. Puede recibir rechazo legítimo por cambios; explicarlo |
| Entrada académica explícita en fábrica | Usar raíz real; crear mini proyecto ficticio; copiar fixture de tests | Evita base real y duplicación de propietarios; cambio mínimo de arranque requiere aprobación integral. Si no se aprueba, falta vía pública adecuada para recorrido descartable sin artificios |
| Sin control de reconciliación avanzada | Botón recuperar/desbloquear automático | Evita prometer H4 fiable o reintentar incierto. El bloqueo puede impedir continuar una sesión académica; se conserva y reporta |
| Classpath de desarrollo sin plugin nuevo | Empaquetar aplicación distribuible ahora | Suficiente para operación local H5; documentación de ruta H2 necesaria. Distribución final sigue H7 |

Riesgos de doble confirmación, respuestas cruzadas, captura vieja, excepciones sin resultado y cierre en vuelo se mitigan en §§6–8. La integridad permanece en fachada/coordinador/JDBC. Nunca se mantienen locks de presentación al pedir aplicación. Ausencia de reservas parciales no prueba ausencia universal de deadlock; starvation de pendientes imposibles o primera factible no iniciada sigue permitida en operativo. Dependencia principal: H4 puede bloquear un caso incluso si la UI es correcta; conservar evidencia y detener esa sesión, sin relajar controles.

Con una **única autorización humana integral posterior para implementar H5**, que incorpore el diseño aprobado y sus límites, el trabajo interno sería:

1. Confirmar contratos/base académica y crear arranque seguro propuesto; verificar que ninguna ruta alcanza base real y que guardia/propietario se conservan.
2. Crear puerto/adaptador, presentador y ejecución asíncrona con dobles de prueba; validar EDT, resultados y cierre antes de añadir pantallas completas.
3. Implementar recorrido mínimo en Órdenes y confirmar integración focal sobre nueva descartable, sin automatización de negocio.
4. Añadir Recursos, fallo/cancelación, cinco contadores, historial y revisión informativa; marcador H6 y mensajes completos.
5. Completar pruebas de respuestas tardías, errores y ciclo de vida; ejecutar regresiones autorizadas pertinentes, registrar cualquier fallo previo/nuevo sin confundir alcances.
6. Validar manualmente en Windows y redactar reporte H5 con archivos modificados, evidencia real, limitaciones H4 y pendientes H6/H7. Detener para revisión del incremento, sin commits/publicación automáticos.

No solicitar aprobaciones entre estos pasos internos si están contenidos en el futuro prompt integral. Sí detener ante impedimento material no contemplado: necesidad de tocar integridad/negocio/SQL, usar base real o tecnologías/dependencias nuevas, o imposibilidad del recorrido nominal sin eludir H4. Este plan no constituye autorización para comenzar ninguno de esos pasos.

## 12. Decisiones humanas para aprobar el diseño

Las PA/PT ya ratificadas no se reabren. Se someten sólo estas decisiones de frontera:

1. **Arranque académico:** ¿aprobar la entrada mínima en `FabricaContextos` y `ArranqueAcademico` para nueva base de archivo exclusivamente bajo `target/h5-*`, sin acceso al operativo real ni reapertura/migración desde GUI? Recomendación: sí; conserva guardia, autorización, registro y recuperación interna actuales, y evita depender de clases JUnit para operar. Si se exige reapertura de una base existente, debe precisarse autorización por ruta/huella y alcance antes de incorporarla.
2. **Revisión y bloqueo H4:** ¿aprobar que H5 muestre revisiones existentes y permita cerrarlas sólo mediante finalizar/fallar cuando el contexto esté sano, manteniendo diagnóstico/salida segura ante bloqueo y sin botones de reconciliación/adopción? Recomendación: sí; es el alcance representable sin prometer corregir recuperación avanzada. Si se exige recuperar desde la GUI, requiere diseño/validación específica adicional y es bloqueante para ese flujo, no para el nominal nuevo.

El resto son decisiones técnicas propuestas para revisión conjunta, sin consultas individuales obligatorias. Aprobar este diseño y emitir el prompt de implementación H5 son **instancias humanas posteriores y separadas**. El punto de detención de esta ejecución es la entrega de `IMPLEM-H5.md` v0.1, pendiente de revisión y aprobación; no se avanza a Java, H6/H7 ni aceptación global.
