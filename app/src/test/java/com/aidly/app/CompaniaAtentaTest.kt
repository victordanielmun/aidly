package com.aidly.app

import org.junit.Assert.assertEquals
import org.junit.Test

class CompaniaAtentaTest {
    private val cfg = CompaniaAtenta.Config(noVisibleS = 90, respuestaS = 15, interaccionVigenteS = 600, esperaEntreRondasS = 600, maxPreguntas = 2)
    private fun s(x: Int) = x * 1000L

    private fun nueva(): CompaniaAtenta = CompaniaAtenta(cfg).also { it.reanudar(0) }

    @Test fun sinConversacionNuncaPregunta() {
        val c = nueva()
        for (t in 0..600 step 5) assertEquals(CompaniaAtenta.Accion.Nada, c.observar(s(t), visible = t < 10))
    }

    @Test fun conversoSeVioYDejaDeVerseDespuesDeNSegundosPregunta() {
        val c = nueva()
        c.interaccion(s(0))
        for (t in 0..10) c.observar(s(t), true)
        for (t in 11..99) assertEquals("t=$t", CompaniaAtenta.Accion.Nada, c.observar(s(t), false))
        assertEquals(CompaniaAtenta.Accion.Preguntar(1), c.observar(s(100), false))
    }

    @Test fun repiteUnaVezYLuegoSeRindeSinAvisarAEmergencias() {
        val c = nueva()
        c.interaccion(s(0)); c.observar(s(5), true)
        assertEquals(CompaniaAtenta.Accion.Preguntar(1), c.observar(s(100), false))
        c.terminoDePreguntar(s(100))
        assertEquals(CompaniaAtenta.Accion.Nada, c.observar(s(110), false))
        assertEquals(CompaniaAtenta.Accion.Preguntar(2), c.observar(s(115), false))
        c.terminoDePreguntar(s(115))
        assertEquals(CompaniaAtenta.Accion.Nada, c.observar(s(125), false))
        assertEquals(CompaniaAtenta.Accion.SinRespuesta, c.observar(s(130), false))
        assertEquals(CompaniaAtenta.Estado.SIN_RESPUESTA, c.estado)
    }

    @Test fun siRespondeSeCierraYNoVuelveAPreguntarEnLaEsperaEntreRondas() {
        val c = nueva()
        c.interaccion(s(0)); c.observar(s(5), true)
        assertEquals(CompaniaAtenta.Accion.Preguntar(1), c.observar(s(100), false))
        c.respondio(s(105))
        for (t in 106..400 step 5) assertEquals(CompaniaAtenta.Accion.Nada, c.observar(s(t), false))
    }

    @Test fun siVuelveAntesDeTiempoNoPregunta() {
        val c = nueva()
        c.interaccion(s(0)); c.observar(s(5), true)
        for (t in 6..60) c.observar(s(t), false)
        c.observar(s(61), true)
        for (t in 62..150) assertEquals(CompaniaAtenta.Accion.Nada, c.observar(s(t), t > 70 && false))
    }

    @Test fun siVuelveMientrasSeEsperaLaRondaSeCierra() {
        val c = nueva()
        c.interaccion(s(0)); c.observar(s(5), true)
        c.observar(s(100), false)
        assertEquals(CompaniaAtenta.Accion.Nada, c.observar(s(105), true))
        assertEquals(CompaniaAtenta.Estado.VISIBLE, c.estado)
    }

    @Test fun conLaCamaraPausadaNoActua() {
        val c = nueva()
        c.interaccion(s(0)); c.observar(s(5), true)
        c.pausar()
        for (t in 6..300 step 5) assertEquals(CompaniaAtenta.Accion.Nada, c.observar(s(t), false))
    }

    @Test fun siLaConversacionYaNoEstaVigenteNoPregunta() {
        val c = nueva()
        c.interaccion(s(0)); c.observar(s(5), true)
        assertEquals(CompaniaAtenta.Accion.Nada, c.observar(s(700), false))
    }

    @Test fun laEsperaDeLaRespuestaSeCuentaDesdeQueAidlyTerminaDePreguntar() {
        val c = nueva()
        c.interaccion(s(0)); c.observar(s(5), true)
        assertEquals(CompaniaAtenta.Accion.Preguntar(1), c.observar(s(100), false))
        assertEquals(CompaniaAtenta.Accion.Nada, c.observar(s(116), false))   // sigue hablando: no se adelanta
        c.terminoDePreguntar(s(112))             // hablar y escuchar tomo 12 s
        assertEquals(CompaniaAtenta.Accion.Nada, c.observar(s(120), false))
        assertEquals(CompaniaAtenta.Accion.Preguntar(2), c.observar(s(127), false))
    }
}
