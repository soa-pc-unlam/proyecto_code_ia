# P03-H6 — Implementación integral del hito H6 | ReparaBarrio

| Control documental | Valor |
|---|---|
| Versión | **0.1 — APROBADA** |
| Fecha | 15/10/2026 |
| Estado | **APROBADO Y AUTORIZADO PARA IMPLEMENTACIÓN INTEGRAL H6 EL 15/10/2026** |
| Fase | Spec-Driven Development (SDD), P03 — implementación incremental, H6 |
| Destinatario futuro | Codex CLI, en el **repositorio local original** de `APO1-ReparaBarrio` |
| Diseño autorizado | `IMPLEM-H6.md` v0.1, aprobado posteriormente por decisión humana |
| Decisiones obligatorias | `docs/APROBACION-DISENO-H6.md` (H6-D01 y H6-D02, ambas aprobadas **con restricciones**) |
| Auditoría | `docs/AUDITORIA-DISENO-H6-v0.1-2026-10-15.md` (dictamen favorable con condiciones, si está copiada al repositorio) |
| Base funcional | `SPEC.md` v0.2 aprobada; `PLAN.md` v0.2 aprobado, PT-01…PT-08 y §8.7; `AGENTS.md` |
| Resultado esperado de la ejecución autorizada | Simulación H6 funcional, contexto H2 aislado, motor concurrente, estadísticas, pestaña Swing, tests JUnit y reporte de implementación |
| Excluido | Reparación avanzada H4, empaquetado/aceptación global H7, informe y métricas académicas H8, operaciones Git y publicaciones remotas |

> **CONTROL DE EJECUCIÓN:** Este prompt v0.1 ha sido **aprobado y autorizado expresamente por el responsable el 15/10/2026** para la implementación integral H6 con Codex, limitada al alcance y a las restricciones aquí documentadas. La constancia `docs/APROBACION-DISENO-H6.md` reflejó correctamente una etapa anterior (diseño aprobado, implementación aún no autorizada); esta aprobación posterior del prompt resuelve esa condición, sin modificar retrospectivamente la constancia.

## 1. Mandato de implementación aprobado

**Este prompt fue aprobado y su ejecución autorizada expresamente por el responsable el 15/10/2026.** Implementá el **hito H6 completo** de ReparaBarrio en una invocación integral de Codex, con fases técnicas internas y verificación temprana de sus dependencias sensibles. Ejecutá los tests posibles, corregí únicamente defectos introducidos por H6 dentro del alcance autorizado y producí evidencia verificable. No se exige aprobación humana entre subetapas ordinarias; **sí** se exige detenerse ante incompatibilidades materiales, fallas de protección o necesidad de alterar decisiones normativas.

H6 debe proporcionar generación reproducible de una carga ficticia, solicitudes de negocio y trabajos concurrentes efectivos, terminación informada, estadísticas basadas en hechos confirmados y una interfaz Swing que permita configurar y observar la ejecución, sin afectar datos del modo operativo. Es un **prototipo académico**, no una aplicación productiva, y no debe ampliar funciones fuera del MVP.

### 1.1 Lectura obligatoria y precedencia

Leé **completos** `AGENTS.md`, `SPEC.md` v0.2, `PLAN.md` v0.2, `IMPLEM-H6.md` v0.1, `docs/APROBACION-DISENO-H6.md`, el prompt de diseño H6 y, si está disponible, `docs/AUDITORIA-DISENO-H6-v0.1-2026-10-15.md`; también `docs/DECISION-ALCANCE-ACADEMICO-H4.md` y `docs/H5-VALIDACION-EXTERNA-Y-CIERRE-ACADEMICO.md` cuando estén presentes. Consultá los diseños H3/H4/H5 y las pruebas pertinentes para confirmar contratos y garantías existentes. Inspeccioná código real: `ServicioTaller`, `ContextoTaller`, `ResultadoOperacion`, instantáneas, `Planificador`, `FabricaContextos`, `GuardiaInstancia`, `PersistenciaH2`, `InicializadorBase`, `AutorizacionBase`, `UnidadTrabajoJdbc`, `PanelSimulacion`, `VentanaPrincipal`, `PresentadorTaller`, `ReparaBarrioApp` y composición/cierre actuales.

