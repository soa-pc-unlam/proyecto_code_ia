# P01 - Prompt técnico de especificación | ReparaBarrio

| Control del documento | Valor |
|---|---|
| **Versión** | **0.2** |
| **Fecha** | **13/10/2026** |
| **Estado** | **PRELIMINAR, pendiente de revisión y aprobación** |
| **Metodología y fase** | Spec-Driven Development (SDD), fase 1: Especificación |
| **Herramienta destinataria** | Codex |
| **Resultado solicitado** | Generación inicial del archivo `SPEC.md` |

> **Nota de control:** Este documento es el **prompt de entrada** para la primera fase de SDD. No es el archivo `SPEC.md` ni una especificación ya aprobada. 

Vamos a desarrollar **ReparaBarrio**, una aplicación para organizar la asignación de técnicos, puestos y herramientas en pequeños talleres y cooperativas de reparación de electrodomésticos y equipos electrónicos, utilizando **Spec-Driven Development (SDD)**.

En esta **primera fase, ESPECIFICACIÓN**, se solicita generar una especificación verificable del sistema. **Todavía no se debe diseñar la arquitectura ni generar código.**

## PROBLEMA Y OBJETIVO

**Problema:** Un taller recibe varias órdenes de reparación que pueden requerir los mismos técnicos, puestos de trabajo y herramientas. Una asignación informal puede generar conflictos, recursos ociosos, esperas difíciles de explicar o el intento de utilizar simultáneamente una herramienta exclusiva.

**Objetivo:** Desarrollar un planificador que permita conocer las órdenes pendientes, proponer los trabajos que pueden comenzar y confirmar el inicio real de una reparación **sin asignaciones incompatibles ni reservas parciales**. Debe permitir seguir el estado de las reparaciones y la disponibilidad de recursos.

La aplicación **no efectúa reparaciones físicas**: coordina su planificación y registro. Debe tener un **modo operativo**, utilizable en un taller real, y un **modo de simulación** para generar cargas concurrentes y evaluar el funcionamiento del mismo núcleo de reglas.

## ACTORES Y RECURSOS

**Actores**

- **Encargado del taller:** registra órdenes, configura recursos, consulta propuestas, confirma comienzos y consulta el tablero.
- **Técnico reparador:** informa la finalización o el fallo de una reparación en curso.
- **Simulador automático:** genera órdenes ficticias y confirma y finaliza trabajos de manera automática en escenarios de prueba.

En esta versión, los roles identifican responsabilidades. No se requiere implementar cuentas de usuario ni autenticación.

**Recursos compartidos**

- **Técnicos identificables:** T01, T02, T03, etc.; cada uno puede atender como máximo una reparación en curso.
- **Puestos identificables:** P01, P02, etc.; cada puesto puede ser usado por una sola reparación a la vez.
- **Herramientas físicas identificables:** M01 (multímetro), S01 (soldador), etc.; cada unidad de herramienta es exclusiva mientras esté asignada.

Una orden debe requerir **un técnico, un puesto y las herramientas necesarias**, indicadas por tipo. Los recursos deben poder figurar como disponibles, ocupados o fuera de servicio.

## CARACTERÍSTICAS Y REGLAS DE NEGOCIO

- El planificador debe respetar la **primera orden factible por antigüedad**: examinar las órdenes pendientes en orden de llegada y proponer la primera cuyos recursos puedan satisfacerse. Si una orden antigua no es factible, se pueden examinar las siguientes.
- **Una propuesta no implica reserva:** consultar o mostrar una propuesta no debe ocupar recursos ni cambiar el estado de una orden.
- **La reserva se produce al confirmar el inicio real:** en modo operativo confirma una persona; en modo simulación, un proceso automático.
- En la confirmación debe verificarse nuevamente que la orden siga siendo la primera factible, que permanezca pendiente y que sus recursos continúen disponibles. Si alguna condición dejó de cumplirse, se debe rechazar sin realizar cambios parciales.
- Deben asignarse unidades físicas concretas mediante una selección determinista. Si existe más de un técnico o puesto disponible, la selección debe seguir un criterio estable y reproducible.
- La asignación completa de recursos y el cambio de estado de la orden deben efectuarse de manera **atómica: todos o ninguno**.
- Cuando una reparación finaliza o falla, deben liberarse sus recursos de forma consistente; no debe admitirse una doble finalización.
- Si una orden es la primera factible, pero el encargado decide no iniciarla, **el MVP no contempla un mecanismo de salteo o prioridad**. Esta limitación debe aparecer como cuestión explícita en `SPEC.md`, sin resolverla silenciosamente.
- El modo operativo debe conservar sus datos entre sesiones. El modo de simulación debe utilizar datos independientes y descartables, sin modificar las órdenes o los recursos operativos.

