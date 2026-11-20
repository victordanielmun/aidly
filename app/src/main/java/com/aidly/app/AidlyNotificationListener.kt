package com.aidly.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

/**
 * Lee las notificaciones de mensajeria y correo:
 *  - todas se guardan en la Bandeja (para "leeme los mensajes / el correo"),
 *  - los mensajes ademas se anuncian por voz si el companero esta activo (Voice Loop).
 */
class AidlyNotificationListener : NotificationListenerService() {

    override fun onListenerConnected() {
        SpikeLog.log("Listener de notificaciones conectado")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val pkg = sbn.packageName
        if (pkg == packageName) return
        val (tipo, app) = Bandeja.clasificar(pkg) ?: return   // no es mensajeria ni correo

        val n = sbn.notification
        val extras = n.extras
        val titulo = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val texto = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val grande = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString().orEmpty()
        val resumenGrupo = n.flags and Notification.FLAG_GROUP_SUMMARY != 0
        val fija = n.flags and Notification.FLAG_ONGOING_EVENT != 0
        val reply = n.actions?.firstOrNull { !it.remoteInputs.isNullOrEmpty() }

        SpikeLog.log("NOTIF pkg=$pkg tipo=$tipo titulo='$titulo' resumenGrupo=$resumenGrupo categoria=${n.category}")
        if (resumenGrupo || fija || texto.isBlank()) return

        // Avisos internos de la app ("Eliminando mensajes...", copias de seguridad): un mensaje real es de
        // categoria "msg", tiene accion de respuesta, o es un correo.
        val esMensaje = tipo == Bandeja.Tipo.CORREO || n.category == Notification.CATEGORY_MESSAGE ||
            reply != null || pkg == "com.android.shell"
        if (!esMensaje) { SpikeLog.log("Ignorada (no es un mensaje)"); return }

        // Correo: titulo = remitente, texto = asunto, texto grande = asunto + vista previa.
        val detalle = if (tipo == Bandeja.Tipo.CORREO) grande.removePrefix(texto).trim() else ""
        Bandeja.agregar(
            this,
            Bandeja.Item(
                id = "${sbn.key}|${texto.hashCode()}",
                tipo = tipo, app = app, remitente = titulo, texto = texto, detalle = detalle,
                hora = sbn.postTime, leido = false,
            ),
        )
        SpikeLog.log("BANDEJA: guardado ${tipo.name.lowercase()} de '$titulo' ($app)")

        // Solo los mensajes se anuncian solos; el correo se lee cuando se pide ("leeme el correo").
        if (tipo != Bandeja.Tipo.MENSAJE) return
        if (!AidlyService.running) {
            SpikeLog.log("Companero inactivo: no lo anuncio por voz (queda en la bandeja).")
            return
        }
        SpikeLog.log("Accion de respuesta disponible: ${reply != null}")
        AidlyService.start(this, AidlyService.ACTION_ANNOUNCE, text = texto, sender = titulo, reply = reply)
    }
}
