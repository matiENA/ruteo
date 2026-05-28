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

        // Sanitización defensiva de datos a nivel de JVM
        val numeroUt = (viaje.numeroUt as? String?).orEmpty().ifEmpty { "S/D" }
        val chofer = (viaje.chofer as? String?).orEmpty().ifEmpty { "Chofer S/D" }
        val tractor = (viaje.tractor as? String?).orEmpty().ifEmpty { "S/D" }
        val semi = (viaje.semi as? String?).orEmpty().ifEmpty { "S/D" }
        val nViaje = (viaje.nViaje as? String?).orEmpty().ifEmpty { "S/D" }
        val numDespacho = (viaje.numDespacho as? String?).orEmpty().ifEmpty { "S/N" }
        val horarioVacio = (viaje.horarioVacio as? String?).orEmpty().ifEmpty { "Pendiente" }

        // 👇 REQUERIMIENTO 2: Título simétrico con la cabecera física [txt]
        val title = "[UT: $numeroUt] $chofer"

        // Estructuración textual multilínea de alta proximidad [txt]
        val bigTextBody = """
            $tractor | $semi | Viaje: $nViaje
            TD: $numDespacho
            VACIO: $horarioVacio
        """.trimIndent()

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Icono nativo del sistema [txt]
            .setContentTitle(title)
            .setContentText("$tractor | $semi | Viaje: $nViaje") // Resumen de línea única para pantallas bloqueadas
            // 👇 CRÍTICO: Estilo BigTextStyle que evita la mutilación de texto por el SO en la bandeja [txt]
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigTextBody))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationId = numDespacho.hashCode()
        notificationManager.notify(notificationId, builder.build())
    }
}