extends Control
class_name Menu

signal left_game_ack

@onready var connect_panel: VBoxContainer = $ConnectPanel
@onready var server_url: LineEdit = $ConnectPanel/ServerURL
@onready var player_name: LineEdit = $ConnectPanel/PlayerName
@onready var connect_button: Button = $ConnectPanel/ConnectButton

@onready var lobby_panel: VBoxContainer = $LobbyPanel
@onready var create_button: Button = $LobbyPanel/CreateGameBtn
@onready var join_button: Button = $LobbyPanel/JoinGameBtn
@onready var cancel_button: Button = $LobbyPanel/CancelBtn
@onready var exit_system_button: Button = $LobbyPanel/ExitBtn
@onready var exit_screen: CanvasLayer = $"../ExitLayer"

@onready var status_label: Label = $LobbyPanel/StatusLabel
@onready var game_list_panel := $GameListPanel

func _ready():
	server_url.text = Config.MenuDefaults.REMOTE_SERVER_URL if OS.has_feature("template") else Config.MenuDefaults.LOCAL_SERVER_URL
	player_name.text = "" if OS.has_feature("template") else "Pepe"
	lobby_panel.visible = false
	game_list_panel.visible = false
	cancel_button.visible = false

	
	connect_button.pressed.connect(_on_ConnectButton_pressed)
	create_button.pressed.connect(_on_CreateButton_pressed)
	exit_system_button.pressed.connect(_on_exit_pressed)
	cancel_button.pressed.connect(_on_leave_game)
	join_button.pressed.connect(_on_JoinButton_pressed)
	
	game_list_panel.join_game_requested.connect(_on_game_selected)
	game_list_panel.join_game_viewer.connect(_on_game_selected_viewer)
	game_list_panel.back_pressed.connect(_on_back_from_list)
	game_list_panel.refresh_pressed.connect(_on_refresh_list)
	
	Networking.connected.connect(_on_connected)
	Networking.connection_failed.connect(_on_connection_failed)
	Networking.game_created.connect(_on_game_created)
	Networking.joined_game.connect(_on_joined_game)
	Networking.error_received.connect(_on_error)
	Networking.games_list.connect(_on_games_list)
	Networking.left_game.connect(_on_left_game)


	
func reset_to_lobby():
	connect_panel.visible = false
	lobby_panel.visible = true
	game_list_panel.visible = false
	
	create_button.disabled = false
	join_button.disabled = false
	cancel_button.visible = false
	
	status_label.text = ""

func _on_ConnectButton_pressed() -> void:
	var url := server_url.text.trim_suffix(" ")
	var playerName := player_name.text.strip_edges()

	if url.is_empty() or playerName.is_empty():
		status_label.text = "Enter server URL + name."
		return

	status_label.text = "Connecting..."

	Networking.connect_ws(url)
	Store.clear()
	Store.my_color = ""
	Store.game_id = ""


func _on_connected(player_id):
	status_label.text = "Connected as %s" % player_id
	connect_panel.visible = false
	lobby_panel.visible = true


func _on_connection_failed(reason):
	status_label.text = "Connection failed: " + str(reason)


#
# --- STEP 2A: CREATE GAME ---
#

func _on_CreateButton_pressed():
	status_label.text = "Creating game..."
	Networking.send_create_game()


func _on_game_created(game_id):
	status_label.text = "Game created.\nWaiting for opponent...\nGame ID: %s" % game_id
	create_button.disabled = true
	join_button.disabled = true
	cancel_button.visible = true
	

#
# --- STEP 2B: JOIN GAME ---
#

func _on_JoinButton_pressed():
	lobby_panel.visible = false
	game_list_panel.visible = true
	Networking.send_list_games()



func _on_refresh_list():
	Networking.send_list_games()


func _on_games_list(payload):
	var games = payload.get("games", [])
	game_list_panel.populate(games)
#
# --- STEP 3: JOINED GAME ---
#

func _on_joined_game(game_id):
	connect_panel.visible = false
	lobby_panel.visible = false
	game_list_panel.visible = false

func _on_game_selected_viewer(game_id):
	game_list_panel.visible = false
	lobby_panel.visible = false
	status_label.text = "Joining game %s..." % game_id
	Networking.send_join_viewer_game(game_id)
	
func _on_game_selected(game_id):
	game_list_panel.visible = false
	lobby_panel.visible = false
	status_label.text = "Joining game %s..." % game_id
	Networking.send_join_game(game_id)
	Networking.unsub_list_games()

func _on_back_from_list():
	game_list_panel.visible = false
	lobby_panel.visible = true
	Networking.unsub_list_games()
# leave game request due cancel or opponent left
func _on_leave_game():
	Networking.send_leave_game()

# left game confirmation signal from server and send signal
func _on_left_game():
	emit_signal("left_game_ack")
	
func _on_exit_pressed():
	Networking.close()
	connect_panel.visible = false
	lobby_panel.visible = false
	exit_screen.visible = true
	exit_screen.show()
	
# --- ERRORS ---
#	
func _on_error(msg):
	status_label.text = "Error: " + str(msg)
	
	
