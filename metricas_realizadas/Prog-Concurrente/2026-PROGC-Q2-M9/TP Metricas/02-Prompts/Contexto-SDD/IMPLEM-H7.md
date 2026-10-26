# ReparaBarrio — Diseño de integración académica H7

| Control | Valor |
|---|---|
| Versión | 0.1 |
| Fecha | 15/10/2026 |
| Estado | **PROPUESTA — PENDIENTE DE APROBACIÓN HUMANA** |
| Fase | SDD P03: diseño previo; no implementación |
| Solicitud | `prompts/anexos/P03-D-H7.md`, ejecución de diseño autorizada por el usuario |
| Alcance aprobado | `docs/APROBACION-ALCANCE-H7-2026-10-15.md` |
| Entrega exclusiva | `IMPLEM-H7.md`, raíz del proyecto local |

## 1. Autoridad, fuentes y delimitación

La precedencia es SPEC funcional y PLAN arquitectónico aprobados, decisiones humanas posteriores que delimitan el esfuerzo académico y contratos actuales observados. Los diseños históricos no sustituyen al código ni sus estados originales se actualizan retrospectivamente. La autorización de alcance habilita este diseño; **no autoriza implementar**. La implementación futura requiere aprobación explícita de este documento y un prompt técnico independiente aprobado. No se crea ese prompt ahora.

H7 original, PLAN §8.3, exige integración MVP, empaquetado, Windows, referencia completa de 100 órdenes y CA-01…CA-26 sin requisitos omitidos. H7 acotado permite consolidar el recorrido académico, corregir accesibilidad Swing, repetir escenarios y registrar brechas, sin reparar recuperación avanzada. No habilita declarar MVP completo ni conformidad de requisitos diferidos. H4 permanece **PARCIAL, NO APROBADO**. La discrepancia normativa PT-06 quedó resuelta como decisión humana por PLAN §8.7; su implementación/verificación integral sigue siendo otro asunto.

Fuentes documentales inspeccionadas en lectura:

- `AGENTS.md`; `SPEC.md` v0.2, requisitos, invariantes, CA de §9 y D016; `PLAN.md` v0.2, contratos, matriz §8.1, hitos §8.3, PT-01…PT-08 y ratificación §8.7.
- `prompts/anexos/P03-D-H7.md`; propuesta de alcance `docs/H7-PROPUESTA-ALCANCE-SDD-ReparaBarrio-v0.1.md` y aprobación del 15/10/2026. El estado histórico «propuesto» del documento de origen queda superado únicamente por el acta posterior.
- `IMPLEM-H5.md`, `IMPLEM-H6.md`; `H5-REPORTE-IMPLEMENTACION.md`, `H6-REPORTE-IMPLEMENTACION.md`; `docs/APROBACION-DISENO-H5.md`, `docs/APROBACION-DISENO-H6.md`; `docs/DECISION-ALCANCE-ACADEMICO-H4.md`.
- `docs/H6-REPORTE-FASE1-CODEX.md`, `docs/H6-REPORTE-ANTES-CONTINUACION-20261015-105950-226.md`, validaciones externas H6 FASE1 y FINAL del 15/10/2026; checklist Windows H5/H6 y `docs/H5-VALIDACION-EXTERNA-Y-CIERRE-ACADEMICO.md`.
- `metricas/logs/h6/windows-integral-20261015-115818-625.txt`, leído como evidencia histórica de clases y resultados; log focal `validacion-externa-H6-focal-20261015-115043.txt`. El focal contiene 21 pruebas; no confundirlo con las 141 del integral.

Inspección estática de archivos efectivos: `pom.xml`, `scripts/iniciar-h5.ps1`; `arranque/ReparaBarrioApp`, `ArranqueAcademico`, `FabricaContextos`, `GuardiaInstancia`; `interfaz/PanelSimulacion`, `VentanaPrincipal`, `PresentadorSimulacion`, `CierreConjunto`, `PanelOrdenes`, `PanelRecursos`, `TrabajosSwing`; contratos de `aplicacion/ServicioTaller`; motor, configuración y generador de `simulacion`; calculador de `metricas`; ancla/cierre de `persistencia/PersistenciaH2`. Pruebas consultadas: presentadores e integraciones H5/H6, aislamiento, motor, transacciones H2, concurrencia H3 y recuperación H4. Prefijo Java de estas referencias: `src/main/java/ar/edu/unlam/reparabarrio/`; tests bajo `src/test/java/ar/edu/unlam/reparabarrio/`.

No falta el acta de aprobación necesaria ni se detectó una contradicción que impida diseñar. Las referencias a resultados son históricas, no ejecuciones de esta sesión. No se certificaron rama, commit, remotos ni estado del árbol mediante Git.

## 2. Estado observado y evidencia disponible

H1–H3 tienen validación previa para sus alcances. La decisión académica H4 conserva fallos abiertos en `RecuperacionH4Test.h4T02_caidaConDosEnCursoRecuperacionesBYCIdempotentes`, `h4T03_revisionRollbackYCarreraDeCierresConReasignacion` y `h4T09_revisionFallidaConservaSesionAbiertaYAsignaciones`; también el antecedente intermitente H3-T05 (`t05_cierreCompetidorYReasignacion_CA14`). No se infiere causa raíz única ni pérdida física irreversible.

H5 tiene recorrido Windows documentado: creación descartable, recursos, propuesta exacta, volver sin iniciar, confirmación, finalización/fallo/cancelación, historial y cierre. H6 tiene motor, generador H6-G1, estadísticas, presentación y cierre conjunto incorporados. Su reporte Codex conserva una regresión focal negativa de 112 casos y un impedimento de archivo `90097-232`. La validación externa posterior acredita la selección integral de **141 casos, 0 fallos, 0 errores, 0 omitidos, EXIT=0**, sin borrar aquella evidencia negativa. El log integral incluye `AislamientoSimulacionTest` completo y `ConcurrenciaH3Test`; no incluye toda la recuperación H4.

El acta final externa H6 acredita visualmente ambos escenarios de 100 y devolución del prompt al cerrar. No acredita equivalencia general con persistencia de archivo ni ausencia universal de defectos. El problema de layout permanece registrado. Las capturas humanas mencionadas en actas no se presumen archivadas localmente si no hay ruta concreta.

Brechas actuales: accesibilidad al redimensionar, reevaluación visual tras corregirla, JAR ejecutable aún sin configuración en POM y aceptación global incompleta por H4. El éxito externo de una selección no sustituye evidencia por criterio.

## 3. Diagnóstico Swing y distribución propuesta

### 3.1 Hallazgos concretos

`PanelSimulacion` usa `BorderLayout`: toda la configuración, progreso y estadísticas va a NORTH; las tablas a CENTER. La configuración es `GridLayout(0,1)`, que iguala alturas de filas heterogéneas, incluido un inventario de tres filas. Semilla/N/W/fallos, mezcla de ocho pesos y tiempos usan `FlowLayout`. Este puede envolver componentes según el ancho, pero su altura preferida no comunica adecuadamente todas las líneas necesarias al contenedor superior. El inventario `GridLayout(0,5)` reparte cinco columnas sin contemplar el ancho mínimo de etiquetas y editores. No existe scroll para esa configuración. Etiquetas extensas de progreso/período pueden ampliar el ancho preferido o resultar cortadas.

