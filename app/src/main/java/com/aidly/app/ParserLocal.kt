package com.aidly.app

import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * Entiende por reglas las peticiones de recordatorio mas comunes, sin red y al instante:
 * "recuerdame tomar la pastilla a las 8 de la noche", "en 20 minutos", "manana a las 7", "todos los dias",
 * "cada 8 horas". Si algo no encaja, devuelve null y se le pregunta al modelo (hibrido/nube).
 * Un aviso de medicina no debe depender de que un servidor este disponible.
 */
object ParserLocal {
    private const val N = "(\\d{1,3}|un|una|uno|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|once|doce|quince|veinte|treinta)"
    private val NUM = mapOf(
        "un" to 1, "una" to 1, "uno" to 1, "dos" to 2, "tres" to 3, "cuatro" to 4, "cinco" to 5, "seis" to 6, "siete" to 7,
        "ocho" to 8, "nueve" to 9, "diez" to 10, "once" to 11, "doce" to 12, "quince" to 15, "veinte" to 20, "treinta" to 30,
    )
    private fun num(s: String) = s.toIntOrNull() ?: NUM[s] ?: 0

    private val DISPARADOR = Regex(
        "\\b(recuerdame|recordarme|recuerdamelo|avisame|(ponme|pon|crea|creame|agrega|agregame|anade|programa)\\s+(un\\s+|una\\s+)?(recordatorio|alarma))\\b",
    )
    private val INICIO = Regex(
        "^\\W*(?:oye\\s+aidly\\W*)?(?:por\\s+favor\\W*)?(?:recuerdame|recordarme|recuerdamelo|avisame|" +
            "(?:ponme|pon|crea|creame|agrega|agregame|anade|programa)\\s+(?:un\\s+|una\\s+)?(?:recordatorio|alarma))\\b" +
            "(?:\\s+(?:de|para|que)\\b)?",
    )
    private val REL = Regex("\\ben\\s+(?:$N|media)\\s+(minutos?|horas?)\\b|\\ben\\s+media\\s+hora\\b")
    private val ABS = Regex(
        "\\ba\\s+las?\\s+$N(?::(\\d{2}))?(?:\\s+y\\s+(media|cuarto))?" +
            "(?:\\s+(?:(?:de|por|en)\\s+la\\s+)?(manana|tarde|noche|madrugada)\\b|\\s*(am|pm)\\b)?",
    )
    private val DIARIO = Regex("\\b(todos\\s+los\\s+dias|cada\\s+dia|diariamente|diario)\\b")
    private val CADA_H = Regex("\\bcada\\s+(?:$N\\s+horas?|hora)\\b")
    private val PASADO_MANANA = Regex("\\bpasado\\s+manana\\b")
    private val MANANA = Regex("(?<!la )\\bmanana\\b")
    private val HOY = Regex("\\bhoy\\b")
    private val DIA_SEMANA = Regex("\\b(?:el\\s+)?(lunes|martes|miercoles|jueves|viernes|sabado|domingo)\\b")
    private val MEDICINA = Regex("\\b(pastilla|pastillas|medicina|medicinas|medicamento|medicamentos|antibiotico|insulina|gotas|inyeccion|jarabe|vitamina|vitaminas|tableta|tabletas|capsula|capsulas|remedio|remedios)\\b")
    private val SUBJUNTIVO = mapOf(
        "tome" to "tomar", "llame" to "llamar", "compre" to "comprar", "apague" to "apagar", "cierre" to "cerrar",
        "saque" to "sacar", "vaya" to "ir", "salga" to "salir", "mida" to "medir", "camine" to "caminar", "beba" to "beber",
    )
    private val DIAS = mapOf("lunes" to 2, "martes" to 3, "miercoles" to 4, "jueves" to 5, "viernes" to 6, "sabado" to 7, "domingo" to 1)