Jerarquía: **SPEC y decisiones humanas explícitas → PLAN aprobado → `IMPLEM-H6.md` y aprobación H6-D01/D02 → este prompt operativo**. La auditoría explica las restricciones, sin alterar SPEC/PLAN. Las anotaciones «pendiente» del diseño y auditoría son históricos **previos** a `docs/APROBACION-DISENO-H6.md`; no requieren una segunda solicitud de aprobación de esas mismas dos decisiones.

Antes de modificar nada, identificar raíz, rama, HEAD, estado de trabajo y remotos **sólo con lectura Git**. El punto histórico conocido es `75d98bb` (H5); el HEAD efectivo puede haber avanzado legítimamente y no debe restablecerse a ese commit. Ejecutar únicamente en el repositorio original, **no** en el clon de publicación del grupo. No dar por limpia la rama ni por existentes todos los archivos sin comprobarlo. Conservar sin sobreescritura todos los cambios preexistentes ajenos a H6, incluidos archivos sin seguimiento.

## 2. Límites y autorizaciones materiales

El único cambio del núcleo previamente aprobado corresponde a **H6-D01 y H6-D02**, con superficie mínima y justificación por archivo. Queda **prohibido**:

- Relajar, eludir o rediseñar sustancialmente la capacidad, guardia, auditoría, protocolo de intención, integridad, coordinación o recuperación H4 para que funcione la simulación.
- Abrir, inspeccionar por JDBC, copiar, migrar, reanudar, inicializar, sobrescribir o borrar bases operativas/preexistentes, en especial `datos/operativo`; crear una base H6 en esa ruta.
- Sustituir H2 **en memoria** por archivo como salida automática ante una dificultad; construir strings JDBC desde Swing o parámetros del usuario; compartir conexión/ancla entre trabajadores.
- Crear otro planificador, inventario mutable en memoria de recursos o estados de orden, reservas anticipadas, prioridades, salteos, bloqueo por recurso, transiciones/SQL nuevos por comodidad, cambios de reglas o dependencias ajenas a PLAN.
- Implementar pausa, reanudación, cancelación automática, editor de catálogo, reintentos de commits inciertos, migraciones, reparación H4, distribución JAR H7 o métricas del informe H8.
- Cambiar `SPEC.md`, `PLAN.md`, `AGENTS.md`, diseños H3/H4/H5/H6, aprobaciones humanas, prompts anteriores, SQL normativo o pruebas históricas para aparentar conformidad. Si un cambio adicional resulta imprescindible y material, parar y explicarlo.
- Ejecutar `git add`, `commit`, `push`, `pull`, `merge`, `checkout`, `switch`, `reset`, rebase o crear PR. No configurar remotos ni publicar en el repositorio grupal. El responsable conserva operaciones Git.

Pueden crearse clases Java, tests, fixtures, documentación/reportes propios H6 y ajustes acotados en archivos de composición/presentación expresamente descritos por `IMPLEM-H6.md` §9. La ampliación temporal o de permisos debe respetar literalmente la aprobación humana. Conservar artefactos originales JSONL y logs; **no calcular todavía tokens acumulados ni cifras de CPU/RAM para planillas**.

## 3. Fase interna 1 — contexto H2 en memoria y captura temporal (PRIMER PUNTO DE CONTROL)

### 3.1 H6-D01: sesión aislada segura

Implementá por la fábrica actual una entrada de simulación nueva y acotada, para crear un contexto H2 en memoria `jdbc:h2:mem:<nombre-interno>` generado **exclusivamente por infraestructura**. Cada corrida usa UUID/nombre únicos; directorio nuevo `target/h6-*` sirve sólo de guardia/diagnóstico y **no contiene un archivo de negocio H2**. Ninguna ruta o URL operativa puede pasar desde UI/motor. El permiso específico de creación de memoria conserva validaciones de raíz real, propiedad y ausencia de colisiones. Sin reapertura ni adopción de datos anteriores.

