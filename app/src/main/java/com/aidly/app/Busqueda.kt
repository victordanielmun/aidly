package com.aidly.app

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.Tool
import kotlinx.coroutines.withTimeout

/**
 * Busqueda en internet por voz ("como esta el clima", "cuanto cuesta el dolar", "busca una receta de pozole").
 * Usa Gemini en la nube con "Google Search grounding": el modelo busca y responde corto y sencillo.
 * Solo funciona con internet y la pregunta sale a la nube (a diferencia de recordatorios, que se resuelven en el telefono).
 * Las herramientas no tienen modo local, por eso aqui no se usa PREFER_ON_DEVICE.
 */
@OptIn(PublicPreviewAPI::class)
object Busqueda {
    private const val CLOUD_MODEL = "gemini-3.5-flash-lite"

    class Respuesta(val texto: String, val ms: Long, val fuentes: List<String>)

    private const val SISTEMA =
        "Eres Aidly, un companero amable para una persona mayor. Busca en internet y responde en espanol, " +
            "con maximo 3 frases cortas y sencillas, sin tecnicismos, listas, emojis ni enlaces. " +
            "Da primero el dato que preguntaron (cifra, hora, resultado). Si el tema es de salud, dinero o " +
            "leyes, da informacion general y recomienda confirmarlo con un profesional. " +
            "Si no encuentras algo confiable, dilo con honestidad."

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = CLOUD_MODEL,
            tools = listOf(Tool.googleSearch()),
        )
    }

    suspend fun responder(pregunta: String): Respuesta {
        val t0 = System.currentTimeMillis()
        val r = withTimeout(40_000) { model.generateContent("$SISTEMA\n\nLa persona pregunta: $pregunta\n\nTu respuesta:") }
        val ms = System.currentTimeMillis() - t0
        val texto = r.text?.trim().orEmpty().ifBlank { "No encontré nada confiable sobre eso." }
        val fuentes = r.candidates.firstOrNull()?.groundingMetadata?.groundingChunks
            ?.mapNotNull { it.web?.title }?.distinct().orEmpty()
        SpikeLog.log("BUSQUEDA: $ms ms | pregunta='$pregunta' | fuentes=${fuentes.take(4)} | respuesta='${texto.take(160)}'")
        return Respuesta(texto, ms, fuentes)
    }
}
