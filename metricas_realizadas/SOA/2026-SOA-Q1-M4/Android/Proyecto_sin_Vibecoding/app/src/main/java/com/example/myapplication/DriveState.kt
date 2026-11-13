package com.example.myapplication

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import dev.romainguy.kotlin.math.Float3
import io.github.sceneview.Scene
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberCollisionSystem
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberEnvironmentLoader
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes
import io.github.sceneview.rememberRenderer
import io.github.sceneview.rememberScene
import io.github.sceneview.rememberView
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

enum class DriveState { STILL, FORWARD, BACKWARD, SPIN_LEFT, SPIN_RIGHT }

private const val GRID_SIZE = 20f
private const val GRID_LINES = 10

private const val FRAMES_PER_SECOND = 30

@Composable
fun DrivingAnimation(
    state: DriveState,
    modifier: Modifier = Modifier
) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val view = rememberView(engine)
    val renderer = rememberRenderer(engine)
    val scene = rememberScene(engine)
    val environmentLoader = rememberEnvironmentLoader(engine)
    val cameraNode = rememberCameraNode(engine)
    val collisionSystem = rememberCollisionSystem(view)

    val modelInstance = remember(modelLoader) {
        modelLoader.createModelInstance("models/rc_car.glb")
    }

    var carRotation by remember { mutableFloatStateOf(0f) }
    var terrainOffsetX by remember { mutableFloatStateOf(0f) }
    var terrainOffsetZ by remember { mutableFloatStateOf(0f) }

    val darkMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0.05f, 0.05f, 0.05f))
    }
    val greenMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0f, 0.8f, 0f))
    }

    val cellSize = GRID_SIZE / GRID_LINES


    val carNode = remember(modelInstance) {
        ModelNode(
            modelInstance = modelInstance,
            scaleToUnits = 0.3f
        ).apply {
            position = Position(0f, 0f, 0f)
        }
    }

    val groundNode = remember(engine, darkMaterial) {
        CubeNode(
            engine = engine,
            size = Float3(GRID_SIZE, 0.01f, GRID_SIZE),
            materialInstance = darkMaterial
        ).apply {
            position = Position(0f, -1f, 0f)
        }
    }

    val horizontalLines = remember(engine, greenMaterial) {
        List(GRID_LINES + 1) {
            CubeNode(
                engine = engine,
                size = Float3(GRID_SIZE, 0.02f, 0.04f),
                materialInstance = greenMaterial
            )
        }
    }

    val verticalLines = remember(engine, greenMaterial) {
        List(GRID_LINES + 1) {
            CubeNode(
                engine = engine,
                size = Float3(0.04f, 0.02f, GRID_SIZE),
                materialInstance = greenMaterial
            )
        }
    }

    val nodes = rememberNodes()

    LaunchedEffect(Unit) {
        nodes.clear()
        nodes += carNode
        nodes += groundNode
        nodes += horizontalLines
        nodes += verticalLines
    }

    LaunchedEffect(state) {
        while (true) {

            val rad = Math.toRadians(carRotation.toDouble())

            when (state) {
                DriveState.FORWARD -> {
                    terrainOffsetX -= sin(rad).toFloat() * 0.05f
                    terrainOffsetZ -= cos(rad).toFloat() * 0.05f
                }

                DriveState.BACKWARD -> {
                    terrainOffsetX += sin(rad).toFloat() * 0.05f
                    terrainOffsetZ += cos(rad).toFloat() * 0.05f
                }

                DriveState.SPIN_LEFT -> {
                    carRotation -= 2f
                }

                DriveState.SPIN_RIGHT -> {
                    carRotation += 2f
                }

                DriveState.STILL -> {}
            }

            val rad2 = Math.toRadians(carRotation.toDouble())

            cameraNode.position = Position(
                x = (-sin(rad2) * 1f).toFloat(),
                y = 1f,
                z = (-cos(rad2) * 1f).toFloat()
            )

            cameraNode.lookAt(Position(0f, 0f, 0f))

            carNode.rotation = Rotation(
                0f,
                carRotation,
                0f
            )

            val wrappedX = terrainOffsetX % cellSize
            val wrappedZ = terrainOffsetZ % cellSize

            horizontalLines.forEachIndexed { i, node ->
                node.position = Position(
                    wrappedX,
                    -0.99f,
                    (i - GRID_LINES / 2) * cellSize + wrappedZ
                )
            }

            verticalLines.forEachIndexed { i, node ->
                node.position = Position(
                    (i - GRID_LINES / 2) * cellSize + wrappedX,
                    -0.99f,
                    wrappedZ
                )
            }

            delay(1000L / FRAMES_PER_SECOND)
        }
    }

    Scene(
        modifier = modifier,
        engine = engine,
        view = view,
        renderer = renderer,
        scene = scene,
        modelLoader = modelLoader,
        materialLoader = materialLoader,
        environmentLoader = environmentLoader,
        collisionSystem = collisionSystem,
        cameraNode = cameraNode,
        cameraManipulator = rememberCameraManipulator(),
        childNodes = nodes
    )
}