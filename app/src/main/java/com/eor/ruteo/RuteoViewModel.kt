package com.eor.ruteo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
// 👇 Estos imports conectan la carpeta UI con la carpeta DATA
import com.eor.ruteo.data.NetworkClient
import com.eor.ruteo.data.RuteoData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Modelo para el Menú Principal
data class DiaRuteo(val fecha: String, val sheetId: String)

sealed class UiState {
    object Loading : UiState()
    // Ahora el estado exitoso tiene la lista de días y los datos del día
    data class Success(val diasDisponibles: List<DiaRuteo>, val dataRuteo: List<RuteoData>) : UiState()
    data class Error(val message: String) : UiState()
}

class RuteoViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    // 👇 ESTE ES EL ÚNICO ID QUE QUEDARÁ FIJO EN LA APP (El del Sheet Maestro)
    private val masterIndexSheetId = "1ny9yOftgyYWfzJFpQ9h8l2T_owDlyMV_HdEgeQ5Gm8E"

    private var listaDias = listOf<DiaRuteo>()

    init {
        cargarIndiceDeDias()
    }

    private fun cargarIndiceDeDias() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                // Asegúrate de que la pestaña de tu Sheet Maestro se llame "Indice"
                val response = NetworkClient.api.getSheetData(masterIndexSheetId, "Indice")

                if (response.success) {
                    listaDias = response.data.map { fila ->
                        // Usamos getOrElse para evitar errores si Google Sheets devuelve celdas vacías
                        DiaRuteo(
                            fecha = fila.getOrElse(0) { "Sin fecha" },
                            sheetId = fila.getOrElse(1) { "" }
                        )
                    }

                    // Automáticamente cargamos el ruteo del ÚLTIMO DÍA (el más reciente)
                    val ultimoDia = listaDias.lastOrNull()
                    if (ultimoDia != null && ultimoDia.sheetId.isNotEmpty()) {
                        cargarRuteoDelDia(ultimoDia.sheetId)
                    } else {
                        _uiState.value = UiState.Error("El índice no tiene días registrados.")
                    }
                } else {
                    _uiState.value = UiState.Error("No se pudo cargar el índice.")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Error de red: ${e.message}")
            }
        }
    }

    fun cargarRuteoDelDia(idDelDia: String) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val response = NetworkClient.api.getSheetData(idDelDia, "Ruteo")
                if (response.success) {
                    val ruteoList = mapResponseToUI(response.data)
                    // Devolvemos tanto los botones del menú como la data
                    _uiState.value = UiState.Success(listaDias, ruteoList)
                } else {
                    _uiState.value = UiState.Error("Respuesta fallida del servidor.")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Error al cargar el día: ${e.message}")
            }
        }
    }

    // 👇 AQUÍ ESTÁ LA FUNCIÓN QUE FALTABA
    private fun mapResponseToUI(rawData: List<List<String>>): List<RuteoData> {
        return rawData.mapIndexed { index, row ->
            fun safeGet(idx: Int) = row.getOrElse(idx) { "" }

            val vehiculo = safeGet(0)
            val destino = safeGet(23)
            val fecha = safeGet(36)
            val terminal = safeGet(15)

            val ultimoDato = row.lastOrNull()
            val colorHex = if (ultimoDato?.startsWith("#") == true) ultimoDato else null

            val isCompletado = safeGet(10).isNotEmpty()

            RuteoData(
                id = index.toString(),
                terminal = terminal.ifEmpty { "Sin Terminal" },
                fecha = fecha.ifEmpty { "Sin Fecha" },
                vehiculo = vehiculo,
                destino = destino,
                colorCeldaH = colorHex,
                isCompletado = isCompletado
            )
        }
    }
}