# ReparaBarrio — Especificación del sistema

| Control | Valor |
|---|---|
| Versión | 0.2 refinada |
| Fecha | 13/10/2026 |
| Estado | APROBADA, con aclaración humana D016 posterior a R01 |
| Metodología y fase | SDD, fase 1: Especificación |
| Fuente | P01 v0.2 + refinamiento R01 v1.0; referencia local de P01: `prompts/P01-especificacion.md` |
| Instrucciones aplicables | `AGENTS.md` del proyecto |
| Entregable | Exclusivamente `SPEC.md` |

Esta especificación deriva del prompt P01; no sustituye ni modifica la consigna original. No constituye aprobación de alcance ni autorización para implementar. Los identificadores RF, RNF, RN, INV, CA y PA permiten trazar requisitos, reglas, invariantes, aceptación y decisiones resueltas. Los criterios describen verificaciones futuras: no se ejecutaron pruebas ni se presentan resultados empíricos.

## 1. Problema, objetivo y alcance

La asignación informal de órdenes de reparación puede provocar uso incompatible de técnicos, puestos y herramientas, recursos ociosos y esperas difíciles de explicar. ReparaBarrio debe proponer la primera orden factible por antigüedad y registrar su inicio real mediante una asignación completa, exclusiva y atómica, permitiendo seguir estados y disponibilidad.

El MVP comprende un taller local, una aplicación de escritorio para Windows con interfaz gráfica y dos modos: operativo, con datos conservados entre sesiones y acciones humanas; simulación, con datos independientes y descartables y trabajos ficticios concurrentes. Ambos deben aplicar las mismas reglas de planificación, asignación y transición. Se contempla la futura reutilización de las reglas funcionales por otros sistemas, sin realizar integraciones ahora.

Se incluyen registro y consulta de órdenes y recursos, propuestas sin reserva, confirmación, finalización, fallo, cancelación pendiente, historial, recuperación y simulaciones con estadísticas básicas. La aplicación coordina y registra; no realiza reparaciones físicas.

Se excluyen sistemas distribuidos, uso multiusuario en red, facturación, cobros, clientes, repuestos consumibles, reservas futuras, especialidades, autenticación, urgencias, prioridades, salteos manuales, optimización avanzada e integraciones externas. Esta fase excluye planificación, arquitectura, clases, tablas, módulos, algoritmos concretos de sincronización, elección de almacenamiento, código y ejecución de pruebas.

## 2. Actores y recursos

| Actor | Responsabilidades |
|---|---|
| Encargado | Registrar órdenes, configurar recursos, consultar propuestas, confirmar inicios, cancelar pendientes y consultar tablero, historial y recuperación. |
| Técnico reparador | Informar finalización o fallo de una reparación en curso. |
| Simulador automático | Generar órdenes ficticias, emitir solicitudes concurrentes, confirmar y terminar trabajos automáticamente. |

Los roles expresan responsabilidades; no implican cuentas ni controles de autenticación.

| Recurso | Identificación y uso |
|---|---|
| Técnico | Unidad identificable, por ejemplo T01; atiende como máximo una reparación en curso. |
| Puesto | Unidad identificable, por ejemplo P01; admite una reparación en curso. |
| Herramienta | Unidad física identificable y tipo, por ejemplo M01 de tipo multímetro; uso exclusivo. |

Cada orden requiere exactamente un técnico, un puesto y las herramientas necesarias expresadas por tipo. Cada recurso figura como disponible, ocupado o fuera de servicio. Se admiten cero herramientas especiales y cero o una unidad de cada tipo, combinando tipos distintos; dos o más unidades del mismo tipo quedan excluidas (PA-03). Un técnico real no representa un hilo de ejecución.

## 3. Casos de uso

| ID | Actor y caso | Entrada/precondición | Resultado esperado y alternativas | Requisitos |
|---|---|---|---|---|
| CU-01 | Encargado: registrar orden | Identificador, descripción, requisitos de herramientas | Orden PENDIENTE con secuencia única creciente e historial; datos inválidos o duplicados rechazados. | RF-01, RF-02, RF-10 |
| CU-02 | Encargado: configurar/consultar recursos | Identidad, clase, tipo de herramienta y disponibilidad | Recursos consultables; ningún recurso ocupado admite cambios manuales a DISPONIBLE o FUERA_DE_SERVICIO. | RF-03, RF-04 |
| CU-03 | Encargado/simulador: consultar pendientes y propuesta | Estado actual del modo | Primera factible con unidades concretas, sin reserva; si no existe, ausencia explicada. | RF-05, RF-06, RF-09 |
| CU-04 | Encargado/simulador: confirmar inicio | Propuesta previamente obtenida | Revalidación y asignación completa con EN_CURSO, o rechazo motivado sin cambios parciales. | RF-07, RF-08 |
| CU-05 | Técnico/simulador: finalizar o informar fallo | Orden EN_CURSO y resultado | FINALIZADA o FALLIDA, liberación e historial; repetición o transición inválida rechazada. | RF-08, RF-10, RF-11 |
| CU-06 | Encargado: cancelar | Orden PENDIENTE | CANCELADA e historial; otro estado rechazado. | RF-08, RF-10, RF-12 |
| CU-07 | Encargado: consultar tablero/historial | Órdenes y recursos del modo seleccionado | Estados, ocupación, causas de espera, contadores y cambios relevantes. | RF-09, RF-10, RF-15 |
| CU-08 | Encargado: recuperar sesión | Datos operativos previamente conservados | Recuperación consistente o error informado que impide operaciones normales; EN_CURSO tras cierre inesperado requiere revisión con asignaciones intactas. | RF-13, RF-14 |
| CU-09 | Encargado/simulador: ejecutar escenario | Órdenes, recursos, requisitos y duraciones definidos | Carga reproducible por configuración y semilla, actividad concurrente, evolución y estadísticas; termina informando pendientes bloqueadas si no hay posibilidad de progreso, sin alterar datos operativos. | RF-16 a RF-19 |

