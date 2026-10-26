# ReparaBarrio — Propuesta técnica de implementación H3

| Control documental | Valor |
|---|---|
| Versión | 0.2 |
| Fecha | 13/10/2026 |
| Estado | PROPUESTA REFINADA, PENDIENTE DE APROBACIÓN HUMANA |
| Metodología | SDD, diseño previo dentro de la fase 3, hito H3 |
| Solicitud | `prompts/anexos/P03-D-H3.md` v0.1, leído íntegramente; autorización expresa del usuario limitada a este documento |
| Refinamiento | `prompts/anexos/R03-H3.md` v0.1, leído íntegramente; H3-D03 y H3-D04 ratificadas para H3, sin autorización de implementación |
| Base normativa | `SPEC.md` v0.2, incluida D016 y cierre humano; `PLAN.md` v0.2, PT-01 a PT-08 y adenda 8.7 |
| Continuidad | H1 y H2 aprobados; `docs/CIERRE-H2.md` y `docs/Auditoria_ReparaBarrio_H2_2026-10-13.md` |
| Entregable | Únicamente `IMPLEM-H3.md`; propuesta sin implementación ni autorización para programar |

## 1. Alcance y criterios de salida

H3 debe demostrar concurrencia real sobre el núcleo transaccional existente y una base H2 de archivo descartable. Se propone conservar el diseño que funciona y añadir pruebas controladas, trabajadores de prueba y observación acotada. Las modificaciones de producción se limitarían a documentar el límite de composición ratificado, facilitar la observación imprescindible o corregir defectos de concurrencia efectivamente detectados dentro del alcance aprobado; no se rediseñará preventivamente la composición.

| Criterio | Trazabilidad | Salida exigida a la implementación futura |
|---|---|---|
| Una confirmación para una misma orden | CA-07; RF-07/08; INV-01/05 | Dos solicitudes reales, un éxito confirmado, un rechazo y un único inicio completo durable |
| Herramienta exclusiva, sin reservas parciales | CA-08 y CA-06; INV-01/02/03/07 | Competencia compatible con prioridad; nunca dos titulares ni técnico/puesto retenidos por un intento rechazado |
| Reparaciones independientes superpuestas | CA-09; RF-17 parcial; INV-01/07 | Dos hilos trabajando fuera del coordinador, dos EN_CURSO simultáneas con conjuntos disjuntos y prioridad válida en cada inicio |
| Inicio frente a cancelación | CA-13; INV-02/05/06 | Una única salida de PENDIENTE y efectos completos del ganador |
| Finalización frente a fallo y nuevos inicios | CA-14; INV-01/05/08 | Un cierre, liberación íntegra, rechazo del cierre perdedor sin liberar la asignación de otra orden |
| Consultas coherentes | CA-05; RNF-03; RF-10 | Lectores concurrentes reciben agregado e historial compatibles; no observan mitad de inicio o cierre |
| Autoridad y progreso acotado | PLAN 5.1–5.3 y 8.3; CA-26 parcial | Una fachada/coordinador por archivo en la vía normal, interrupción de espera sin efectos y evidencia con límites explícitos |

Se complementa con registro concurrente (CA-01/RF-02), prioridad cambiante y unidades exactas (CA-03/10/19), habilitación concurrente (CA-15) y propagación del bloqueo preventivo PT-06 entre hilos. Son verificaciones del núcleo, sin ampliar funcionalidades.

Quedan fuera: recuperación y reconciliación entre procesos, guardia FileChannel, marcas de sesión y revisión humana durable (H4); GUI/EDT (H5); motor, llegadas, generación por semilla, terminación global sin progreso y estadísticas de simulación (H6); empaquetado/aceptación global (H7); mediciones académicas de CPU, memoria y tokens (H8). H3 no acredita íntegramente RF-17, CA-17, CA-21 ni CA-26. Un ensayo de 100 órdenes aquí sería carga del núcleo, no un escenario funcional completo.

## 2. Diagnóstico del código existente

La inspección estática identifica estas bases concretas:

