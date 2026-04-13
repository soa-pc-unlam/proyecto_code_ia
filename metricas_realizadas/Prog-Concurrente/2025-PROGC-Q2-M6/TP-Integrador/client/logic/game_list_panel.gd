extends Panel
class_name GameListPanel

signal back_pressed()
signal refresh_pressed()
signal join_game_requested(game_id)
signal join_game_viewer(game_id)

var game_row_scene := preload("res://scenes/GameRow.tscn")
@onready var rows_container: VBoxContainer = $MarginContainer/VBoxContainer/ScrollContainer/RowsContainer
@onready var back_btn: Button = $MarginContainer/VBoxContainer/HBoxContainer/Back
@onready var refresh_btn: Button = $MarginContainer/VBoxContainer/HBoxContainer/Refresh

func _ready():
	back_btn.pressed.connect(_on_back)
	refresh_btn.pressed.connect(_on_refresh)

	
func populate(games) -> void:
	for c in rows_container.get_children():
		c.free()  
	if games.is_empty():
		var lbl := Label.new()
		lbl.text = "No games available"
		rows_container.add_child(lbl)
		return
	for game in games:
		var row: GameRow = game_row_scene.instantiate()
		row.setup({
			"id": game.get("id", ""),
			"players": int(game.get("players", 0)),
			"spectators": int(game.get("spectators", 0))
		})
		row.join_pressed.connect(_on_row_join)
		row.viewer_pressed.connect(_on_row_viewer_join)
		rows_container.add_child(row)

func _on_row_join(game_id):
	emit_signal("join_game_requested", game_id)
	
func _on_row_viewer_join(game_id):
	emit_signal("join_game_viewer", game_id)
	
func _on_back():
	emit_signal("back_pressed")

func _on_refresh():
	emit_signal("refresh_pressed")
