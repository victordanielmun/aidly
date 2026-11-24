package com.aidly.app

/**
 * Escucha sin boton en el modo dock. Pura (sin Android) para poder probarla. Reglas:
 *  - Solo se hace caso si dicen la palabra de llamada «Aidly» (por la tele o una charla ajena no se responde)...
 *  - ...o si es la continuacion de una conversacion: tras hablar Aidly queda una ventana de [Config.ventanaMs] en la que no hace falta repetir el nombre
 *    (hasta [Config.maxTurnosVentana] turnos seguidos).
 *  - Para gastar poco y molestar poco: [Config.entreIntentosMs] entre intentos, descanso de [Config.descansoMs] tras [Config.vaciosParaDescansar]
 *    intentos seguidos sin nada util, y como mucho [Config.maxPorHora] intentos por hora. Nunca escucha si la camara ve que no hay nadie.
 */
class EscuchaLibre(private val cfg: Config = Config()) {
    class Config(
        val entreIntentosMs: Long = 1_500, val vaciosParaDescansar: Int = 4, val descansoMs: Long = 30_000,
        val ventanaMs: Long = 10_000, val maxTurnosVentana: Int = 6, val maxPorHora: Int = 300,
    )

    sealed interface Entrada {
        data object Nada : Entrada
        /** Dijeron «Aidly»; [resto] es lo que sigue ("" si solo la llamaron). */
        class Llamada(val resto: String) : Entrada
        /** Continuacion de la conversacion, sin decir el nombre. */
        class Continuacion(val texto: String) : Entrada
    }

    private var ultimoIntento = Long.MIN_VALUE / 2
    private var vacios = 0
    private var descansaHasta = 0L
    private var ventanaHasta = 0L
    private var turnosVentana = 0
    private val intentos = ArrayDeque<Long>()

    /** [hayAlguien]: lo que dice la camara (true/false) o null si no se sabe (camara apagada). */
    fun puedeEscuchar(ms: Long, hayAlguien: Boolean?): Boolean {
        if (hayAlguien == false) return false
        if (ms < descansaHasta || ms - ultimoIntento < cfg.entreIntentosMs) return false
        while (intentos.isNotEmpty() && ms - intentos.first() > 3_600_000L) intentos.removeFirst()
        return intentos.size < cfg.maxPorHora
    }

    fun empezoIntento(ms: Long) { ultimoIntento = ms; intentos.addLast(ms) }

    fun enVentana(ms: Long) = ms < ventanaHasta && turnosVentana < cfg.maxTurnosVentana

    /** Aidly acaba de contestar: la persona puede seguir hablando un rato sin repetir el nombre. */
    fun abrirVentana(ms: Long) { ventanaHasta = ms + cfg.ventanaMs }

    fun cerrarVentana() { ventanaHasta = 0; turnosVentana = 0 }

    /** Clasifica lo que se oyo en un intento de escucha. */
    fun clasificar(ms: Long, texto: String?): Entrada {
        val t = texto?.trim().orEmpty()
        val llamada = if (t.isEmpty()) null else LLAMADA.find(t)
        return when {
            llamada != null -> { vacios = 0; turnosVentana = 0; Entrada.Llamada(t.substring(llamada.range.last + 1).trim().trim('.', ',', '!', '?', ':', ';').trim()) }
            t.isNotEmpty() && enVentana(ms) -> { vacios = 0; turnosVentana++; Entrada.Continuacion(t) }
            else -> {
                if (t.isEmpty() && ms < ventanaHasta) cerrarVentana()   // en silencio, la conversacion termino
                if (++vacios >= cfg.vaciosParaDescansar) { vacios = 0; descansaHasta = ms + cfg.descansoMs }
                Entrada.Nada
            }
        }
    }

    companion object {
        /** «Aidly» como lo suele transcribir un reconocedor de voz, con saludo opcional delante. */
        val LLAMADA = Regex(
            "(?i)\\b(?:(?:hey|oye|hola|ok|okay|hi)[ ,]+)?(?:aidly|aidli|aidley|aidlee|eidly|eidli|aily|ai dly|ay dly)\\b[ ,.:;!?-]*",
        )
    }
}
