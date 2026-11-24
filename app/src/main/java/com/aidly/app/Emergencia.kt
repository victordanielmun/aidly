package com.aidly.app

import android.content.Context
import android.content.res.Resources
import android.telephony.TelephonyManager
import java.util.Locale

/** Pais del telefono (SIM, red o region del sistema) y numero de emergencias de ese pais (`paises` de idiomas.json). */
object Emergencia {
    fun pais(ctx: Context): String {
        val tm = ctx.getSystemService(TelephonyManager::class.java)
        val deRed = listOf(tm?.simCountryIso, tm?.networkCountryIso).firstOrNull { !it.isNullOrBlank() }
        // La region del SISTEMA (no Locale.getDefault(), que Aidly cambia segun el idioma elegido).
        val region = Resources.getSystem().configuration.locales.let { if (it.isEmpty) "" else it[0].country }
        return (deRed ?: region).uppercase(Locale.ROOT)
    }

    /** Si no se conoce el pais, el comodin del JSON (112: la mayoria de los telefonos GSM lo redirigen al servicio local). */
    fun numero(ctx: Context): String =
        Preferencias.numeroEmergencia(ctx).ifBlank { numeroDelPais(ctx) }

    fun numeroDelPais(ctx: Context): String = Idioma.datosPais(ctx).optString("emergencia", "112").ifBlank { "112" }

    /** Telefono valido para marcar: solo digitos (y un + al comienzo), de 3 a 15 digitos; null si no lo es. Quita espacios, guiones y parentesis. */
    fun limpiarNumero(s: String): String? {
        val t = s.trim().filter { it.isDigit() || it == '+' }
        val plus = t.startsWith("+")
        val d = t.filter { it.isDigit() }
        if (t.count { it == '+' } > 1 || (t.contains('+') && !plus)) return null
        return if (d.length in 3..15) (if (plus) "+" else "") + d else null
    }

    /** "1 2 3" para que la voz lo diga digito por digito. */
    fun habla(ctx: Context): String = numero(ctx).toList().joinToString(" ")
}