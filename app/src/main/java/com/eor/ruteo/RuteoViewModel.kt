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
import kotlinx.coroutines.flow.combine
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

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _diasDisponibles = MutableStateFlow<List<DiaReciente>>(emptyList())
    private val _viajesRaw = MutableStateFlow<List<ViajeIntegrado>>(emptyList())
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(true)

    val viajesGuardados: StateFlow<Set<String>> = repository.viajesGuardados
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )

    // Combinador de flujo reactivo en vivo de alta velocidad
    val uiState: StateFlow<UiState> = combine(
        _isLoading,
        _errorMessage,
        _diasDisponibles,
        _viajesRaw,
        _searchQuery
    ) { loading, error, dias, viajes, query ->
        when {
            error != null -> UiState.Error(error)
            loading -> UiState.Loading
            else -> {
                val viajesFiltrados = if (query.isBlank()) {
                    viajes
                } else {
                    val q = query.trim()
                    viajes.filter { v ->
                        v.chofer.contains(q, ignoreCase = true) ||
                                v.numeroUt.contains(q, ignoreCase = true) ||
                                v.tractor.contains(q, ignoreCase = true) ||
                                v.semi.contains(q, ignoreCase = true) ||
                                v.numDespacho.contains(q, ignoreCase = true)
                    }
                }

                val activos = viajesFiltrados.filter { !it.isCompletado }
                val finalizados = viajesFiltrados.filter { it.isCompletado }

                UiState.Success(dias, activos, finalizados)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = UiState.Loading
    )

    private val masterIndexSheetId = "1ny9yOftgyYWfzJFpQ9h8l2T_owDlyMV_HdEgeQ5Gm8E"
    private var ultimaListaViajes: List<ViajeIntegrado> = emptyList()

    init {
        iniciarSincronizacionEnVivo()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
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

    // 👇 REFACTORIZADO: Bucle simple de polling que consume la caché instantánea del BFF [txt]
    private fun iniciarSincronizacionEnVivo() {
        viewModelScope.launch {
            while (isActive) {
                try {
                    val response = NetworkClient.api.getViajesRecientes(masterIndexSheetId)
                    if (response.success) {
                        val todosLosViajes = response.data

                        // Comparación histórica para lanzar notificaciones de favoritos
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

                        _diasDisponibles.value = response.diasDisponibles
                        _viajesRaw.value = todosLosViajes
                        ultimaListaViajes = todosLosViajes
                        _isLoading.value = false
                        _errorMessage.value = null
                    } else {
                        if (_viajesRaw.value.isEmpty()) {
                            _errorMessage.value = "No se pudieron consolidar los datos de ruteo."
                            _isLoading.value = false
                        }
                    }
                } catch (e: Exception) {
                    if (_viajesRaw.value.isEmpty()) {
                        _errorMessage.value = "Error de comunicación: ${e.message}"
                        _isLoading.value = false
                    }
                }
                delay(30000)
            }
        }
    }
}