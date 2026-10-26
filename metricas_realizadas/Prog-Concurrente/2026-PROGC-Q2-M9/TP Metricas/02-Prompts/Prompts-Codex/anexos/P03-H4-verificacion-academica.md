# P03-H4 — Verificación y estabilización nominal académica (una ejecución)

| Control | Valor |
|---|---|
| Versión | 0.1 |
| Fecha | 14/10/2026 |
| Estado | **Aprobado en objetivo por el responsable: cambio a prototipo académico; ejecución local por Codex pendiente** |
| Contexto | ReparaBarrio; continuación de P03 tras cuatro intentos H4 y fallos de recuperación documentados |
| Fuente de alcance | `docs/DECISION-ALCANCE-ACADEMICO-H4.md` |
| Resultado esperado | Un recorrido nominal verificable con datos descartables y un informe de evidencias; NO cerrar H4 completo |

## Instrucción a Codex

Leé íntegramente este prompt, `docs/DECISION-ALCANCE-ACADEMICO-H4.md`, `SPEC.md` v0.2, `PLAN.md` v0.2, `IMPLEM-H4.md` v0.2, y revisá el código vigente H1–H4, las pruebas, `H4-REPORTE-IMPLEMENTACION.md`, `H4-REPORTE-CORRECCION.md` y logs externos de H4 disponibles. Conservá intactos los documentos aprobados y el historial de evidencia.

**Cambio de finalidad:** el responsable decidió priorizar el estudio académico de vibe coding + SDD + programación concurrente. No se busca despliegue en producción, ni una demo formal. **No sigas investigando H4-T02/T03/T09 ni el reproductor transaccional del antiguo intento 5** salvo que una causa afecte directamente a un uso nominal seguro. Estos fallos deben quedar registrados como abiertos, no escondidos.

Tu único encargo es **comprobar que el núcleo actual permite operar normalmente con un contexto NUEVO y descartable, durante una sesión sin interrupciones forzadas**, y aplicar la corrección mínima indispensable si descubrieras un defecto específico en ese recorrido. No avanzar a implementar Swing H5 ni simulación H6 en esta ejecución; dejar el núcleo preparado como dependencia para esos hitos.

### A. Escenario de aceptación nominal

Usando las APIs reales de aplicación y persistencia, construí o reutilizá la menor cantidad de tests JUnit posible para verificar:

1. Apertura de un contexto nuevo y exclusivo dentro de `target/h4-*`, con guardia válida, sin tocar datos operativos.
2. Registro y consulta de recursos físicos: técnicos, puestos y unidades de herramientas de tipos aprobados.
3. Registro de varias órdenes con/sin herramientas, propuestas de la primera factible por secuencia y confirmación de unidades físicas exactas.
4. Dos órdenes `EN_CURSO` simultáneas con técnicos/puestos/herramientas disjuntos, comprobando que nadie usa dos veces una unidad; no confundir operaciones serializadas bajo el coordinador con trabajo físico simultáneo.
5. Intento incompatible o duplicado correctamente rechazado, sin efecto parcial sobre estado, asignaciones ni historial.
6. Finalización/fallo válidos, liberación propia de recursos, avance posterior de una orden que esperaba, y consulta coherente de estado/historial.
7. Cierre limpio y seguro del contexto en ese recorrido. **La reapertura, migración o recuperación tras caída sigue fuera de la meta nominal**; registrá cualquier ejecución adicional como evidencia independiente, sin simular su aprobación.

Diferenciá claramente: **concurrencia real de solicitudes**, **serialización transaccional** y **simulación futura de duración física**. No confundas un test de asignaciones concurrentes con H6 implementado.

### B. Estrategia de mínima intervención