- `ServicioTaller.ejecutar` ya adquiere `lockInterruptibly`, abre una unidad de trabajo privada, carga el dominio, valida/escribe, confirma o revierte, cierra y libera en `finally`. El resultado confirmado se construye después de `commit`; una excepción de cierre posterior conserva ese resultado con diagnóstico.
- `CoordinadorTaller` contiene `ReentrantLock(true)` y la salud/operación incierta en memoria. Su acceso actual se realiza bajo el mismo lock; no se necesita añadir `volatile` para ese uso.
- `PersistenciaH2.abrir` es sincronizado y reutiliza `ServicioTaller` en un mapa por ruta cuyo directorio padre se resuelve con `toRealPath`. Inicializa antes de publicar la fachada. La sincronización estática protege composición; no serializa todas las operaciones de las bases ya abiertas.
- `UnidadTrabajoJdbc` crea una conexión por operación, configura `TRANSACTION_SERIALIZABLE` y desactiva autocommit. La fábrica normal fija `WRITE_DELAY=0` y `LOCK_TIMEOUT=5000`; este último limita espera de locks de H2, no toda operación JDBC ni el lock Java.
- `RepositorioTallerJdbc` reconstruye el agregado; registra contador, estado, asignación e historial dentro de la transacción de la fachada. `evento` incrementa la revisión y conserva el ID de operación. `liberar` elimina herramientas de la orden y una asignación principal, con comprobación de filas de esta última.
- `InstantaneaDominio` valida exclusividad, correspondencia, completitud y unidades habilitadas. Los valores utilizan copias inmutables. `Planificador` es puro: propone sin reservar y valida contexto, primera factible y unidades exactas, sin exigir que sigan siendo las mínimas.
- El esquema SQL defiende exclusividad de técnico, puesto y herramienta, referencias y unicidad de secuencias/operaciones. No reemplaza las validaciones de prioridad ni de completitud del dominio.
- `TransaccionesH2Test` ya contiene fixtures de archivo, nueva conexión e inyección mediante los puertos. Su prueba con dos asignaciones no ejecuta trabajos superpuestos. Sus fachadas inyectadas tienen coordinadores propios: se usan como ensayos aislados; no deben convertirse en clientes concurrentes alternativos del mismo archivo.
- `pom.xml` ya declara Java 21, H2 2.3.232, JUnit Jupiter 5.11.4 y plugins compiler/Surefire. No se propone agregar dependencias ni cambiar versiones.

La auditoría y el cierre documentan 34 pruebas previas aprobadas. Son antecedentes atribuidos a esos documentos; no se volvieron a ejecutar en esta instancia. No hay evidencia nueva de carreras, rendimiento o ausencia de deadlock.

## 3. Componentes y cambios propuestos

| Componente existente o futuro | Propuesta y responsabilidad |
|---|---|
| Dominio y planificación H1 | Conservar entidades, estados, selecciones, instantáneas, comparador y planificador. No reconstruir ni introducir inventario mutable paralelo |
| `ServicioTaller.java` (producción, modificación prevista) | Conservar operaciones y frontera transaccional; instrumentar `ejecutar` según 4.4 y documentar inyección exclusiva de pruebas. Corregir solamente defectos revelados por los ensayos |
| `CoordinadorTaller` | Conservar lock justo y salud compartida; documentar que todos los accesos de negocio/diagnóstico pasan por él. No crear locks por recurso |
| `PersistenciaH2.java` (producción, conservar lógica) | Mantener `abrir` y comprobar reutilización según identidad actual; precisar documentación si afirma unicidad universal. No ofrecer reapertura que evada salud |
| Puertos y adaptadores JDBC/esquema | Conservar contratos, aislamiento y restricciones. Usar decoradores exclusivamente de prueba sobre JDBC real para detener/fallar una operación en fronteras conocidas |
| `ConcurrenciaH3Test.java`, nuevo en `src/test/java/ar/edu/unlam/reparabarrio/persistencia/` | Suite del hito H3: fixtures de archivo y matriz de carreras, lecturas y persistencia; acceso a adaptadores del paquete para instrumentar ensayos |
| `CoordinadorConcurrenciaTest`, futuro en `src/test/java/.../aplicacion/` | Adquisición interrumpible, liberación y medición interna sin exponer el lock a clientes |
| Ayudas de prueba, preferentemente internas a las suites | Ejecutor de cuatro trabajadores, barreras/latches, trazas, decoradores transaccionales y oráculo de historial. Crear clases separadas sólo si hay reutilización real |

No crear `MotorSimulacion`, `TrabajoSimulado` de producción, nueva fábrica general de contextos ni servicios de recuperación en H3. Un trabajador de prueba consulta, confirma, espera una señal externa y cierra por la fachada; no incorpora reglas ni reintentos automáticos indefinidos.

