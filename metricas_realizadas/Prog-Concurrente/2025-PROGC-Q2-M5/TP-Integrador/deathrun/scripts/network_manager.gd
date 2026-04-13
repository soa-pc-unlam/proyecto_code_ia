extends Node

# ============================================
# CONFIGURACIÓN INICIAL
# ============================================
const DEFAULT_PORT = 7777
const MAX_PLAYERS = 2

var peer = null
var is_host = false
var player_role = ""  # "runner" o "trap_master"

# ============================================
# SISTEMA DE HILOS
# ============================================
# Thread worker para procesar RPC
var rpc_worker_thread: Thread = null
var thread_running: bool = false

# Cola de mensajes RPC (thread-safe)
var rpc_queue: Array = []
var queue_mutex: Mutex = null
var queue_semaphore: Semaphore = null

# Contador de mensajes procesados (para estadísticas)
var messages_processed: int = 0
var messages_queued: int = 0

# ============================================
# SEÑALES
# ============================================
signal player_connected(id)
signal player_disconnected(id)
signal connection_failed
signal connection_succeeded
signal server_started

# ============================================
# SEÑALES PARA EL HILO
# ============================================
signal rpc_message_validated(message: Dictionary)

func _ready():
	# === Inicializar sistema de sincronización de la queue de mensajes ===
	queue_mutex = Mutex.new()
	queue_semaphore = Semaphore.new()
	print("[NetworkManager] Sistema de sincronización inicializado")
	
	# === Conectar señales de multijugador ===
	multiplayer.peer_connected.connect(_on_player_connected)
	multiplayer.peer_disconnected.connect(_on_player_disconnected)
	multiplayer.connected_to_server.connect(_on_connected_to_server)
	multiplayer.connection_failed.connect(_on_connection_failed)
	multiplayer.server_disconnected.connect(_on_server_disconnected)

# ============================================
# CREAR SERVIDOR
# ============================================
func create_server(port = DEFAULT_PORT):
	disconnect_from_game()
	
	"""Crea un servidor (Host) e inicia el hilo worker"""
	peer = ENetMultiplayerPeer.new()
	var error = peer.create_server(port, MAX_PLAYERS)
	
	if error != OK:
		print("[ERROR] No se pudo crear servidor: ", error)
		return false
	
	multiplayer.multiplayer_peer = peer
	is_host = true
	player_role = "runner"  # El host es el Runner
	
	# === Iniciar hilo worker para validar RPC ===
	_start_worker_thread()
	
	print("[HOST] Servidor creado en puerto: ", port)
	print("[HOST] Hilo worker iniciado para procesar RPC")
	server_started.emit()
	return true

# ============================================
# CONECTAR COMO CLIENTE
# ============================================
func join_server(address = "127.0.0.1", port = DEFAULT_PORT):
	disconnect_from_game()
	
	"""Conecta a un servidor existente"""
	peer = ENetMultiplayerPeer.new()
	var error = peer.create_client(address, port)
	
	if error != OK:
		print("[ERROR] No se pudo conectar: ", error)
		connection_failed.emit()
		return false
	
	multiplayer.multiplayer_peer = peer
	is_host = false
	player_role = "trap_master"  # El cliente es el Trap Master
	
	print("[CLIENT] Conectando a: ", address, ":", port)
	return true

# ============================================
# SISTEMA DE HILOS - IMPLEMENTACIÓN
# ============================================

func _start_worker_thread():
	"""Inicia el hilo worker (solo en el Host)"""
	if thread_running:
		print("[WARNING] El hilo worker ya está en ejecución")
		return
	
	if not is_host:
		print("[INFO] Los clientes no necesitan hilo worker")
		return
	
	thread_running = true
	rpc_worker_thread = Thread.new()
	rpc_worker_thread.start(_worker_thread_function)
	
	print("[THREAD] ✓ Hilo worker iniciado")
	print("[THREAD] ID Hilo Principal: ", OS.get_main_thread_id())
	print("[THREAD] ID Hilo Worker: ", rpc_worker_thread.get_id())

func _worker_thread_function():
	"""
	ESTA FUNCIÓN CORRE EN EL HILO WORKER (no en el principal)
	Procesa mensajes RPC de forma asíncrona
	
	¡IMPORTANTE! 
	- NO puede acceder a nodos de Godot directamente
	- NO puede modificar la escena
	- Solo valida y prepara datos
	"""
	print("[WORKER] Hilo worker en ejecución")
	print("[WORKER] Thread ID: ", OS.get_thread_caller_id())
	
	while thread_running:
		# PASO 1: Esperar hasta que haya mensajes en la cola
		# Este wait() hace que el hilo "duerma" (0% CPU) hasta que
		# alguien haga semaphore.post()
		queue_semaphore.wait()
		
		# Si nos despertaron para terminar el hilo, salir
		if not thread_running:
			break
		
		# PASO 2: Obtener mensaje de la cola (THREAD-SAFE)
		queue_mutex.lock()
		var message = null
		if rpc_queue.size() > 0:
			message = rpc_queue.pop_front()
		queue_mutex.unlock()
		
		# PASO 3: Si había un mensaje, procesarlo
		if message != null:
			_process_rpc_in_worker(message)
	
	print("[WORKER] Hilo worker finalizado correctamente")

