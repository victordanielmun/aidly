package com.aidly.app

import java.text.Normalizer
import java.util.Calendar
import java.util.Locale
import java.util.UUID

/**
 * English version of [ParserLocal]: understands common reminder requests by rules, instantly and without a network:
 * "remind me to take my pill at 8 pm", "in 20 minutes", "tomorrow at 7", "every day", "every 8 hours", "on Monday at 10 am".
 * If something does not fit it returns null and the hybrid model is asked.
 */
object ParserLocalEn {
    private const val N = "(\\d{1,3}|an?|one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve|fifteen|twenty|thirty)"
    private val NUM = mapOf(
        "a" to 1, "an" to 1, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7,
        "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12, "fifteen" to 15, "twenty" to 20, "thirty" to 30,
    )
    private fun num(s: String) = s.toIntOrNull() ?: NUM[s] ?: 0

    private val DISPARADOR = Regex("\\b(remind me|set (a|an) (reminder|alarm)|create (a )?reminder|add (a )?reminder|let me know|wake me( up)?)\\b")
    private val INICIO = Regex(
        "^\\W*(?:hey\\s+aidly\\W*)?(?:please\\W*)?(?:remind\\s+me|set\\s+(?:a|an)\\s+(?:reminder|alarm)|create\\s+(?:a\\s+)?reminder|" +
            "add\\s+(?:a\\s+)?reminder|let\\s+me\\s+know|wake\\s+me(?:\\s+up)?)\\b(?:\\s+(?:to|about|that|for)\\b)?",
    )
    private val REL = Regex("\\bin\\s+(?:$N|half\\s+an?)\\s+(minutes?|mins?|hours?|hrs?)\\b|\\bin\\s+half\\s+an\\s+hour\\b")
    private val ABS = Regex(
        "\\b(?:at|for)\\s+$N(?::(\\d{2}))?(?:\\s+o\\s*clock)?(?:\\s*(am|pm|a\\s*m|p\\s*m)\\b)?" +
            "(?:\\s+in\\s+the\\s+(morning|afternoon|evening)\\b|\\s+(?:at|tonight)\\s*(night)?\\b)?",
    )
    private val MEDIODIA = Regex("\\bat\\s+(noon|midnight)\\b")
    private val DIARIO = Regex("\\b(every\\s+day|everyday|each\\s+day|daily)\\b")
    private val CADA_H = Regex("\\bevery\\s+(?:$N\\s+hours?|hour)\\b")
    private val PASADO_MANANA = Regex("\\bday\\s+after\\s+tomorrow\\b")
    private val MANANA = Regex("\\btomorrow\\b")
    private val HOY = Regex("\\b(today|tonight)\\b")
    private val DIA_SEMANA = Regex("\\b(?:on\\s+)?(?:next\\s+)?(monday|tuesday|wednesday|thursday|friday|saturday|sunday)\\b")
    private val MEDICINA = Regex("\\b(pill|pills|medicine|medicines|medication|medications|antibiotic|insulin|drops|injection|syrup|vitamin|vitamins|tablet|tablets|capsule|capsules)\\b")
    private val DIAS = mapOf("sunday" to 1, "monday" to 2, "tuesday" to 3, "wednesday" to 4, "thursday" to 5, "friday" to 6, "saturday" to 7)

