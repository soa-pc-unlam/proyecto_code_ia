extends CanvasLayer

@onready var corazones = [
	$MarginContainer/HBoxContainer/Corazon1,
	$MarginContainer/HBoxContainer/Corazon2,
	$MarginContainer/HBoxContainer/Corazon3
]

func _ready():
	# Verificar si el jugador es trap_master
	if NetworkManager.player_role == "trap_master":
		visible = false
		return
		
	# Conectar a la señal del jugador
	var player = get_tree().get_first_node_in_group("player")
	if player:
		player.vidas_changed.connect(_on_vidas_changed)
		# Inicializar con las vidas actuales del jugador
		actualizar_corazones(player.vidas_actuales)
	else:
		# Si no encuentra el jugador, mostrar 3 corazones por defecto
		actualizar_corazones(3)

func _on_vidas_changed(vidas_restantes: int):
	actualizar_corazones(vidas_restantes)

func actualizar_corazones(vidas: int):
	for i in range(corazones.size()):
		if i < vidas:
			# Corazón lleno
			corazones[i].modulate = Color(1, 1, 1, 1)
		else:
			# Corazón vacío (semi-transparente)
			corazones[i].modulate = Color(1, 1, 1, 0.3)
			# O puedes hacer invisible: corazones[i].visible = false