`VentanaPrincipal` reserva cabecera y pie fuera del centro; impone tamaño inicial 1200×850 y mínimo 950×650. El área efectiva de Simulación es menor que la ventana. Con resolución/escalado que reduzca el espacio lógico, maximizar no garantiza resolverlo. Las dimensiones anteriores son hechos del código, no un diagnóstico experimental de cada monitor.

### 3.2 Solución mínima recomendada

Modificar `PanelSimulacion.java` y la composición exterior de `VentanaPrincipal.java`; conservar las pantallas operativas, modelos y conexiones de eventos.

1. Sustituir el `GridLayout(0,1)` de configuración por grupos verticales con `BoxLayout.Y_AXIS`, alineados a izquierda. Dentro de cada grupo usar `GridBagLayout`, insets derivados de fuente y campos con tamaños preferidos naturales. No repartir todas las filas con la misma altura.
2. Parámetros generales: filas etiqueta/editor para semilla, órdenes, trabajadores y fallos por diez mil. Inventario: tres columnas, categoría, total, habilitados; cinco filas. Mezcla: ocho filas con nombre explícito del subconjunto y su spinner, preservando el orden `[sin, M, S, MS, D, MD, SD, MSD]`. Tiempos: patrón e intervalos/duraciones en filas cortas con unidad ms. Se prioriza una disposición vertical estable frente a un layout adaptable complejo.
3. Componer el contenido de la pestaña con configuración arriba y pestañas de resultados abajo dentro de **un `JScrollPane` exterior**, políticas AS_NEEDED en ambos ejes. Los scrolls existentes de tablas/historial permanecen. El contenido conserva un ancho mínimo natural suficiente para sus editores; no forzar seguimiento del ancho del viewport cuando éste sea menor. Así, una ventana estrecha permite desplazarse en lugar de comprimir o superponer controles. Si hay ancho suficiente puede ocuparlo. No ubicar de nuevo la configuración fuera del viewport.
4. Mantener Iniciar y actividad en un grupo accesible por scroll. Progreso, cinco contadores, estadísticas y período deben disponer de texto completo seleccionable y con ajuste de línea, mediante áreas no editables; no ocultar información esencial en tooltips. La escritura sigue en los mismos métodos de vista. No añadir cómputo funcional al renderizado.
5. En `VentanaPrincipal`, conservar cabecera, pie, pestañas y cierres; alojar la composición existente en un contenedor con scroll exterior de seguridad para tamaños donde su mínimo natural no quepa. No modificar `PanelOrdenes` ni `PanelRecursos`. El scroll global permite acceder al cierre seguro y mensajes incluso en área lógica reducida; el scroll propio de Simulación sigue resolviendo sus controles.
6. Reemplazar la imposición de mínimo 950×650 por mínimos derivados de componentes/fuente y limitados al área útil del monitor. Calcular tamaño inicial a partir del preferido y acotarlo a `GraphicsConfiguration` menos insets de pantalla; no establecer nuevos mínimos absolutos ni agrandar la ventana como única solución. No hacer `pack()` tras cada refresco: no mover ni redimensionar la ventana durante una corrida.

Esquema de composición:

```text
Ventana / viewport de seguridad
  cabecera operativa + cinco contadores operativos
  pestañas Órdenes | Recursos | Simulación
    Simulación / viewport propio
      aviso de aislamiento y H4 parcial
      grupos: generales / inventario / mezcla / tiempos
      iniciar + actividad
      progreso / cinco estados simulados / estadísticas / período
      pestañas: órdenes / recursos-utilización / historial / diagnóstico
  consultar operativo + cerrar seguro + resultados/salud
```

No se necesita biblioteca ni un layout manager nuevo. No usar posicionamiento absoluto, tamaños fijos de paneles ni ocultar controles para que entren. Conservar valores, foco y desplazamiento durante refrescos; sólo vaciar modelos cuando ya lo indica la preparación de una corrida nueva. Asociar etiquetas a editores (`labelFor`), mantener recorrido Tab/Shift+Tab y hacer visible el control enfocado dentro de su viewport cuando sea necesario, sin desplazar automáticamente al usuario por actualizaciones de datos.

Los tests de geometría comprobarán acceso mediante desplazamiento, ausencia de solapamiento y tamaños positivos; **no acreditarán legibilidad humana ni escalado real de Windows**. El coste del renderizado actual de tablas e historial existe en EDT; para N=100 se verifica respuesta real antes de proponer optimizaciones. No extender este cambio a cargas ilimitadas ni reescribir el presentador por anticipación.

## 4. Integración, EDT, aislamiento y cierre

Se conservan PT-01…PT-08: monolito Java 21/Maven/Swing/H2, coordinador justo por contexto, conexiones privadas, transacciones completas, orden natural, catálogo cerrado, resultado incierto y guardia antes de JDBC. No se agregan locks por recurso ni otro inventario.

`PanelSimulacion.configuracion()` captura valores ligeros en EDT y entrega un supplier que construye inventario fuera de él. `PresentadorSimulacion` posee ejecutor de ciclo; apertura, generación, observación, cálculo y cierre ocurren allí. Su estado de interacción es EDT; activa/abriendo impiden doble inicio y generación, identidad, petición y revisiones filtran entregas. Mantener esa separación. El Timer de 250 ms sólo solicita refresco agrupado; no debe consultar JDBC directamente.

`MotorSimulacion.crear` genera la carga y llama exclusivamente a `FabricaContextos.crearSimulacion`. Cada corrida crea directorio `target/h6-UUID` para guardia/diagnóstico y una H2 de negocio en memoria, ancla y propietario nuevos. El operativo académico usa archivo nuevo `target/h5-UUID/taller`; ninguna GUI acepta URL o base anterior. Ancla, permisos y retiro del propietario se preservan. Dos ventanas que crean sesiones distintas no equivalen a abrir dos veces la misma base.

El monitor del motor protege futuras, registros, activos, versión y captura, y se libera antes de llamar al servicio. Dos hilos de control, scheduler y W reparadores son infraestructura; no representan técnicos. Las duraciones ficticias esperan fuera de coordinador/JDBC/monitor. Los cambios funcionales se serializan mediante el coordinador y commit; hechos y captura coherentes alimentan métricas.

Al terminar, futuras/registros/activos deben ser cero, sin EN_CURSO ni propuesta factible para declarar SIN_PROGRESO. Pendientes imposibles conservan PENDIENTE, causas y ausencia de asignaciones. Un timeout o error técnico no se reclasifica como falta de recursos. El motivo de escenario no es estado de orden.

Nueva corrida: cerrar la anterior fuera de EDT antes de crear otra. Cierre de ventana: detener Timer, invalidar generación, retirar futuras y nuevos inicios, drenar trabajo admitido y cierres conocidos, apagar/drenar ejecutores, cerrar contexto/ancla y sólo entonces liberar guardia. `CierreConjunto` dispone únicamente con operativo y simulación cerrados. Fallo de cierre conserva propietario y ventana, con diagnóstico y reintento exclusivo de cierre seguro. No añadir `System.exit`, `shutdownNow`, cierres automáticos de negocio operativo ni borrado de locks.

