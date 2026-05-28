package com.eor.ruteo.data

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

data class SheetResponse(
    val success: Boolean,
    val headers: List<String> = emptyList(),
    val data: List<List<String>> = emptyList()
)

// 👇 NUEVO: Modelo que mapea la respuesta consolidada del agregador del BFF
data class ViajesAgregadosResponse(
    val success: Boolean,
    val diasDisponibles: List<DiaReciente> = emptyList(),
    val data: List<ViajeIntegrado> = emptyList()
)

data class DiaReciente(
    val fecha: String,
    val sheetId: String
)
// (SheetResponse, ViajesAgregadosResponse y DiaReciente continúan idénticos)

// ... (Dentro de RuteoNetwork.kt, actualiza la firma de tu clase principal)


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
    val numeroUt: String,
    val semi: String,
    val chofer: String,
    val ultimoTracking: String,
    val colorHexA: String?,
    val colorHexHx: String?,
    val nViaje: String,
    val llegadaPlanta: String,
    val horarioVacio: String,
    val estadoUt: String
)

data class ParadaViaje(
    val destino: String,
    val producto: String,
    val cantidad: String,
    val cisternado: String,
    val direccion: String
)

interface ApiService {
    @GET("api/sheet/{spreadsheetId}/{sheetName}")
    suspend fun getSheetData(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("sheetName") sheetName: String
    ): SheetResponse

    @GET("api/viajes-integrados/{spreadsheetId}")
    suspend fun getViajesIntegrados(
        @Path("spreadsheetId") spreadsheetId: String
    ): ViajesAgregadosResponse

    // 👇 LA LLAMADA AL NUEVO AGREGADOR BFF (Petición Única)
    @GET("api/viajes-recientes/{masterIndexSheetId}")
    suspend fun getViajesRecientes(
        @Path("masterIndexSheetId") masterIndexSheetId: String
    ): ViajesAgregadosResponse
}

object NetworkClient {
    private const val BASE_URL = "https://db-ehnc.onrender.com/"

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