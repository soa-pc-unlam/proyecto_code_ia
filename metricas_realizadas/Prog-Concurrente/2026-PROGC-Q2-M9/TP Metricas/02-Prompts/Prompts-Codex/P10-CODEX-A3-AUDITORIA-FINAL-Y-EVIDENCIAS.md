# P10 | A3: auditoría final focalizada y registro de aceptación manual

**Proyecto:** APO1 ReparaBarrio (UNLaM, Programación Concurrente, 2026)  
**Línea:** MVP, alternativa A  
**Estado:** AUTORIZADO PARA AUDITORÍA ACOTADA; NO AUTORIZA A APROBAR A3 POR CUENTA PROPIA  
**Antecedentes:** P07, P08 y P09; `docs/A3-EMPAQUETADO-PORTABLE.md`  
**Fecha de evidencia humana:** 2026-10-16  
**Objeto:** decidir si A3 reúne las condiciones técnicas para una aprobación humana explícita, sin desarrollar funciones nuevas.

## 1. Hechos de entrada que no deben confundirse

P09 finalizó con `EXIT=0` y comunicó **resultado PARCIAL**: creó `target/distribucion/ReparaBarrio-portable.zip`, `target/distribucion/ReparaBarrio-portable/app/ReparaBarrio.jar` y `target/distribucion/ReparaBarrio-portable/Iniciar-ReparaBarrio.cmd`. Incorporó `ContextoEjecucion` inmutable para transportar expresamente el modo portable a través del arranque, Swing y simulación. La distribución requiere Java 21 externo e incluye H2.

P09 informó una ronda final de **29 pruebas, 1 falla, 0 errores y 2 omisiones**. La falla se atribuyó a un escenario H2 «solo lectura» y, según el informe, también se reprodujo con la fábrica del código de `HEAD`. Las omisiones se atribuyeron a permisos de creación/uso de enlaces. Esta descripción es una *afirmación que debe corroborarse contra los registros*, no una dispensa automática de los problemas.

**Evidencia reportada directamente por el usuario el 2026-10-16:** realizó la prueba gráfica del ZIP extraído fuera del repositorio, en una carpeta con espacios, empleando el lanzador de Windows y Java 21. Reportó **OK** en los cinco puntos: (1) apertura de la ventana principal; (2) aparición y aceptación del consentimiento académico; (3) registro de una orden; (4) inicio y finalización de una simulación corta; (5) cierre mediante X sin errores visibles.

Registrar esto como **validación manual declarada por el usuario**. No atribuir esa prueba visual a Codex ni inventar capturas, grabaciones o automatizaciones.

**Estados ajenos:** H4 = PARCIAL / NO APROBADO. Aceptación integral = PENDIENTE. No fusionar estos hitos con A3.

## 2. Permisos y prohibiciones

### Se autoriza

- Inspección y análisis de código, pruebas, documentación, artefactos, informes existentes y estado Git.
- Comandos **no destructivos** para verificar inventario del ZIP/JAR, firmas/contratos de ejecución, resultados y trazabilidad.
- Inspección de `target/p09-evidencia/`, reportes de pruebas y registros disponibles, incluidos JSONL y stderr cuando existan.
- Una **única reejecución focal** de alguna prueba necesaria para descartar una duda material, si puede hacerse de manera segura y sin modificar datos de usuario. Si no es necesario, no reejecutar.
- **Única edición documental permitida:** actualizar `docs/A3-EMPAQUETADO-PORTABLE.md` para incorporar los resultados manuales declarados, el análisis de los hallazgos y el estado de aceptación propuesto. No borrar información histórica.

### No se autoriza

- Cambiar código Java, pruebas, configuración, Maven, scripts, distribución ni empaquetado.
- Crear nuevos prompts, fuentes, suites de pruebas o lanzadores.
- Refactorizar, corregir H2, H4 u otra área; tampoco alterar los flujos de concurrencia, planificación o simulación.
- Realizar `git add`, `commit`, `push`, `stash`, `reset`, `checkout`, `clean`, rebase u operaciones que modifiquen el árbol de trabajo o su historial.
- Borrar datos, sesiones, reportes, evidencias o archivos no rastreados; no ejecutar `mvn clean`.
- Cambiar configuración de seguridad de Windows, habilitar permisos de enlaces simbólicos ni instalar dependencias.
- Calcular métricas finales. Conservar la captura externa de eventos y tokens de Codex en JSONL sin modificarla.
- Dar A3 por **APROBADO** sin decisión expresa del usuario.

## 3. Examen puntual de la falla H2

1. Localizar la prueba exacta: clase, método, aserción fallida, traza, resultado esperado y observado.
2. Recuperar el comando de ejecución y los archivos de evidencia de P09.
3. Examinar la evidencia concreta de reproducción contra `HEAD` y explicar qué demuestra y qué no. No afirmar que la falla está probada como preexistente si no hay evidencia verificable.
4. Determinar si la falla puede afectar las propiedades que A3 pretende garantizar (sesión académica nueva, acceso a H2, rutas y escritura/lectura en el paquete portable) o si corresponde estrictamente a un problema independiente de H2.
5. Etiquetar la conclusión: `FUERA_DEL_ALCANCE_ACREDITADO`, `INCIERTO` o `IMPACTA_A3`, con fundamento explícito. No corregirla aquí.

