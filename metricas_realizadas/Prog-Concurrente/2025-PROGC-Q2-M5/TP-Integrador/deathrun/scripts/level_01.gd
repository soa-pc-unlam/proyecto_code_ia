extends Node2D

@onready var player = $player
@onready var trap_master = $TrapMaster

func _ready():
	setup_multiplayer()
	GameManager.start_level_stats()


func setup_multiplayer():
	"""Configura el nivel según el modo de juego"""
	
	
	# Modo multijugador online
	if multiplayer.is_server():
		# HOST: Controla al jugador (Runner)
		print("Nivel: Configurado como HOST (Runner)")
		
		# Desactivar el TrapMaster local si existe
		if trap_master:
			trap_master.queue_free()
		
		# El player funciona normalmente
		
	else:
		# CLIENT: Trap Master
		if player:
			player.set_physics_process(false)
			
			# Desactivar cámara del jugador
			var player_camera = player.get_node_or_null("Camera2D")
			if player_camera:
				player_camera.enabled = false
		
		# Crear cámara móvil para Trap Master
		var camera = preload("res://scripts/trap_master_camera.gd").new()
		camera.position = Vector2(-850, 400)  # Centro inicial (ajustar)
		add_child(camera)
