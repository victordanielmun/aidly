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

/**
 * Control del giro (y simulador de la base): suave, con zona muerta y velocidad maxima parecida a la de un motor paso a paso con reduccion.
 *
 * [camaraGiraConDock]: con el dock REAL la camara gira con la plataforma, asi que `x` (donde ve a la persona) se acerca a 0 a medida que gira.
 * En la base SIMULADA el telefono no se mueve y `x` no cambia nunca: sin esto el control seguiria girando hasta el tope de 180 grados.
 * Con `false` se estima el rumbo de la persona (angulo + x * 60) cuando aparece o se mueve, y se gira hasta ese rumbo y se para.
 */
class DockSimulado(private val maxGradosPorS: Float = 25f, private val camaraGiraConDock: Boolean = true) : Dock {
    override val nombre = "simulado"
    private val _angulo = MutableStateFlow(0f)
    override val angulo: StateFlow<Float> = _angulo
    private var antes = 0L
    private var rumbo: Float? = null
    private var xAnterior: Float? = null

    override fun seguir(x: Float?, ms: Long) {
        val dt = if (antes == 0L) 0f else ((ms - antes).coerceIn(0, 500)) / 1000f
        antes = ms
        if (x == null) { rumbo = null; xAnterior = null; return }                  // sin persona: no se mueve
        val visto = if (camaraGiraConDock) x else {
            val previo = xAnterior
            if (rumbo == null || previo == null || kotlin.math.abs(x - previo) > 0.2f) rumbo = (_angulo.value + x * 60f).coerceIn(-180f, 180f)
            xAnterior = x
            ((rumbo!! - _angulo.value) / 60f).coerceIn(-1f, 1f)
        }
        if (kotlin.math.abs(visto) < 0.12f) return                                   // ya centrada
        val deseado = visto * 60f                                                    // grados que faltan, como mucho 60 por observacion
        val paso = deseado.coerceIn(-maxGradosPorS * dt, maxGradosPorS * dt)
        _angulo.value = (_angulo.value + paso).coerceIn(-180f, 180f)
    }

    override fun detener() { antes = 0L; rumbo = null; xAnterior = null }

    /** Corrige el angulo cuando el dock real informa de donde esta de verdad. */
    fun fijar(grados: Float) { _angulo.value = grados.coerceIn(-180f, 180f) }
}
