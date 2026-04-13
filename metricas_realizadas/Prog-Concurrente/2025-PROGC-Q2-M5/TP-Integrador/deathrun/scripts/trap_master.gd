extends Node2D

# El Trap Master controla las trampas remotamente
# Demuestra: COMUNICACIÓN entre procesos (Trap Master -> TrapManager -> Trampas)

# Referencias
var trap_manager: Node = null
var available_traps: Array = []
var selected_trap_index: int = 0
@onready var camera = $Camera2D
var following_runner: bool = true

# UI
var label: Label = null

# Señales
signal trap_activated_by_master(trap_id)

func _ready():
	print("=== TrapMaster _ready() iniciado ===")
	print("  -> NetworkManager.is_multiplayer_active(): ", NetworkManager.is_multiplayer_active())
	print("  -> multiplayer.is_server(): ", multiplayer.is_server())
	# Buscar el TrapManager en el nivel
	trap_manager = get_node_or_null("/root/Level01/TrapManager")
	print("  -> TrapManager encontrado: ", trap_manager != null)
	
	if not trap_manager:
		print("ERROR: No se encontró el TrapManager")
		return
	
	# Esperar a que todas las trampas estén registradas
	if trap_manager.has_signal("all_traps_registered"):
		trap_manager.all_traps_registered.connect(_on_all_traps_registered)
		print("  -> Señal all_traps_registered conectada")
	
	# Crear UI
	create_ui()
	
	print("Trap Master iniciado")
	print("Controles: Q/E para cambiar trampa, ESPACIO para activar")
	print("=== TrapMaster _ready() completado ===")


func _on_all_traps_registered():
	"""Callback cuando todas las trampas están registradas"""
	update_available_traps()
	print("Trap Master: %d trampas disponibles" % available_traps.size())

func create_ui():
	"""Crea la interfaz visual para el Trap Master"""
	# Crear CanvasLayer para que la UI siempre se vea en pantalla
	var canvas_layer = CanvasLayer.new()
	add_child(canvas_layer)
	
	# Crear panel de fondo semi-transparente
	var panel = Panel.new()
	panel.position = Vector2(10, 10)
	panel.size = Vector2(300, 240)
	panel.modulate = Color(0, 0, 0, 0.7)  # Negro semi-transparente
	canvas_layer.add_child(panel)
	
	# Crear label encima del panel
	label = Label.new()
	label.position = Vector2(20, 20)
	label.add_theme_font_size_override("font_size", 12)
	label.add_theme_color_override("font_color", Color.WHITE)
	canvas_layer.add_child(label)
	
	update_ui()
func focus_on_selected_trap() -> void:
	if camera == null:
		return
	if available_traps.is_empty():
		return

	var trap_id = available_traps[selected_trap_index]
	var trap = trap_manager.get_trap_by_id(trap_id)
	if trap:
		# Teletransporte directo
		camera.global_position = trap.global_position
		
func _follow_runner(delta):
	var runner = get_node_or_null("/root/Level01/player")
	if not runner or not camera:
		return
	var target = runner.global_position
	camera.global_position = camera.global_position.lerp(target, delta * 3.0)

func update_ui():
	"""Actualiza el texto de la UI"""
	if not label or not trap_manager:
		return
	
	var text = "=== TRAP MASTER ===\n"
	text += "Controles: Q/E = Cambiar | ESPACIO = Activar\n\n"
	
	if available_traps.size() > 0:
		var current_trap_id = available_traps[selected_trap_index]
		var state = trap_manager.get_trap_state(current_trap_id)
		
		text += "Trampa seleccionada: %d\n" % current_trap_id
		text += "Estado: %s\n" % state
		text += "(%d/%d)\n" % [selected_trap_index + 1, available_traps.size()]
	else:
		text += "No hay trampas disponibles\n"
	
	var stats = trap_manager.get_stats()
	text += "\n--- ESTADÍSTICAS ---\n"
	text += "Activas: %d/%d\n" % [stats.active_traps, stats.total_traps]
	text += "Activaciones: %d\n" % stats.total_activations
	text += "Hits: %d" % stats.total_hits
	
	label.text = text

func _process(_delta):
	update_available_traps()
	update_ui()
	if following_runner:
		_follow_runner(_delta)
	else:
		focus_on_selected_trap()

func _input(event):
	if not trap_manager:
		return
	
	# Cambiar trampa seleccionada con Q/E
	if event.is_action_pressed("ui_focus_prev"):  # Q por defecto
		print("TrapMaster: Tecla Q presionada")
		change_selected_trap(-1)
	
	if event.is_action_pressed("ui_focus_next"):  # E por defecto  
		print("TrapMaster: Tecla E presionada")
		change_selected_trap(1)
	
	# Activar trampa con ESPACIO
	if event.is_action_pressed("ui_select"):  # ESPACIO
		print("TrapMaster: ESPACIO presionado")
		activate_selected_trap()

func change_selected_trap(direction: int):
	"""Cambia la trampa seleccionada"""
	if available_traps.size() == 0:
		return
	
	selected_trap_index += direction
	following_runner = false
	# Wrap around
	if selected_trap_index < 0:
		selected_trap_index = available_traps.size() - 1
	elif selected_trap_index >= available_traps.size():
		selected_trap_index = 0
	focus_on_selected_trap()
	print("Trap Master: Trampa %d seleccionada" % available_traps[selected_trap_index])

func activate_selected_trap():
	"""Intenta activar la trampa seleccionada"""
	if available_traps.size() == 0:
		print("Trap Master: No hay trampas disponibles")
		return
	
	var trap_id = available_traps[selected_trap_index]
	print("Trap Master: Intentando activar trampa %d" % trap_id)
	
	# Si hay conexión de red activa, enviar comando por RPC al TrapManager
	if NetworkManager.is_multiplayer_active():
		print("  -> Modo multiplayer activo")
		print("  -> Soy servidor: ", multiplayer.is_server())
		print("  -> ID del peer: ", multiplayer.get_unique_id())
		
		# Obtener referencia al TrapManager en el servidor
		var trap_manager_path = "/root/Level01/TrapManager"
		var server_trap_manager = get_node_or_null(trap_manager_path)
		
		if server_trap_manager:
			# Enviar RPC al TrapManager del servidor
			print("  -> Enviando RPC al servidor (peer ID: 1)")
			server_trap_manager.rpc_id(1, "remote_activate_trap", trap_id)
			print("  -> RPC enviado")
		else:
			print("Trap Master: Error - No se encontró TrapManager en: ", trap_manager_path)
	else:
		# Modo local
		if trap_manager.activate_trap(trap_id):
			trap_activated_by_master.emit(trap_id)
			print("Trap Master: Activando trampa %d" % trap_id)
		else:
			print("Trap Master: No se pudo activar trampa %d" % trap_id)

func update_available_traps():
	"""Actualiza la lista de trampas disponibles"""
	if not trap_manager:
		return
	
	var new_available = trap_manager.get_all_trap_ids()
	
	# Si cambió la lista, resetear el índice
	if new_available != available_traps:
		available_traps = new_available
		selected_trap_index = 0 if available_traps.size() > 0 else 0