No hacer `get`, `join`, `awaitTermination`, JDBC o cierre bloqueante en EDT. `MotorSimulacion.close()` contiene `fin.get()` fuera de EDT; no proporciona una cota universal ante JDBC que no retorne. Su espera de ejecutores puede fallar conservando protección. La prueba externa debe distinguir ventana cerrada, retorno del proceso y comprobación automatizada del drenaje; ninguna de esas señales por sí sola prueba todo el ciclo.

Invariantes de salida: INV-01…INV-10 sin cambios; cinco estados; propuesta exacta sin reserva; prioridad al confirmar; liberación sólo por cierre válido; rechazo sin efecto funcional; incertidumbre bloquea sin retry ciego. La GUI operativa exige confirmación humana. La simulación sólo automatiza acciones ficticias sobre su propio contexto. Si mantener estas garantías exige reparar H4, detener y documentar.

## 5. Arranque y JAR local condicional

El POM actual declara Java 21, H2 2.3.232, JUnit Jupiter 5.11.4 de test, compiler 3.13.0 y Surefire 3.5.2. No configura Main-Class ni inclusión de H2 en JAR. Un `package` normal no basta para `java -jar`.

El script existente valida Java/javac/H2, compila opcionalmente con javac, copia recursos y ejecuta por classpath `target/classes;h2.jar`. Registra comando, versión, salida y código en `metricas/logs/h5/`. Requiere JDK y H2 local; puede usarse sin Maven global. Es la alternativa ya validada, a conservar sin cambios.

Se recomienda intentar un incremento pequeño de POM con **maven-assembly-plugin 3.7.1**, referencia ya prevista en PLAN §7, ejecución `single` ligada a `package`, descriptor `jar-with-dependencies` y manifest Main-Class `ar.edu.unlam.reparabarrio.arranque.ReparaBarrioApp`. No cambiar versiones de H2/JUnit ni incluir dependencias de test. Conservar recursos de inicialización, driver H2 y sus recursos; no minimizar clases ni introducir sombreado/relocalización. Nombre previsto: `target/reparabarrio-0.1.0-jar-with-dependencies.jar`; confirmar el nombre realmente generado.

Viabilidad estática: sólo una dependencia de ejecución y entrada real existente; no requiere alterar H4. **Disponibilidad offline del plugin y dependencias no verificada** en este diseño. En futura implementación comprobar resolución local con Maven offline. Si no está disponible o el ensamblado exige cambios de arquitectura/almacenamiento, conservar script y documentar brecha CA-25; no descargar ni pedir autorización externa como parte implícita de H7.

Comandos propuestos, NO ejecutados:

```powershell
# Tras aprobar implementación; todas las aplicaciones/contextos cerrados.
& $mavenH7 -o -B -ntp '-DskipTests' package
# Construcción únicamente: skipTests no acredita pruebas.
& $javaH7 -jar .\target\reparabarrio-0.1.0-jar-with-dependencies.jar --raiz $raizH7 --academico-nuevo
```

Las variables representan rutas reales del entorno registradas antes de ejecutar. Probar sin red y sin `-cp` ni IDE. El JAR requiere **Java 21 y raíz del proyecto local existente**, no es un ejecutable autónomo ni distribución independiente: `validarRaiz` exige ruta absoluta real, sin `;`, con `pom.xml` y `AGENTS.md`. No cambiar ese contrato para simular portabilidad. Maven sólo es necesario para construir; H2 queda incorporado al artefacto si el ensamblado es correcto. Invocar desde otra carpeta pasando la raíz absoluta debe conservar destino de datos; espacios en rutas deben viajar como un argumento.

Aceptar sólo sesión nueva tras consentimiento. Mantener guardia por directorio, rutas reales y controles de enlaces. Probar rechazo de segunda guardia sobre **el mismo fixture nuevo** por tests existentes; dos lanzamientos académicos crean bases distintas y no demuestran ese rechazo. No agregar una opción GUI para reabrir un fixture ni una base real sólo para probar el JAR. La evidencia de CA-25 combinará test de guardia con apertura/recorrido/cierre humanos desde JAR, identificando esos alcances separados.

Si assembly no conserva correctamente recursos/driver, guardar error y posponer; no introducir otro mecanismo de empaquetado en cadena de intentos. El checklist debe acreditar GUI visible, operaciones, dos modos y cierre con devolución del prompt. JAR presente o build verde no satisfacen por sí solos CA-25.

## 6. Matriz individual de aceptación provisional

Fuentes por fila: **S9** = SPEC §9, fila de igual ID, con requisitos indicados; **P8.1** = PLAN §8.1, misma fila. **E141** = log integral Windows y acta final H6 citados en §1; **E5** = cierre externo H5; **EH4** = decisión académica H4. Son referencias históricas, no pruebas nuevas.

Estados: APROBADO significa evidencia suficiente para el aspecto original indicado en la fila, con límites explícitos; PARCIAL, sólo una parte acreditada; FALLIDO, requisito con contraejemplo documentado abierto; NO EJECUTADO, sin evidencia suficiente de ejecución; FUERA DEL ALCANCE ACOTADO, exclusión del esfuerzo con brecha original, nunca conformidad. El estado de un criterio compuesto no se eleva por aprobar una subprueba. No se fuerza el uso de todas las etiquetas si la evidencia no lo justifica. Las verificaciones H7 futuras están todas NO EJECUTADAS.

