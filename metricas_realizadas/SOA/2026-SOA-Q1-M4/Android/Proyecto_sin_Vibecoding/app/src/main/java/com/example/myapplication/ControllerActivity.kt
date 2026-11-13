package com.example.myapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.example.myapplication.DrivingAnimation
import com.example.myapplication.DriveState
import com.google.android.material.appbar.MaterialToolbar
import kotlinx.coroutines.launch
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

class ControllerActivity : AppCompatActivity() {

    private var driveStateHolder = mutableStateOf(DriveState.STILL)
    private val mqtt by lazy {
        (application as CustomApplication).mqttManager
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.controller)

        val composeContainer = findViewById<FrameLayout>(R.id.composeContainer)
        val composeView = ComposeView(this).apply {
            setContent {
                DrivingAnimation(
                    state = driveStateHolder.value,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        composeContainer.addView(composeView)

        // Escuchar el topico robot/state y actualizar la animacion
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED){
            mqtt.stateFlow.collect { robotState ->
                android.util.Log.d("DRIVE_STATE", "Estado recibido: ${robotState.state}")
                driveStateHolder.value = when {
                    robotState.state?.contains("FORWARD") == true  -> DriveState.FORWARD
                    robotState.state?.contains("BACKWARD") == true -> DriveState.BACKWARD
                    robotState.state?.contains("LEFT") == true     -> DriveState.SPIN_LEFT
                    robotState.state?.contains("RIGHT") == true    -> DriveState.SPIN_RIGHT
                    else                                           -> DriveState.STILL
                  }
                }
            }
        }

        val topBar = findViewById<MaterialToolbar>(R.id.topAppBar)
        topBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_settings -> {
                    ConfigModalFragment().show(supportFragmentManager, "settings")
                    true
                }
                else -> false
            }
        }

        val btnSensors = findViewById<Button>(R.id.btnSensors)
        val btnForward = findViewById<Button>(R.id.btnUp)
        val btnRight = findViewById<Button>(R.id.btnRight)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val btnDown = findViewById<Button>(R.id.btnDown)
        val btnLeft = findViewById<Button>(R.id.btnLeft)

        btnSensors.setOnClickListener {
            startActivity(Intent(this, SensorActivity::class.java))
        }

        btnForward.setOnClickListener { mqtt.sendCommand("forward") }
        btnDown.setOnClickListener { mqtt.sendCommand("backward") }
        btnLeft.setOnClickListener { mqtt.sendCommand("left") }
        btnRight.setOnClickListener { mqtt.sendCommand("right") }
        btnStop.setOnClickListener { mqtt.sendCommand("stop") }
    }
}