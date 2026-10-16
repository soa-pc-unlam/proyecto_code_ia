package com.soa.stocksecurity.data

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Endpoints HTTP expuestos por Node-RED (ver infraestructura_nodeRed.md).
 * La base URL (http://<ip>:<puerto>/) se inyecta al construir Retrofit.
 */
interface NodeRedApi {

    /** Estado completo del dispositivo: health, system, alarm y estantes. */
    @GET("api/{device}")
    suspend fun getDevice(@Path("device") device: String): DeviceState

    /** Activa ("ON") o desactiva ("OFF") el modo Stock. */
    @POST("api/{device}/stock")
    suspend fun setStock(
        @Path("device") device: String,
        @Body command: StatusCommand,
    ): Response<Unit>

    /** Activa ("ON") o desactiva ("OFF") el modo Seguridad. */
    @POST("api/{device}/security")
    suspend fun setSecurity(
        @Path("device") device: String,
        @Body command: StatusCommand,
    ): Response<Unit>

    /** Silencia ("MUTE") o reactiva ("UNMUTE") la alarma (buzzer). */
    @POST("api/{device}/security/alarm")
    suspend fun setAlarm(
        @Path("device") device: String,
        @Body command: StatusCommand,
    ): Response<Unit>
}
