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

    private const val SISTEMA_EN =
        "You are Aidly, a kind companion for an older adult. Answer in English with short, simple sentences (maximum 3), " +
            "no jargon, lists or emojis. If they ask about health, give general information and recommend consulting their doctor. " +
            "If you do not know something, say so honestly. You cannot set reminders or alarms, make calls or send messages from this conversation: " +
            "never say or imply that you did. If the person asks for one of those, say you did not catch it well and ask them to say it again, " +
            "for example: remind me to take my pill at 8 tonight."

    private const val SISTEMA =
        "Eres Aidly, un companero amable para una persona mayor. Responde en espanol, con frases cortas y " +
            "sencillas (maximo 3), sin tecnicismos ni listas ni emojis. Si preguntan algo de salud, da " +
            "informacion general y recomienda consultar al medico. Si no sabes algo, dilo con honestidad. " +
            "Desde esta conversacion NO puedes crear recordatorios ni alarmas, ni hacer llamadas ni enviar mensajes: nunca digas ni insinues que lo hiciste. " +
            "Si te lo piden, di que no lo entendiste bien y pide que lo repitan, por ejemplo: recuerdame tomar la pastilla a las 8 de la noche."

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

    /** Fecha y hora de ahora, para que el modelo no diga que no sabe que dia es. */
    private fun ahora(en: Boolean): String =
        if (en) java.text.SimpleDateFormat("EEEE, MMMM d, yyyy, h:mm a", java.util.Locale.US).format(java.util.Date())
        else java.text.SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy, HH:mm", java.util.Locale("es", "MX")).format(java.util.Date())

    suspend fun responder(ctx: android.content.Context, pregunta: String): Respuesta {
        val en = Idioma.resolver(ctx) == "en"
        val t0 = System.currentTimeMillis()
        // Nube apagada: solo Gemini Nano en el telefono (lanza excepcion si no hay).
        val model = if (Privacidad.nubeActiva()) modelHibrido else modelLocal
        val r = withTimeout(30_000) {
            model.generateContent(if (en) "$SISTEMA_EN\n\nNow it is ${ahora(true)}.\n\nThe person says: $pregunta\n\nYour answer:" else "$SISTEMA\n\nAhora es ${ahora(false)}.\n\nLa persona dice: $pregunta\n\nTu respuesta:")
        }
        val ms = System.currentTimeMillis() - t0
        val texto = r.text?.trim().orEmpty().ifBlank { if (en) "I did not know what to answer. Could you ask me in another way?" else "No supe que responderte. Puedes preguntarme de otra forma?" }
        SpikeLog.log("HABLAR: fuente=${r.inferenceSource} $ms ms | pregunta='$pregunta' | respuesta='${texto.take(160)}'")
        return Respuesta(texto, r.inferenceSource.toString(), ms)
    }

    /** Conversacion sobre una tarjeta de "Para ti": el modelo ve la tarjeta y responde corto. No busca en internet (usa el modelo del telefono si existe). */
    suspend fun responderSobre(ctx: android.content.Context, tarjeta: Tarjeta, pregunta: String): Respuesta {
        val en = Idioma.resolver(ctx) == "en"
        val t0 = System.currentTimeMillis()
        val model = if (Privacidad.nubeActiva()) modelHibrido else modelLocal
        val contexto = if (en) "Card we are looking at: \"${tarjeta.titulo}\". ${tarjeta.resumen} (source: ${tarjeta.fuente}). Answer only about this card and general knowledge; if you do not know, say so."
        else "Tarjeta que estamos viendo: \"${tarjeta.titulo}\". ${tarjeta.resumen} (fuente: ${tarjeta.fuente}). Responde solo sobre esta tarjeta y conocimiento general; si no sabes, dilo."
        val r = withTimeout(40_000) {
            model.generateContent(if (en) "$SISTEMA_EN\n\n$contexto\n\nThe person says: $pregunta\n\nYour answer:" else "$SISTEMA\n\n$contexto\n\nLa persona dice: $pregunta\n\nTu respuesta:")
        }
        val ms = System.currentTimeMillis() - t0
        val texto = r.text?.trim().orEmpty().ifBlank { if (en) "I did not know what to answer. Could you ask me in another way?" else "No supe que responderte. Puedes preguntarme de otra forma?" }
        SpikeLog.log("PARATI: fuente=${r.inferenceSource} $ms ms | pregunta='$pregunta' | respuesta='${texto.take(160)}'")
        return Respuesta(texto, r.inferenceSource.toString(), ms)
    }
}

