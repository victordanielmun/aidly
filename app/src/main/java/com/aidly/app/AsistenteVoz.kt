package com.aidly.app

import com.google.firebase.Firebase
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import kotlinx.coroutines.withTimeout

/**
 * "Hablar con Aidly": pregunta de un solo turno, respuesta corta y sencilla.
 * Es el caso ideal para el modo hibrido PREFER_ON_DEVICE: texto corto, un turno, sin herramientas.
 * Con Gemini Nano responde en el telefono; sin el (p. ej. el Galaxy A23) cae solo a la nube.
 * Ojo: el modelo local solo esta validado en ingles y coreano; en espanol hay que comprobar su calidad.
 */
@OptIn(PublicPreviewAPI::class)
object AsistenteVoz {
    private const val CLOUD_MODEL = "gemini-3.5-flash-lite"

    class Respuesta(val texto: String, val fuente: String, val ms: Long)

    private const val SISTEMA =
        "Eres Aidly, un companero amable para una persona mayor. Responde en espanol, con frases cortas y " +
            "sencillas (maximo 3), sin tecnicismos ni listas ni emojis. Si preguntan algo de salud, da " +
            "informacion general y recomienda consultar al medico. Si no sabes algo, dilo con honestidad."

    private val modelHibrido by lazy { crear(InferenceMode.PREFER_ON_DEVICE) }
    private val modelLocal by lazy { crear(InferenceMode.ONLY_ON_DEVICE) }

    private fun crear(modo: InferenceMode) =
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = CLOUD_MODEL,
            onDeviceConfig = OnDeviceConfig(mode = modo, maxOutputTokens = 200),
            generationConfig = com.google.firebase.ai.type.generationConfig {
                // Respuesta corta de conversacion: sin razonamiento largo, para que conteste rapido.
                thinkingConfig = com.google.firebase.ai.type.thinkingConfig {
                    thinkingLevel = com.google.firebase.ai.type.ThinkingLevel.MINIMAL
                }
            },
        )

    suspend fun responder(pregunta: String): Respuesta {
        val t0 = System.currentTimeMillis()
        // Nube apagada: solo Gemini Nano en el telefono (lanza excepcion si no hay).
        val model = if (Privacidad.nubeActiva()) modelHibrido else modelLocal
        val r = withTimeout(30_000) {
            model.generateContent("$SISTEMA\n\nLa persona dice: $pregunta\n\nTu respuesta:")
        }
        val ms = System.currentTimeMillis() - t0
        val texto = r.text?.trim().orEmpty().ifBlank { "No supe que responderte. Puedes preguntarme de otra forma?" }
        SpikeLog.log("HABLAR: fuente=${r.inferenceSource} $ms ms | pregunta='$pregunta' | respuesta='${texto.take(160)}'")
        return Respuesta(texto, r.inferenceSource.toString(), ms)
    }
}
