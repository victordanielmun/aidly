package com.aidly.app

/**
 * Maquina de estados de "Compania atenta" (modo dock): decide CUANDO Aidly pregunta "¿estas bien?".
 * No detecta caidas ni emergencias. Solo: si la persona estuvo conversando, se la vio cerca y deja de verse mas de [Config.noVisibleS]
 * segundos, Aidly la llama y le pregunta si esta bien. Sin imagenes: solo recibe "se ve / no se ve" con la hora.
 * Es codigo puro (sin Android) para poder probarlo con secuencias simuladas.
 */
class CompaniaAtenta(val config: Config = Config()) {
    data class Config(
        /** Tiempo sin verla antes de preguntar (propuesta 60-120 s). */
        val noVisibleS: Int = 90,
        /** Espera de respuesta a cada pregunta. */
        val respuestaS: Int = 15,
        /** Cuanto tiempo despues de la ultima conversacion sigue "vigente" la posibilidad de preguntar. */
        val interaccionVigenteS: Int = 600,
        /** Minimo entre dos rondas de preguntas, para no molestar. */
        val esperaEntreRondasS: Int = 600,
        /** Preguntas por ronda antes de rendirse. */
        val maxPreguntas: Int = 2,
    )

    enum class Estado { PAUSADA, NO_VISIBLE, VISIBLE, ESPERANDO_RESPUESTA, SIN_RESPUESTA }

    sealed interface Accion {
        data object Nada : Accion
        /** Decir la pregunta; [repeticion] = 1 la primera vez, 2 la segunda. */
        data class Preguntar(val repeticion: Int) : Accion
        /** Ya se pregunto las veces permitidas y nadie respondio. */
        data object SinRespuesta : Accion
    }

    var estado: Estado = Estado.PAUSADA
        private set
    private var ultimaVista = Long.MIN_VALUE / 2
    private var ultimaInteraccion = Long.MIN_VALUE / 2
    private var inicioPregunta = 0L
    private var preguntando = false      // Aidly esta diciendo la pregunta o escuchando: no se adelanta la siguiente
    private var preguntas = 0
    private var ultimaRonda = Long.MIN_VALUE / 2

    /** La persona hablo con Aidly (cualquier conversacion). */
    fun interaccion(ms: Long) { ultimaInteraccion = ms }

    fun pausar() { estado = Estado.PAUSADA; preguntas = 0; preguntando = false }
    fun reanudar(ms: Long) { if (estado == Estado.PAUSADA) estado = Estado.NO_VISIBLE; ultimaVista = Long.MIN_VALUE / 2 }

    /** Aidly termino de decir la pregunta y de escuchar: la espera de la respuesta se cuenta desde aqui. */
    fun terminoDePreguntar(ms: Long) { preguntando = false; if (estado == Estado.ESPERANDO_RESPUESTA) inicioPregunta = ms }

    /** La persona respondio a la pregunta (bien = dijo que esta bien). Cierra la ronda y arranca la espera entre rondas. */
    fun respondio(ms: Long) {
        if (estado != Estado.ESPERANDO_RESPUESTA && estado != Estado.SIN_RESPUESTA) return
        estado = Estado.NO_VISIBLE; preguntas = 0; preguntando = false; ultimaRonda = ms; ultimaInteraccion = ms
    }

    /** Llamar con cada observacion de la camara. [visible] = se ve a una persona (cara o cuerpo). */
    fun observar(ms: Long, visible: Boolean): Accion {
        if (estado == Estado.PAUSADA) return Accion.Nada
        if (visible) {
            ultimaVista = ms
            // Si volvio mientras se esperaba la respuesta, la ronda se cierra sin mas.
            if (estado == Estado.ESPERANDO_RESPUESTA || estado == Estado.SIN_RESPUESTA) { ultimaRonda = ms; preguntas = 0 }
            estado = Estado.VISIBLE
            return Accion.Nada
        }
        when (estado) {
            Estado.VISIBLE, Estado.NO_VISIBLE -> {
                estado = Estado.NO_VISIBLE
                val conversoHaceUnRato = ms - ultimaInteraccion <= config.interaccionVigenteS * 1000L
                val laVioCerca = ultimaVista >= ultimaInteraccion - 60_000L && ultimaVista > Long.MIN_VALUE / 4
                val faltaPoco = ms - ultimaVista >= config.noVisibleS * 1000L
                val sinMolestar = ms - ultimaRonda >= config.esperaEntreRondasS * 1000L
                if (conversoHaceUnRato && laVioCerca && faltaPoco && sinMolestar) {
                    estado = Estado.ESPERANDO_RESPUESTA; preguntas = 1; inicioPregunta = ms; preguntando = true
                    return Accion.Preguntar(1)
                }
            }
            Estado.ESPERANDO_RESPUESTA -> if (!preguntando && ms - inicioPregunta >= config.respuestaS * 1000L) {
                if (preguntas < config.maxPreguntas) {
                    preguntas++; inicioPregunta = ms; preguntando = true
                    return Accion.Preguntar(preguntas)
                }
                estado = Estado.SIN_RESPUESTA; ultimaRonda = ms
                return Accion.SinRespuesta
            }
            else -> {}
        }
        return Accion.Nada
    }
}
