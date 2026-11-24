package com.aidly.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.Normalizer
import java.util.Locale

/**
 * Servicio en primer plano tipo micrófono, iniciado desde la UI (Android 14+ no deja abrir el
 * micrófono desde segundo plano). Implementa el Voice Loop de mensajes:
 * anuncia -> "¿te lo leo?" -> lee -> "¿quieres responder?" -> dictado -> confirma -> envía.
 */
class AidlyService : Service() {

    private enum class Step { IDLE, ASK_READ, ASK_REPLY, DICTATE, CONFIRM }
    private enum class Intento { SI, NO, NADA }
    private class Convo(val sender: String, val text: String, val reply: Notification.Action?)

    private val main = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    private var pendingSay: Pair<String, (() -> Unit)?>? = null
    private val doneCallbacks = HashMap<String, () -> Unit>()
    private var recognizer: SpeechRecognizer? = null

    private var step = Step.IDLE
    private var convo: Convo? = null
    private var retries = 0
    private var dictated = ""
    private var goodLangIdx = 0
    private var serverRetries = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var sttJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_servicio_canal), NotificationManager.IMPORTANCE_LOW),
        )
        val notif = Notification.Builder(this, CHANNEL)
            .setContentTitle(getString(R.string.notif_servicio_titulo))
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(NOTIF_ID, notif)
            }
        } catch (e: Exception) {
            // Android 14+: el micro solo se abre si el servicio nace con la app a la vista.
            // Si el sistema reinicia el servicio en segundo plano llegamos aqui: no caerse.
            SpikeLog.log("No puedo iniciar el servicio (${e::class.simpleName}). Abre Aidly y pulsa 'Activar'.")
            stopSelf()
            return
        }
        running = true
        SpikeLog.log("Servicio FGS(microfono) iniciado")

        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (!ttsReady) { SpikeLog.log("TTS fallo init status=$status"); return@TextToSpeech }
            val r = tts?.let { Idioma.aplicarVoz(this, it) }
            SpikeLog.log("TTS listo, idioma $r")
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(id: String?) {}
                override fun onDone(id: String?) {
                    main.post { doneCallbacks.remove(id)?.invoke() }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(id: String?) {
                    SpikeLog.log("TTS error")
                    main.post { doneCallbacks.remove(id) }
                }
            })
            pendingSay?.let { (t, cb) -> pendingSay = null; say(t, cb) }
        }
    }

    // ---------- Voz ----------

    private fun say(text: String, then: (() -> Unit)? = null) {
        SpikeLog.log("Aidly dice: $text  [salida: ${salidaDeAudio()}]")
        if (!ttsReady) { pendingSay = text to then; return }
        val id = "u${System.nanoTime()}"
        then?.let { doneCallbacks[id] = it }
        tts?.speak(text, TextToSpeech.QUEUE_ADD, null, id)
    }

    /** Diagnostico: si hay audio Bluetooth conectado, la voz sale por ahi y no por el altavoz del telefono. */
    private fun salidaDeAudio(): String {
        val am = getSystemService(AudioManager::class.java)
        val tipos = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.type }
        return when {
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP in tipos || AudioDeviceInfo.TYPE_BLUETOOTH_SCO in tipos -> "BLUETOOTH"
            AudioDeviceInfo.TYPE_WIRED_HEADSET in tipos || AudioDeviceInfo.TYPE_WIRED_HEADPHONES in tipos -> "AURICULARES CABLE"
            else -> "altavoz del telefono"
        }
    }

    /** Escucha con una cadena de idiomas: el error 12 (idioma no soportado) pasa al siguiente. */
    private fun listen(langIdx: Int = goodLangIdx) {
        val LANGS = Idioma.sttCadena(this)
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            SpikeLog.log("STT no disponible en este telefono"); endConvo(); return
        }
        recognizer?.destroy()
        val lang = LANGS.getOrNull(langIdx)
        SpikeLog.log("STT idioma=${lang ?: "(del sistema)"}")
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).also { r ->
            r.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(p: Bundle?) {
                    goodLangIdx = langIdx   // recordar el idioma que si funciona en este telefono
                    SpikeLog.log("STT escuchando")
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rms: Float) {}
                override fun onBufferReceived(b: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onPartialResults(p: Bundle?) {}
                override fun onEvent(t: Int, p: Bundle?) {}
                override fun onError(code: Int) {
                    SpikeLog.log("STT error $code (${errName(code)})")
                    when (code) {
                        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ->
                            if (langIdx + 1 < LANGS.size) main.post { listen(langIdx + 1) } else fallo()
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> reintentar()
                        // 8 = BUSY, 11 = SERVER_DISCONNECTED: pasa al recrear el reconocedor muy rapido.
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY, ERROR_SERVER_DISCONNECTED ->
                            if (++serverRetries <= 3) main.postDelayed({ listen(langIdx) }, 700) else fallo()
                        else -> fallo()
                    }
                }
                override fun onResults(res: Bundle?) {
                    val list = res?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION).orEmpty()
                    SpikeLog.log("STT oyo: $list")
                    if (list.isEmpty()) reintentar() else oyo(list)
                }
            })
            r.startListening(
                Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    lang?.let { putExtra(RecognizerIntent.EXTRA_LANGUAGE, it) }
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                },
            )
        }
    }

    /**
     * Elige el motor de voz segun el paso: el "si/no" inicial usa el reconocedor del sistema (rapido);
     * responder, dictar y confirmar usan Gemini (mucho mas preciso en frases como "contestale...").
     * Pequena pausa para no captar la cola del propio TTS.
     */
    private fun escuchar() {
        if (Privacidad.nubeActiva() && (step == Step.ASK_REPLY || step == Step.DICTATE || step == Step.CONFIRM)) escucharGemini()
        else main.postDelayed({ listen() }, 350)
    }

    private fun escucharSistema() = main.postDelayed({ listen() }, 350)

    private fun escucharGemini() {
        recognizer?.destroy(); recognizer = null
        sttJob?.cancel()
        val paso = step
        sttJob = scope.launch {
            delay(350)
            SpikeLog.log("GEMINI STT: escuchando...")
            try {
                val texto = GeminiStt.escuchar(this@AidlyService)
                if (step != paso) return@launch          // la conversacion cambio mientras tanto
                SpikeLog.log("GEMINI STT oyo: ${texto ?: "(sin voz)"}")
                if (texto == null) reintentar() else oyo(listOf(texto), gemini = true)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                SpikeLog.log("GEMINI STT fallo (${e::class.simpleName}: ${e.message}); uso el reconocedor del sistema")
                if (step == paso) escucharSistema()
            }
        }
    }

    // ---------- Conversación (Voice Loop) ----------

    private val cola = ArrayDeque<Convo>()

    private fun startConvo(c: Convo) {
        if (step != Step.IDLE) {
            // Hay otra conversacion en curso: se encola en vez de perder el mensaje.
            cola.addLast(c); SpikeLog.log("Ocupado: encolo mensaje de ${c.sender} (${cola.size} en cola)"); return
        }
        convo = c; step = Step.ASK_READ; retries = 0; serverRetries = 0
        say(Idioma.vozTexto(this, R.string.v_te_escribio, c.sender)) { escuchar() }
    }

    private fun oyo(alternativas: List<String>, gemini: Boolean = false) {
        val c = convo ?: return
        when (step) {
            Step.ASK_READ -> when (intento(alternativas)) {
                Intento.SI -> {
                    say(Idioma.vozTexto(this, R.string.v_dice, c.sender, c.text))
                    if (c.reply != null) { avanzar(Step.ASK_REPLY); say(Idioma.vozTexto(this, R.string.v_quieres_responder)) { escuchar() } }
                    else endConvo(Idioma.vozTexto(this, R.string.v_eso_es_todo))
                }
                Intento.NO -> endConvo(Idioma.vozTexto(this, R.string.v_lo_dejamos))
                Intento.NADA -> reintentar()
            }
            Step.ASK_REPLY -> {
                // "Contestale buenas tardes": la respuesta viene dentro de la misma frase.
                val inline = respuestaInline(alternativas, gemini)
                if (inline != null) {
                    dictated = inline; avanzar(Step.CONFIRM)
                    say(Idioma.vozTexto(this, R.string.v_dijiste_envio, dictated)) { escuchar() }
                } else when (intento(alternativas)) {
                    Intento.SI -> { avanzar(Step.DICTATE); say(Idioma.vozTexto(this, R.string.v_dime_respuesta)) { escuchar() } }
                    Intento.NO -> endConvo(Idioma.vozTexto(this, R.string.v_no_respondo))
                    Intento.NADA -> reintentar()
                }
            }
            Step.DICTATE -> {
                dictated = alternativas.first().trim()
                avanzar(Step.CONFIRM)
                say(Idioma.vozTexto(this, R.string.v_dijiste_envio, dictated)) { escuchar() }
            }
            Step.CONFIRM -> when (intento(alternativas)) {
                Intento.SI -> { enviar(c, dictated); endConvo(Idioma.vozTexto(this, R.string.v_ya_envie)) }
                Intento.NO -> { avanzar(Step.DICTATE); say(Idioma.vozTexto(this, R.string.v_otra_vez)) { escuchar() } }
                Intento.NADA -> {
                    // Ni si ni no: lo mas probable es que este corrigiendo el texto ("dije buenas tardes").
                    val nueva = limpiarDictado(alternativas.first(), cortarComa = !gemini)
                    if (nueva.isNotBlank()) {
                        dictated = nueva; retries = 0
                        say(Idioma.vozTexto(this, R.string.v_dijiste_envio, dictated)) { escuchar() }
                    } else reintentar()
                }
            }
            Step.IDLE -> {}
        }
    }

    private fun avanzar(s: Step) { step = s; retries = 0; serverRetries = 0 }

    private fun reintentar() {
        if (step == Step.IDLE) return
        if (++retries > 1) { endConvo(Idioma.vozTexto(this, R.string.v_no_escuche_dejamos)); return }
        say(
            if (step == Step.DICTATE) Idioma.vozTexto(this, R.string.v_no_escuche_respuesta)
            else Idioma.vozTexto(this, R.string.v_no_escuche_si_no),
        ) { escuchar() }
    }

    private fun fallo() = endConvo(Idioma.vozTexto(this, R.string.v_problema_micro))

    private fun endConvo(despedida: String? = null) {
        step = Step.IDLE; convo = null; retries = 0
        sttJob?.cancel(); sttJob = null
        recognizer?.destroy(); recognizer = null
        val siguiente = { cola.removeFirstOrNull()?.let { startConvo(it) } }
        if (despedida != null) say(despedida) { siguiente() } else siguiente()
    }

    private fun enviar(c: Convo, texto: String) {
        val action = c.reply ?: return
        val ri = action.remoteInputs?.firstOrNull() ?: return
        val i = Intent()
        val b = Bundle().apply { putCharSequence(ri.resultKey, texto) }
        RemoteInput.addResultsToIntent(action.remoteInputs, i, b)
        try {
            action.actionIntent.send(this, 0, i)
            SpikeLog.log("Respuesta ENVIADA a ${c.sender}: $texto")
        } catch (e: Exception) {
            SpikeLog.log("No pude enviar: ${e.message}")
        }
    }

    private fun intento(alts: List<String>): Intento {
        for (a in alts) {
            val tokens = normalizar(a).split(Regex("\\s+"))
            if (tokens.any { it in Idioma.palabrasVoz(this, "palabras_no") }) return Intento.NO
            if (tokens.any { it in Idioma.palabrasVoz(this, "palabras_si") }) return Intento.SI
            // El reconocedor a veces oye "si" como "se"/"ci": solo se acepta en respuestas de 1-2 palabras.
            if (tokens.size <= 2 && tokens.any { it in Idioma.palabrasVoz(this, "si_mal_oido") }) return Intento.SI
        }
        return Intento.NADA
    }

    /** "Contestale buenas tardes" -> "buenas tardes". Devuelve null si no hay texto despues del verbo. */
    private fun respuestaInline(alts: List<String>, gemini: Boolean = false): String? {
        for (a in alts) {
            val m = (if (Idioma.resolver(this) == "en") INLINE_EN else INLINE).find(a) ?: continue
            val t = limpiarDictado(m.groupValues[2], cortarComa = !gemini)
            if (t.isNotBlank()) return t
        }
        return null
    }

    /**
     * Quita muletillas ("dije", "escribe", "que"...). Con el reconocedor del sistema tambien corta en la
     * primera coma (repite la frase); con Gemini NO, porque la puntuacion es legitima ("Hola, como estas").
     */
    private fun limpiarDictado(s: String, cortarComa: Boolean = true): String {
        val base = if (cortarComa) s.substringBefore(",") else s
        return base.trim()
            .replace(Regex("(?i)^(dije|digo|escribe|pon|mejor|que)\\s+"), "")
            .trim()
    }

    private fun normalizar(s: String) =
        Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")

    private fun errName(c: Int) = when (c) {
        SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
        SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "PERMISOS"
        SpeechRecognizer.ERROR_NETWORK -> "RED"
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "RED_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "OCUPADO"
        SpeechRecognizer.ERROR_SERVER -> "SERVIDOR"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SIN_VOZ"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "IDIOMA_NO_SOPORTADO"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "IDIOMA_NO_DISPONIBLE"
        else -> "OTRO"
    }

    // ---------- Comandos ----------

    @Suppress("DEPRECATION")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!running) return START_NOT_STICKY
        when (intent?.action) {
            ACTION_SAY -> say(intent.getStringExtra(EXTRA_TEXT).orEmpty())
            ACTION_LISTEN -> listen()
            ACTION_ANNOUNCE -> startConvo(
                Convo(
                    intent.getStringExtra(EXTRA_SENDER).orEmpty(),
                    intent.getStringExtra(EXTRA_TEXT).orEmpty(),
                    intent.getParcelableExtra(EXTRA_REPLY),
                ),
            )
            ACTION_STOP -> { endConvo(); stopSelf(); SpikeLog.log("Servicio detenido") }
        }
        // NOT_STICKY: si el sistema mata el proceso, un reinicio en segundo plano no podria abrir el micro.
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        running = false
        scope.cancel()
        recognizer?.destroy()
        tts?.shutdown()
        super.onDestroy()
    }

    companion object {
        /** Verdadero mientras el servicio con micro esta vivo (lo consulta el listener de notificaciones). */
        @Volatile var running = false

        private const val CHANNEL = "aidly_companion"
        private const val NOTIF_ID = 1
        const val ACTION_SAY = "com.aidly.app.SAY"
        const val ACTION_LISTEN = "com.aidly.app.LISTEN"
        const val ACTION_ANNOUNCE = "com.aidly.app.ANNOUNCE"
        const val ACTION_STOP = "com.aidly.app.STOP"
        const val EXTRA_TEXT = "text"
        const val EXTRA_SENDER = "sender"
        const val EXTRA_REPLY = "reply_action"

        private const val ERROR_SERVER_DISCONNECTED = 11
        private val INLINE_EN = Regex(
            "(?i)\\b(reply|answer|respond|tell(?: (?:him|her|them))?|say)\\b[\\s,:]+(?:(?:that|to (?:him|her|them))\\b[\\s,:]+)?(.+)",
        )
        private val INLINE = Regex(
            "(?i)\\b(cont[eé]sta(?:le|la|lo)?|resp[oó]nd[eé](?:le|la|lo)?|d[ií]le|d[ií]les)\\b[\\s,:]+(.+)", // i18n-ok
        )

        fun start(
            ctx: Context,
            action: String? = null,
            text: String? = null,
            sender: String? = null,
            reply: Notification.Action? = null,
        ) {
            val i = Intent(ctx, AidlyService::class.java).apply {
                this.action = action
                text?.let { putExtra(EXTRA_TEXT, it) }
                sender?.let { putExtra(EXTRA_SENDER, it) }
                reply?.let { putExtra(EXTRA_REPLY, it) }
            }
            ctx.startForegroundService(i)
        }
    }
}