## 4. Requisitos funcionales

- **RF-01. Registro:** exigir identificador único de orden y descripción no vacía. Admitir cero herramientas especiales o una unidad de cada tipo requerido, combinando tipos distintos; rechazar cantidades superiores a una del mismo tipo y tipos inexistentes en el catálogo. Si el tipo reconocido carece de unidades registradas o elegibles, registrar la orden como PENDIENTE y explicar su falta de factibilidad. Rechazar duplicados y campos obligatorios faltantes sin sustituir registros. No reutilizar IDs operativos, ni siquiera de órdenes finales; dentro de cada modo rige unicidad independiente (PA-03, PA-04).
- **RF-02. Antigüedad:** asignar al confirmar exitosamente cada registro una secuencia única y creciente, incluso con registros concurrentes. Esta secuencia determina antigüedad y desempate y se conserva entre sesiones operativas; fecha y hora pueden mostrarse, pero no sustituyen la secuencia. Un registro rechazado no crea una orden válida ni altera la prioridad de las confirmadas (PA-01).
- **RF-03. Recursos:** registrar y consultar técnicos, puestos y unidades físicas de herramientas con identificador obligatorio y único dentro de su categoría o entidad y, para herramientas, tipo reconocido. Rechazar duplicados y datos obligatorios faltantes sin reemplazar entidades. No reutilizar identificadores operativos, aun fuera de servicio; la unicidad es independiente entre modos (PA-04).
- **RF-04. Disponibilidad:** excluir recursos OCUPADO o FUERA_DE_SERVICIO de nuevas propuestas y asignaciones. En técnicos, puestos y herramientas, rechazar todo cambio manual de OCUPADO a DISPONIBLE o FUERA_DE_SERVICIO mientras haya asignación a EN_CURSO. Permitir DISPONIBLE a FUERA_DE_SERVICIO y rehabilitación a DISPONIBLE de recursos libres. Sólo confirmación, finalización o fallo producen ocupación o liberación; solicitudes inválidas no alteran asignaciones (PA-05).
- **RF-05. Propuesta:** consultar pendientes por secuencia de registro y devolver la primera cuyos requisitos completos puedan satisfacerse. Si ninguna es factible, informar ausencia y causas. Consultar no modifica órdenes, asignaciones ni disponibilidad.
- **RF-06. Unidades propuestas:** mostrar orden, técnico, puesto y herramientas físicas concretas, aclarando que no están reservados. Elegir el disponible de menor identificador según orden natural en cada categoría o tipo requerido: sufijos numéricos comparables por valor (T2 precede a T10), con comparación textual estable cuando no sean comparables y desempate textual estable ante igual valor numérico. El criterio debe constituir un orden total estable, sin especialidades, preferencias ni optimización. A igual estado e inventario, la nueva propuesta es idéntica (PA-02).
- **RF-07. Confirmación:** comprobar atómicamente que la orden siga PENDIENTE, siga siendo la primera factible por secuencia y cada unidad exacta propuesta siga disponible y habilitada. Si no se cumple, rechazar con motivo y permitir una nueva propuesta; nunca sustituir unidades silenciosamente. Un recurso adicional o ajeno que pase a disponible no invalida por sí solo una propuesta válida que respeta prioridad, aunque una nueva consulta seleccionara otras unidades. No hay reservas temporales ni caducidad temporal. Un éxito asigna todos los recursos y cambia a EN_CURSO atómicamente, sujeto a durabilidad operativa de RF-13 (PA-06).
- **RF-08. Estados:** admitir exclusivamente las transiciones de la sección 6. Rechazar las demás sin alterar estado ni asignaciones.
- **RF-09. Consulta y espera:** mostrar estados de órdenes, recursos DISPONIBLE, OCUPADO y FUERA_DE_SERVICIO, asignaciones y causas de falta de factibilidad, incluida ausencia de unidades de un tipo reconocido. Distinguir falta de recursos de bloqueo por anterior factible. Espera o bloqueo es información de PENDIENTE, no otro estado.
- **RF-10. Historial:** conservar historial consultable de registro, inicio, asignaciones, finalización correcta, fallo, cancelación y liberación asociada al cierre. Cada cambio efectivo identifica orden, operación, momento y recursos implicados cuando corresponda, en secuencia coherente. No se exige persistir consultas, propuestas sin confirmar ni intentos rechazados; éstos se comunican comprensiblemente. En operativo, historial y cambio se conservan completos conforme a RF-13 (PA-07).
- **RF-11. Cierre de reparación:** pasar EN_CURSO a FINALIZADA o FALLIDA, liberando todos sus recursos consistentemente y registrando el cambio completo. Rechazar doble finalización, doble fallo y cierres contradictorios.
- **RF-12. Cancelación:** cancelar únicamente PENDIENTE; no generar reservas ni afectar recursos de otra orden.
- **RF-13. Conservación:** conservar y recuperar órdenes, secuencia de antigüedad, identificadores no reutilizables, catálogo de tipos, recursos, disponibilidad, asignaciones e historial operativos entre sesiones. Informar éxito sólo después de confirmar el cambio completo en almacenamiento persistente. Si la conservación falla, informar rechazo o error controlado, sin modificaciones parciales de estado, asignaciones, disponibilidad o historial. Recuperar datos coherentes tras interrupción; no imponer tecnología ni arquitectura (PA-08).
- **RF-14. Recuperación inesperada:** mantener EN_CURSO, asignaciones y recursos OCUPADO tras cierre inesperado, señalando necesidad de revisión humana sin liberar por suposición. Registrar dicha necesidad como información asociada, sin sexto estado ni nuevas transiciones; resolver mediante finalización o fallo previstos. Ante inconsistencia o error de recuperación, informar el problema e impedir continuar operaciones normales sobre datos inconsistentes (PA-08).
- **RF-15. Interfaz:** ofrecer Órdenes, Recursos y Simulación; mostrar propuestas sin reserva, rechazos motivados y cantidades separadas de PENDIENTE, EN_CURSO, FINALIZADA, FALLIDA y CANCELADA, con actualización visible durante simulaciones. No agrupar FALLIDA o CANCELADA bajo «Finalizadas»; cualquier total adicional explicita sus componentes (PA-09).
- **RF-16. Configuración de simulación:** misma configuración y semilla producen igual carga de entrada: órdenes, requisitos, llegadas previstas y duraciones configuradas. Definir recursos suficientes o limitados, alta contención y órdenes imposibles de iniciar; los fallos ficticios, si existen, se definen reproduciblemente en la configuración. No exigir tiempos reales, intercalados ni orden de finalización idénticos; no incorporar pausa, reanudación ni gestión avanzada (PA-10).
- **RF-17. Ejecución concurrente:** solicitudes y trabajos ficticios realmente concurrentes, con confirmación y cierre automáticos según reglas comunes. Permitir EN_CURSO simultáneas con recursos independientes. Si no quedan EN_CURSO ni futuras acciones capaces de liberar recursos y quedan pendientes no factibles, informarlas como bloqueadas y terminar el escenario sin espera indefinida; conservan PENDIENTE, sin estado nuevo (PA-10).
- **RF-18. Estadísticas:** mostrar procesadas = FINALIZADA + FALLIDA, excluyendo CANCELADA y PENDIENTE; espera de iniciada = instante de inicio menos registro, con unidad temporal. Espera promedio sobre las que comenzaron, sin esperas ficticias para pendientes o canceladas; sin inicios, «N/D». Duración del escenario = tiempo desde comienzo hasta terminación de simulación, con unidad. Utilización por recurso = tiempo ocupado / tiempo habilitado para uso dentro del período observado × 100; excluir FUERA_DE_SERVICIO del denominador y mostrar «N/D» si es cero. Explicitar período de observación y unidades; mostrar pendientes y fallidas separadas y canceladas cuando corresponda. Son estadísticas funcionales, no mediciones académicas de CPU y memoria, que se obtendrán posteriormente (PA-11).
- **RF-19. Aislamiento:** datos de simulación independientes y descartables; ninguna operación modifica datos operativos. IDs pueden coincidir entre modos, manteniendo unicidad dentro de cada uno (PA-04).

