package com.soa.stocksecurity.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.GppGood
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soa.stocksecurity.data.Shelf
import com.soa.stocksecurity.ui.DeviceViewModel
import com.soa.stocksecurity.ui.components.CardHeader
import com.soa.stocksecurity.ui.components.InfoRow
import com.soa.stocksecurity.ui.components.ScreenHeader
import com.soa.stocksecurity.ui.components.SectionCard
import com.soa.stocksecurity.ui.components.SoftDivider
import com.soa.stocksecurity.ui.components.StatusChip
import com.soa.stocksecurity.ui.formatWeight
import com.soa.stocksecurity.ui.theme.Amber
import com.soa.stocksecurity.ui.theme.Cream
import com.soa.stocksecurity.ui.theme.Danger
import com.soa.stocksecurity.ui.theme.DangerSoft
import com.soa.stocksecurity.ui.theme.Success
import com.soa.stocksecurity.ui.theme.SuccessSoft
import com.soa.stocksecurity.ui.theme.Terracotta
import com.soa.stocksecurity.ui.theme.TerracottaDark
import com.soa.stocksecurity.ui.theme.TerracottaSoft
import com.soa.stocksecurity.ui.theme.TextPrimary
import com.soa.stocksecurity.ui.theme.TextSecondary

@Composable
fun SecurityScreen(
    viewModel: DeviceViewModel,
    onBack: () -> Unit,
) {
    val state = viewModel.uiState
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message, state.error) {
        val text = state.message ?: state.error
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

    val securityActive = state.data?.system?.status == "SECURITY_MODE"
    val alarm = state.data?.alarm
    val muted = alarm?.muted ?: true          // por defecto asumimos silenciada
    val playing = alarm?.playing ?: false
    val shelves = state.data?.shelves.orEmpty()

    Scaffold(
        containerColor = Cream,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            ScreenHeader(
                title = "Modo Seguridad",
                subtitle = "Detección de anomalías",
                onBack = onBack,
            )
            Spacer(Modifier.height(16.dp))

            // ---- Activar modo Seguridad ----
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(42.dp).background(TerracottaSoft, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Shield, null, tint = TerracottaDark, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Activar modo Seguridad", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            if (securityActive) "Activo en el dispositivo" else "Inactivo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (securityActive) Success else TextSecondary,
                        )
                    }
                    Switch(
                        checked = securityActive,
                        onCheckedChange = { viewModel.setSecurityMode(it) },
                        enabled = !state.actionInProgress,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Terracotta,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // ---- Control de alarma (buzzer) ----
            AlarmCard(
                muted = muted,
                playing = playing,
                enabled = !state.actionInProgress,
                onToggle = { enabled -> viewModel.setAlarmMuted(!enabled) },
            )

            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "ESTANTES",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                SecurityRefreshButton(loading = state.loading) { viewModel.refresh() }
            }
            Spacer(Modifier.height(12.dp))

            if (!state.connected && shelves.isEmpty()) {
                SecurityEmptyState("Sin conexión con el dispositivo. Volvé al inicio y presioná actualizar.")
            } else if (shelves.isEmpty()) {
                SecurityEmptyState("No hay estantes reportados. El dispositivo puede estar en modo virgen.")
            } else {
                shelves.entries.sortedBy { it.key }.forEach { (shelfId, shelf) ->
                    SecurityShelfCard(shelfId = shelfId, shelf = shelf)
                    Spacer(Modifier.height(14.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun AlarmCard(
    muted: Boolean,
    playing: Boolean,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
) {
    // "Habilitada" = no silenciada (UNMUTE). El buzzer solo suena si además hay anomalía.
    val alarmEnabled = !muted
    val accent by animateColorAsState(
        when {
            playing -> Danger
            alarmEnabled -> Terracotta
            else -> TextSecondary
        },
        label = "alarmAccent",
    )
    val icon = if (alarmEnabled) Icons.Filled.NotificationsActive else Icons.Filled.NotificationsOff

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(if (playing) DangerSoft else TerracottaSoft, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Alarma (buzzer)", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                Text(
                    when {
                        playing -> "¡Sonando ahora!"
                        alarmEnabled -> "Habilitada · en silencio"
                        else -> "Apagada (silenciada)"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = accent,
                    fontWeight = FontWeight.Medium,
                )
            }
            Switch(
                checked = alarmEnabled,
                onCheckedChange = onToggle,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Terracotta,
                    uncheckedThumbColor = Color.White,
                    uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            )
        }
    }
}

@Composable
private fun SecurityShelfCard(shelfId: String, shelf: Shelf) {
    val security = shelf.security
    val anomaly = security?.anomaly == true
    val currentWeight = security?.weight
    val baselineWeight = security?.baselineWeight
    val difference: Int? =
        if (currentWeight != null && baselineWeight != null) currentWeight - baselineWeight else null

    SectionCard {
        CardHeader(
            icon = if (anomaly) Icons.Outlined.ReportProblem else Icons.Outlined.GppGood,
            title = shelfId,
            tint = if (anomaly) Danger else Success,
            container = if (anomaly) DangerSoft else SuccessSoft,
            subtitle = if (anomaly) "Anomalía detectada" else "Sin anomalías",
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Estado", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            if (anomaly) {
                StatusChip("ALERTA", Danger, DangerSoft)
            } else {
                StatusChip("SEGURO", Success, SuccessSoft)
            }
        }

        Spacer(Modifier.height(6.dp))
        SoftDivider()
        Spacer(Modifier.height(6.dp))

        InfoRow(
            label = "Peso actual",
            value = formatWeight(security?.weight),
            valueColor = if (anomaly) Danger else TextPrimary,
        )
        InfoRow(label = "Peso de referencia", value = formatWeight(security?.baselineWeight))
        InfoRow(
            label = "Diferencia",
            value = formatWeight(difference),
            valueColor = if (anomaly) Amber else TextSecondary,
        )
    }
}

@Composable
private fun SecurityRefreshButton(loading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .clickable(enabled = !loading) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Terracotta)
        } else {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "Actualizar",
                tint = TerracottaDark,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun SecurityEmptyState(message: String) {
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
        }
    }
}
