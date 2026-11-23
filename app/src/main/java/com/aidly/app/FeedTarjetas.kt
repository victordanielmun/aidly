package com.aidly.app

import android.content.Context
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.Tool
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.Locale

/**
 * Trae las tarjetas de "Para ti": UNA llamada a Gemini con busqueda de Google para varios temas a la vez (ahorra cuota gratuita), con una
 * guia de seguridad y de tono para una persona mayor. Se guarda en cache cifrada (6 h): el seguimiento por voz sobre una tarjeta ya
 * guardada puede correr con el modelo del telefono y sin gastar otra busqueda. Necesita la nube encendida para traer tarjetas nuevas;
 * con la nube apagada solo se ve la cache.
 */
@OptIn(PublicPreviewAPI::class)
object FeedTarjetas {
    private const val CLOUD_MODEL = "gemini-3.5-flash-lite"
    private const val MODELO_RESPALDO = "gemini-3.8-flash"
    const val ALMACEN = "feed"
    private const val CLAVE = "cache"
    private const val VIGENCIA_MS = 6L * 60 * 60 * 1000
    /** Se piden unas mas de las que se muestran por si el filtro de seguridad descarta alguna. */
    private const val PEDIR = 5

    /** [origen]: "cache", "curadas" (las de la app) o "nube". */
    class Resultado(val tarjetas: List<Tarjeta>, val deCache: Boolean, val ms: Long, val origen: String = if (deCache) "cache" else "nube")

    /** [modeloPrueba] solo para probar con otro modelo desde el log de depuracion. */
    @Volatile internal var modeloPrueba: String? = null
    private val modelos = mutableMapOf<String, com.google.firebase.ai.GenerativeModel>()
    private fun model(conBusqueda: Boolean, nombreModelo: String? = null): com.google.firebase.ai.GenerativeModel = synchronized(modelos) {
        val nombre = nombreModelo ?: modeloPrueba ?: CLOUD_MODEL
        modelos.getOrPut("$nombre|$conBusqueda") {
            val ai = Firebase.ai(backend = GenerativeBackend.googleAI())
            // Respuesta acotada. El modelo de respaldo "piensa" siempre, asi que ahi se pide el nivel bajo.
            val config = com.google.firebase.ai.type.generationConfig {
                maxOutputTokens = 1500
                // Como el chat (que responde bien): razonamiento minimo; el modelo de respaldo no lo admite, ahi nivel bajo.
                thinkingConfig = com.google.firebase.ai.type.thinkingConfig { thinkingLevel = if (nombre == MODELO_RESPALDO) com.google.firebase.ai.type.ThinkingLevel.LOW else com.google.firebase.ai.type.ThinkingLevel.MINIMAL }
            }
            if (conBusqueda) ai.generativeModel(modelName = nombre, generationConfig = config, tools = listOf(Tool.googleSearch()))
            else ai.generativeModel(modelName = nombre, generationConfig = config)
        }
    }