## 5. Requisitos no funcionales

- **RNF-01. Plataforma local:** aplicación de escritorio para Windows con interfaz gráfica, utilizable sin servicios remotos obligatorios. Versiones concretas del entorno se seleccionarán en la fase posterior correspondiente (PA-12).
- **RNF-02. Tecnología:** Java es el lenguaje requerido. Posponer herramientas concretas de construcción y pruebas, bibliotecas, bases de datos y arquitectura a planificación; evitar dependencias innecesarias. El refinamiento R01 corrige la incorporación prematura de Maven y JUnit a esta especificación, sin modificar AGENTS.md.
- **RNF-03. Atomicidad y exclusividad:** operaciones concurrentes preservan los invariantes de sección 8; ninguna consulta observa una asignación parcial como estado válido.
- **RNF-04. Durabilidad y coherencia recuperable:** éxito operativo sólo tras conservación completa; un fallo no deja modificaciones parciales. Datos recuperados respetan estados, exclusividad, asignaciones, disponibilidad e historial. Error o inconsistencia se informa e impide operar normalmente sobre datos inconsistentes (PA-08).
- **RNF-05. Interfaz utilizable:** navegación y consultas continúan durante una simulación concurrente, con actualización visible de estados y estadísticas. Escenario inicial de referencia: 100 órdenes con recursos suficientes o limitados. No se fijan tiempos máximos de respuesta, porcentajes de CPU ni rendimiento no medido; versiones y umbrales cuantitativos se decidirán en la fase posterior correspondiente (PA-12).
- **RNF-06. Determinismo verificable:** a igual estado e inventario, una nueva consulta selecciona igual primera factible por secuencia y mismas unidades por orden natural de RF-06. Validar una propuesta entregada exige las unidades exactas de RF-07 y no recalcular su selección. Reproducibilidad de entrada no exige tiempos, intercalados ni orden de finalización idénticos.
- **RNF-07. Reutilización funcional:** aplicar reglas en ambos modos y contemplar uso futuro por otros sistemas, sin exigir integración ni arquitectura; verificar equivalencia con los mismos casos de negocio.
- **RNF-08. Progreso y evidencia:** esperas sin retención parcial. Simulaciones sin posibilidad de progreso terminan conforme a RF-17. Validación futura documentará recursos compartidos, riesgos de race conditions, deadlocks y starvation y límites de evidencia; pruebas finitas no acreditan ausencia universal.

Las propiedades funcionales y cualitativas quedan definidas; las versiones y los umbrales cuantitativos se posponen, sin presentar suposiciones como mediciones.

## 6. Reglas de negocio y máquina de estados

- **RN-01:** factible significa que hay un técnico, un puesto y todas las unidades necesarias de herramientas disponibles; los ocupados y fuera de servicio no son elegibles.
- **RN-02:** se propone la primera pendiente factible por secuencia de registro. Una anterior no factible permite examinar posteriores; una anterior factible impide comenzar otra posterior.
- **RN-03:** una propuesta no reserva, no garantiza disponibilidad futura ni cambia el estado.
- **RN-04:** confirmar exige revalidación en una situación coherente con las operaciones concurrentes; se validan unidades exactas sin sustitución ni caducidad; asignación, estado e historial suceden todos o ninguno, con éxito operativo sólo después de conservación completa.
- **RN-05:** seleccionar unidades físicas mediante un orden natural estable definido en RF-06; no confundir tipos requeridos con unidades asignadas.
- **RN-06:** cada recurso exclusivo pertenece como máximo a una reparación en curso; finalizar o fallar libera sus asignaciones de forma consistente una sola vez.
- **RN-07:** fuera de servicio implica no asignable. Ningún técnico, puesto o herramienta OCUPADO admite cambios manuales a DISPONIBLE o FUERA_DE_SERVICIO; un recurso libre permite pasar entre DISPONIBLE y FUERA_DE_SERVICIO.
- **RN-08:** el MVP no admite salteo ni prioridad. Si el encargado no inicia la primera factible, las posteriores no pueden iniciarse mientras aquella siga siendo la primera factible. Cancelar sigue siendo una operación sobre pendientes, no una política de prioridad añadida.
- **RN-09:** operativo y simulación comparten reglas; sólo el segundo automatiza la duración de trabajos ficticios y su terminación.
- **RN-10:** al recuperar una sesión inesperadamente interrumpida se mantienen ocupados los recursos de órdenes EN_CURSO; se registra necesidad de revisión humana sin transiciones adicionales; finalización o fallo la resuelven. Un error de recuperación impide operar normalmente sobre datos inconsistentes.

