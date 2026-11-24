package com.aidly.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Datos de fuentes publicas hechas para que otros las lean (sin usar la busqueda de Google de Gemini, que tiene cupo limitado):
 *  - Wikipedia "un dia como hoy" (API REST oficial; texto con licencia CC BY-SA: se cita como "Wikipedia").
 *  - Titulares RSS de la BBC (BBC Mundo / BBC News), pensados para sindicacion no comercial.
 *  - Clima de Open-Meteo (gratis y sin clave).
 * Salir a internet es salir del telefono: TODO esto exige la nube encendida (`Privacidad.exigirNube`), igual que Gemini. Las partes puras
 * (leer el JSON o el XML) se prueban sin red.
 */
object FuentesWeb {
    private const val UA = "Aidly/0.1 (Android; concurso #BuildHybridAI)"
    private const val MAX_BYTES = 1_500_000

    // ====================== Partes puras ======================

    /** Efemerides de Wikipedia convertidas en tarjetas ("historia"); las que hablan de muertes, guerras, etc. se descartan. */
    fun efemerides(json: String, idioma: String, hoy: LocalDate, max: Int = 2): List<Tarjeta> {
        val en = idioma == "en"
        val eventos = try { JSONObject(json).optJSONArray("events") } catch (e: Exception) { null } ?: return emptyList()
        val fecha = hoy.format(DateTimeFormatter.ofPattern(if (en) "MMMM d" else "d 'de' MMMM", if (en) Locale.ENGLISH else Locale.forLanguageTag("es")))
        val candidatas = (0 until eventos.length()).mapNotNull { i ->
            val o = eventos.optJSONObject(i) ?: return@mapNotNull null
            val texto = o.optString("text").trim()
            val anio = o.optInt("year", 0)
            if (texto.length < 25 || anio == 0) return@mapNotNull null
            val titulo = if (en) "On $fecha, $anio" else "El $fecha de $anio"
            Tarjeta(titulo, texto.take(300), "historia", "Wikipedia", hoy.toString())
        }.filter { Tarjetas.esSegura(it) }
        if (candidatas.isEmpty()) return emptyList()
        // Estable durante el dia (no cambia al reabrir) pero distinta cada dia.
        val desde = hoy.dayOfYear % candidatas.size
        return (candidatas.drop(desde) + candidatas.take(desde)).take(max)
    }

