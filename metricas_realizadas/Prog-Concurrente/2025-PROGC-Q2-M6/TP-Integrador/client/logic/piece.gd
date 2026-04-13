extends TextureRect
class_name Piece

@export var piece_name: String = ""
@export var tile_position: String = ""

var dragging := false
var drag_offset := Vector2.ZERO
var original_position := Vector2.ZERO
var original_square := ""
var board: Board

func _ready():
	mouse_filter = MOUSE_FILTER_PASS
	board = get_tree().get_first_node_in_group("board_root")
	
func _gui_input(event: InputEvent) -> void:
	if event is InputEventMouseButton and event.pressed and event.button_index == MOUSE_BUTTON_LEFT:
		_on_drag_start(event as InputEventMouseButton)
	elif event is InputEventMouseButton and not event.pressed and event.button_index == MOUSE_BUTTON_LEFT and dragging:
		_on_drag_end(event as InputEventMouseButton)
	elif event is InputEventMouseMotion and dragging:
		_on_drag_motion(event as InputEventMouseMotion)
		
func _on_drag_start(event: InputEventMouseButton) -> void:
	if not Store.is_my_turn():
		return
	
	if Store.my_color != "" and not piece_name.begins_with(Store.my_color):
		return
	
	dragging = true
	original_position = global_position
	original_square = board.get_square_from_pos(global_position)
	drag_offset = global_position - event.global_position
	move_to_front()
	
	Store.highlight_from(original_square)

func _on_drag_motion(event: InputEventMouseMotion):
	if dragging:
		global_position = event.global_position + drag_offset

func _on_drag_end(event: InputEventMouseButton) -> void:
	dragging = false
	
	Store.clear_highlights()
	
	var dropped_square := board.get_square_from_pos(event.global_position)
	
	if dropped_square == "":
		global_position = original_position
		return
	
	if Store.try_move(original_square, dropped_square):
		global_position = board.get_tile_position(dropped_square)
		Networking.send_make_move(original_square, dropped_square)
	else:
		global_position = original_position
