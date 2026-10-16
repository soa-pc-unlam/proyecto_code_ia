package com.soa.stocksecurity.ui.screens

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
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
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
import com.soa.stocksecurity.ui.theme.Cream
import com.soa.stocksecurity.ui.theme.Danger
import com.soa.stocksecurity.ui.theme.DangerSoft
import com.soa.stocksecurity.ui.theme.Sage
import com.soa.stocksecurity.ui.theme.SageDark
import com.soa.stocksecurity.ui.theme.SageSoft
import com.soa.stocksecurity.ui.theme.Success
import com.soa.stocksecurity.ui.theme.SuccessSoft
import com.soa.stocksecurity.ui.theme.TextPrimary
import com.soa.stocksecurity.ui.theme.TextSecondary

@Composable
fun StockScreen(
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

    val stockActive = state.data?.system?.status == "STOCK_MODE"
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
                title = "Modo Stock",
                subtitle = "Inventario de estantes",
                onBack = onBack,
            )
            Spacer(Modifier.height(16.dp))

            // ---- Control de activación ----
            SectionCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(42.dp).background(SageSoft, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Inventory2, null, tint = SageDark, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Activar modo Stock", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        Text(
                            if (stockActive) "Activo en el dispositivo" else "Inactivo",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (stockActive) Success else TextSecondary,
                        )
                    }
                    Switch(
                        checked = stockActive,
                        onCheckedChange = { viewModel.setStockMode(it) },
                        enabled = !state.actionInProgress,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Sage,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                    )
                }
            }

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
                RefreshButton(loading = state.loading) { viewModel.refresh() }
            }
            Spacer(Modifier.height(12.dp))

            if (!state.connected && shelves.isEmpty()) {
                EmptyState("Sin conexión con el dispositivo. Volvé al inicio y presioná actualizar.")
            } else if (shelves.isEmpty()) {
                EmptyState("No hay estantes reportados. El dispositivo puede estar en modo virgen.")
            } else {
                shelves.entries.sortedBy { it.key }.forEach { (shelfId, shelf) ->
                    StockShelfCard(shelfId = shelfId, shelf = shelf)
                    Spacer(Modifier.height(14.dp))
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun StockShelfCard(shelfId: String, shelf: Shelf) {
    val stock = shelf.stock
    val current = stock?.stock ?: 0
    val minimum = stock?.minimumAcceptableStock ?: 0
    val needsRestock = stock != null && current < minimum

    SectionCard {
        CardHeader(
            icon = Icons.Filled.Inventory2,
            title = stock?.name?.takeIf { it.isNotBlank() } ?: "Producto",
            tint = SageDark,
            container = SageSoft,
            subtitle = shelfId,
        )
        Spacer(Modifier.height(8.dp))

        // Estado de reposición
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Reposición", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            if (needsRestock) {
                StatusChip("REPONER", Danger, DangerSoft)
            } else {
                StatusChip("OK", Success, SuccessSoft)
            }
        }

        Spacer(Modifier.height(6.dp))
        SoftDivider()
        Spacer(Modifier.height(6.dp))

        InfoRow(
            label = "Stock actual",
            value = "$current u.",
            valueColor = if (needsRestock) Danger else TextPrimary,
        )
        InfoRow(label = "Stock mínimo", value = "$minimum u.")
        InfoRow(label = "Peso actual", value = formatWeight(stock?.weight))
        InfoRow(label = "Peso por unidad", value = formatWeight(stock?.weightPerUnit))
    }
}

@Composable
private fun RefreshButton(loading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(MaterialTheme.colorScheme.surface, CircleShape)
            .clickable(enabled = !loading) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Sage)
        } else {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "Actualizar",
                tint = SageDark,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    SectionCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Warning,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary,
            )
        }
    }
}