    // ---------- prompt ----------
    internal fun prompt(idioma: String, pais: String, fecha: String, temas: List<TemaInteres>, vistos: List<String>): String {
        val en = idioma == "en"
        val lista = temas.joinToString("; ") { "${it.id} = ${if (en) it.buscaEn else it.buscaEs}" }
        val previos = if (vistos.isEmpty()) "" else (if (en) "\nDo NOT repeat these already shown topics: " else "\nNo repitas estos temas ya mostrados: ") + vistos.takeLast(20).joinToString("; ")
        return if (en) {
            "You are Aidly, a kind companion for an older adult. Today is $fecha; the person lives in a country with code $pais. " +
                "Search the internet and prepare $PEDIR different, recent, reliable items that this person would enjoy, spread across these interests (id = what to look for): $lista.\n" +
                "RULES: warm, positive or neutral tone; real items from real sources only; never invent anything. Avoid deaths, crimes, accidents, wars, divisive politics, " +
                "elections, scams, get-rich offers, gambling, miracle cures and medical claims. For health, only general healthy habits.$previos\n" +
                "Answer ONLY with a JSON array, no other text. Each item: {\"titulo\": short title in English (max 90 characters), " +
                "\"resumen\": two short, simple sentences in English with no jargon, \"tema\": one of the interest ids, " +
                "\"fuente\": name of the site or newspaper, \"fecha\": YYYY-MM-DD if known or empty}."
        } else {
            "Eres Aidly, un companero amable para una persona mayor. Hoy es $fecha; la persona vive en un pais con codigo $pais. " +
                "Busca en internet y prepara $PEDIR cosas distintas, recientes y confiables que esta persona disfrutaria, repartidas entre estos intereses (id = que buscar): $lista.\n" +
                "REGLAS: tono carinoso, positivo o neutro; solo cosas reales de fuentes reales; nunca inventes nada. Evita muertes, crimenes, accidentes, guerras, politica que divida, " +
                "elecciones, estafas, ofertas para hacerse rico, apuestas, curas milagrosas y afirmaciones medicas. En salud, solo habitos saludables generales.$previos\n" +
                "Responde SOLO con un arreglo JSON, sin otro texto. Cada elemento: {\"titulo\": titulo corto en espanol (maximo 90 caracteres), " +
                "\"resumen\": dos frases cortas y sencillas en espanol, sin tecnicismos, \"tema\": uno de los ids de interes, " +
                "\"fuente\": nombre del sitio o periodico, \"fecha\": AAAA-MM-DD si se conoce o vacio}."
        }
    }

    /** Sin busqueda (la cuota gratuita de busqueda de Google se agota): solo datos muy conocidos y estables, sin cifras ni "novedades". */
    internal fun promptGeneral(idioma: String, temas: List<TemaInteres>, vistos: List<String>): String {
        val en = idioma == "en"
        val lista = temas.filter { it.id != "noticias" }.joinToString("; ") { "${it.id} = ${if (en) it.buscaEn else it.buscaEs}" }
        val previos = if (vistos.isEmpty()) "" else (if (en) "\nDo NOT repeat these already shown topics: " else "\nNo repitas estos temas ya mostrados: ") + vistos.takeLast(20).joinToString("; ")
        return if (en) {
            "You are Aidly, a kind companion for an older adult. Do NOT search the internet. Prepare $PEDIR different pleasant items from your general knowledge, " +
                "spread across these interests (id = topic): $lista.\n" +
                "RULES: only very well-known, stable facts, stories, tips or recipes; NO news, NO recent events, NO statistics, NO exact figures you are not sure of; " +
                "never invent anything. Warm tone. Avoid deaths, crimes, accidents, wars, politics, scams, gambling, miracle cures and medical claims. " +
                "For health, only general healthy habits.$previos\n" +
                "Answer ONLY with a JSON array. Each item: {\"titulo\": short title in English (max 90 characters), \"resumen\": two short, simple sentences in English, " +
                "\"tema\": one of the interest ids, \"fuente\": \"${Tarjetas.FUENTE_GENERAL_EN}\", \"fecha\": \"\"}."
        } else {
            "Eres Aidly, un companero amable para una persona mayor. NO busques en internet. Prepara $PEDIR cosas agradables y distintas de tu conocimiento general, " +
                "repartidas entre estos intereses (id = tema): $lista.\n" +
                "REGLAS: solo datos, historias, consejos o recetas muy conocidos y estables; NADA de noticias, NADA de hechos recientes, NADA de estadisticas ni cifras exactas de las que no estes seguro; " +
                "nunca inventes nada. Tono carinoso. Evita muertes, crimenes, accidentes, guerras, politica, estafas, apuestas, curas milagrosas y afirmaciones medicas. " +
                "En salud, solo habitos saludables generales.$previos\n" +
                "Responde SOLO con un arreglo JSON. Cada elemento: {\"titulo\": titulo corto en espanol (maximo 90 caracteres), \"resumen\": dos frases cortas y sencillas en espanol, " +
                "\"tema\": uno de los ids de interes, \"fuente\": \"${Tarjetas.FUENTE_GENERAL_ES}\", \"fecha\": \"\"}."
        }
    }

