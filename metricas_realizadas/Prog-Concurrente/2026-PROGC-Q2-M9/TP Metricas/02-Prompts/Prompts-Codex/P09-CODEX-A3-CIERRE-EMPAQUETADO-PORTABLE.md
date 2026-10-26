# P09 | A3: desbloqueo controlado y cierre del empaquetado portable

**Proyecto:** APO1 ReparaBarrio (UNLaM, Programación Concurrente, 2026)  
**Línea de trabajo:** MVP, alternativa A  
**Estado del prompt:** Aprobado para ejecución acotada  
**Antecedentes:** P07 y P08  
**Objetivo:** Completar A3 sin modificar la lógica funcional de ReparaBarrio.

## 1. Situación de partida

P07 detectó un conflicto entre la validación de la raíz de desarrollo (que incluye `pom.xml` y `AGENTS.md`) y una distribución portable fuera del repositorio.

P08 concluyó **bloqueado por alcance** y se detuvo correctamente, antes de implementar. Identificó que también se valida la raíz al iniciar la simulación mediante:

`VentanaPrincipal -> PresentadorSimulacion -> MotorSimulacion -> FabricaContextos`

En ese recorrido se transmite un `Path`, pero no se propaga un contexto explícito de ejecución portable. P08 no implementó `--portable`, no creó el JAR ni el ZIP, y solo actualizó `docs/A3-EMPAQUETADO-PORTABLE.md`. P08 indicó que hay **dos archivos Swing modificados previamente** que deben conservarse, así como otros archivos locales no rastreados.

El contrato existente, antes de P09, incluye `--raiz <absoluta> --academico-nuevo`, junto con validación de proyecto, seguridad de rutas, consentimiento, nueva sesión y guardias. Mantener ese comportamiento salvo la ampliación explícitamente autorizada aquí.

Leer antes de modificar:

- `docs/A3-EMPAQUETADO-PORTABLE.md`.
- El código real de entrada, el procesamiento de argumentos, las validaciones de raíz y el recorrido completo hasta `FabricaContextos`.
- Los scripts y la configuración Maven existentes para construir y ejecutar el proyecto.
- Las instrucciones de repositorio aplicables, incluido `AGENTS.md`, si existe.

No asumir que los nombres de clases, firmas o estructura del paquete son suficientes para definir una solución sin inspeccionar el código.

## 2. Autorización y alcance preciso

El usuario **autoriza** completar A3 y, únicamente para ello:

1. Introducir un **modo portable explícito**, activado por `--portable` y sujeto al contrato de autorización/consentimiento académico vigente (`--academico-nuevo`). No habilitar el modo portable por detección implícita de directorios ni por un estado global.
2. Propagar el contexto explícito necesario desde la entrada de la aplicación hasta los puntos que validan la raíz al abrir o iniciar una simulación.
3. Modificar, si es imprescindible, **firmas, constructores, parámetros o llamadas de Swing y simulación** solo para transportar ese contexto. Priorizar un mecanismo mínimo, comprensible e inmutable, o una sobrecarga compatible, según lo permita el código existente.
4. Diferenciar la validación de raíz del **modo de desarrollo** (que conserva las exigencias de estructura de proyecto) de la del **modo portable** (que no exige `pom.xml` ni `AGENTS.md`). En ambos modos, mantener controles de seguridad, rutas y sesiones que correspondan.
5. Crear o ajustar exclusivamente lo necesario para empaquetar una distribución ejecutable en Windows con **Java 21**, fuera del repositorio, sin requerir IntelliJ, Maven o código fuente en la máquina donde se ejecuta.
6. Incorporar pruebas focales, verificar regresiones y actualizar la documentación A3 con instrucciones reproducibles y limitaciones conocidas.

Antes de modificar, determinar y dejar asentada la **causa raíz confirmada**, los puntos exactos afectados y el diseño mínimo elegido. La autorización para cambiar contratos de transporte de contexto **no** autoriza a modificar el funcionamiento del motor.

## 3. Restricciones obligatorias