| Estado de origen | Evento permitido | Estado destino | Efecto sobre recursos |
|---|---|---|---|
| Sin orden registrada | Registro válido | PENDIENTE | Ninguna asignación |
| PENDIENTE | Confirmación válida | EN_CURSO | Asignación completa y atómica |
| PENDIENTE | Cancelación | CANCELADA | Ninguna reserva |
| EN_CURSO | Finalización | FINALIZADA | Liberación completa y consistente |
| EN_CURSO | Fallo | FALLIDA | Liberación completa y consistente |
| FINALIZADA, FALLIDA o CANCELADA | Cualquier nueva transición | Rechazo; conserva estado | Sin cambio |

Toda transición no listada se rechaza. Consultar, proponer y marcar para revisión no son transiciones. «Esperando recursos» y «requiere revisión» son información asociada, no nuevos estados persistentes de la orden.

## 7. Entradas, salidas, restricciones y supuestos

| Operación | Entradas | Salidas esperadas |
|---|---|---|
| Registro de orden | ID, descripción, tipos de herramientas necesarios | Orden PENDIENTE registrada o motivo de rechazo |
| Registro/configuración de recurso | ID, clase, tipo si es herramienta, disponibilidad solicitada | Recurso y disponibilidad efectiva o rechazo |
| Consulta/propuesta | Modo y estado actual | Pendientes por secuencia, primera factible y unidades sin reserva, o ausencia y causas |
| Confirmación | Orden y referencia a su propuesta | Inicio con asignación completa o rechazo motivado |
| Finalización/fallo/cancelación | Orden y evento solicitado | Estado resultante, liberaciones cuando correspondan e historial, o rechazo |
| Consulta de tablero/historial | Modo y orden cuando corresponda | Estados, contadores, disponibilidad, asignaciones, causas y secuencia de cambios |
| Recuperación | Datos operativos conservados | Estado coherente y señalización de EN_CURSO para revisión tras cierre inesperado, o error informado sin operación normal sobre datos inconsistentes |
| Simulación | Configuración y semilla: cantidad y llegadas previstas, requisitos, recursos, duraciones y fallos ficticios si corresponden | Evolución concurrente, resultados y estadísticas independientes; bloqueo informado y terminación cuando no hay posibilidad de progreso |

El formato exacto de los campos, los mensajes y la representación de la referencia a una propuesta se decidirán en la fase posterior correspondiente, sin definir estructuras técnicas aquí.

Restricciones: trabajo exclusivamente local en este proyecto; ningún remoto, publicación, GitHub o acción externa. No se modifican consignas ni rúbricas. Los recursos son exclusivos, sin capacidad compartida ni consumibles; no hay especialidades técnicas. No se fija tecnología de almacenamiento ni sincronización. La futura validación deberá documentar mecanismos y recursos compartidos cuando se diseñen en su fase correspondiente.

Supuestos derivados del alcance: las reparaciones físicas se informan mediante acciones humanas en operativo; el sistema sólo conoce lo registrado. En modo operativo, una orden puede quedar pendiente indefinidamente si sus requisitos no pueden satisfacerse; en simulación se informa el bloqueo y termina el escenario conforme a RF-17; no hay obligación de agregar recursos ni prioridad correctiva. Los datos descartables de simulación no implican un requisito de persistir simulaciones. PA-01 a PA-12 son decisiones aprobadas por R01 y PA-13 es una limitación ratificada; la especificación completa sigue pendiente de validación final humana.

## 8. Concurrencia e invariantes

- **INV-01. Exclusividad:** ninguna unidad de técnico, puesto o herramienta está asignada simultáneamente a dos órdenes EN_CURSO.
- **INV-02. Completitud:** toda EN_CURSO tiene exactamente un técnico, un puesto y exactamente una unidad concreta por cada tipo requerido; si no requiere herramientas especiales, no tiene herramientas asignadas.
- **INV-03. Sin retención pendiente:** ninguna PENDIENTE ni solicitud en espera conserva reservas parciales; una propuesta no posee recursos.
- **INV-04. Correspondencia:** un recurso ocupado corresponde a una asignación de una orden EN_CURSO; una unidad disponible o fuera de servicio no tiene asignación activa. Las asignaciones históricas no cuentan como activas.
- **INV-05. Transiciones únicas:** una orden tiene un único estado válido; cada salida de PENDIENTE y de EN_CURSO ocurre como máximo una vez, y los estados finales no se reabren.
- **INV-06. Rechazo íntegro:** un rechazo no altera el estado, las asignaciones ni la disponibilidad ni el historial de cambios efectivos por efecto de esa solicitud; incluye errores de conservación sin modificaciones parciales. Otros eventos concurrentes pueden cambiar el sistema; eso no constituye un efecto parcial del rechazo.
- **INV-07. Primera factible:** cada inicio exitoso satisface la prioridad por secuencia única creciente en el momento coherente en que se acepta, aunque su propuesta fuera anterior.
- **INV-08. Cierre consistente:** finalizar o fallar elimina todas las asignaciones activas de la orden y conserva el historial; no libera recursos de otra orden.
- **INV-09. Recuperación:** una EN_CURSO recuperada mantiene la correspondencia de sus asignaciones y ocupación hasta una resolución válida.
- **INV-10. Separación de modos:** ninguna operación de simulación altera los datos operativos.

