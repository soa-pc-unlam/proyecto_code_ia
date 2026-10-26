# P05 — ReparaBarrio — INC-SIM-01: corrección focal del primer clic

## Contexto comprobado

Estamos terminando el MVP académico acotado (alternativa A) de ReparaBarrio, Java 21 / Maven / Swing. El usuario observó repetidamente en «Simulación» que la **primera pulsación** de «Iniciar escenario nuevo» reacomoda o desplaza el formulario hacia la izquierda **sin iniciar** la simulación; la **segunda pulsación** sí la inicia. La incidencia está en `docs/INC-SIM-01-INICIO-ESCENARIO-2026-10-16.md`. No presupongas la causa técnica (foco o confirmación de editor, rediseño del layout, listeners, estado del botón, etc.).

**Muy importante:** el 2026-10-16 a las 14:20 se ejecutaron **67 pruebas, cero fallos, BUILD SUCCESS** en Windows con Maven offline y JDK 21, incluyendo H2/H3. Los fallos de H2 por base en solo lectura ocurrieron en una ejecución anterior en el sandbox de Codex. **No modificar H2/H3, persistencia ni H4** basándose en esos fallos restringidos.

## Restricciones

1. Alcance exclusivo: `INC-SIM-01` y la porción estrictamente necesaria de su interfaz Swing. No intervenir en H4, persistencia, modelo de dominio, `pom.xml`, métricas académicas, empaquetado ni GitHub.
2. **Antes de cualquier modificación**, ejecutar `git status --short` y consultar `git diff` de los archivos de la interfaz. `PanelSimulacion.java` y `VentanaPrincipal.java` ya tienen **modificaciones locales sin commit**, y hay tests y documentos nuevos. Preservar todo lo existente. No hacer `reset`, `checkout`, `clean`, revertir cambios ajenos, formateo masivo ni reemplazar archivos enteros.
3. Prohibido hacer `git add`, `git commit`, `git push`, borrar archivos o bases H2, ejecutar `mvn clean`, descargar dependencias o modificar configuración global.
4. Máximo **un parche focal** y **una revisión limitada** de ese parche si fallan tests de UI. Si no se puede sustentar la causa, no forzar una corrección: informar hipótesis y cómo comprobarla.
5. Ejecutar pruebas automáticas pequeñas relacionadas con la interfaz (JUnit, headless si corresponde), sin lanzar la batería larga H2/H3 ni repetir 100 órdenes. Si el sandbox impide ejecutar tests, informar bloqueo sin atribuirlo al código.

## Tareas

**A. Diagnosticar.** Leer la incidencia y examinar `PanelSimulacion`, `VentanaPrincipal` y los tests pertinentes, en especial la secuencia: edición y confirmación de parámetros (p. ej. `JSpinner`), recepción de la primera pulsación, acción que inicia el escenario, estado visible, y cambios de layout (`revalidate`, `pack`, scroll, etc.). Explicar causa demostrada o hipótesis identificada.

**B. Corregir mínimamente si está justificado.** Una pulsación de «Iniciar escenario nuevo», con datos válidos y sin escenario activo, debe iniciar **exactamente una** simulación. No usar la simulación de un doble clic ni invocar dos veces el inicio. Mantener validaciones y controles contra ejecuciones duplicadas. No modificar otras conductas de H5/H6.

**C. Comprobar.** Incorporar o ajustar un test automatizado puntual si es viable verificar con precisión que la acción real de inicio se invoca una sola vez. Ejecutar solo tests focales de interfaz offline con JDK 21, evitando `mvn clean`. No declarar corregida la condición de foco/layout con una prueba headless si requiere además observación manual.

**D. Resumen en español.** Informar: (1) causa confirmada o hipótesis; (2) archivos y métodos cambiados, discriminando cambios preexistentes; (3) comandos de tests y resultados; (4) una verificación manual mínima, idealmente abrir GUI, configurar 8 órdenes y observar un único clic; (5) `git status --short` final. Sin commits ni pushes. No declarar aprobado H7-A8 ni el MVP integral.
