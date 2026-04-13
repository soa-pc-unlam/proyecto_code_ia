extends Node2D
class_name Board

@onready var tile_container: Node2D = $Tiles
@onready var piece_container: Node2D = $Pieces
@onready var exit_button: Button = $ExitBtn
@onready var exit_dialog: ConfirmationDialog = $ExitConfirmDialog
@onready var opponent_left_dialog: AcceptDialog = $OpponentLeftDialog
@onready var player_left_dialog: AcceptDialog = $PlayerLeftDialog

const TILE_SIZE := Config.TILE_SIZE
const TILE_SCENE := preload("res://scenes/BoardTile.tscn")
const PIECE_SCENE := preload("res://scenes/Piece.tscn")
const FILES := ["A","B","C","D","E","F","G","H"]
const RANKS := [8,7,6,5,4,3,2,1]
var tiles: Dictionary[String, BoardTile] = {}

var PIECE_TEXTURES := {
	"WHITE_PAWN": preload("res://sprites/pieces/white_pawn.svg"),
	"WHITE_KNIGHT": preload("res://sprites/pieces/white_knight.svg"),
	"WHITE_BISHOP": preload("res://sprites/pieces/white_bishop.svg"),
	"WHITE_ROOK": preload("res://sprites/pieces/white_rook.svg"),
	"WHITE_QUEEN": preload("res://sprites/pieces/white_queen.svg"),
	"WHITE_KING": preload("res://sprites/pieces/white_king.svg"),

	"BLACK_PAWN": preload("res://sprites/pieces/black_pawn.svg"),
	"BLACK_KNIGHT": preload("res://sprites/pieces/black_knight.svg"),
	"BLACK_BISHOP": preload("res://sprites/pieces/black_bishop.svg"),
	"BLACK_ROOK": preload("res://sprites/pieces/black_rook.svg"),
	"BLACK_QUEEN": preload("res://sprites/pieces/black_queen.svg"),
	"BLACK_KING": preload("res://sprites/pieces/black_king.svg"),
}

func _ready():
	add_to_group("board_root")
	_generate_board()
	_redraw_pieces(Store.board)
	_connect_signals()

func _connect_signals():
	Store.board_changed.connect(_on_board_changed)
	exit_button.pressed.connect(_on_exit_button_pressed)
	Networking.opponent_left.connect(_on_opponent_left)
	Networking.player_left.connect(_on_player_left)

func _generate_board():
	var reversed := Store.my_color == Config.PlayerColor.BLACK
	for rank in range(8):
		for file in range(8):

			var tile := TILE_SCENE.instantiate()
			tile.size = Vector2(TILE_SIZE, TILE_SIZE)

			var file_idx := (7 - file) if reversed else file
			var rank_idx := (7 - rank) if reversed else rank

			tile.is_dark = ((rank_idx + file_idx) % 2 == 1)

			var tileName := "%s%d" % [FILES[file_idx], RANKS[rank_idx]]
			tile.tile_name = tileName

			tile.position = Vector2(file * TILE_SIZE, rank * TILE_SIZE)

			tile_container.add_child(tile)
			tiles[tileName] = tile

func _on_board_changed(new_board: Dictionary):
	_redraw_pieces(new_board)

func _redraw_pieces(board: Dictionary):
	for c in piece_container.get_children():
		c.queue_free()

	for square in board.keys():
		var piece_name = board[square]
		if piece_name == "" or piece_name == null or piece_name == 'NONE':
			continue

		var sprite: TextureRect = PIECE_SCENE.instantiate()
		sprite.piece_name = piece_name
		sprite.texture = _get_piece_texture(piece_name)
		sprite.position = tiles[square].position
		sprite.tile_position = square
		
		sprite.set_stretch_mode(TextureRect.STRETCH_KEEP_CENTERED)
		sprite.set_size(Vector2(TILE_SIZE, TILE_SIZE))

		piece_container.add_child(sprite)

func _get_piece_texture(pieceName: String) -> Texture2D:
	return PIECE_TEXTURES.get(pieceName, null)

func get_square_from_pos(event_global_position: Vector2) -> String:
	for tile_key in tiles:
		var tile := tiles[tile_key]
		if tile.get_global_rect().has_point(event_global_position):
			return tile.tile_name
	return ""
	
func get_tile_position(square: String) -> Vector2:
	if tiles.has(square):
		return tiles[square].global_position
	return Vector2.ZERO

func _on_exit_button_pressed():
	exit_dialog.popup_centered()


func _on_exit_confirm_dialog_confirmed() -> void:
	Networking.send_leave_game()

#opponent_left signal received
func _on_opponent_left():		
	opponent_left_dialog.popup_centered()

func _on_opponent_left_dialog_confirmed() -> void:
	Networking.send_leave_game()
	
func _on_player_left():
	player_left_dialog.popup_centered()

func _on_player_left_dialog_confirmed() -> void:
	Networking.send_leave_game()
