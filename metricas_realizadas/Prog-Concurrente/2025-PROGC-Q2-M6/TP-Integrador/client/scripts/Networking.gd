extends Node

## Signals to notify UI, Store, Game, etc
signal connected(player_id)
signal connection_failed(reason)
signal disconnected()
signal game_created(game_id)
signal joined_game(game_id)
signal game_state(payload)
signal move_made(from, to)
signal error_received(message)
signal games_list(payload)
signal left_game()
signal opponent_left()
signal joined_as_spectator()
signal player_left()
signal exit_game

var peer: WebSocketPeer
var is_connecting := false
var is_open := false
var player_id := ""

const HEARTBEAT_INTERVAL := 25

func connect_ws(url: String) -> void:
	var normalized_url := normalize_ws_url(url)
	peer = WebSocketPeer.new()
	peer.heartbeat_interval = HEARTBEAT_INTERVAL

	var err: int = peer.connect_to_url(normalized_url)
	if err != OK:
		emit_signal("connection_failed", "connect_to_url failed: %s" % err)
		return

	is_connecting = true
	is_open = false


func _process(delta: float) -> void:
	if peer == null:
		return
	peer.poll()

	match peer.get_ready_state():

		WebSocketPeer.STATE_CONNECTING:
			# Waiting for connection handshake
			pass

		WebSocketPeer.STATE_OPEN:
			if not is_open:
				# First time we enter OPEN state
				is_open = true
				is_connecting = false
				# NOTE: the server sends "connected" as a *message*, NOT immediately on handshake

			# Read all packets
			while peer.get_available_packet_count() > 0:
				var raw := peer.get_packet().get_string_from_utf8()
				_on_message(raw)

		WebSocketPeer.STATE_CLOSING:
			# Normal closing handshake
			pass

		WebSocketPeer.STATE_CLOSED:
			if is_open:
				is_open = false
				emit_signal("disconnected")
			if peer.get_close_code() != -1:
				print("Closed WS: %s" % peer.get_close_reason())
			peer = null


func _on_message(text: String) -> void:
	var msg = JSON.parse_string(text)
	if msg == null:
		emit_signal("error_received", "Invalid JSON from server")
		return

	var type = msg.get("type", "")
	var payload = msg.get("payload", {})

	match type:

		"connected":
			player_id = payload.playerId
			emit_signal("connected", player_id)

		"error":
			emit_signal("error_received", payload)

		"game_created":
			emit_signal("game_created", payload.gameId)

		"joined_game":
			emit_signal("joined_game", payload.gameId)

		"move_made":
			emit_signal("move_made", payload.from, payload.to)

		"game_state":
			emit_signal("game_state", payload)

		"left_game":
			emit_signal("left_game")
			
		"opponent_left":
			emit_signal("opponent_left")
		"player_left":
			emit_signal("player_left")
		"games_list":
			emit_signal("games_list", payload)
		"joined_as_spectator":
			emit_signal("joined_as_spectator")
		_:
			print("Unknown WS type: ", text)


#
# ---- Sending ----
#

func close():
	send_exit_system()
	await get_tree().create_timer(0.05).timeout
	
	

func send_create_game():
	_send({"type": "create_game"})

func send_exit_system():
	_send({"type": "exit_game"})

func send_join_game(game_id: String):
	_send({
		"type": "join_game",
		"payload": { "gameId": game_id }
	})
	
func send_join_viewer_game(game_id):
	_send({
		"type": "join_viewer_game",
		"payload": { "gameId": game_id }
	})
	
func send_make_move(from_square: String, to_square: String):
	_send({
		"type": "make_move",
		"payload": {
			"from": from_square,
			"to": to_square
		}
	})

func send_leave_game():
	_send({"type": "leave_game"})

func send_list_games():
	_send({
		"type": "subscribe_list_games"
	})
func unsub_list_games():
	_send({
		"type": "unsubscribe_list_games"
	})
func _send(dict: Dictionary):
	if peer != null and peer.get_ready_state() == WebSocketPeer.STATE_OPEN:
		peer.send_text(JSON.stringify(dict))

func normalize_ws_url(raw: String) -> String:
	var url := raw.strip_edges()
	
	# Must always point to /ws
	if not url.ends_with("/ws") and not url.ends_with("/ws/"):
		if url.ends_with("/"):
			url += "ws"
		else:
			url += "/ws"

	# Already provided protocol
	if url.begins_with("ws://") or url.begins_with("wss://"):
		return url

	# Localhost → assume ws
	if url.begins_with("localhost") or url.begins_with("127."):
		return "ws://" + url

	# Everything else → assume wss
	return "wss://" + url
