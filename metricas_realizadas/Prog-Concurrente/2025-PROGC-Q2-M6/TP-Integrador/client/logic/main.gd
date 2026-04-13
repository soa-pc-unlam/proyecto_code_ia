extends Node

@onready var menu: Menu = $Menu
var game: Node2D
var board: Node2D
@onready var game_over_layer: CanvasLayer = $GameOverLayer
@onready var result_label: Label = $GameOverLayer/GameOverPanel/VBoxContainer/ResultLabel
@onready var back_to_menu_btn: Button = $GameOverLayer/GameOverPanel/VBoxContainer/BackToMenuBtn


func _ready():
	game_over_layer.visible = false
	menu.left_game_ack.connect(_on_back_to_menu)
	back_to_menu_btn.pressed.connect(_on_back_to_menu)
	Networking.joined_as_spectator.connect(_on_viewer_joined)
	Store.game_over.connect(_on_game_over)
	Store.new_game_started.connect(_on_game_started)

func _on_viewer_joined():
	Store.set_viewer()

func _on_game_started():
	if game:
		game.queue_free()
	game = preload("res://scenes/Game.tscn").instantiate()
	board = game.get_node("Board")
	add_child(game)
	menu.visible = false
	game.visible = true
	game_over_layer.visible = false

func _on_game_over(winner_color: Variant):
	var message := ""
	
	if Store.is_viewer:
		message = "Game ended."
	else:
		if winner_color == null or winner_color == "":
			message = "Draw!"
		elif winner_color == Store.my_color:
			message = "You Won!"
		else:
			message = "You Lost!"
	
	result_label.text = message
	
	_show_game_over_panel()

func _show_game_over_panel():
	game_over_layer.visible = true
	game_over_layer.show()
	
func _on_back_to_menu():
	game_over_layer.hide()

	if game:
		game.queue_free()
		game = null
		board = null

	menu.visible = true
	menu.reset_to_lobby()
	
	Store.clear()