- No cambiar algoritmos de simulación, planificación, reservas, asignación de recursos ni concurrencia.
- No cambiar reglas de negocio, contratos funcionales ni comportamiento de casos de uso.
- No realizar refactorizaciones generales ni limpieza oportunista fuera de A3.
- No introducir autorización global implícita, propiedades de sistema usadas como bypass, estados estáticos globales ni detección automática insegura del modo portable.
- No debilitar validaciones de rutas, protección contra traversal, guardias, consentimiento o aislamiento entre sesiones. El modo portable solo exime las comprobaciones específicas de estructura de **proyecto de desarrollo**.
- No crear archivos ficticios `pom.xml` o `AGENTS.md` en la distribución para eludir las validaciones.
- No modificar, revertir, sobrescribir, limpiar, restaurar ni perder trabajo local preexistente. Inspeccionar primero `git status --short` y revisar diferencias antes de tocar archivos ya modificados. **Conservar los dos archivos Swing previamente modificados** y todos los archivos no rastreados; integrar cambios mínimos si hacen falta.
- No reabrir CA-16 ni CA-17, ni mezclar esta ejecución con el cierre de H4.
- No hacer commits, pushes, rebases, resets, stashes ni operaciones de Git que alteren el estado existente.
- No calcular métricas finales. El registro de tokens y eventos de Codex se realiza externamente mediante JSONL; no modificarlo ni sustituirlo.
- No declarar A3 aprobado por cuenta propia. Informar evidencia y dejar la decisión de aceptación al usuario.

## 4. Contratos esperados

### 4.1 Modo normal y modo académico existente

La ejecución sin `--portable` debe conservar íntegramente el contrato vigente, incluidas las validaciones de raíz del proyecto y las reglas asociadas a `--academico-nuevo`.

### 4.2 Modo portable explícito

- `--portable` debe ser reconocido expresamente y no aceptarse como autorización suficiente por sí solo.
- Mantener el requisito académico de consentimiento/autorización aplicable; no inventar ni relajar reglas.
- Admitir una raíz absoluta portable válida **sin** los archivos propios del repositorio de desarrollo.
- Evitar que las posteriores operaciones de simulación vuelvan a aplicar, por accidente, la validación exclusiva de raíz de desarrollo.
- Preservar toda validación que **sí** sea relevante para seguridad y consistencia de datos, rutas y sesiones.
- Ante argumentos inválidos o combinaciones no autorizadas, informar un error claro y no pasar silenciosamente a otro modo.

### 4.3 Distribución portable

Producir, si el diseño y las pruebas lo permiten:

- `target/distribucion/ReparaBarrio-portable/app/ReparaBarrio.jar`.
- `target/distribucion/ReparaBarrio-portable.zip`.
- Un lanzador Windows y los archivos de apoyo realmente necesarios.

El lanzador debe localizar sus archivos de forma relativa a su propia ubicación, funcionar con rutas que incluyan espacios y verificar/informar claramente si falta Java 21 compatible. No asumir la existencia de Maven o del repositorio original. Definir la raíz portable y el modo de inicio sobre la base del contrato efectivo de la aplicación; documentar el comando exacto y cualquier intervención requerida del usuario. No incluir credenciales, logs de desarrollo ni datos locales privados en el ZIP.

Si no es posible crear un paquete autosuficiente respecto de las dependencias Java con las herramientas actuales, documentar el bloqueo concreto en vez de etiquetar como portable un JAR que no arranca.

## 5. Secuencia de trabajo

### Paso A. Preflight y análisis de impacto

1. Confirmar directorio de trabajo y versión de Java utilizada.
2. Registrar `git status --short` completo, sin alterar archivos.
3. Leer A3 y localizar todas las validaciones y usos de la raíz en arranque y simulación.
4. Revisar scripts Maven y lanzadores disponibles.
5. Determinar el cambio mínimo y sus riesgos, sin modificar el sistema todavía.

### Paso B. Implementación acotada

