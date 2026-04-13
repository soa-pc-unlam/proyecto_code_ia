extends Control

@onready var lbl_time: Label   = $Tiempo
@onready var lbl_deaths: Label = $Muertes
@onready var btn_exit: Button  = $ExitBtn
@onready var btn_menu: Button = $MenuBtn

func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	mouse_filter = Control.MOUSE_FILTER_STOP

	btn_exit.pressed.connect(_on_exit)
	btn_menu.pressed.connect(_on_menu)
	
	var gm = GameManager

	# Mostrar stats guardadas por GameManager antes de cambiar a WinMenu
	if lbl_time:
		lbl_time.text = "Pasaste el juego en: %.2f segundos" % gm.last_run_time
	if lbl_deaths:
		lbl_deaths.text = "Te moriste: 1 vez" if gm.last_run_deaths==1 else "Te moriste: %d veces" % gm.last_run_deaths

func _on_exit() -> void:
	var tree = get_tree()
	if tree:
		tree.paused = false
		tree.quit()
		
func _on_menu() -> void:
	var tree = get_tree()
	if tree:
		tree.paused = false
		tree.change_scene_to_file("res://scenes/MainMenu.tscn")
