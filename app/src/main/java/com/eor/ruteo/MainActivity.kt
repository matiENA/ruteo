package com.eor.ruteo

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eor.ruteo.data.ParadaViaje
import com.eor.ruteo.data.ViajeIntegrado


class MainActivity : ComponentActivity() {
    private val viewModel: RuteoViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val state by viewModel.uiState.collectAsState()
                    RuteoAppScreen(state = state)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RuteoAppScreen(state: UiState) {
    when (state) {
        is UiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        is UiState.Error -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error) }
        is UiState.Success -> {
            Column(modifier = Modifier.fillMaxSize()) {

                TopAppBar(
                    title = { Text("RUTEO OPERATIVO", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )

                val terminalesFijas = listOf("Plaza Huincul", "Dock Sud", "Sin Terminal")
                var selectedTabIndex by remember { mutableIntStateOf(0) }

                val tareasPendientes = state.dataRuteo.filter { !it.isCompletado }

                // Indicador visual numérico en las pestañas para reducir la carga de navegación
                ScrollableTabRow(selectedTabIndex = selectedTabIndex, edgePadding = 16.dp) {
                    terminalesFijas.forEachIndexed { index, terminal ->
                        val count = tareasPendientes.count { it.terminalOrigen == terminal }
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = terminal, fontWeight = FontWeight.Bold)
                                    if (count > 0) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                            Text(
                                                text = count.toString(),
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                fontSize = 10.sp,
                                                modifier = Modifier.padding(horizontal = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                    }
                }

                val terminalSeleccionada = terminalesFijas[selectedTabIndex]
                val viajesDeEstaTerminal = tareasPendientes.filter { it.terminalOrigen == terminalSeleccionada }

                if (viajesDeEstaTerminal.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No hay viajes para $terminalSeleccionada", color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    val dataAgrupada = viajesDeEstaTerminal.groupBy { it.fechaPlanificada }

                    LazyColumn(modifier = Modifier.fillMaxSize().weight(1f)) {
                        dataAgrupada.forEach { (fechaPlan, tareas) ->
                            stickyHeader {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                ) {
                                    Text(
                                        text = "📅 Planificado: $fechaPlan",
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            items(tareas, key = { it.idUnico }) { tarea ->
                                ViajeCardExpandible(tarea)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ViajeCardExpandible(viaje: ViajeIntegrado) {
    var isExpanded by remember { mutableStateOf(false) }

    // Colores por defecto del tema para cuando no hay degradado definido en el Excel
    val defaultGradientStart = MaterialTheme.colorScheme.surfaceContainerLow
    val defaultGradientEnd = MaterialTheme.colorScheme.surfaceContainerHigh

    val parsedColorStart = parsearColorHexSeguro(viaje.colorHexA, defaultGradientStart)
    val parsedColorEnd = parsearColorHexSeguro(viaje.colorHexHx, defaultGradientEnd)

    // Gradiente horizontal
    val gradientBrush = remember(viaje.colorHexA, viaje.colorHexHx) {
        androidx.compose.ui.graphics.Brush.horizontalGradient(
            colors = listOf(parsedColorStart, parsedColorEnd)
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .background(gradientBrush)
                .padding(16.dp)
        ) {
            Column {
                // Fila Principal: Vista Colapsada del Viaje
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Lado izquierdo: N° Unidad Gigante
                    Text(
                        text = viaje.numeroUt.ifEmpty { "S/D" },
                        fontSize = 38.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .widthIn(min = 65.dp),
                        style = androidx.compose.ui.text.TextStyle(
                            lineHeight = 40.sp,
                            letterSpacing = (-1).sp
                        )
                    )

                    // Centro: Información consolidada del viaje
                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = viaje.chofer.ifEmpty { "Chofer S/D" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${viaje.tractor.ifEmpty { "S/D" }} | ${viaje.semi.ifEmpty { "S/D" }}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "TD: ${viaje.numDespacho.ifEmpty { "S/N" }}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Fila Inferior de Control
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Expandir detalles",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    if (viaje.ultimoTracking.isNotEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = viaje.ultimoTracking,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Sección del Acordeón Desplegable (Carga de Paradas en Bloques Coloreados)
                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        viaje.paradas.forEachIndexed { index, parada ->
                            var isRead by remember { mutableStateOf(false) }

                            val basePastelColor = getProductPastelColor(parada.producto)
                            val containerColor = if (isRead) basePastelColor.copy(alpha = 0.5f) else basePastelColor

                            Surface(
                                color = containerColor,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                border = if (isRead) null else androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Checkbox(
                                            checked = isRead,
                                            onCheckedChange = { isRead = it },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = MaterialTheme.colorScheme.primary,
                                                uncheckedColor = MaterialTheme.colorScheme.outline
                                            )
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Marcar como leído",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (isRead) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    val destinoFiltrado = remember(parada.destino) {
                                        parada.destino.split(" - ").last().trim()
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = "Ubicación",
                                            tint = if (isRead) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                                            else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = destinoFiltrado,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = if (isRead) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                            else MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(12.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.Top
                                    ) {
                                        Column(modifier = Modifier.weight(1.2f)) {
                                            Text(
                                                text = "PRODUCTO",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            ProductBadge(producto = parada.producto)
                                        }

                                        Column(modifier = Modifier.weight(0.8f)) {
                                            Text(
                                                text = "CANTIDAD",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = parada.cantidad.ifEmpty { "-" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isRead) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    if (parada.cisternado.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(10.dp))
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = "CISTERNADO SUGERIDO",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                            )
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = parada.cisternado,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isRead) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (viaje.cisternadoReal.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.primary,
                                thickness = 1.5.dp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "🚚 CISTERNADO REAL (Total Unidad)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = viaje.cisternadoReal,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                } // <--- FIN AnimatedVisibility
            } // <--- FIN Column
        } // <--- FIN Box
    } // <--- FIN Card
} // <--- FIN ViajeCardExpandible


// =================================================================================================
// 👇 FUNCIONES AUXILIARES GLOBALES (Declaradas al nivel del archivo, fuera de ViajeCardExpandible)
// =================================================================================================

@Composable
fun getProductPastelColor(producto: String): Color {
    val uppercaseProd = producto.uppercase()
    return when {
        uppercaseProd.contains("SUPER") -> Color(0xFFF1F8E9) // Verde Pastel
        uppercaseProd.contains("INFINIA DIESEL") -> Color(0xFFFBE9E7) // Naranja/Rojo Pastel
        uppercaseProd.contains("INFINIA") -> Color(0xFFE3F2FD) // Azul Pastel
        uppercaseProd.contains("DIESEL") || uppercaseProd.contains("500") -> Color(0xFFECEFF1) // Gris Pastel
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }
}

@Composable
fun ProductBadge(producto: String) {
    val uppercaseProd = producto.uppercase()

    val (backColor, textColor) = when {
        uppercaseProd.contains("SUPER") -> {
            Color(0xFFDCEDC8) to Color(0xFF2E7D32)
        }
        uppercaseProd.contains("INFINIA DIESEL") -> {
            Color(0xFFFFCCBC) to Color(0xFFD84315)
        }
        uppercaseProd.contains("INFINIA") -> {
            Color(0xFFBBDEFB) to Color(0xFF1565C0)
        }
        uppercaseProd.contains("DIESEL") || uppercaseProd.contains("500") -> {
            Color(0xFFCFD8DC) to Color(0xFF37474F)
        }
        else -> {
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Surface(
        color = backColor,
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, textColor.copy(alpha = 0.2f)),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = producto,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun DatoExtraUI(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(end = 8.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value.ifEmpty { "-" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

// Analizador de color seguro que mitiga posibles caídas de parsing de UI
private fun parsearColorHexSeguro(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        fallback
    }
}