    // ---------- cache ----------
    private fun prefs(ctx: Context) = ctx.getSharedPreferences(ALMACEN, Context.MODE_PRIVATE)

    /** Tarjetas guardadas (aunque ya hayan caducado) o vacio. */
    fun cacheadas(ctx: Context): List<Tarjeta> = leerCache(ctx)?.third.orEmpty()

    private fun leerCache(ctx: Context): Triple<Long, String, List<Tarjeta>>? {
        val txt = Cifrado.abrir(prefs(ctx).getString(CLAVE, null)) ?: return null
        return try {
            val j = JSONObject(txt)
            val arr = j.getJSONArray("tarjetas")
            val lista = (0 until arr.length()).map { i -> arr.getJSONObject(i).let { Tarjeta(it.getString("titulo"), it.getString("resumen"), it.optString("tema"), it.getString("fuente"), it.optString("fecha")) } }
            Triple(j.getLong("ts"), j.optString("clave"), lista)
        } catch (e: Exception) { null }
    }

    private fun guardarCache(ctx: Context, clave: String, lista: List<Tarjeta>) {
        val j = JSONObject().put("ts", System.currentTimeMillis()).put("clave", clave).put("tarjetas", org.json.JSONArray(Tarjetas.aJson(lista)))
        prefs(ctx).edit().putString(CLAVE, Cifrado.cerrar(j.toString())).apply()
    }

    private fun claveCache(idioma: String, temas: List<String>) = idioma + ":" + temas.sorted().joinToString(",")

    // ---------- rapido (sin red) ----------
    /** Lo primero que se ve: la cache vigente o, si no hay, las tarjetas que trae la app. Nunca usa la red ni falla. */
    fun rapido(ctx: Context): Resultado {
        val perfil = Intereses.cargar(ctx)
        val idioma = Idioma.resolver(ctx)
        val cache = leerCache(ctx)
        if (cache != null && cache.second == claveCache(idioma, perfil.temasParaBuscar()) && cache.third.isNotEmpty()) {
            val vigencia = if (cache.third.all { it.general }) VIGENCIA_GENERAL_MS else VIGENCIA_MS
            if (System.currentTimeMillis() - cache.first < vigencia) return Resultado(cache.third, true, 0, "cache")
        }
        val todas = try { ctx.assets.open("config/para_ti.json").bufferedReader(Charsets.UTF_8).use { Curadas.deJson(it.readText(), idioma) } } catch (e: Exception) { emptyList() }
        return Resultado(Curadas.elegir(todas, perfil), true, 0, "curadas")
    }

    // ---------- obtener ----------
    private const val VIGENCIA_GENERAL_MS = 30L * 60 * 1000   // si fueron de respaldo, se vuelve a intentar con busqueda pronto

    private fun sinCupo(e: Exception) = e is com.google.firebase.ai.type.QuotaExceededException || e is com.google.firebase.ai.type.ServerException

