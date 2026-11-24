package com.aidly.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import com.google.firebase.Firebase
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

/**
 * Pilar 2: lee una caja o blister de medicamento con la camara.
 * Modo hibrido PREFER_IN_CLOUD: la nube (mas precisa con letra pequena) y, si no hay red y el telefono
 * tiene Gemini Nano, el modelo local. Solo se lee lo impreso: nunca se inventa una dosis.
 */
@OptIn(PublicPreviewAPI::class)
object MedicinaValidador {
    private const val CLOUD_MODEL = "gemini-3.5-flash-lite"
    private const val MAX_LADO = 1792

    class Resultado(
        val legible: Boolean,
        val nombre: String,
        val principio: String,
        val concentracion: String,
        val forma: String,
        val indicaciones: String,
        val advertencias: String,
        val confianza: String,
        val resumenVoz: String,
        val fuente: String,
        val ms: Long,
    ) {
        /** Solo se da por bueno si se leyo el nombre y la confianza no es baja. */
        val confiable get() = legible && nombre.isNotBlank() && confianza != "baja"
    }

    private const val PROMPT =
        "Eres un asistente para adultos mayores. Mira la foto de una caja o blister de medicamento y lee " +
            "SOLO lo que se ve impreso. No inventes datos que no se vean ni los completes con lo que sepas. " +
            "Si algo no se lee con claridad, deja ese campo vacio (\"\"). " +
            "Responde SOLO un objeto JSON con estas claves: " +
            "legible (true/false: se puede leer el nombre del medicamento), nombre_comercial, principio_activo, " +
            "concentracion, forma (tabletas, jarabe, crema...), " +
            "indicaciones_en_la_caja (dosis o modo de uso SOLO si estan impresas), advertencias, " +
            "confianza (alta, media o baja), " +
            "resumen_para_voz (2 o 3 frases muy cortas, en espanol sencillo y sin tecnicismos, como se lo " +
            "dirias a una persona mayor: que medicina es, para que dice la caja que sirve si esta impreso, " +
            "y lo que indica la caja sobre como tomarla si esta impreso)."

