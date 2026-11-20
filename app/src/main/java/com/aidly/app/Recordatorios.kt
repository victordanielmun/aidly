package com.aidly.app

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.firebase.Firebase
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Recordatorios y medicamentos. Todo vive en el telefono (SharedPreferences) y suena con alarmas del sistema
 * (setAlarmClock: exactas, sobreviven al ahorro de bateria y no piden permisos). Cada aviso deja un registro
 * (tomado / omitido / pospuesto) que despues alimenta el seguimiento familiar, solo si la persona lo permite.
 */
@OptIn(PublicPreviewAPI::class)
object Recordatorios {
    enum class Tipo { MEDICINA, GENERAL, CHECKIN }
    enum class Estado { PENDIENTE, TOMADO, OMITIDO, POSPUESTO, AYUDA }

    /** [cadaMin] = 0 una sola vez; 1440 todos los dias; otro valor = cada tantos minutos (p. ej. 480 = cada 8 h). */
    class Item(val id: String, val texto: String, val tipo: Tipo, var proxima: Long, val cadaMin: Long)

    class Registro(
        val id: String, val recId: String, val texto: String, val tipo: Tipo,
        val programada: Long, var estado: Estado, var hora: Long,
    )

    const val CANAL = "recordatorios"
    const val ACC_SONAR = "com.aidly.app.REC_SONAR"
    const val ACC_VENCIDO = "com.aidly.app.REC_VENCIDO"
    const val ACC_TOMADO = "com.aidly.app.REC_TOMADO"
    const val ACC_POSPONER = "com.aidly.app.REC_POSPONER"
    const val ACC_AYUDA = "com.aidly.app.REC_AYUDA"
    private const val PREFS = "recordatorios"
    // Tiempos por defecto (idiomas.json > inicial.recordatorios): omitido tras 30 min sin respuesta; posponer 10 min.
    private fun minVence(ctx: Context) = Idioma.inicial(ctx).getJSONObject("recordatorios").optLong("marcar_omitido_tras_min", 30)
    private fun minPosponer(ctx: Context) = Idioma.inicial(ctx).getJSONObject("recordatorios").optLong("posponer_min", 10)
    private const val MAX_HISTORIAL = 100
    private const val MAX_EDAD_HISTORIAL = 30L * 24 * 60 * 60 * 1000

    // ---------- Almacen ----------

    /** Recordatorios del usuario (el check-in diario es interno y no aparece en la lista). */
    @Synchronized fun listar(ctx: Context): List<Item> = cargarItems(ctx).filter { it.tipo != Tipo.CHECKIN }.sortedBy { it.proxima }
    @Synchronized fun obtener(ctx: Context, id: String): Item? = cargarItems(ctx).firstOrNull { it.id == id }

    @Synchronized
    fun agregar(ctx: Context, item: Item) {
        guardarItems(ctx, cargarItems(ctx).filterNot { it.id == item.id } + item)
        programar(ctx, item)
    }

    @Synchronized
    fun cancelar(ctx: Context, id: String) {
        guardarItems(ctx, cargarItems(ctx).filterNot { it.id == id })
        ctx.getSystemService(AlarmManager::class.java).cancel(pendingSonar(ctx, id))
    }

    @Synchronized
    private fun actualizar(ctx: Context, item: Item) {
        guardarItems(ctx, cargarItems(ctx).filterNot { it.id == item.id } + item)
    }

    /** Despues de sonar: si se repite, programa la siguiente vez; si era unica, la quita. */
    @Synchronized
    fun avanzarOEliminar(ctx: Context, item: Item, ahora: Long = System.currentTimeMillis()) {
        if (item.cadaMin <= 0) { cancelar(ctx, item.id); return }
        item.proxima = siguiente(item.proxima, item.cadaMin, ahora)
        actualizar(ctx, item)
        programar(ctx, item)
    }

    /** Tras reiniciar el telefono o actualizar la app: vuelve a poner las alarmas y salta las que ya pasaron. */
    @Synchronized
    fun reprogramarTodo(ctx: Context, ahora: Long = System.currentTimeMillis()) {
        for (it in cargarItems(ctx)) {
            if (it.proxima > ahora) { programar(ctx, it); continue }
            if (it.cadaMin > 0) { it.proxima = siguiente(it.proxima, it.cadaMin, ahora); actualizar(ctx, it); programar(ctx, it) }
            else cancelar(ctx, it.id)
        }
    }