## PROMPT PATTERN (PROMPT TÉCNICO)

### LENGUAJE DE PROGRAMACIÓN:

- Utilizar **Java** como lenguaje para la futura aplicación.
- En esta fase, describir qué debe hacer el sistema y qué condiciones debe cumplir, **sin producir código fuente**.

### PLATAFORMA:

- Desarrollar una **aplicación local de escritorio para Windows**, con interfaz gráfica.
- Contemplar un **núcleo funcional reutilizable** por otros sistemas en el futuro, sin implementar ahora una integración externa.
- Contemplar dos modos de trabajo claramente diferenciados: **operativo** y **simulación**, con las mismas reglas de planificación y reserva.

### FUNCIONALIDADES A IMPLEMENTAR:

- Permitir **registrar órdenes de reparación**, indicando identificador, descripción y herramientas necesarias.
- Permitir **registrar y consultar técnicos, puestos y herramientas**, con su identificación y disponibilidad.
- Permitir **consultar las órdenes pendientes** y obtener una **propuesta de la primera orden factible**.
- Permitir **confirmar el comienzo real** de la orden propuesta, asignando un técnico, un puesto y las herramientas que requiere.
- Permitir **informar la finalización o el fallo** de una reparación en curso y liberar los recursos asignados.
- Permitir **cancelar órdenes que aún estén pendientes**.
- Permitir **consultar el estado de las órdenes**, los recursos ocupados y disponibles y las causas por las que una orden no puede comenzar.
- Permitir **consultar un historial de cambios relevantes** de cada orden, incluidas las asignaciones y liberaciones.
- Permitir **conservar y recuperar los datos del modo operativo** al cerrar y volver a abrir la aplicación.
- Permitir **configurar y ejecutar escenarios de simulación** con varias órdenes, distintas duraciones y recursos limitados.
- Permitir **visualizar estadísticas básicas** de órdenes procesadas, tiempos de espera y utilización de recursos durante una simulación.

### REQUISITOS ESPECÍFICOS:

**Órdenes y estados**

- Cada orden debe tener un **identificador único**.
- La máquina de estados de órdenes debe contemplar: `PENDIENTE`, `EN_CURSO`, `FINALIZADA`, `FALLIDA` y `CANCELADA`.
- Una orden `PENDIENTE` solamente debe pasar a `EN_CURSO` o `CANCELADA`.
- Una orden `EN_CURSO` solamente debe pasar a `FINALIZADA` o `FALLIDA`.
- Las órdenes en estados finales no deben aceptar nuevas transiciones en esta versión.
- La condición «esperando recursos» debe mostrarse como información de una orden pendiente, **no como un sexto estado persistente**.

**Asignación y concurrencia**

- El sistema debe poder recibir **confirmaciones de inicio simultáneas** de distintas órdenes.
- Si varias confirmaciones compiten por el mismo recurso exclusivo, **solamente deben prosperar las compatibles** con la disponibilidad real.
- Si dos solicitudes intentan iniciar la misma orden al mismo tiempo, **solamente una** debe efectuar el cambio de estado.
- No debe comenzar una reparación si no se pueden reservar **todos** los recursos que necesita.
- Una reserva fallida no debe dejar un técnico, puesto o herramienta parcialmente ocupado.
- El sistema debe permitir que **dos reparaciones con recursos independientes estén en curso simultáneamente**.
- Las tareas en espera no deben retener recursos parcialmente adquiridos, evitando esperas circulares por esa causa.
- Una confirmación basada en una propuesta que quedó desactualizada debe rechazarse de forma controlada y permitir obtener otra propuesta.
- Las operaciones simultáneas de inicio, cancelación, finalización o fallo no deben producir estados contradictorios.

**Recursos y recuperación**