| ID | Fuente / requisitos | Alcance original | Prueba/flujo existente o necesario | Tipo | Evidencia observable y fundamento | Riesgo/límite / faltante | Estado provisional |
|---|---|---|---|---|---|---|---|
| CA-01 | S9/P8.1; RF-01/02/10 | Altas válidas/inválidas, IDs, tipos y secuencias concurrentes | DominioTest; TransaccionesH2Test.registroValidacionCatalogoAmbitosYSecuencia_CA01_CA02; ConcurrenciaH3Test.t07 | Automático/externo | E141 incluye las clases completas: historial y secuencia por registro, rechazo sin sustitución | No demuestra conservación entre sesiones; corresponde CA-16. Repetir selección tras cambio | APROBADO, histórico para esos casos |
| CA-02 | S9/P8.1; RF-03/04/06 | Recursos únicos por categoría, fuera de servicio y catálogo | TransaccionesH2Test CA01_CA02 y cancelacionYHabilitacionLibre_CA02_CA12; E5 recursos | Automático/visual/externo | E141 y E5 acreditan altas, consulta y exclusión de inhabilitados | No certifica reapertura; catálogo sin editor según D016 | APROBADO, alcance histórico observado |
| CA-03 | S9/P8.1; RF-02/05/07 | Primera factible B tras A imposible; habilitar A invalida B | PlanificadorTest; ConcurrenciaH3Test.t08_prioridadHabilitadaDuranteInicio_CA03_CA10 | Automático/externo | E141 con propuestas y rechazos íntegros en carreras controladas | No inferir orden temporal fijo ni validar sólo por selección de tabla | APROBADO para ensayos existentes |
| CA-04 | S9/P8.1; RF-05/06, RNF-06 | Misma propuesta y orden natural, sin reserva | OrdenNaturalIdTest, PlanificadorTest y TransaccionesH2Test | Automático/externo | E141: comparador y consulta repetida; prueba funcional sin ocupación al proponer | Propiedades/casos finitos, no prueba matemática de todo ID posible | APROBADO para evidencia existente |
| CA-05 | S9/P8.1; RF-07/10, RNF-03 | Inicio completo y ninguna lectura parcial | TransaccionesH2Test.inicioCompletoYMenorAjenaNoInvalida_CA05_CA10_CA15; ConcurrenciaH3Test.t06 | Automático/externo | E141 y E5: EN_CURSO, asignación e historial; lectores coordinados | Durabilidad bajo interrupción pertenece CA-17 | APROBADO, nominal y concurrencia ensayada |
| CA-06 | S9/P8.1; RF-07/09, RNF-08 | Falta herramienta, rechazo sin retener T/P | PlanificadorTest, TransaccionesH2Test.primeraFactibleSinReservasNiSalteos_CA03_CA06_CA19; MotorSimulacionTest | Automático/externo | E141 compara recursos antes/después y pendientes sin asignación | No convertir falta de recursos en error técnico ni en sexto estado | APROBADO para los casos ensayados |
| CA-07 | S9/P8.1; RF-07/08/17 | Doble inicio concurrente: un ganador | ConcurrenciaH3Test.t01_dobleInicio_CA07; CoordinadorConcurrenciaTest | Automático/externo | E141, barreras/resultados y única transición | No acredita ausencia universal de carreras | APROBADO para ensayos controlados |
| CA-08 | S9/P8.1; RF-07/17 | Competencia por herramienta única con prioridad | ConcurrenciaH3Test.t02; MotorSimulacionTest.herramientaUnicaNoSolapaNiReservaParcialYAvanza | Automático/externo | E141, trazas/historial sin posesión simultánea ni reservas | No medir sólo cantidad final, conservar oráculo temporal | APROBADO para ensayos existentes |
| CA-09 | S9/P8.1; RF-05/07/17 | Dos trabajos independientes superpuestos | ConcurrenciaH3Test.t03; MotorSimulacionTest.dosTrabajosSolapadosYConsultasMientrasTrabajan | Automático/externo | E141 con hilos/intervalos distintos y dos EN_CURSO | GUI animada no reemplaza prueba de concurrencia | APROBADO para superposición ensayada |
| CA-10 | S9/P8.1; RF-04/06/07 | Revalidación exacta sin sustitución; unidad ajena no invalida | TransaccionesH2Test casos CA10; ConcurrenciaH3Test.t08; AislamientoSimulacionTest | Automático/externo | E141: rechazos por exacta/prioridad y aceptación ante ajena menor | No invalidar por tiempo o revisión global al cambiar layout | APROBADO, cobertura histórica de variantes |
| CA-11 | S9/P8.1; RF-08/10/11 | Finalizar/fallar, liberar todas propias, rechazar repetición | TransaccionesH2Test.cierreLiberaSoloPropiasYRechazaRepeticiones_CA11; E5 | Automático/visual/externo | E141 y E5: terminales, liberación e historial | No confundir cierre de ventana con reparación finalizada | APROBADO para flujo y pruebas existentes |
| CA-12 | S9/P8.1; RF-08/10/12 | Cancelar sólo pendiente; recursos ajenos intactos | DominioTest, TransaccionesH2Test.cancelacionYHabilitacionLibre_CA02_CA12; E5 | Automático/visual/externo | E141 y E5 con CANCELADA sin asignación | La simulación no añade cancelación automática | APROBADO para los casos existentes |
| CA-13 | S9/P8.1; RF-07/08/12 | Carrera inicio/cancelación, una salida | ConcurrenciaH3Test.t04_inicioCancelacion_CA13 | Automático/externo | E141 incluye ambos órdenes controlados, estado/asignación compatibles | Pruebas finitas; repetir sin debilitar aserciones | APROBADO para ensayos controlados |
| CA-14 | S9/P8.1; RF-07/08/11 | Carrera finalizar/fallar con reasignación, única liberación | ConcurrenciaH3Test.t05_cierreCompetidorYReasignacion_CA14 | Automático/externo | E141 pasó clase completa | Intermitencia histórica H3-T05 abierta; un pase posterior no basta para cerrar estabilidad | PARCIAL por antecedente intermitente |
| CA-15 | S9/P8.1; RF-04/11 | Ocupado no admite cambio manual; libre sí rehabilita | TransaccionesH2Test casos CA15; ConcurrenciaH3Test.t08_unidadExactaFrenteAInhabilitacion_CA10_CA15 | Automático/externo | E141, asignación intacta y rechazo | Botones deshabilitados solos no prueban protección de núcleo | APROBADO para las variantes ensayadas |
| CA-16 | S9/P8.1; RF-02/10/13, RNF-04 | Reapertura limpia completa, cinco estados, IDs, catálogo y secuencia | TransaccionesH2Test.reaperturaLimpiaMismaBase_CA16parcial; RecuperacionH4Test.h4T01 | Automático/externo | E141 acredita reapertura parcial; EH4 impide aceptación general | Matriz completa de reapertura no revalidada aquí; GUI académica no reabre. Reparación avanzada fuera de implementación acotada | PARCIAL; brecha original explícita |
| CA-17 | S9/P8.1; RF-13/14, RNF-04; PT-06/07 | Interrupciones, commit incierto, revisión y bloqueo coherentes | RecuperacionH4Test H4-T02/T03/T09; TransaccionesH2Test rollback/incertidumbre parciales | Automático/externo | EH4 conserva contraejemplos abiertos; E141 sólo cubre subconjunto nominal/inyección | Corrección avanzada FUERA DEL ALCANCE ACOTADO; faltan garantías de recuperación, no se infiere de memoria | FALLIDO por defectos abiertos; H4 no aprobado |
| CA-18 | S9/P8.1; RF-06/09/15, RNF-05 | Tres secciones, sin reserva, causas y cinco contadores durante ejecución | PresentadorTallerTest, PresentadorSimulacionTest; E5/E141; H7-T01/T02/T06 necesarios | Automático/visual/externo | Funciones presentes y recorrido histórico; acta H6 registra defecto de disposición | Acceso completo a controles/textos al redimensionar aún no acreditado | PARCIAL |
| CA-19 | S9/P8.1; RF-05/07, RN-08 | Sin salteo de primera factible aun con recursos independientes | PlanificadorTest; TransaccionesH2Test CA19; ConcurrenciaH3Test.t02; presentador | Automático/externo | E141 y revisión de UI sin acción de prioridad/salteo | Selección/filtro no puede reconstruir otra propuesta | APROBADO para casos existentes |
| CA-20 | S9/P8.1; RF-16, RNF-06/07 | Reproducibilidad de entrada completa | GeneradorCargaTest; ConfiguracionEscenario.referencia | Automático/externo | E141, igualdad de carga H6-G1 con semilla/configuración | UUID, secuencia real, intercalados y duración real no son parte de igualdad | APROBADO para generador/configuraciones probadas |
| CA-21 | S9/P8.1; RF-16/17, RNF-03/08 | Concurrencia, contención, imposibles y fin sin progreso | MotorSimulacionTest, AislamientoSimulacionTest; perfiles Windows | Automático/visual/externo | E141 y acta final H6: ambos escenarios, pendientes conservadas y trabajadores cero | No demuestra progreso si JDBC no retorna ni todas las carreras posibles | APROBADO para escenarios ensayados |
| CA-22 | S9/P8.1; RF-15/18, RNF-05 | 100 órdenes, interfaz utilizable, fórmulas/N/D/unidades | EstadisticasEscenarioTest, MotorSimulacionTest; checklist H7-T04/T05/T06 | Automático/visual/externo | E141 acredita aritmética/casos borde; acta final acredita escenarios visibles | Accesibilidad/lectura al redimensionar pendiente; métricas académicas no sustituyen RF-18 | PARCIAL |
| CA-23 | S9/P8.1; RF-19, INV-10 | Operativo idéntico antes/después, IDs iguales | AislamientoSimulacionTest.equivalenciaConArchivoNuevoYAislamientoOperativoDeFixture; ContextoSimulacionTest | Automático/externo | E141 incluye clase de aislamiento completa; código sólo crea memoria nueva | Acredita fixtures; falta nueva comparación tras cambio y evidencia humana separada. No certifica durabilidad de archivo | APROBADO para aislamiento de fixtures probado |
| CA-24 | S9/P8.1; RF-07/11/17, RNF-07 | Mismas decisiones de negocio en ambos modos; origen humano/automático | AislamientoSimulacionTest equivalencia; E5; inspección de llamadas del motor | Automático/visual/externo | E141 cubre comparación funcional nominal y E5 confirmación humana | Acta final excluye equivalencia general de persistencia; no extender el fixture nominal a todos los fallos/recuperación | PARCIAL; equivalencia nominal evidenciada |
| CA-25 | S9/P8.1; RNF-01/02; PLAN §7 | Windows local, GUI, JAR con H2/Main-Class y guardia misma base | Script H5 validado; ArranqueAcademicoTest/GuardiaInstanciaTest; H7-T10 necesario | Automático/visual/externo | E5/E141 y acta H6 acreditan classpath y guardia ensayada | POM no arma JAR ejecutable; apertura/cierre offline real desde JAR NO EJECUTADOS | PARCIAL; JAR pendiente condicional |
| CA-26 | S9/P8.1; RNF-03/08, INV-03 | Identificar recursos compartidos/riesgos/límites, sin retención parcial | Revisión coordinador/motor, tests contención y reportes H3/H6 | Automático/revisión/externo | E141 y mecanismo descrito en §4: lock por contexto y monitor breve sin esperas cruzadas | Evidencia finita no certifica ausencia universal de deadlock/starvation | APROBADO para revisión/evidencia acotada |