H3-D03 está ratificada: la vía normal usa `PersistenciaH2.abrir`, compartiendo fachada/coordinador según la identidad que hoy resuelve. La inyección se limita a fixtures exclusivos, sin fachadas alternativas concurrentes sobre la misma base. El constructor público y los límites de normalización impiden afirmar unicidad universal; H4 consolidará identidad, ciclo de vida y guardia entre procesos. No se restringirá preventivamente el constructor ni se agregará otro registro en H3.

Los archivos de producción de `dominio/`, `planificacion/`, puertos, `InstantaneaTaller.java`, `ResultadoOperacion.java`, `UnidadTrabajoJdbc.java`, `RepositorioTallerJdbc.java`, `esquema.sql` y `pom.xml` se prevén conservados. `CoordinadorTaller.java` conservará lock y salud; sólo podría precisar documentación. `CoordinadorConcurrenciaTest.java` será nuevo en el paquete de prueba `aplicacion`, donde podrá activar observación interna. Se conservarán las suites H1/H2. Decoradores, señales, recolector y oráculo serán ayudas anidadas; extraer archivos de prueba es opcional sólo ante reutilización real. Los tipos mínimos de observación serán internos a `ServicioTaller`, sin nuevo módulo ni dependencia.

### 3.1 Interacciones de los trabajadores de prueba

El controlador inicializa órdenes/recursos con `registrarOrden` y `registrarRecurso`. Cada trabajador usa `consultar()` y comprueba `CONFIRMADA` antes de tomar el `Optional` de `valor().planificacion().propuesta()`; sólo si está presente extrae la `PropuestaInicio`. No existe una operación pública separada de propuesta. Una barrera exterior reúne a los contendientes antes de `confirmarInicio(propuesta)`. En T01 ambos reciben deliberadamente la misma propuesta; un rechazo no dispara reintentos automáticos.

En T03, después del inicio confirmado de A, su trabajador registra inicio del trabajo y señala un latch. El trabajador de B espera esa señal fuera de la fachada, consulta de nuevo y confirma B. Ambos señalan trabajo iniciado y esperan permiso de cierre fuera del coordinador. El controlador espera ambas señales, llama a `consultar()` para acreditar dos EN_CURSO y libera los cierres; los trabajadores registran fin del intervalo y llaman a `finalizar(id)` o `fallar(id)`. T04/T05 sitúan la barrera exterior antes de `cancelar`, confirmar o cerrar; las variantes con precedencia se controlan mediante decoradores de unidad de trabajo/repositorio en las fronteras señaladas por la matriz. T06 señala desde el decorador que la escritura está detenida y desde los lectores que van a consultar; el controlador libera la pausa sin consultar mientras posee el lock el escritor. Todas las señales tienen timeout y liberación de emergencia. El intento anunciado por un lector no prueba por sí solo que ya esté encolado en el lock.

## 4. Estrategia de concurrencia

### 4.1 Frontera atómica y lecturas

Mantener el orden aprobado: coordinador → nueva conexión/transacción → lectura → validación → escrituras → commit/rollback → cierre → liberación. No abrir la conexión antes de adquirir el coordinador ni compartirla entre hilos. Ningún repositorio adquiere otro coordinador. Lecturas por `consultar` usan esa misma frontera; su finalización mediante rollback de lectura actual se conserva.

El commit confirmado es el punto de linealización de una mutación exitosa. Un rechazo se decide sobre el agregado protegido; una consulta captura un estado coherente bajo exclusión. El instante de retorno al cliente no ordena los commits: después de soltar el lock otro hilo puede completar primero su retorno. La revisión/historial persistidos ordenarán los cambios en las pruebas.

No ejecutar barreras de trabajo, esperas de futures, callbacks externos, temporizadores ni escritura de trazas a disco dentro de la sección crítica de producción. Para ensayos controlados se permiten pausas acotadas en decoradores de prueba; no son esperas añadidas a la aplicación. El controlador de esas pausas nunca solicita el coordinador para poder liberarlas.

### 4.2 Prioridad, exactitud y exclusión

Toda confirmación vuelve a evaluar la primera factible sobre el estado actual y verifica el conjunto exacto. Una propuesta antigua sigue válida si sólo aparece una unidad ajena menor y no cambian su prioridad ni sus unidades. No comparar revisión global, introducir caducidad, sustituir unidades ni reservar entre consulta y confirmación.

Dos peticiones simultáneas pueden obtener la misma propuesta A. Sólo una inicia A; el perdedor recibe rechazo y solicita una nueva propuesta cuando el ensayo lo indique. Para iniciar B, A ya debe haber dejado de ser primera pendiente factible. Si hay capacidad independiente, B inicia sin esperar al cierre de A. La prioridad serializa decisiones de inicio, no la duración de las reparaciones.

