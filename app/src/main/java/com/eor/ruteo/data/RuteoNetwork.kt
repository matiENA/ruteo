package com.eor.ruteo.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

// 1. Modelo de respuesta clásico (para el Índice)
data class SheetResponse(
    val success: Boolean,
    val headers: List<String> = emptyList(),
    val data: List<List<String>> = emptyList()
)

// 👇 NUEVO: Modelo de respuesta directa desde tu microservicio BFF
data class ViajesIntegradosResponse(
    val success: Boolean,
    val data: List<ViajeIntegrado> = emptyList()
)

// 2. Modelo Principal: LA CÁSCARA DEL CAMIÓN (Un solo camión por TD)
// Contrato de datos mapeado de forma idéntica con el BFF de Node.js
data class ViajeIntegrado(
    val idUnico: String,
    val tractor: String,
    val numDespacho: String,
    val terminalOrigen: String,
    val fechaPlanificada: String,
    val cisternadoReal: String,
    val colorHex: String?,
    val isCompletado: Boolean,
    val paradas: List<ParadaViaje>,
    // 👇 Nuevos campos mapeados para el rediseño adaptativo de tarjetas
    val numeroUt: String,
    val semi: String,
    val chofer: String,
    val ultimoTracking: String,
    val colorHexA: String?,
    val colorHexHx: String?
)

// 3. Sub-modelo: EL INTERIOR DEL CAMIÓN (Lo que se ve al abrir el acordeón)
data class ParadaViaje(
    val destino: String,
    val producto: String,
    val cantidad: String,
    val cisternado: String
)

// 4. Interfaz Retrofit para llamar a tu backend en Render
interface ApiService {
    @GET("api/sheet/{spreadsheetId}/{sheetName}")
    suspend fun getSheetData(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("sheetName") sheetName: String
    ): SheetResponse

    // 👇 NUEVO: La llamada directa a tu backend optimizado
    @GET("api/viajes-integrados/{spreadsheetId}")
    suspend fun getViajesIntegrados(
        @Path("spreadsheetId") spreadsheetId: String
    ): ViajesIntegradosResponse
}

// 5. Cliente de red configurado
object NetworkClient {
    private const val BASE_URL = "https://db-ehnc.onrender.com/"

    // Mantuvimos los 60 segundos de paciencia para que nunca te dé Timeout
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}