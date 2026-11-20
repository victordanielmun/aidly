package com.aidly.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Una frase con el reconocedor de voz del sistema, para cuando la nube de Aidly esta apagada en Privacidad.
 * Ojo: segun el telefono, el reconocedor del sistema puede usar el servicio de voz de Google; Aidly no controla eso
 * (por eso la pantalla de Privacidad lo dice).
 */
object EscuchaSistema {

    /** Devuelve el texto o null si no hubo voz o no hay reconocedor. */
    suspend fun escuchar(ctx: Context): String? {
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) return null
        for (idioma in Idioma.sttCadena(ctx).filterNotNull().ifEmpty { listOf(Idioma.locale(ctx).toLanguageTag()) }) {
            when (val r = una(ctx.applicationContext, idioma)) {
                is Resultado.Texto -> return r.valor
                Resultado.SinVoz -> return null
                Resultado.Idioma -> continue
            }
        }
        return null
    }

    private sealed interface Resultado {
        class Texto(val valor: String) : Resultado
        data object SinVoz : Resultado
        data object Idioma : Resultado
    }

    private suspend fun una(ctx: Context, idioma: String): Resultado = withContext(Dispatchers.Main) {
        withTimeoutOrNull(20_000) {
            suspendCancellableCoroutine<Resultado> { cont ->
                val rec = SpeechRecognizer.createSpeechRecognizer(ctx)
                fun fin(r: Resultado) { if (cont.isActive) cont.resume(r); rec.destroy() }
                rec.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(p: Bundle?) {}
                    override fun onBeginningOfSpeech() {}
                    override fun onRmsChanged(rms: Float) {}
                    override fun onBufferReceived(b: ByteArray?) {}
                    override fun onEndOfSpeech() {}
                    override fun onPartialResults(p: Bundle?) {}
                    override fun onEvent(t: Int, p: Bundle?) {}
                    override fun onError(code: Int) {
                        SpikeLog.log("ESCUCHA SISTEMA: error $code")
                        fin(
                            if (code == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED || code == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE) Resultado.Idioma
                            else Resultado.SinVoz,
                        )
                    }
                    override fun onResults(res: Bundle?) {
                        val t = res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim().orEmpty()
                        fin(if (t.isBlank()) Resultado.SinVoz else Resultado.Texto(t))
                    }
                })
                cont.invokeOnCancellation { rec.destroy() }
                rec.startListening(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                        putExtra(RecognizerIntent.EXTRA_LANGUAGE, idioma)
                        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                    },
                )
            }
        } ?: Resultado.SinVoz
    }
}