La aprobación final de H7 acotado no altera automáticamente estos estados ni convierte CA-16/17/25 en conformes. La matriz definitiva deberá enlazar métodos, log de ejecución, XML pertinentes y capturas efectivas por fila; si no se consigue evidencia adicional, conservar PARCIAL/FALLIDO/NO EJECUTADO según corresponda.

## 7. Secuencia posterior de implementación y archivos

Todo este apartado es una propuesta futura dependiente de aprobación integral, sin nuevas puertas por decisiones triviales de layout.

| Paso / dependencia | Archivos existentes a modificar o nuevos a crear | Cambio y justificación / verificación |
|---|---|---|
| 1. Línea base autorizada | Sin modificación de producto; evidencia nueva `metricas/logs/h7/` | Registrar entorno, selector y regresión focal externa. Si falla apertura de fixture con 90097, conservar error y separar precondición; no eludir bloqueo. No repetir indefinidamente ni afirmar causa ambiental |
| 2. Layout propio, tras 1 | Modificar `src/main/java/ar/edu/unlam/reparabarrio/interfaz/PanelSimulacion.java` | Grupos/GridBag y scroll, textos completos; conservar campos, eventos y contratos. Crear test de layout del paso 4 |
| 3. Área disponible, tras 2 | Modificar `src/main/java/ar/edu/unlam/reparabarrio/interfaz/VentanaPrincipal.java` | Viewport de seguridad y tamaño inicial limitado al monitor; preservar composición operativa y CierreConjunto. No tocar paneles operativos ni arranque/negocio |
| 4. Pruebas focales, tras 2/3 | Crear `src/test/java/ar/edu/unlam/reparabarrio/interfaz/PanelSimulacionLayoutTest.java` y `VentanaPrincipalLayoutTest.java` | Geometría, scroll, foco/control alcanzable, conservación de valores/modelos y acciones. Reusar pruebas de presentador/cierre; no duplicar aritmética ni crear tests que sólo comparen clases de layout |
| 5. Integración, tras 4 | Reutilizar tests H5/H6 intactos; ampliar `IntegracionSimulacionH6Test.java` sólo si falta una aserción de ciclo exigida por un cambio real | Ensayar EDT, entregas tardías, cierre/aislamiento y perfiles con mismos parámetros. No modificar aserciones históricas para conseguir verde |
| 6. JAR condicional, tras 5 | Modificar únicamente `pom.xml` para assembly de §5 | Comprobar disponibilidad offline, empaquetar con recursos/H2/manifest y probar GUI. Si no viable, registrar diferimiento; script existente intacto |
| 7. Validación externa, tras 5 y 6 si viable | Crear `docs/H7-CHECKLIST-WINDOWS.md`, `docs/H7-GUIA-EJECUCION-LOCAL.md`, `docs/H7-MATRIZ-ACEPTACION.md` | Procedimientos de §§8/9 y matriz §6 actualizada con resultados reales; distinguir script/JAR y límites H4 |
| 8. Reportar y detener | Crear `H7-REPORTE-IMPLEMENTACION.md`; logs nuevos y capturas bajo `metricas/logs/h7/` y `evidencias/h7/` | Archivos realmente cambiados, comandos, resultados positivos/negativos, faltantes, estado JAR, referencias a JSONL. Sin métricas H8, Git o publicación |

No se prevé modificar `ServicioTaller`, `ContextoTaller`, fábricas, motor, generador, calculador, JDBC, SQL ni recuperación. Un defecto nuevo material que requiera salir de esa frontera se documenta con hecho, consecuencia, alternativa y decisión humana requerida. Si la solución pasa por H4 avanzado, parar ese trabajo; no desbloquear, migrar, adoptar ni sustituir almacenamiento. Conservar lo ya verificable y no presentar integración bloqueada como aprobada.

## 8. Casos H7 propuestos

Todos **NO EJECUTADOS en esta sesión**. Rutas abreviadas de tests referidas a los paquetes existentes; los dos archivos de layout aún no existen. Timeouts son límites de ensayo, no promesas del producto. Los ensayos de JFrame requieren sesión gráfica: si no está disponible, registrar NO EJECUTADO y trasladar a Windows externo, sin contarlo como aprobado.

