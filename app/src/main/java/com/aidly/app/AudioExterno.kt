package com.aidly.app

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/**
 * Audio externo del dock (parlantes y microfono por el USB-C). Android enruta a un dispositivo de audio USB solo; aqui se elige
 * de forma EXPLICITA para el microfono (la grabacion de la voz) y se muestra en pantalla, para que no haya sorpresas
 * (ya nos paso que el audio salia por un auricular sin avisar). No se usa Bluetooth SCO: baja la calidad de la voz.
 */
object AudioExterno {
    private val TIPOS = setOf(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET)

    fun entrada(ctx: Context): AudioDeviceInfo? =
        ctx.getSystemService(AudioManager::class.java)?.getDevices(AudioManager.GET_DEVICES_INPUTS)?.firstOrNull { it.type in TIPOS }

    fun salida(ctx: Context): AudioDeviceInfo? =
        ctx.getSystemService(AudioManager::class.java)?.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.firstOrNull { it.type in TIPOS }

    /** Nombre para mostrar ("Aidly Dock") o null si el audio es el del telefono. */
    fun nombre(ctx: Context): String? =
        (entrada(ctx) ?: salida(ctx))?.productName?.toString()?.takeIf { it.isNotBlank() } ?: (entrada(ctx) ?: salida(ctx))?.let { "USB" }
}
