package com.eor.ruteo

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import com.eor.ruteo.R
import com.eor.ruteo.data.ViajeIntegrado

object NotificacionHelper {
    private const val CHANNEL_ID = "canal_estados_criticos"
    private const val CHANNEL_NAME = "Cambios de Estado Críticos"
    private const val CHANNEL_DESC = "Notificaciones de cambios en viajes guardados"

    fun crearCanalNotificaciones(context: Context) {
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
            description = CHANNEL_DESC
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun mostrarNotificacionCambioEstado(context: Context, viaje: ViajeIntegrado) {
        crearCanalNotificaciones(context)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // 👇 DEFENSA EXTREMA: Sanitización preventiva contra tipos de plataforma nulos en segundo plano [txt]
        val numeroUt = (viaje.numeroUt as? String?).orEmpty().trim().ifEmpty { "S/D" }
        val chofer = (viaje.chofer as? String?).orEmpty().trim().ifEmpty { "Chofer S/D" }
        val tractor = (viaje.tractor as? String?).orEmpty().trim().ifEmpty { "S/D" }
        val semi = (viaje.semi as? String?).orEmpty().trim().ifEmpty { "S/D" }
        val nViaje = (viaje.nViaje as? String?).orEmpty().trim().ifEmpty { "S/D" }
        val numDespacho = (viaje.numDespacho as? String?).orEmpty().trim().ifEmpty { "S/N" }

        // Conservamos los valores crudos para la evaluación condicional estricta [txt]
        val rawHorarioVacio = (viaje.horarioVacio as? String?).orEmpty().trim()
        val rawEstadoUt = (viaje.estadoUt as? String?).orEmpty().trim()

        // Título de la alerta unificado con formato simétrico [txt]
        val title = "[UT: $numeroUt] $chofer"

        // 👇 LÓGICA DE PRIORIDAD DEFENSIVA: Fuente de la Verdad Absoluta [txt]
        val lineaEstado = if (rawHorarioVacio.isNotEmpty()) {
            // Prioridad 1: Si hay horario de vacío, el viaje está finalizado sin importar lo que diga estadoUt [txt]
            "VACIO: $rawHorarioVacio"
        } else {
            // Prioridad 2: Si no hay horario de vacío, confiamos en la columna ESTADOUT [txt]
            rawEstadoUt.uppercase().ifEmpty { "PENDIENTE" }
        }

        // Estructuración multilínea simétrica en espejo con el Ticket de la UI [txt]
        val bigTextBody = """
            UT: $numeroUt | $tractor | $semi | Viaje: $nViaje
            TD: $numDespacho
            $lineaEstado
        """.trimIndent()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText("$tractor | $semi | Viaje: $nViaje") // Línea de fallback para vistas colapsadas
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigTextBody)) // BigTextStyle para evitar mutilaciones [txt]
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationId = numDespacho.hashCode()
        notificationManager.notify(notificationId, builder.build())
    }
}