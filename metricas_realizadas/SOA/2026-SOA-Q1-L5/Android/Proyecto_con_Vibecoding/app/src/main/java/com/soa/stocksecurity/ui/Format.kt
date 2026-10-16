package com.soa.stocksecurity.ui

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Formatea un epoch (ms) a "dd/MM HH:mm:ss" o "—" si es nulo. */
fun formatTimestamp(epochMillis: Long?): String {
    if (epochMillis == null || epochMillis <= 0L) return "—"
    val formatter = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}

/** Convierte gramos a un texto amigable: "1000 g" o "1,2 kg". */
fun formatWeight(grams: Int?): String {
    if (grams == null) return "—"
    return if (grams >= 1000) {
        String.format(Locale.getDefault(), "%.2f kg", grams / 1000.0)
    } else {
        "$grams g"
    }
}

/** Traduce el estado del sistema del embebido a una etiqueta legible. */
fun systemStatusLabel(status: String?): String = when (status) {
    "VIRGIN_EMBEDDED" -> "Modo virgen (en espera)"
    "STOCK_MODE" -> "Modo Stock activo"
    "SECURITY_MODE" -> "Modo Seguridad activo"
    "UNKNOWN_SYSTEM_STATUS" -> "Estado desconocido"
    null -> "Sin datos"
    else -> status
}