    private val esquema = Schema.obj(
        mapOf(
            "legible" to Schema.boolean("true si se puede leer con claridad el nombre del medicamento"),
            "nombre_comercial" to Schema.string("Nombre comercial impreso, o cadena vacia"),
            "principio_activo" to Schema.string("Principio activo impreso, o cadena vacia"),
            "concentracion" to Schema.string("Concentracion impresa (por ejemplo 500 mg), o cadena vacia"),
            "forma" to Schema.string("Forma farmaceutica, o cadena vacia"),
            "indicaciones_en_la_caja" to Schema.string("Dosis o modo de uso solo si estan impresos, o cadena vacia"),
            "advertencias" to Schema.string("Advertencias impresas, o cadena vacia"),
            "confianza" to Schema.enumeration(listOf("alta", "media", "baja"), "Confianza en la lectura"),
            "resumen_para_voz" to Schema.string("Resumen en espanol sencillo para leer en voz alta"),
        ),
    )

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = CLOUD_MODEL,
            onDeviceConfig = OnDeviceConfig(mode = InferenceMode.PREFER_IN_CLOUD),
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = esquema
                temperature = 0f
            },
        )
    }

    suspend fun analizar(foto: Bitmap): Resultado {
        Privacidad.exigirNube()
        val bmp = reducir(foto)
        val t0 = System.currentTimeMillis()
        val r = withTimeout(30_000) {
            model.generateContent(
                content {
                    image(bmp)
                    text(if (Idioma.ultimo == "en") PROMPT + " The app language is ENGLISH: write ALL the text values (names read aloud, summaries, data, advice) in simple English, even if the text in the photo is in another language (keep product names and numbers as printed). Keep the JSON keys and the fixed values (alto/medio/bajo/no_aplica, alta/media/baja, tipo) exactly as specified." else PROMPT)
                },
            )
        }
        val ms = System.currentTimeMillis() - t0
        val crudo = r.text.orEmpty()
        SpikeLog.log("MEDICINA: fuente=${r.inferenceSource} ${ms} ms  json=${crudo.take(300)}")
        val j = JSONObject(crudo.substringAfter("{", "").let { "{$it" }.substringBeforeLast("}") + "}")
        fun s(k: String) = j.optString(k, "").trim()
        return Resultado(
            legible = j.optBoolean("legible", false),
            nombre = s("nombre_comercial"),
            principio = s("principio_activo"),
            concentracion = s("concentracion"),
            forma = s("forma"),
            indicaciones = s("indicaciones_en_la_caja"),
            advertencias = s("advertencias"),
            confianza = s("confianza").ifBlank { "baja" },
            resumenVoz = s("resumen_para_voz"),
            fuente = r.inferenceSource.toString(),
            ms = ms,
        )
    }

    // ---------- Preguntas sobre la medicina identificada ----------

    /** Frases que NO se responden con el modelo: son de riesgo y la respuesta correcta es siempre acudir a un profesional. */
    private val URGENTE = Regex(
        "\\b(sobredosis|me pase|tome de mas|tome demas|me tome (dos|tres|varias|todas|el frasco|toda)|doble dosis|" +
            "alergia|alergico|reaccion|me hizo mal|me siento mal|me siento raro|me duele el pecho|no puedo respirar|" +
            "mareo|desmayo|vomito|sangrado|envenen)\\w*\\b",
    )
    private val DOSIS_OLVIDO = Regex("\\b(se me olvido|olvide|me falto|me salte|puedo tomar (dos|el doble)|tomo (dos|doble)|me tomo (dos|doble))\\b")

    private val URGENTE_EN = Regex(
        "\\b(overdose|took too much|took too many|took double|double dose|allerg\\w*|reaction|made me sick|i feel (sick|bad|strange|weird|dizzy)|" +
            "chest pain|can t breathe|cant breathe|cannot breathe|dizz\\w*|faint\\w*|vomit\\w*|bleeding|poison\\w*)\\b",
    )
    private val DOSIS_OLVIDO_EN = Regex("\\b(i forgot|forgot to take|missed a dose|missed my dose|skipped|can i take (two|double)|took (two|double)|take (two|double))\\b")

    private fun plano(s: String) =
        java.text.Normalizer.normalize(s.lowercase(java.util.Locale.ROOT), java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")

    private val modeloPreguntas by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = CLOUD_MODEL,
            generationConfig = generationConfig {
                temperature = 0.2f
                thinkingConfig = com.google.firebase.ai.type.thinkingConfig {
                    thinkingLevel = com.google.firebase.ai.type.ThinkingLevel.MINIMAL
                }
            },
        )
    }

    /**
     * Responde una duda sobre la medicina identificada. Informacion general y sencilla, nunca una dosis distinta
     * de la de la caja. Los casos de riesgo (sobredosis, alergias, sintomas, dosis olvidadas o dobles) no pasan por
     * el modelo: se remite al medico, al farmaceutico o a emergencias.
     */
    /** Respuesta fija si la pregunta es de riesgo (sobredosis, alergia, sintomas, dosis olvidada o doble); si no, null. */
    fun respuestaSegura(ctx: Context, pregunta: String): String? {
        val p = plano(pregunta)
        if (Idioma.resolver(ctx) == "en") {
            if (URGENTE_EN.containsMatchIn(p)) return Idioma.vozTexto(ctx, R.string.v_riesgo_urgente, Emergencia.numero(ctx))
            if (DOSIS_OLVIDO_EN.containsMatchIn(p)) return Idioma.vozTexto(ctx, R.string.v_riesgo_dosis)
            return null
        }
        if (URGENTE.containsMatchIn(p)) return Idioma.vozTexto(ctx, R.string.v_riesgo_urgente, Emergencia.numero(ctx))
        if (DOSIS_OLVIDO.containsMatchIn(p)) return Idioma.vozTexto(ctx, R.string.v_riesgo_dosis)
        return null
    }

    suspend fun preguntar(ctx: Context, r: Resultado, pregunta: String): String {
        respuestaSegura(ctx, pregunta)?.let { return it }
        Privacidad.exigirNube()
        val datos = listOf(
            "Nombre" to r.nombre, "Principio activo" to r.principio, "Concentración" to r.concentracion,
            "Forma" to r.forma, "Indicaciones impresas en la caja" to r.indicaciones, "Advertencias impresas" to r.advertencias,
        ).filter { it.second.isNotBlank() }.joinToString("\n") { "${it.first}: ${it.second}" }
        val prompt = (if (Idioma.ultimo == "en") "IMPORTANT: answer the person in simple English (the instructions below are in Spanish). " else "") +
            "Eres Aidly, un compañero amable para una persona mayor. La persona tiene en la mano una medicina; " +
                "esto es lo que se leyó de su caja:\n$datos\n\n" +
                "Responde su pregunta en español sencillo, máximo 4 frases cortas, sin tecnicismos, sin listas ni emojis. " +
                "Reglas estrictas: da solo información general y educativa; nunca indiques una dosis, un horario o una " +
                "frecuencia distintos de los impresos en la caja; no digas que se puede combinar con otras medicinas, alcohol " +
                "o alimentos si no estás seguro; no diagnostiques; si la caja no lo dice o no estás seguro, dilo y recomienda " +
                "preguntar a su médico o farmacéutico. Termina siempre recordando que la decisión es de su médico o farmacéutico.\n\n" +
                "Pregunta de la persona: $pregunta\n\nTu respuesta:"
        val t0 = System.currentTimeMillis()
        val resp = withTimeout(30_000) { modeloPreguntas.generateContent(prompt) }
        val texto = resp.text?.trim().orEmpty()
            .ifBlank { Idioma.vozTexto(ctx, R.string.v_no_supe_medicina) }
        SpikeLog.log("MEDICINA pregunta: ${System.currentTimeMillis() - t0} ms | '${pregunta.take(80)}' -> '${texto.take(160)}'")
        return texto
    }

    /** Reduce el lado mayor a MAX_LADO (la letra chica pide resolucion, pero la nube no necesita 12 MP). */
    private fun reducir(b: Bitmap): Bitmap {
        val m = maxOf(b.width, b.height)
        if (m <= MAX_LADO) return b
        val k = MAX_LADO.toFloat() / m
        return Bitmap.createScaledBitmap(b, (b.width * k).toInt(), (b.height * k).toInt(), true)
    }

    fun rotar(b: Bitmap, grados: Int): Bitmap {
        if (grados == 0) return b
        val m = Matrix().apply { postRotate(grados.toFloat()) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }
}
