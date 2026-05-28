package com.eor.ruteo

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.eor.ruteo.data.ParadaViaje
import com.eor.ruteo.data.ViajeIntegrado

class MainActivity : ComponentActivity() {
    private val viewModel: RuteoViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permiso concedido
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        solicitarPermisosNotificaciones()

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    val state by viewModel.uiState.collectAsState()
                    RuteoAppScreen(state = state, viewModel = viewModel)
                }
            }
        }
    }

    private fun solicitarPermisosNotificaciones() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun RuteoAppScreen(state: UiState, viewModel: RuteoViewModel) {
    var showHistorial by remember { mutableStateOf(false) }

    // 👇 GESTALT - Continuidad: La TopAppBar se extrae de los estados para evitar parpadeos superiores [txt]
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Text(
                    text = if (showHistorial) "HISTORIAL DE VIAJES" else "RUTEO OPERATIVO",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                if (showHistorial) {
                    IconButton(onClick = { showHistorial = false }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            },
            actions = {
                if (!showHistorial) {
                    IconButton(onClick = { showHistorial = true }) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.List, contentDescription = "Historial")
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        )

        when (state) {
            is UiState.Loading -> {
                // 👇 SKELETON LOADER INICIAL: Reduce la incertidumbre táctil mientras impacta el Fetch Rápido [txt]
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(4) {
                        SkeletonViajeCard()
                    }
                }
            }
            is UiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                }
            }
            is UiState.Success -> {
                val viajesGuardados by viewModel.viajesGuardados.collectAsState()

                if (showHistorial) {
                    // ==========================================
                    // 👇 VISTA HISTORIAL (isCompletado == true)
                    // ==========================================
                    if (state.viajesFinalizados.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No se registran viajes finalizados", color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        val dataAgrupada = state.viajesFinalizados.groupBy { it.fechaPlanificada }
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
                                            text = "📅 Entregado: $fechaPlan",
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                items(tareas, key = { it.idUnico }) { tarea ->
                                    val isGuardado = tarea.idUnico in viajesGuardados
                                    Box(modifier = Modifier.animateItemPlacement()) {
                                        ViajeHistorialCard(
                                            viaje = tarea,
                                            isGuardado = isGuardado,
                                            onToggleGuardar = { viewModel.toggleGuardarViaje(tarea.idUnico) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // ==========================================
                    // 👇 PANTALLA PRINCIPAL: VIAJES ACTIVOS (isCompletado == false)
                    // ==========================================
                    val terminalesFijas = listOf("⭐ Guardados", "Plaza Huincul", "Dock Sud", "Sin Terminal")
                    var selectedTabIndex by remember { mutableIntStateOf(0) }

                    ScrollableTabRow(selectedTabIndex = selectedTabIndex, edgePadding = 16.dp) {
                        terminalesFijas.forEachIndexed { index, terminal ->
                            val count = if (terminal == "⭐ Guardados") {
                                state.viajesActivos.count { it.idUnico in viajesGuardados }
                            } else {
                                state.viajesActivos.count { it.terminalOrigen == terminal }
                            }

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
                    val viajesFiltrados = if (terminalSeleccionada == "⭐ Guardados") {
                        state.viajesActivos.filter { it.idUnico in viajesGuardados }
                    } else {
                        state.viajesActivos.filter { it.terminalOrigen == terminalSeleccionada }
                    }

                    if (viajesFiltrados.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                            val mensaje = if (terminalSeleccionada == "⭐ Guardados") {
                                "No posees viajes en seguimiento local"
                            } else {
                                "No hay viajes activos para $terminalSeleccionada"
                            }
                            Text(mensaje, color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        val dataAgrupada = viajesFiltrados.groupBy { it.fechaPlanificada }
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
                                    val isGuardado = tarea.idUnico in viajesGuardados
                                    Box(modifier = Modifier.animateItemPlacement()) {
                                        ViajeCardExpandible(
                                            viaje = tarea,
                                            isGuardado = isGuardado,
                                            onGuardarToggle = { viewModel.toggleGuardarViaje(tarea.idUnico) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// 👇 NUEVO: Componente estructural de Skeleton para mitigar la recreación de layouts [txt]
@Composable
fun SkeletonViajeCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Bloque para simular el nombre del Chofer
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .height(18.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(8.dp))
                // Bloque para simular Tractor | Semi
                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .height(14.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.height(6.dp))
                // Bloque para simular Viaje | Llegada
                Box(
                    modifier = Modifier
                        .width(200.dp)
                        .height(12.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(4.dp))
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Bloque para simular el número de UT gigante (Troquelado "Stub") [txt]
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(8.dp))
            )
        }
    }
}

@Composable
fun ViajeCardExpandible(
    viaje: ViajeIntegrado,
    isGuardado: Boolean,
    onGuardarToggle: () -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    val numeroUt = remember(viaje.numeroUt) { (viaje.numeroUt as? String?).orEmpty() }
    val chofer = remember(viaje.chofer) { (viaje.chofer as? String?).orEmpty() }
    val tractor = remember(viaje.tractor) { (viaje.tractor as? String?).orEmpty() }
    val semi = remember(viaje.semi) { (viaje.semi as? String?).orEmpty() }
    val nViaje = remember(viaje.nViaje) { (viaje.nViaje as? String?).orEmpty() }
    val llegadaPlanta = remember(viaje.llegadaPlanta) { (viaje.llegadaPlanta as? String?).orEmpty() }
    val numDespacho = remember(viaje.numDespacho) { (viaje.numDespacho as? String?).orEmpty() }
    val ultimoTracking = remember(viaje.ultimoTracking) { (viaje.ultimoTracking as? String?).orEmpty() }
    val horarioVacio = remember(viaje.horarioVacio) { (viaje.horarioVacio as? String?).orEmpty() }
    val cisternadoReal = remember(viaje.cisternadoReal) { (viaje.cisternadoReal as? String?).orEmpty() }
    val colorHexA = remember(viaje.colorHexA) { viaje.colorHexA as? String? }
    val colorHexHx = remember(viaje.colorHexHx) { viaje.colorHexHx as? String? }
    val isCompletado = remember(viaje.isCompletado) { (viaje.isCompletado as? Boolean) ?: false }

    val pastelPalette = remember {
        listOf(
            Color(0xFFF1F8E9), // Verde suave
            Color(0xFFE3F2FD), // Azul suave
            Color(0xFFFFFDE7), // Amarillo suave
            Color(0xFFF3E5F5), // Lila suave
            Color(0xFFE8F5E9), // Menta suave
            Color(0xFFECEFF1), // Gris azulado suave
            Color(0xFFFFF3E0), // Melocotón suave
            Color(0xFFE0F7FA)  // Cian suave
        )
    }

    val colorMap = remember(viaje.paradas) {
        val uniqueDestinations = viaje.paradas.map { (it.destino as? String?).orEmpty() }.distinct()
        uniqueDestinations.mapIndexed { index, destino ->
            destino to pastelPalette[index % pastelPalette.size]
        }.toMap()
    }

    val defaultGradientStart = MaterialTheme.colorScheme.surfaceContainerLow
    val defaultGradientEnd = MaterialTheme.colorScheme.surfaceContainerHigh

    val parsedColorStart = parsearColorHexSeguro(colorHexA, defaultGradientStart)
    val parsedColorEnd = parsearColorHexSeguro(colorHexHx, defaultGradientEnd)

    val gradientBrush = remember(colorHexA, colorHexHx) {
        androidx.compose.ui.graphics.Brush.horizontalGradient(
            colors = listOf(parsedColorStart, parsedColorEnd)
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { isExpanded = !isExpanded }
            .then(
                if (isGuardado) {
                    Modifier.border(
                        width = 2.dp,
                        color = Color(0xFFFFC107),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .background(gradientBrush)
                .padding(16.dp)
        ) {
            Column {
                if (isCompletado) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = chofer.ifEmpty { "Chofer S/D" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "FINALIZADO: $horarioVacio",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp)
                        ) {
                            Text(
                                text = numeroUt.ifEmpty { "S/D" },
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = onGuardarToggle) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Guardar viaje",
                                    tint = if (isGuardado) Color(0xFFFFD700) else Color(0xFFD3D3D3),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = chofer.ifEmpty { "Chofer S/D" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${tractor.ifEmpty { "S/D" }} | ${semi.ifEmpty { "S/D" }}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Viaje: $nViaje | Llegada: $llegadaPlanta",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "TD: ${numDespacho.ifEmpty { "S/N" }}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(start = 16.dp)
                        ) {
                            Text(
                                text = numeroUt.ifEmpty { "S/D" },
                                style = MaterialTheme.typography.displaySmall,
                                fontWeight = FontWeight.Black,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = onGuardarToggle) {
                                Icon(
                                    imageVector = if (isGuardado) Icons.Default.Star else StarBorderIcon,
                                    contentDescription = "Guardar viaje",
                                    tint = if (isGuardado) Color(0xFFFFD700) else Color(0xFFD3D3D3),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
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

                    if (ultimoTracking.isNotEmpty() && !isCompletado) {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.CenterEnd
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = ultimoTracking,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }
                }

                // Sección del Acordeón Desplegable (Carga limpia de bloques por cliente)
                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) {
                        viaje.paradas.forEachIndexed { index, parada ->
                            if (index > 0) {
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            val destino = (parada.destino as? String?).orEmpty()
                            val producto = (parada.producto as? String?).orEmpty()
                            val cantidad = (parada.cantidad as? String?).orEmpty()
                            val cisternado = (parada.cisternado as? String?).orEmpty()
                            val direccion = (parada.direccion as? String?).orEmpty()

                            val containerColor = colorMap[destino] ?: MaterialTheme.colorScheme.surfaceVariant

                            Surface(
                                color = containerColor,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                                border = androidx.compose.foundation.BorderStroke(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                                )
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp)
                                ) {
                                    val destinoFiltrado = remember(destino) {
                                        destino.split(" - ").last().trim()
                                    }

                                    // 1. Cabecera de Ubicación (📍 Destino + Dirección)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = "Ubicación",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = destinoFiltrado,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    if (direccion.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Dir.: $direccion",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    // 2. Fila de Identidad del Producto (Únicamente la insignia de combustible)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ProductBadge(producto = producto)
                                    }

                                    // 3. Fila de Física de Carga: Separación de compartimentos de volumen
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Lado Izquierdo: Compartimentos del Cisternado (Chips dinámicos separados)
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (cisternado.isNotEmpty()) {
                                                val compartimentos = remember(cisternado) {
                                                    cisternado.split(";").filter { it.isNotBlank() }
                                                }
                                                compartimentos.forEach { comp ->
                                                    Surface(
                                                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                        shape = RoundedCornerShape(4.dp),
                                                        border = androidx.compose.foundation.BorderStroke(
                                                            width = 1.dp,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                                                        )
                                                    ) {
                                                        Text(
                                                            text = comp.trim(),
                                                            style = MaterialTheme.typography.labelMedium,
                                                            fontWeight = FontWeight.Bold,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                        )
                                                    }
                                                }
                                            } else {
                                                Surface(
                                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                    shape = RoundedCornerShape(4.dp)
                                                ) {
                                                    Text(
                                                        text = "Sin comp.",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = cantidad.ifEmpty { "-" },
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (cisternadoReal.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.primary,
                                thickness = 1.5.dp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "🚚 CISTERNADO",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = cisternadoReal,
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
// 👇 FUNCIONES AUXILIARES GLOBALES
// =================================================================================================

@Composable
fun ProductBadge(producto: String?) {
    val safeProducto = (producto as? String?).orEmpty()
    val uppercaseProd = safeProducto.uppercase()

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
            text = safeProducto,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun DatoExtraUI(label: String, value: String, modifier: Modifier = Modifier) {
    val safeValue = (value as? String?).orEmpty()
    Column(modifier = modifier.padding(end = 8.dp)) {
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = safeValue.ifEmpty { "-" }, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}

private fun parsearColorHexSeguro(hex: String?, fallback: Color): Color {
    val safeHex = hex as? String?
    if (safeHex.isNullOrBlank()) return fallback
    return try {
        Color(android.graphics.Color.parseColor(safeHex))
    } catch (e: Exception) {
        fallback
    }
}

private val StarBorderIcon: ImageVector
    get() {
        val existing = _starBorderIcon
        if (existing != null) return existing
        val newVector = ImageVector.Builder(
            name = "StarBorder",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd
        ) {
            moveTo(22f, 9.24f)
            lineTo(14.81f, 8.62f)
            lineTo(12f, 2f)
            lineTo(9.19f, 8.63f)
            lineTo(2f, 9.24f)
            lineTo(7.46f, 13.97f)
            lineTo(5.82f, 21f)
            lineTo(12f, 17.27f)
            lineTo(18.18f, 21f)
            lineTo(16.55f, 13.97f)
            lineTo(22f, 9.24f)
            close()

            moveTo(12f, 15.4f)
            lineTo(8.24f, 17.67f)
            lineTo(9.24f, 13.39f)
            lineTo(5.92f, 10.51f)
            lineTo(10.3f, 10.13f)
            lineTo(12f, 6.1f)
            lineTo(13.71f, 10.14f)
            lineTo(18.09f, 10.52f)
            lineTo(14.77f, 13.4f)
            lineTo(15.77f, 17.68f)
            lineTo(12f, 15.4f)
            close()
        }.build()
        _starBorderIcon = newVector
        return newVector
    }

private var _starBorderIcon: ImageVector? = null