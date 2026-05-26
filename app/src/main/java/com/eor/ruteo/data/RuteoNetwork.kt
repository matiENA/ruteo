package com.eor.ruteo.data

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path

// 1. Modelo de respuesta que coincide con el JSON de tu dataController.js
data class SheetResponse(
    val success: Boolean,
    val headers: List<String>,
    val data: List<List<String>> // Matriz de filas y columnas devuelta por el backend
)

// 2. Modelo de UI (Lo que usa Jetpack Compose)
data class RuteoData(
    val id: String,
    val terminal: String,
    val fecha: String,
    val vehiculo: String, // ¡Fíjate que esté en minúscula!
    val destino: String,
    val colorCeldaH: String?,
    val isCompletado: Boolean
)

// 3. Interfaz Retrofit para llamar a tu backend
interface ApiService {
    @GET("api/sheet/{spreadsheetId}/{sheetName}")
    suspend fun getSheetData(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("sheetName") sheetName: String
    ): SheetResponse
}

object NetworkClient {
    // 1. Reemplaza la IP local por tu URL real de Render.
    // ⚠️ IMPORTANTE: Asegúrate de que termine con una barra diagonal "/"
    private const val BASE_URL = "https://db-ehnc.onrender.com/"

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}