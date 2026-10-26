# P04 — Codex: auditoría final acotada y cierre documental académico de ReparaBarrio

**Autoridad:** instrucción humana de cierre académico, 16/10/2026. **Modo obligatorio: auditoría, validación y documentación; no refactorizar el producto ni arreglar H4.**

## Finalidad
Concluir la validación *académica acotada* de ReparaBarrio con la evidencia ya obtenida. Priorizar trazabilidad, pruebas automáticas disponibles y reporte honesto sobre perfeccionismo. En este turno NO se autoriza declarar conformidad integral de SPEC/PLAN ni aprobar H4, CA-16/17 o el JAR. La intervención humana adicional debe tender a cero; no solicitar repetir corridas gráficas ya documentadas.

## Documentos a leer primero
1. `AGENTS.md`, `SPEC.md`, `PLAN.md`, `IMPLEM-H7.md` (si existe), y aprobaciones de alcance/diseño de H7.
2. `docs/H7-MATRIZ-ACEPTACION.md`, `docs/H7-CHECKLIST-WINDOWS.md`, `docs/H7-GUIA-EJECUCION-LOCAL.md`, junto con reportes H5/H6/H7 y decisión académica H4 existentes. Los archivos pueden estar en ubicaciones distintas: localizarlos; no inventar rutas.
3. Identificar tests realmente presentes, scripts de ejecución y regresión vigente; no afirmar tests no ejecutados.

## Evidencia humana que aporta el responsable (no fabricada por Codex)
Fuente de 32 páginas con 64 capturas: `Capturas a partir de Pruebas simulación - 2.2.docx`, conservado externamente; si no está copiado al repositorio, registrar que se auditó por el responsable/ChatGPT y **no afirmar que Codex inspeccionó el archivo físico**. El cierre humano nominal H5 y el perfil Suficiente están documentados en evidencias anteriores.

- H7-T05 / Limitado, N=100: 54 PENDIENTE, 0 EN_CURSO, 42 FINALIZADA, 4 FALLIDA, 0 CANCELADA; 0 futuras, 0 registros en vuelo, 0 trabajadores; terminal «Terminado con pendientes sin posibilidad de progreso»; duración visible 10,525 s. Evidencia página 14 del Word, y configuración previa en páginas 1–5.
- H7-T07 / consulta sin mutación: el usuario capturó consultas posteriores, manteniendo el resultado; páginas 14–15. Es verificación visual, no demostración matemática de inmutabilidad.
- H7-T11 / Z-01: N=0, estados a cero, sin inicios; promedio N/D; páginas 16–22.
- H7-T08 / C-01: solicitó cerrar durante actividad, la ventana se cerró rápidamente sin diálogo; páginas 23–26. **No demuestra por sí mismo drenaje correcto, fin de cada trabajador ni EXIT**; mantener PARCIAL hasta contrastar tests/logs.
- H7-T11 / Z-02: N=8, inventario cero, 8 PENDIENTE, 0 EN_CURSO, 0 FINALIZADA, 0 FALLIDA, 0 CANCELADA; 0 futuras, 0 registros en vuelo, 0 trabajadores; final «sin posibilidad de progreso», promedio N/D; páginas 27–32.
- INC-SIM-01: el usuario observa repetidamente que «Iniciar escenario nuevo» en la primera pulsación mueve/reacomoda el panel sin iniciar, y la segunda pulsación sí inicia; páginas 30–31. **Incidencia funcional/interfaz reproducida manualmente, mecanismo causal no diagnosticado.** No normalizar el doble clic ni declarar reparada.
- H7-T04 / Suficiente, corrida anterior ya documentada: N=100; 93 FINALIZADA, 7 FALLIDA, 0 PENDIENTE, 0 EN_CURSO. Identificar evidencia histórica si está disponible; si no, declarar que los valores provienen de revisión previa y no de ejecución Codex actual.
- H7-T03 / H5 recorrido nominal anterior: órdenes IMPOSIBLE, A, B, C; dos en curso disjuntas, A finalizada, B fallida, C cancelada, IMPOSIBLE pendiente por D1 fuera de servicio; historial secuencial y recursos liberados. Registrar como verificación humana histórica, no repetir por sistema.