func _process_rpc_in_worker(message: Dictionary):
	"""
	Procesa y valida un mensaje RPC en el HILO WORKER
	
	Se puede:
	- Validaciones complejas
	- Cálculos pesados
	- Simulaciones
	- Comprobaciones de seguridad
	
	No se puede:
	- Acceder a nodos: TrapManager.activate_trap() ❌
	- Modificar la escena
	- Crear/destruir nodos
	"""
	var start_time = Time.get_ticks_msec()
	
	print("[WORKER] ⚙️ Procesando mensaje tipo: %s" % message.type)
	print("[WORKER] Remitente: Jugador %d (rol: %s)" % [
		message.sender_id,
		message.sender_role
	])
	
	# Procesar según tipo de mensaje
	match message.type:
		"activate_trap":
			_validate_trap_activation(message)
		
		"player_action":
			_validate_player_action(message)
		
		_:
			print("[WORKER] ⚠️ Tipo de mensaje desconocido: ", message.type)
			message["valid"] = false
			message["error"] = "Tipo de mensaje no reconocido"
	
	var elapsed = Time.get_ticks_msec() - start_time
	print("[WORKER] ✓ Mensaje procesado en %d ms" % elapsed)
	
	# CRÍTICO: Enviar resultado al hilo principal usando call_deferred
	# Esto programa la ejecución de _on_rpc_validated en el hilo principal
	call_deferred("_on_rpc_validated", message)

func _validate_trap_activation(message: Dictionary):
	"""
	Valida una petición de activación de trampa
	Corre en el HILO WORKER
	"""
	var trap_id = message.data.get("trap_id", -1)
	var sender_role = message.sender_role
	
	print("[WORKER] Validando activación de trampa %d..." % trap_id)
	
	# VALIDACIÓN 1: ID válido
	if trap_id < 0:
		print("[WORKER] ❌ Trap ID inválido: %d" % trap_id)
		message["valid"] = false
		message["error"] = "ID de trampa inválido"
		return
	
	# VALIDACIÓN 2: Permisos (solo Trap Master puede activar)
	if sender_role != "trap_master":
		print("[WORKER] ❌ Solo Trap Master puede activar (recibido: %s)" % sender_role)
		message["valid"] = false
		message["error"] = "Permisos insuficientes"
		return
	
	# VALIDACIÓN 3: Simular validación compleja
	# - Verificar cooldowns
	# - Consultar base de datos
	# - Validar estado del juego
	# - Anti-cheat checks
	OS.delay_msec(30)  # Simula trabajo pesado (30ms)
	
	# El mensaje es válido
	message["valid"] = true
	print("[WORKER] ✓ Trampa %d validada correctamente" % trap_id)

func _validate_player_action(message: Dictionary):
	"""
	Valida acciones del jugador
	Corre en el HILO WORKER
	"""
	var action = message.data.get("action", "")
	
	# Validaciones básicas
	if action == "":
		message["valid"] = false
		message["error"] = "Acción vacía"
		return
	
	# Simular validación
	OS.delay_msec(20)
	
	# El mensaje es válido
	message["valid"] = true
	print("[WORKER] ✓ Acción '%s' validada" % action)

func _on_rpc_validated(message: Dictionary):
	"""
	Callback que corre en el HILO PRINCIPAL después de que
	el worker terminó de procesar el mensaje.
	
	Se puede:
	- Acceder a nodos
	- Modificar la escena
	- Activar trampas
	- Actualizar UI
	"""
	print("[MAIN] 📨 Mensaje validado recibido: %s" % message.type)
	print("[MAIN] ¿Válido?: %s" % ("SÍ" if message.get("valid", false) else "NO"))
	
	messages_processed += 1
	
	# Si el mensaje no es válido, rechazar
	if not message.get("valid", false):
		print("[MAIN] ⚠️ Mensaje RECHAZADO: %s" % message.get("error", "Error desconocido"))
		return
	
	# Emitir señal con el mensaje validado
	rpc_message_validated.emit(message)
	
	print("[MAIN] ✓ Mensaje aplicado al juego")

# ============================================
# API PÚBLICA PARA ENCOLAR MENSAJES RPC
# ============================================

