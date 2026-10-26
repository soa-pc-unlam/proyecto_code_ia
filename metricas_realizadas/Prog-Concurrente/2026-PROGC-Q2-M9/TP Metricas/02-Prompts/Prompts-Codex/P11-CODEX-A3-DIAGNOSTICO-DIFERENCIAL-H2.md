# P11 | A3 ReparaBarrio: diagnóstico diferencial focalizado de apertura H2

**Proyecto:** APO1 ReparaBarrio (UNLaM, Programación Concurrente, 2026)  
**Línea:** MVP, alternativa A  
**Estado:** PROPUESTA DE EJECUCIÓN BAJO AUTORIZACIÓN DEL USUARIO  
**Antecedentes:** P07 a P10; `docs/A3-EMPAQUETADO-PORTABLE.md`  
**Naturaleza:** investigación y verificación, **sin correcciones de código**  
**Propósito:** delimitar si la falla H2 de P09/P10 afecta el contrato de A3 o es específica del escenario de prueba.

## 1. Hechos de partida

P09 construyó la distribución portable de Windows con Java 21. Una comprobación fuera del repositorio completó una simulación mediante los JAR extraídos. El usuario informó el **2026-10-16** cinco verificaciones manuales correctas desde un ZIP extraído en carpeta con espacios: apertura GUI, consentimiento, registro de orden, simulación corta y cierre mediante X. Esta evidencia es **declarada por el usuario**, no observada directamente por Codex.

P10 emitió **`NO_APTO_AUN_A3`** porque no pudo delimitar la siguiente falla de P09:

- `ar.edu.unlam.reparabarrio.arranque.ContextoSimulacionTest.aislamientoRespectoArchivoNuevoDeFixture`.
- Aserción informada en línea 76: se esperaba `HABILITADO` y se obtuvo `BLOQUEADO` al abrir sesión académica nueva.
- Síntoma H2: error `90097-232`, intento de `INSERT` en `rb_sesiones` rechazado por base de solo lectura.
- P09 informó **29 pruebas, 1 falla, 0 errores, 2 omisiones**, con salida Maven 1. **No alterar ni reinterpretar este dato histórico**.
- Un ensayo que usó una fábrica idéntica a `HEAD` reprodujo el síntoma, pero otras clases eran actuales: **no constituye una ejecución integral del `HEAD`**.

Las dos pruebas omitidas por permisos para crear enlaces simbólicos son un asunto separado: `ArranqueAcademicoTest.rechazaEnlaceDeDirectorioNuevo_T10` y `PortableTest.targetEnlazadoSeRechazaSiElEntornoPermiteEnlaces`. Mantenerlas como omisiones y limitación de evidencia; no cambiar permisos ni forzar su ejecución.

## 2. Alcance autorizado

1. Leer `AGENTS.md`, la documentación A3 y las evidencias de P09/P10 relevantes, incluidos reportes de Surefire, stacktraces, `target/p09-evidencia/`, JSONL y stderr si están disponibles.
2. Inspeccionar sin editar `ContextoSimulacionTest`, `FabricaContextos`, `ArranqueAcademico`, `ContextoEjecucion`, persistencia H2 y el código de fixtures/configuración de pruebas.
3. Identificar con precisión qué ubicación, archivo H2, URL JDBC y modo de apertura intervienen en la prueba fallida; distinguir datos reales del proyecto, fixtures, archivos generados y ruta portable.
4. Revisar si existe algún estado residual, atributo de solo lectura, bloqueo, conflicto de conexiones, reutilización de contexto, parámetro de JDBC o secuencia de creación que explique el error. Tratar cada explicación como **hipótesis**, no hecho demostrado, hasta contar con evidencia.
5. Realizar, **solo si puede garantizarse aislamiento de datos**, hasta dos comprobaciones focales y reproducibles para discriminar: (a) caso fallido y (b) apertura académica representativa en una raíz fresca. Priorizar mecanismos de pruebas existentes; no añadir pruebas ni modificar fuentes. Registrar los comandos, las rutas, las condiciones iniciales, el resultado y los códigos de salida. Si no se puede asegurar aislamiento, **no ejecutar** y explicitar por qué.
6. Si se propone una comparación con `HEAD`, aclarar exactamente qué archivos/clases corresponden a `HEAD` y cuáles no. Una reproducción parcial no debe presentarse como baseline completo. Si no es viable construir un baseline realmente comparable **sin alterar el repositorio y sin red**, declararlo no verificable.
7. Comprobar que las rutas nuevas de diagnóstico no comparten base ni datos con las sesiones anteriores. No lanzar la interfaz gráfica, no rehacer la prueba manual ya aprobada y no reutilizar directorios de datos académicos existentes.

## 3. Restricciones estrictas

