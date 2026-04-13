extends Area2D

# 1. Definimos la clase genérica
class_name Bomberman

const MACRO_SPEED = 75
const MACRO_MAX_BOMBS = 1

@onready var animated_sprite_2d: AnimatedSprite2D = $AnimatedSprite2D
@onready var rayCasts: RayCasts = $RayCasts
# Nota: Asegúrate de que los nodos hijos en la escena tengan estos scripts asignados
@onready var bomb_placed_system: BombPlacementSystem = $BombPlacementSystem
@onready var power_up_system: PowerUpSystem = $PowerUpSystem

var movement: Vector2 = Vector2.ZERO

@export var movement_speed: float = MACRO_SPEED
var max_bombs = MACRO_MAX_BOMBS

# --- CONFIGURACIÓN PARA MULTIJUGADOR ---
@export_group("Configuración Jugador")
@export var animation_prefix: String = "black" # Ejemplo: "white", "black", "red"
@export var input_up: String = "black_up"
@export var input_down: String = "black_down"
@export var input_left: String = "black_left"
@export var input_right: String = "black_right"
@export var input_bomb: String = "black_bomb"

func _ready():
	# Puedes personalizar esto por jugador si quisieras layers distintos
	self.collision_layer = 1   
	self.collision_mask = 64   

func _process(delta: float) -> void:
	var collisions = rayCasts.check_collisions()
	# La lógica de movimiento se mantiene idéntica
	if collisions.has(movement):
		return
	
	position += movement * delta * movement_speed

func _input(event: InputEvent) -> void:
	# AHORA USAMOS LAS VARIABLES EXPORTADAS, NO EL TEXTO FIJO
	if Input.is_action_pressed(input_right):
		movement = Vector2.RIGHT
		# Construimos el nombre de la animación dinámicamente: "white" + "_" + "right"
		play_anim("right")
	elif Input.is_action_pressed(input_left):
		movement = Vector2.LEFT
		play_anim("left")
	elif Input.is_action_pressed(input_down):
		movement = Vector2.DOWN
		play_anim("down")
	elif Input.is_action_pressed(input_up):
		movement = Vector2.UP
		play_anim("up")
	elif Input.is_action_just_pressed(input_bomb):
		bomb_placed_system.place_bomb()
		
	else:
		movement = Vector2.ZERO
		animated_sprite_2d.stop()

# Función auxiliar para no repetir código de string
func play_anim(direction_suffix: String):
	var anim_name = animation_prefix + "_" + direction_suffix
	# Validación opcional para evitar errores si falta una animación
	if animated_sprite_2d.sprite_frames.has_animation(anim_name):
		animated_sprite_2d.play(anim_name)

func die():
	play_anim("die") # Asegúrate de tener "white_die", "black_die", etc.
	movement = Vector2.ZERO
	set_process_input(false)
	queue_free()

func _on_area_entered(area: Area2D) -> void:
	if area is PowerUp:
		power_up_system.enable_power_up((area as PowerUp).type)
		area.queue_free()
