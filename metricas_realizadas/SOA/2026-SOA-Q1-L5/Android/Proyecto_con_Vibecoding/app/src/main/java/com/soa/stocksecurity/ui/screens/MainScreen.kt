package com.soa.stocksecurity.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soa.stocksecurity.ui.DeviceViewModel
import com.soa.stocksecurity.ui.components.CardHeader
import com.soa.stocksecurity.ui.components.InfoRow
import com.soa.stocksecurity.ui.components.SectionCard
import com.soa.stocksecurity.ui.components.SoftDivider
import com.soa.stocksecurity.ui.formatTimestamp
import com.soa.stocksecurity.ui.systemStatusLabel
import com.soa.stocksecurity.ui.theme.Cream
import com.soa.stocksecurity.ui.theme.Danger
import com.soa.stocksecurity.ui.theme.DangerSoft
import com.soa.stocksecurity.ui.theme.Sage
import com.soa.stocksecurity.ui.theme.SageDark
import com.soa.stocksecurity.ui.theme.SageSoft
import com.soa.stocksecurity.ui.theme.Success
import com.soa.stocksecurity.ui.theme.SuccessSoft
import com.soa.stocksecurity.ui.theme.Terracotta
import com.soa.stocksecurity.ui.theme.TerracottaDark
import com.soa.stocksecurity.ui.theme.TerracottaSoft
import com.soa.stocksecurity.ui.theme.TextPrimary
import com.soa.stocksecurity.ui.theme.TextSecondary

@Composable
fun MainScreen(
    viewModel: DeviceViewModel,
    onOpenStock: () -> Unit,
    onOpenSecurity: () -> Unit,
) {
    val state = viewModel.uiState
    val snackbar = remember { SnackbarHostState() }

    // Intenta conectar automáticamente al abrir la app.
    LaunchedEffect(Unit) { viewModel.refresh() }

    // Muestra feedback de acciones/errores.
    LaunchedEffect(state.message, state.error) {
        val text = state.message ?: state.error
        if (text != null) {
            snackbar.showSnackbar(text)
            viewModel.clearMessage()
        }
    }

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
            // ---- Encabezado ----
            Spacer(Modifier.height(8.dp))
            Text(
                "Stock & Seguridad",
                style = MaterialTheme.typography.headlineMedium,
                color = TextPrimary,
            )
            Text(
                "Panel de control · ${state.device}",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
            Spacer(Modifier.height(20.dp))

            // ---- Estado de conexión ----
            ConnectionCard(
                connected = state.connected,
                loading = state.loading,
                systemStatus = systemStatusLabel(state.data?.system?.status),
                deviceHealth = state.data?.health?.status,
                lastRefresh = formatTimestamp(state.lastRefresh),
                onRefresh = { viewModel.refresh() },
            )

            Spacer(Modifier.height(16.dp))

            // ---- Configuración de conexión ----
            ConfigCard(
                host = state.host,
                port = state.port,
                device = state.device,
                onHostChange = viewModel::onHostChange,
                onPortChange = viewModel::onPortChange,
                onDeviceChange = viewModel::onDeviceChange,
            )

            Spacer(Modifier.height(24.dp))

            Text(
                "MODOS",
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                modifier = Modifier.padding(start = 4.dp, bottom = 12.dp),
            )

            // ---- Burbujas de modos ----
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ModeBubble(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Inventory2,
                    title = "Modo Stock",
                    subtitle = "Ver inventario",
                    tint = SageDark,
                    container = SageSoft,
                    ring = Sage,
                    onClick = onOpenStock,
                )
                ModeBubble(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Filled.Shield,
                    title = "Modo Seguridad",
                    subtitle = "Ver alertas",
                    tint = TerracottaDark,
                    container = TerracottaSoft,
                    ring = Terracotta,
                    onClick = onOpenSecurity,
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ConnectionCard(
    connected: Boolean,
    loading: Boolean,
    systemStatus: String,
    deviceHealth: String?,
    lastRefresh: String,
    onRefresh: () -> Unit,
) {
    val accent by animateColorAsState(if (connected) Success else Danger, label = "accent")
    val container = if (connected) SuccessSoft else DangerSoft
    val statusText = if (connected) "Servicio conectado" else "Sin conexión"
    val icon = if (connected) Icons.Outlined.Wifi else Icons.Outlined.WifiOff

    SectionCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(container, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(26.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(statusText, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
                Text(
                    "Actualizado: $lastRefresh",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                )
            }
            // Botón de actualizar manual
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                    .clickable(enabled = !loading) { onRefresh() },
                contentAlignment = Alignment.Center,
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.5.dp,
                        color = accent,
                    )
                } else {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "Actualizar",
                        tint = TextPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SoftDivider()
        Spacer(Modifier.height(6.dp))

        InfoRow(label = "Estado del sistema", value = systemStatus)
        InfoRow(
            label = "Salud del dispositivo",
            value = when (deviceHealth) {
                "online" -> "En línea"
                "offline" -> "Fuera de línea"
                else -> "—"
            },
            valueColor = when (deviceHealth) {
                "online" -> Success
                "offline" -> Danger
                else -> TextSecondary
            },
        )
    }
}

@Composable
private fun ConfigCard(
    host: String,
    port: String,
    device: String,
    onHostChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onDeviceChange: (String) -> Unit,
) {
    SectionCard {
        CardHeader(
            icon = Icons.Filled.Router,
            title = "Conexión",
            tint = SageDark,
            container = SageSoft,
            subtitle = "IP del broker / Node-RED",
        )
        Spacer(Modifier.height(16.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(
                modifier = Modifier.weight(2f),
                value = host,
                onValueChange = onHostChange,
                label = "IP / Host",
                leading = Icons.Outlined.Dns,
                keyboardType = KeyboardType.Uri,
            )
            AppTextField(
                modifier = Modifier.weight(1f),
                value = port,
                onValueChange = onPortChange,
                label = "Puerto",
                keyboardType = KeyboardType.Number,
            )
        }
        Spacer(Modifier.height(12.dp))
        AppTextField(
            modifier = Modifier.fillMaxWidth(),
            value = device,
            onValueChange = onDeviceChange,
            label = "Dispositivo",
            leading = Icons.Outlined.Memory,
        )
    }
}

@Composable
private fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    leading: ImageVector? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = true,
        leadingIcon = leading?.let {
            { Icon(it, contentDescription = null, modifier = Modifier.size(20.dp)) }
        },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Sage,
            focusedLabelColor = SageDark,
            focusedLeadingIconColor = SageDark,
            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
            cursorColor = Sage,
        ),
    )
}

@Composable
private fun ModeBubble(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    container: Color,
    ring: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(24.dp))
            .clickable { onClick() }
            .padding(vertical = 22.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .background(container, CircleShape)
                .border(1.5.dp, ring.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = title, tint = tint, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