## Límites y resguardos inviolables
- No modificar **código de producto, pom.xml, scripts, tests históricos, SPEC.md, PLAN.md, ni decisiones/actas aprobadas**. Sólo se permiten documentos NUEVOS de resultados/cierre y logs nuevos con nombres no colisionantes. Si un cambio de código fuera imprescindible, **detenerse y recomendar un parche aislado a aprobación humana**, sin aplicarlo.
- NO arreglar H4, NO alterar `RecuperacionH4Test`, NO debilitar aserciones, NO suprimir tests fallidos y NO afirmar CA-17 conforme. H4 sigue PARCIAL, NO APROBADO; CA-17 FALLIDO por H4-T02/T03/T09. CA-16 PARCIAL y CA-25 PARCIAL (sin JAR validado).
- No hacer `mvn clean`, no borrar ni reutilizar H2 de sesiones humanas, no abrir ni migrar `datos/operativo` o bases previas. No descargar dependencias, no acceder a red. Usar JDK 21 explícito, H2/Maven locales ya documentados y opciones Maven offline. El responsable declara la GUI cerrada; verificar ausencia de proceso antes de lanzar tests, sin matar procesos ajenos.
- No ejecutar ni abrir la GUI automáticamente; no generar más escenarios manuales. No hacer `git add`, `commit`, `push`, `merge`, no instalar plugins ni publicar nada. `git status --short` y `git diff --stat` sólo para registrar cambios son admisibles.
- No calcular todavía métricas académicas de vibecoding (tokens, costos, eficiencia, promedios): **preservar JSONL/logs y solo constatar que existen y no se modifican**.

## Ejecución única y limitada
1. Leer los archivos, comprobar el estado de trabajo y localizar la selección de regresión H5/H6/H7 usada en Windows y los dos tests de layout. Realizar UNA ejecución automatizada focal, local/offline, sólo si no toca bases de usuario; capturar COMANDO exacto, Java/Maven/H2, fecha/hora, EXIT inmediatamente y XML/log en carpeta nueva con sufijo único. Contar ejecutados/aprobados/fallidos/errores/omitidos. Si el entorno carece de capacidad gráfica para Swing, registrar claramente los tests NO EJECUTADOS/OMITIDOS, no contarlos como éxito.
2. No ejecutar la suite H4 completa sólo por perseguir el 100%. Si aparece el problema histórico H2 90097-232 o un fallo nuevo, registrar evidencia literal, clasificar (producto/precondición/no determinado) y NO iniciar ciclos iterativos de arreglos. Máximo una corrida adicional **sin cambios de código**, únicamente si corrige un error evidente de invocación, ruta o JDK.
3. En lectura estática, identificar la ruta exacta del botón «Iniciar escenario nuevo» (listener/EDT/commit de JSpinner/foco/layout), formular como máximo tres hipótesis y proponer un test de regresión. NO modificar la interfaz en esta orden ni afirmar causalidad sin prueba.
4. Revisar la matriz completa CA-01 a CA-26 y clasificar cada fila según evidencia nueva más historial; mantener los estados parciales/fallidos. Separar 'probado automático', 'observado humano', 'histórico', 'no ejecutado', 'defecto abierto'. No reescribir retroactivamente documentos históricos; registrar una **matriz complementaria de cierre**, sin alterar la norma original.
5. Registrar explícitamente lo que falta: H4 recuperación avanzada, CA-16, CA-17, CA-25 JAR, posible H3-T05 intermitente, INC-SIM-01, cobertura de escalas/layout que no se acreditó y cierre durante actividad sólo observado visualmente. Nada de eso impide documentar un **cierre académico acotado con desviaciones explícitas**, si la autoridad humana lo aprueba.
6. Generar bajo `docs/` (o ruta de docs ya vigente) archivos NUEVOS de texto Markdown:
   - `H7-RESULTADOS-MANUALES-2026-10-16.md` (resumen por prueba, Word externo por página, estado y límites; mencionar el origen humano).
   - `H7-CIERRE-ACADEMICO-PROPUESTA-2026-10-16.md` (alcance cumplido, pruebas automáticas verificadas, matriz de 26 CA con estados, brechas, decisión pendiente de firma).
   - `INC-SIM-01-INICIO-ESCENARIO-2026-10-16.md` (síntoma y propuesta de prueba, sin parche).
   - Log nuevo de esta ejecución, si realmente ocurrió.
7. En terminal, emitir un resumen final BREVE: documentos creados, comandos y resultados reales, observaciones abiertas, propuesta 'CERRAR ALCANCE ACADÉMICO CON DESVIACIONES' / 'BLOQUEADO POR REGRESIÓN MATERIAL' con justificación. No autootorgar la aprobación humana final H7-A8.

## Condición de parada
Una validación automatizada focal y sus documentos; como máximo una segunda corrida por error mecánico de invocación, sin cambios. **NO hacer un tercer intento, no perseguir perfección de H4 ni JAR sin autorización.** Si los tests nuevos fallan o faltan, consignarlo y detenerse. La decisión final de cierre, aceptación de riesgos y publicación son exclusivamente humanas.