Una posterior B con conjunto físicamente distinto tampoco inicia mientras A siga siendo primera factible. Para comprobar competencia por herramienta única no se fingirá que la consulta normal entrega simultáneamente propuestas válidas para A y B: una propuesta de B construida deliberadamente para el ensayo es un intento inválido de prioridad, no una propuesta emitida por la fachada.

Al cerrar, el agregado cargado ya valida que la EN_CURSO tenga su conjunto íntegro. Estado terminal, eliminación de asignaciones e historial con recursos se conservan juntos. El cierre perdedor revalida el estado y no borra recursos que un nuevo inicio haya adquirido. La ocupación se deriva de asignación activa y habilitación, nunca de un contador paralelo.

### 4.3 Progreso y salud

La adquisición justa reduce adelantamientos de hilos, pero no garantiza tiempo máximo ni elimina starvation del negocio. Una orden imposible permanece PENDIENTE sin recursos; una primera factible no confirmada bloquea posteriores conforme a RN-08/PA-13. H3 no añade colas de espera por recurso, `Condition`, sondeo permanente ni política de salteo.

Una interrupción mientras se espera el lock retorna `ERROR_TECNICO`, restaura el indicador del hilo y no abre transacción. No se promete que `Thread.interrupt` cancele instantáneamente una llamada JDBC ya iniciada. Los timeouts de ensayo no se convierten en resultados de negocio ni autorizan liberar una EN_CURSO.

Un commit incierto o rollback no acreditado marca salud bajo el coordinador; las siguientes solicitudes del mismo contexto retornan `PENDIENTE_VERIFICACION`, sin abrir otra transacción ni presentar datos como confirmados. Se libera el lock y otro contexto sano puede continuar. No reintentar, compensar ni reconciliar en H3. La interpretación normativa de PT-06 ya fue ratificada en PLAN 8.7: no es una duda normativa abierta, aunque su recuperación durable esté pendiente.

### 4.4 Observación de latencia

H3-D04 ratifica la instrumentación interna opcional. Se elige un observador y una muestra inmutable anidados en `ServicioTaller`, con un constructor auxiliar de acceso de paquete para pruebas de `aplicacion`. El constructor público existente mantiene observación desactivada; el observador se fija al componer el fixture, sin setter concurrente ni modificación de la salud. Las pruebas de medición usarán esa composición exclusiva; la suite de persistencia conserva la vía normal para las carreras sin fallos inyectados. No se expone el lock ni se agregan hooks de negocio.

Con observación activa, `ejecutar` captura `System.nanoTime()` inmediatamente antes de `lockInterruptibly` (t0), inmediatamente después de adquirirlo (t1) y después del cierre de la unidad de trabajo, justo antes de `unlock` (t2). Espera = t1 − t0; posesión aproximada = t2 − t1. La segunda incluye conexión, validación, SQL, commit/rollback y cierre; excluye el pequeño tramo hasta completar `unlock`. Son intervalos monotónicos en nanosegundos, afectados por planificación del SO y costo de captura, no tiempo CPU ni duración de reparación. Si la espera se interrumpe, se captura su fin, se informa adquisición fallida y posesión ausente, nunca cero como si hubiera adquirido. Se conserva el indicador de interrupción.

En el `finally`, la liberación queda garantizada antes de construir/entregar la muestra mediante un bloque de limpieza exterior. Así se observa también el retorno anticipado por `PENDIENTE_VERIFICACION`; una ruta sin adquisición nunca hace `unlock`. La muestra contiene sólo contexto, ID de intento cuando exista, ID/nombre de hilo, adquisición y duraciones primitivas; consultas no necesitan inventar un ID de negocio. No contiene conexión, repositorio, salud ni referencias mutables. Construcción, entrega y cualquier diagnóstico de observación quedan aislados de las salidas de negocio: un fallo del observador se descarta sin sustituir resultado o excepción, sin reintento y sin cambiar el indicador de interrupción. Un fallo fatal de JVM queda fuera de esta garantía acotada.

El recolector interno de prueba hace una oferta no bloqueante a una cola de capacidad fija después de liberar el coordinador; si está llena descarta la muestra. El controlador drena y calcula distribuciones/escribe trazas fuera de las operaciones; reporta capacidad y muestras descartadas, sin presentar cobertura completa si hubo pérdidas. No invoca la fachada desde el observador. La entrega puede intercalarse con operaciones posteriores: no ordena commits. No se admiten I/O, esperas, adquisición de otro lock ni callbacks bajo el coordinador.