Crear una conexión ancla privada que conserve la base mientras viva el contexto, no utilizada por trabajadores ni por `UnidadTrabajoJdbc`. Inicializar esquema v2, marcador, catálogo y auditoría reutilizando código efectivo, sin duplicar una segunda implementación de reglas. El resto de operaciones de negocio sigue usando nuevas conexiones transaccionales privadas por invocación al mismo H2. Mantener un propietario/capacidad/guardia por contexto: no crear un registro mutable alternativo.

Cierre **verificable**: drenar ejecutores del escenario, cerrar contexto/ancla **antes de liberar guardia**, comprobar estado y retirar propietario. Ante cierre incompleto, registrar falla y retener protección, no declarar éxito ni eliminar manualmente locks/directorios. Un escenario nuevo crea nueva memoria; no borra la anterior por inferencia.

### 3.2 H6-D02: hechos confirmados y reloj

Instrumentar de forma **aditiva** la operación serializada: reloj monotónico inyectable para intervalos, `Clock` para fecha/hora, hecho temporal emitido **después del commit confirmado**, antes de liberar el orden de serialización, con contexto, operación, acción, entidad, recursos, revisión y tiempo. El punto observado es el **instante conocido posterior al commit**, no el instante físico interno de la transacción H2. La bitácora no hace E/S ni callbacks bajo el coordinador; no se utiliza para disponibilidad ni para resolver prioridad.

Ofrecer consulta coherente e inmutable de captura y hechos. No usar observaciones perdibles H3 ni repintados Swing como origen único de estadísticas. Si hay commit conocido seguido de fallo en el registro temporal, preservar `CONFIRMADA`, detener/advertir sobre evidencia incompleta y presentar métricas `N/D` cuando corresponda; **no repetir la mutación**. Ante resultado incierto, no agregar hecho supuesto ni compensar. La instrumentación del operativo puede permanecer desactivada sin cambiar sus resultados existentes.

### 3.3 Puerta de seguridad temprana

**Antes de implementar generador, motor o Swing H6:** ejecutar pruebas focales nuevas de esquema/catálogo, apertura sana, operaciones H2 reales, aislamiento respecto de una base operativa *de fixture* totalmente nueva, guardia/capacidad, cierre/ancla; además las regresiones sensibles de `ContextoTaller`, `FabricaContextos`, H4/H5 que puedan correr en el entorno disponible. Registrar resultados exactos.

Si el diseño no es realizable sin debilitar H4, o las adaptaciones generan un defecto nuevo material de integridad, **DETENERSE**, producir reporte de bloqueo y no construir el resto. El error conocido H2 `90097-232` en sandbox y defectos antiguos H4-T02/T03/T09 **no autorizan** cambiar parámetros, saltear pruebas o afirmar corrección; diferenciar problemas previos, entorno y regresiones introducidas con evidencia. No entrar en bucles indefinidos de intentos para «verdear» H4.

## 4. Fase interna 2 — configuración y generador determinista

Implementá el modelo H6-G1 conforme `IMPLEM-H6.md` §4. Configuración inmutable y validada: semilla, N órdenes (incluye N=0 y referencia N=100), W trabajadores (W≥1; referencia W=4, **sin límite funcional arbitrario de cuatro**), cantidad/habilitación de recursos físicos por categoría y tipo, pesos de subconjuntos de herramientas, patrón de llegadas, duraciones ficticias y tasa de fallos definida en partes por diez mil.

Consumir aleatoriedad en un **único generador secuencial**, en orden fijo y documentado para la versión H6-G1, **antes** de iniciar concurrencia. Producir carga inmutable completa: IDs/descriciones válidas, requisitos, offsets de llegadas, duraciones y resultado ficticio predecidido. Ningún trabajador usa Random compartido ni decide los fallos en función de intercalados.

Validar sin overflow semilla/parámetros/rangos, timestamps/offsets/capacidad y requisitos; errores antes de abrir contexto. No crear recursos implícitos para «destrabar» casos imposibles. **Igual configuración y semilla implican igual entrada, no necesariamente igual secuencia efectiva de registros, commits, tiempos ni resultados derivados de contención.** No exigir igualdad del calendario real entre corridas.

## 5. Fase interna 3 — motor concurrente real

