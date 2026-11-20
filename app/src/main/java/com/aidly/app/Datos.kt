package com.aidly.app

import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject

/** Ajustes sencillos de la app (guardados en el telefono). */
object Preferencias {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE)

    /** "Reducir movimiento" propio o animaciones del sistema apagadas (Ajustes > Accesibilidad). */
    fun reducirMovimiento(ctx: Context): Boolean =
        prefs(ctx).getBoolean("reducirMovimiento", false) ||
            Settings.Global.getFloat(ctx.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

    fun reducirMovimientoPropio(ctx: Context) = prefs(ctx).getBoolean("reducirMovimiento", false)
    fun fijarReducirMovimiento(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("reducirMovimiento", v).apply()

    /** Pantalla de bienvenida (primer arranque) ya recorrida. */
    fun bienvenidaHecha(ctx: Context) = prefs(ctx).getBoolean("bienvenidaHecha", false)
    fun fijarBienvenidaHecha(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean("bienvenidaHecha", v).apply()

    /** Como quiere la persona que la llame (se usa como nombre por defecto del circulo familiar). */
    fun nombre(ctx: Context) = prefs(ctx).getString("nombre", "").orEmpty()
    fun fijarNombre(ctx: Context, v: String) = prefs(ctx).edit().putString("nombre", v.trim()).apply()
}

/** Contactos favoritos de "Mi familia" (hasta 4). Se eligen con el selector del sistema: no piden permiso de contactos. */
object Favoritos {
    class Favorito(val nombre: String, val numero: String)

    const val MAX = 4
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("favoritos", Context.MODE_PRIVATE)

    fun lista(ctx: Context): List<Favorito> = try {
        val arr = JSONArray(Cifrado.abrir(prefs(ctx).getString("lista", null)) ?: "[]")
        (0 until arr.length()).map { arr.getJSONObject(it) }.map { Favorito(it.getString("nombre"), it.getString("numero")) }
    } catch (e: Exception) { emptyList() }

    private fun guardar(ctx: Context, l: List<Favorito>) {
        val arr = JSONArray()
        l.forEach { arr.put(JSONObject().put("nombre", it.nombre).put("numero", it.numero)) }
        prefs(ctx).edit().putString("lista", Cifrado.cerrar(arr.toString())).apply()
    }

    /** Devuelve false si ya hay [MAX] o el contacto ya estaba. */
    fun agregar(ctx: Context, f: Favorito): Boolean {
        val l = lista(ctx)
        if (l.size >= MAX || l.any { it.numero == f.numero }) return false
        guardar(ctx, l + f); return true
    }

    fun quitar(ctx: Context, numero: String) = guardar(ctx, lista(ctx).filterNot { it.numero == numero })

    /** Busca por palabras del nombre ("hija", "mama"); [plano] normaliza (sin acentos ni mayusculas). */
    fun buscar(ctx: Context, pedido: String, plano: (String) -> String): Favorito? {
        val palabras = plano(pedido).split(" ").filter { it.isNotBlank() }
        if (palabras.isEmpty()) return null
        return lista(ctx).filter { f -> val n = plano(f.nombre); palabras.all { it in n } }.minByOrNull { it.nombre.length }
    }
}

/** Prefijo telefonico del pais del SIM (`paises` de idiomas.json), para abrir WhatsApp con un numero guardado sin prefijo. */
object Prefijo {
    /** Solo digitos, con prefijo de pais si el numero no lo traia. */
    fun parawhatsapp(ctx: Context, numero: String): String {
        val digitos = numero.filter { it.isDigit() }
        if (numero.trim().startsWith("+")) return digitos
        val p = Idioma.datosPais(ctx).optString("prefijo_tel").ifBlank { return digitos }
        return if (digitos.startsWith(p) && digitos.length > 10) digitos else p + digitos.removePrefix("0")
    }
}