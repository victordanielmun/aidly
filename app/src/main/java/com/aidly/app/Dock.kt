package com.aidly.app

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * El dock fisico (base giratoria): la app solo le dice hacia donde mirar. Hoy existe [DockSimulado]; `DockUsb` (por el
 * puerto serie del USB-C, ver dock/PROTOCOLO.md) implementara la misma interfaz.
 */
interface Dock {
    val nombre: String
    /** Angulo actual en grados, de -180 (izquierda) a 180 (derecha), 0 = de frente. */
    val angulo: StateFlow<Float>
    /** [x] = donde ve a la persona (-1 izquierda ... 1 derecha), o null si no la ve. */
    fun seguir(x: Float?, ms: Long)
    fun detener()
}

/** Simula el giro: control suave con zona muerta y velocidad maxima parecida a la de un motor paso a paso con reduccion. */
class DockSimulado(private val maxGradosPorS: Float = 25f) : Dock {
    override val nombre = "simulado"
    private val _angulo = MutableStateFlow(0f)
    override val angulo: StateFlow<Float> = _angulo
    private var antes = 0L

    override fun seguir(x: Float?, ms: Long) {
        val dt = if (antes == 0L) 0f else ((ms - antes).coerceIn(0, 500)) / 1000f
        antes = ms
        if (x == null || kotlin.math.abs(x) < 0.12f) return                     // sin persona o ya centrada: no se mueve
        val deseado = x * 60f                                                       // grados que faltan, como mucho 60 por observacion
        val paso = deseado.coerceIn(-maxGradosPorS * dt, maxGradosPorS * dt)
        _angulo.value = (_angulo.value + paso).coerceIn(-180f, 180f)
    }

    override fun detener() { antes = 0L }
}
