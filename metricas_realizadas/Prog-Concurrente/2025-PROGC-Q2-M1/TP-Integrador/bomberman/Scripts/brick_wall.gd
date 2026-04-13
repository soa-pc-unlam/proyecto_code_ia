extends Area2D

class_name BrickWall
@onready var animated_sprite_2d: AnimatedSprite2D = $AnimatedSprite2D
const POWER_UP_SCENE = preload("res://Scenes/power_up.tscn")

@export var power_up_res: PowerUpRes

func destroy():
	animated_sprite_2d.play("destroy")
	

func _on_animated_sprite_2d_animation_finished() -> void:
	if animated_sprite_2d.animation == "destroy":
		if power_up_res != null:
				spawn_power_up()
		queue_free()


func spawn_power_up():
	var power_up = POWER_UP_SCENE.instantiate()
	power_up.global_position = global_position
	get_tree().root.add_child(power_up)
	power_up.init(power_up_res)
