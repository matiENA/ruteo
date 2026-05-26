package com.eor.ruteo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eor.ruteo.data.NetworkClient
import com.eor.ruteo.data.ViajeIntegrado
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DiaRuteo(val fecha: String, val sheetId: String)

sealed class UiState {
    object Loading : UiState()
    data class Success(val diasDisponibles: List<DiaRuteo>, val dataRuteo: List<ViajeIntegrado>) : UiState()
    data class Error(val message: String) : UiState()
}

class RuteoViewModel : ViewModel() {

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    private val masterIndexSheetId = "1ny9yOftgyYWfzJFpQ9h8l2T_owDlyMV_HdEgeQ5Gm8E"
    private var listaDias = listOf<DiaRuteo>()

    init {
        cargarIndiceDeDias()
    }

    private fun cargarIndiceDeDias() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val response = NetworkClient.api.getSheetData(masterIndexSheetId, "Indice")
                if (response.success) {
                    listaDias = response.data.map { fila ->
                        DiaRuteo(fecha = fila.getOrElse(0) { "Sin fecha" }, sheetId = fila.getOrElse(1) { "" })
                    }.filter { it.sheetId.isNotEmpty() }

                    if (listaDias.isNotEmpty()) {
                        cargarTodosLosDias(listaDias)
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

    private fun cargarTodosLosDias(dias: List<DiaRuteo>) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val todosLosViajes = mutableListOf<ViajeIntegrado>()

                // 👇 LLAMAMOS DIRECTAMENTE AL MICROSERVICIO LIMPIO Y RÁPIDO
                val deferredDias = dias.map { dia ->
                    async {
                        try {
                            val respuestaApi = NetworkClient.api.getViajesIntegrados(dia.sheetId)
                            if (respuestaApi.success) respuestaApi.data else emptyList()
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                }

                val resultados = deferredDias.awaitAll()
                resultados.forEach { todosLosViajes.addAll(it) }

                val viajesOrdenados = todosLosViajes.sortedByDescending { it.fechaPlanificada.toComparableDate() }

                _uiState.value = UiState.Success(listaDias, viajesOrdenados)

            } catch (e: Exception) {
                _uiState.value = UiState.Error("Error procesando los datos: ${e.message}")
            }
        }
    }

    private fun String.toComparableDate(): Long {
        try {
            val parts = this.split("/")
            if (parts.size == 3) {
                val d = parts[0].padStart(2, '0')
                val m = parts[1].padStart(2, '0')
                val y = parts[2]
                return "$y$m$d".toLong()
            }
        } catch (e: Exception) {}
        return 0L
    }
}