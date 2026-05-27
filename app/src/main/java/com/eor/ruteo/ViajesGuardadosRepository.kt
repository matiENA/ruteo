package com.eor.ruteo

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Extensión delegada para asegurar una única instancia Singleton de DataStore a nivel de Context
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "viajes_guardados_prefs")

class ViajesGuardadosRepository(private val context: Context) {

    companion object {
        // Llave de tipo StringSet para guardar de forma única las llaves de despacho (idUnico)
        private val KEY_VIAJES_GUARDADOS = stringSetPreferencesKey("viajes_guardados_ids")
    }

    // Expone un Flow asíncrono observable reactivo desde la UI
    val viajesGuardados: Flow<Set<String>> = context.dataStore.data
        .map { preferences ->
            preferences[KEY_VIAJES_GUARDADOS] ?: emptySet()
        }

    // Escritura transaccional protegida contra mutaciones concurrentes
    suspend fun toggleViajeGuardado(idUnico: String) {
        context.dataStore.edit { preferences ->
            val actual = preferences[KEY_VIAJES_GUARDADOS] ?: emptySet()
            val nuevo = if (actual.contains(idUnico)) {
                actual - idUnico
            } else {
                actual + idUnico
            }
            preferences[KEY_VIAJES_GUARDADOS] = nuevo
        }
    }
}