Se deben aceptar solicitudes simultáneas, preservando estos invariantes. Dos confirmaciones de la misma pendiente sólo pueden producir un inicio. Para órdenes diferentes sólo prosperan las compatibles con recursos y prioridad al aceptarse; tener recursos independientes no elimina la revalidación de primera factible. Una orden posterior puede recibir rechazo y obtener nueva propuesta después de iniciar la anterior; ambas reparaciones pueden entonces permanecer EN_CURSO simultáneamente.

En una carrera inicio/cancelación sólo una transición desde PENDIENTE puede prosperar. En una carrera finalización/fallo sólo una salida de EN_CURSO puede prosperar. Las consultas y la recuperación deben presentar estados coherentes; no se prescribe un mecanismo de sincronización. No se exige que solicitudes simultáneas terminen en un orden temporal idéntico en cada ejecución.

## 9. Criterios de aceptación trazables

Todos son escenarios futuros, sin ejecución en esta fase. Incorporan las decisiones PA resueltas por R01; no constituyen resultados de pruebas.

| ID | Condición y acción | Resultado observable exigido | Trazabilidad |
|---|---|---|---|
| CA-01 | Registrar órdenes válidas simultáneas, sin herramientas o con tipos distintos; repetir ID incluso final; omitir datos o solicitar tipo desconocido o dos unidades del mismo tipo | Una orden PENDIENTE por cada registro válido; repetición del ID rechazada sin sustituir datos; registro visible en historial; secuencias únicas crecientes. Datos faltantes, tipo desconocido y cantidad mayor a una del mismo tipo rechazados; tipo reconocido sin unidades conserva PENDIENTE con causa. | RF-01, RF-02, RF-10; INV-05 |
| CA-02 | Registrar y consultar unidades de cada clase, incluyendo fuera de servicio | Identidades únicas por categoría/entidad, tipos y disponibilidades visibles; duplicados, IDs faltantes o tipo no reconocido rechazados sin reemplazo ni reutilización operativa; una unidad fuera de servicio no aparece como candidata. | RF-03, RF-04, RF-06; RN-01, RN-07 |
| CA-03 | Pendientes A, B y C por secuencia de registro; A no factible y B/C factibles | Se propone B. Si A pasa a ser factible antes de confirmar B, confirmar B se rechaza sin efectos parciales. | RF-02, RF-05, RF-07; INV-06, INV-07 |
| CA-04 | Consultar repetidamente con varias unidades elegibles y sin cambios de estado | Misma orden y unidades mínimas por orden natural, T2 antes de T10, comparación/desempate textual estables sin sufijos comparables; todas siguen disponibles y la orden sigue PENDIENTE. | RF-05, RF-06; RNF-06; INV-03 |
| CA-05 | Confirmar propuesta vigente con recursos completos | EN_CURSO, un técnico, un puesto y todas las herramientas ocupados para esa orden; historial coherente y ninguna asignación parcial visible. | RF-07, RF-10; RNF-03; INV-01, INV-02, INV-04 |
| CA-06 | Falta una herramienta aunque hay técnico y puesto; intentar iniciar | Rechazo explicado; técnico y puesto sin reserva por ese intento, orden pendiente. | RF-07, RF-09; RNF-08; INV-03, INV-06 |
| CA-07 | Dos solicitudes simultáneas intentan iniciar la misma primera factible | Exactamente un inicio si sus condiciones permanecen válidas; la otra solicitud se rechaza; una sola asignación completa y transición. | RF-07, RF-08, RF-17; RNF-03; INV-01, INV-05 |
| CA-08 | Dos órdenes compiten simultáneamente por una única herramienta disponible, manteniendo la regla de primera factible | Sólo pueden prosperar inicios compatibles con exclusividad y prioridad; nunca ambas poseen la herramienta al mismo tiempo ni quedan reservas parciales. | RF-07, RF-17; RNF-03; INV-01, INV-03, INV-07 |
| CA-09 | Dos órdenes con conjuntos disponibles independientes; solicitudes simultáneas y nuevas propuestas tras rechazos de prioridad | Se pueden alcanzar dos EN_CURSO a la vez con unidades distintas, respetando el orden factible de cada inicio. | RF-05, RF-07, RF-17; INV-01, INV-07 |
| CA-10 | Obtener propuesta y luego ocupar/deshabilitar unidad exacta, cambiar estado de orden o habilitar anterior no factible; en otro caso habilitar sólo unidad ajena; confirmar | En los casos de pérdida de disponibilidad, de condición PENDIENTE o de prioridad: rechazo controlado sin cambios parciales y nueva propuesta posible, con validación exacta sin sustitución. Si sólo se habilita una unidad ajena y conserva prioridad y unidades exactas, la confirmación sigue válida, aunque cambie una nueva selección. | RF-04, RF-06, RF-07; INV-06 |
| CA-11 | Finalizar una EN_CURSO; en otro caso informar fallo; repetir cierre e intentar reabrir | FINALIZADA o FALLIDA respectivamente, todos los recursos liberados e historial consultable; nuevas transiciones rechazadas. | RF-08, RF-10, RF-11; INV-05, INV-08 |
| CA-12 | Cancelar PENDIENTE; intentar cancelar EN_CURSO o estado final | Sólo la pendiente pasa a CANCELADA, sin reservar; otros intentos rechazados sin liberar recursos ajenos. | RF-08, RF-10, RF-12; INV-05, INV-06 |
| CA-13 | Confirmación y cancelación simultáneas de la misma PENDIENTE | Una sola transición: EN_CURSO con asignación completa o CANCELADA sin asignación; nunca ambas. | RF-07, RF-08, RF-12; RNF-03; INV-02, INV-05 |
| CA-14 | Finalización y fallo simultáneos sobre la misma EN_CURSO, también con otros inicios concurrentes | Una salida terminal, una liberación lógica completa; nuevos inicios sólo utilizan recursos efectivamente disponibles. | RF-07, RF-08, RF-11; RNF-03; INV-01, INV-05, INV-08 |
| CA-15 | Para técnicos, puestos y herramientas intentar cambiar OCUPADO manualmente a DISPONIBLE o FUERA_DE_SERVICIO; rehabilitar recurso libre fuera de servicio | Cambios sobre OCUPADO rechazados, conservando asignación y ocupación hasta cierre; cambios sobre recurso libre entre DISPONIBLE y FUERA_DE_SERVICIO admitidos. | RF-04, RF-11; INV-04 |
| CA-16 | Cerrar y reabrir normalmente con órdenes en diferentes estados e historial | Recuperación de catálogo, IDs no reutilizables, secuencia de antigüedad, estados, disponibilidad, asignaciones e historial coherentes, con orden, operación, momento y recursos; siguiente registro recibe secuencia mayor. | RF-02, RF-10, RF-13; RNF-04; INV-04 |
| CA-17 | Interrumpir incluso durante conservación con EN_CURSO; provocar error de conservación y de recuperación | Órdenes y unidades conservadas, recursos ocupados y señal de revisión; ninguna liberación automática ni estado nuevo, revisión resuelta por cierre previsto. Éxito sólo tras conservación completa; error sin cambios parciales de estado, asignaciones, disponibilidad o historial. Error/inconsistencia de recuperación informado e impide operación normal sobre datos inconsistentes. | RF-13, RF-14; RNF-04; INV-06, INV-09 |
| CA-18 | Consultar tablero y órdenes bloqueadas durante ejecución | Secciones requeridas, propuesta identificada como no reservada, rechazos y causas visibles; espera no figura como sexto estado; cinco contadores separados por estado, sin agrupar fallidas/canceladas como «Finalizadas». | RF-06, RF-09, RF-15; RNF-05 |
| CA-19 | Primera factible A no confirmada; intentar iniciar posterior B | B no inicia mientras A conserve esa condición, aun con recursos independientes; no existe acción de salteo/prioridad. | RF-05, RF-07; RN-08; INV-07 |
| CA-20 | Repetir configuración de escenario con órdenes, recursos y duraciones definidos | Misma carga definida y mismas reglas; misma configuración y semilla generan órdenes, requisitos, llegadas previstas, duraciones y fallos ficticios iguales; no se exigen tiempos reales, intercalados o cierres idénticos. | RF-16; RNF-06, RNF-07 |
| CA-21 | Simular recursos suficientes y alta contención con solicitudes concurrentes | Evidencia de solicitudes superpuestas y, con recursos suficientes, trabajos con intervalos de ejecución superpuestos; bajo contención, esperas/rechazos sin violar invariantes. Incluir órdenes imposibles: sin EN_CURSO ni futuras acciones capaces de liberar recursos, pendientes no factibles se informan bloqueadas y termina escenario sin espera indefinida ni estado nuevo. Una animación secuencial no cumple. | RF-16, RF-17; RNF-03, RNF-08 |
| CA-22 | Ejecutar referencia de 100 órdenes con recursos suficientes o limitados y consultar interfaz/estadísticas, también sin inicios o denominador cero | Navegación y consultas utilizables, actualización visible; procesadas = FINALIZADA + FALLIDA; espera inicio menos registro con unidad; promedio sólo iniciadas o «N/D»; duración comienzo a terminación con unidad; utilización ocupado/habilitado × 100 excluyendo fuera de servicio o «N/D» con denominador cero. Período/unidades explícitos y pendientes/fallidas/canceladas cuando corresponda separadas; sin cifras no medidas ni sustituir mediciones académicas. | RF-15, RF-18; RNF-05 |
| CA-23 | Comparar datos operativos antes y después de generar y ejecutar simulación | Ningún cambio operativo atribuible a simulación, incluso con identificadores iguales entre modos permitidos. | RF-19; INV-10 |
| CA-24 | Aplicar los mismos casos de propuesta, inicio y cierre en ambos modos | Mismas decisiones de negocio ante igual estado; operativo avanza por acciones humanas y simulación admite automatización. | RF-07, RF-11, RF-17; RNF-07 |
| CA-25 | Ejecutar la futura aplicación en el Windows aprobado sin servicios remotos | Funciones básicas disponibles localmente con GUI; Java requerido; herramientas de construcción/pruebas, bibliotecas, versiones, almacenamiento y arquitectura se decidirán en planificación. | RNF-01, RNF-02 |
| CA-26 | Revisar evidencia futura de concurrencia y progreso | Se identifican recursos compartidos, riesgos y límites; no hay retención parcial en esperas ni afirmación de ausencia universal fundada sólo en pruebas finitas. | RNF-03, RNF-08; INV-03 |

