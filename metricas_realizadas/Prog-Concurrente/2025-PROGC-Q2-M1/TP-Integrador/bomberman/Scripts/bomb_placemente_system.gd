extends Node

class_name BombPlacementSystem

const BOMB_SCENE = preload("res://Scenes/bomb.tscn")
const MACRO_TILE_SIZE = 16
const MACRO_BOMB_PLACED = 0
const MACRO_EXPLOSION_SIZE = 1

@onready var audio_bomb: AudioStreamPlayer2D = $"../AudioBomb"
# CORRECCIÓN 1: Cambiamos el tipo específico 'WhiteBomberman' por el genérico 'Bomberman'
# También renombramos la variable para que tenga sentido con cualquier personaje.
var bomberman : Bomberman = null

var bomb_placed = MACRO_BOMB_PLACED
var explosion_size = MACRO_EXPLOSION_SIZE

func _ready() -> void:
	# Al hacer get_parent(), ahora guardamos al padre sea quien sea (White o Black)
	bomberman = get_parent()

func place_bomb():
	# CORRECCIÓN 2: Usamos la variable genérica 'bomberman'
	if bomb_placed == bomberman.max_bombs:
		return
	
	audio_bomb.play()
	
	var bomb = BOMB_SCENE.instantiate()
	
	# CORRECCIÓN 3: Obtenemos la posición del bomberman genérico
	var bomberman_position = bomberman.position
	
	var bomb_position = Vector2(round(bomberman_position.x / MACRO_TILE_SIZE) * MACRO_TILE_SIZE, \
								round(bomberman_position.y / MACRO_TILE_SIZE) * MACRO_TILE_SIZE)
	
	bomb.explosion_size = explosion_size
	bomb.position = bomb_position
	get_tree().root.add_child(bomb)
	bomb_placed += 1
	
	bomb.tree_exiting.connect(on_bomb_exploded)
	
func on_bomb_exploded():
	bomb_placed -= 1