1. Implementar el contrato explícito `--portable`.
2. Transportar el contexto necesario hasta las validaciones de raíz del recorrido de simulación.
3. Mantener compatibilidad y comportamiento del modo anterior.
4. Ajustar empaquetado y lanzador sin agregar dependencias innecesarias.
5. No modificar nada ajeno al bloqueo de A3.

### Paso C. Pruebas y verificación

Realizar las verificaciones ejecutables en el entorno y registrar resultados y comandos:

1. **Regresión:** modo normal rechaza una raíz inválida como antes.
2. **Contrato:** `--portable` sin condiciones de autorización requeridas se rechaza.
3. **Portabilidad:** modo portable acepta raíz adecuada sin `pom.xml` ni `AGENTS.md`.
4. **Simulación:** el contexto portable llega hasta `FabricaContextos` y permite iniciar el flujo correspondiente, sin volver a requerir una raíz de desarrollo.
5. **Seguridad:** traversal, rutas indebidas, guardias y sesiones mantienen las restricciones previas.
6. **Pruebas automáticas:** ejecutar las pertinentes con Java 21 y reportar fallas preexistentes o nuevas sin maquillarlas.
7. **Artefactos:** construir y comprobar existencia, estructura, contenido y dependencias del JAR y ZIP esperados.
8. **Fuera del repositorio:** descomprimir el ZIP en una carpeta temporal externa y con espacios en la ruta, e intentar iniciar el programa. No utilizar directamente archivos del repositorio en esa ejecución.
9. **GUI:** si no es posible verificar automáticamente una ventana real o el flujo visual, reportar con precisión qué sí se probó y proporcionar pasos manuales concretos; **no declarar verificada una GUI no ejecutada**.
10. **Integridad:** ejecutar `git diff --check`, revisar `git status --short` y corroborar que se preservó el trabajo local.

Las pruebas deben evitar cambios destructivos en los datos preexistentes; usar escenarios/ubicaciones temporales o de prueba cuando corresponda.

### Paso D. Documentación

Actualizar `docs/A3-EMPAQUETADO-PORTABLE.md` con:

- Causa del bloqueo de P08.
- Cambio mínimo seleccionado y archivos tocados.
- Contratos del modo normal y portable.
- Construcción y ejecución paso por paso.
- Pruebas realizadas, resultados y verificaciones manuales pendientes.
- Estado de A3 y limitaciones, sin dar por aprobados otros hitos.

## 6. Condición de parada

**Detener la implementación y explicar el bloqueo** si la solución requiere tocar algoritmos, reglas de negocio, invariantes de concurrencia o contratos ajenos al transporte mínimo del contexto portable; si exige eliminar protecciones de seguridad; o si no se puede preservar trabajo local.

Ante la condición de parada, no crear artefactos ficticios ni informar éxito. Conservar el trabajo existente, documentar el análisis y proponer la ampliación precisa que sería necesaria para una autorización futura.

## 7. Informe final solicitado

Devolver un informe estructurado en español con:

1. **Resultado:** `COMPLETADO`, `PARCIAL` o `BLOQUEADO`, distinguiendo ejecución del agente de éxito de A3.
2. **Causa raíz:** puntos concretos de validación y por qué P08 quedó bloqueado.
3. **Solución:** mecanismo elegido, justificación y alcance real.
4. **Cambios:** archivos creados/modificados, con observaciones sobre los Swing preexistentes.
5. **Pruebas:** comandos, resultados, fallas y verificaciones no realizadas.
6. **Artefactos:** rutas y existencia real del ZIP, JAR y lanzador.
7. **Integridad:** resultado de `git diff --check`, `git status --short` y preservación de trabajo local.
8. **Instrucciones de prueba manual** del modo portable, si corresponden.
9. **Pendientes:** riesgos o decisiones que deberá evaluar el usuario antes de aceptar A3.

Mantener expresamente **H4 = PARCIAL / NO APROBADO** y **aceptación integral = PENDIENTE**. No crear commits o pushes ni publicar cálculos de métricas finales.