## 10. Riesgos y limitaciones

| Riesgo o límite | Consecuencia que debe revisarse |
|---|---|
| Race conditions entre consulta, confirmación y cambios de disponibilidad | Propuestas obsoletas, dobles inicios o ocupaciones incompatibles si falla la revalidación atómica. |
| Carreras entre inicio/cancelación y finalización/fallo | Estados contradictorios, doble liberación o historial incoherente; deben preservarse los invariantes. |
| Deadlocks | La ausencia de reservas parciales elimina la espera circular por esa causa; otros riesgos dependerán del diseño posterior y requieren análisis, sin garantías universales basadas sólo en pruebas. |
| Starvation y requisitos insatisfacibles | En operativo puede seguir PENDIENTE sin cota de espera; en simulación RF-17 exige informar bloqueo y terminar cuando no hay posibilidad de progreso. |
| Primera factible no iniciada | Puede impedir iniciar posteriores aun con capacidad ociosa. Bloqueo de cabeza de cola aceptado y ratificado por PA-13; salteos, prioridades y postergaciones fuera del MVP como posible evolución futura. |
| Cierre inesperado durante un cambio | Riesgo a prevenir mediante RF-13 y RF-14: éxito sólo tras conservación completa, sin efectos parciales ante error; recuperación coherente o error informado que impide operación normal. |
| Registro distinto de la realidad física | La disponibilidad depende de información humana; no hay detección automática de reparaciones ni herramientas físicas. |
| Simulación concurrente | La misma carga puede producir tiempos e intercalados distintos; repetibilidad no equivale automáticamente a resultados temporales idénticos. |
| Interfaz y estadísticas sin umbrales cuantitativos medidos | Fórmulas y contadores definidos; referencia inicial de 100 órdenes. Versiones y umbrales se posponen sin inventar mediciones. |

