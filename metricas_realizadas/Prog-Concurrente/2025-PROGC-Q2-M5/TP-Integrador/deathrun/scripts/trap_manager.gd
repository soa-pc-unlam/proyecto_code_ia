extends Node

# ============================================
# CÓDIGO ORIGINAL (Sin cambios)
# ============================================

# Diccionario para trackear todas las trampas registradas
var registered_traps: Dictionary = {}

# Estadísticas del juego
var stats = {
	"total_traps": 0,
	"active_traps": 0,
	"total_activations": 0,
	"total_hits": 0
}

# Señales para comunicación con otros sistemas
signal trap_registered(trap_id, trap_node)
signal all_traps_registered()
signal trap_state_changed(trap_id, state_name)
signal player_hit_trap(trap_id)

func _ready():
	# === Conectar señal del NetworkManager ===
	# Cuando el NetworkManager valide un mensaje RPC, reaccionamos aquí
	if NetworkManager.has_signal("rpc_message_validated"):
		NetworkManager.rpc_message_validated.connect(_on_network_rpc_validated)
		print("[TrapManager] Conectado a señal rpc_message_validated del NetworkManager")
	
	# === CÓDIGO ORIGINAL ===
	await get_tree().process_frame
	register_all_traps()

func register_all_traps():
	"""Encuentra y registra todas las trampas del nivel"""
	var traps = get_tree().get_nodes_in_group("traps")
	
	for trap in traps:
		register_trap(trap)
	
	stats.total_traps = registered_traps.size()
	all_traps_registered.emit()
	
func register_trap(trap_node):
	"""Registra una trampa y conecta sus señales"""
	if not trap_node.has_method("get_state_name"):
		return  # No es una trampa válida
	
	var trap_id = trap_node.trap_id
	print("[TrapManager] ✓ Trampa con ID: ", trap_id)

	# Guardar referencia
	registered_traps[trap_id] = trap_node
	
	# Conectar señales de la trampa
	if not trap_node.trap_activated.is_connected(_on_trap_activated):
		trap_node.trap_activated.connect(_on_trap_activated)
	
	if not trap_node.trap_deactivated.is_connected(_on_trap_deactivated):
		trap_node.trap_deactivated.connect(_on_trap_deactivated)
	
	if not trap_node.player_hit.is_connected(_on_player_hit):
		trap_node.player_hit.connect(_on_player_hit)
	
	trap_registered.emit(trap_id, trap_node)

func _on_trap_activated(trap_id):
	"""Callback cuando una trampa se activa"""
	stats.active_traps += 1
	stats.total_activations += 1
	
	var trap = get_trap(trap_id)
	if trap:
		var state = trap.get_state_name()
		trap_state_changed.emit(trap_id, state)
		print("[TrapManager] Trampa %d ACTIVADA (Total activas: %d)" % [trap_id, stats.active_traps])

func _on_trap_deactivated(trap_id):
	"""Callback cuando una trampa se desactiva"""
	stats.active_traps = max(0, stats.active_traps - 1)
	
	var trap = get_trap(trap_id)
	if trap:
		var state = trap.get_state_name()
		trap_state_changed.emit(trap_id, state)

func _on_player_hit(trap_id):
	"""Callback cuando una trampa golpea al jugador"""
	stats.total_hits += 1
	player_hit_trap.emit(trap_id)
	print("[TrapManager] ¡Jugador golpeado por trampa %d! (Total hits: %d)" % [trap_id, stats.total_hits])
	
func get_trap_by_id(trap_id: int) -> Node2D:
	if registered_traps.has(trap_id):
		return registered_traps[trap_id]
	return null

# ============================================
# RPC MODIFICADO PARA USAR SISTEMA DE HILOS
# ============================================

@rpc("any_peer", "call_remote", "reliable")
func remote_activate_trap(trap_id: int):
	"""
	Recibe petición RPC de activación de trampa.
	"""
	# Solo el servidor procesa activaciones de trampas
	if not multiplayer.is_server():
		return
	
	var sender_id = multiplayer.get_remote_sender_id()
	
	print("[TrapManager] RPC recibido: activate_trap(%d) de jugador %d" % [trap_id, sender_id])
	
	# Encolar mensaje en el NetworkManager ===
	# En lugar de procesar directamente, enviamos al hilo worker
	NetworkManager.enqueue_rpc_message(
		"activate_trap",  # Tipo de mensaje
		sender_id,        # Quién lo envió
		{                 # Datos adicionales
			"trap_id": trap_id
		}
	)
	
	print("[TrapManager] Mensaje encolado para procesamiento en hilo worker")

# ============================================
# CALLBACK DESDE EL HILO WORKER
# ============================================

func _on_network_rpc_validated(message: Dictionary):
	"""
	Callback ejecutado en el HILO PRINCIPAL cuando el hilo worker
	terminó de validar un mensaje RPC.
	
	Aquí aplicamos los cambios al juego (activar trampas, etc.)
	"""
	# Solo procesar mensajes de tipo "activate_trap"
	if message.type != "activate_trap":
		return
	
	# El mensaje ya fue validado por el worker, así que es seguro aplicarlo
	var trap_id = message.data.trap_id
	
	print("[TrapManager] Aplicando activación de trampa %d (validada por worker)" % trap_id)
	
	# Activar la trampa
	if activate_trap(trap_id):
		print("[TrapManager] ✓ Trampa %d activada exitosamente" % trap_id)
	else:
		print("[TrapManager] ⚠️ No se pudo activar la trampa %d" % trap_id)

# ============================================
# API PÚBLICA
# ============================================

func activate_trap(trap_id: int) -> bool:
	"""
	Activa manualmente una trampa específica
	Esta función ahora solo se llama DESPUÉS de la validación del worker
	"""
	var trap = get_trap(trap_id)
	
	if trap and trap.has_method("force_activate"):
		trap.force_activate()
		return true
	
	return false

func get_trap(trap_id: int):
	"""Obtiene la referencia a una trampa por su ID"""
	return registered_traps.get(trap_id, null)

func get_all_trap_ids() -> Array:
	"""Retorna array con todos los IDs de trampas registradas"""
	return registered_traps.keys()

func get_trap_state(trap_id: int) -> String:
	"""Obtiene el estado actual de una trampa"""
	var trap = get_trap(trap_id)
	if trap and trap.has_method("get_state_name"):
		return trap.get_state_name()
	return "Desconocido"

func get_available_traps() -> Array:
	"""Retorna array de IDs de trampas que están inactivas (pueden activarse)"""
	var available = []
	
	for trap_id in registered_traps:
		var trap = registered_traps[trap_id]
		if trap.current_state == 0:  # TrapState.INACTIVE = 0
			available.append(trap_id)
	
	return available

func get_stats() -> Dictionary:
	"""Retorna las estadísticas actuales"""
	return stats.duplicate()