Costo previsto, pendiente de medición: desactivada, comprobación de configuración sin marcas ni muestras; activa, tres lecturas monotónicas por adquisición exitosa (dos si interrumpida), variables locales y, poslock, una muestra y una oferta de costo acotado. No se estiman nanosegundos ni porcentajes sin ejecutar. La latencia total de consulta se mide exteriormente; tiempos JDBC adicionales son complementarios mediante decoradores, sin confundirlos con espera/posesión. Las pruebas futuras de observación compararán resultados con/sin observador, observador que falla, retorno por salud bloqueada, interrupción y cola llena; su semántica aún está pendiente de verificación.

## 5. Verificación propuesta

### 5.1 Infraestructura y oráculo

Cada caso tendrá archivo exclusivo bajo `target`, ruta absoluta y fachada normal compartida por todos sus trabajadores. Inicializar el fixture antes de lanzarlos. Para fallos instrumentados usar una única fachada inyectada y un único coordinador sobre otro fixture exclusivo; no operar simultáneamente con la fachada normal de ese archivo. Las conexiones auxiliares sólo leerán al terminar las operaciones o estando el ensayo detenido en un punto seguro sin escrituras; no serán un camino alternativo de negocio.

Usar `ExecutorService` de cuatro trabajadores iniciales, `CountDownLatch`/`CyclicBarrier` y futures con tiempos acotados. El controlador externo no consumirá un trabajador necesario para alcanzar una barrera. Recoger todas las excepciones de las tareas. Siempre liberar señales en `finally`, cancelar tareas pendientes y cerrar el ejecutor con espera acotada; si no termina, fallar y conservar diagnóstico.

La barrera de salida prueba solicitudes solapadas, no garantiza qué hilo adquiere primero. Cada carrera tendrá ensayos libres y variantes con precedencia forzada por señales antes de adquisición o decoradores dentro de la transacción. No esperar que los dos contendientes alcancen simultáneamente una barrera dentro del lock: eso produciría un deadlock artificial. En un ensayo detenido dentro del lock, el controlador libera sin necesitar una consulta de la fachada.

Tras cada frontera controlada y al final, comprobar por consulta coherente y, después de detener escritores, nueva conexión JDBC: estados, asignaciones, requisitos/tipos, habilitación, recursos ocupados, contador de antigüedad, revisión e historial. Añadir un oráculo de prueba independiente que recorra eventos por secuencia, reconstruya titulares por unidad y compruebe prioridad de cada INICIO usando la configuración y cambios confirmados. No confiar sólo en que el constructor del dominio no lanzó excepción.

Un rechazo concurrente no obliga a que la instantánea global anterior y posterior sea idéntica: otros hilos pueden confirmar cambios. Atribuir efectos por `operacionId`, filas y eventos; el intento rechazado no debe tener evento ni asignación propios. Comparar instantáneas completas sólo cuando todos los otros mutantes estén detenidos. No confundir secuencia de registro con revisión del historial.

### 5.2 Matriz de ensayos

**Imprescindibles para cerrar H3:** T01–T06 (CA-07/08/09/13/14 y lecturas coherentes CA-05), T08 (prioridad/unidades exactas/habilitación concurrente), T09 (composición dentro del límite D03), T10 (interrupción y progreso acotado) y T11 (salud PT-06 entre hilos), más los controles de instrumentación de 4.4 y regresión H1/H2. La revisión de riesgos, trazas y límites aporta CA-26 parcial; ninguna prueba finita lo acredita universalmente.

**Complementarias:** T07 refuerza el registro concurrente ya cubierto funcionalmente en H2; T12 caracteriza carga/latencia con 100 órdenes y capacidad suficiente/limitada. Se conservan ambas en la matriz y en la secuencia, pero su caracterización no es requisito de cierre H3 ni reemplaza CA-21/22 o el escenario funcional del MVP posterior. Si se ejecutan y revelan defectos de atomicidad/exclusión, éstos bloquean el cierre aunque el ensayo sea complementario. Toda la matriz permanece pendiente de ejecución.

