# R01 - Prompt de refinamiento de especificación | ReparaBarrio

| Control del documento | Valor |
|---|---|
| **Versión** | **1.0** |
| **Fecha** | **13/10/2026** |
| **Estado** | **BORRADOR PARA REVISIÓN, NO EJECUTADO** |
| **Metodología y fase** | Spec-Driven Development (SDD), fase 1: Especificación, refinamiento R01 |
| **Herramienta destinataria** | Codex CLI |
| **Documento de entrada** | `SPEC.md`, versión 0.1 inicial, generada a partir de P01 v0.2 |
| **Documento de salida** | `SPEC.md`, versión 0.2 refinada |
| **Objetivo** | Resolver las preguntas PA-01 a PA-13 con decisiones aprobadas, conservando trazabilidad |

## CONTEXTO Y OBJETIVO DEL REFINAMIENTO

Vamos a continuar la **fase 1: Especificación** de ReparaBarrio. El archivo `SPEC.md` ya fue generado en una primera ejecución de Codex a partir del prompt técnico `prompts/P01-especificacion.md` (versión 0.2). **No genere una especificación nueva desde cero.** Lea y refine la existente, respetando las decisiones funcionales aprobadas que se indican a continuación.

ReparaBarrio es una aplicación local para pequeños talleres que propone la **primera orden de reparación factible por antigüedad** y reserva recursos **solo cuando se confirma su inicio real**. Utiliza técnicos, puestos y herramientas físicas exclusivas, un modo operativo con conservación de datos y un modo de simulación independiente con concurrencia real.

El propósito de R01 es **cerrar las trece preguntas abiertas** de la versión 0.1 y ajustar los requisitos y criterios de aceptación afectados. No se autoriza rediseñar el producto ni ampliar el MVP.

## DECISIONES FUNCIONALES APROBADAS PARA INCORPORAR

### PA-01. Orden de llegada de registros simultáneos

- El sistema debe asignar a cada orden, al confirmar exitosamente su registro, **una secuencia única y creciente**, incluso ante registros concurrentes.
- La secuencia determina la antigüedad y el desempate; la fecha y hora pueden mostrarse, pero no reemplazan la secuencia como criterio de prioridad.
- El orden de secuencia debe conservarse entre sesiones en el modo operativo. Una solicitud rechazada no crea una orden válida ni altera la prioridad de órdenes confirmadas.

### PA-02. Selección determinista de técnicos, puestos y herramientas

- Para la propuesta se debe elegir el **recurso disponible de menor identificador según orden natural** dentro de cada clase o tipo requerido.
- La comparación debe reconocer los sufijos numéricos: por ejemplo, `T2` debe preceder a `T10`. Debe haber un criterio estable también cuando los identificadores no tengan un sufijo numérico comparable.
- La asignación debe distinguir cada **unidad física concreta** del tipo de herramienta solicitado. No incorporar especialidades, preferencias de técnicos ni algoritmos de optimización.
- Ante igual estado e inventario, una consulta debe producir la misma propuesta.

### PA-03. Cantidades y tipos de herramientas

- Permitir registrar órdenes que **no requieran herramientas especiales**; aun así necesitan exactamente un técnico y un puesto.
- Permitir cero o una unidad de **cada tipo** de herramienta por orden y combinaciones de distintos tipos. No admitir que una orden requiera dos o más unidades del mismo tipo en este MVP.
- Si un tipo de herramienta requerido no existe en el **catálogo de tipos**, rechazar el registro de la orden e informar el motivo.
- Si el tipo existe, pero no hay ninguna unidad elegible disponible o registrada, conservar la orden como **PENDIENTE** y explicar por qué no es factible. No convertir la espera en un nuevo estado persistente.

### PA-04. Identificadores y datos obligatorios

- Exigir identificador único de orden y descripción no vacía. Exigir identificador único para cada recurso de su categoría o entidad; para herramientas, registrar también su tipo reconocido.
- Rechazar duplicados y datos obligatorios faltantes, sin reemplazar registros previos.
- No reutilizar identificadores de órdenes o recursos en el modo operativo durante el MVP, aunque la entidad tenga un estado final o esté fuera de servicio.
- Los identificadores pueden repetirse **entre** modo operativo y simulación porque ambos conjuntos de datos están aislados; dentro de cada uno rigen las reglas de unicidad.
- Expresar estas reglas sin anticipar esquemas de tablas, clases o formatos internos de persistencia.

### PA-05. Cambios de disponibilidad de recursos

- Aplicar a técnicos, puestos y herramientas la misma regla de exclusión: un recurso **OCUPADO** no puede marcarse manualmente DISPONIBLE ni FUERA_DE_SERVICIO mientras esté asignado a una orden EN_CURSO.
- Se permite pasar un recurso libre de DISPONIBLE a FUERA_DE_SERVICIO y rehabilitar uno FUERA_DE_SERVICIO a DISPONIBLE.
- La ocupación y liberación derivan de la confirmación, finalización o fallo de la orden; no de ediciones manuales que eludan esas transiciones.
- Toda solicitud inválida debe rechazarse de forma controlada, sin alterar asignaciones activas.

