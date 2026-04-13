extends Area2D

class_name DirectionalExplosion

@onready var animated_sprite_2D: AnimatedSprite2D = $AnimatedSprite2D

func play_animation(animation_name: String):
	animated_sprite_2D.play(animation_name)

func _on_area_entered(area: Area2D) -> void:
	if area is Bomberman:
		(area as Bomberman).die()
