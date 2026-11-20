package com.aidly.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bandeja local de mensajes y correos recibidos como notificacion. Solo se guarda en el telefono
 * (SharedPreferences), con tope de cantidad y de antiguedad. Sin acceso a la cuenta de correo:
 * solo lo que Android muestra en la notificacion (remitente, asunto y vista previa).
 */
object Bandeja {
    enum class Tipo { MENSAJE, CORREO }

    class Item(
        val id: String,
        val tipo: Tipo,
        val app: String,        // "WhatsApp", "Gmail", ...
        val remitente: String,
        val texto: String,      // mensaje o asunto
        val detalle: String,    // vista previa del correo (puede estar vacia)
        val hora: Long,
        var leido: Boolean,
    )

    private const val PREFS = "bandeja"
    private const val CLAVE = "items"
    private const val MAX_ITEMS = 40
    private const val MAX_EDAD_MS = 48L * 60 * 60 * 1000

    private val MENSAJERIA = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "com.google.android.apps.messaging" to "Mensajes",
        "com.samsung.android.messaging" to "Mensajes",
        "org.telegram.messenger" to "Telegram",
        "com.android.shell" to "Prueba", // solo para `adb shell cmd notification post`
    )
    private val CORREO = mapOf(
        "com.google.android.gm" to "Gmail",
        "com.microsoft.office.outlook" to "Outlook",
        "com.samsung.android.email.provider" to "Correo de Samsung",
        "com.yahoo.mobile.client.android.mail" to "Yahoo Mail",
    )

    /** Devuelve (tipo, nombre de la app) o null si el paquete no es de mensajeria ni de correo. */
    fun clasificar(pkg: String): Pair<Tipo, String>? =
        MENSAJERIA[pkg]?.let { Tipo.MENSAJE to it } ?: CORREO[pkg]?.let { Tipo.CORREO to it }

    @Synchronized
    fun agregar(ctx: Context, item: Item) {
        val lista = cargar(ctx).filterNot { it.id == item.id }.toMutableList()
        lista.add(item)
        guardar(ctx, lista)
    }

    /** Pendientes (no leidos) del tipo indicado, del mas reciente al mas antiguo. */
    @Synchronized
    fun pendientes(ctx: Context, tipo: Tipo): List<Item> =
        cargar(ctx).filter { it.tipo == tipo && !it.leido }.sortedByDescending { it.hora }

    @Synchronized
    fun marcarLeidos(ctx: Context, ids: Collection<String>) {
        val lista = cargar(ctx)
        lista.forEach { if (it.id in ids) it.leido = true }
        guardar(ctx, lista)
    }

    @Synchronized
    fun vaciar(ctx: Context) = guardar(ctx, emptyList())

    private fun cargar(ctx: Context): List<Item> {
        val crudo = Cifrado.abrir(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(CLAVE, null)) ?: return emptyList()
        val limite = System.currentTimeMillis() - MAX_EDAD_MS
        return try {
            val arr = JSONArray(crudo)
            (0 until arr.length()).map { arr.getJSONObject(it) }.mapNotNull { o ->
                val hora = o.optLong("hora")
                if (hora < limite) null else Item(
                    id = o.getString("id"),
                    tipo = Tipo.valueOf(o.getString("tipo")),
                    app = o.optString("app"),
                    remitente = o.optString("remitente"),
                    texto = o.optString("texto"),
                    detalle = o.optString("detalle"),
                    hora = hora,
                    leido = o.optBoolean("leido"),
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun guardar(ctx: Context, lista: List<Item>) {
        val arr = JSONArray()
        lista.sortedBy { it.hora }.takeLast(MAX_ITEMS).forEach {
            arr.put(
                JSONObject()
                    .put("id", it.id).put("tipo", it.tipo.name).put("app", it.app)
                    .put("remitente", it.remitente).put("texto", it.texto).put("detalle", it.detalle)
                    .put("hora", it.hora).put("leido", it.leido),
            )
        }
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(CLAVE, Cifrado.cerrar(arr.toString())).apply()
    }
}
