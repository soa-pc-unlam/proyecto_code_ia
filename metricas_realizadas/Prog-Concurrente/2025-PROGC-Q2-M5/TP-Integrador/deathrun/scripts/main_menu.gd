extends Control

# Referencias a los botones
@onready var btn_single_player = $VBoxContainer/BtnSinglePlayer
@onready var btn_multiplayer = $VBoxContainer/BtnMultiplayer
@onready var btn_quit = $VBoxContainer/BtnQuit
@onready var title_label = $Title

func _ready():
	# Conectar señales de los botones
	btn_single_player.pressed.connect(_on_single_player_pressed)
	btn_multiplayer.pressed.connect(_on_multiplayer_pressed)
	btn_quit.pressed.connect(_on_quit_pressed)
	
	print("Menú Principal cargado")

func _on_single_player_pressed():
	"""Inicia el juego en modo un jugador"""
	print("Modo: Un Jugador")
	
	# Guardar el modo de juego en una variable global/autoload
	GameManager.singleplayer = true
	
	# Verifica en el FileSystem la ruta exacta
	var level_path = "res://scenes/levels/Level_01.tscn"
	
	
	if ResourceLoader.exists(level_path):
		get_tree().change_scene_to_file(level_path)
	else:
		print("ERROR: No se encontró el nivel en: ", level_path)
		print("Verifica la ruta del archivo level_01.tscn")

func _on_multiplayer_pressed():
	"""Inicia el juego en modo multijugador"""
	print("Modo: Multijugador")
	
	# Ir al lobby de conexión para multijugador online
	get_tree().change_scene_to_file("res://scenes/LobbyMenu.tscn")

func _on_quit_pressed():
	"""Cierra el juego"""
	print("Saliendo del juego...")
	get_tree().quit()
