# P03-H4 — Corrección dirigida de recuperación entre JVM (v0.1)

**Estado:** instrucción de continuación de la implementación H4 ya autorizada; no reabrir diseño, no iniciar H5. **Objetivo:** resolver H4-T02, H4-T03 y H4-T09 con una causa verificable y el mínimo cambio seguro; no cambiar oráculos válidos para maquillar resultados.

Leer íntegramente `IMPLEM-H4.md` v0.2, `prompts/anexos/P03-H4-implementacion.md`, `H4-REPORTE-CORRECCION.md`, `H4-REPORTE-IMPLEMENTACION.md`, `metricas/logs/P03-H4-validacion-Windows-corregida.txt`, fuentes Java/SQL y tests actuales. La regresión real de Windows terminó con **217 pruebas, 3 failures, 0 errors, 0 skipped**. H3 T05 pasó en esta corrida aunque existe antecedente de intermitencia. En MXC reaparece `90097-232`, ya ausente de la ejecución externa: no cambiar persistencia, permisos o aserciones para esquivar exclusivamente MXC.

## INVESTIGACIÓN PRIORITARIA

1. **H4-T02** `RecuperacionH4Test:31-50`: A cae con dos EN_CURSO; B y C confirman conservación; tercera apertura consigue leer contexto y asignaciones, pero alta de NUEVA_SIN_HERRAMIENTAS devuelve `ERROR_TECNICO` por `Metadatos: cantidad de filas inesperada`. Identificar exactamente qué DML de `RepositorioRecuperacionJdbc.escribir` actualiza cero o múltiples filas, con SQL y estado de intención y sesión. No inferir que el problema es de prioridad T2/T3.
2. **H4-T03** `RecuperacionH4Test:57-85`: `rb_intenciones.sesion_id` referencia un UUID ausente de `rb_sesiones` (SQLState 23506). El control `RR_RESOLVERREVISION` nunca se alcanza. Rastrear el ID desde `ContextoTaller.sesion`, el INSERT de sesión, su commit, la reapertura y el INSERT de intención. Mantener FK y distinguir negocio no iniciado de incertidumbre real; cuando se alcance la fase correcta, preservar PT-06 y aserciones de rollback conocido.
3. **H4-T09** `RecuperacionH4Test:245-260`: tras rollback inyectado en `RR_REVISAR`, SQL comprueba 2 asignaciones, 0 revisiones, 1 sesión abierta. Al quitar el fallo, `recuperar()` publica HABILITADO pero se observan 0 asignaciones, 0 revisiones. Determinar mediante consultas antes/después y dentro/fuera de la misma transacción dónde desaparecen las filas; ninguna operación de recuperación puede liberar recursos físicos automáticamente. Comprobar repositorio, auditoría, resultado de infraestructura, commits/rollbacks y comportamiento real H2 en archivos descartables.

Las tres incidencias afectan a la relación **contexto/sesión/intenciones/transacciones/recuperación**. Investigar posible causa común, pero no asumirla. La lectura de sólo datos de pruebas en memoria NO acredita durabilidad de archivo. `WRITE_DELAY=0` NO reemplaza la prueba efectiva de caída entre JVM.

## CONDICIONES DE TRABAJO

- Instrumentar los tests/fixtures con evidencias independientes y **mínimas**: UUID base y sesión, estado en `rb_sesiones`, conteos y claves de `rb_intenciones`, `rb_revisiones`, `asignaciones`, `rb_contexto`, fase, SQL y cantidad afectada; antes/después de commit/rollback, caída, reapertura y recuperación. Evitar logs de datos sensibles ajenos a fixtures. Cuando sea viable, añadir asserts invariantes que detecten primera frontera incorrecta.
- Evaluar expresamente `ServicioRecuperacion.recuperar`, `ContextoTaller.infraestructura`, `ServicioTaller.ejecutar`, `RepositorioRecuperacionJdbc`, `InicializadorBase`, `UnidadTrabajoJdbc`, `H4Fixture`, `ProcesoH4` y `ProcesosH4`.
- Distinguir error del test, comportamiento del motor H2 y defecto productivo con evidencia reproducible. Si es un fixture/oráculo incorrecto, justificar con reglas de SPEC/PLAN/IMPLEM y conservar igual o mayor cobertura. No sustituir escenarios reales entre JVM por memoria.
- Implementar el arreglo **más pequeño que mantenga** guardia FileChannel, única autoridad, integridad referencial, atomicidad, identidad, estrategia B, no liberación de recursos EN_CURSO, no duplicación de efectos e invariantes H1/H2/H3.
- Preservar las aserciones y el diagnóstico del fallo intermitente H3-T05; evitar degradarlo para mejorar resultados.
- No tocar datos operativos, no borrar código ni evidencia previa, no cambiar SPEC/PLAN/IMPLEM/prompt aprobado/POM, no ejecutar operaciones remotas ni commits, no avanzar a H5.
- Compilar y usar focales que permita MXC, sin ciclos interminables de `90097-232`. No declarar aprobadas pruebas de archivo no validadas. Mantener registro en `metricas/logs` con nombres nuevos y no sobrescribir intento1/2/3.

## ENTREGA

Informar causa raíz demostrada por fallo, antes/después (datos persistidos y flujo), archivos cambiados, tests realmente ejecutados, pendientes y **comandos de PowerShell externo** para las tres focales de archivo y la suite integral. Si falta evidencia para una corrección segura, detener sólo esa modificación y especificar qué prueba dirigida faltaría, sin inventar éxito.