- **No modificar** Java, tests, scripts, configuración, empaquetado, ZIP, JAR, `SPEC.md`, `PLAN.md` ni documentación A3.
- No corregir H2 ni elaborar implementaciones nuevas en este paso.
- No ejecutar suites completas ni `mvn clean`; no actualizar dependencias ni usar red.
- No eliminar, restaurar, mover, sobrescribir o cambiar permisos/atributos de archivos, bases, sesiones, directorios ni evidencias existentes.
- No usar `git add`, `commit`, `push`, `stash`, `reset`, `checkout`, `clean`, rebase ni operaciones que modifiquen el índice o el historial.
- No intervenir H4, lógica de concurrencia, planificación, simulación, ni reabrir CA-16/CA-17.
- No habilitar enlaces simbólicos o privilegios de Windows; mantener las dos omisiones separadas de la falla H2.
- No calcular métricas finales. La captura de tokens y eventos la realiza externamente el comando Codex en el JSONL.
- **Únicos archivos de salida adicionales permitidos:** notas y evidencias del diagnóstico en `target/p11-evidencia/` y los artefactos temporales estrictamente necesarios de las comprobaciones autorizadas, en rutas nuevas, aisladas y claramente identificadas. No escribir en ubicaciones de datos de usuarios ni reutilizar carpetas de trabajo previas. Si la herramienta de test no permite ese aislamiento, no ejecutarla.
- Preservar todos los archivos modificados y no rastreados previamente; especialmente los cambios Swing.

## 4. Método de investigación requerido

### A. Reconstrucción de la falla histórica

- Recuperar reporte de Surefire, traza completa y comando de P09.
- Determinar secuencia exacta: preparación de fixture, creación/selección de raíz, conexión H2, creación de sesión, operación SQL y punto de bloqueo.
- Inspeccionar la ubicación real de la base y su estado conocido **sin alterarla**. Separar evidencia objetiva de explicaciones posibles.
- Examinar la aserción y establecer si el test comprueba una ruta admisible del producto o un fixture artificial con condiciones particulares.

### B. Comparación controlada, si es segura

- Identificar las diferencias relevantes entre caso fallido y caso portable exitoso: raíz, carpeta de datos, URL JDBC, flags de conexión, contenido previo, permisos, procesos/locks, orden de inicialización y contrato de ejecución.
- Para cualquier nueva ejecución, usar un directorio fresco exclusivo y verificar antes que **no** apunta, ni directa ni indirectamente, a las bases anteriores. Conservar toda la evidencia; no limpiar después.
- Capturar salida, error, código de salida y contexto suficiente para repetir. No confiar en `EXIT=0` de Codex como prueba de que el escenario pasó.
- Si la prueba focal no puede restringir con seguridad dónde escribe, reemplazar la ejecución por análisis estático y dejar pendiente la evidencia experimental.

### C. Límite de inferencias

- Que el ZIP haya funcionado una vez no demuestra fiabilidad universal.
- Que una prueba haya fallado con una clase obtenida de `HEAD` no demuestra fallo del `HEAD` completo.
- Que el problema sea reproducible en pruebas no implica automáticamente que afecte a una distribución portable; hay que documentar el nexo técnico.
- No declarar seguras las protecciones de enlaces cuyos tests quedaron omitidos.

## 5. Entrega y criterio de salida

Emitir un informe final en español, en la respuesta de Codex y en `target/p11-evidencia/DIAGNOSTICO-A3-H2.md`, con:

1. **Causa raíz:** `CONFIRMADA`, `HIPOTESIS_FUNDADA` o `NO_DETERMINADA`, junto con evidencia trazable.
2. **Reproducción:** qué se ejecutó y qué no, comandos, condiciones de directorios, URLs H2 relevantes (sin datos privados), códigos de salida y extractos precisos de la falla.
3. **Comparación con portable:** diferencias demostradas y su posible efecto. Distinguir la evidencia de P09, la declaración manual del usuario y cualquier prueba nueva de P11.
4. **Baseline `HEAD`:** `COMPARABLE`, `PARCIAL` o `NO_VERIFICADO`, especificando el alcance exacto.
5. **Impacto en A3:** una sola clasificación: `NO_AFECTA_A3_ACREDITADO`, `AFECTA_A3` o `SIGUE_INCIERTO`, con razones explícitas.
6. **Siguiente decisión recomendada:** `EVALUAR_ACEPTACION_A3_CON_RESERVAS`, `CORRECCION_FOCAL_NECESARIA` o `EVIDENCIA_ADICIONAL_IMPRESCINDIBLE`. No declarar A3 aprobado.
7. **Pruebas omitidas:** mantener las dos pruebas de enlaces simbólicos con su limitación residual, sin mezclarlas con el diagnóstico H2.
8. **Integridad:** resultados de `git status --short` antes y después, `git diff --check`, constancia de que no se editó código, configuración, ZIP, documentación ni Git.

**Condición de parada:** si falta información, si el diagnóstico requiere alterar el funcionamiento o si las comprobaciones amenazan archivos/sesiones anteriores, detenerse, no modificar código y señalar la **única evidencia mínima** que falta.

**Estados obligatorios al cierre:** H4 = **PARCIAL / NO APROBADO**; aceptación integral = **PENDIENTE**. Sin commits, pushes ni métricas finales.
