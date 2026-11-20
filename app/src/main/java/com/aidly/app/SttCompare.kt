package com.aidly.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.ToneGenerator
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Comparacion de transcripcion con EL MISMO audio:
 *  1) se graba N segundos (16 kHz, mono, PCM) con la misma fuente que usa el reconocedor del sistema,
 *  2) se manda a Gemini (nube, audio) y 3) se le da al SpeechRecognizer de Android como archivo.
 */
object SttCompare {
    private const val RATE = 16_000
    private const val CLOUD_MODEL = "gemini-3.5-flash-lite"

    suspend fun run(ctx: Context, seconds: Int = 5) {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            SpikeLog.log("COMPARAR: falta permiso de microfono"); return
        }
        SpikeLog.log("COMPARAR: di tu frase cuando suene el tono (${seconds}s)")
        ToneGenerator(AudioManager.STREAM_MUSIC, 90).startTone(ToneGenerator.TONE_PROP_BEEP, 200)
        delay(450)

        val pcm = withContext(Dispatchers.IO) { grabar(seconds) } ?: return
        // Se guarda el WAV para poder evaluar otros modelos (p. ej. Whistle) con el mismo audio:
        //   adb pull /sdcard/Android/data/com.aidly.app/files/comparar
        withContext(Dispatchers.IO) {
            val dir = java.io.File(ctx.getExternalFilesDir(null), "comparar").apply { mkdirs() }
            val f = java.io.File(dir, "clip_${System.currentTimeMillis()}.wav")
            f.writeBytes(wav(pcm))
            SpikeLog.log("COMPARAR: audio guardado ${f.name}")
        }
        SpikeLog.log("COMPARAR: grabado. Transcribiendo...")

        val gemini = withContext(Dispatchers.IO) { conGemini(pcm) }
        SpikeLog.log("COMPARAR Gemini  -> $gemini")

        val sistema = withContext(Dispatchers.Main) { conSistema(ctx, pcm) }
        SpikeLog.log("COMPARAR Android -> $sistema")
    }

    private fun grabar(seconds: Int): ByteArray? {
        val min = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        @Suppress("MissingPermission")
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION, RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, RATE),
        )
        if (rec.state != AudioRecord.STATE_INITIALIZED) {
            SpikeLog.log("COMPARAR: no pude abrir el microfono"); rec.release(); return null
        }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(RATE / 5 * 2)
        val total = RATE * 2 * seconds
        rec.startRecording()
        try {
            while (out.size() < total) {
                val n = rec.read(buf, 0, buf.size)
                if (n <= 0) break
                out.write(buf, 0, n)
            }
            val dev = rec.routedDevice
            SpikeLog.log("COMPARAR: microfono usado = ${dev?.productName} (tipo ${dev?.type})")
        } finally {
            rec.stop(); rec.release()
        }
        val pcm = out.toByteArray()
        val samples = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
        var peak = 0; var sum = 0.0; val n = samples.remaining()
        for (i in 0 until n) { val v = samples.get(i).toInt(); peak = maxOf(peak, abs(v)); sum += v.toDouble() * v }
        val rms = if (n > 0) sqrt(sum / n).toInt() else 0
        SpikeLog.log("COMPARAR: nivel pico=$peak/32767 rms=$rms (rms < ~300 = voz muy baja)")
        return pcm
    }

    private suspend fun conGemini(pcm: ByteArray): String = try {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(modelName = CLOUD_MODEL)
        val t0 = System.currentTimeMillis()
        val r = model.generateContent(
            content {
                inlineData(wav(pcm), "audio/wav")
                text(
                    "Transcribe literalmente lo que se dice en este audio, que esta en espanol. " +
                        "Responde solo con la transcripcion. Si no hay voz, responde exactamente: (silencio)",
                )
            },
        )
        "\"${r.text?.trim()}\" (${System.currentTimeMillis() - t0} ms)"
    } catch (e: Throwable) {
        "ERROR ${e::class.simpleName}: ${e.message}"
    }

    /** El mismo PCM, entregado al reconocedor de Android como fuente de audio (Android 13+). */
    private suspend fun conSistema(ctx: Context, pcm: ByteArray): String {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) return "no disponible"
        val pipe = ParcelFileDescriptor.createPipe()
        Thread {
            ParcelFileDescriptor.AutoCloseOutputStream(pipe[1]).use { it.write(pcm) }
        }.start()
        val r = withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine { cont ->
                val rec = SpeechRecognizer.createSpeechRecognizer(ctx)
                fun fin(s: String) { if (cont.isActive) { rec.destroy(); cont.resume(s) } }
                rec.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(p: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(v: Float) {}
                    override fun onBufferReceived(b: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(p: Bundle?) {}
                    override fun onEvent(t: Int, p: Bundle?) {}
                    override fun onError(code: Int) = fin("ERROR codigo=$code")
                    override fun onResults(res: Bundle?) {
                        val l = res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                        fin(if (l.isEmpty()) "(sin resultado)" else l.joinToString(" | ") { "\"$it\"" })
                    }
                })
                rec.startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
                        putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, RATE)
                    },
                )
                cont.invokeOnCancellation { rec.destroy() }
            }
        }
        return r ?: "sin respuesta en 20 s (este reconocedor quiza no acepta audio desde archivo)"
    }

    /** Cabecera WAV de 44 bytes + PCM 16 bit mono. */
    internal fun wav(pcm: ByteArray): ByteArray {
        val b = ByteBuffer.allocate(44 + pcm.size).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray())
        b.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1)
        b.putInt(RATE).putInt(RATE * 2).putShort(2).putShort(16)
        b.put("data".toByteArray()).putInt(pcm.size).put(pcm)
        return b.array()
    }
}
