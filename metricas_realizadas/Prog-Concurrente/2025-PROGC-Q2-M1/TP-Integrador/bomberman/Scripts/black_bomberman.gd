extends Area2D


class_name BlackBomberman
const MACRO_SPEED = 75
@onready var animated_sprite_2d: AnimatedSprite2D = $AnimatedSprite2D
@onready var rayCasts = $RayCasts
@onready var bomb_placed_system : BombPlacementSystem = $BombPlacementSystem

var movement: Vector2 = Vector2.ZERO

@export var movement_speed: float = MACRO_SPEED

func _process(delta: float) -> void:
	
	var collisions = rayCasts.check_collisions()
	if collisions.has(movement):
		return
	
	position += movement * delta * movement_speed

func _input(event: InputEvent) -> void:
	if Input.is_action_pressed("black_right"):
		movement = Vector2.RIGHT
		animated_sprite_2d.play("black_right")
	elif Input.is_action_pressed("black_left"):
		movement = Vector2.LEFT
		animated_sprite_2d.play("black_left")
	elif Input.is_action_pressed("black_down"):
		movement = Vector2.DOWN
		animated_sprite_2d.play("black_down")
	elif Input.is_action_pressed("black_up"):
		movement = Vector2.UP
		animated_sprite_2d.play("black_up")
	else:
		movement = Vector2.ZERO
		animated_sprite_2d.stop()
		
		

	
	
	