- Primero examiná suites ya existentes y sus resultados; aprovechá pruebas que acrediten el recorrido sin crear una plataforma nueva.
- Agregá un test de aceptación nominal pequeño sólo si no existe cobertura explícita end-to-end. Preferí una clase JUnit independiente que pueda ejecutarse por nombre desde PowerShell sin ejecutar toda la regresión H4. Si creás una clase nueva para el recorrido, llamala `RecorridoAcademicoNominalTest` y conservá una sola prueba integral con aserciones significativas por paso.
- Si el caso nominal pasa sin cambios de producción: **no modifiques producción**. Registrá la evidencia y terminá.
- Si falla, localizá la causa; aplicá sólo una corrección **segura, necesaria, justificada y limitada al uso nominal**. No bypass de salud, guardia, SQL, identidad, intención ni integridad referencial. No declares seguro el recorrido si necesitás un bypass para hacerlo pasar.
- No eliminar ni relajar las 217 pruebas históricas ni silenciar su ejecución. **No gastar la ejecución en repetir regresiones extensas de recuperación que sabemos fallan.** Conservá las evidencias de esas fallas para el informe.
- Si no podés garantizar funcionamiento nominal sin suprimir protecciones, **detenete sin cambios destructivos**: informa el obstáculo concreto y una alternativa acotada para decisión humana. Nunca conviertas H4 en aprobado por decreto.

### C. Herramientas y límites

- Java 21, Maven, JUnit y H2 existentes. No modificar `pom.xml`, JDK ni configuraciones globales.
- Codex CLI bajo MXC puede devolver H2 `90097-232` aunque Windows externo funcione. Si reaparece, no lo «arregles» mediante permisos o cambios artificiales: compilá si es posible, anotá `NO VERIFICADO EN MXC` y proporcioná un comando Maven exacto, individual, para que el responsable lo ejecute en PowerShell Windows.
- **Prohibido** abrir, crear, migrar, restaurar, copiar, borrar o consultar bases reales bajo `datos/operativo` o fuera de fixtures nuevos `target/h4-*`. No ejecutar terminaciones de procesos ajenos ni alterar archivos ya existentes del usuario.
- No realizar commits, staging ni operaciones remotas; no crear Git remote y no contactar el GitHub grupal. No modificar documentos aprobados ni prompts anteriores. No implementar UI ni motor de simulación.
- Preservar logs, CSV de tokens y pruebas antiguas. Usar logs nuevos en UTF-8. Si se crea un informe, usar **`H4-REPORTE-ALCANCE-ACADEMICO.md`**, sin reemplazar reportes anteriores.

### D. Entrega final obligatoria

En una sola ejecución entregá:

1. Estado `NOMINAL ACREDITADO EN MXC` / `IMPLEMENTADO, PENDIENTE DE VALIDACIÓN EXTERNA` / `NO ACREDITADO`, conforme a lo realmente ejecutado.
2. Matriz de siete pasos nominales, cada uno con evidencia (test, método, aserción) y resultado; número real de tests, fallos, errores y código de salida.
3. Lista de archivos creados/modificados y justificación precisa de cada cambio; si no cambió producción, aclararlo.
4. Comando **PowerShell Windows de una sola prueba o clase focal**, ejecutable por el responsable, con `-Dsurefire.timeout=900`, que acredite el recorrido. Si hubiera pasos no automatizados, identificarlos.
5. Registro separado de riesgos H4-T02/T03/T09 y de la intermitencia histórica H3-T05, sin modificarlos para hacer pasar el nominal; aclarar que H4 integral, CA-16/17 y aptitud productiva siguen NO APROBADOS.
6. Consideraciones mínimas para integrar H5/H6 sin ocultar fallos de recuperación; particularmente cómo impedir que una UI habilite un contexto BLOQUEADO y cómo aislar simulación de la base operativa.

**Detenerse al terminar este encargo.** No solicitar aprobaciones internas entre pasos ni lanzar una implementación H5/H6. El responsable decidirá sobre H5/H6 después de la validación externa nominal y de registrar el estado parcial de H4.
