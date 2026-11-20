package com.aidly.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProtocoloDockTest {
    /** Puerto de mentira: guarda lo escrito y devuelve las respuestas que se le dieron. */
    private class PuertoFalso(vararg respuestas: String) : PuertoDock {
        val escrito = mutableListOf<String>()
        val cola = ArrayDeque(respuestas.toList())
        override fun escribir(linea: String) { escrito += linea }
        override fun leer(timeoutMs: Int): String? = cola.removeFirstOrNull()
        override fun cerrar() {}
    }

    @Test fun saludoCorrectoGuardaVersionYRecorrido() {
        val p = PuertoFalso("AIDLY-DOCK 1 ROT=170")
        val c = ClienteDock(p)
        assertTrue(c.saludar())
        assertEquals(listOf("HELLO"), p.escrito)
        assertEquals(1, c.version); assertEquals(170, c.recorrido)
    }

    @Test fun unDispositivoQueNoSaludaNuncaRecibeMovimiento() {
        val p = PuertoFalso("hola mundo")
        val c = ClienteDock(p)
        assertFalse(c.saludar())
        assertFalse(c.irA(20)); assertFalse(c.parar())
        assertEquals(listOf("HELLO"), p.escrito)
    }

    @Test fun sinRespuestaAlSaludoNoSeConsideraDock() {
        val c = ClienteDock(PuertoFalso())
        assertFalse(c.saludar())
    }

    @Test fun irALimitaAlRecorridoYEnviaGoto() {
        val p = PuertoFalso("AIDLY-DOCK 1 ROT=180", "OK", "OK")
        val c = ClienteDock(p); c.saludar()
        assertTrue(c.irA(400)); assertTrue(c.irA(-45))
        assertEquals(listOf("HELLO", "GOTO 180", "GOTO -45"), p.escrito)
    }

    @Test fun unErrorDelDockEsFalso() {
        val p = PuertoFalso("AIDLY-DOCK 1 ROT=180", "ERR RANGE")
        val c = ClienteDock(p); c.saludar()
        assertFalse(c.irA(10))
    }

    @Test fun losEventosNoSeConfundenConRespuestas() {
        val eventos = mutableListOf<String>()
        val p = PuertoFalso("AIDLY-DOCK 1 ROT=180", "EVT BTN", "OK")
        val c = ClienteDock(p) { eventos += it }; c.saludar()
        assertTrue(c.parar())
        assertEquals(listOf("BTN"), eventos)
    }

    @Test fun estadoSeInterpreta() {
        val p = PuertoFalso("AIDLY-DOCK 1 ROT=180", "POS -30 MOVING 1 LIM 0")
        val c = ClienteDock(p); c.saludar()
        val e = c.estado(); assertNotNull(e)
        assertEquals(-30, e!!.grados); assertTrue(e.moviendo); assertFalse(e.limite)
    }

    @Test fun estadoMalFormadoEsNulo() {
        val p = PuertoFalso("AIDLY-DOCK 1 ROT=180", "POS x")
        val c = ClienteDock(p); c.saludar()
        assertNull(c.estado())
    }

    @Test fun velocidadSeAcotaDe1a10() {
        val p = PuertoFalso("AIDLY-DOCK 1 ROT=180", "OK", "OK")
        val c = ClienteDock(p); c.saludar()
        c.velocidad(99); c.velocidad(-5)
        assertEquals(listOf("HELLO", "SPEED 10", "SPEED 1"), p.escrito)
    }
}