### PA-06. Vigencia de una propuesta no reservada

- Una propuesta debe rechazarse al confirmar si **la orden dejó de estar PENDIENTE, dejó de ser la primera factible por antigüedad o alguno de los recursos concretos propuestos ya no está disponible o habilitado**.
- No sustituir silenciosamente un técnico, puesto o herramienta propuestos. Ante rechazo se debe permitir solicitar una propuesta nueva.
- La disponibilidad de un recurso **adicional o ajeno a los recursos propuestos** no invalida por sí sola una propuesta que continúa siendo válida y respeta la prioridad.
- No crear reservas temporales ni caducidades de tiempo. La comprobación y asignación efectiva deben seguir siendo atómicas al confirmar.
- Diferenciar la **selección determinista de una nueva propuesta** (PA-02) de la **validación de las unidades exactas de una propuesta ya entregada** (PA-06).

### PA-07. Historial de cambios efectivos

- Conservar un historial consultable de registro, inicio, asignaciones, finalización correcta, fallo, cancelación y liberación de recursos asociada al cierre.
- Cada cambio efectivo debe identificar la orden, operación, momento y recursos implicados cuando corresponda, con secuencia coherente.
- No exigir en el MVP persistencia de cada consulta, propuesta sin confirmar ni intento rechazado; los rechazos sí deben comunicarse de manera comprensible al solicitante.

### PA-08. Durabilidad, errores de conservación y recuperación

- En modo operativo, informar **éxito únicamente después de que el cambio completo haya quedado confirmado en el almacenamiento persistente**.
- Si la conservación falla, no informar éxito ni dejar modificaciones parciales de estado, asignaciones, disponibilidad o historial. El resultado debe ser un rechazo o error controlado.
- Ante cierre inesperado, recuperar datos coherentes. Las órdenes EN_CURSO deben permanecer EN_CURSO, con sus recursos ocupados y asignaciones conservadas, y quedar señaladas para revisión humana; **no liberar recursos automáticamente por suposición**.
- Si al iniciar se detecta inconsistencia o un error de recuperación, informar el problema y no continuar las operaciones normales silenciosamente sobre datos inconsistentes.
- Registrar la necesidad de revisión sin inventar un sexto estado de orden ni nuevas transiciones. La revisión podrá resolverse mediante las operaciones de cierre ya previstas.
- Mantener estas reglas en el plano de requisitos y garantías funcionales: **no imponer en esta fase una base de datos concreta, transacciones JDBC ni una arquitectura**.

### PA-09. Contadores de la interfaz

- Mostrar por separado las cantidades de órdenes **PENDIENTE, EN_CURSO, FINALIZADA, FALLIDA y CANCELADA**.
- No agrupar FALLIDA ni CANCELADA bajo la etiqueta ambigua «Finalizadas». Si hay totales agrupados adicionales, sus componentes deben estar expresados.

### PA-10. Reproducibilidad y finalización de simulaciones

- Repetir una misma configuración y semilla debe permitir obtener la **misma carga de entrada**: órdenes, requisitos, llegadas previstas y duraciones configuradas.
- No exigir iguales tiempos reales, intercalados de hilos u orden de finalización en todas las ejecuciones concurrentes.
- Permitir escenarios con recursos suficientes y con alta contención, incluidas órdenes que resulten imposibles de iniciar con los recursos definidos.
- Si ya no hay trabajos EN_CURSO ni futuras acciones capaces de liberar recursos y quedan órdenes no factibles, la simulación debe **informarlas como bloqueadas y finalizar el escenario**, no esperar indefinidamente.
- Los fallos ficticios, cuando formen parte del escenario, deben estar definidos por la configuración reproducible. No agregar controles de pausa, reanudación o gestión avanzada del simulador que no se solicitaron.

### PA-11. Definición de estadísticas funcionales

- **Órdenes procesadas:** cantidad que alcanzó FINALIZADA o FALLIDA; CANCELADA y PENDIENTE no cuentan como procesadas.
- **Tiempo de espera de una orden iniciada:** instante de inicio menos instante de registro; expresar unidad temporal.
- **Espera promedio:** promedio sobre las órdenes que efectivamente comenzaron; no asignar tiempos ficticios de espera a pendientes o canceladas. Si no hubo inicios, mostrar «N/D».
- **Duración del escenario:** tiempo transcurrido entre comienzo y terminación de la simulación, indicando unidad.
- **Utilización por recurso:** tiempo ocupado dividido por tiempo durante el cual estuvo habilitado para uso dentro del período de observación, multiplicado por 100. Excluir períodos FUERA_DE_SERVICIO del denominador. Con denominador nulo, mostrar «N/D».
- Mostrar también cantidades separadas de pendientes, fallidas y, cuando corresponda, canceladas. Estas estadísticas pertenecen a la aplicación y **no reemplazan** las mediciones académicas de CPU y memoria, que se obtendrán posteriormente.

