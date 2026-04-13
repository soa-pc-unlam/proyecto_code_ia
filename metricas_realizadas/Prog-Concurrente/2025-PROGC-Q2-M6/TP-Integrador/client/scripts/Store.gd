extends Node

signal board_changed(board)
signal game_over(winner_color)
signal last_move_changed(last_move)
signal new_game_started()
signal highlight_moves(from_square, available_squares)

var game_id: String
var board: Dictionary = {}
var allowed_moves: Array = []
var player_turn: String = ""
var game_is_over: bool = false
var winner_color: Variant = ""
var my_color: String = ""
var last_move: Dictionary = {}
var is_viewer: bool = false
var has_received_initial_state: bool = false

func _ready() -> void:
	Networking.game_state.connect(apply_state)

func clear():
	is_viewer = false
	has_received_initial_state = false
	game_id = ""
	board.clear()
	allowed_moves.clear()
	player_turn = ""
	game_is_over = false
	winner_color = ""
	my_color = ""


func apply_state(payload: Dictionary):
	var is_first_state: bool = not has_received_initial_state
	has_received_initial_state = true
	
	game_id = payload.gameId
	allowed_moves = payload.allowedMoves
	player_turn = payload.playerTurn
	game_is_over = payload.gameOver
	winner_color = payload.get("winner")
	last_move = payload.get("lastMove", {})
	board = _array_to_map(payload.boardState)
	
	var players_dict = payload.players
	var players_count = players_dict.size()
	var my_id: String = Networking.player_id
	if players_count >= 2 and not players_dict.has(my_id):
		is_viewer = true
		my_color = ""
	else:
		is_viewer = false
		my_color = players_dict[my_id].get("color", "")

	emit_signal("board_changed", board)	
	emit_signal("last_move_changed", last_move)
	
	if is_first_state:
		emit_signal("new_game_started")
	if game_is_over:
		print("Store: Game is over! Winner: ", winner_color)
		emit_signal("game_over", winner_color)


func _array_to_map(arr: Array) -> Dictionary:
	var map := {}
	var files: Array[Variant] = ["A","B","C","D","E","F","G","H"]
	var ranks: Array[Variant] = [1,2,3,4,5,6,7,8]
	var i: int = 0

	for r in ranks:
		for f in files:
			var sq: String = "%s%d" % [f,r]
			var val = arr[i]
			map[sq] = val if val != null else ""
			i += 1

	return map

func try_move(from: String, to: String) -> bool:
	if (is_viewer):
		return false
	if player_turn != Networking.player_id:
		print("Not your turn!")
		return false
	
	var from_up := from.to_upper()
	var to_up := to.to_upper()
	for m in allowed_moves:
		var mf := String(m.get("from", "")).to_upper()
		var mt := String(m.get("to", "")).to_upper()
		if mf == from_up and mt == to_up:
			return true
	return false

func is_my_turn() -> bool:
	return player_turn == Networking.player_id

func set_viewer():
	is_viewer = true
	
func highlight_from(square: String):
	var from_up := square.to_upper()
	var available := []
	
	for m in allowed_moves:
		var mf := String(m.get("from", "")).to_upper()
		if mf == from_up:
			var mt := String(m.get("to", "")).to_upper()
			available.append(mt)
	
	emit_signal("highlight_moves", from_up, available)

func clear_highlights():
	emit_signal("highlight_moves", "", [])
