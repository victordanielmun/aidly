package com.aidly.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat

/** Recibe las alarmas de los recordatorios y los botones de la notificacion (tomado / posponer / vencido). */
class RecordatorioReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Recordatorios.ACC_SONAR -> sonar(ctx, intent.getStringExtra("id") ?: return)
            Recordatorios.ACC_VENCIDO -> intent.getStringExtra("reg")?.let {
                Recordatorios.marcar(ctx, it, Recordatorios.Estado.OMITIDO)
            }
            Recordatorios.ACC_TOMADO -> intent.getStringExtra("reg")?.let {
                Recordatorios.marcar(ctx, it, Recordatorios.Estado.TOMADO)
            }
            Recordatorios.ACC_POSPONER -> intent.getStringExtra("reg")?.let { Recordatorios.posponer(ctx, it) }
            Recordatorios.ACC_AYUDA -> intent.getStringExtra("reg")?.let {
                Recordatorios.marcar(ctx, it, Recordatorios.Estado.AYUDA)
            }
        }
    }

    private fun sonar(ctx: Context, id: String) {
        val item = Recordatorios.obtener(ctx, id) ?: return
        val reg = Recordatorios.nuevoRegistro(ctx, item)
        Recordatorios.avanzarOEliminar(ctx, item)
        AvisoRecordatorio.mostrar(ctx, reg)
    }
}

/** Arranque del telefono o actualizacion de la app: las alarmas se pierden y hay que volver a ponerlas. */
class ArranqueReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            Recordatorios.reprogramarTodo(ctx)
        }
    }
}

object AvisoRecordatorio {
    fun crearCanal(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(Recordatorios.CANAL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(Recordatorios.CANAL, Idioma.envolver(ctx).getString(R.string.notif_canal), NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Avisos de Aidly para tus medicinas y tareas"
                enableVibration(true)
            },
        )
    }

    private fun accion(ctx: Context, accion: String, regId: String) = PendingIntent.getBroadcast(
        ctx, 0,
        Intent(ctx, RecordatorioReceiver::class.java).setAction(accion)
            .setData(Uri.parse("aidly://${accion.substringAfterLast('.')}/$regId")).putExtra("reg", regId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun mostrar(ctx: Context, reg: Recordatorios.Registro) {
        crearCanal(ctx)
        val txt = Idioma.envolver(ctx)
        val abrir = PendingIntent.getActivity(
            ctx, reg.id.hashCode(),
            Intent(ctx, RecordatorioActivity::class.java).putExtra("reg", reg.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val checkin = reg.tipo == Recordatorios.Tipo.CHECKIN
        val titulo = when (reg.tipo) {
            Recordatorios.Tipo.MEDICINA -> txt.getString(R.string.notif_medicina)
            Recordatorios.Tipo.CHECKIN -> txt.getString(R.string.notif_checkin)
            else -> txt.getString(R.string.notif_recordatorio)
        }
        val texto = if (checkin) txt.getString(R.string.rec_checkin_pregunta) else reg.texto
        val n = NotificationCompat.Builder(ctx, Recordatorios.CANAL)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(abrir)
            .setFullScreenIntent(abrir, true)
            .addAction(0, txt.getString(if (checkin) R.string.rec_estoy_bien else R.string.rec_ya_hice), accion(ctx, Recordatorios.ACC_TOMADO, reg.id))
            .addAction(
                0, txt.getString(if (checkin) R.string.rec_no_bien else R.string.notif_en_10),
                accion(ctx, if (checkin) Recordatorios.ACC_AYUDA else Recordatorios.ACC_POSPONER, reg.id),
            )
            .build()
        try {
            ctx.getSystemService(NotificationManager::class.java).notify(reg.id.hashCode(), n)
        } catch (e: SecurityException) {
            SpikeLog.log("RECORDATORIO: sin permiso de notificaciones")
        }
    }
}