### PA-12. Plataforma y comportamiento medible

- Mantener como requisito una aplicación de escritorio **local para Windows**, desarrollada en Java y utilizable sin servicios remotos obligatorios.
- Exigir que la interfaz permita continuar navegando y consultando mientras progresa una simulación concurrente; los estados y estadísticas deben actualizarse de forma visible.
- Incluir como **escenario de referencia inicial** una simulación de 100 órdenes con recursos suficientes o limitados, sin establecer por anticipado tiempos máximos de respuesta, porcentajes de CPU ni cifras de rendimiento no medidas.
- Dejar la selección de versiones concretas del entorno, herramientas de construcción, biblioteca de pruebas y umbrales cuantitativos para la fase posterior correspondiente. No presentar suposiciones como mediciones.

### PA-13. Limitación aceptada del MVP

- **Ratificar sin cambios** que, si el encargado decide no confirmar la primera orden factible, no puede iniciar una posterior mientras aquella siga siendo la primera factible, aunque existan recursos ociosos.
- Mantener fuera del alcance salteos manuales, prioridades y postergaciones. Documentar el posible bloqueo de cabeza de cola como limitación de negocio aceptada del MVP y como posible evolución futura, sin implementarlo ni resolverlo en esta fase.

## RESTRICCIONES DEL REFINAMIENTO

1. **Modificar exclusivamente `SPEC.md`** en la raíz del proyecto local. Leer `prompts/P01-especificacion.md` y `AGENTS.md` solo como contexto; **no modificarlos**. No alterar consignas, rúbricas, scripts, logs, ni registros de tokens.
2. Mantener la estructura general del documento y, siempre que sea posible, **conservar todos los identificadores existentes** `CU`, `RF`, `RNF`, `RN`, `INV`, `CA` y `PA` y sus referencias cruzadas. Actualizar las secciones afectadas sin renumerar para evitar perder trazabilidad; agregar identificadores solo si es imprescindible, con justificación.
3. Convertir la sección de preguntas `PA-01` a `PA-13` en un **registro de decisiones resueltas** con el texto preciso de la solución y estado APROBADA o LIMITACIÓN RATIFICADA, en lugar de conservarlas como preguntas abiertas sin resolver. Si surge una contradicción nueva no cubierta, identificarla claramente sin inventar una respuesta.
4. **Corregir en `SPEC.md` las referencias prematuras a Maven y JUnit** introducidas desde `AGENTS.md` en `RNF-02` y `CA-25`. En esta fase, especificar las propiedades verificables del producto y conservar Java como lenguaje requerido; posponer herramientas concretas de construcción, pruebas, bibliotecas, bases de datos y decisiones arquitectónicas a la planificación. **No modificar `AGENTS.md`**.
5. No generar arquitectura, diagramas técnicos de componentes, clases, módulos, tablas, SQL, algoritmos de locks, código, pruebas ejecutables ni `PLAN.md`. No ejecutar pruebas ni inventar resultados.
6. No agregar funcionalidades fuera del MVP, autenticación, multiusuario en red, prioridades, reservas futuras, facturación o integraciones externas.
7. Trabajar únicamente dentro de este proyecto local. **No utilizar `git push`, no agregar remotos, no ejecutar operaciones en GitHub** y no modificar otros proyectos.

## RESULTADO SOLICITADO

**Actualice y guarde `SPEC.md` como versión 0.2 refinada**, con fecha 13/10/2026 y estado **PRELIMINAR, pendiente de validación final humana**. En su control documental indique: fuente P01 v0.2 + refinamiento R01 v1.0.

Antes de concluir, verifique **sin ejecutar código** la coherencia textual entre requisitos, reglas, estados, invariantes, casos de uso, riesgos y criterios de aceptación; corrija referencias que hayan quedado inconsistentes. Mantenga explícita la distinción entre modo operativo y simulación.

Agregue al final de `SPEC.md` una sección breve **«Registro de refinamiento R01»** que incluya:

- Referencia a P01 v0.2, `SPEC.md` inicial v0.1 y R01 v1.0.
- Tabla PA-01 a PA-13 con resolución resumida y principales identificadores `RF/RNF/RN/INV/CA` afectados.
- Cambios destacados, exclusiones conservadas y eventuales asuntos nuevos pendientes de aprobación, si existieran.
- Indicación de que no se ejecutaron pruebas ni se midió comportamiento de la aplicación en esta fase.

En su **respuesta final en la conversación de Codex**, indique únicamente: archivo modificado, versión resultante, resumen de decisiones incorporadas, contradicciones o pendientes restantes y confirmación de que no generó código ni avanzó a planificación. **No escriba tokens ni métricas de consumo dentro de `SPEC.md`**: se medirán desde el registro externo de la ejecución Codex CLI.