| ID futuro / trazabilidad | Intercalado y observaciones válidas | Resultado que falla el ensayo |
|---|---|---|
| H3-T01 / CA-07 | Dos confirmaciones de la misma propuesta A con barrera; repetir forzando ambos ganadores. Un `CONFIRMADA`, un `RECHAZADA`, un INICIO, una asignación íntegra | Dos éxitos, error SQL por carrera normal, doble evento o efecto del perdedor |
| H3-T02 / CA-08/06/19 | A y B requieren M única, con dos técnicos/puestos. Intentos simultáneos A y B; B no saltea. Mientras A trabaja, nueva consulta no puede iniciar B; tras cerrar A, nueva propuesta permite B | M compartida, B adelantada, técnico/puesto reservados para B pendiente |
| H3-T03 / CA-09/19 | Dos conjuntos suficientes, preferentemente herramientas de tipos distintos. Intentar B mientras A es primera y acreditar rechazo; iniciar A, obtener nueva propuesta B e iniciar B desde otro trabajador. Ambos notifican trabajo iniciado y esperan señal exterior; controlador consulta dos EN_CURSO antes de habilitar cierres | B inicia antes que A, asignaciones intersectadas, una reparación espera el cierre de la otra, intervalos sin superposición |
| H3-T04 / CA-13 | Confirmar A frente a cancelar A; forzar ambos órdenes. Ganador único: EN_CURSO con conjunto completo o CANCELADA sin conjunto; evento único de salida de pendiente | Inicio y cancelación efectivos, liberación ajena o resultado parcial |
| H3-T05 / CA-14/11 | A EN_CURSO; finalizar y fallar simultáneamente, con otro hilo intentando iniciar B. Consultar antes/después de liberar; nueva propuesta B tras cierre. Después de iniciar B, repetir cierres de A | Dos cierres, terminal contradictorio, B usa recursos aún ocupados, repetición de A libera B |
| H3-T06 / CA-05 | Detener inicio tras cambio de estado o asignación real y antes de commit; lanzar lectores y comprobar que intentan consultar y aún no retornan. Liberar y verificar sólo agregado completo. Repetir con rollback y cierre detenido entre deletes | Lectura parcial, historial incompatible, un lector retorna datos sin commit de la operación detenida |
| H3-T07 / CA-01/RF-02 | Registros simultáneos distintos y duplicados; ordenar éxitos por secuencia persistida, verificar unicidad/incremento y rechazo sin registro/historial ni aumento del contador atribuible | Secuencia repetida, registro reemplazado, orden de prioridad derivado del orden de retorno |
| H3-T08 / CA-03/10/15 | Habilitar recurso que vuelve factible a A antes no factible frente a confirmar propuesta B; forzar ambos órdenes. También deshabilitar unidad exacta frente a inicio y habilitar sólo unidad ajena menor | Prioridad omitida, sustitución de exacta, unidad ocupada inhabilitada, rechazo por revisión o por unidad ajena |
| H3-T09 / O-H2-03 | Abrir concurrentemente la misma ruta y alias con segmentos normalizables; misma instancia por identidad y comportamiento. Mutaciones/consultas usando esas referencias comparten autoridad | Fachadas distintas en la vía normal o salud divergente para la misma base |
| H3-T10 / PT-03, CA-26 parcial | Una transacción detenida mantiene el lock; otro hilo intenta entrar y se interrumpe. Verificar indicador, ERROR_TECNICO y ninguna apertura para él. Liberar al dueño y ejecutar otra solicitud | Conexión del interrumpido, efectos persistidos, lock no liberado o nueva operación impedida |
| H3-T11 / PT-06 parcial | Decorador real produce excepción de commit antes/después del delegate, con solicitudes ya en espera sobre el mismo coordinador. Todas posteriores ven mismo ID incierto sin nuevas aperturas; otra base progresa | Éxito supuesto, rechazo definitivo del incierto, reintento ciego o bloqueo del otro contexto |
| H3-T12 / O-H2-05, CA-26 parcial | Carga acotada del núcleo con 100 órdenes, recursos suficientes y limitados; incluir orden imposible. Registrar esperas, posesión y latencia, validar todos los commits y pendientes sin reservas | Invariante rota, excepción no recogida, timeout tratado como éxito o garantía universal deducida del ensayo |

Para H3-T03 conservar ID/nombre de ambos hilos y tiempos monotónicos de inicio/fin del trabajo exterior. Comprobar `max(inicios) < min(fines)` y una instantánea tomada mientras ambos trabajadores están detenidos fuera del coordinador con dos EN_CURSO. No basta ver dos filas activas, usar dos tareas sin comprobar su ejecución o inferir simultaneidad por sleeps.

En H3-T02/T05 los intentos de inicio anticipados pueden rechazarse legítimamente; después de un cierre confirmado se vuelve a consultar. No exigir que sobreviva una propuesta obtenida antes del cierre ni reintentar hasta que una prueba deje de fallar. En las variantes de precedencia, especificar exactamente qué estado permite cada resultado.