    private fun siguiente(desde: Long, cadaMin: Long, ahora: Long): Long {
        var t = desde
        val paso = cadaMin * 60_000
        while (t <= ahora) t += paso
        return t
    }

    private fun pendingSonar(ctx: Context, id: String) = PendingIntent.getBroadcast(
        ctx, 0,
        Intent(ctx, RecordatorioReceiver::class.java).setAction(ACC_SONAR).setData(Uri.parse("aidly://rec/$id")).putExtra("id", id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun programar(ctx: Context, item: Item) {
        val ver = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, RecordatoriosActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val am = ctx.getSystemService(AlarmManager::class.java)
        try {
            am.setAlarmClock(AlarmManager.AlarmClockInfo(item.proxima, ver), pendingSonar(ctx, item.id))
        } catch (e: SecurityException) {
            // Sin permiso de alarmas exactas (Android 12+): alarma aproximada, que puede tardar unos minutos.
            SpikeLog.log("RECORDATORIO: sin permiso de alarmas exactas, uso alarma aproximada")
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, item.proxima, pendingSonar(ctx, item.id))
        }
        SpikeLog.log("RECORDATORIO: programado '${item.texto}' para ${Date(item.proxima)} (cada ${item.cadaMin} min)")
    }

    // ---------- Registro de tomas ----------

    /** Crea el registro PENDIENTE de un aviso que acaba de sonar y programa el cierre automatico (omitido). */
    @Synchronized
    fun nuevoRegistro(ctx: Context, item: Item, ahora: Long = System.currentTimeMillis()): Registro {
        val r = Registro(UUID.randomUUID().toString(), item.id, item.texto, item.tipo, ahora, Estado.PENDIENTE, ahora)
        guardarRegistros(ctx, cargarRegistros(ctx) + r)
        val vence = PendingIntent.getBroadcast(
            ctx, 0,
            Intent(ctx, RecordatorioReceiver::class.java).setAction(ACC_VENCIDO)
                .setData(Uri.parse("aidly://vence/${r.id}")).putExtra("reg", r.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        ctx.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ahora + minVence(ctx) * 60_000, vence)
        Circulo.sincronizarAsync(ctx)
        return r
    }

    @Synchronized fun registro(ctx: Context, id: String): Registro? = cargarRegistros(ctx).firstOrNull { it.id == id }
    @Synchronized fun historial(ctx: Context): List<Registro> = cargarRegistros(ctx).sortedByDescending { it.programada }
    @Synchronized fun pendientes(ctx: Context): List<Registro> = historial(ctx).filter { it.estado == Estado.PENDIENTE }

    /** Marca el registro solo si seguia pendiente (evita pisar "tomado" con el cierre automatico). */
    @Synchronized
    fun marcar(ctx: Context, regId: String, estado: Estado, soloSiPendiente: Boolean = true): Registro? {
        val lista = cargarRegistros(ctx)
        val r = lista.firstOrNull { it.id == regId } ?: return null
        if (soloSiPendiente && r.estado != Estado.PENDIENTE) return null
        r.estado = estado
        r.hora = System.currentTimeMillis()
        guardarRegistros(ctx, lista)
        ctx.getSystemService(NotificationManager::class.java).cancel(regId.hashCode())
        SpikeLog.log("RECORDATORIO: '${r.texto}' -> $estado")
        when (estado) {
            Estado.AYUDA -> Circulo.sosAsync(ctx)
            Estado.TOMADO -> { if (r.tipo == Tipo.CHECKIN) Circulo.limpiarSos(ctx); Circulo.sincronizarAsync(ctx) }
            else -> Circulo.sincronizarAsync(ctx)
        }
        return r
    }

    /** "Recuerdamelo en 10 minutos": cierra este aviso como pospuesto y crea uno unico nuevo. */
    fun posponer(ctx: Context, regId: String) {
        val r = marcar(ctx, regId, Estado.POSPUESTO) ?: return
        agregar(ctx, Item(UUID.randomUUID().toString(), r.texto, r.tipo, System.currentTimeMillis() + minPosponer(ctx) * 60_000, 0))
    }

    // ---------- Texto hablado ----------

    fun describir(it: Item, ahora: Long = System.currentTimeMillis()): String {
        val c = Calendar.getInstance().apply { timeInMillis = it.proxima }
        val hoy = Calendar.getInstance().apply { timeInMillis = ahora }
        val dias = diasEntre(hoy, c)
        val en = Idioma.ultimo == "en"
        val dia = when (dias) {
            0 -> if (en) "today" else "hoy"; 1 -> if (en) "tomorrow" else "mañana"
            else -> if (en) "on ${MESES_EN[c.get(Calendar.MONTH)]} ${c.get(Calendar.DAY_OF_MONTH)}" else "el ${c.get(Calendar.DAY_OF_MONTH)} de ${MESES[c.get(Calendar.MONTH)]}"
        }
        val hora = horaHablada(c)
        return if (en) when {
            it.cadaMin == 1440L -> "every day at $hora"
            it.cadaMin > 0 && it.cadaMin % 60 == 0L -> "every ${it.cadaMin / 60} hours, the first time $dia at $hora"
            it.cadaMin > 0 -> "every ${it.cadaMin} minutes, the first time $dia at $hora"
            else -> "$dia at $hora"
        } else when {
            it.cadaMin == 1440L -> "todos los días $hora"
            it.cadaMin > 0 && it.cadaMin % 60 == 0L -> "cada ${it.cadaMin / 60} horas, la primera vez $dia $hora"
            it.cadaMin > 0 -> "cada ${it.cadaMin} minutos, la primera vez $dia $hora"
            else -> "$dia $hora"
        }
    }

    private val MESES_EN = listOf("January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    private val MESES = listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre")

    private fun diasEntre(a: Calendar, b: Calendar): Int {
        fun d(c: Calendar) = Calendar.getInstance().apply {
            set(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH), 12, 0, 0)
        }.timeInMillis / 86_400_000L
        return (d(b) - d(a)).toInt()
    }

    /** "a las 8 de la noche", "a las 8:05 de la mañana", "a la 1 de la tarde", "a las 12 del día". */
    fun horaHablada(c: Calendar): String {
        val h = c.get(Calendar.HOUR_OF_DAY)
        val m = c.get(Calendar.MINUTE)
        val h12 = when { h == 0 -> 12; h > 12 -> h - 12; else -> h }
        if (Idioma.ultimo == "en") return (if (m == 0) "$h12" else "$h12:${m.toString().padStart(2, '0')}") + if (h < 12) " AM" else " PM"
        val periodo = when {
            h in 0..5 -> "de la madrugada"; h in 6..11 -> "de la mañana"; h == 12 -> "del día"
            h in 13..18 -> "de la tarde"; else -> "de la noche"
        }
        val num = if (m == 0) "$h12" else "$h12:${m.toString().padStart(2, '0')}"
        return (if (h12 == 1) "a la " else "a las ") + "$num $periodo"
    }

    // ---------- Interpretar la frase con el modelo hibrido ----------

    private val esquema = Schema.obj(
        mapOf(
            "entendido" to Schema.boolean("true si hay algo que recordar y se sabe cuando"),
            "texto" to Schema.string("Que hay que recordar, en infinitivo, sin la palabra recuerdame"),
            "tipo" to Schema.enumeration(listOf("medicina", "general"), "medicina si es tomar medicina o pastilla"),
            "hora" to Schema.string("HH:mm en 24 horas, o cadena vacia si es relativo (en X minutos)"),
            "fecha" to Schema.string("yyyy-MM-dd solo si dijo un dia concreto; si no, cadena vacia"),
            "en_minutos" to Schema.integer("Minutos desde ahora si dijo 'en X minutos/horas'; si no, 0"),
            "cada_horas" to Schema.integer("24 si es todos los dias; N si es cada N horas; 0 si es una sola vez"),
        ),
    )

    /**
     * Convierte "recuerdame tomar la pastilla a las 8 de la noche" en un recordatorio (modelo del telefono y,
     * si falla, la nube). La hora la calcula el codigo. Devuelve null si no se entendio cuando avisar.
     */
    suspend fun interpretar(frase: String, ahora: Long = System.currentTimeMillis()): Item? {
        // Primero las reglas locales: instantaneas, sin red y sin depender de que el modelo este disponible.
        val reglas = if (Idioma.ultimo == "en") ParserLocalEn.recordatorio(frase, ahora) else ParserLocal.recordatorio(frase, ahora)
        reglas?.let {
            SpikeLog.log("RECORDATORIO: entendido por reglas locales (sin red): '${it.texto}' ${describir(it, ahora)}")
            return it
        }
        val prompt = if (Idioma.ultimo == "en")
            "Convert a spoken reminder request into data. Current date and time: ${Interprete.contexto(ahora)}. " +
                "The person said: \"$frase\". " +
                "Reply ONLY with a JSON object with: entendido, texto, tipo, hora, fecha, en_minutos, cada_horas. " +
                "Rules: texto is the task in the infinitive without 'remind me to' (for example 'take your blood pressure pill'), in English, addressed to the person (use 'your', never 'my'). " +
                "If they say a time with no am/pm (for example 'at 8'), use the next future occurrence. " +
                "'8 in the evening' is 20:00. 'every day' is cada_horas 24. 'every 8 hours' is cada_horas 8. " +
                "If you cannot tell when to remind, entendido=false."
        else
            "Convierte una peticion hablada de recordatorio en datos. Fecha y hora actuales: ${Interprete.contexto(ahora)}. " +
                "La persona dijo: \"$frase\". " +
                "Responde SOLO un objeto JSON con: entendido, texto, tipo, hora, fecha, en_minutos, cada_horas. " +
                "Reglas: texto va en infinitivo (por ejemplo 'tomar la pastilla de la presion'). " +
                "Si dice una hora sin mañana/tarde/noche (por ejemplo 'a las 8'), usa la proxima vez futura. " +
                "'8 de la noche' es 20:00. 'todos los dias' es cada_horas 24. 'cada 8 horas' es cada_horas 8. " +
                "Si no se sabe cuando avisar, entendido=false."
        return Interprete.pedir("recordatorio", prompt, esquema) { j ->
            val texto = j.optString("texto", "").trim()
            if (!j.optBoolean("entendido", false) || texto.isBlank()) return@pedir null
            val proxima = Interprete.cuando(
                j.optString("hora", ""), j.optString("fecha", ""), j.optInt("en_minutos", 0), ahora,
            ) ?: j.optInt("cada_horas", 0).takeIf { it in 1..168 }?.let { ahora + it * 3_600_000L }   // "cada 8 horas" sin hora: la primera vez es dentro de 8 horas
                ?: return@pedir null
            Item(
                UUID.randomUUID().toString(), texto,
                if (j.optString("tipo") == "medicina") Tipo.MEDICINA else Tipo.GENERAL,
                proxima, j.optInt("cada_horas", 0).coerceIn(0, 168) * 60L,
            )
        }
    }


    // ---------- Persistencia ----------

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun cargarItems(ctx: Context): List<Item> = try {
        val arr = JSONArray(Cifrado.abrir(prefs(ctx).getString("items", null)) ?: "[]")
        (0 until arr.length()).map { arr.getJSONObject(it) }.map {
            Item(it.getString("id"), it.getString("texto"), Tipo.valueOf(it.getString("tipo")), it.getLong("proxima"), it.getLong("cadaMin"))
        }
    } catch (e: Exception) { emptyList() }

    private fun guardarItems(ctx: Context, lista: List<Item>) {
        val arr = JSONArray()
        lista.forEach {
            arr.put(JSONObject().put("id", it.id).put("texto", it.texto).put("tipo", it.tipo.name).put("proxima", it.proxima).put("cadaMin", it.cadaMin))
        }
        prefs(ctx).edit().putString("items", Cifrado.cerrar(arr.toString())).apply()
    }

    private fun cargarRegistros(ctx: Context): List<Registro> = try {
        val limite = System.currentTimeMillis() - MAX_EDAD_HISTORIAL
        val arr = JSONArray(Cifrado.abrir(prefs(ctx).getString("historial", null)) ?: "[]")
        (0 until arr.length()).map { arr.getJSONObject(it) }.filter { it.getLong("programada") >= limite }.map {
            Registro(
                it.getString("id"), it.getString("recId"), it.getString("texto"), Tipo.valueOf(it.getString("tipo")),
                it.getLong("programada"), Estado.valueOf(it.getString("estado")), it.getLong("hora"),
            )
        }
    } catch (e: Exception) { emptyList() }

    private fun guardarRegistros(ctx: Context, lista: List<Registro>) {
        val arr = JSONArray()
        lista.sortedBy { it.programada }.takeLast(MAX_HISTORIAL).forEach {
            arr.put(
                JSONObject().put("id", it.id).put("recId", it.recId).put("texto", it.texto).put("tipo", it.tipo.name)
                    .put("programada", it.programada).put("estado", it.estado.name).put("hora", it.hora),
            )
        }
        prefs(ctx).edit().putString("historial", Cifrado.cerrar(arr.toString())).apply()
    }
}
