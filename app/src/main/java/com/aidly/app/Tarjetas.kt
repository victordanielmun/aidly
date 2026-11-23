package com.aidly.app

import org.json.JSONArray
import org.json.JSONObject

/** Una tarjeta de "Para ti": algo de interes para la persona, con su fuente. */
class Tarjeta(val titulo: String, val resumen: String, val tema: String, val fuente: String, val fecha: String) {
    /** Dato de conocimiento general (sin busqueda): no es una noticia de hoy y la pantalla lo dice. */
    val general: Boolean get() = fuente == Tarjetas.FUENTE_GENERAL_ES || fuente == Tarjetas.FUENTE_GENERAL_EN

    fun aJson(): JSONObject = JSONObject().put("titulo", titulo).put("resumen", resumen).put("tema", tema).put("fuente", fuente).put("fecha", fecha)
}

/**
 * Lee la respuesta del modelo con tolerancia (puede venir con texto alrededor o dentro de un bloque de codigo) y deja solo las tarjetas
 * validas: con fuente, sin repetir lo ya mostrado y sin temas delicados. El filtro de seguridad es ADEMAS del que se le pide al modelo: no
 * se confia solo en el prompt.
 */
object Tarjetas {
    const val MAX = 3
    const val FUENTE_GENERAL_ES = "Conocimiento general"
    const val FUENTE_GENERAL_EN = "General knowledge"
    private const val MAX_TITULO = 120
    private const val MAX_RESUMEN = 300

    /** Temas que una persona mayor no necesita recibir sin pedirlos (violencia, politica que divide, dinero facil, curas milagrosas). Se compara sin tildes. */
    private val DELICADO = Regex(
        "\\b(asesin\\w*|muert[eo]s?|murio|muere\\w*|fallec\\w*|homicid\\w*|masacre\\w*|tirote\\w*|terroris\\w*|atentado\\w*|secuestr\\w*|violacion|" +
            "abuso sexual|suicid\\w*|guerra\\w*|bombarde\\w*|accidente (mortal|grave)|tragedia|cadaver\\w*|elecciones?|candidat[oa]s?|partido politico|" +
            "estafa\\w*|fraude\\w*|inversion garantizada|criptomoned\\w*|bitcoin|apuestas?|casino|cura (milagrosa|definitiva)|cura(r|n)? el cancer|" +
            "remedio casero|murder\\w*|killed|dies|died|death toll|massacre|shooting|terroris\\w*|kidnap\\w*|rape|suicide|war|bombing|fatal|tragedy|corpse|" +
            "election|candidates?|political party|scams?|fraud\\w*|guaranteed returns|crypto\\w*|betting|gambling|miracle cure|home remedy|cure for cancer)\\b",
    )

    fun esSegura(t: Tarjeta): Boolean = !DELICADO.containsMatchIn(normalizarTexto("${t.titulo} ${t.resumen}"))

    /** [vistos]: titulos normalizados ya mostrados. [temasValidos]: si no esta vacio, el tema de la tarjeta debe ser uno de ellos. */
    fun parsear(texto: String, vistos: Collection<String> = emptyList(), temasValidos: Set<String> = emptySet()): List<Tarjeta> {
        val arr = extraerLista(texto) ?: return emptyList()
        val resultado = mutableListOf<Tarjeta>()
        val usados = vistos.toMutableSet()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val titulo = o.optString("titulo").trim().take(MAX_TITULO)
            val resumen = o.optString("resumen").trim().take(MAX_RESUMEN)
            val fuente = o.optString("fuente").trim()
            val tema = o.optString("tema").trim().lowercase()
            if (titulo.isBlank() || resumen.isBlank() || fuente.isBlank()) continue          // sin fuente no hay tarjeta
            if (temasValidos.isNotEmpty() && tema !in temasValidos) continue
            val t = Tarjeta(titulo, resumen, tema, fuente, o.optString("fecha").trim())
            val n = normalizarTexto(titulo)
            if (n in usados || !esSegura(t)) continue
            usados += n
            resultado += t
            if (resultado.size == MAX) break
        }
        return resultado
    }

    private fun extraerLista(texto: String): JSONArray? {
        val a = texto.indexOf('['); val o = texto.indexOf('{')
        try {
            if (a >= 0 && (o < 0 || a < o)) return JSONArray(texto.substring(a, texto.lastIndexOf(']') + 1))
            if (o >= 0) {
                val j = JSONObject(texto.substring(o, texto.lastIndexOf('}') + 1))
                return j.optJSONArray("tarjetas") ?: j.optJSONArray("cards")
            }
        } catch (_: Exception) {}
        return null
    }

    fun aJson(lista: List<Tarjeta>): String = JSONArray(lista.map { it.aJson() }).toString()
}

/** Las tarjetas que vienen dentro de la app (assets/config/para_ti.json): funcionan sin internet y con la nube apagada. */
object Curadas {
    /** Elige hasta [n] repartidas entre los temas del perfil (de mas a menos interesantes), primero las no vistas. Si ya vio todas, repite. */
    fun elegir(todas: List<Tarjeta>, perfil: Perfil, n: Int = Tarjetas.MAX): List<Tarjeta> {
        val temas = perfil.temasParaBuscar().ifEmpty { return emptyList() }
        val porTema = temas.associateWith { id -> todas.filter { it.tema == id } }
        fun reparto(soloNuevas: Boolean): List<Tarjeta> {
            val colas = porTema.mapValues { (_, l) -> l.filter { !soloNuevas || !perfil.haVisto(it.titulo) }.toMutableList() }
            val salida = mutableListOf<Tarjeta>()
            while (salida.size < n && colas.values.any { it.isNotEmpty() }) {
                for (id in temas) { colas[id]?.removeFirstOrNull()?.let { salida += it }; if (salida.size == n) break }
            }
            return salida
        }
        return reparto(true).ifEmpty { reparto(false) }
    }

    fun deJson(texto: String, idioma: String): List<Tarjeta> = try {
        val arr = JSONObject(texto).getJSONArray(if (idioma == "en") "en" else "es")
        val fuente = if (idioma == "en") Tarjetas.FUENTE_GENERAL_EN else Tarjetas.FUENTE_GENERAL_ES
        (0 until arr.length()).map { i -> arr.getJSONObject(i).let { Tarjeta(it.getString("titulo"), it.getString("resumen"), it.getString("tema"), fuente, "") } }
    } catch (e: Exception) { emptyList() }
}