### 5.3 Parámetros, evidencia y límites

Adoptar como punto de partida de PLAN 8.2 cincuenta repeticiones para carreras y diez segundos de presupuesto por caso controlado, ajustables al entorno y registrados. Las repeticiones no multiplicarán inadvertidamente un timeout total de diez segundos: documentar presupuesto por repetición y máximo global de la suite/carga. No son objetivos de rendimiento ni límites funcionales del producto.

Un timeout falla el ensayo y captura pilas de hilos, operaciones, señales pendientes, tiempos y ruta del fixture. Un `Future.get` vencido no demuestra deadlock por sí solo; distinguir espera del controlador, JDBC, executor y lock. Si una tarea JDBC no responde a cancelación, no borrar su archivo mientras siga activa ni asumir rollback. El límite del proceso de pruebas se usará sólo como protección de ejecución futura, sin atribuirlo a recuperación H4.

La evidencia futura incluirá reportes JUnit/Surefire, comando y código de salida, JDK/Maven/H2/Windows, parámetros y repeticiones, trazas con contexto/hilo/operación/revisión/resultado, instantáneas y comprobaciones JDBC, intervalos superpuestos y diagnósticos de fallos. Medidas de espera/posesión se reportarán con unidad, cantidad de muestras y distribución observada; no se fijan ahora valores ni porcentajes. Volcar archivos después de liberar el coordinador. Mantener diagnósticos de ensayo separados del historial de negocio.

La futura validación ejecutará primero las pruebas específicas y luego `mvn.cmd -B -ntp clean test` para regresión completa H1/H2/H3; esos comandos no se ejecutaron aquí. No fijar un total nuevo de pruebas antes de implementarlas. Pruebas finitas y revisión del orden de locks sustentan evidencia acotada; no certifican ausencia universal de race conditions, deadlocks o starvation.

## 6. Riesgos, alternativas y estado de decisiones

| Decisión propuesta | Alternativas y justificación | Riesgo o revisión humana |
|---|---|---|
| H3-D01: conservar coordinador único justo y transacciones completas | Locks por recurso agregan orden de adquisición y reservas parciales; control optimista/read-write obliga a rediseñar prioridad y lecturas. PT-03 ya aprueba la opción conservada | Disco lento retiene el coordinador. No sustituir estrategia sin evidencia y aprobación |
| H3-D02: trabajadores sólo de prueba, pool inicial de cuatro | Motor mínimo de producción adelantaría H6 y ciclo de vida aún pendiente; dos o más trabajadores bastan para demostrar solapamiento | Evitar barreras que requieran más tareas activas que el pool y controladores en el mismo pool |
| H3-D03 APROBADA para H3 por R03-H3: composición normal por `PersistenciaH2.abrir` | Inyección sólo para fixtures exclusivos; sin clientes alternativos concurrentes sobre la misma base ni rediseño preventivo | Identidad según resolución actual, sin unicidad universal ante constructor público, alias/caso/enlaces; H4 consolida identidad, ciclo de vida y guardia |
| H3-D04 APROBADA para H3 por R03-H3: observación interna opcional, poslock | Diseño concreto elegido en 4.4; decoradores solos no separan espera/posesión | Desactivada por defecto; entrega inocua después de liberar. Precisión, costo y preservación semántica pendientes de pruebas |
| H3-D05: decoradores de prueba y precedencia controlada | Sleeps solos son frágiles; hooks generales de producción ampliarían API sin necesidad | Sólo fixtures reales exclusivos, señales acotadas; distinguir ensayo forzado del recorrido normal |
| H3-D06: correcciones sustentadas por defectos H3 | Rehacer H2 preventivamente perdería continuidad sin defecto probado | Un defecto de asignación bloquea aprobación H3; cambios a contratos/tecnologías/esquema fuera del alcance requieren revisión antes de aplicarse |

H3-D03/H3-D04 están ratificadas; H3-D01/D02/D05/D06 conservan su carácter de propuesta dentro de las PT aprobadas y requieren aprobación del documento completo, sin nueva duda normativa específica. No se reabren PT-01 a PT-08 ni se identifica un conflicto nuevo que exija otra decisión humana. Persisten el límite de composición y la falta de evidencia empírica. La adenda 8.7 resuelve la antigua observación normativa PT-06, aunque los apartados históricos conserven su redacción previa.

