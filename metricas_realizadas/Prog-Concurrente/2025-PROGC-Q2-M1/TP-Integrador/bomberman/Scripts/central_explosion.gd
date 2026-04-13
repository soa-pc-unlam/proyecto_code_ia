extends Area2D


class_name CentralExplosion
const EXPLOSION_SIZE = 1
const TILE_SIZE = 16

@onready var raycasts: Array[RayCast2D] = [
	$RayCasts/RayCastsUp,
	$RayCasts/RayCastsRight,
	$RayCasts/RayCastsDown,
	$RayCasts/RayCastsLeft
]
@onready var audio_explosion: AudioStreamPlayer2D = $AudioExplosion

var animation_names = ["explosion_up", "explosion_right", "explosion_down", "explosion_left"]
var animation_directions = [
	Vector2(0, -TILE_SIZE),
	Vector2(TILE_SIZE, 0),
	Vector2(0, TILE_SIZE),
	Vector2(-TILE_SIZE, 0)
]

const DIRECTIONAL_EXPLOSION = preload("res://Scenes/directional_explosion.tscn")

var size = EXPLOSION_SIZE


func _ready() -> void:
	check_raycasts()
	audio_explosion.play()
	
#UP DIRECTION
func check_raycasts():
	for i in raycasts.size():
		check_raycasts_for_direction(animation_names[i], raycasts[i], animation_directions[i])

func check_raycasts_for_direction(animation_name: String, raycasts: RayCast2D, animation_direction: Vector2):
	raycasts.target_position = raycasts.target_position * size
	raycasts.force_raycast_update()
	if !raycasts.is_colliding():
		create_explosion_for_size(size, animation_name, animation_direction)
	else:
		var size_of_explosion = calculate_size_of_explosion(raycasts)
		var collider = raycasts.get_collider()
		if size_of_explosion != null:
			create_explosion_for_size(size_of_explosion, animation_name, animation_direction)
		execute_explosion_collision(collider)

func create_explosion_for_size(size: int, animation_name: String, animation_position: Vector2):
	for i in size:
		if i < size - 1:
			#until we hit the last tile create middle animations
			create_explosion_animation_slice("%s_middle" % animation_name, animation_position * (i+1))
		else:
			create_explosion_animation_slice("%s_end" % animation_name, animation_position * (i+1))
			
func create_explosion_animation_slice(animation_name: String, animation_position: Vector2):
	var directional_explosion = DIRECTIONAL_EXPLOSION.instantiate()
	directional_explosion.position = animation_position
	add_child(directional_explosion)
	directional_explosion.play_animation(animation_name)
	
func calculate_size_of_explosion(raycasts: RayCast2D):
	var collider = raycasts.get_collider()
	if collider is TileMapLayer:
		var collision_point = raycasts.get_collision_point()
		var distance_to_collider = raycasts.global_position.distance_to(collision_point)
		var size_of_explosion_before_collider = max(roundi(absf(distance_to_collider) / TILE_SIZE - 1), 0)
		return	size_of_explosion_before_collider	
	
func execute_explosion_collision (collider: Object):
	if collider is BrickWall:
		(collider as BrickWall).destroy()
		

func _on_animated_sprite_2d_animation_finished() -> void:
	queue_free()


func _on_area_entered(area: Area2D) -> void:
	if area is Bomberman:
		(area as Bomberman).die()
