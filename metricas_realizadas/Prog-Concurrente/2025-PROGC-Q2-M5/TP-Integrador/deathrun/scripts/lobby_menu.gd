extends Control

# Referencias UI
@onready var btn_host = $VBoxContainer/BtnHost
@onready var btn_join = $VBoxContainer/BtnJoin
@onready var btn_back = $VBoxContainer/BtnBack
@onready var input_ip = $VBoxContainer/InputIP
@onready var label_status = $LabelStatus

func _ready():
	# Conectar botones
	btn_host.pressed.connect(_on_host_pressed)
	btn_join.pressed.connect(_on_join_pressed)
	btn_back.pressed.connect(_on_back_pressed)
	
	# Conectar señales de NetworkManager
	NetworkManager.server_started.connect(_on_server_started)
	NetworkManager.connection_succeeded.connect(_on_connection_succeeded)
	NetworkManager.connection_failed.connect(_on_connection_failed)
	NetworkManager.player_connected.connect(_on_player_connected)
	
	# IP por defecto
	input_ip.text = "127.0.0.1"
	label_status.text = "Esperando..."

func _on_host_pressed():
	"""Crear servidor (Host)"""
	label_status.text = "Creando servidor..."
	
	if NetworkManager.create_server():
		label_status.text = "Servidor creado. Esperando jugador..."
		btn_host.disabled = true
		btn_join.disabled = true
	else:
		label_status.text = "Error al crear servidor"

func _on_join_pressed():
	"""Unirse a servidor"""
	var ip = input_ip.text
	label_status.text = "Conectando a " + ip + "..."
	
	if NetworkManager.join_server(ip):
		btn_host.disabled = true
		btn_join.disabled = true
	else:
		label_status.text = "Error al conectar"

func _on_back_pressed():
	"""Volver al menú principal"""
	NetworkManager.disconnect_from_game()
	get_tree().change_scene_to_file("res://scenes/MainMenu.tscn")

# === Callbacks de red ===
func _on_server_started():
	"""Servidor creado exitosamente"""
	label_status.text = "Servidor activo. Esperando jugador..."

func _on_connection_succeeded():
	"""Cliente conectado al servidor"""
	label_status.text = "Conectado! Iniciando juego..."
	#await get_tree().create_timer(1.0).timeout
	#start_game()

func _on_connection_failed():
	"""Falló la conexión"""
	label_status.text = "Error: No se pudo conectar"
	btn_host.disabled = false
	btn_join.disabled = false

func _on_player_connected():
	"""Otro jugador se conectó (solo host recibe esto)"""
	# Solo el HOST coordina el inicio
	if not multiplayer.is_server():
		return
	
	label_status.text = "Jugador conectado! Iniciando juego..."
	await get_tree().create_timer(1.0).timeout
	start_game_as_host()

func start_game_as_host():
	"""Solo el HOST ejecuta esto para iniciar el juego"""
	if not multiplayer.is_server():
		return
	
	# Verificar que seguimos en el árbol
	var tree = get_tree()
	if tree == null:
		push_error("[HOST] No se puede iniciar: nodo removido del árbol")
		return
	
	print("[HOST] Iniciando juego para todos los jugadores...")
	
	# Configurar modo de juego
	GameManager.singleplayer = false
	
	# Notificar al cliente que cambie de escena (RPC)
	rpc("client_start_game")
	
	# Cambiar nuestra propia escena
	tree.change_scene_to_file("res://scenes/levels/Level_01.tscn")
	
	
@rpc("authority", "call_remote", "reliable")
func client_start_game():
	"""RPC que el cliente recibe para iniciar el juego"""
	print("[CLIENT] Servidor inició el juego, cambiando escena...")
	
	# Verificar que seguimos en el árbol
	var tree = get_tree()
	if tree == null:
		push_error("[CLIENT] No se puede cambiar escena: nodo removido")
		return
	
	# Configurar modo de juego
	GameManager.singleplayer = false
	
	# Cambiar escena
	tree.change_scene_to_file("res://scenes/levels/Level_01.tscn")
