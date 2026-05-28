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

    // 👇 OPTIMIZADO: Eliminado chequeo innecesario de SDK_INT >= 26 ya que tu minSDK es >= 26 [txt]
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

        val title = "[UT: ${viaje.numeroUt}] ${viaje.tractor} ➔ ${viaje.estadoUt.uppercase()}"
        val body = "TD: ${viaje.numDespacho} | Viaje: ${viaje.nViaje} | Semi: ${viaje.semi}"

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        val notificationId = viaje.numDespacho.hashCode()
        notificationManager.notify(notificationId, builder.build())
    }
}