## 11. Registro de decisiones resueltas

Las trece PA quedan resueltas por R01 v1.0. Las referencias remiten al texto normativo completo incorporado en los requisitos; no habilitan planificación ni implementación. El documento sigue pendiente de validación final humana.

| ID | Estado | Resolución aprobada | Referencias |
|---|---|---|---|
| PA-01 | APROBADA | Secuencia única creciente al registro exitoso incluso concurrente; prioridad y desempate por secuencia durable en operativo. Fecha/hora no la reemplazan; rechazo no crea orden ni altera prioridad confirmada. | RF-02; RN-02; INV-07; CA-01, CA-16 |
| PA-02 | APROBADA | Menor ID disponible por orden natural en cada categoría/tipo, T2 antes de T10 y criterio textual estable sin sufijos comparables y en desempates. Unidades físicas concretas; igual estado/inventario produce igual propuesta sin preferencias ni optimización. | RF-06; RNF-06; RN-05; CA-04 |
| PA-03 | APROBADA | Cero herramientas especiales o una unidad por tipo combinando tipos distintos; no dos del mismo tipo. Tipo inexistente en catálogo rechaza; reconocido sin unidades registradas/elegibles conserva PENDIENTE con causa. Siempre un técnico y un puesto; sin estado nuevo. | RF-01, RF-09; RN-01; INV-02; CA-01, CA-06 |
| PA-04 | APROBADA | ID único y descripción no vacía para orden; ID único por categoría/entidad de recurso y tipo reconocido de herramienta. Faltantes/duplicados rechazados sin reemplazo; no reutilizar IDs operativos finales/inactivos. Coincidencias entre modos permitidas con unicidad interna y aislamiento; sin esquemas internos. | RF-01, RF-03, RF-19; INV-10; CA-01, CA-02, CA-23 |
| PA-05 | APROBADA | Todo OCUPADO asignado a EN_CURSO rechaza cambios manuales a DISPONIBLE/FUERA_DE_SERVICIO. Libres pasan entre DISPONIBLE y FUERA_DE_SERVICIO; sólo inicio/cierre ocupan/liberan. Rechazo conserva asignaciones. | RF-04; RN-07; INV-01, INV-04; CA-15 |
| PA-06 | APROBADA | Validar atómicamente PENDIENTE, primera factible y unidades exactas disponibles/habilitadas. Incumplimiento rechaza sin sustitución y permite nueva propuesta. Disponibilidad adicional/ajena no invalida por sí sola si conserva prioridad y recursos. Sin reserva temporal ni caducidad; selección nueva distinta de validación exacta. | RF-07; RNF-06; RN-04; INV-06, INV-07; CA-03, CA-10 |
| PA-07 | APROBADA | Historial consultable de registro, inicio, asignaciones, finalización, fallo, cancelación y liberación al cierre, con orden, operación, momento y recursos cuando corresponda, secuencia coherente. Sin persistencia exigida de consultas/propuestas/intentos rechazados; comunicar rechazos. | RF-10; INV-08; CA-01, CA-11, CA-12, CA-16 |
| PA-08 | APROBADA | Éxito operativo sólo tras conservación completa; error controlado sin cambios parciales de estado, asignaciones, disponibilidad o historial. Recuperación coherente conserva EN_CURSO y ocupación, con revisión humana registrada como información resuelta por cierres existentes; sin liberar por suposición ni crear estados/transiciones. Error/inconsistencia informado e impide operación normal; sin imponer almacenamiento/arquitectura. | RF-13, RF-14; RNF-04; RN-10; INV-06, INV-09; CA-16, CA-17 |
| PA-09 | APROBADA | Contadores separados de cinco estados, sin fallidas/canceladas bajo «Finalizadas»; totales adicionales explicitan componentes. | RF-15; CA-18 |
| PA-10 | APROBADA | Configuración/semilla reproducen entrada, llegadas previstas, duraciones y fallos ficticios configurados, sin exigir tiempos/intercalados/cierres idénticos. Recursos suficientes, contención e imposibles admitidos. Sin EN_CURSO ni futuras acciones capaces de liberar recursos, pendientes no factibles informadas bloqueadas y escenario termina. Sin pausa, reanudación ni gestión avanzada. | RF-16, RF-17; RNF-06, RNF-08; CA-20, CA-21 |
| PA-11 | APROBADA | Procesadas FINALIZADA + FALLIDA; espera inicio menos registro con unidad, promedio sólo iniciadas o «N/D». Duración comienzo a terminación con unidad. Utilización ocupado/habilitado en período observado × 100 excluyendo fuera de servicio o «N/D» si denominador cero. Pendientes/fallidas/canceladas cuando corresponda separadas; no sustituye futuras mediciones académicas CPU/memoria. | RF-18; CA-22 |
| PA-12 | APROBADA | Escritorio local Windows en Java sin servicios remotos obligatorios, navegación/consultas y actualizaciones visibles durante concurrencia. Referencia inicial 100 órdenes con recursos suficientes o limitados; versiones, herramientas, bibliotecas y umbrales a fase posterior sin cifras no medidas. | RF-15; RNF-01, RNF-02, RNF-05; CA-22, CA-25 |
| PA-13 | LIMITACIÓN RATIFICADA | No confirmar primera factible impide iniciar posteriores mientras conserve esa condición aun con capacidad ociosa. Bloqueo de cabeza de cola aceptado; salteos, prioridades y postergaciones fuera del MVP como posible evolución futura sin resolver aquí. | RF-05, RF-07; RN-08; INV-07; CA-19 |

