extends Camera2D

# Configuración de movimiento de cámara
@export var move_speed: float = 300.0
@export var zoom_speed: float = 0.2
@export var min_zoom: float = 0.3
@export var max_zoom: float = 2.0

func _ready():
	enabled = true
	# Vista más alejada por defecto
	zoom = Vector2(1.5, 1.5)

func _process(delta):
	# Mover cámara con WASD
	var direction = Vector2.ZERO
	
	if Input.is_action_pressed("camera_left"):
		direction.x -= 1
	if Input.is_action_pressed("camera_right"):
		direction.x += 1
	if Input.is_action_pressed("camera_up"):
		direction.y -= 1
	if Input.is_action_pressed("camera_down"):
		direction.y += 1
	
	position += direction.normalized() * move_speed * delta
	
	# Zoom (igual que antes)
	if Input.is_action_just_pressed("ui_page_up"):
		zoom += Vector2(zoom_speed, zoom_speed)
	if Input.is_action_just_pressed("ui_page_down"):
		zoom -= Vector2(zoom_speed, zoom_speed)
	
	zoom.x = clamp(zoom.x, min_zoom, max_zoom)
	zoom.y = clamp(zoom.y, min_zoom, max_zoom)

func _input(event):
	# Zoom con rueda del mouse
	if event is InputEventMouseButton:
		if event.button_index == MOUSE_BUTTON_WHEEL_UP:
			zoom += Vector2(zoom_speed, zoom_speed)
			zoom.x = clamp(zoom.x, min_zoom, max_zoom)
			zoom.y = clamp(zoom.y, min_zoom, max_zoom)
		elif event.button_index == MOUSE_BUTTON_WHEEL_DOWN:
			zoom -= Vector2(zoom_speed, zoom_speed)
			zoom.x = clamp(zoom.x, min_zoom, max_zoom)
			zoom.y = clamp(zoom.y, min_zoom, max_zoom)
