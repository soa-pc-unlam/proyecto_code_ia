# P06 — INC-SIM-01 — Única corrección focal de primer clic y scroll en Simulación

## Contexto comprobado

Proyecto ReparaBarrio, Swing / Java 21. Se reproduce manualmente en Windows al usar el formulario de la pestaña «Simulación»:

1. En una sesión académica descartable, ingresar **8** en «Órdenes a generar» sin confirmar con Enter ni Tab.
2. Desplazarse con la rueda del mouse hasta que sea visible «Iniciar escenario nuevo»; al aparecer, el formulario se reacomoda/desplaza horizontalmente.
3. Primer clic físico: **no inicia** la simulación y se mueve el formulario.
4. Segundo clic físico: **sí inicia** la simulación de ocho órdenes, demostrada en historial.

P05 introdujo `PanelSimulacionInicioTest.java`. Su `doClick(0)` acredita una activación programática, pero **no** representa un ciclo real `mousePressed/mouseReleased`, foco y scroll. P05 obtuvo 7 tests correctos y 2 gráficos omitidos, sin modificar producción. Se investigaron `enfocarVisible()` / `scrollRectToVisible()` con listeners aparentemente instalados en `PanelSimulacion` y `VentanaPrincipal`, así como la ausencia de `commitEdit()` explícito en algunos `JSpinner`. **Son hipótesis, no causas confirmadas**.

## Objetivo único

Eliminar el defecto por el cual el **primer clic físico** en «Iniciar escenario nuevo» no inicia, **sin generar doble ejecución** ni alterar la lógica funcional. Mantener navegación accesible por teclado y foco visible. No modificar el diseño SDD de negocio, persistencia, recuperación avanzada H4, el generador de simulaciones ni recursos.

## Trabajo permitido (máximo un ciclo de implementación y una regresión focal)

1. Leer los componentes `PanelSimulacion.java`, `VentanaPrincipal.java`, presentador y los tests `PanelSimulacionInicioTest`, `PanelSimulacionLayoutTest`, `VentanaPrincipalLayoutTest`, `PresentadorSimulacionTest` y el registro `docs/INC-SIM-01-INICIO-ESCENARIO-2026-10-16.md` si existe.
2. Determinar el orden de registro/ejecución de listeners de foco, scroll y acción; examinar qué componentes llaman a `scrollRectToVisible`, cuándo se invalida/revalida el layout y si el botón se desplaza entre presión y liberación. Identificar si se instala redundante el mismo comportamiento. Verificar la lectura de `JSpinner` editado sin Enter. No dar por probada una causa sin evidencia.
3. Si hay una causa razonablemente demostrable en el código, aplicar **el cambio mínimo** en GUI/presentador para que el scroll no sea desencadenado por la interacción con el botón y el primer clic ejecute una sola vez. Preservar comportamiento accesible: no suprimir arbitrariamente el foco o la navegación. Si es necesario `commitEdit()` de spinners, agregar validación robusta y mensajes de entrada incorrecta sin cambiar el alcance. **No introducir doble clic artificial, timers de reintento ni llamadas duplicadas a iniciar.**
4. Agregar o ajustar una prueba focal para el mecanismo causal, preferentemente verificable en entorno headless. Un test que solo haga `doClick()` **no demuestra** corrección del gesto físico; señalar esta limitación explícitamente. Si una prueba Robot con GUI real no puede ejecutarse desde Codex, dejarla para **una sola comprobación humana de 8 órdenes** al final.
5. Ejecutar una sola regresión Maven **offline, Java 21, sin `clean`**, limitada a los tests de GUI/presentador indicados. Si el entorno restringido impide validar, registrar la limitación; no reinterpretar tests omitidos como aprobados.
6. Documentar en `docs/INC-SIM-01-INICIO-ESCENARIO-2026-10-16.md` el diagnóstico, cambio, tests y el estado final `CORREGIDO PENDIENTE CONFIRMACION MANUAL` o `ABIERTO SIN CORRECCION DEMOSTRABLE`. Actualizar la propuesta de cierre solamente si corresponde, conservando la trazabilidad.

## Límites de intervención

- Como máximo **una propuesta de corrección** y **una regresión focal**. No iniciar una secuencia de reparaciones iterativas si falla.
- No tocar código H2/H3/H4 ni reabrir la recuperación avanzada.
- No ejecutar `mvn clean`, ni eliminar bases o carpetas de sesión, ni crear commits/push/PR.
- No sobrescribir, revertir ni reformatear cambios locales preexistentes. `PanelSimulacion.java` y `VentanaPrincipal.java` ya tenían cambios **antes** de P05/P06.
- Conservar registros JSONL de Codex y reportar tokens **solo como datos de log existentes**, sin hacer cálculos académicos agregados.
- Si el problema no puede identificarse de manera razonable, **no modificar producción**: informar bloqueo y recomendar cierre con desviación conocida.

## Informe final obligatorio

Dar salida concisa en español indicando:

1. Diagnóstico: confirmado o hipótesis, con archivos/métodos.
2. Archivos modificados por P06 (distinguir modificaciones antiguas).
3. Tests ejecutados: comando, cantidades aprobadas/fallidas/omitidas, `EXIT`.
4. Si la incidencia está pendiente de confirmación mediante un único clic físico en Windows.
5. Riesgos y límites. Ningún commit ni push.

Tras esto, detenerse. No solicitar nuevas implementaciones automáticamente.
