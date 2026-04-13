extends Control
class_name MoveIndicator

func _ready():
	mouse_filter = MOUSE_FILTER_IGNORE
	queue_redraw()

func _draw():
	var tile_size = min(size.x, size.y)
	var center := Vector2(size.x / 2, size.y / 2)
	var radius = (tile_size * Config.MovementIndicatorConfig.DIAMETER_RATIO) / 2.0
	
	draw_arc(center, radius, 0, TAU, Config.MovementIndicatorConfig.POINTS, Config.MovementIndicatorConfig.COLOR, Config.MovementIndicatorConfig.LINE_WIDTH)
