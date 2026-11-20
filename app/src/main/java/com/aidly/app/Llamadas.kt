package com.aidly.app

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import androidx.core.content.ContextCompat

/**
 * "Aidly dice quien llama": cuando entra una llamada, el telefono avisa a Aidly (rol de filtrado de llamadas, sin ser
 * el marcador predeterminado) y Aidly dice en voz alta el nombre de quien llama, o que es un numero que no tiene
 * guardado. La llamada SIEMPRE se deja pasar: Aidly no bloquea ni contesta. El numero se busca solo en el telefono
 * (favoritos y contactos) y no sale a ninguna parte.
 */
object Llamadas {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    fun anunciar(ctx: Context) = prefs(ctx).getBoolean("anunciarLlamadas", true)
    fun fijarAnunciar(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("anunciarLlamadas", v).apply()

    /** El rol de filtrado solo existe desde Android 10. */
    fun disponible() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun rolConcedido(ctx: Context): Boolean =
        disponible() && ctx.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_CALL_SCREENING)

    fun pedirRol(ctx: Context) =
        ctx.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING)

    private fun ultimos(numero: String) = numero.filter { it.isDigit() }.takeLast(10)

    /** Nombre guardado para ese numero (favoritos primero, luego contactos si hay permiso), o null. */
    fun nombre(ctx: Context, numero: String): String? {
        if (numero.isBlank()) return null
        Favoritos.lista(ctx).firstOrNull { ultimos(it.numero) == ultimos(numero) && ultimos(numero).length >= 7 }?.let { return it.nombre }
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return try {
            val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(numero))
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (e: Exception) { SpikeLog.log("LLAMADAS: no pude buscar el contacto (${e::class.simpleName})"); null }
    }

    /** Frase que se dice en voz alta (siempre en espanol, como el resto de la voz de Aidly). */
    fun frase(ctx: Context, numero: String): String {
        val n = nombre(ctx, numero)
        return if (n != null) Idioma.vozTexto(ctx, R.string.llamada_de, n) else Idioma.vozTexto(ctx, R.string.llamada_desconocido)
    }
}

class AidlyCallScreeningService : CallScreeningService() {
    override fun onScreenCall(details: Call.Details) {
        // Primero se responde: dejar pasar siempre (la respuesta debe llegar en pocos segundos).
        respondToCall(details, CallResponse.Builder().build())
        try {
            if (details.callDirection != Call.Details.DIRECTION_INCOMING) return
            if (!Llamadas.anunciar(this)) return
            val numero = details.handle?.schemeSpecificPart.orEmpty()
            val texto = Llamadas.frase(this, numero)
            SpikeLog.log("LLAMADAS: entra una llamada; digo '$texto'")
            val voz = Voz(this)
            val cerrar = Handler(Looper.getMainLooper())
            val limpiar = Runnable { voz.cerrar() }
            cerrar.postDelayed(limpiar, 20_000)
            voz.decir(texto) { cerrar.removeCallbacks(limpiar); voz.cerrar() }
        } catch (e: Exception) {
            SpikeLog.log("LLAMADAS: fallo al anunciar (${e::class.simpleName}: ${e.message})")
        }
    }
}