    /** Minusculas sin acentos y con la MISMA longitud que el original (para poder recortar el texto original). */
    private fun plano(s: String) = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    fun recordatorio(frase: String, ahora: Long = System.currentTimeMillis()): Recordatorios.Item? {
        val p = plano(frase)
        if (p.length != frase.length || !DISPARADOR.containsMatchIn(p)) return null
        val borrar = BooleanArray(p.length)
        fun marcar(r: MatchResult) { for (i in r.range) borrar[i] = true }

        INICIO.find(p)?.let(::marcar)

        // ---- Cuando ----
        var enMin = 0
        var h = -1; var m = 0; var periodo: String? = null
        REL.find(p)?.let { r ->
            marcar(r)
            val g = r.groupValues
            enMin = if (r.value.contains("media hora")) 30 else {
                val cantidad = if (r.value.contains("media")) 0.5 else num(g[1]).toDouble()
                (cantidad * if (g[2].startsWith("hora")) 60 else 1).toInt()
            }
        }
        ABS.find(p)?.let { r ->
            marcar(r)
            h = num(r.groupValues[1]); m = r.groupValues[2].toIntOrNull() ?: 0
            when (r.groupValues[3]) { "media" -> m = 30; "cuarto" -> m = 15 }
            periodo = r.groupValues[4].ifBlank { r.groupValues[5] }.ifBlank { null }
        }
        var cadaMin = 0L
        DIARIO.find(p)?.let { marcar(it); cadaMin = 1440 }
        var cadaHoras = 0
        CADA_H.find(p)?.let { r -> marcar(r); cadaHoras = if (r.groupValues[1].isBlank()) 1 else num(r.groupValues[1]); cadaMin = cadaHoras * 60L }

        var dias = -1
        PASADO_MANANA.find(p)?.let { marcar(it); dias = 2 }
        if (dias < 0) MANANA.find(p)?.let { marcar(it); dias = 1 }
        HOY.find(p)?.let { marcar(it); if (dias < 0) dias = 0 }
        DIA_SEMANA.find(p)?.let { r ->
            marcar(r)
            val hoy = Calendar.getInstance().apply { timeInMillis = ahora }.get(Calendar.DAY_OF_WEEK)
            dias = (DIAS.getValue(r.groupValues[1]) - hoy + 7) % 7
        }
        val fecha = if (dias >= 0) {
            val c = Calendar.getInstance().apply { timeInMillis = ahora; add(Calendar.DAY_OF_MONTH, dias) }
            "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
        } else ""

        val proxima: Long = when {
            enMin > 0 -> Interprete.cuando("", "", enMin, ahora)
            h in 0..23 -> {
                if (m > 59 || (h > 12 && periodo != null && periodo != "pm")) return null
                val candidatas = when (periodo) {
                    "manana", "madrugada", "am" -> listOf(h % 12)
                    "tarde", "pm" -> listOf(if (h < 12) h + 12 else h)
                    "noche" -> listOf(if (h == 12) 0 else if (h < 12) h + 12 else h)
                    else -> if (h >= 12 || h == 0) listOf(h) else listOf(h % 12, h % 12 + 12)   // "a las 8": la proxima vez; "a las 12" es mediodia
                }
                candidatas.mapNotNull { Interprete.cuando("%02d:%02d".format(it, m), fecha, 0, ahora) }.minOrNull()
            }
            cadaHoras > 0 -> ahora + cadaHoras * 3_600_000L        // "cada 8 horas" sin hora: la primera vez es dentro de 8 horas
            else -> null
        } ?: return null

        // ---- Que ----
        val sb = StringBuilder()
        for (i in frase.indices) if (!borrar[i]) sb.append(frase[i])
        var texto = sb.toString().replace(Regex("\\s+"), " ").trim().trim(',', '.', ';', ':', '¿', '?', '¡', '!', ' ')
        while (true) {   // quita conectores sueltos al inicio o al final ("de", "para", "que", "y"...)
            val q = plano(texto)
            val conector = Regex("^(de|para|que|a|y|me|por favor|el|en)\\b\\s*").find(q)
                ?: Regex("\\s*\\b(de|para|que|a|y|en|por favor|el|la)$").find(q)
            if (conector == null || texto.isBlank()) break
            texto = if (conector.range.first == 0) texto.substring(conector.range.last + 1) else texto.substring(0, conector.range.first)
            texto = texto.trim().trim(',', '.', ';', ':', ' ')
        }
        if (texto.isBlank()) return null
        val primera = plano(texto).substringBefore(' ')
        val infinitivo = SUBJUNTIVO[primera]
        if (infinitivo != null) texto = infinitivo + texto.substring(primera.length)
        else if (primera.endsWith("e") && p.contains("avisame que")) return null   // verbo conjugado raro: que lo resuelva el modelo

        val tipo = if (MEDICINA.containsMatchIn(plano(texto))) Recordatorios.Tipo.MEDICINA else Recordatorios.Tipo.GENERAL
        return Recordatorios.Item(UUID.randomUUID().toString(), texto, tipo, proxima, cadaMin)
    }
}
