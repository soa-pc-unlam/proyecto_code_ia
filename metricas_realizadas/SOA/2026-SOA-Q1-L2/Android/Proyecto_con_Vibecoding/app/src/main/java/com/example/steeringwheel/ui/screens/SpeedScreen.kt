package com.example.steeringwheel.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.steeringwheel.domain.model.GpsStatus
import com.example.steeringwheel.presentation.state.SpeedUiState
import com.example.steeringwheel.presentation.viewmodel.SpeedViewModel

@Composable
fun SpeedScreen(
    viewModel: SpeedViewModel
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            viewModel.startLocationUpdates()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopLocationUpdates()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Velocidad GPS",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        if (!hasPermission) {
            PermissionDeniedCard {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        } else {
            SpeedDisplay(state)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            GpsStatusCard(state)
            
            Spacer(modifier = Modifier.height(24.dp))
            
            ThresholdSettingsCard(
                threshold = state.threshold,
                isSaving = state.isSaving,
                onSave = viewModel::updateThreshold
            )
        }
    }
}

@Composable
fun SpeedDisplay(state: SpeedUiState) {
    val isActive = state.locationData.speedKmh >= state.threshold
    val statusColor = if (isActive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val statusText = if (isActive) "Las maniobras activan alarma" else "Maniobras silenciadas (velocidad baja)"

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = statusColor.copy(alpha = 0.1f))
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "${state.locationData.speedKmh}",
                fontSize = 80.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
            Text(
                text = "km/h",
                style = MaterialTheme.typography.headlineSmall,
                color = statusColor
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }
    }
}

@Composable
fun GpsStatusCard(state: SpeedUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.GpsFixed, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Estado del GPS", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (state.locationData.status) {
                    GpsStatus.SEARCHING -> "Buscando señal..."
                    GpsStatus.ACTIVE -> "Activo"
                    GpsStatus.PERMISSION_DENIED -> "Permiso denegado"
                    GpsStatus.ERROR -> "Error de GPS"
                }
            )
            if (state.locationData.status == GpsStatus.ACTIVE) {
                Text(text = "Latitud: ${"%.5f".format(state.locationData.latitude)}")
                Text(text = "Longitud: ${"%.5f".format(state.locationData.longitude)}")
                Text(text = "Precisión: ${"%.1f".format(state.locationData.accuracy)} m")
            }
        }
    }
}

@Composable
fun ThresholdSettingsCard(
    threshold: Int,
    isSaving: Boolean,
    onSave: (Int) -> Unit
) {
    var textValue by remember(threshold) { mutableStateOf(threshold.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Umbral de Velocidad", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = textValue,
                onValueChange = {
                    textValue = it
                    val value = it.toIntOrNull()
                    error = when {
                        value == null -> "Debe ser un número"
                        value !in 1..100 -> "Rango: 1 - 100"
                        else -> null
                    }
                },
                label = { Text("Umbral (km/h)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = error != null,
                supportingText = { error?.let { Text(it) } }
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { textValue.toIntOrNull()?.let { onSave(it) } },
                modifier = Modifier.align(Alignment.End),
                enabled = !isSaving && error == null
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
                } else {
                    Text("Guardar Umbral")
                }
            }
        }
    }
}

@Composable
fun PermissionDeniedCard(onRetry: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Permiso de ubicación necesario",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "La app necesita acceder a tu ubicación para calcular la velocidad y enviarla al volante.",
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onRetry, modifier = Modifier.align(Alignment.End)) {
                Text("Conceder Permiso")
            }
        }
    }
}
