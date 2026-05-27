package com.eor.ruteo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eor.ruteo.data.DiaReciente
import com.eor.ruteo.data.NetworkClient
import com.eor.ruteo.data.ViajeIntegrado
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class UiState {
    object Loading : UiState()
    data class Success(
        val diasDisponibles: List<DiaReciente>,
        val viajesActivos: List<ViajeIntegrado>,
        val viajesFinalizados: List<ViajeIntegrado>
    ) : UiState()
    data class Error(val message: String) : UiState()
}

class RuteoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ViajesGuardadosRepository(application)

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState

    val viajesGuardados: StateFlow<Set<String>> = repository.viajesGuardados
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    private val masterIndexSheetId = "1ny9yOftgyYWfzJFpQ9h8l2T_owDlyMV_HdEgeQ5Gm8E"
    private var listaDias = listOf<DiaReciente>()

    init {
        cargarTodoDesdeBff()
    }

    fun toggleGuardarViaje(idUnico: String) {
        viewModelScope.launch {
            try {
                repository.toggleViajeGuardado(idUnico)
            } catch (e: Exception) {
                // Manejo de errores de almacenamiento local silencioso
            }
        }
    }

    private fun cargarTodoDesdeBff() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val response = NetworkClient.api.getViajesRecientes(masterIndexSheetId)
                if (response.success) {
                    listaDias = response.diasDisponibles
                    val todosLosViajes = response.data

                    // 👇 REGLA DE NEGOCIO: Separar los viajes globales en Activos e Historial
                    val activos = todosLosViajes.filter { !it.isCompletado }
                    val finalizados = todosLosViajes.filter { it.isCompletado }

                    _uiState.value = UiState.Success(response.diasDisponibles, activos, finalizados)
                } else {
                    _uiState.value = UiState.Error("No se pudieron consolidar los datos de ruteo.")
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error("Error de comunicación: ${e.message}")
            }
        }
    }
}