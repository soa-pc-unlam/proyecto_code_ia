extends AnimatableBody2D

# ---------- Config ----------
@export var trap_id: int = 0
@export var drop_distance: float = 180.0
@export var drop_time: float = 0.7
@export var hold_time: float = 0.60
@export var rise_time: float = 0.35
@export var cooldown_time: float = 1.20

# Control de activación
@export var auto_activate: bool = true  # Se activa sola en singleplayer

# ---------- Estado ----------
enum TrapState { INACTIVE, DROPPING, HOLDING, RAISING, COOLDOWN, DISABLED }
var state: TrapState = TrapState.INACTIVE
var busy: bool = false
var original_y: float
var player_in_trigger: bool = false

# ---------- Nodos ----------
@onready var solid_col: CollisionShape2D = $CollisionShape2D
@onready var kill_zone: Area2D = $KillZone
@onready var trigger_zone: Area2D = $TriggerZone
@onready var sprite: Sprite2D = get_node_or_null("Sprite2D")

# Señales para TrapManager
signal trap_activated(trap_id)
signal trap_deactivated(trap_id)
signal player_hit(trap_id)

func _ready() -> void:
	add_to_group("traps")
	original_y = global_position.y

	# Conexiones
	trigger_zone.body_entered.connect(_on_trigger_enter)
	trigger_zone.body_exited.connect(_on_trigger_exit)
	kill_zone.body_entered.connect(_on_kill_enter)

	# La zona que mata solo se usa durante la caída/hold
	_set_kill_enabled(false)
	
	# En modo multijugador, desactivar activación automática
	if NetworkManager.is_multiplayer_active() and multiplayer.is_server():
		auto_activate = false
		print("[Crusher %d] Modo multijugador: auto_activate = false" % trap_id)
	
	_update_visual()

func _on_trigger_enter(body: Node) -> void:
	if not body.is_in_group("player"):
		return
	
	# Solo el servidor maneja la lógica
	if NetworkManager.is_multiplayer_active() and not multiplayer.is_server():
		return
	
	player_in_trigger = true
	
	# Solo activar automáticamente si está permitido
	if auto_activate:
		_try_start_cycle_server()

func _on_trigger_exit(body: Node) -> void:
	if body.is_in_group("player"):
		player_in_trigger = false

func _on_kill_enter(body: Node) -> void:
	# Solo el servidor maneja colisiones
	if NetworkManager.is_multiplayer_active() and not multiplayer.is_server():
		return

	if body.is_in_group("player") and (state == TrapState.DROPPING or state == TrapState.HOLDING):
		if body.has_method("die"):
			body.die()
			player_hit.emit(trap_id)
			print("[Crusher %d] ☠️ Jugador eliminado!" % trap_id)

# =============== API para Trap Master / TrapManager =================
func force_activate() -> void:
	"""Método para que el Trap Master active manualmente la trampa"""
	# Solo el servidor puede iniciar el ciclo
	if NetworkManager.is_multiplayer_active() and not multiplayer.is_server():
		print("[Crusher %d] ⚠️ Cliente no puede activar trampas" % trap_id)
		return
	
	_try_start_cycle_server()

# =============== Lógica server-authoritative ========================
func _try_start_cycle_server() -> void:
	"""Intenta iniciar el ciclo (con validaciones)"""
	if busy or state == TrapState.DISABLED:
		print("[Crusher %d] ⏸️ Trampa ocupada o deshabilitada" % trap_id)
		return
	
	if state != TrapState.INACTIVE:
		print("[Crusher %d] ⏸️ Estado actual: %s" % [trap_id, _get_state_name()])
		return
	
	_start_cycle_server()

func _start_cycle_server() -> void:
	"""Inicia el ciclo completo de la trampa (DROP → HOLD → RAISE → COOLDOWN)"""
	busy = true
	print("[Crusher %d] 🔨 Ciclo iniciado" % trap_id)
	trap_activated.emit(trap_id)

	# 1) DROPPING
	state = TrapState.DROPPING
	_set_kill_enabled(true)
	_update_visual()
	
	# Sincronizar ANTES del tween para que los clientes animen también
	_sync_state_to_clients()
	
	# Notificar a los clientes que inicien el tween
	if NetworkManager.is_multiplayer_active():
		rpc("sync_drop_animation", original_y + drop_distance, drop_time)
	
	await _tween_y(original_y + drop_distance, drop_time, Tween.TRANS_QUAD, Tween.EASE_IN)

	# 2) HOLDING
	state = TrapState.HOLDING
	_set_kill_enabled(true)
	_update_visual()
	_sync_state_to_clients()
	await get_tree().create_timer(hold_time).timeout

	# 3) RAISING
	state = TrapState.RAISING
	_set_kill_enabled(false)
	_update_visual()
	
	# Sincronizar animación de subida
	if NetworkManager.is_multiplayer_active():
		rpc("sync_rise_animation", original_y, rise_time)
	
	_sync_state_to_clients()
	await _tween_y(original_y, rise_time, Tween.TRANS_SINE, Tween.EASE_OUT)

	trap_deactivated.emit(trap_id)

	# 4) COOLDOWN
	state = TrapState.COOLDOWN
	_update_visual()
	_sync_state_to_clients()
	await get_tree().create_timer(cooldown_time).timeout

	# 5) VOLVER A INACTIVO
	state = TrapState.INACTIVE
	_update_visual()
	_sync_state_to_clients()
	busy = false
	print("[Crusher %d] ✅ Ciclo completado" % trap_id)

