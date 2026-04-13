extends PanelContainer
class_name GameRow

signal join_pressed(game_id)
signal viewer_pressed(game_id)

@export var game_id: String = ""
@export var title_text: String = ""
@export var players: int = 0
@export var spectators: int = 0
@export var can_join: bool = true


@onready var name_label: Label = $HBoxContainer/NameLabel
@onready var player_count_label: Label = $HBoxContainer/PlayerCountLabel
@onready var observers_label: Label = $HBoxContainer/ObserversLabel
@onready var join_button: Button = $HBoxContainer/JoinButton
@onready var viewer_button: Button = $HBoxContainer/JoinAsViewer

func _ready() -> void:
	_update_ui()
	join_button.pressed.connect(_on_join_pressed)
	viewer_button.pressed.connect(_on_viewer_pressed)

func setup(data: Dictionary) -> void:
	_setup_deferred.call_deferred(data)

func _setup_deferred(data: Dictionary):
	game_id = str(data.get("id", ""))
	
	title_text = "Game " + (game_id.substr(0, 8) if game_id.length() >= 8 else game_id)
	players = data.get("players", 0)
	spectators = data.get("spectators", 0)
	can_join = players < 2
	_update_ui()

func _update_ui():
	name_label.text = title_text
	player_count_label.text = "Players: " + str(players)
	observers_label.text = "Spectators: " + str(spectators)
	# Bloquear el botón si ya hay 2 jugadores
	join_button.disabled = not can_join
	#bloquear si hay menos de dos jugadores
	viewer_button.disabled = can_join

func _on_join_pressed():
	emit_signal("join_pressed", game_id)

func _on_viewer_pressed():
	emit_signal("viewer_pressed", game_id)
