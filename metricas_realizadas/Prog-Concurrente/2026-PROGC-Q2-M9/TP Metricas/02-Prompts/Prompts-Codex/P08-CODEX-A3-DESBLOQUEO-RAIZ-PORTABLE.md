# P08 — A3 ReparaBarrio: raíz portable explícita y empaquetado Windows

**Estado:** PROPUESTA PARA APROBACIÓN HUMANA. No ejecutar sin autorización expresa.
**Fase:** implementación SDD, extensión estrictamente acotada del empaquetado A3.
**Antecedentes:** P07 terminó EXIT=0, pero no construyó distribución. Diagnóstico documentado en `docs/A3-EMPAQUETADO-PORTABLE.md`: el arranque exige que `--raiz` contenga `pom.xml` y `AGENTS.md`; eso impide usar una carpeta portable real. H4 permanece PARCIAL/NO APROBADO.

## 1. Objetivo y límites

Generar, en Windows, un ZIP de ReparaBarrio capaz de abrir el modo académico en Java 21 **sin IntelliJ, Maven, fuentes de código ni estructura de proyecto**. Mantener intacta la validación actual de `--raiz` cuando no se solicite expresamente modo portable.

Autorizar **únicamente** los ajustes mínimos en la composición/validación del arranque (paquete `arranque`) y archivos nuevos de empaquetado, lanzador, tests focales y documentación. NO modificar lógica de dominio, planificación, concurrencia, simulación, operaciones H2/H3/H4 ni interfaz Swing. No reabrir CA-16/CA-17, no modificar SPEC.md/PLAN.md, no relajar las invariantes, no crear `pom.xml`/`AGENTS.md` falsos en el paquete. Conservar los dos archivos Swing previamente modificados y demás trabajo local. Sin `mvn clean`, sin borrado de datos previos, sin commit/push, sin red, sin instalación de plugins, sin cálculo de métricas.

## 2. Investigación obligatoria antes de editar

Leer `AGENTS.md`, `SPEC.md`, `PLAN.md`, `docs/A3-EMPAQUETADO-PORTABLE.md`, `pom.xml`, `scripts/iniciar-h5.ps1` y **todo el camino de arranque** (`ReparaBarrioApp`, `ArranqueAcademico`, `FabricaContextos`, guardia de instancia, persistencia y rutas involucradas). Determinar dónde y por qué se exige `pom.xml` + `AGENTS.md`, qué otros directorios crea/lee `--raiz`, qué datos se conservan y qué restricciones de seguridad hacen falta. Identificar la ruta de compilación real y los recursos que deben ingresar al JAR. No asumir que basta con omitir un `if`.

## 3. Adaptación mínima de contrato (sujeta a viabilidad segura)

Proponer e implementar un **modo explícito** de ejecución portable, por ejemplo `--portable`, permitido sólo junto con `--academico-nuevo`; el nombre concreto se decide según el parser existente. Sin el indicador, conservar la validación existente de raíz de proyecto (`pom.xml`, `AGENTS.md`) sin cambios semánticos. Con indicador, validar la estructura real de la distribución, los archivos indispensables y una ubicación de datos escribible, impidiendo que una ruta arbitraria, enlace no confiable o una instalación incompleta se trate como válida. Mantener las protecciones pertinentes sobre rutas, guardia de instancia, errores seguros y creación de una nueva sesión académica; no recuperar automáticamente sesiones antiguas ni simular aprobación H4. Evitar la necesidad de fuentes, POM, `AGENTS.md` o IntelliJ en tiempo de ejecución del paquete. No añadir otra forma implícita de omitir las restricciones.

Si los contratos actuales obligaran a una reforma significativa del arranque, la persistencia o H4, **DETENERSE**: producir análisis de impacto y no implementar. Es preferible un bloqueo documentado a debilitar las restricciones.

## 4. Distribución esperada

Crear, usando herramientas del JDK 21 y PowerShell disponibles, sin plugins nuevos:

```
target/distribucion/ReparaBarrio-portable/
  Iniciar-ReparaBarrio.cmd
  LEEME.txt
  app/ReparaBarrio.jar
  lib/h2-2.3.232.jar
```

La ubicación de datos de ejecución deberá ser definida y explicada explícitamente; evitar escribir dentro de los JAR y de las carpetas `app`/`lib`. Construir el JAR a partir de las clases compiladas actuales (incluidos los recursos necesarios), con `jar` del JDK si hace falta, y distribuir H2 como dependencia separada. El lanzador debe resolver su propia carpeta con `%~dp0`, formar el classpath correctamente con rutas que contengan espacios, usar Java 21 instalado (JAVA_HOME o PATH), mostrar errores de Java ausente/version incorrecta, y ejecutar el modo portable académico. Debe funcionar tras descomprimir en una carpeta fuera del repositorio, escribible y con espacios en su ruta. Generar `target/distribucion/ReparaBarrio-portable.zip` mediante PowerShell. No incluir datos locales de sesiones, logs privados, directorios `.git`, `target` ajeno al paquete, archivos académicos internos ni repositorio Maven.

El LEEME debe explicar requisitos, instalación/uso, ruta de datos, carácter descartable de las sesiones, H4 parcial/no aprobado y limitaciones vigentes de recuperación y layout.

## 5. Verificación y evidencia

Efectuar **una sola ronda focal automatizada**, no la batería completa. Registrar comando, JDK, código de salida y pruebas aprobadas/fallidas/omitidas. Comprobar:

1. Arranque de desarrollo existente: se siguen exigiendo archivos del proyecto y funciona el launcher original.
2. Modo portable: reconoce exclusivamente la opción explícita y valida estructura/seguridad; no permite usar la estructura incompleta ni aceptar archivos señuelo.
3. Tests del arranque, guardia e integridad relevantes (selección mínima, sin H4 avanzado).
4. Existencia y contenido del JAR (`jar tf`), existencia/tamaño de H2 y estructura del ZIP tras extracción.
5. Comando de arranque real del ZIP descomprimido **fuera del repo**; si una GUI automática no puede verificarse con fiabilidad, declararlo pendiente para una sola verificación humana. No atribuir a `headless` un éxito gráfico.
6. `git diff` para demostrar el alcance de cambios y no pérdida de modificaciones preexistentes.

Si falla por entorno restringido Codex pero puede probarse fuera, clasificar honestamente y documentar comando de repetición Windows. Registrar todas las salidas y ubicaciones en `docs/A3-EMPAQUETADO-PORTABLE.md` o informe de cierre A3, sin alterar evidencia histórica. Conservar el JSONL externo que captura esta ejecución. No afirmar que `EXIT=0` de Codex significa que el ZIP está validado.

## 6. Condición de parada y entrega

Máximo **una propuesta de cambio focal y un ciclo de corrección** si la prueba falla. No iniciar refactorizaciones. Si no se puede resolver dentro de esos límites, detenerse y dejar un informe claro de bloqueo/impacto.

Respuesta final de Codex en español, con: (a) causa precisa del bloqueo; (b) archivos efectivamente modificados y preservados; (c) contrato de lanzamiento y controles de seguridad; (d) ruta exacta del ZIP/JAR y su verificación, o explicación de por qué no existen; (e) resultados de pruebas/códigos; (f) comando exacto de lanzamiento para la validación humana; (g) `git status --short`; (h) pendientes separados de H4 y aceptación integral. **No hacer commit ni push.**