- Un recurso que esté asignado a una orden en curso no debe poder quedar simultáneamente asignado a otra.
- No debe permitirse marcar una herramienta ocupada como fuera de servicio sin resolver antes la orden en curso.
- Si se cierra inesperadamente el modo operativo, las órdenes que estaban `EN_CURSO` deben conservar sus asignaciones y quedar identificadas para revisión del encargado al reiniciar. **No se deben liberar recursos automáticamente por suposición.**

**Interfaz gráfica y visualización**

- Presentar una sección de **Órdenes** para registrar, consultar, proponer, confirmar y finalizar trabajos.
- Presentar una sección de **Recursos** que permita ver técnicos, puestos y herramientas, distinguiendo claramente cuáles están disponibles, ocupados o fuera de servicio.
- Presentar una sección de **Simulación** para configurar escenarios y observar su evolución.
- Mostrar de manera visible la **orden propuesta**, el **técnico**, el **puesto** y las **herramientas** que utilizaría, aclarando que todavía no están reservados.
- Informar los **rechazos de confirmación** y sus motivos, sin dejar cambios parciales.
- Mostrar la cantidad de órdenes **pendientes, en curso y terminadas**, y actualizar la visualización mientras se desarrollan las simulaciones.
- La interfaz debe mantenerse utilizable durante el procesamiento concurrente de las simulaciones.

**Modo simulación**

- Permitir generar escenarios **repetibles**, con cantidad de órdenes, requisitos de recursos y duraciones de trabajo definidos.
- Ejecutar solicitudes y trabajos ficticios en forma **realmente concurrente**; no limitarse a una animación secuencial.
- Permitir observar escenarios con recursos suficientes y escenarios de alta contención.
- Asegurar que los resultados de la simulación no modifiquen los datos del modo operativo.

### RESTRICCIONES:

- Limitar el alcance inicial a **un taller local**. No desarrollar un sistema distribuido ni una aplicación multiusuario en red.
- No incorporar en esta versión facturación, cobros, clientes, repuestos consumibles, reservas de fechas futuras, especialidades de técnicos ni autenticación de usuarios.
- No incorporar políticas de urgencia, salteos manuales ni optimización avanzada del calendario; la regla aprobada es **primera orden factible por antigüedad**.
- No prometer que una secuencia de pruebas demuestra por sí sola ausencia universal de condiciones de carrera o deadlocks.
- No agregar recursos externos obligatorios ni depender de servicios remotos para que la aplicación básica funcione.
- No confundir **técnicos reales** con **hilos de ejecución**: en modo operativo, la reparación física avanza por acciones humanas; en simulación, la duración y finalización pueden ser automáticas.
- No definir todavía arquitectura, clases, tablas, módulos, algoritmos concretos de sincronización ni tecnología específica de almacenamiento.
- No implementar funcionalidades, generar código ni ejecutar pruebas durante esta fase.
- No conectarse, publicar ni modificar repositorios remotos. Trabajar únicamente en el proyecto local autorizado.

## RESULTADO SOLICITADO DE LA FASE 1: ESPECIFICACIÓN

**Genere y guarde en la raíz del proyecto local un archivo denominado `SPEC.md`.** La especificación debe estar redactada en español e incluir, como mínimo:

1. Problema, objetivo y alcance del sistema, con exclusiones explícitas.
2. Actores y recursos involucrados.
3. Casos de uso.
4. **Requisitos funcionales** numerados, claros y verificables.
5. **Requisitos no funcionales** numerados, claros y verificables.
6. Entradas y salidas esperadas.
7. Reglas de negocio y máquina de estados de las órdenes.
8. Restricciones y supuestos del sistema.
9. Reglas de concurrencia e invariantes que deban cumplirse.
10. **Criterios de aceptación** trazables a los requisitos, incluidos casos simultáneos y de simulación.
11. Riesgos, limitaciones y cuestiones que requieren validación humana, incluida la imposibilidad de saltear la primera orden factible.

**IMPORTANTE:** Esta primera interacción debe terminar con la **generación de `SPEC.md`**, no con la planificación ni con la implementación. No defina todavía la arquitectura ni genere código. **No modifique silenciosamente las reglas indicadas:** si identifica inconsistencias o ambigüedades, documéntelas como preguntas abiertas para revisión.

Al finalizar, informe únicamente que se generó `SPEC.md`, qué secciones contiene y qué cuestiones quedaron pendientes. No modifique archivos ajenos a ese entregable.