    /** Titulares de un RSS como tarjetas de "noticias", sin los temas delicados. [fuente]: nombre del medio. */
    fun titulares(xml: String, fuente: String, max: Int = 3): List<Tarjeta> {
        if (xml.contains("<!DOCTYPE", ignoreCase = true)) return emptyList()            // nada de entidades externas
        return try {
            val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream(Charsets.UTF_8))
            val items = doc.getElementsByTagName("item")
            val formato = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)
            (0 until items.length).mapNotNull { i ->
                val it = items.item(i)
                fun hijo(n: String) = (0 until it.childNodes.length).map { k -> it.childNodes.item(k) }.firstOrNull { c -> c.nodeName == n }?.textContent?.trim().orEmpty()
                val titulo = hijo("title").take(120)
                val resumen = hijo("description").replace(Regex("<[^>]*>"), "").replace(Regex("\\s+"), " ").trim().take(300)
                if (titulo.length < 15) return@mapNotNull null
                val fecha = try { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(formato.parse(hijo("pubDate"))!!) } catch (e: Exception) { "" }
                Tarjeta(titulo, resumen.ifBlank { titulo }, "noticias", fuente, fecha)
            }.filter { Tarjetas.esSegura(it) }.take(max)
        } catch (e: Exception) { emptyList() }
    }

    /** "En Bogota", "in Madrid", "de Medellin" -> la ciudad, o null si la frase no nombra una. */
    fun ciudadDe(frase: String): String? {
        val t = frase.trim()
        var c = Regex("(?i)\\b(?:en|in)\\s+(?:la ciudad de\\s+|the city of\\s+)?([^?.!,]+)").findAll(t).lastOrNull()?.groupValues?.get(1)
            ?: Regex("\\bde\\s+([A-ZÁÉÍÓÚÑ][^?.!,]*)").find(t)?.groupValues?.get(1)
            ?: return null
        c = c.replace(Regex("(?i)\\s+(hoy|manana|mañana|ahora|esta tarde|esta noche|today|tomorrow|now|right now|tonight)$"), "").trim()
        if (c.isBlank() || c.length > 40 || Regex("(?i)^(el|la|los|las|mi|tu|este|esta|the|my|this)\\b").containsMatchIn(c)) return null
        return c.replaceFirstChar { it.uppercase() }
    }

    fun esClima(normalizada: String) =
        Regex("\\b(clima|tiempo hace|que tiempo|pronostico|llover|lluvia|temperatura|weather|forecast|rain|temperature)\\b").containsMatchIn(normalizada)

    /** Palabras para el codigo del tiempo de la OMM (el que usa Open-Meteo). */
    internal fun estado(codigo: Int, en: Boolean): String = when (codigo) {
        0 -> if (en) "clear" else "el cielo está despejado"
        1 -> if (en) "mostly clear" else "está casi despejado"
        2 -> if (en) "partly cloudy" else "está parcialmente nublado"
        3 -> if (en) "cloudy" else "está nublado"
        45, 48 -> if (en) "foggy" else "hay niebla"
        in 51..57 -> if (en) "drizzling" else "hay llovizna"
        in 61..67 -> if (en) "raining" else "está lloviendo"
        in 71..77 -> if (en) "snowing" else "está nevando"
        in 80..82 -> if (en) "showery" else "hay chubascos"
        in 95..99 -> if (en) "stormy" else "hay tormenta"
        else -> if (en) "changeable" else "el tiempo está cambiante"
    }

    /** Frase hablada con el clima, o null si el JSON no sirve. */
    fun textoClima(json: String, ciudad: String, idioma: String): String? = try {
        val j = JSONObject(json)
        val ahora = j.getJSONObject("current")
        val dia = j.getJSONObject("daily")
        val t = Math.round(ahora.getDouble("temperature_2m")).toInt()
        val max = Math.round(dia.getJSONArray("temperature_2m_max").getDouble(0)).toInt()
        val min = Math.round(dia.getJSONArray("temperature_2m_min").getDouble(0)).toInt()
        val en = idioma == "en"
        val est = estado(ahora.getInt("weather_code"), en)
        if (en) "Right now in $ciudad it is $t degrees and $est. Today the high will be $max and the low $min."
        else "Ahora en $ciudad hay $t grados y $est. Hoy la máxima será de $max y la mínima de $min."
    } catch (e: Exception) { null }

    // ====================== Red ======================

    internal suspend fun descargar(url: String): String? = withContext(Dispatchers.IO) {
        try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 8_000; c.readTimeout = 10_000
            c.setRequestProperty("User-Agent", UA); c.setRequestProperty("Accept", "application/json, application/xml, text/xml, */*")
            if (c.responseCode != 200) { SpikeLog.log("WEB: $url -> ${c.responseCode}"); return@withContext null }
            c.inputStream.use { entrada ->
                val salida = java.io.ByteArrayOutputStream(); val b = ByteArray(8192); var total = 0
                while (total < MAX_BYTES) { val n = entrada.read(b); if (n < 0) break; salida.write(b, 0, n); total += n }
                salida.toString("UTF-8")
            }
        } catch (e: Exception) { SpikeLog.log("WEB: fallo ${e::class.simpleName} en ${url.substringBefore('?')}"); null }
    }

    /** Tarjetas reales de internet para los temas elegidos (historia y noticias); nunca lanza, devuelve lo que haya. Exige la nube encendida. */
    suspend fun tarjetas(ctx: Context, idioma: String, temas: List<String>, vistos: Collection<String>): List<Tarjeta> {
        Privacidad.exigirNube()
        val en = idioma == "en"
        val hoy = LocalDate.now()
        val (historia, noticias) = coroutineScope {
            val h = async { if ("historia" in temas) descargar("https://${if (en) "en" else "es"}.wikipedia.org/api/rest_v1/feed/onthisday/events/${"%02d".format(hoy.monthValue)}/${"%02d".format(hoy.dayOfMonth)}")?.let { efemerides(it, idioma, hoy) }.orEmpty() else emptyList() }
            // En ingles se usan las secciones de ciencia y cultura (mas amables que la portada); BBC Mundo solo tiene la portada.
            val n = async {
                if ("noticias" !in temas) emptyList()
                else if (en) {
                    val ciencia = async { descargar("https://feeds.bbci.co.uk/news/science_and_environment/rss.xml")?.let { titulares(it, "BBC News", 2) }.orEmpty() }
                    val cultura = async { descargar("https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml")?.let { titulares(it, "BBC News", 2) }.orEmpty() }
                    (ciencia.await() + cultura.await()).sortedByDescending { it.fecha }
                } else descargar("https://feeds.bbci.co.uk/mundo/rss.xml")?.let { titulares(it, "BBC Mundo") }.orEmpty()
            }
            h.await() to n.await()
        }
        val nuevas = (noticias + historia).filter { normalizarTexto(it.titulo) !in vistos }
        SpikeLog.log("WEB: historia=${historia.size} noticias=${noticias.size} nuevas=${nuevas.size}")
        return nuevas
    }

    /** Clima de la ciudad que nombra la frase (o la ultima que se pregunto). null si no hay ciudad conocida o falla algo. Exige la nube encendida. */
    suspend fun clima(ctx: Context, frase: String): String? {
        Privacidad.exigirNube()
        val idioma = Idioma.resolver(ctx)
        val prefs = ctx.getSharedPreferences("ajustes", Context.MODE_PRIVATE)
        val ciudad = ciudadDe(frase) ?: prefs.getString("ultimaCiudad", null) ?: return null
        val q = URLEncoder.encode(ciudad, "UTF-8")
        val geo = descargar("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=$idioma&format=json") ?: return null
        val r = try { JSONObject(geo).optJSONArray("results")?.optJSONObject(0) } catch (e: Exception) { null } ?: return null
        val nombre = r.optString("name", ciudad)
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${r.getDouble("latitude")}&longitude=${r.getDouble("longitude")}" +
            "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
        val texto = textoClima(descargar(url) ?: return null, nombre, idioma) ?: return null
        prefs.edit().putString("ultimaCiudad", nombre).apply()
        return texto
    }
}
