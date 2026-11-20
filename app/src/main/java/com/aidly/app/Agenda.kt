package com.aidly.app

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.Schema
import java.util.Calendar
import java.util.TimeZone
import java.util.UUID

/**
 * Calendario por voz: "agenda cita con el doctor el martes a las 10" y "que tengo hoy".
 * Si hay permiso, la cita se escribe directo en el calendario del telefono (la persona no tiene que tocar
 * nada); si no, se abre el calendario con la cita ya llena. Ademas Aidly avisa una hora antes con su propia alarma.
 */
@OptIn(PublicPreviewAPI::class)
object Agenda {
    class Evento(val titulo: String, val inicio: Long, val fin: Long, val lugar: String)

    fun tienePermiso(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    private val esquema = Schema.obj(
        mapOf(
            "entendido" to Schema.boolean("true si hay una cita o evento y se sabe cuando es"),
            "titulo" to Schema.string("Nombre corto de la cita, por ejemplo 'Cita con el doctor'"),
            "hora" to Schema.string("HH:mm en 24 horas"),
            "fecha" to Schema.string("yyyy-MM-dd; si no dijo el dia, cadena vacia"),
            "duracion_min" to Schema.integer("Duracion en minutos; 60 si no la dijo"),
            "lugar" to Schema.string("Lugar si lo dijo, o cadena vacia"),
        ),
    )

    suspend fun interpretar(frase: String, ahora: Long = System.currentTimeMillis()): Evento? {
        val prompt = if (Idioma.ultimo == "en")
            "Convert a spoken request to schedule an appointment into data. Current date and time: ${Interprete.contexto(ahora)}. " +
                "The person said: \"$frase\". " +
                "Reply ONLY with a JSON object with: entendido, titulo, hora, fecha, duracion_min, lugar. " +
                "Rules: 'on Tuesday' means the next Tuesday; 'tomorrow' is the day after today. " +
                "'10 in the morning' is 10:00 and '4 in the afternoon' is 16:00. If they say a time with no am/pm, use the next future occurrence. " +
                "The title is in English, starts with a capital letter and does not include 'schedule' (for example 'Doctor appointment'). If the time is missing, entendido=false."
        else
            "Convierte una peticion hablada de agendar una cita en datos. Fecha y hora actuales: ${Interprete.contexto(ahora)}. " +
                "La persona dijo: \"$frase\". " +
                "Responde SOLO un objeto JSON con: entendido, titulo, hora, fecha, duracion_min, lugar. " +
                "Reglas: si dice 'el martes' usa el proximo martes; 'manana' es el dia siguiente a hoy. " +
                "'10 de la manana' es 10:00 y '4 de la tarde' es 16:00. Si dice una hora sin manana/tarde, usa la proxima " +
                "vez futura. El titulo va con mayuscula inicial y sin 'agendame'. Si falta la hora, entendido=false."
        return Interprete.pedir("cita", prompt, esquema) { j ->
            val titulo = j.optString("titulo", "").trim()
            if (!j.optBoolean("entendido", false) || titulo.isBlank()) return@pedir null
            val ini = Interprete.cuando(j.optString("hora", ""), j.optString("fecha", ""), 0, ahora) ?: return@pedir null
            val dur = j.optInt("duracion_min", 60).coerceIn(5, 12 * 60)
            Evento(titulo, ini, ini + dur * 60_000L, j.optString("lugar", "").trim())
        }
    }

    /** Escribe la cita en el primer calendario editable (el principal primero). Devuelve false si no se pudo. */
    fun guardar(ctx: Context, e: Evento): Boolean {
        if (!tienePermiso(ctx)) return false
        return try {
            val cr = ctx.contentResolver
            val calId = cr.query(
                CalendarContract.Calendars.CONTENT_URI,
                arrayOf(CalendarContract.Calendars._ID),
                "${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL} >= ? AND ${CalendarContract.Calendars.VISIBLE} = 1",
                arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
                "${CalendarContract.Calendars.IS_PRIMARY} DESC",
            )?.use { if (it.moveToFirst()) it.getLong(0) else null } ?: return false
            val v = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calId)
                put(CalendarContract.Events.TITLE, e.titulo)
                put(CalendarContract.Events.DTSTART, e.inicio)
                put(CalendarContract.Events.DTEND, e.fin)
                put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
                put(CalendarContract.Events.HAS_ALARM, 1)
                if (e.lugar.isNotBlank()) put(CalendarContract.Events.EVENT_LOCATION, e.lugar)
            }
            val uri = cr.insert(CalendarContract.Events.CONTENT_URI, v) ?: return false
            val id = uri.lastPathSegment?.toLongOrNull() ?: return true
            cr.insert(
                CalendarContract.Reminders.CONTENT_URI,
                ContentValues().apply {
                    put(CalendarContract.Reminders.EVENT_ID, id)
                    put(CalendarContract.Reminders.MINUTES, 60)
                    put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                },
            )
            SpikeLog.log("AGENDA: guardado '${e.titulo}' en calendario $calId")
            true
        } catch (ex: Exception) {
            SpikeLog.log("AGENDA: no pude guardar (${ex::class.simpleName}: ${ex.message})"); false
        }
    }

    /** Intent para abrir el calendario con la cita ya llena (cuando no hay permiso). */
    fun intentInsertar(e: Evento) = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, e.inicio)
        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, e.fin)
        .putExtra(CalendarContract.Events.TITLE, e.titulo)
        .putExtra(CalendarContract.Events.EVENT_LOCATION, e.lugar)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Aviso propio de Aidly una hora antes (o 10 min antes si la cita es muy pronto). Devuelve el aviso o null. */
    fun avisoPrevio(ctx: Context, e: Evento, ahora: Long = System.currentTimeMillis()): Recordatorios.Item? {
        val minutos = Idioma.inicial(ctx).getJSONObject("recordatorios").optJSONArray("aviso_previo_cita_min")
        val lista = (0 until (minutos?.length() ?: 0)).map { minutos!!.getLong(it) }.ifEmpty { listOf(60L, 10L) }
        val antes = lista.map { e.inicio - it * 60_000 }.firstOrNull { it > ahora + 60_000 } ?: return null
        return Recordatorios.Item(UUID.randomUUID().toString(), (if (Idioma.ultimo == "en") "go to " else "ir a ") + e.titulo.replaceFirstChar { it.lowercase() }, Recordatorios.Tipo.GENERAL, antes, 0)
    }

    /** Citas entre [desde] y [hasta] del calendario del telefono, ordenadas. Vacio si no hay permiso. */
    fun citas(ctx: Context, desde: Long, hasta: Long): List<Evento> {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) return emptyList()
        val lista = mutableListOf<Evento>()
        try {
            val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().also {
                android.content.ContentUris.appendId(it, desde); android.content.ContentUris.appendId(it, hasta)
            }.build()
            ctx.contentResolver.query(
                uri,
                arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.ALL_DAY),
                null, null, "${CalendarContract.Instances.BEGIN} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    if (c.getInt(4) == 1) continue          // dias festivos y cumpleanos de todo el dia suelen ser ruido
                    lista += Evento(c.getString(0).orEmpty().ifBlank { if (Idioma.ultimo == "en") "Event" else "Evento" }, c.getLong(1), c.getLong(2), c.getString(3).orEmpty())
                }
            }
        } catch (ex: Exception) {
            SpikeLog.log("AGENDA: no pude leer el calendario (${ex::class.simpleName}: ${ex.message})")
        }
        return lista
    }

    /** Limites [inicio, fin) del dia que se pide: hoy, manana, o los proximos 7 dias. */
    fun rango(ctx: Context, n: String, ahora: Long = System.currentTimeMillis()): Triple<Long, Long, String> {
        val en = Idioma.resolver(ctx) == "en"
        fun diaDesde(offset: Int) = Calendar.getInstance().apply {
            timeInMillis = ahora; add(Calendar.DAY_OF_MONTH, offset)
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return when {
            "semana" in n || "week" in n -> Triple(ahora, diaDesde(7), if (en) "in the next seven days" else "en los próximos siete días")
            "manana" in n || "tomorrow" in n -> Triple(diaDesde(1), diaDesde(2), if (en) "tomorrow" else "mañana")
            else -> Triple(ahora, diaDesde(1), if (en) "today" else "hoy")
        }
    }
}
