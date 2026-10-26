# P07 — A3: empaquetado portable Windows de ReparaBarrio

**Fecha:** 16/10/2026  
**Estado:** solicitud de implementación acotada, sujeta a inspección y validación  
**Objetivo:** crear una distribución ZIP funcional con Java 21 externo, sin IntelliJ, Maven ni acceso de red durante su ejecución.

## Contexto comprobado externamente

- Raíz del proyecto: `C:\Users\Alejandro\Documents\UNLaM\ProgramacionConcurrente-2026\APO1-ReparaBarrio`.
- Java 21 instalado: `%USERPROFILE%\.jdks\temurin-21.0.12.1`.
- H2 2.3.232 disponible: `%USERPROFILE%\.m2\repository\com\h2database\h2\2.3.232\h2-2.3.232.jar`.
- `scripts/iniciar-h5.ps1` compila con `javac --release 21`, copia recursos y ejecuta `ar.edu.unlam.reparabarrio.arranque.ReparaBarrioApp --raiz ... --academico-nuevo` con H2 en el classpath. Inspeccionar el script entero antes de reutilizarlo.
- No hay JAR propio generado en `target/`. Hay `maven-jar-plugin` 3.2.2/3.5.0 y un `maven-assembly-plugin` 2.5.5 local, pero no `shade` ni `dependency`; NO depender de descarga o instalación de plugins.
- Las últimas validaciones de Windows incluyen 67/67 casos focales H2/H3 y 9 casos gráficos no omitidos aprobados con P06; dos tests gráficos se omitieron en modo headless.
- H4 recuperación avanzada continúa **PARCIAL/NO APROBADO**. GUI académica abre **sesiones nuevas descartables** y no acredita reapertura normal de sesiones anteriores. `INC-SIM-01` fue corregida para el escenario manual ensayado; persiste reacomodamiento visual `INC-SIM-02`.
- `git status --short` presenta cambios y nuevos archivos anteriores sin commit. No los borrar ni sobrescribir por accidente. Los dos archivos Java ya modificados deben conservarse.

## Alcance autorizado

1. Inspeccionar `AGENTS.md`, `SPEC.md`, `PLAN.md`, `pom.xml`, el arranque (`ReparaBarrioApp`), `scripts/iniciar-h5.ps1` y el árbol relevante de recursos, para conocer las rutas y opciones efectivas.
2. Crear **un script reproducible de empaquetado** en `scripts/empaquetar-portable.ps1` que, usando únicamente herramientas ya disponibles de JDK 21 y PowerShell, genere bajo `target/distribucion/` una carpeta portable y un ZIP (nombre con versión o marca inequívoca). Evitar modificar el POM y evitar plugins Maven nuevos salvo que exista una razón técnica fuerte que primero deba informarse.
3. El script debe compilar **frescas** las fuentes y recursos actuales (no empaquetar ciegamente `target/classes` potencialmente obsoleto). Se puede reutilizar `scripts/iniciar-h5.ps1 -Compilar -SoloCompilar` si después de inspeccionarlo se confirma el comportamiento. Compilar a un directorio controlado y verificar el `.class` de entrada antes de empaquetar.
4. Empaquetar los `.class` y recursos como `app/ReparaBarrio.jar` usando `jar.exe` del JDK 21, y copiar exclusivamente la dependencia de ejecución `lib/h2-2.3.232.jar`. No incluir el JAR de fuentes H2 ni JUnit, ni bases de datos previas, ni `target` histórico, ni logs, JSONL, prompts, capturas o documentación académica sensible.
5. Incluir `Iniciar-ReparaBarrio.cmd` en la raíz de la carpeta distribuible. Debe identificar un `java.exe` de Java 21 accesible por `JAVA_HOME` o PATH y dar un error comprensible si no está. Lanzar por `-cp "app\ReparaBarrio.jar;lib\h2-2.3.232.jar" ar.edu.unlam.reparabarrio.arranque.ReparaBarrioApp ...`. Usar rutas relativas a `%~dp0` y comillas seguras para espacios; no usar rutas absolutas del equipo de desarrollo. Determinar, después de inspeccionar el código, el uso correcto de `--raiz` para una sesión académica nueva, preferiblemente dentro de la carpeta extraída o un directorio de datos escrito de manera explícita y documentada. No reclamar continuidad entre sesiones.
6. Incluir `LEEME-WINDOWS.txt` breve con requisitos, modo de extraer ZIP a una carpeta escribible, ejecución del `.cmd`, uso mínimo, cierre seguro y limitaciones H4 e `INC-SIM-02`. Dejar claro que **requiere Java 21 instalado** (no es `.exe`, no incluye JVM).
7. Agregar documento de evidencia `docs/A3-EMPAQUETADO-PORTABLE.md` con estructura final, comandos exactos, rutas relativas, controles efectuados, resultados y límites. Verificar que el ZIP incluya solo lo intencionado.
8. Realizar verificación automatizada **sin abrir una interfaz física**: compilar JDK 21, comprobar `jar tf`, existencia y contenido del ZIP, `java --version`, dependencia de H2 en la carpeta y ausencia de rutas absolutas del desarrollador. Si es razonablemente viable, ejecutar un smoke headless seguro que no requiera interacción; nunca presentar este smoke como validación humana de la interfaz. No iniciar nueva batería completa Maven ni tocar H4.
9. Resumir qué puede probar el usuario posteriormente **fuera de IntelliJ**, copiando el ZIP a una carpeta temporal diferente de la raíz del repositorio, extrayéndolo, ejecutando el `.cmd`, creando sesión académica, realizando una operación corta y cerrando con seguridad. Solicitar una sola comprobación humana final, no un recorrido de 100 órdenes.
10. Documentar el resultado en forma honesta. Si no es posible empaquetar sin cambios productivos, detenerse y explicar el bloqueo **sin introducir soluciones improvisadas**.

## Restricciones esenciales

- NO modificar código de negocio, persistencia, recuperación H4, concurrencia H2/H3, interfaz Swing, tests existentes ni `SPEC.md`/`PLAN.md` para facilitar artificialmente el empaquetado.
- NO ejecutar `mvn clean`, `git reset`, `git checkout` restaurador, borrados masivos de `target` ni alterar bases existentes. Limpiar solamente el subdirectorio de salida de distribución si se comprueba que pertenece al propio script y no contiene datos de usuario.
- NO acceder a internet, instalar JDK/plugins/librerías ni introducir dependencias externas.
- NO hacer commit, push, merge ni modificar el repositorio grupal.
- NO iniciar métricas académicas ni calcular totales de tokens. Conservar el log JSONL de esta ejecución, administrado externamente por el usuario.
- NO declarar H4, CA-16, CA-17 o CA-25 integrales aprobados por el éxito del paquete. Este trabajo comprueba **la distribución acotada**, no la especificación integral.
- Respetar `AGENTS.md` y cualquier instrucción del proyecto que no contradiga estas restricciones explícitas.

## Entrega final obligatoria

En la respuesta final, informar:
- archivos creados/modificados y archivos preexistentes respetados;
- carpeta y ZIP exactos generados, o la razón de no generarlos;
- compilación, verificación del JAR y verificación del ZIP, incluyendo códigos de salida;
- formato de lanzamiento y requerimiento externo de Java 21;
- límites conocidos, incluida la sesión académica descartable;
- un procedimiento **mínimo y exacto** de prueba manual para el usuario en Windows;
- resultado de `git status --short` sin modificar Git;
- resultado final: **A3 empaquetado preparado / A3 bloqueado**, sin autoproclamar aceptación humana.
