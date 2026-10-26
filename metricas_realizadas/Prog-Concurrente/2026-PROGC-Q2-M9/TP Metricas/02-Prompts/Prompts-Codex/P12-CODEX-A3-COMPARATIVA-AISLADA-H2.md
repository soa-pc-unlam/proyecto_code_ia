# P12 | ReparaBarrio APO1 | Preparación de comparativa aislada de H2 para A3

**Fecha:** 2026-10-16  
**Estado:** autorizado exclusivamente para preparar un experimento local; no autoriza modificar código ni realizar la ejecución comparativa dentro de Codex.  
**Antecedentes:** P09 produjo el paquete portable y el usuario completó cinco verificaciones manuales satisfactoriamente. P10 detectó 29 pruebas, una falla y dos omisiones. P11 diagnosticó un `AssertionError` de H2 2.3.232 en `RandomAccessStore.moveChunkInside` durante el cierre inicial, anterior a `90097-232` (base de solo lectura), sin acreditar causalidad ni comparación completa. La prueba falló antes de crear la simulación. H4 continúa PARCIAL / NO APROBADO.

## 1. Objetivo

Preparar **un solo ensayo comparativo, reproducible y no destructivo**, para que el usuario lo ejecute desde su PowerShell normal de Windows, fuera del sandbox de Codex. Debe comparar la apertura de **dos sesiones académicas nuevas** con la misma versión de aplicación y de H2, usando raíces y bases independientes:

1. **DESARROLLO:** raíz descartable que reproduzca válidamente los requisitos de desarrollo sin tocar la raíz del proyecto.
2. **PORTABLE:** raíz descartable obtenida del ZIP existente, con modo portable explícito.

El propósito es examinar la secuencia de inicialización, cierre y apertura posterior, especialmente si hay un `AssertionError` de compactación y/o un `90097`, para delimitar la fiabilidad de A3. No es una auditoría integral de H4 ni una suite general.

## 2. Inspección previa obligatoria

Antes de generar cualquier script, leer `AGENTS.md`, `docs/A3-EMPAQUETADO-PORTABLE.md`, `target/p11-evidencia/DIAGNOSTICO-A3-H2.md`, `pom.xml` y las implementaciones reales de `FabricaContextos`, `ArranqueAcademico`, `ContextoEjecucion`, `InicializadorBase`, composición de contexto, conexiones H2, y los probes históricos de P09 si existen. Verificar contrato exacto, dependencias y cómo observar `HABILITADO`/`BLOQUEADO`. **No inventar firmas ni comandos de la aplicación.** Documentar cualquier limitación detectada.

## 3. Aislamiento y límites de escritura

