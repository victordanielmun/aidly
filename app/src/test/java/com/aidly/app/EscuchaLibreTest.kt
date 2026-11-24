package com.aidly.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EscuchaLibreTest {
    private fun resto(e: EscuchaLibre.Entrada) = (e as? EscuchaLibre.Entrada.Llamada)?.resto

    @Test fun reconoceLaLlamadaYDevuelveLoQueSigue() {
        val l = EscuchaLibre()
        assertEquals("que hora es", resto(l.clasificar(0, "Aidly, que hora es")))
        assertEquals("cuéntame una noticia", resto(l.clasificar(0, "oye Aidly cuéntame una noticia")))
        assertEquals("", resto(l.clasificar(0, "Hey Aidly")))
        assertEquals("what time is it", resto(l.clasificar(0, "Aidly: what time is it?")))
    }

    @Test fun sinLaPalabraNoHaceCaso() {
        val l = EscuchaLibre()
        assertTrue(l.clasificar(0, "el partido empieza a las ocho") is EscuchaLibre.Entrada.Nada)
        assertTrue(l.clasificar(0, "la caridad no es ladrona") is EscuchaLibre.Entrada.Nada)   // "aidly"-parecido no debe disparar
    }

    @Test fun conversacionSinRepetirElNombreSoloEnLaVentana() {
        val l = EscuchaLibre()
        l.abrirVentana(1_000)
        assertTrue(l.clasificar(5_000, "y mañana va a llover") is EscuchaLibre.Entrada.Continuacion)
        assertTrue(l.clasificar(20_000, "y mañana va a llover") is EscuchaLibre.Entrada.Nada)   // la ventana ya cerro
    }

    @Test fun elSilencioCierraLaVentana() {
        val l = EscuchaLibre()
        l.abrirVentana(0)
        l.clasificar(2_000, null)
        assertFalse(l.enVentana(3_000))
    }

    @Test fun laVentanaTieneTopeDeTurnos() {
        val l = EscuchaLibre(EscuchaLibre.Config(maxTurnosVentana = 2))
        l.abrirVentana(0)
        assertTrue(l.clasificar(1_000, "uno") is EscuchaLibre.Entrada.Continuacion)
        assertTrue(l.clasificar(2_000, "dos") is EscuchaLibre.Entrada.Continuacion)
        assertTrue(l.clasificar(3_000, "tres") is EscuchaLibre.Entrada.Nada)
    }

    @Test fun descansaTrasVariosIntentosVacios() {
        val l = EscuchaLibre(EscuchaLibre.Config(vaciosParaDescansar = 3, descansoMs = 30_000, entreIntentosMs = 1_000))
        var t = 0L
        repeat(3) { t += 2_000; assertTrue(l.puedeEscuchar(t, true)); l.empezoIntento(t); l.clasificar(t, "el partido va uno a uno") }
        assertFalse(l.puedeEscuchar(t + 5_000, true))
        assertTrue(l.puedeEscuchar(t + 31_000, true))
    }

    @Test fun elSilencioNoHaceDescansarYPuedeOirUnaLlamadaDespues() {
        val l = EscuchaLibre(EscuchaLibre.Config(vaciosParaDescansar = 3, entreIntentosMs = 0))
        repeat(20) { l.empezoIntento(it * 7_000L); l.clasificar(it * 7_000L, null) }
        assertTrue(l.puedeEscuchar(150_000, true))
        assertEquals("hola", resto(l.clasificar(150_000, "Aidly hola")))
    }

    @Test fun unFalloInmediatoHaceDescansar() {
        val l = EscuchaLibre(EscuchaLibre.Config(descansoMs = 30_000, entreIntentosMs = 0))
        l.fallo(1_000)
        assertFalse(l.puedeEscuchar(10_000, true))
        assertTrue(l.puedeEscuchar(32_000, true))
    }

    @Test fun nuncaEscuchaSiLaCamaraVeQueNoHayNadie() {
        val l = EscuchaLibre()
        assertFalse(l.puedeEscuchar(10_000, false))
        assertTrue(l.puedeEscuchar(10_000, null))
        assertTrue(l.puedeEscuchar(10_000, true))
    }

    @Test fun respetaElTopePorHora() {
        val l = EscuchaLibre(EscuchaLibre.Config(maxPorHora = 3, entreIntentosMs = 0, vaciosParaDescansar = 99))
        repeat(3) { l.empezoIntento(it * 1_000L) }
        assertFalse(l.puedeEscuchar(4_000, true))
        assertTrue(l.puedeEscuchar(3_700_000, true))
    }
}