| ID | Precondición y pasos | Invariantes / evidencia exigida | Automatización y validación externa |
|---|---|---|---|
| H7-T01 | Panel real en EDT, sin JDBC; aplicar tamaños de viewport normal/reducido/amplio y distintos textos de resultados; recorrer todos los grupos y desplazar ambos ejes | Editores/botón con área positiva, sin intersección entre campos, todos alcanzables por viewport; ocho pesos, diez cantidades y tiempos presentes; valores y foco conservados al refrescar | PanelSimulacionLayoutTest: layout recursivo y scrollRectToVisible/viewport, verificar intersección completa al enfocar. Windows: ratón/Tab, lectura y edición reales a varias escalas |
| H7-T02 | JFrame real en sesión gráfica, sin conectar a base; redimensionar, maximizar/restaurar, revisar cabecera, pie y pestaña | Ventana dentro del área útil al iniciar; no tamaño mínimo que exceda pantalla; cierre/consulta/resultados alcanzables; no pérdida de controles operativos | VentanaPrincipalLayoutTest en EDT, dispose en finally; no activar servicios. Humano prueba monitores/escalas y registra límites reales |
| H7-T03 | Sesión académica nueva H5, dos técnicos/puestos, M1/M2 y S1 disponibles, D1 fuera de servicio; recorrido de §9 | Primera factible, propuesta exacta sin reserva, dos EN_CURSO disjuntas; finalización/fallo/cancelación con historial, recursos liberados y cinco contadores | Reusar RecorridoAcademicoNominalTest e IntegracionInterfazH5Test; humano confirma diálogos y guarda capturas por etapa |
| H7-T04 | Perfil suficiente exacto de §9; repetir generación con igual configuración y ejecutar GUI nueva | Carga completa igual en dos generaciones; 100 registradas/procesadas, cero pendientes/en curso al fin nominal; futuras/registros/trabajadores cero, métricas con unidades | GeneradorCargaTest y MotorSimulacionTest.perfilesCienOrdenesReales; humano observa actualizaciones y captura configuración/resultado |
| H7-T05 | Perfil limitado exacto de §9, nuevo contexto | 100 registradas, termina SIN_PROGRESO, pendientes imposibles informadas y conservadas con causas, sin asignación, sin sexto estado; cero futuras/registros/trabajadores/EN_CURSO | MotorSimulacionTest y replay existente; humano revisa pendientes por falta de taladro y resultado final. No imponer recuentos/timing inventados |
| H7-T06 | Durante T04/T05, navegar, consultar operativo y volver; doble inicio, ráfagas y respuestas tardías en dobles controlados | EDT procesa eventos mientras trabajo de fondo espera latch; no doble corrida; vistas sólo EDT, entregas viejas/cerradas no actualizan; contadores de cada modo separados | PresentadorTallerTest, PresentadorSimulacionTest e IntegracionSimulacionH6Test; inspección humana de navegación y acceso por scroll durante ambas corridas |
| H7-T07 | Operativo de fixture nuevo, dos memorias sucesivas, IDs iguales, sin acciones humanas entre capturas comparadas | Captura operativa antes/después idéntica; identidades diferentes, propuesta cruzada rechazada; anterior cerrado antes de nueva corrida | ContextoSimulacionTest y AislamientoSimulacionTest completos; humano conserva captura operativa y vuelve tras ambos escenarios. No abrir datos anteriores |
| H7-T08 | Cerrar durante corrida positiva con llegadas futuras; variar cierre durante preparación con dobles; inyectar fallo de close controlado | No nuevos inicios al salir; drena obligaciones conocidas, ventana no se dispone antes de ambos propietarios; fallo retiene protección/diagnóstico, reintento sólo de cierre; terminal vuelve sin trabajadores vivos | Pruebas actuales de presentador, motor e integración; humano solicita cierre seguro, espera y registra salida. No matar proceso para fabricar éxito |
| H7-T09 | Aplicaciones cerradas; selección focal §9, y diagnóstico histórico por separado si autorizado | Conservar rechazos, invariantes, XML, selector, EXIT; H4-T02/T03/T09 y H3-T05 siguen visibles; ningún cambio de aserciones | Maven offline fuera de Codex en Windows; comparar por caso con evidencia previa, sin afirmar suite global desde selección |
| H7-T10 | Assembly viable, Java 21 local, raíz real con espacios si corresponde, sin red; ejecutar JAR sin classpath externo | Main-Class/H2/recursos presentes, consentimiento antes de base nueva, misma ubicación con cwd distinto; GUI/recorridos y cierre funcionales; rechazo misma guardia en fixture | Inspección de contenido/manifest y tests de guardia; humano arranca JAR y comprueba devolución del prompt. Si se pospone, NO EJECUTADO y CA-25 parcial |
| H7-T11 | N=0, cero recursos o habilitados cero; capturas de métricas controladas ya existentes | Fin informado sin espera indefinida, N/D sin inicios/denominador, período/unidades y cinco estados separados, sin overflow de layout por diagnóstico largo | EstadisticasEscenarioTest, MotorSimulacionTest; panel de layout con textos largos y humano inspecciona N/D. No alterar los perfiles de referencia |

## 9. Checklist Windows y evidencia propuesta

El futuro checklist tendrá columnas por paso: fecha/hora y zona, responsable, entrada efectiva, resultado esperado, resultado observado, estado, ruta de log/captura e incidencia. Registrar Windows, Java/Maven/H2 efectivos, resolución física, área útil, escala de Windows y tamaño lógico de ventana. Probar normal, maximizada y reducida razonablemente: como referencia de ensayo, 1200×850 y 950×650 cuando quepan, y 800×600 o el área útil disponible si es menor. Son tamaños de prueba, no mínimos del producto. Ensayar escalas 100 %, 125 % y 150 % disponibles en el equipo, con al menos una resolución de área limitada y la habitual; no inventar resultados de escalas no ensayadas. Registrar restricciones del monitor/sesión.

### 9.1 Recorrido operativo descartable

1. Cerrar otras sesiones. Ejecutar `./scripts/iniciar-h5.ps1 -Compilar` con las rutas Java/H2 locales correctas, desde la raíz. Verificar aviso H4 parcial y descarte; Volver/cerrar antes de consentir no debe crear sesión. Aceptar una creación nueva y anotar `target/h5-UUID` e identidad, sin abrir sesiones anteriores.
2. Crear T1/T2, P1/P2, M1/M2 de MULTIMETRO y S1 de SOLDADOR habilitados; D1 de TALADRO inhabilitado. Consultar disponibilidad y catálogo cerrado. Probar duplicado sin reemplazo.
3. Registrar, en orden: IMPOSIBLE con TALADRO; A con MULTIMETRO y SOLDADOR; B con MULTIMETRO; C sin herramientas, descripciones propias no vacías. Registrar secuencias asignadas por núcleo. Esperado: IMPOSIBLE pendiente por falta de D disponible, A primera factible, B/C bloqueadas por prioridad.
4. Consultar dos veces, anotar T1/P1/M1/S1 propuestos para A según orden natural, sin ocupación. Abrir confirmación y Volver; comprobar PENDIENTE y recursos libres. Volver a consultar y confirmar humanamente la propuesta exacta. No seleccionar otra orden para construir inicio.
5. Consultar nueva primera factible B y confirmar: debe usar T2/P2/M2 mientras A usa T1/P1/M1/S1. Guardar captura de dos EN_CURSO y asignaciones disjuntas. Intentar cambio manual de ocupado sólo por vías admitidas: UI lo impide; protección del servicio se acredita con tests, sin añadir atajos a GUI.
6. Finalizar A, informar fallo B, cancelar C pendiente, cada una con diálogo humano e ID correcto. No cancelar IMPOSIBLE: conservarla para verificar su causa. Resultado esperado del fixture sin otras acciones: PENDIENTE=1, EN_CURSO=0, FINALIZADA=1, FALLIDA=1, CANCELADA=1; todos los recursos de A/B libres, D1 fuera de servicio. Son oráculos de este caso futuro, no mediciones realizadas.
7. Revisar historial ordenado, eventos de registro/inicio/cierre y recursos asociados, rechazo de IDs duplicados/finales y cinco contadores. No provocar corrupción para conseguir revisiones. Cerrar seguro al finalizar toda la validación, registrar mensajes, salida y devolución del prompt. Una sesión nueva posterior no acredita reapertura CA-16.

