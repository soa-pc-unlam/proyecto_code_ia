# Prompt técnico y evolución del proyecto

## 1. Prompt inicial

El proyecto comenzó con un prompt acotado, orientado a obtener un esqueleto
jugable mínimo en pocos pasos:

```
Quiero hacer un juego 2D top-down sencillo en Java usando solo
Swing/AWT, sin librerías externas. La idea es un sheriff en un
mapa estilo western.

Necesito que arranques con lo más básico:
- Una ventana JFrame con un JPanel que renderice el mundo.
- Un game loop corriendo en su propio Thread (separado del
  Event Dispatch Thread de Swing).
- Un mapa por tiles cargado desde un archivo de recursos.
- Un jugador controlado con WASD que se desplaza por el mapa,
  con una cámara que lo sigue.
- Estructura del proyecto en un solo paquete SheriffsssPackage,
  recursos bajo resources/, compilable con javac y java -cp.

No incluyas todavía enemigos, audio ni menús: que sea un
esqueleto mínimo que pueda compilar, correr y mover el personaje.
```

A partir de este prompt el modelo entregó la primera versión funcional con:
`Main.java`, `Game.java` (con el game loop + render), `GameMap.java`,
`Player.java`, `Camera.java`, `TileType.java`, `AssetManager.java`, y los
recursos básicos. Esta base sirvió como punto de anclaje para las
iteraciones posteriores.

---

## 2. Evolución por iteraciones

Cada iteración se inició con un nuevo prompt sobre la base ya generada,
pidiendo agregar una feature o refinar una existente. Las iteraciones se
agrupan a continuación por bloque temático; dentro de cada bloque hubo
sub-prompts de ajuste (correcciones de bugs, tweaks de balance, refactor
puntual).

### Bloque A — Combate y enemigos
- **Iter A1**: agregar enemigos básicos con vida y colisión
  (`Enemy.java`, `EnemyType.java`).
- **Iter A2**: sistema de proyectiles con click izquierdo
  (`Projectile.java`, `ProjectileSystem.java`, `ProjectileType.java`).
- **Iter A3**: múltiples armas con stats configurables —
  cadencia, daño, dispersión (`ProjectileWeaponStats.java`,
  `ProjectileStatModifiers.java`, `WeaponType.java`,
  `Equipment.java`, `ItemDefinition.java`,
  `ItemDefinitionDrawConfig.java`).
- **Iter A4**: IA de enemigos (patrullaje + persecución) y
  organización en un sistema dedicado
  (`EnemySystem.java`, `EnemyBehavior.java`, `EnemyDensity.java`).
- **Iter A5**: efectos de combate — números flotantes de daño,
  burst de fuego, sonidos de impacto
  (`CombatFloatingText.java`, `FlameBurstEffect.java`,
  `EnemyHitSound.java`).

### Bloque B — Mundo y ambientación
- **Iter B1**: objetos del mapa (rocas, cactus, etc.) con
  colisión (`MapObject.java`, `MapObjectType.java`).
- **Iter B2**: ciclo día/noche con transiciones suaves
  (`DayNightCycle.java`, `DayPhase.java`, `GameTheme.java`).
- **Iter B3**: sistema de iluminación dinámica (luces puntuales,
  oscurecimiento nocturno) (`WorldLighting.java`).

### Bloque C — Audio
- **Iter C1**: reproducción de música de fondo en loop
  (`AudioManager.java` primera versión).
- **Iter C2**: efectos de sonido superpuestos (disparos, pasos,
  daño) — requirió hacer el `AudioManager` thread-safe porque el
  game loop y los eventos del EDT pueden disparar SFX
  simultáneamente. Se introdujeron los `synchronized` y la
  gestión de múltiples `Clip` paralelos.

### Bloque D — Modo Entrenamiento (tutorial)
- **Iter D1**: introducir un modo "Entrenamiento" separado del
  modo Partida, accesible desde el menú principal
  (`TrainingMode.java`, `TrainingControls.java`).
- **Iter D2**: tutorial paso-a-paso con mensajes en pantalla y
  avance por condición o por tiempo
  (`TutorialStep.java`, `TutorialEventType.java`).
- **Iter D3**: para que el tutorial no bloquee el game loop, se
  movió la lógica de avance a un **hilo dedicado**
  (`TutorialThread.java`), con comunicación thread-safe vía
  `AtomicBoolean` (`skipRequested`, `finished`) y `Thread.join`
  con timeout para el shutdown ordenado. **Este fue el momento en
  que la complejidad concurrente del proyecto creció
  significativamente.**

### Bloque E — Interfaz y configuración
- **Iter E1**: menú principal navegable
  (`MenuRenderer.java`, `States.java`).
- **Iter E2**: pantalla de settings — resolución, fullscreen,
  volúmenes (`GameConfig.java`, métodos `updateSettings` en
  `Game.java`).
- **Iter E3**: sistema de guardado de progreso (`saves/`).
- **Iter E4**: texto en pantalla con tipografía propia
  (`TextRenderer.java`).

### Bloque F — Debug y herramientas internas
- **Iter F1**: overlays de debug activables por teclado — FPS,
  hitboxes (`DebugOptions.java`).
- **Iter F2**: visualización de trayectorias de bala para
  balanceo (`DebugBulletTrajectory.java`).

### Bloque G — Pulido y correcciones
- **Iter G1**: shutdown hook con `Runtime.addShutdownHook` para
  cerrar el game loop, el TutorialThread y liberar audio cuando
  el usuario cierra la ventana sin pasar por el menú
  (`Main.java`).
- **Iter G2**: marcas `volatile` en `Game.gameThread` y
  `Game.shuttingDown` tras detectar que el shutdown podía dejar
  el thread vivo en ciertas condiciones.
- **Iter G3**: refactor del `AudioManager` para serializar todas
  las mutaciones bajo `synchronized this`.
- **Iter G4**: ajustes de input handling (`GameInput.java`) —
  el `keyPressed`/`keyReleased` creció en complejidad porque
  acumula bindings de gameplay + debug + menú.
- **Iter G5**: corrección de assets faltantes, paths de
  recursos, errores de carga de sprites.

---

## 3. Resumen del proceso

| Bloque | Iteraciones estimadas | Foco |
|---|---:|---|
| Inicial | 1 | Esqueleto: ventana, loop, mapa, jugador |
| A — Combate y enemigos | 5–8 | Gameplay principal |
| B — Mundo y ambientación | 3–4 | Inmersión visual |
| C — Audio | 2–3 | Atmósfera + concurrencia (AudioManager) |
| D — Tutorial | 3–5 | **Hilo dedicado (TutorialThread)** |
| E — Interfaz y configuración | 4–5 | UX |
| F — Debug | 2–3 | Tooling interno |
| G — Pulido y correcciones | 5–10 | Fixes, sincronización, portabilidad |
| **Total estimado** | **25–40 prompts principales**, con sub-prompts de ajuste que duplican o triplican esa cifra | — |

## 4. Modelo y herramienta utilizada

| Item | Valor |
|---|---|
| Herramienta de VibeCoding | **Claude Code (CLI)** |
| Modelos disponibles durante la generación | Claude Sonnet 4.x y Claude Opus 4.x (uso mixto según iteración) |
| Lenguaje objetivo | Java 17+ (probado con Amazon Corretto 20) |
| Dependencias externas | Ninguna — solo JDK estándar (Swing/AWT, `javax.sound.sampled`) |
