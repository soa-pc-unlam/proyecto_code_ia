# Instrucciones para Codex

## 1. Principios generales

- Priorizar código sencillo, legible, modular y fácil de mantener (KISS y DRY).
- No usar expresiones complejas.
- Usar expresiones simples, secillas de interpretar por un programador
- Reutilizar funciones, clases y módulos existentes antes de crear otros.
- Aplicar SOLID y patrones de diseño solo cuando aporten una mejora concreta.
- Evitar abstracciones artificiales, duplicaciones y dependencias innecesarias.
- Respetar la arquitectura, los nombres y las convenciones del proyecto.
- No introducir funcionalidades ni refactorizaciones no solicitadas.

## 2. Análisis previo y uso eficiente del contexto

- Identificar primero los archivos directamente relacionados con la solicitud.
- Buscar implementaciones reutilizables y revisar sus contratos de entrada y salida.
- Consultar otros archivos solo si una dependencia o un problema lo requiere.
- Evitar búsquedas globales, lecturas repetidas y análisis exhaustivos innecesarios.
- Aprovechar lo ya investigado en la conversación, verificando solo lo que haya cambiado.
- No volver a recorrer el proyecto completo en cada solicitud.
- En tareas de solo análisis, no modificar archivos ni ejecutar pruebas.
- En tareas complejas, presentar un plan breve antes de implementar y esperar aprobación.
- En tareas simples y claras, proceder directamente sin pedir confirmaciones innecesarias.

## 3. Reutilización y arquitectura

- Reutilizar directamente las operaciones existentes cuando sean compatibles.
- Adaptar funciones mediante cambios pequeños si esto preserva su comportamiento.
- Crear componentes nuevos solo ante diferencias funcionales o técnicas justificadas.
- No duplicar cálculos, validaciones, transformaciones ni generación de reportes.
- Separar lectura de datos, procesamiento, cálculos y presentación cuando corresponda.
- Favorecer alta cohesión y bajo acoplamiento sin fragmentar excesivamente el código.
- En proyectos multiplataforma, compartir la lógica independiente de la plataforma.
- Mantener separadas las operaciones específicas de cada lenguaje o dispositivo.
- No forzar la reutilización cuando comprometa la corrección técnica.

## 4. Cambios mínimos y compatibilidad

- Modificar únicamente los archivos necesarios para cumplir la solicitud.
- Preservar interfaces públicas, parámetros, retornos, cálculos y formatos existentes.
- No alterar funcionalidades anteriores sin autorización explícita.
- No cambiar nombres, directorios ni estilos de archivos ajenos a la tarea.
- No reformatear archivos completos por una modificación localizada.
- Informar antes de implementar si existe un riesgo significativo de regresión.
- Comunicar mejoras fuera del alcance sin aplicarlas automáticamente.
- No instalar nuevas dependencias sin justificar su necesidad.

## 5. Calidad, simplicidad y estilo del código

- Cada función debe tener una responsabilidad clara y un nombre descriptivo.
- Procurar funciones de 10 a 30 líneas como orientación, no como límite rígido.
- Dividir funciones extensas solo cuando mejore realmente la comprensión.
- Evitar funciones auxiliares triviales que solo deleguen una llamada.
- Reducir anidamientos y condiciones complejas; usar retornos tempranos si ayudan.
- Preferir código explícito antes que expresiones compactas difíciles de leer.
- Evitar parámetros excesivos y optimizaciones prematuras.
- Mantener juntas las funciones relacionadas sin crear un archivo por función.
- Seguir el estilo existente
- En Python: 4 espacios, snake_case, PascalCase y constantes UPPER_SNAKE_CASE.
- Preferir anotaciones de tipos y líneas de hasta 88 caracteres cuando corresponda.
- Si estas convenciones difieren del proyecto, priorizar su consistencia interna.
- No usar numeros magicos, en su lugar usar constantes

## 6. Documentación

- Documentar las funciones y métodos nuevos mediante docstrings breves.
- En Python, preferir Google Style, con Args, Returns y Raises cuando apliquen.
- Describir propósito, parámetros, retorno y excepciones relevantes.
- Actualizar los docstrings de funciones cuyo comportamiento se modifique.
- Evitar comentarios redundantes y explicaciones de instrucciones evidentes.
- Usar comentarios internos para decisiones técnicas que no sean obvias.

## 7. Pruebas y verificación

- Ejecutar primero las pruebas específicas relacionadas con los cambios.
- Agregar pruebas para funcionalidades nuevas cuando corresponda.
- Verificar contratos y resultados de las funciones reutilizadas o adaptadas.
- Ejecutar pruebas generales cuando el alcance o riesgo de regresión lo justifique.
- Evitar ejecutar reiteradamente pruebas sin cambios relevantes entre ejecuciones.
- No modificar pruebas existentes solo para ocultar defectos.
- No simular resultados ni afirmar que se ejecutó algo que no se ejecutó.
- Si faltan herramientas, datos o dependencias, indicar la limitación claramente.
- Comprobar que no se haya introducido duplicación o complejidad innecesaria.

## 8. Restricciónes
- Evitar números mágicos; utilizar constantes con nombres descriptivos cuando mejoren la comprensión.
- Definir las constantes compartidas en `definiciones.py`. Mantener constantes específicas dentro de sus módulos cuando no necesiten compartirse.
- No usar expresiones complejas.
- Usar 

## 9. Procedimiento y respuesta final

### Antes de implementar

- Determinar el alcance, los archivos afectados y las funciones reutilizables.
- Identificar adaptaciones necesarias, componentes nuevos y riesgos relevantes.
- Para tareas complejas, solicitar aprobación del plan antes de modificar código.

### Durante la implementación

- Aplicar cambios pequeños y localizados, reutilizando el código existente.
- No realizar trabajo adicional fuera del alcance solicitado.
- Evitar comandos exploratorios repetitivos y pruebas generales innecesarias.

### Al finalizar

Informar brevemente:

1. Archivos creados o modificados y motivo.
2. Funciones reutilizadas o adaptadas.
3. Componentes nuevos y justificación, si los hubiera.
4. Pruebas efectivamente ejecutadas y resultados.
5. Limitaciones o riesgos pendientes.

**Regla final: reutilizar primero, adaptar solo lo necesario y crear código nuevo únicamente cuando esté justificado. La corrección técnica tiene prioridad sobre el ahorro de operaciones.**