### 9.2 Perfiles H6 sin cambios

Comunes: **semilla 20261015, N=100, trabajadores=4, ocho pesos=1, llegadas PERIODICAS 10/10 ms, duración uniforme inclusiva 200–400 ms, fallos 1000/10000**.

| Perfil | Técnicos total/habilitados | Puestos | Multímetros | Soldadores | Taladros |
|---|---|---|---|---|---|
| Suficiente | 4/4 | 4/4 | 4/4 | 4/4 | 4/4 |
| Limitado | 2/2 | 2/2 | 1/1 | 1/1 | 0/0 |

Completar configuración, guardar captura antes de iniciar y resultado final con identidad, cinco estados, causas, futuras/registros/trabajadores y estadísticas/período. Usar nuevo escenario entre perfiles y confirmar identidad distinta. Antes/después, capturar operativo sin mutaciones intermedias; la simulación no debe modificarlo. Durante ambas corridas navegar pestañas, consultar, desplazar formulario y resultados, redimensionar/maximizar/restaurar. No exigir igual tiempo real, agenda, secuencia de registros empatados o intercalado. Comparar entrada completa mediante el test del generador, no mediante imágenes del resultado.

Al terminar suficiente: todas procesadas, ninguna pendiente/EN_CURSO. Limitado: pendientes imposibles con causas, ninguna EN_CURSO y sin trabajo futuro útil. No esperar indefinidamente ni cancelar pendientes para aparentar finalización completa. Estadísticas: procesadas=FINALIZADA+FALLIDA; espera promedio sólo iniciadas en ms, duración en s, utilización por unidad en %, período explícito; N/D cuando falta denominador/evidencia. Son datos funcionales H6, no H8.

Efectuar además una corrida separada de cierre activo con los mismos parámetros: solicitar salida durante actividad y registrar interrupción/no entregadas; no atribuirle la aceptación de un perfil completo. El cierre debe devolver el prompt. Correlacionar con pruebas de drenaje; si no retorna o queda propietario retenido, registrar resultado negativo y diagnóstico, sin retirar guardias ni borrar datos.

### 9.3 Selecciones y registro externo

Selección focal no destructiva propuesta: todas las clases de `docs/H6-CHECKLIST-WINDOWS.md` §1, más `PanelSimulacionLayoutTest` y `VentanaPrincipalLayoutTest` después de crearlas. Incluye H5/H6 y regresiones H1–H3 pertinentes; sólo crea fixtures nuevos. «No destructiva» se refiere a datos del usuario: algunos tests inyectan fallos sobre su fixture. No seleccionar datos reales ni ejecutar clean.

Selector completo base, para construir una única cadena `-Dtest`:

```text
ContextoSimulacionTest,HechosConfirmadosTest,GeneradorCargaTest,MotorSimulacionTest,EstadisticasEscenarioTest,AislamientoSimulacionTest,PresentadorSimulacionTest,IntegracionSimulacionH6Test,DominioTest,PlanificadorTest,OrdenNaturalIdTest,AuditorCoherenciaTest,TransaccionesH2Test,CoordinadorConcurrenciaTest,ConcurrenciaH3Test,CicloContextoTest,GuardiaInstanciaTest,RecorridoAcademicoNominalTest,PresentadorTallerTest,ArranqueAcademicoTest,IntegracionInterfazH5Test
```

Comando futuro: `& $mavenH7 -o -B -ntp "-Dtest=$selectorH7" test`, con las dos clases nuevas añadidas y sesión gráfica cuando corresponda. Conservar los nombres/métodos realmente ejecutados, omitidos y fallidos. No predecir un total de pruebas H7 ni reutilizar 141 como resultado futuro.

Diagnóstico histórico separado, si su ejecución queda incluida en la futura autorización: `RecuperacionH4Test` completo o selector exacto de sus tres métodos fallidos antes enumerados; H3-T05 ya está en la clase focal. Las demás clases históricas H4 (`ArchivoH2H4Test`, `MigracionH4Test`, `ProtocoloJdbcH4Test`, `CoherenciaH4Test`, `CorreccionH4MemoriaTest`) no forman parte de la selección focal H5/H6. No iniciar una campaña de reparaciones o suite global como consecuencia automática de un fallo. Si se ejecuta diagnóstico, registrar su selector y alcance individual sin mezclarlo con focales verdes.

Procedimiento de conservación por cada ejecución:

1. Crear carpeta única con sello `yyyyMMdd-HHmmss-fff` bajo `metricas/logs/h7/`. Guardar comando literal, selector, entorno, raíz, inicio/fin civil y política offline. Preparar directorios antes de redirigir salida.
2. Redirigir stdout/stderr a un log nuevo y capturar `$LASTEXITCODE` inmediatamente tras Maven/Java; escribir `EXIT=...` antes de ejecutar otro comando nativo. Conservar salida incluso si falta BUILD SUCCESS o no hay XML por fallo previo a Surefire.
3. Copiar `target/surefire-reports` a esa carpeta antes de otra ejecución, sin borrar reportes originales ni fixtures. Identificar las clases `Running` en el log y fechas/contenido de XML: pueden existir XML antiguos de otras selecciones. No sumar la carpeta entera como ejecución actual. Guardar manifiesto por comando con rutas de reportes realmente atribuibles y casos negativos/omitidos.
4. Preservar capturas nuevas bajo `evidencias/h7/` con fecha, perfil, tamaño/escala y fase; si no existen, escribir «captura no conservada». No afirmar una prueba visual por un JUnit verde.
5. Ante timeout conservar pilas/estado si se obtienen mediante diagnóstico autorizado, y registrar timeout como fallo del ensayo. Ante 90097 registrar fase exacta, contexto y precondición fallida, sin imputar causa demostrada al entorno.

No se ejecuta ninguno de estos pasos durante este diseño. Los comandos de este documento son instrucciones futuras, no un registro de actividad realizada.

## 10. Riesgos, alternativas y decisión material