    private fun plano(s: String) = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    fun recordatorio(frase: String, ahora: Long = System.currentTimeMillis()): Recordatorios.Item? {
        val p = plano(frase).replace("'", " ").replace("’", " ")
        if (p.length != frase.length || !DISPARADOR.containsMatchIn(p)) return null
        val borrar = BooleanArray(p.length)
        fun marcar(r: MatchResult) { for (i in r.range) borrar[i] = true }

        INICIO.find(p)?.let(::marcar)

        var enMin = 0
        var h = -1; var m = 0; var periodo: String? = null
        REL.find(p)?.let { r ->
            marcar(r)
            enMin = if (r.value.contains("half an hour")) 30 else {
                val g = r.groupValues
                val cantidad = if (r.value.contains("half")) 0.5 else num(g[1]).toDouble()
                (cantidad * if (g[2].startsWith("h")) 60 else 1).toInt()
            }
        }
        MEDIODIA.find(p)?.let { r -> marcar(r); h = if (r.groupValues[1] == "noon") 12 else 0; periodo = "exacto" }
        if (h < 0) ABS.find(p)?.let { r ->
            marcar(r)
            h = num(r.groupValues[1]); m = r.groupValues[2].toIntOrNull() ?: 0
            val ampm = r.groupValues[3].replace(" ", "")
            periodo = when {
                ampm.isNotBlank() -> ampm
                r.groupValues[4] == "morning" -> "manana"
                r.groupValues[4] == "afternoon" || r.groupValues[4] == "evening" -> "tarde"
                r.value.contains("night") || r.value.contains("tonight") -> "noche"
                else -> null
            }
        }
        var cadaMin = 0L
        DIARIO.find(p)?.let { marcar(it); cadaMin = 1440 }
        var cadaHoras = 0
        CADA_H.find(p)?.let { r -> marcar(r); cadaHoras = if (r.groupValues[1].isBlank()) 1 else num(r.groupValues[1]); cadaMin = cadaHoras * 60L }

        var dias = -1
        PASADO_MANANA.find(p)?.let { marcar(it); dias = 2 }
        if (dias < 0) MANANA.find(p)?.let { marcar(it); dias = 1 }
        HOY.find(p)?.let { marcar(it); if (dias < 0) dias = 0; if (it.value == "tonight" && periodo == null) periodo = "noche" }
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
                if (m > 59 || (h > 12 && periodo != null && periodo != "pm" && periodo != "exacto")) return null
                val candidatas = when (periodo) {
                    "exacto" -> listOf(h)
                    "manana", "am" -> listOf(h % 12)
                    "tarde", "pm" -> listOf(if (h < 12) h + 12 else h)
                    "noche" -> listOf(if (h == 12) 0 else if (h < 12) h + 12 else h)
                    else -> if (h >= 12 || h == 0) listOf(h) else listOf(h % 12, h % 12 + 12)   // "at 8": next occurrence; "at 12" is noon
                }
                candidatas.mapNotNull { Interprete.cuando("%02d:%02d".format(it, m), fecha, 0, ahora) }.minOrNull()
            }
            cadaHoras > 0 -> ahora + cadaHoras * 3_600_000L
            else -> null
        } ?: return null

        val sb = StringBuilder()
        for (i in frase.indices) if (!borrar[i]) sb.append(frase[i])
        var texto = sb.toString().replace(Regex("\\s+"), " ").trim().trim(',', '.', ';', ':', '?', '!', ' ')
        while (true) {   // quita conectores sueltos al inicio o al final
            val q = plano(texto)
            val conector = Regex("^(to|for|that|about|and|at|on|in|please|me)\\b\\s*").find(q)
                ?: Regex("\\s*\\b(to|for|that|about|and|at|on|in|please|the|a)$").find(q)
            if (conector == null || texto.isBlank()) break
            texto = if (conector.range.first == 0) texto.substring(conector.range.last + 1) else texto.substring(0, conector.range.first)
            texto = texto.trim().trim(',', '.', ';', ':', ' ')
        }
        if (texto.isBlank()) return null
        // El aviso se dice a la persona: "take my pill" -> "take your pill".
        texto = texto.replace(Regex("(?i)\\bmyself\\b"), "yourself").replace(Regex("(?i)\\bmy\\b"), "your")
        val tipo = if (MEDICINA.containsMatchIn(plano(texto))) Recordatorios.Tipo.MEDICINA else Recordatorios.Tipo.GENERAL
        return Recordatorios.Item(UUID.randomUUID().toString(), texto, tipo, proxima, cadaMin)
    }
}