    /**
     * Devuelve las tarjetas. Si hay cache vigente (mismo idioma y mismos temas) y no se pide [forzar], no gasta ninguna llamada.
     * Primero intenta con busqueda (noticias y datos de hoy); si no hay cupo o el servicio no responde, usa datos de conocimiento general
     * (marcados como tales). Lanza [NubeApagada] si hace falta traer tarjetas nuevas y la nube esta apagada.
     */
    suspend fun obtener(ctx: Context, forzar: Boolean = false): Resultado {
        val perfil = Intereses.cargar(ctx)
        val ids = perfil.temasParaBuscar()
        val idioma = Idioma.resolver(ctx)
        val clave = claveCache(idioma, ids)
        val cache = leerCache(ctx)
        if (!forzar && cache != null && cache.second == clave && cache.third.isNotEmpty()) {
            val vigencia = if (cache.third.all { it.general }) VIGENCIA_GENERAL_MS else VIGENCIA_MS
            if (System.currentTimeMillis() - cache.first < vigencia) return Resultado(cache.third, true, 0)
        }
        Privacidad.exigirNube()
        if (ids.isEmpty()) return Resultado(emptyList(), false, 0)
        val temas = ids.mapNotNull { Intereses.tema(it) }
        val pais = Locale.getDefault().country.ifBlank { "US" }
        val fecha = java.time.LocalDate.now().toString()
        val t0 = System.currentTimeMillis()
        var crudas: String
        var motivo = ""
        var lista = try {
            crudas = withTimeout(60_000) { model(true).generateContent(prompt(idioma, pais, fecha, temas, perfil.vistos)) }.text.orEmpty()
            Tarjetas.parsear(crudas, vistos = perfil.vistos, temasValidos = ids.toSet())
        } catch (e: Exception) {
            SpikeLog.log("FEED: la busqueda fallo tras ${System.currentTimeMillis() - t0} ms (${e::class.simpleName}); se usa conocimiento general")
            if (!sinCupo(e)) throw e
            motivo = e::class.simpleName.orEmpty(); crudas = ""; emptyList()
        }
        if (lista.isEmpty()) {
            val sinNoticias = temas.filter { it.id != "noticias" }
            if (sinNoticias.isNotEmpty()) {
                val p = promptGeneral(idioma, sinNoticias, perfil.vistos)
                crudas = try {
                    withTimeout(45_000) { model(false).generateContent(p) }.text.orEmpty()
                } catch (e: Exception) {
                    // "Alta demanda" del servicio: se prueba una vez con el modelo de respaldo.
                    if (e !is com.google.firebase.ai.type.ServerException) throw e
                    SpikeLog.log("FEED: el modelo esta saturado (${e.message?.take(60)}); pruebo ${MODELO_RESPALDO}")
                    withTimeout(45_000) { model(false, MODELO_RESPALDO).generateContent(p) }.text.orEmpty()
                }
                lista = Tarjetas.parsear(crudas, vistos = perfil.vistos, temasValidos = sinNoticias.map { it.id }.toSet())
            }
        }
        val ms = System.currentTimeMillis() - t0
        SpikeLog.log("FEED: $ms ms | temas=$ids | respaldo=${lista.any { it.general }} $motivo | recibidas=${crudas.length} car | validas=${lista.size} | titulos=${lista.map { it.titulo.take(40) }}")
        if (lista.isNotEmpty()) guardarCache(ctx, clave, lista)
        return Resultado(lista, false, ms)
    }

    /** Solo depuracion: mide cuanto tarda una generacion sin busqueda con un prompt corto (a) o el completo (b). */
    suspend fun sonda(ctx: Context, variante: String) {
        val t0 = System.currentTimeMillis()
        try {
            val p = if (variante == "a" || variante == "d") "Dame 3 datos curiosos de los gatos como arreglo JSON con titulo y resumen."
            else if (variante == "c") "Responde solo: hola."
            else promptGeneral("es", Intereses.TEMAS.filter { it.id in setOf("musica", "cocina", "jardin") }, emptyList())
            val m = if (variante == "d") Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(modelName = CLOUD_MODEL) else model(false)
            val r = withTimeout(120_000) { m.generateContent(p) }
            SpikeLog.log("FEED sonda $variante: ${System.currentTimeMillis() - t0} ms, ${r.text.orEmpty().length} car | ${r.text.orEmpty().take(200)}")
        } catch (e: Exception) { SpikeLog.log("FEED sonda $variante: error tras ${System.currentTimeMillis() - t0} ms ${e::class.simpleName}: ${e.message?.take(120)}") }
    }
}
