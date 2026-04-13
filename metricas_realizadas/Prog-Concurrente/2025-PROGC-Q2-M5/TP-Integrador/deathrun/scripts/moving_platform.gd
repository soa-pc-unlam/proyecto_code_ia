extends AnimatableBody2D

# Configuración del movimiento
@export var move_distance: float = 180.0  # Distancia a moverse (píxeles)
@export var move_speed: float = 200.0  # Velocidad de movimiento
@export var move_direction: Vector2 = Vector2.RIGHT  # Dirección (RIGHT, LEFT, UP, DOWN)
@export var auto_start: bool = false  # Empieza automáticamente
@export var wait_time: float = 0.5  # Tiempo de espera en cada extremo

# Configuración de detección de proximidad
@export var activate_on_proximity: bool = true  # Activar por proximidad
@export var detection_range: float = 90.0  # Distancia de detección
@export var one_cycle_only: bool = true  # Solo un ciclo por activación

# Referencias
@onready var sprite = $Sprite2D
var player: Node = null

# Estado interno
var start_position: Vector2
var target_position: Vector2
var is_moving: bool = false
var is_waiting: bool = false
var wait_timer: float = 0.0
var moving_forward: bool = true
var has_completed_cycle: bool = false  # Para rastrear si completó un ciclo

func _ready():
	# Guardar posición inicial
	start_position = global_position
	
	# Calcular posición objetivo
	target_position = start_position + (move_direction.normalized() * move_distance)
	
	# Buscar al jugador
	await get_tree().process_frame
	player = get_tree().get_first_node_in_group("player")
	
	# Iniciar movimiento si auto_start
	if auto_start:
		start_moving()

func _physics_process(delta):
	# Detectar proximidad del jugador solo si no está en movimiento
	if activate_on_proximity and not is_moving and player and not has_completed_cycle:
		var distance_to_player = global_position.distance_to(player.global_position)
		if distance_to_player <= detection_range:
			start_moving()
	
	# Si el jugador se alejó mucho, resetear el flag
	if player and has_completed_cycle:
		var distance_to_player = global_position.distance_to(player.global_position)
		if distance_to_player > detection_range * 1.5:  # Un poco más lejos para evitar activaciones repetidas
			has_completed_cycle = false
	
	if not is_moving:
		return
	
	# Si está esperando en un extremo
	if is_waiting:
		wait_timer -= delta
		if wait_timer <= 0:
			is_waiting = false
			moving_forward = not moving_forward
		return
	
	# Determinar posición objetivo actual
	var current_target = target_position if moving_forward else start_position
	
	# Mover hacia el objetivo
	var direction = (current_target - global_position).normalized()
	var distance_to_target = global_position.distance_to(current_target)
	
	if distance_to_target < 1.0:
		# Llegó al extremo
		global_position = current_target
		
		# Si llegó de vuelta al inicio y one_cycle_only está activo
		if one_cycle_only and not moving_forward:
			# Completó el ciclo (ida y vuelta)
			stop_moving()
			has_completed_cycle = true
		else:
			start_waiting()
	else:
		# Seguir moviendo
		var velocity = direction * move_speed
		global_position += velocity * delta

func start_moving():
	"""Inicia el movimiento de la plataforma"""
	is_moving = true
	moving_forward = true
	has_completed_cycle = false

func stop_moving():
	"""Detiene el movimiento de la plataforma"""
	is_moving = false
	is_waiting = false

func start_waiting():
	"""Inicia el tiempo de espera en un extremo"""
	is_waiting = true
	wait_timer = wait_time

func reset_position():
	"""Reinicia la plataforma a su posición inicial"""
	global_position = start_position
	moving_forward = true
	is_waiting = false
	wait_timer = 0.0
	has_completed_cycle = false