## 4. Examen puntual de las dos omisiones

1. Identificar las **dos pruebas exactas** omitidas, su clase/método, motivo real de omisión y condición de entorno.
2. Precisar qué controles habrían verificado: protección de rutas, enlaces simbólicos, escape de raíz, persistencia, guardias u otro.
3. Buscar evidencia alternativa ya existente, si la hay; no suponer que una prueba omitida pasó.
4. Determinar si falta una prueba crítica de seguridad del modo portable o si la omisión puede documentarse como limitación de entorno con riesgo residual acotado.
5. Etiquetar cada caso: `CUBIERTO_POR_OTRA_EVIDENCIA`, `LIMITACION_DOCUMENTADA` o `BLOQUEANTE`.

## 5. Comprobaciones de distribución e integridad

Sin reconstruir ni sobrescribir el paquete:

- Comprobar presencia real del ZIP, del JAR, del lanzador y de las dependencias indispensables, incluida H2.
- Examinar el **contenido** del ZIP para detectar ausencias o incorporaciones indebidas (`.git`, fuentes, sesiones privadas, logs de desarrollo, archivos locales de usuarios). Documentar qué se comprobó.
- Revisar el contrato `--portable` junto con `--academico-nuevo`, la validación normal sin `--portable`, el consentimiento y el recorrido de simulación. Evaluar con pruebas y código existentes; no inventar resultados.
- Identificar la ubicación efectiva de datos durante la ejecución portable y si se documenta en `LEEME.txt` o informe A3. No borrar los datos ya creados por la prueba manual.
- Registrar `git status --short` y `git diff --check`; distinguir archivos modificados por P09 de cambios previos, hasta donde la evidencia lo permita. Especial atención a los cambios Swing previos y a `PanelSimulacion.java`, que P09 declaró no haber alterado en bytes.
- Distinguir la compatibilidad validada (Windows + Java 21) de cualquier portabilidad no comprobada.

## 6. Registro de evidencia manual en la documentación

Actualizar exclusivamente `docs/A3-EMPAQUETADO-PORTABLE.md` agregando una sección con fecha **2026-10-16**, que deje claramente identificado:

- Entorno: ZIP extraído fuera del repositorio, carpeta con espacios, lanzador `.cmd`, Java 21, según procedimiento acordado.
- Origen de la información: **resultado informado por el usuario** (no prueba automatizada ni observación directa de Codex).
- Cinco verificaciones y sus cinco resultados `OK` exactamente como se describen en el apartado 1.
- Qué se validó y qué no: es una comprobación funcional GUI representativa, no una certificación exhaustiva de todas las rutas de seguridad o recuperación.
- Hallazgos técnicos de la falla y omisiones, referencias a evidencias y situación actual de aceptación.

Preservar íntegramente los resultados y observaciones históricos de P07, P08 y P09.

## 7. Criterio de dictamen (no equivale a aprobación)

Emitir **uno** de estos dictámenes, justificado:

- **APTO_PARA_ACEPTACION_A3:** todas las condiciones relevantes de A3 están acreditadas y las incidencias externas están delimitadas por evidencia.
- **APTO_CON_LIMITACIONES_A3:** funciona el caso representativo, pero quedan limitaciones identificadas que no invalidan el propósito acotado de A3; exponerlas y proponer aceptación explícita con reservas.
- **NO_APTO_AUN_A3:** hay una falla o incertidumbre relevante para seguridad, integridad o ejecución portable; especificar exactamente la evidencia o corrección mínima necesaria para resolverlo.

No maquillar el resultado de las 29 pruebas ni transformar las 2 omisiones en aprobaciones. Si la documentación o evidencia disponible no permite distinguir los casos, marcar `NO_APTO_AUN_A3` por incertidumbre concreta y formular una única petición focal de evidencia, **sin implementar correcciones**.

## 8. Entrega final de P10

Presentar una respuesta final en español que contenga:

1. Dictamen exacto y fundamentos.
2. Matriz breve de criterios A3: criterio, evidencia, estado y reserva.
3. Falla H2: nombre completo, prueba, evidencia de `HEAD`, impacto en A3 y acción diferida.
4. Dos omisiones: identificación y riesgos residuales por separado.
5. Resultado del inventario ZIP/JAR y validez de la evidencia GUI del usuario.
6. Documentos modificados (solo `docs/A3-EMPAQUETADO-PORTABLE.md`), y toda verificación no efectuada.
7. Resultado de `git diff --check`, `git status --short` y confirmación de que no hubo cambios en código ni Git.
8. Recomendación para decisión humana: **aceptar A3**, **aceptar A3 con reservas explícitas** o **mantener A3 pendiente**, sin decidir por el usuario.

Finalizar reiterando: **H4 = PARCIAL / NO APROBADO**, **aceptación integral = PENDIENTE**, **sin commits, pushes ni métricas finales**.
