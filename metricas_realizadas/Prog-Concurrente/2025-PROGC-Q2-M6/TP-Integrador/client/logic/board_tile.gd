extends ColorRect
class_name BoardTile

@export var tile_name: String = ""
@export var is_dark: bool
var highlight: ColorRect
var move_indicator: Control

func _draw() -> void:
	draw_string(ThemeDB.fallback_font, Vector2(4, size.y - 4), tile_name,HORIZONTAL_ALIGNMENT_LEFT, -1, 8, Color.WHITE if is_dark else Color.BLACK)

func _ready():
	name = tile_name
	color = Config.TileColors.DARK if is_dark else Config.TileColors.LIGHT
	highlight = ColorRect.new()
	highlight.color = Config.TileColors.HIGHLIGHT
	highlight.visible = false
	highlight.size = size
	add_child(highlight)
	
	move_indicator = MoveIndicator.new()
	move_indicator.size = size
	move_indicator.visible = false
	add_child(move_indicator)
	
	Store.last_move_changed.connect(_on_last_move_changed)
	Store.highlight_moves.connect(_on_moves_changed)

func _on_moves_changed(_from_square: String, available_squares: Array):
	move_indicator.visible = tile_name.to_upper() in available_squares

func _on_last_move_changed(last_move: Dictionary):
	if last_move.has("from") and last_move.has("to"):
		if tile_name == last_move["from"] or tile_name == last_move["to"]:
			highlight.visible = true
		else:
			highlight.visible = false
	else:
		highlight.visible = false