| Observación H2 | Tratamiento en H3 | Pendiente posterior |
|---|---|---|
| O-H2-01 | Ensayo de salud compartida en la JVM, sin afirmar recuperación durable | H4: diagnóstico/ID accesibles tras caída y reconciliación |
| O-H2-02 | Lectura de archivo con nueva conexión para verificar commits; no llamarla reinicio | H4: JVM independiente, caída y guardia |
| O-H2-03 | Unicidad de fachada normal y prohibición de composición alternativa concurrente | H4: identidad estable, autoridad y guardia entre procesos |
| O-H2-04 | Matriz de carreras y trabajo superpuesto con hilos reales | Aprobación humana tras resultados reales H3 |
| O-H2-05 | Medir latencia/lock sin optimización prematura; fixtures acotados | H4/H6: cierre/eliminación de contextos y ciclo de vida |

El mapa estático mantiene referencias a fachadas de fixtures aunque cierren sus conexiones. Usar número acotado de bases y proceso de pruebas finito; no introducir limpieza del registro que permita evadir un contexto incierto. La gestión definitiva de ciclo de vida se mantiene en los hitos indicados.

## 7. Secuencia sugerida para una ejecución posterior autorizada

1. Revisar y aprobar esta propuesta refinada completa, con H3-D03/H3-D04 ya ratificadas. Preparar autorización separada de implementación con archivos/criterios previstos; conservar documento y prompts como trazabilidad.
2. Ejecutar la línea base H1/H2 sobre el estado aprobado y registrar resultados reales. Si falla, identificar causa antes de atribuirla a H3; conservar las pruebas existentes.
3. Añadir fixtures y ayudas de prueba, verificar identidad de fachada, adquisiciones interrumpidas y ausencia de conexiones compartidas. Incorporar observación mínima ratificada sin cambiar semántica transaccional.
4. Implementar y ejecutar doble inicio, herramienta única, prioridad/exactitud y lectores durante commit/rollback. Si aparece un defecto, corregirlo dentro del alcance y repetir los casos afectados y regresión correspondiente.
5. Añadir inicio/cancelación, finalización/fallo y reasignación posterior; después demostrar dos trabajos superpuestos con nueva propuesta y dos EN_CURSO durables.
6. Verificar salud PT-06 y observación entre hilos; ejecutar repeticiones imprescindibles. Añadir T07/T12 como complementarias, registrando su ejecución u omisión y latencia sin umbral inventado.
7. Ejecutar regresión completa; revisar trazabilidad y evidencia, documentar limitaciones y pedir aprobación humana de H3. Transferir pendientes a H4 sin iniciar recuperación, GUI ni simulador.

Condición de cierre: todas las propiedades H3 de la sección 1 respaldadas por las pruebas imprescindibles de 5.2 ejecutadas, observación verificada, ningún defecto de atomicidad/exclusión pendiente y regresión aprobada; informar cobertura complementaria y límites. No declarar el MVP completo ni aptitud operativa final.

## 8. Evidencia obtenida en esta instancia y detención

Se leyó íntegramente P03-D-H3 y se inspeccionaron SPEC, PLAN, código de dominio/planificación/aplicación/persistencia, puertos, esquema, pruebas de integración, pom y documentos de auditoría/cierre H2. Las conclusiones sobre mecanismos existentes son estáticas; los resultados anteriores se atribuyen a sus documentos originales. No se ejecutaron compilación, JUnit, benchmarks ni experimentos concurrentes.

Registro R03-H3 (13/10/2026): v0.1 → v0.2. Se leyó íntegramente R03-H3 y se revisaron los documentos normativos, el prompt original y las operaciones existentes necesarias. Se precisaron impacto en archivos e interacciones (3), diseño de observación (4.4), prioridades de verificación sin alterar T01–T12 (5.2), estado de H3-D03/H3-D04 ratificadas (6) y secuencia/cierre (7). La suite futura se denomina `ConcurrenciaH3Test`. Se conserva el diagnóstico estático y la atribución histórica H1/H2, sin nuevos resultados experimentales.

Sólo se refinó este documento. No se implementaron cambios Java/SQL/pruebas/configuración, ni se alteraron contratos, otros documentos o consignas. No se ejecutaron compilación, tests, benchmarks ni experimentos. No se hicieron commits ni operaciones externas. La consulta de estado Git fue rechazada por propiedad del repositorio (`dubious ownership`); no se cambió configuración para resolverlo ni se afirma una verificación Git satisfactoria.

La autorización actual termina con esta propuesta. La implementación de H3 requiere aprobación y autorización humana posterior y separada.
