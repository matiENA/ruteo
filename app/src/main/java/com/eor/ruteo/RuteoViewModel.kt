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
    private var ultimaListaViajes: List<ViajeIntegrado> = emptyList()

    init {
        iniciarSincronizacionEnVivo()
    }

    fun toggleGuardarViaje(idUnico: String) {
        viewModelScope.launch {
            try {
                repository.toggleViajeGuardado(idUnico)
            } catch (e: Exception) {
                // Silenciado defensivo
            }
        }
    }

    // 👇 RUTA DE RENDERIZADO OPTIMIZADA: Doble Fetching en la carga inicial [txt]
    private fun iniciarSincronizacionEnVivo() {
        viewModelScope.launch {
            var esPrimerCarga = true

            while (isActive) {
                try {
                    if (esPrimerCarga) {
                        // 1. 👇 PRIORIDAD 1 (Fetch Rápido): limit=1 para carga inicial sub-segundo (<1s) de viajes activos [txt]
                        val responseFast = NetworkClient.api.getViajesRecientes(masterIndexSheetId, limit = 1)
                        if (responseFast.success) {
                            listaDias = responseFast.diasDisponibles
                            val todosFast = responseFast.data
                            ultimaListaViajes = todosFast

                            val activosFast = todosFast.filter { !it.isCompletado }
                            val finalizadosFast = todosFast.filter { it.isCompletado }

                            _uiState.value = UiState.Success(responseFast.diasDisponibles, activosFast, finalizadosFast)
                            esPrimerCarga = false // Cancelamos flag de inicio
                        }

                        // 2. 👇 PRIORIDAD 2 (Fetch Profundo): limit=10 diferido en segundo plano sin congelar la UI [txt]
                        launch {
                            try {
                                val responseDeep = NetworkClient.api.getViajesRecientes(masterIndexSheetId, limit = 10)
                                if (responseDeep.success) {
                                    listaDias = responseDeep.diasDisponibles
                                    val todosDeep = responseDeep.data
                                    ultimaListaViajes = todosDeep

                                    val activosDeep = todosDeep.filter { !it.isCompletado }
                                    val finalizadosDeep = todosDeep.filter { it.isCompletado }

                                    _uiState.value = UiState.Success(responseDeep.diasDisponibles, activosDeep, finalizadosDeep)
                                }
                            } catch (e: Exception) {
                                // Silenciado para no alterar la UX rápida si el historial falla
                            }
                        }
                    } else {
                        // 3. 👇 POLLING PERIÓDICO: Actualización continua de 30s con historial completo para notificaciones [txt]
                        val response = NetworkClient.api.getViajesRecientes(masterIndexSheetId, limit = 10)
                        if (response.success) {
                            listaDias = response.diasDisponibles
                            val todosLosViajes = response.data

                            // Comparación histórica en memoria para las notificaciones
                            if (ultimaListaViajes.isNotEmpty()) {
                                val guardados = viajesGuardados.value
                                todosLosViajes.forEach { nuevoViaje ->
                                    if (nuevoViaje.idUnico in guardados) {
                                        val viejoViaje = ultimaListaViajes.find { it.idUnico == nuevoViaje.idUnico }
                                        if (viejoViaje != null) {
                                            val estadoNuevo = nuevoViaje.estadoUt.trim()
                                            val estadoViejo = viejoViaje.estadoUt.trim()

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

                            ultimaListaViajes = todosLosViajes

                            val activos = todosLosViajes.filter { !it.isCompletado }
                            val finalizados = todosLosViajes.filter { it.isCompletado }

                            _uiState.value = UiState.Success(response.diasDisponibles, activos, finalizados)
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