package com.eor.ruteo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eor.ruteo.data.ViajeIntegrado

@Composable
fun ViajeHistorialCard(
    viaje: ViajeIntegrado,
    isGuardado: Boolean,
    onToggleGuardar: () -> Unit
) {
    // Sanitización defensiva de tipos de plataforma de la JVM [txt]
    val nViaje = remember(viaje.nViaje) { (viaje.nViaje as? String?).orEmpty() }
    val numeroUt = remember(viaje.numeroUt) { (viaje.numeroUt as? String?).orEmpty() }
    val chofer = remember(viaje.chofer) { (viaje.chofer as? String?).orEmpty() }
    val tractor = remember(viaje.tractor) { (viaje.tractor as? String?).orEmpty() }
    val semi = remember(viaje.semi) { (viaje.semi as? String?).orEmpty() }
    val horarioVacio = remember(viaje.horarioVacio) { (viaje.horarioVacio as? String?).orEmpty() }

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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Viaje: ${nViaje.ifEmpty { "S/D" }}",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "UT: ${numeroUt.ifEmpty { "S/D" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = chofer.ifEmpty { "Chofer S/D" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "Placas: ${tractor.ifEmpty { "S/D" }} | ${semi.ifEmpty { "S/D" }}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Entregado: $horarioVacio",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            IconButton(
                onClick = onToggleGuardar,
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = if (isGuardado) Icons.Default.Star else StarBorderIcon,
                    contentDescription = "Guardar viaje",
                    tint = if (isGuardado) {
                        Color(0xFFFFD700)
                    } else {
                        MaterialTheme.colorScheme.onSurface // Sólido de alto contraste [txt]
                    },
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

// 👇 VECTOR DE SOPORTE: Declaración privada para evitar colisiones de classpath o ambigüedad de imports [txt]
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
            pathFillType = PathFillType.EvenOdd // Mapeado correcto de tipo de empaque [txt]
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