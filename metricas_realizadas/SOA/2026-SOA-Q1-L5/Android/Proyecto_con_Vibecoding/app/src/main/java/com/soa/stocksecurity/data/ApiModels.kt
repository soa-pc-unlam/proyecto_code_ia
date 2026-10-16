package com.soa.stocksecurity.data

import com.google.gson.annotations.SerializedName

/**
 * Modelos de datos que reflejan la respuesta del endpoint
 * `GET :hostname/api/:device` expuesto por Node-RED.
 *
 * Todos los campos son nullable a propósito: el dispositivo puede estar en un
 * estado incompleto (p. ej. modo virgen sin estantes), y así evitamos crashes
 * de deserialización si falta algún campo.
 */
data class DeviceState(
    @SerializedName("health") val health: Health? = null,
    @SerializedName("lastUpdate") val lastUpdate: Long? = null,
    @SerializedName("system") val system: SystemStatus? = null,
    @SerializedName("alarm") val alarm: Alarm? = null,
    @SerializedName("shelves") val shelves: Map<String, Shelf>? = null,
)

data class Health(
    @SerializedName("status") val status: String? = null, // "online" | "offline"
)

data class SystemStatus(
    // "VIRGIN_EMBEDDED" | "STOCK_MODE" | "SECURITY_MODE" | "UNKNOWN_SYSTEM_STATUS"
    @SerializedName("status") val status: String? = null,
)

data class Alarm(
    @SerializedName("muted") val muted: Boolean? = null,
    @SerializedName("playing") val playing: Boolean? = null,
)

data class Shelf(
    @SerializedName("stock") val stock: Stock? = null,
    @SerializedName("security") val security: Security? = null,
    @SerializedName("tare") val tare: Tare? = null,
)

data class Stock(
    @SerializedName("name") val name: String? = null,
    @SerializedName("weight") val weight: Int? = null,               // gramos
    @SerializedName("weightPerUnit") val weightPerUnit: Int? = null, // gramos por unidad
    @SerializedName("stock") val stock: Int? = null,                 // unidades actuales
    @SerializedName("minimumAcceptableStock") val minimumAcceptableStock: Int? = null,
)

data class Security(
    @SerializedName("weight") val weight: Int? = null,               // gramos
    @SerializedName("anomaly") val anomaly: Boolean? = null,
    @SerializedName("baselineWeight") val baselineWeight: Int? = null,
)

data class Tare(
    @SerializedName("offset") val offset: Int? = null,
    @SerializedName("lastUpdate") val lastUpdate: Long? = null,
)

/**
 * Cuerpo genérico para los comandos POST:
 *  - stock/security  -> "ON" | "OFF"
 *  - security/alarm  -> "MUTE" | "UNMUTE"
 */
data class StatusCommand(
    @SerializedName("status") val status: String,
)
