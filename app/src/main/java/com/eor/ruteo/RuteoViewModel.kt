package com.eor.ruteo

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eor.ruteo.data.DiaReciente
import com.eor.ruteo.data.NetworkClient
import com.eor.ruteo.data.ViajeIntegrado
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
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

    // 👇 NUEVO: Historial en memoria del último ciclo para rastrear cambios en caliente
    private var ultimaListaViajes: List<ViajeIntegrado> = emptyList()

    init {
        iniciarSincronizacionEnVivo()
    }

    fun toggleGuardarViaje(idUnico: String) {
        viewModelScope.launch {
            try {
                repository.toggleViajeGuardado(idUnico)
            } catch (e: Exception) {
                // Silenciado defensivo de persistencia local
            }
        }
    }

    private fun iniciarSincronizacionEnVivo() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    val response = NetworkClient.api.getViajesRecientes(masterIndexSheetId)
                    if (response.success) {
                        listaDias = response.diasDisponibles
                        val todosLosViajes = response.data

                        // 👇 DETECCIÓN DE CAMBIOS EN CALIENTE: Solo evalúa si ya existía una carga previa
                        if (ultimaListaViajes.isNotEmpty()) {
                            val guardados = viajesGuardados.value

                            todosLosViajes.forEach { nuevoViaje ->
                                // Regla: Evaluar únicamente los marcados para seguimiento (Favoritos) [txt]
                                if (nuevoViaje.idUnico in guardados) {
                                    val viejoViaje = ultimaListaViajes.find { it.idUnico == nuevoViaje.idUnico }
                                    if (viejoViaje != null) {
                                        val estadoNuevo = nuevoViaje.estadoUt.trim()
                                        val estadoViejo = viejoViaje.estadoUt.trim()

                                        // Dispara alerta solo ante cambios de estado reales y no vacíos
                                        if (estadoNuevo != estadoViejo && estadoNuevo.isNotEmpty()) {
                                            NotificacionHelper.mostrarNotificacionCambioEstado(
                                                getApplication(),
                                                nuevoViaje
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Conserva la referencia actual para el próximo ciclo de pooling
                        ultimaListaViajes = todosLosViajes

                        val activos = todosLosViajes.filter { !it.isCompletado }
                        val finalizados = todosLosViajes.filter { it.isCompletado }

                        _uiState.value = UiState.Success(response.diasDisponibles, activos, finalizados)
                    } else {
                        if (_uiState.value is UiState.Loading) {
                            _uiState.value = UiState.Error("No se pudieron consolidar los datos de ruteo.")
                        }
                    }
                } catch (e: Exception) {
                    if (_uiState.value is UiState.Loading) {
                        _uiState.value = UiState.Error("Error de comunicación: ${e.message}")
                    }
                }
                delay(30000)
            }
        }
    }
}