Implementá `MotorSimulacion` en torno a la **fachada real** de `ServicioTaller`, sin acceso a JDBC/SQL desde el motor y sin duplicar `Planificador`. Un pool de W trabajadores simula reparaciones en hilos reales; mecanismos auxiliares de programación/control gestionan llegadas, consultas y eventos **sin ocupar todos los trabajadores esperando disponibilidad de recursos**.

Secuencia exigida: generar y validar → abrir contexto sano → registrar recursos por fachada → fijar t0 → programar y registrar llegadas → consultar propuesta real → reclamar ficha y capacidad W → `confirmarInicio` con la **misma selección exacta** → esperar duración ficticia fuera de locks y JDBC → `finalizar` o `fallar` **una vez** según dato precomputado → registrar resultado confirmado → nueva consulta tras cambio relevante. Cada evento en vuelo debe tener propiedad y diagnóstico. No conservar monitor del motor al llamar servicio, refrescar Swing ni esperar futures. Ningún control auxiliar bloquea al pool de trabajos.

Consultar de nuevo cuando haya llegada, inicio/cierre confirmado o liberación de capacidad; agrupar consultas, impedir tormenta/busy-wait y evitar dos trabajos de la misma orden. Si una propuesta es rechazada por competencia/cambio pertinente, liberar sólo ficha/capacidad de infraestructura y reevaluar contra una captura nueva; no saltar primera factible ni elegir recursos alternativos por cuenta propia. Los recursos físicos se asignan exclusivamente mediante `confirmarInicio` confirmado.

Nunca interrumpir una transacción JDBC iniciada ni repetir mutación ante `PENDIENTE_VERIFICACION`, error técnico ambiguo o bloqueo. Distinguir `FALLIDA` ficticia de un error del motor. Si una reparación ya iniciada enfrenta incidente de tarea, cumplir la política acotada de `IMPLEM-H6.md` §5: un único intento de `fallar` **sólo cuando la coherencia se acredita**; ante incertidumbre detener conservando asignación y diagnóstico, no «liberar» por suposición.

### 5.1 Finalización sin espera indefinida

Aplicar el criterio de quiescencia de `IMPLEM-H6.md` §6. No hay terminación si quedan llegadas previstas o entregadas, registros/admisiones/consultas/cierres en vuelo, trabajadores con obligación de cierre, temporizadores útiles o `EN_CURSO` en la captura. La decisión requiere versión estable del registro del motor y consulta real confirmada. **Una captura sin propuesta mientras hay EN_CURSO no prueba bloqueo definitivo.**

Cuando se agoten todos los eventos capaces de cambiar el estado y queden `PENDIENTE` realmente imposibles, terminar el escenario informándolas y mostrando las causas exactas; conservar su estado PENDIENTE. Si existe propuesta factible y el motor está ocioso, es error de motor, **no** fin «sin progreso». Separar terminal normal, sin progreso, error técnico e interrupción humana de salida de los cinco estados del negocio.

La espera por JDBC permanentemente bloqueado no admite garantía temporal universal; tests con timeout deben fallar y conservar pilas/trazas, nunca clasificarse como «pendientes bloqueadas normales».

## 6. Fase interna 4 — estadísticas funcionales RF-18

Usar exclusivamente los **hechos confirmados** y captura coherente de un mismo contexto y revisión. Período: `[t0,t)` mientras corre, `[t0,tF)` al finalizar; tiempos monotónicos para diferencias y marca civil sólo para visualización. El cálculo no altera estados del negocio ni fuerza cierres.

- Cinco cantidades separadas: `PENDIENTE`, `EN_CURSO`, `FINALIZADA`, `FALLIDA`, `CANCELADA`.
- **Procesadas = FINALIZADA + FALLIDA**. `CANCELADA` y PENDIENTE no se cuentan.
- Espera por orden que tuvo inicio = instante de inicio confirmado menos registro confirmado, en ms; espera promedio sólo sobre órdenes iniciadas, incluso aún EN_CURSO o luego fallidas. Sin iniciadas: `N/D`.
- Duración del escenario = tF−t0 (o t−t0 durante ejecución), en segundos con precisión de presentación y período explícitos.
- Por recurso: intervalos ocupados reales confirmados recortados al período / unión de intervalos habilitados dentro del mismo período × 100; `N/D` con denominador cero. `FUERA_DE_SERVICIO` se excluye del denominador; un recurso ocupado sigue habilitado. Nunca ocultar inconsistencia truncando utilización a 100.
- Pendientes imposibles: causas informativas, **no sexto estado**. El tiempo ficticio configurado no se considera automáticamente «ocupación» de un recurso; ocupación va de inicio a cierre confirmado.