| Riesgo | Mitigación / límite |
|---|---|
| Scrolls anidados o foco difícil | Grupos verticales cortos, Tab/Shift+Tab, control enfocado visible; prueba humana con rueda, barras y teclado. No hacer scroll hacia resultado en cada refresco |
| Escalado reduce área lógica | Tamaño inicial acotado al monitor, mínimo sin imposición absoluta y viewport de seguridad; capturar resolución y escala reales |
| Texto/modelos extensos ocupan EDT | Mantener cálculo fuera de EDT y coalescencia existente; probar N=100. Sólo si falla el objetivo, documentar una corrección localizada antes de ampliar alcance |
| Carrera de nueva corrida/cierre/refresco | Conservar generación/identidad/revisiones, doble inicio y cierre conjunto; reusar tests existentes |
| Race conditions / inversión de locks | Coordinador por contexto y monitor del motor separados; no invocar fachada con monitor retenido, no esperar Swing/futures bajo lock |
| Starvation y espera indefinida | Fin sin progreso cuando no queda actividad útil; pendientes conservadas. Fair lock no garantiza recursos ni plazo por orden; JDBC puede no retornar |
| Fuga de trabajadores/conexiones | Cierre por propietario, drenaje comprobado y ancla antes de guardia; fallo retiene protección, no se fuerza éxito |
| H4 bloquea nominal o empaquetado | Guardar evidencia y parar la parte bloqueada; no recuperar, adoptar o relajar protecciones; pedir decisión sobre alcance si hace falta |
| Plugin offline ausente o JAR incompleto | Un intento acotado con assembly ya previsto; conservar script validado y CA-25 parcial si no viable |
| Falsa aceptación global | Matriz individual, negativos históricos conservados y aprobación humana separada; sin porcentajes de confiabilidad |

Alternativas mínimas: sólo agregar scroll a las filas actuales conserva la mala distribución y altura preferida, por lo que se recomienda además reorganizar sus grupos. Aumentar la ventana no resuelve escalado; no se adopta. Rediseñar todas las pantallas o introducir otra biblioteca resulta desproporcionado. Para ejecución, classpath con script es alternativa válida de uso académico, pero deja la brecha de JAR del PLAN.

| Decisión material | Recomendación concreta | Estado / consecuencia |
|---|---|---|
| H7-D01: JAR local condicionado al entorno offline y contratos actuales | Aprobar el intento acotado con assembly 3.7.1, Java 21 y raíz de proyecto obligatoria; si no se resuelve offline o exige alterar H4, diferir documentando CA-25 parcial y conservar script | Pendiente junto con el diseño. La aprobación integral puede ratificar esta condición sin autorizaciones intermedias por detalles triviales. Un cambio de condición, descarga o distribución independiente requeriría otra decisión |

No hay decisión nueva sobre reglas, tecnologías, cinco estados o recuperación. El layout se somete a revisión integral sin preguntas sobre cada gestor/alineación. Si surge impedimento material, el reporte debe consignar hecho, consecuencia, alternativa y decisión requerida; no inventar acuerdo ni proseguir por fuera del diseño.

## 11. Criterios verificables de salida H7-A1…H7-A8

| ID | Condición y evidencia futura | Límite / autoridad |
|---|---|---|
| H7-A1 | Todos los controles/textos de Simulación accesibles normal/reducida/maximizada; layout tests y checklist humano Windows con escala/resolución/capturas | Geometría automática no sustituye accesibilidad real |
| H7-A2 | Recorrido T03 completo con propuesta exacta, confirmación humana, terminales, historial y recursos liberados; H4 parcial visible | Nominal no aprueba recuperación avanzada ni CA-16/17 |
| H7-A3 | T04/T05 con parámetros exactos, carga reproducible, suficiente completada y limitado terminado con pendientes imposibles informadas; resultados conservados | No exigir igualdad de intercalados ni aceptar timeout como SIN_PROGRESO |
| H7-A4 | Captura operativa sin cambios atribuibles a simulación, contextos distintos y UI utilizable; cierre conjunto/EDT acreditados en T06/T07/T08 | No ocultar propietario retenido ni liberar por suposición |
| H7-A5 | Selección focal H5/H6 y layout ejecutada externamente, con selector, EXIT, logs y XML; fallos/regresiones/H4 informados | No extrapolar a toda la suite; fallos nuevos materiales impiden afirmar salida técnica satisfecha |
| H7-A6 | Matriz con las 26 filas, estados fundados y enlaces efectivos a evidencia/faltantes, incluidos CA-16/17/25 | Se admite documentar brechas en H7 acotado, nunca declararlas conformes |
| H7-A7 | Guía local, checklist y reporte creados; JAR probado realmente offline o diferimiento explícito con causa y CA-25 parcial | BUILD SUCCESS o presencia de JAR no acreditan apertura/cierre |
| H7-A8 | Revisión y aprobación humana final del alcance acotado, con defectos y pendientes aceptados explícitamente para el cierre académico | Acto humano separado de validación técnica. No autoriza Git: eventual commit local exige autorización posterior específica; publicación grupal independiente |

El reporte posterior distinguirá criterios técnicos verificados, parciales/fallidos/no ejecutados y decisión humana final. Sin H7-A8 no hay cierre humano de H7. Aunque se acepten brechas académicas, el MVP original no queda automáticamente aprobado.

## 12. Trazabilidad, telemetría y parada de esta entrega

Secuencia de esta solicitud: autorización humana para ejecutar exclusivamente P03-D-H7 → lectura del prompt y aprobación de alcance → inspección estática → propuesta v0.1. No hubo refinamiento adicional acordado ni implementación.

En ejecución futura, `H7-REPORTE-IMPLEMENTACION.md` debe referenciar los JSONL/logs originales conservados por el lanzador externo, indicando ruta real y relación con prompt/ejecución; si no se conoce, anotar pendiente sin fabricar una ruta. Antecedentes localizados por nombre: `metricas/logs/h6/P03-D-H6-ejecucion.jsonl`, `P03-H6-integral.jsonl` y `P03-H6-continuacion-20261015-105802.jsonl`. No se analizó ni calculó su consumo. H7 no agrega recolectores al producto ni copia telemetría externa por su cuenta. No atribuir a Codex intervenciones humanas, ChatGPT, PowerShell, Git, Maven o pruebas humanas.

Tokens, CPU, RAM, planillas, rúbricas e informe académico corresponden a H8 y no se calculan aquí. Las estadísticas funcionales RF-18 permanecen en H6. Se conservan prompts, refinamientos y aprobaciones históricos; no se alteran consignas ni rúbricas.

**Único archivo creado en esta ejecución: `IMPLEM-H7.md`. No se modificó código, tests, SQL, Maven, scripts, normativa, prompts ni configuraciones. No se ejecutaron Java, Maven, pruebas, interfaces, escenarios, JAR ni operaciones Git; no se abrió ninguna base ni hubo acciones externas o publicación remota.** La comprobación realizada fue exclusivamente lectura de fuentes/documentos y revisión documental de esta propuesta.

**PARADA:** queda pendiente la aprobación humana del diseño, incluida H7-D01, y un prompt técnico de implementación independiente. No se crea ni ejecuta ese prompt en esta entrega. H4 continúa PARCIAL y NO APROBADO.
