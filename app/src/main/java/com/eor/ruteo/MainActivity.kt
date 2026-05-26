package com.eor.ruteo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import com.eor.ruteo.data.NetworkClient
import com.eor.ruteo.data.RuteoData



class MainActivity : ComponentActivity() {
    private val viewModel: RuteoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val state by viewModel.uiState.collectAsState()
                    RuteoAppScreen(state = state, viewModel = viewModel)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RuteoAppScreen(state: UiState, viewModel: RuteoViewModel) {
    when (state) {
        is UiState.Loading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is UiState.Error -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Error: ${state.message}", color = MaterialTheme.colorScheme.error)
            }
        }
        is UiState.Success -> {
            var expandedMenu by remember { mutableStateOf(false) }
            var diaSeleccionado by remember { mutableStateOf(state.diasDisponibles.lastOrNull()) }

            Column(modifier = Modifier.fillMaxSize()) {

                // 1. HEADER: Dropdown Menú para cambiar de Día (Como en tu index.html)
                TopAppBar(
                    title = {
                        Text(
                            text = "📅 Día: ${diaSeleccionado?.fecha ?: "Cargando..."}",
                            style = MaterialTheme.typography.titleMedium
                        )
                    },
                    actions = {
                        Box {
                            Button(onClick = { expandedMenu = true }) {
                                Text("Cambiar Día")
                            }
                            DropdownMenu(
                                expanded = expandedMenu,
                                onDismissRequest = { expandedMenu = false }
                            ) {
                                state.diasDisponibles.forEach { dia ->
                                    DropdownMenuItem(
                                        text = { Text(dia.fecha) },
                                        onClick = {
                                            diaSeleccionado = dia
                                            expandedMenu = false
                                            // Llamamos al backend para traer el nuevo día!
                                            viewModel.cargarRuteoDelDia(dia.sheetId)
                                        }
                                    )
                                }
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )

                // 2. TABS POR TERMINAL (Filtrando completados por Ley de Prägnanz)
                val tareasPendientes = state.dataRuteo.filter { !it.isCompletado }

                val terminales = remember(tareasPendientes) {
                    tareasPendientes.map { it.terminal }.distinct().sorted()
                }

                var selectedTabIndex by remember { mutableIntStateOf(0) }

                if (terminales.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        Text("¡Todo completado para este día!", color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTabIndex,
                        edgePadding = 16.dp
                    ) {
                        terminales.forEachIndexed { index, terminal ->
                            val nombreCorto = terminal.split("-").last().trim()
                            Tab(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                text = { Text(text = nombreCorto) }
                            )
                        }
                    }

                    val terminalSeleccionada = terminales.getOrElse(selectedTabIndex) { terminales.first() }

                    // 3. AGRUPAMIENTO POR DESTINO (Con Sticky Headers)
                    // Ya que la fecha se controla arriba, agrupamos la lista por "Destino" para mejor escaneo visual
                    val dataAgrupada = tareasPendientes
                        .filter { it.terminal == terminalSeleccionada }
                        .groupBy { it.destino }

                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        dataAgrupada.forEach { (destino, tareas) ->
                            // HEADER PEGAJOSO (El destino)
                            stickyHeader {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surface)
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = destino.ifEmpty { "Sin destino" },
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            // TARJETAS DEL VIAJE
                            items(tareas) { tarea ->
                                ViajeCard(tarea)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ViajeCard(tarea: RuteoData) { // <--- Aquí debe decir RuteoData
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 👇 Fíjate que dice tarea.vehiculo (todo minúscula)
                Text(text = "Unidad: ${tarea.vehiculo}", fontWeight = FontWeight.Bold)

                // 👇 Fíjate que dice tarea.colorCeldaH
                if (tarea.colorCeldaH != null) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .background(Color(android.graphics.Color.parseColor(tarea.colorCeldaH)))
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            // Agreguemos el destino para que se vea más completa
            Text(text = "Destino: ${tarea.destino}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}