Pruebas focales con reloj controlado: cero inicios, período de longitud cero, intervalos abiertos, alta tardía, habilitación parcial, fuera de servicio, denominadores nulos, salida prematura/resultado incierto, cambio artificial de `Clock` civil y redondeos. Las estadísticas funcionales **no sustituyen** las planillas ni rúbricas de medición académica H8.

## 7. Fase interna 5 — interfaz Swing e integración de ciclo de vida

Sustituir sólo el marcador H5 de `PanelSimulacion` por una pestaña propia operativa, conectada a contexto H6 aislado y no a `ServicioTaller` operativo. Configuración mínima suficiente: semilla, número de órdenes, W, cantidades y habilitación inicial de recursos, mezcla, llegadas, duración y fallos; inicio de escenario **nuevo**, progreso, cinco estados, órdenes, recursos/utilización, estadísticas con unidades y diagnósticos. Mostrar claramente carácter ficticio, aislamiento y H4 parcial. No agregar funciones excluidas.

Todas las aperturas, generación pesada, JDBC, métricas, espera/cierre de pools y contexto fuera del **EDT**. Controlar doble inicio, edición concurrente y respuestas tardías con generación/propietario/contexto/secuencia/revisiones. Publicar capturas inmutables en EDT, agrupar repintados sin perder hechos. El modo operativo conserva sus contadores y acciones humanas; no transformarlo en control automático ni mezclar resultados.

Coordinar el cierre de la ventana con **ambos** propietarios: no hacer `dispose()` hasta terminar cierre seguro de H5 y H6. Ante salida durante corrida, detener nuevas admisiones, drenar obligaciones iniciadas mientras el contexto esté sano, apagar scheduler/pools y cerrar memoria/ancla/capacidad/guardia; si queda incertidumbre, conservar diagnóstico y no forzar cierre ni liberar recursos ficticiamente. Una nueva corrida recibe memoria/identidad/ejecutores nuevos. No `System.exit` ni `mvn clean` mientras hay contexto vivo.

## 8. Fase interna 6 — pruebas, regresiones y evidencia

Aplicar como mínimo las **12 familias de pruebas** de `IMPLEM-H6.md` §10, sin sustituir casos difíciles por aserciones triviales:

| Prueba | Evidencia indispensable |
|---|---|
| **H6-T01** | Igualdad completa de carga H6-G1 para misma configuración+semilla; N=0/100, valores extremos y entradas inválidas |
| **H6-T02** | Dos trabajos independientes verdaderamente superpuestos en hilos distintos, intentos concurrentes y dos EN_CURSO compatibles |
| **H6-T03** | Una herramienta física única nunca ocupada por dos reparaciones; sin reservas parciales; progreso al liberar |
| **H6-T04** | Primera factible, prohibición de salteo y validación exacta tras cambios pertinentes |
| **H6-T05** | No declarar fin con llegadas/operaciones/cierres útiles; finalizar con causas de pendientes imposibles, sin esperas eternas |
| **H6-T06** | Fórmulas exactas con reloj controlable, fuera de servicio parcial, período cero, promedio y `N/D` |
| **H6-T07** | Operativo de fixture y dos corridas aislados, incluso con IDs iguales; propuestas cruzadas rechazadas; ancla/propietario cerrados |
| **H6-T08** | Escenarios reales de **100 órdenes** con recursos suficientes y limitados; capturas/trazas, resultados y estadísticas confirmadas |
| **H6-T09** | Swing/EDT no bloqueante, clic doble, callbacks tardíos, cierre durante ejecución, nueva corrida sin compartir estado |
| **H6-T10** | Regresiones focales de H1–H5 y recorrido académico nominal, sin ocultar errores H4 anteriores |
| **H6-T11** | Hechos temporales correlacionados con commit conocido, rollback, commit incierto y error posterior; sin duplicados ni éxito falso |
| **H6-T12** | Mismos fixtures de decisión funcional en modo operativo de prueba y simulación, con distintos propietarios |