## 12. Trazabilidad y control de refinamientos

| Tema de P01 v0.2 | Especificación derivada |
|---|---|
| Problema, objetivo, actores y recursos | Secciones 1 y 2; CU-01 a CU-09 |
| Registro, consulta, historial y estados | RF-01 a RF-04, RF-08 a RF-12; sección 6; CA-01, CA-02, CA-11, CA-12, CA-18 |
| Primera factible, propuesta, confirmación y selección | RF-02, RF-05 a RF-07; RN-01 a RN-05, RN-08; CA-03 a CA-10, CA-19 |
| Concurrencia y exclusividad | RNF-03, RNF-08; sección 8; CA-07 a CA-09, CA-13, CA-14, CA-21, CA-26 |
| Datos operativos y recuperación | RF-13, RF-14; RNF-04; CA-16, CA-17 |
| GUI Windows, reutilización y tecnología local | RF-15; RNF-01, RNF-02, RNF-05, RNF-07; CA-18, CA-22, CA-24, CA-25 |
| Simulación, estadísticas y aislamiento | RF-16 a RF-19; CA-20 a CA-24 |
| Exclusiones, no salteo y límites de evidencia | Secciones 1, 7, 10 y 11; RN-08; RNF-08; CA-19, CA-26 |

Registro inicial: se transforma P01 v0.2 en esta especificación preliminar con requisitos y aceptación identificados; se conservan las reglas solicitadas y se explicitan PA-01 a PA-13 sin resolverlas por suposición. Este registro describe SPEC.md inicial v0.1; R01 v1.0 incorpora las decisiones aprobadas en v0.2 refinada. La siguiente fase queda fuera de este entregable.

## 13. Registro de refinamiento R01

Fuentes: P01 v0.2 (prompts/P01-especificacion.md), SPEC.md inicial v0.1 y R01 v1.0 recibido el 13/10/2026. Resultado: v0.2 refinada, PRELIMINAR, pendiente de validación final humana. Se conservan todos los identificadores existentes, sin agregar identificadores.

| Decisión | Resolución resumida | Principales identificadores afectados |
|---|---|---|
| PA-01 | Secuencia concurrente única y durable | RF-02; RN-02; INV-07; CA-01, CA-16 |
| PA-02 | Orden natural estable por unidad | RF-06; RNF-06; RN-05; CA-04 |
| PA-03 | Cero herramientas o una por tipo; catálogo e inventario distintos | RF-01, RF-09; RN-01; INV-02; CA-01, CA-06 |
| PA-04 | Datos obligatorios, unicidad, no reutilización y modos aislados | RF-01, RF-03, RF-19; INV-10; CA-01, CA-02, CA-23 |
| PA-05 | Sin edición manual de ocupados | RF-04; RN-07; INV-01, INV-04; CA-15 |
| PA-06 | Validación exacta sin sustitución ni caducidad | RF-07; RNF-06; RN-04; INV-06, INV-07; CA-03, CA-10 |
| PA-07 | Historial efectivo con momento y recursos | RF-10; INV-08; CA-01, CA-11, CA-12, CA-16 |
| PA-08 | Durabilidad completa y recuperación con revisión humana | RF-13, RF-14; RNF-04; RN-10; INV-06, INV-09; CA-16, CA-17 |
| PA-09 | Cinco contadores separados | RF-15; CA-18 |
| PA-10 | Entrada reproducible y fin ante bloqueo sin progreso | RF-16, RF-17; RNF-06, RNF-08; CA-20, CA-21 |
| PA-11 | Fórmulas, poblaciones, unidades y «N/D» | RF-18; CA-22 |
| PA-12 | Windows/Java, interfaz utilizable y referencia de 100 órdenes | RF-15; RNF-01, RNF-02, RNF-05; CA-22, CA-25 |
| PA-13 | Bloqueo de cabeza de cola ratificado | RF-05, RF-07; RN-08; INV-07; CA-19 |

Cambios destacados: cierre de trece PA y ajuste trazable de casos de uso, requisitos, reglas, invariantes, riesgos y aceptación. RNF-02 y CA-25 corrigen referencias prematuras a Maven/JUnit y posponen herramientas concretas a planificación sin modificar AGENTS.md. Se distingue selección nueva de validación exacta y espera operativa de terminación de simulaciones bloqueadas.

Exclusiones conservadas: sin ampliación del MVP, salteos, prioridades, postergaciones, reservas futuras, autenticación, multiusuario en red, facturación, integraciones ni gestión avanzada del simulador; sin arquitectura, código, pruebas ejecutables o PLAN.md.

Revisión textual: coherencia entre requisitos, reglas, cinco estados, invariantes, casos de uso, riesgos y aceptación y sus referencias. No se identificaron contradicciones nuevas ni asuntos funcionales adicionales pendientes de aprobación. Pendientes: validación final humana y, en fase posterior, versiones y umbrales cuantitativos deliberadamente pospuestos; no se reabren las PA.

No se ejecutaron pruebas ni se midió comportamiento de la aplicación en esta fase.

## 14. Validación humana y cierre de la especificación

**Fecha:** 13/10/2026  
**Estado:** APROBADA  
**Fase SDD:** 1 - Especificación

La especificación v0.2 fue revisada y aprobada
funcionalmente después del refinamiento R01.

### D016 - Catálogo inicial de herramientas

El MVP dispondrá de un catálogo inicial
preconfigurado de tipos de herramientas.

El encargado podrá registrar unidades físicas
de tipos existentes, pero no administrar los
tipos desde la interfaz.

Los tipos concretos se determinarán durante
la planificación.

Esta aclaración complementa RF-01 y RF-03,
sin modificar los invariantes de concurrencia.

**Trazabilidad:** esta aclaración corresponde
a una decisión humana posterior a R01.
No fue generada por Codex.

La Fase 1 SDD queda aprobada.
La arquitectura, implementación y pruebas
corresponden a las fases posteriores.