extends Node

class_name PowerUpSystem

# CORRECCIÓN: Usamos la clase base genérica para que acepte a White y Black
var player: Bomberman

@onready var bomb_placement_system: BombPlacementSystem = $"../BombPlacementSystem"
@onready var animated_sprite_2d: AnimatedSprite2D = $"../AnimatedSprite2D"
@onready var speed_up_timer: Timer = $SpeedUpTimer

const SPEED_MULTIPLIER = 2

func _ready() -> void:
	# Esto obtendrá al padre, sea WhiteBomberman o BlackBomberman
	player = get_parent()

func enable_power_up(power_up_type: Utils.PowerUpType): # Asegúrate que sea 'Utils' con mayúscula si así llamaste a la clase
	match power_up_type:
		Utils.PowerUpType.BOMB_UP:
			player.max_bombs += 1
			
		Utils.PowerUpType.FIRE_UP:
			bomb_placement_system.explosion_size += 1
			
		Utils.PowerUpType.SPEED_UP:
			player.movement_speed *= SPEED_MULTIPLIER
			animated_sprite_2d.speed_scale = 2
			speed_up_timer.start()
			
		Utils.PowerUpType.WALL_PASS:
			# OJO: Esta lógica del grupo "raycasts" afectará a TODOS los jugadores a la vez.
			# Lo ideal a futuro es pedirle al 'player' específico sus raycasts, 
			# pero por ahora funcionará para probar.
			var raycasts_nodes = get_tree().get_nodes_in_group("raycasts") as Array[RayCast2D]
			for raycast in raycasts_nodes:
				raycast.set_collision_mask_value(3, false)

func _on_speed_up_timer_timeout() -> void:
	player.movement_speed /= SPEED_MULTIPLIER
	animated_sprite_2d.speed_scale = 1
