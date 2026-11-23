package com.aidly.app

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.sqrt

/**
 * Transcripcion con Gemini (nube): graba hasta que la persona deja de hablar y manda el audio.
 * En las pruebas con la voz del usuario acerto 6/6 frases, contra 3/6 de Whistle.
 * Requiere el servicio en primer plano con microfono (Android 14+) y App Check configurado.
 */
object GeminiStt {
    private const val RATE = 16_000
    private const val FRAME = RATE / 10           // 100 ms
    private const val MIN_THR = 250               // rms minimo para considerar voz
    private const val ESPERA_VOZ_MS = 7_000       // sin voz en este tiempo -> null
    private const val SILENCIO_FRAMES = 13        // 1,3 s de silencio = fin de frase
    private const val MAX_FRAMES = 100            // 10 s maximo hablando
    private const val CLOUD_MODEL = "gemini-3.5-flash-lite"

    private const val PROMPT_ES =
        "Transcribe en espanol lo que dice la persona. Usa puntuacion y mayusculas correctas, " +
            "sin anadir ni cambiar palabras. Responde solo con la transcripcion. " +
            "Contexto: es una persona mayor hablando con su asistente de voz; suelen ser frases cortas " +
            "(por ejemplo: 'Que tengo hoy', 'Leeme los mensajes', 'Recuerdame tomar la pastilla', 'Llama a mi hija', " +
            "'Si', 'No'). Si dudas entre dos palabras, elige la que tenga sentido en ese contexto. " +
            "Nunca adivines ni inventes palabras a partir de ruido, musica o sonidos poco claros: los ejemplos son solo contexto, no los repitas. " +
            "Si no oyes palabras claras, responde exactamente: (silencio)"

    private val model by lazy {
        Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(modelName = CLOUD_MODEL)
    }

    /** Devuelve el texto, o null si no hubo voz. Lanza excepcion si falla el microfono o la nube. */
    suspend fun escuchar(ctx: android.content.Context): String? {
        Privacidad.exigirNube()
        val pcm = withContext(Dispatchers.IO) { grabarFrase(ctx) } ?: return null
        return transcribirWav(ctx, SttCompare.wav(pcm), pcm.size / 32)
    }

    /** Instruccion de transcripcion en el idioma de la app (la lista de frases de ejemplo sale del JSON de idiomas). */
    private fun prompt(ctx: android.content.Context): String {
        if (Idioma.resolver(ctx) == "es") return PROMPT_ES
        val ejemplos = Idioma.frasesContexto(ctx).joinToString(", ") { "'$it'" }
        return "Transcribe in ${Idioma.geminiIdioma(ctx)} what the person says. Use correct punctuation and capitalization, " +
            "without adding or changing words. Reply only with the transcription. " +
            "Context: an older adult speaking to their voice assistant; usually short phrases (for example: $ejemplos). " +
            "If you are unsure between two words, choose the one that makes sense in that context. " +
            "Never guess or invent words from noise, music or unclear sounds: the examples are only context, do not repeat them. " +
            "If you cannot clearly hear spoken words, reply exactly: (silence)"
    }

    /** Transcribe un WAV ya grabado (16 kHz mono PCM16). Lo usan las pruebas con audios sinteticos. */
    suspend fun transcribirWav(ctx: android.content.Context, wav: ByteArray, duracionMs: Int = 0): String? {
        Privacidad.exigirNube()
        val t0 = System.currentTimeMillis()
        val r = withTimeout(30_000) {
            model.generateContent(
                content {
                    inlineData(wav, "audio/wav")
                    text(prompt(ctx))
                },
            )
        }
        val texto = r.text?.trim().orEmpty()
        SpikeLog.log("GEMINI STT: nube ${System.currentTimeMillis() - t0} ms, audio $duracionMs ms")
        // El modelo a veces parte palabras ("(sil ence)"): se compara sin espacios.
        val pegado = texto.replace(" ", "")
        return if (texto.isBlank() || pegado.contains("(silencio)", ignoreCase = true) || pegado.contains("(silence)", ignoreCase = true)) null else texto
    }

    private fun rms(b: ByteArray): Double {
        var sum = 0.0
        var i = 0
        while (i + 1 < b.size) {
            val v = ((b[i + 1].toInt() shl 8) or (b[i].toInt() and 0xFF)).toShort().toInt()
            sum += v.toDouble() * v
            i += 2
        }
        return sqrt(sum / (b.size / 2))
    }

    /** Graba con deteccion simple de voz por energia. Devuelve null si nadie hablo. */
    private fun grabarFrase(ctx: android.content.Context): ByteArray? {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        @Suppress("MissingPermission")
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, RATE),
        )
        // Con el dock por USB, el microfono es el suyo (y se dice en pantalla); si no, el del telefono.
        AudioExterno.entrada(ctx)?.let { rec.setPreferredDevice(it); SpikeLog.log("MIC: uso el microfono USB ${it.productName}") }
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            rec.release(); throw IllegalStateException("microfono no disponible")
        }
        val frames = ArrayList<ByteArray>()
        rec.startRecording()
        try {
            var noise = 0.0
            var thr = MIN_THR.toDouble()
            var started = -1
            var hot = 0
            var quiet = 0
            var i = 0
            while (true) {
                val buf = ByteArray(FRAME * 2)
                var off = 0
                while (off < buf.size) {
                    val n = rec.read(buf, off, buf.size - off)
                    if (n <= 0) throw IOException("lectura de microfono fallo ($n)")
                    off += n
                }
                frames.add(buf)
                val level = rms(buf)
                if (i < 3) {
                    // Primeros 300 ms: nivel de ruido de fondo (con tope, por si queda cola del TTS).
                    noise = maxOf(noise, level)
                    if (i == 2) thr = minOf(1500.0, maxOf(MIN_THR.toDouble(), noise * 2.5))
                } else if (started < 0) {
                    if (level > thr) { if (++hot >= 2) started = i - 1 } else hot = 0
                    if (i * 100 > ESPERA_VOZ_MS) return null
                } else {
                    if (level > thr) quiet = 0 else quiet++
                    if (quiet >= SILENCIO_FRAMES || i - started > MAX_FRAMES) break
                }
                i++
            }
            val desde = maxOf(0, started - 3)
            val out = ByteArrayOutputStream()
            for (k in desde until frames.size) out.write(frames[k])
            return out.toByteArray()
        } finally {
            rec.stop(); rec.release()
        }
    }
}
