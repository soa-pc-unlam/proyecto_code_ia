extends Area2D

# Configuración de la trampa
@export var trap_id: int = 0
@export var auto_activate: bool = true  # Se activa sola por proximidad
@export var activation_delay: float = 0.5  # Delay antes de activarse
@export var active_duration: float = 2.0  # Tiempo que está activa
@export var cooldown_duration: float = 1.0  # Tiempo de recarga

# Estados de la trampa ( múltiples trampas con estados independientes)
enum TrapState {
	INACTIVE,
	ACTIVATING,
	ACTIVE,
	COOLDOWN
}

var current_state: TrapState = TrapState.INACTIVE
var state_timer: float = 0.0
var player_in_range: bool = false

# Señales para comunicación (Signals - Comunicación entre objetos)
signal trap_activated(trap_id)
signal trap_deactivated(trap_id)
signal player_hit(trap_id)

# Referencias visuales
@onready var sprite = $Sprite2D
@onready var collision_shape = $CollisionShape2D

# Posición original para animación
var original_position: Vector2 = Vector2.ZERO
var raised_offset: float = -20.0  # Cuánto sube (negativo = arriba)

func _ready():
	# Configurar grupo para que el player la detecte
	add_to_group("traps")
	
	# Guardar posición original
	original_position = sprite.position
	
	# En modo multijugador, desactivar activación automática
	if NetworkManager.is_multiplayer_active() and multiplayer.is_server():
		auto_activate = false
		
	# Conectar señales de detección
	body_entered.connect(_on_body_entered)
	body_exited.connect(_on_body_exited)
	
	# Color inicial (gris = inactiva)
	update_visual()

func _process(delta):
	# Solo el servidor procesa la lógica de las trampas
	if NetworkManager.is_multiplayer_active() and not multiplayer.is_server():
		return  # El cliente solo ve, no procesa
	
	# Gestión del estado de la trampa
	match current_state:
		TrapState.INACTIVE:
			if auto_activate and player_in_range:
				activate()
		
		TrapState.ACTIVATING:
			state_timer -= delta
			if state_timer <= 0:
				set_state(TrapState.ACTIVE)
		
		TrapState.ACTIVE:
			state_timer -= delta
			if state_timer <= 0:
				deactivate()
		
		TrapState.COOLDOWN:
			state_timer -= delta
			if state_timer <= 0:
				set_state(TrapState.INACTIVE)

func activate():
	"""Activa la trampa (puede ser llamada por proximidad o por el Trap Master)"""
	# Solo el servidor puede activar trampas
	if NetworkManager.is_multiplayer_active() and not multiplayer.is_server():
		return
	
	if current_state != TrapState.INACTIVE:
		return  # No se puede activar si no está inactiva
	
	set_state(TrapState.ACTIVATING)
	trap_activated.emit(trap_id)

func deactivate():
	"""Desactiva la trampa y entra en cooldown"""
	# Solo el servidor puede desactivar trampas
	if NetworkManager.is_multiplayer_active() and not multiplayer.is_server():
		return
	
	set_state(TrapState.COOLDOWN)
	trap_deactivated.emit(trap_id)

func set_state(new_state: TrapState):
	"""Cambia el estado de la trampa"""
	current_state = new_state
	
	match new_state:
		TrapState.INACTIVE:
			state_timer = 0
			collision_shape.set_deferred("disabled", true)
			animate_down()
		
		TrapState.ACTIVATING:
			state_timer = activation_delay
			collision_shape.set_deferred("disabled", true)
			animate_up()
		
		TrapState.ACTIVE:
			state_timer = active_duration
			collision_shape.set_deferred("disabled", false)
		
		TrapState.COOLDOWN:
			state_timer = cooldown_duration
			collision_shape.set_deferred("disabled", true)
			animate_down()
	
	update_visual()
	
		# Sincronizar por red
	if multiplayer.is_server() and NetworkManager.is_multiplayer_active():
		rpc("sync_trap_state", new_state)

@rpc("authority", "call_remote", "reliable")
func sync_trap_state(state: int):
	"""Sincroniza el estado de la trampa desde el servidor"""
	if not multiplayer.is_server():
		current_state = state as TrapState
		update_visual()
		
		# Sincronizar animación visual
		match current_state:
			TrapState.ACTIVATING, TrapState.ACTIVE:
				animate_up()
			TrapState.INACTIVE, TrapState.COOLDOWN:
				animate_down()

func animate_up():
	"""Anima la trampa subiendo"""
	if not sprite:
		return
	
	var tween = create_tween()
	tween.tween_property(sprite, "position", original_position + Vector2(0, raised_offset), 0.2)
	tween.set_ease(Tween.EASE_OUT)
	tween.set_trans(Tween.TRANS_BACK)

func animate_down():
	"""Anima la trampa bajando"""
	if not sprite:
		return
	
	var tween = create_tween()
	tween.tween_property(sprite, "position", original_position, 0.2)
	tween.set_ease(Tween.EASE_IN)
	tween.set_trans(Tween.TRANS_BOUNCE)

func update_visual():
	"""Actualiza el color según el estado"""
	if not sprite:
		return
	
	match current_state:
		TrapState.INACTIVE:
			sprite.modulate = Color(0.5, 0.5, 0.5)  # Gris
		TrapState.ACTIVATING:
			sprite.modulate = Color(1, 1, 0)  # Amarillo (advertencia)
		TrapState.ACTIVE:
			sprite.modulate = Color(1, 0, 0)  # Rojo (peligro)
		TrapState.COOLDOWN:
			sprite.modulate = Color(0, 0.5, 1)  # Azul (recargando)

func _on_body_entered(body):
	"""Detecta cuando el jugador entra en el área"""
	# Solo el servidor maneja colisiones
	if NetworkManager.is_multiplayer_active() and not multiplayer.is_server():
		return
	
	if body.is_in_group("player"):
		player_in_range = true
		
		# Si está activa, mata al jugador
		if current_state == TrapState.ACTIVE:
			if body.has_method("die"):
				body.die()
				player_hit.emit(trap_id)

func _on_body_exited(body):
	"""Detecta cuando el jugador sale del área"""
	# Solo el servidor maneja colisiones
	if NetworkManager.is_multiplayer_active() and not multiplayer.is_server():
		return
	
	if body.is_in_group("player"):
		player_in_range = false

func force_activate():
	"""Método para que el Trap Master active manualmente la trampa"""
	if current_state == TrapState.INACTIVE:
		activate()

func get_state_name() -> String:
	"""Retorna el nombre del estado actual (útil para debug)"""
	match current_state:
		TrapState.INACTIVE:
			return "Inactiva"
		TrapState.ACTIVATING:
			return "Activando..."
		TrapState.ACTIVE:
			return "ACTIVA"
		TrapState.COOLDOWN:
			return "Recargando"
	return "Desconocido"