func enqueue_rpc_message(message_type: String, sender_id: int, data: Dictionary):
	"""
	Encola un mensaje RPC para ser procesado por el hilo worker
	
	Esta función es llamada desde otros scripts (ej: TrapManager)
	cuando reciben un RPC.
	"""
	if not is_host:
		print("[WARNING] Solo el host puede encolar mensajes RPC")
		return
	
	# Determinar rol del remitente
	var sender_role = "runner" if sender_id == 1 else "trap_master"
	
	# Crear mensaje estructurado
	var message = {
		"type": message_type,
		"sender_id": sender_id,
		"sender_role": sender_role,
		"data": data,
		"timestamp": Time.get_ticks_msec(),
		"valid": false  # Se validará en el worker
	}
	
	print("[MAIN] 📥 Encolando mensaje RPC: %s" % message_type)
	
	# Agregar a cola de forma THREAD-SAFE
	queue_mutex.lock()
	rpc_queue.append(message)
	var queue_size = rpc_queue.size()
	queue_mutex.unlock()
	
	messages_queued += 1
	
	print("[MAIN] Cola tiene %d mensajes pendientes" % queue_size)
	
	# Despertar al hilo worker
	queue_semaphore.post()

# ============================================
# CALLBACKS DE RED
# ============================================

func _on_player_connected(id):
	"""Cuando otro jugador se conecta"""
	print("[NET] Jugador conectado: ", id)
	player_connected.emit()

func _on_player_disconnected(id):
	"""Cuando otro jugador se desconecta"""
	print("[NET] Jugador desconectado: ", id)
	player_disconnected.emit(id)

func _on_connected_to_server():
	"""Cliente: conexión exitosa al servidor"""
	print("[CLIENT] ✓ Conectado al servidor!")
	connection_succeeded.emit()

func _on_connection_failed():
	"""Cliente: falló la conexión"""
	print("[CLIENT] ❌ Falló la conexión al servidor")
	connection_failed.emit()

func _on_server_disconnected():
	"""Cliente: el servidor se desconectó"""
	print("[CLIENT] ⚠️ Servidor desconectado")
	multiplayer.multiplayer_peer = null

# ============================================
# UTILIDADES
# ============================================

func is_multiplayer_active():
	"""Verifica si hay una sesión multijugador activa"""
	return GameManager.singleplayer == false

func get_player_id():
	"""Retorna el ID único del jugador"""
	return multiplayer.get_unique_id()

func is_server():
	"""Verifica si este peer es el servidor"""
	return multiplayer.is_server()

# ============================================
# ESTADÍSTICAS Y DEBUGGING
# ============================================

func get_queue_size() -> int:
	"""Retorna el tamaño actual de la cola de mensajes"""
	queue_mutex.lock()
	var size = rpc_queue.size()
	queue_mutex.unlock()
	return size

func is_worker_thread_active() -> bool:
	"""Verifica si el hilo worker está activo"""
	return thread_running and rpc_worker_thread != null and rpc_worker_thread.is_alive()

func get_thread_stats() -> Dictionary:
	"""Retorna estadísticas del sistema de hilos"""
	return {
		"thread_running": thread_running,
		"thread_alive": is_worker_thread_active(),
		"queue_size": get_queue_size(),
		"messages_queued": messages_queued,
		"messages_processed": messages_processed,
		"is_host": is_host,
		"player_role": player_role,
		"main_thread_id": OS.get_main_thread_id(),
		"worker_thread_id": rpc_worker_thread.get_id() if rpc_worker_thread else -1
	}

func print_thread_stats():
	"""Imprime estadísticas detalladas (útil para debugging)"""
	var stats = get_thread_stats()
	print("========== NETWORK MANAGER STATS ==========")
	print("  Thread Running: ", stats.thread_running)
	print("  Thread Alive: ", stats.thread_alive)
	print("  Queue Size: ", stats.queue_size)
	print("  Messages Queued: ", stats.messages_queued)
	print("  Messages Processed: ", stats.messages_processed)
	print("  Is Host: ", stats.is_host)
	print("  Player Role: ", stats.player_role)
	print("  Main Thread ID: ", stats.main_thread_id)
	print("  Worker Thread ID: ", stats.worker_thread_id)
	print("===========================================")

# ============================================
# LIMPIEZA Y CIERRE (MODIFICADO)
# ============================================

func _stop_worker_thread():
	"""Detiene el hilo worker de forma segura"""
	if not thread_running:
		return
	
	print("[THREAD] Deteniendo hilo worker...")
	thread_running = false
	
	# Despertar al hilo para que vea que debe terminar
	queue_semaphore.post()
	
	# Esperar a que termine limpiamente
	if rpc_worker_thread and rpc_worker_thread.is_alive():
		rpc_worker_thread.wait_to_finish()
		print("[THREAD] ✓ Hilo worker detenido correctamente")

func disconnect_from_game():
	"""Desconecta del juego multijugador"""
	# Primero detener el hilo worker
	_stop_worker_thread()
	
	# Luego cerrar la conexión
	if peer:
		peer.close()
	
	multiplayer.multiplayer_peer = null
	is_host = false
	player_role = ""
	
	print("[NET] Desconectado del juego")

func _exit_tree():
	"""Llamado cuando el nodo sale del árbol de escena"""
	print("[NetworkManager] Ejecutando limpieza...")
	disconnect_from_game()
	print("[NetworkManager] ✓ Limpieza completada")