# RPC para animar la caída en clientes
@rpc("authority", "call_remote", "reliable")
func sync_drop_animation(target_y: float, duration: float) -> void:
	"""Sincroniza la animación de caída en los clientes"""
	if multiplayer.is_server():
		return  # El servidor ya tiene su propia animación
	
	print("[Crusher %d] 📡 Cliente: Animando caída" % trap_id)
	_tween_y(target_y, duration, Tween.TRANS_QUAD, Tween.EASE_IN)

# RPC para animar la subida en clientes
@rpc("authority", "call_remote", "reliable")
func sync_rise_animation(target_y: float, duration: float) -> void:
	"""Sincroniza la animación de subida en los clientes"""
	if multiplayer.is_server():
		return  # El servidor ya tiene su propia animación
	
	print("[Crusher %d] 📡 Cliente: Animando subida" % trap_id)
	_tween_y(target_y, duration, Tween.TRANS_SINE, Tween.EASE_OUT)

# Puedes simplificar o remover sync_crusher_state ya que ahora sincronizas las animaciones
@rpc("authority", "call_remote", "reliable")
func sync_crusher_state(state_i: int) -> void:
	"""Recibe el estado desde el servidor (solo clientes)"""
	if multiplayer.is_server():
		return
	
	state = state_i as TrapState
	# Ya no teleportamos, las animaciones manejan la posición
	# global_position.y = y_pos  # Comentar esta línea
	_update_visual()
	
	# Activar/desactivar kill zone según el estado
	match state:
		TrapState.DROPPING, TrapState.HOLDING:
			_set_kill_enabled(true)
		_:
			_set_kill_enabled(false)
	
	print("[Crusher %d] 📡 Estado sincronizado: %s" % [trap_id, _get_state_name()])

func _tween_y(target_y: float, t: float, trans := Tween.TRANS_SINE, ease := Tween.EASE_IN_OUT) -> void:
	"""Anima el movimiento vertical"""
	var tw := create_tween().set_trans(trans).set_ease(ease)
	tw.tween_property(self, "global_position:y", target_y, max(t, 0.0))
	await tw.finished

# =============== Sincronización Multiplayer ========================
func _sync_state_to_clients() -> void:
	"""Sincroniza el estado actual a todos los clientes"""
	if NetworkManager.is_multiplayer_active() and multiplayer.is_server():
		rpc("sync_crusher_state", int(state), global_position.y)

# =============== Utilidades ===========================
func _set_kill_enabled(enabled: bool) -> void:
	"""Habilita/deshabilita la zona de muerte"""
	var col := kill_zone.get_node_or_null("CollisionShape2D") as CollisionShape2D
	if col:
		col.disabled = not enabled
	kill_zone.monitoring = enabled
	kill_zone.monitorable = true

func _update_visual() -> void:
	"""Actualiza el color según el estado"""
	if not sprite:
		return
	
	match state:
		TrapState.INACTIVE:
			sprite.modulate = Color(0.5, 0.5, 0.5)  # Gris
		TrapState.DROPPING, TrapState.HOLDING:
			sprite.modulate = Color(1.0, 0.0, 0.0)  # Rojo (peligro)
		TrapState.RAISING:
			sprite.modulate = Color(0.0, 0.5, 1.0)  # Azul (subiendo)
		TrapState.COOLDOWN:
			sprite.modulate = Color(0.6, 0.6, 1.0)  # Azul claro (recargando)
		TrapState.DISABLED:
			sprite.modulate = Color(0.3, 0.3, 0.3)  # Gris oscuro

func _get_state_name() -> String:
	"""Retorna el nombre del estado actual (para debug)"""
	match state:
		TrapState.INACTIVE:
			return "Inactiva"
		TrapState.DROPPING:
			return "Cayendo"
		TrapState.HOLDING:
			return "Aplastando"
		TrapState.RAISING:
			return "Subiendo"
		TrapState.COOLDOWN:
			return "Recargando"
		TrapState.DISABLED:
			return "Deshabilitada"
	return "Desconocido"

func get_state_name() -> String:
	"""API pública para obtener el estado (compatible con Spike Trap)"""
	return _get_state_name()