Usar H2 **real** para integración y pruebas deterministas de concurrencia con barreras/latches **sólo en test**. La ausencia de carreras universales/deadlocks no se demuestra con un único run; informar límites. Verificar que las pruebas no dejan hilos o conexiones ancla vivos. No ejecutar automáticamente pruebas destructivas contra archivos operativos preexistentes. Al menos las regresiones focales se deben intentar también si falla la fase más avanzada, siempre que sea seguro; reportar selector exacto.

El entorno Codex MXC puede no reproducir las condiciones de Windows y ha dado antecedentes `90097-232` en H2. **No** atribuir automáticamente todo fallo a sandbox ni declarar test pasado si sólo compila. Si hay bloqueo ambiental, informar lo probado/no probado y preparar **instrucciones concretas** para validación externa por el responsable en Windows. No modificar pruebas, restricciones JDBC, recuperación o aislamiento sólo para conseguir verde.

## 9. Orden de trabajo, control de costes y detención

Secuencia obligatoria de trabajo:

1. Leer normativa, verificar baseline y anotar cambios previos.
2. Implementar la **puerta mínima H6-D01/H6-D02**, verificarla y decidir si el resto es seguro.
3. Generador reproducible y sus tests.
4. Motor y terminación más tests concurrentes/controlados.
5. Estadísticas y pruebas exactas.
6. Swing/cierre y pruebas EDT; integrar escenario de 100.
7. Regresiones, documentación y reporte honesto.

Si se detecta una incompatibilidad **material** con H4 o un defecto nuevo grave del contexto/capacidad, **detener y documentar**. No escalar a reescritura del protocolo ni encadenar intentos extensos. Se permite corregir defectos locales evidentes de H6 sin solicitar autorización intermedia; documentar correcciones. No pedir a otros agentes colaboración fuera del registro indicado ni ejecutar pasos H7/H8 automáticamente.

El propósito es construir **funcionalidad académica verificable** y recoger las evidencias del desarrollo por Codex, no perfeccionar recuperación avanzada hasta agotar tiempo/tokens. No recortar aserciones para afirmar aprobación. Las cuentas de tokens originales se conservan en logs/JSONL de Codex y se analizarán **después**, junto con rúbricas/planillas; no producir totales ahora.

## 10. Entregables y reporte de fin

Dejar al finalizar:

1. Código Java 21 bajo el Maven existente: contexto H2 aislado, generación, motor, estadística, Swing y cierre; sin nuevo proyecto ni dependencias injustificadas.
2. Pruebas JUnit de H6 y fixtures de configuración/semilla; regresiones focales documentadas.
3. Mecanismo claro de inicio y comprobación de H6 en Windows desde el proyecto, integrado en GUI H5; no empaquetado JAR H7.
4. `H6-REPORTE-IMPLEMENTACION.md` con estado **IMPLEMENTADO Y PROBADO / PARCIAL / BLOQUEADO** según resultados efectivamente obtenidos; decisiones aplicadas, cambios archivo por archivo, limitaciones, comandos/salida/entorno, pruebas H6-T01..T12, regresiones, H4 histórico, reproducción externa Windows y próximos pasos.
5. Evidencia de comandos/pruebas en `metricas/logs/h6/` o ruta equivalente ya aprobada, **sin destruir/reescribir registros anteriores**. No calcular métricas académicas aún; conservar telemetría original de Codex.

Al responder a la ejecución, indicar: archivos modificados/nuevos, pruebas y resultados reales, estado de 100 órdenes si ejecutado, si hubo errores nuevos o heredados, comandos pendientes de Windows, garantía de no tocar datos operativos ni ejecutar Git, y si se detuvo por impedimento. **No anunciar aceptación de H6**: la validación Windows externa y la aprobación humana suceden después. H7/H8 siguen pendientes.

---

**FIN DEL PROMPT APROBADO — IMPLEMENTACIÓN H6 AUTORIZADA CON LAS RESTRICCIONES DOCUMENTADAS.**
