extends Area2D

var consumed := false

func _ready() -> void:
	monitoring = true
	monitorable = true
	body_entered.connect(_on_body_entered)

func _on_body_entered(body: Node) -> void:
	if consumed: return
	if not body.is_in_group("player"): return
	consumed = true
	GameManager.finish_level()
