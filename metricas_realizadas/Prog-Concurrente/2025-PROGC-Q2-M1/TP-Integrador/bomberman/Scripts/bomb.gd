extends Area2D

class_name Bomb

const CENTRAL_EXPLOSION = preload("res://Scenes/central_explosion.tscn")
const MACRO_EXPLOSION_SIZE = 1

var explosion_size = MACRO_EXPLOSION_SIZE

func _on_timer_timeout() -> void:
	var explosion = CENTRAL_EXPLOSION.instantiate()
	explosion.position = position
	explosion.size = explosion_size
	get_tree().root.add_child(explosion)
	queue_free()
