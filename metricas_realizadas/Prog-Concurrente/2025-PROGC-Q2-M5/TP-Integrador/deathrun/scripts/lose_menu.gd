extends Control

@onready var btn_exit: Button  = $ExitBtn
@onready var btn_menu: Button = $MenuBtn

func _ready() -> void:
	process_mode = Node.PROCESS_MODE_ALWAYS
	mouse_filter = Control.MOUSE_FILTER_STOP

	btn_exit.pressed.connect(_on_exit)
	btn_menu.pressed.connect(_on_menu)
	
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
