package com.aidly.app

import android.content.Context
import androidx.annotation.StringRes
import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer

/** Un tema de interes de la lista. [buscaEs]/[buscaEn] es como se le pide al buscador (en el idioma de la persona). */
class TemaInteres(val id: String, @StringRes val nombre: Int, val buscaEs: String, val buscaEn: String)

/** Texto sin tildes, en minusculas y solo letras y numeros: sirve para comparar titulos y aplicar filtros. */
fun normalizarTexto(s: String): String =
    Normalizer.normalize(s.lowercase(java.util.Locale.ROOT), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

/**
 * Lo que a la persona le gusta, para que Aidly le traiga cosas de su interes ("Para ti"). Pura (sin Android) para poder probarla.
 * Se guarda cifrada solo en el telefono. [reacciones]: -3 (no me interesa) a +3 (me interesa mucho) por tema.
 * [vistos]: titulos normalizados de lo ya mostrado, para no repetir. [propuestas]: la persona acepto que Aidly le ofrezca cosas por voz.
 */
class Perfil(
    val elegidos: Set<String> = emptySet(),
    val reacciones: Map<String, Int> = emptyMap(),
    val vistos: List<String> = emptyList(),
    val propuestas: Boolean = false,
    /** "No mas por hoy": no se ofrece nada hasta esta hora (ms desde 1970). */
    val noMasHasta: Long = 0L,
    /** Frases libres de lo que le gusta leer o ver ("novelas de misterio", "el Deportivo Cali"): hasta [MAX_GUSTOS], cortas y sin datos personales. */
    val gustos: List<String> = emptyList(),
    /** Ya se le pregunto "¿que te gusta leer o ver?" (para no insistir). */
    val preguntado: Boolean = false,
) {
    fun conElegido(id: String, on: Boolean) = copiar(elegidos = if (on) elegidos + id else elegidos - id)

    fun conReaccion(id: String, delta: Int) = copiar(reacciones = reacciones + (id to ((reacciones[id] ?: 0) + delta).coerceIn(-3, 3)))

    fun conVisto(titulo: String): Perfil {
        val n = normalizarTexto(titulo)
        if (n.isBlank()) return this
        return copiar(vistos = (vistos.filterNot { it == n } + n).takeLast(MAX_VISTOS))
    }

    fun haVisto(titulo: String) = normalizarTexto(titulo) in vistos

    fun conPropuestas(v: Boolean) = copiar(propuestas = v)
    fun conPreguntado(v: Boolean) = copiar(preguntado = v)

    /** Agrega una frase de gusto si es valida (ver [limpiarGusto]); la mas antigua sale si ya hay [MAX_GUSTOS]. */
    fun conGusto(frase: String): Perfil {
        val g = limpiarGusto(frase) ?: return this
        if (gustos.any { normalizarTexto(it) == normalizarTexto(g) }) return this
        return copiar(gustos = (gustos + g).takeLast(MAX_GUSTOS), preguntado = true)
    }
    fun sinGusto(frase: String) = copiar(gustos = gustos - frase)

    /** Puntos de una tarjeta segun las frases de gusto: cuantas palabras (de 4 letras o mas) de los gustos aparecen en su titulo y resumen. */
    fun puntos(titulo: String, resumen: String): Int {
        if (gustos.isEmpty()) return 0
        val palabras = normalizarTexto("$titulo $resumen").split(" ").filter { it.isNotEmpty() }.toSet()
        return gustos.flatMap { palabrasClave(it) }.distinct().count { clave ->
            val raiz = clave.removeSuffix("es").removeSuffix("s")
            // Misma palabra o su plural; y, si es larga (6 letras o mas), tambien el comienzo de otra ("misterio" ~ "misteriosa"), no "cali" ~ "california".
            palabras.any { w -> w == clave || w == raiz || w == raiz + "s" || w == raiz + "es" || (raiz.length >= 6 && w.startsWith(raiz)) }
        }
    }
    fun conNoMasHasta(ms: Long) = copiar(noMasHasta = ms)

    /** Temas a pedir, de mas a menos interesantes; los que la persona rechazo dos veces ya no salen. */
    fun temasParaBuscar(): List<String> =
        elegidos.filter { (reacciones[it] ?: 0) > -2 }.sortedWith(compareByDescending<String> { reacciones[it] ?: 0 }.thenBy { it })

    private fun copiar(
        elegidos: Set<String> = this.elegidos, reacciones: Map<String, Int> = this.reacciones, vistos: List<String> = this.vistos,
        propuestas: Boolean = this.propuestas, noMasHasta: Long = this.noMasHasta,
        gustos: List<String> = this.gustos, preguntado: Boolean = this.preguntado,
    ) = Perfil(elegidos, reacciones, vistos, propuestas, noMasHasta, gustos, preguntado)

    fun aJson(): String = JSONObject().apply {
        put("elegidos", JSONArray(elegidos.sorted()))
        put("reacciones", JSONObject(reacciones.mapValues { it.value }))
        put("vistos", JSONArray(vistos))
        put("propuestas", propuestas)
        put("noMasHasta", noMasHasta)
        put("gustos", JSONArray(gustos))
        put("preguntado", preguntado)
    }.toString()

    companion object {
        const val MAX_VISTOS = 60
        const val MAX_GUSTOS = 8
        private const val MAX_LARGO_GUSTO = 40
        private val SIN_RELLENO = setOf("para", "como", "esto", "esta", "este", "muy", "mucho", "mucha", "gusta", "gustan", "quiero", "leer", "ver", "mirar", "with", "that", "this", "about", "like", "love", "read", "watch", "the", "and")

        /** Frase de gusto limpia, o null si esta vacia o parece un dato personal (correo, telefono, mas de 6 digitos seguidos). */
        fun limpiarGusto(frase: String): String? {
            val t = frase.replace(Regex("\\s+"), " ").trim().trim('.', ',', ';', ':', '!', '?').take(MAX_LARGO_GUSTO).trim()
            if (t.length < 3) return null
            if ("@" in t || Regex("\\d{7,}|(\\d[ -]?){9,}").containsMatchIn(t)) return null
            return t
        }

        /** Palabras utiles de una frase de gusto para comparar con titulares (sin tildes, de 4 letras o mas, sin relleno). */
        fun palabrasClave(frase: String): List<String> = normalizarTexto(frase).split(" ").filter { it.length >= 4 && it !in SIN_RELLENO }

        fun deJson(texto: String?): Perfil {
            if (texto.isNullOrBlank()) return Perfil()
            return try {
                val j = JSONObject(texto)
                fun lista(k: String) = j.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty()
                val r = j.optJSONObject("reacciones")
                Perfil(
                    elegidos = lista("elegidos").toSet(),
                    reacciones = r?.keys()?.asSequence()?.associateWith { r.getInt(it).coerceIn(-3, 3) }.orEmpty(),
                    vistos = lista("vistos").takeLast(MAX_VISTOS),
                    propuestas = j.optBoolean("propuestas", false),
                    noMasHasta = j.optLong("noMasHasta", 0L),
                    gustos = lista("gustos").mapNotNull { limpiarGusto(it) }.takeLast(MAX_GUSTOS),
                    preguntado = j.optBoolean("preguntado", false),
                )
            } catch (e: Exception) { Perfil() }
        }
    }
}

/** Almacen del perfil: preferencias privadas de la app, cifradas con el Keystore (igual que recordatorios y favoritos). */
object Intereses {
    const val ALMACEN = "intereses"
    private const val CLAVE = "perfil"

    val TEMAS = listOf(
        TemaInteres("familia", R.string.int_familia, "consejos sencillos para la convivencia y la familia", "simple tips for family life"),
        TemaInteres("musica", R.string.int_musica, "musica clasica y popular: artistas, canciones y anecdotas", "music: artists, songs and stories"),
        TemaInteres("deportes", R.string.int_deportes, "deportes: resultados y noticias amables", "sports: results and friendly news"),
        TemaInteres("cocina", R.string.int_cocina, "recetas faciles y tradicionales", "easy traditional recipes"),
        TemaInteres("jardin", R.string.int_jardin, "jardineria y cuidado de plantas", "gardening and plant care"),
        TemaInteres("animales", R.string.int_animales, "animales y naturaleza: datos curiosos", "animals and nature: fun facts"),
        TemaInteres("historia", R.string.int_historia, "historia: hechos y personajes interesantes", "history: interesting facts and people"),
        TemaInteres("viajes", R.string.int_viajes, "viajes y lugares bonitos para conocer", "travel and beautiful places to visit"),
        TemaInteres("humor", R.string.int_humor, "humor sano, chistes y anecdotas", "clean humor, jokes and anecdotes"),
        TemaInteres("tecnologia", R.string.int_tecnologia, "tecnologia explicada de forma sencilla", "technology explained simply"),
        TemaInteres("noticias", R.string.int_noticias, "noticias positivas o de servicio del pais", "positive or public-service news from the country"),
        TemaInteres("salud", R.string.int_salud, "habitos saludables en general (ejercicio, sueno, alimentacion)", "general healthy habits (exercise, sleep, nutrition)"),
    )

    fun tema(id: String) = TEMAS.firstOrNull { it.id == id }

    fun cargar(ctx: Context): Perfil =
        Perfil.deJson(Cifrado.abrir(ctx.getSharedPreferences(ALMACEN, Context.MODE_PRIVATE).getString(CLAVE, null)))

    @Synchronized
    fun cambiar(ctx: Context, f: (Perfil) -> Perfil): Perfil {
        val nuevo = f(cargar(ctx))
        ctx.getSharedPreferences(ALMACEN, Context.MODE_PRIVATE).edit().putString(CLAVE, Cifrado.cerrar(nuevo.aJson())).apply()
        return nuevo
    }
}