- **Prohibido** editar fuentes Java del proyecto, tests, `pom.xml`, `AGENTS.md`, `SPEC.md`, `PLAN.md`, scripts existentes, `docs/`, JAR/ZIP de P09, index/historial/configuración Git y bases de datos anteriores.
- **Prohibido** `mvn clean`, `mvn test` en el proyecto original, eliminar sesiones, abrir JDBC sobre bases preexistentes, actualizar H2 o instalar dependencias, acceder a Internet, calcular métricas finales, hacer commits/pushes o modificar permisos/propietarios del sistema.
- Autorizar escritura preparatoria **exclusivamente** bajo `target/p12-evidencia/` para el script, un eventual probe Java auxiliar y las instrucciones. No generar ni abrir bases durante la preparación.
- El script que ejecutará el usuario debe crear **una carpeta completamente nueva, con un identificador único**, bajo `$env:TEMP\ReparaBarrio-P12\`, verificar que no exista, usar solamente esa carpeta para copias de distribución y raíces de las dos sesiones, y no tocar datos históricos. Nunca borrar automáticamente esa carpeta: conservarla para examen posterior.
- Para el escenario de desarrollo, copiar los marcadores reales del repositorio a la raíz descartable **solo como fixture de prueba**; no incluirlos en el ZIP portable ni saltarse su validación.
- Para el escenario portable, extraer una **copia** del ZIP existente en su propia raíz descartable, sin reutilizar sesiones manuales. Comprobar que los JAR son los esperados.
- En ambos casos, usar preferentemente las **mismas clases/JAR, el mismo H2 2.3.232 y Java 21**, variando solo el contexto de raíz/modo. Si eso no puede hacerse con seguridad, documentar la imposibilidad y detenerse, sin falsear la comparabilidad.
- El script debe impedir que se abran sesiones en una ruta existente y debe fallar explícitamente si no puede garantizar el aislamiento. No reintentar automáticamente tras fallas. Ejecutar una apertura por escenario.

## 4. Preparación del experimento y capturas

Crear `target/p12-evidencia/EJECUTAR-COMPARATIVA.ps1` y los auxiliares estrictamente necesarios. **Codex no debe ejecutar el script preparado**: la ejecución debe hacerla el usuario desde PowerShell con sus permisos normales, después de revisar este resultado.

Los dos casos deben recorrer, en la medida en que las APIs existentes lo permitan, la creación de sesión académica nueva, el cierre de la conexión inicial y la primera operación que hace pasar la sesión a HABILITADO o BLOQUEADO, registrando el resultado sin iniciar la interfaz gráfica. El probe auxiliar deberá ser aislado y compilado fuera de los árboles `src/` y `target/classes/`, sin alterar ningún artefacto de distribución.

Registrar por cada caso, con salida en `target/p12-evidencia/resultados-<fecha-hora>/`:

- escenario, raíz y carpeta de sesión nueva, Java ejecutado, versión Java y H2, `CodeSource` de clases y driver;
- línea de comando/flags JVM efectivos, incluyendo variables `JAVA_TOOL_OPTIONS`, `JDK_JAVA_OPTIONS`, `_JAVA_OPTIONS` si están presentes y estado de aserciones;
- comprobación de permisos de escritura y usuario efectivo sobre **las carpetas temporales creadas**, evitando volcar información personal innecesaria;
- URL JDBC realmente utilizada o reconstruida claramente como tal, ocultando cualquier credencial (no registrar contraseñas);
- eventos de inicialización y cierre, error de compactación si aparece, estado de apertura de sesión, stderr, código de salida y trazas H2 de las bases recién creadas;
- evaluación de comparabilidad, distinguiendo lo observado de lo inferido.

**No modificar parámetros H2, opciones de compactación, versión de librería ni el ciclo de conexión**, porque ello alteraría el fenómeno a investigar. No simular artificialmente un resultado HABILITADO. Si el API impide medir el cierre/interfaz de la conexión, registrar la limitación.

## 5. Criterios de interpretación

- Si los dos casos funcionan, acreditar **dos éxitos concretos**, no la ausencia universal de un fallo intermitente.
- Si uno o ambos fallan, correlacionar el primer error documentado con el `90097` posterior; evitar atribución causal sin evidencia.
- Diferenciar fallas del mecanismo de H2, diferencias de permisos/usuario y problemas del modo portable sin presumir de antemano la causa.
- Si se necesita una nueva herramienta, bajar artefactos, refactorizar código o ejecutar una suite extensa, **detenerse** y pedir decisión humana.
- Conservar separadas las dos pruebas de enlaces simbólicos omitidas en P10 y la aceptación de H4.

## 6. Entregables y condición de parada

1. Script PowerShell preparado y verificable en `target/p12-evidencia/EJECUTAR-COMPARATIVA.ps1`.
2. Código del probe aislado, si es imprescindible, ubicado únicamente bajo `target/p12-evidencia/`.
3. Instrucciones simples para lanzar el script desde la raíz del proyecto, sin ejecutar aún las pruebas, y para compartir su salida.
4. Una explicación de seguridad/aislamiento, fuentes reales y limitaciones.
5. `git status --short` y `git diff --check` sin modificaciones del proyecto.

Si no es posible diseñar y validar estáticamente un ensayo aislado sin arriesgar datos o cambiar código del proyecto, **detenerse con informe y sin ejecutar nada**. No crear otro ciclo de indagaciones sin una propuesta concreta.

**Sin commits, pushes ni métricas finales. A3 pendiente. H4 PARCIAL / NO APROBADO. Aceptación integral PENDIENTE.**
