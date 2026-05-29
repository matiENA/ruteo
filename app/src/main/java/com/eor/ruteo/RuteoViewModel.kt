package com.eor.ruteo

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.eor.ruteo.data.DiaReciente
import com.eor.ruteo.data.NetworkClient
import com.eor.ruteo.data.ViajeIntegrado
import com.google.firebase.messaging.FirebaseMessaging // 👇 CORREGIDO: API NATIVA COMPILATION-SAFE [txt]
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
                val viaje = _viajesRaw.value.find { it.idUnico == idUnico }
                if (viaje != null && viaje.numeroUt.isNotEmpty()) {
                    val isGuardadoActualmente = idUnico in viajesGuardados.value
                    val topic = "ut_${viaje.numeroUt}"

                    if (isGuardadoActualmente) {
                        // 👇 CORREGIDO: Uso de FirebaseMessaging nativo libre de errores de classpath [txt]
                        FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    Log.d("FCM_SUBSCRIBE", "Desuscripción exitosa de: $topic")
                                }
                            }
                    } else {
                        // 👇 CORREGIDO: Uso de FirebaseMessaging nativo libre de errores de classpath [txt]
                        FirebaseMessaging.getInstance().subscribeToTopic(topic)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    Log.d("FCM_SUBSCRIBE", "Suscripción exitosa a: $topic")
                                }
                            }
                    }
                }

                repository.toggleViajeGuardado(idUnico)
            } catch (e: Exception) {
                // Silenciado defensivo de persistencia local
            }
        }
    }

    private fun iniciarSincronizacionEnVivo() {
        viewModelScope.launch {
            var esPrimerCarga = true

            while (isActive) {
                try {
                    if (esPrimerCarga) {
                        val responseFast = NetworkClient.api.getViajesRecientes(masterIndexSheetId)
                        if (responseFast.success) {
                            _diasDisponibles.value = responseFast.diasDisponibles
                            _viajesRaw.value = responseFast.data
                            ultimaListaViajes = responseFast.data
                            _isLoading.value = false
                            _errorMessage.value = null
                            esPrimerCarga = false
                        }
                    } else {
                        val response = NetworkClient.api.getViajesRecientes(masterIndexSheetId)
                        if (response.success) {
                            val todosLosViajes = response.data

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