extends Area2D

class_name PowerUp

@onready var sprite_2d: Sprite2D = $Sprite2D
var type: utils.PowerUpType

func _ready():
	self.collision_layer = 64  # Capa 7 (PowerUp): 2^6 = 64
	self.collision_mask = 1    # Detectar capa 1 (Player)

func init(power_up_res: PowerUpRes):
	sprite_2d.texture = power_up_res.texture
	type = power_up_res.type
