extends Area2D

# ===== ID y Señales =====
@export var trap_id: int = 0
@export var auto_activate: bool = true

# ===== Config =====
@export var detection_range: float = 110.0
@export var activando_duration: float = 0.8
@export var activado_duration: float = 3.0
@export var apagar_duration: float = 0.6
@export var cooldown_duration: float = 5.0

# ===== Estados =====
enum TrapState { INACTIVE, ACTIVANDO, ACTIVADO, APAGANDO, COOLDOWN }
var current_state: TrapState = TrapState.INACTIVE
var state_timer: float = 0.0
var player_in_range: bool = false

# ===== Señales para TrapManager =====
signal trap_activated(trap_id: int)
signal trap_deactivated(trap_id: int)
signal player_hit(trap_id: int)

# ===== Nodos =====
@onready var sprite: AnimatedSprite2D = $AnimatedSprite2D
@onready var hit_shape: CollisionShape2D = $CollisionShape2D

# ===== Referencias =====
var player: Node = null

func _ready() -> void:
	add_to_group("traps")
	monitoring = true
	monitorable = true

	hit_shape.disabled = false
	sprite.play("idle")

	await get_tree().process_frame
	player = get_tree().get_first_node_in_group("player")
	
	# Desactivar auto-activación en cliente
	if NetworkManager.is_multiplayer_active() and multiplayer.is_server():
		auto_activate = false
		print("[FireTrap %d] Modo cliente: auto-activación deshabilitada" % trap_id)

	# Conectar señales de detección
	body_entered.connect(_on_body_entered)
	body_exited.connect(_on_body_exited)

func _process(delta: float) -> void:
	# Solo el servidor procesa la lógica
	if NetworkManager.is_multiplayer_active() and not multiplayer.is_server():
		return
	
	# Máquina de estados (igual que spike_trap)
	match current_state:
		TrapState.INACTIVE:
			if auto_activate and player_in_range:
				activate()
		
		TrapState.ACTIVANDO:
			state_timer -= delta
			if state_timer <= 0:
				set_state(TrapState.ACTIVADO)
		
		TrapState.ACTIVADO:
			state_timer -= delta
			if state_timer <= 0:
				set_state(TrapState.APAGANDO)
		
		TrapState.APAGANDO:
			state_timer -= delta
			if state_timer <= 0:
				set_state(TrapState.COOLDOWN)
		
		TrapState.COOLDOWN:
			state_timer -= delta
			if state_timer <= 0:
				set_state(TrapState.INACTIVE)

func activate():
	"""Activa la trampa (igual que spike_trap)"""
	# Solo el servidor puede activar
	if NetworkManager.is_multiplayer_active() and not multiplayer.is_server():
		return
	
	if current_state != TrapState.INACTIVE:
		return  # No se puede activar si no está inactiva
	
	print("[FireTrap %d] Activando trampa" % trap_id)
	set_state(TrapState.ACTIVANDO)
	trap_activated.emit(trap_id)

func set_state(new_state: TrapState):
	print("[FireTrap %d] Cambiando estado a: %s" % [trap_id, _state_to_string(new_state)])
	current_state = new_state
	
	match new_state:
		TrapState.INACTIVE:
			state_timer = 0
			# ANTES:
			# hit_shape.set_deferred("disabled", true)
			# AHORA: siempre habilitado para poder detectar al jugador
			hit_shape.set_deferred("disabled", false)
			sprite.play("idle")
		
		TrapState.ACTIVANDO:
			state_timer = activando_duration
			# Podés dejarlo habilitado también, no mata igual porque mirás el estado:
			hit_shape.set_deferred("disabled", false)
			sprite.play("activando")
		
		TrapState.ACTIVADO:
			state_timer = activado_duration
			hit_shape.set_deferred("disabled", false)
			sprite.play("activado")
		
		TrapState.APAGANDO:
			state_timer = apagar_duration
			# Podés elegir: dejarlo habilitado para seguir detectando rango
			hit_shape.set_deferred("disabled", false)
			sprite.play("apagar")
		
		TrapState.COOLDOWN:
			state_timer = cooldown_duration
			hit_shape.set_deferred("disabled", false)
			sprite.play("idle")

	
	# Sincronizar por red
	if NetworkManager.is_multiplayer_active() and multiplayer.is_server():
		rpc("sync_trap_state", new_state)

@rpc("authority", "call_remote", "reliable")
func sync_trap_state(state: int):
	"""Sincroniza el estado desde el servidor (igual que spike_trap)"""
	if not multiplayer.is_server():
		print("[FireTrap %d] Cliente recibió estado: %s" % [trap_id, _state_to_string(state)])
		current_state = state as TrapState
		
		# Sincronizar visual
		match current_state:
			TrapState.INACTIVE:
				sprite.play("idle")
				hit_shape.disabled = true
			TrapState.ACTIVANDO:
				sprite.play("activando")
				hit_shape.disabled = true
			TrapState.ACTIVADO:
				sprite.play("activado")
				hit_shape.disabled = false
			TrapState.APAGANDO:
				sprite.play("apagar")
				hit_shape.disabled = true
			TrapState.COOLDOWN:
				sprite.play("idle")
				hit_shape.disabled = true

func _on_body_entered(body: Node) -> void:
	"""Detecta cuando el jugador entra"""
	# Solo el servidor maneja colisiones
	if NetworkManager.is_multiplayer_active() and not multiplayer.is_server():
		return
	
	if body.is_in_group("player"):
		player_in_range = true
		
		# Si está activa, mata al jugador
		if current_state == TrapState.ACTIVADO and body.has_method("die"):
			body.die()
			player_hit.emit(trap_id)
			print("[FireTrap %d] ¡Jugador golpeado!" % trap_id)

func _on_body_exited(body: Node) -> void:
	"""Detecta cuando el jugador sale"""
	if NetworkManager.is_multiplayer_active() and not multiplayer.is_server():
		return
	
	if body.is_in_group("player"):
		player_in_range = false

# ===== Métodos requeridos por TrapManager =====

func force_activate():
	"""Método para que el Trap Master active manualmente (igual que spike_trap)"""
	print("[FireTrap %d] force_activate() llamado" % trap_id)
	if current_state == TrapState.INACTIVE:
		activate()
	else:
		print("[FireTrap %d] No se puede activar (Estado: %s)" % [trap_id, get_state_name()])

func get_state_name() -> String:
	"""Retorna el nombre del estado actual"""
	return _state_to_string(current_state)

# ===== Helpers =====

func _state_to_string(state: TrapState) -> String:
	"""Convierte estado enum a string"""
	match state:
		TrapState.INACTIVE:
			return "Inactiva"
		TrapState.ACTIVANDO:
			return "Cargando"
		TrapState.ACTIVADO:
			return "Activa"
		TrapState.APAGANDO:
			return "Apagando"
		TrapState.COOLDOWN:
			return "Cooldown"
		_:
			return "Desconocido"
