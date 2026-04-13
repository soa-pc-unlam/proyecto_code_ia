extends Node

# --- tu config actual ---
@export var victory_ui_scene: PackedScene = null  # (ya no lo usamos en single)

var singleplayer: bool = true
var total_deaths: int = 0
var total_games: int = 0

# --- stats de la run actual ---
var level_running: bool = false
var level_started_at: float = 0.0
var run_deaths: int = 0

# --- stats de la última victoria (para que WinMenu las lea) ---
var last_run_time: float = 0.0
var last_run_deaths: int = 0

func _ready() -> void:
	print("GameManager iniciado")

func start_level_stats() -> void:
	level_started_at = Time.get_ticks_msec() * 0.001
	run_deaths = 0
	level_running = true

func register_death() -> void:
	total_deaths += 1
	if level_running:
		run_deaths += 1

func get_elapsed_time() -> float:
	return max(0.0, (Time.get_ticks_msec() * 0.001) - level_started_at)

func finish_level() -> void:
	if not level_running:
		return
	level_running = false

	# Guardar stats para que el WinMenu las lea (para el que gane)
	last_run_time = get_elapsed_time()
	last_run_deaths = run_deaths

	if NetworkManager.is_multiplayer_active():
		# Este RPC lo ejecutan todos y cada uno decide qué escena mostrar
		rpc("sync_end_match", "runner")
		return

	# --- SINGLEPLAYER ---
	var tree := get_tree()
	if tree == null:
		push_error("SceneTree es null: no puedo cambiar al WinMenu.")
		return

	tree.change_scene_to_file("res://scenes/WinMenu.tscn")

	
@rpc("any_peer", "call_local", "reliable")
func sync_end_match(winner_role: String) -> void:
	print("[GameManager] Fin de partida. Ganador:", winner_role)

	# 1) Me guardo mi rol local
	var my_role: String = NetworkManager.player_role
	var i_win: bool = (my_role == winner_role)

	# 2) Cambio de escena según si gané o no
	var tree := get_tree()
	if tree == null:
		push_error("SceneTree es null: no puedo cambiar escena de fin de partida.")
		return

	if i_win:
		# Escena de victoria
		tree.change_scene_to_file("res://scenes/WinMenu.tscn")
	else:
		# Escena de derrota
		tree.change_scene_to_file("res://scenes/GameOver.tscn")
