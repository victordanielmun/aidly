package com.aidly.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale

/** Envoltorio minimo de TextToSpeech (es-MX) para pantallas. Los callbacks llegan en el hilo principal. */
class Voz(ctx: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var listo = false
    private val pendientes = ArrayDeque<Pair<String, (() -> Unit)?>>()
    private val callbacks = HashMap<String, () -> Unit>()

    /** true mientras suena alguna frase (la cara mueve la boca con esto, sea cual sea su estado). */
    private val activos = HashSet<String>()
    private val _hablando = MutableStateFlow(false)
    val hablando: StateFlow<Boolean> = _hablando
    private fun terminaron(id: String?) { if (id != null) activos.remove(id); _hablando.value = activos.isNotEmpty() }

    init {
        tts = TextToSpeech(ctx.applicationContext) { st ->
            if (st != TextToSpeech.SUCCESS) { SpikeLog.log("VOZ: fallo init TTS ($st)"); return@TextToSpeech }
            tts?.let { Idioma.aplicarVoz(ctx, it) }
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) { main.post { if (id != null) activos.add(id); _hablando.value = true } }
                override fun onDone(id: String?) { main.post { terminaron(id); callbacks.remove(id)?.invoke() } }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) { main.post { terminaron(id); callbacks.remove(id)?.invoke() } }
            })
            listo = true
            while (pendientes.isNotEmpty()) pendientes.removeFirst().let { (t, cb) -> decir(t, cb) }
        }
    }

    fun decir(texto: String, alTerminar: (() -> Unit)? = null) {
        if (!listo) { pendientes.addLast(texto to alTerminar); return }
        val id = "v${System.nanoTime()}"
        alTerminar?.let { callbacks[id] = it }
        activos.add(id); _hablando.value = true
        if (tts?.speak(texto, TextToSpeech.QUEUE_ADD, null, id) != TextToSpeech.SUCCESS) { terminaron(id); callbacks.remove(id) }
    }

    /** Habla y espera a que termine (para no grabar la propia voz). Usar con un timeout por si falla el TTS. */
    suspend fun decirYEsperar(texto: String) = kotlinx.coroutines.suspendCancellableCoroutine<Unit> { c ->
        decir(texto) { if (c.isActive) c.resumeWith(Result.success(Unit)) }
    }

    fun callar() { tts?.stop(); callbacks.clear(); pendientes.clear(); activos.clear(); _hablando.value = false }

    fun cerrar() { callar(); tts?.shutdown(); tts = null }
}
