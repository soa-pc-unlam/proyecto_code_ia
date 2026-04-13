extends AnimatableBody2D

@export var pixels_down: float = 200.0
@export var descend_time: float = 0.8
@export var bottom_wait: float = 3.0
@export var ascend_time: float = 0.8

@onready var col_shape: CollisionShape2D = $CollisionShape2D
@onready var area: Area2D = $Area2D

var _moving: bool = false
var _start_y: float

func _ready() -> void:
	_start_y = global_position.y
	area.body_entered.connect(_on_body_entered)

func _on_body_entered(body: Node) -> void:
	if not body.is_in_group("player"):
		return
	if _moving:
		return
	_run_cycle()

func _run_cycle() -> void:
	_moving = true
	var start_y := _start_y
	var end_y := start_y + pixels_down

	# Baja
	var t1 = create_tween()
	t1.tween_property(self, "global_position:y", end_y, descend_time)\
		.set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_IN)
	await t1.finished

	# Abajo: desactiva colisión y espera 3 segundos
	col_shape.disabled = false
	await get_tree().create_timer(bottom_wait).timeout

	# Sube
	var t2 = create_tween()
	t2.tween_property(self, "global_position:y", start_y, ascend_time)\
		.set_trans(Tween.TRANS_SINE).set_ease(Tween.EASE_OUT)
	await t2.finished

	col_shape.disabled = false
	_moving = false
