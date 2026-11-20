package com.aidly.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import java.util.Locale

/** Envoltorio minimo de TextToSpeech (es-MX) para pantallas. Los callbacks llegan en el hilo principal. */
class Voz(ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var listo = false
    private val pendientes = ArrayDeque<Pair<String, (() -> Unit)?>>()
    private val callbacks = HashMap<String, () -> Unit>()

    init {
        tts = TextToSpeech(ctx.applicationContext) { st ->
            if (st != TextToSpeech.SUCCESS) { SpikeLog.log("VOZ: fallo init TTS ($st)"); return@TextToSpeech }
            tts?.setLanguage(Locale("es", "MX"))
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) { main.post { callbacks.remove(id)?.invoke() } }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) { main.post { callbacks.remove(id)?.invoke() } }
            })
            listo = true
            while (pendientes.isNotEmpty()) pendientes.removeFirst().let { (t, cb) -> decir(t, cb) }
        }
    }

    fun decir(texto: String, alTerminar: (() -> Unit)? = null) {
        if (!listo) { pendientes.addLast(texto to alTerminar); return }
        val id = "v${System.nanoTime()}"
        alTerminar?.let { callbacks[id] = it }
        tts?.speak(texto, TextToSpeech.QUEUE_ADD, null, id)
    }

    /** Habla y espera a que termine (para no grabar la propia voz). Usar con un timeout por si falla el TTS. */
    suspend fun decirYEsperar(texto: String) = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { c ->
        decir(texto) { if (c.isActive) c.resumeWith(Result.success(Unit)) }
    }

    fun callar() { tts?.stop(); callbacks.clear(); pendientes.clear() }

    fun cerrar() { callar(); tts?.shutdown(); tts = null }
}
