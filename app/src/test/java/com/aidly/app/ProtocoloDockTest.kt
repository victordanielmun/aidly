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

    @Test fun errBusyNoEsUnFalloDefinitivo() {
        val p = PuertoFalso("AIDLY-DOCK 1 ROT=180", "ERR BUSY", "ERR RANGE")
        val c = ClienteDock(p); c.saludar()
        assertFalse(c.irA(10)); assertTrue(c.ocupado); assertEquals("ERR BUSY", c.ultimoError)
        assertFalse(c.irA(10)); assertFalse(c.ocupado)
    }

    @Test fun okLimpiaElUltimoError() {
        val p = PuertoFalso("AIDLY-DOCK 1 ROT=180", "ERR ARG", "OK")
        val c = ClienteDock(p); c.saludar()
        c.velocidad(3); assertEquals("ERR ARG", c.ultimoError)
        assertTrue(c.parar()); assertEquals(null, c.ultimoError)
    }

    @Test fun esperarReposoConsultaHastaQueDejeDeMoverse() {
        val p = PuertoFalso("AIDLY-DOCK 1 ROT=180", "POS 100 MOVING 1 LIM 0", "POS 40 MOVING 1 LIM 0", "POS 0 MOVING 0 LIM 0")
        val c = ClienteDock(p, pausa = {}); c.saludar()
        assertTrue(c.esperarReposo(5_000))
        assertEquals(listOf("HELLO", "STATUS", "STATUS", "STATUS"), p.escrito)
    }

    @Test fun esperarReposoFallaSiElDockDejaDeResponder() {
        val p = PuertoFalso("AIDLY-DOCK 1 ROT=180", "POS 100 MOVING 1 LIM 0")
        val c = ClienteDock(p, pausa = {}); c.saludar()
        assertFalse(c.esperarReposo(5_000))
    }

    // --- DockConectado: HOME al conectar, ERR BUSY y correccion con STATUS ---

    private fun conectado(vararg r: String): Pair<DockConectado, PuertoFalso> {
        val p = PuertoFalso("AIDLY-DOCK 1 ROT=180", *r)
        val c = ClienteDock(p, pausa = {}); c.saludar()
        val d = DockConectado(c)
        d.sincronizar()                              // espera al HOME inicial del hilo del dock
        return d to p
    }

    @Test fun alConectarHaceHomeYEsperaAntesDeMover() {
        val (d, p) = conectado("OK", "POS 120 MOVING 1 LIM 0", "POS 0 MOVING 0 LIM 0", "OK")
        d.seguir(1f, 1000); d.seguir(1f, 1500)      // 24 grados por segundo durante 0,5 s = 12 grados
        d.sincronizar()
        assertEquals(listOf("HELLO", "HOME", "STATUS", "STATUS", "GOTO 12"), p.escrito)
    }

    @Test fun siElOrigenFallaNuncaMueveYMandaStop() {
        val (d, p) = conectado("ERR CMD", "OK")
        d.sincronizar()
        d.seguir(1f, 1000); d.seguir(1f, 1500); d.sincronizar()
        assertEquals(listOf("HELLO", "HOME", "STOP"), p.escrito)
    }

    @Test fun errBusyNoDesactivaElDockYSeReintenta() {
        val (d, p) = conectado("OK", "POS 0 MOVING 0 LIM 0", "ERR BUSY", "OK")
        d.seguir(1f, 1000); d.seguir(1f, 1500); d.sincronizar()
        d.seguir(1f, 1900); d.sincronizar()           // el siguiente cuadro vuelve a intentarlo
        assertEquals(listOf("HELLO", "HOME", "STATUS", "GOTO 12"), p.escrito.take(4))
        assertTrue(p.escrito[4].startsWith("GOTO "))   // reintento con el angulo ya actualizado; sin STOP
    }

    @Test fun corrigeElAnguloSiElDockQuedoEnOtroSitio() {
        val (d, p) = conectado("OK", "POS 0 MOVING 0 LIM 0", "OK", "POS 5 MOVING 0 LIM 0")
        d.seguir(1f, 1000); d.seguir(1f, 1500)       // GOTO 12
        d.seguir(null, 2600); d.sincronizar()           // pasaron mas de 1 s: consulta STATUS
        assertEquals("STATUS", p.escrito.last())
        assertEquals(5f, d.angulo.value, 0.01f)
    }

    // --- Simulador: no gira sin parar con el telefono quieto ---
    @Test fun elSimuladorSePuraAlLlegarAlRumboDeLaPersona() {
        val d = DockSimulado(camaraGiraConDock = false)
        var ms = 1000L
        repeat(400) { d.seguir(0.8f, ms); ms += 100 }           // la persona siempre aparece a la derecha; el telefono no se mueve
        assertEquals(48f, d.angulo.value, 8f)                   // 0,8 x 60 grados, y se queda ahi
        repeat(100) { d.seguir(0.8f, ms); ms += 100 }
        assertEquals(48f, d.angulo.value, 8f)
    }

    @Test fun elSimuladorSigueAUnaPersonaQueSeMueve() {
        val d = DockSimulado(camaraGiraConDock = false)
        var ms = 1000L
        repeat(300) { d.seguir(0.8f, ms); ms += 100 }
        repeat(300) { d.seguir(-0.8f, ms); ms += 100 }          // pasa al otro lado
        assertTrue(d.angulo.value < 20f)
    }

    @Test fun conCamaraRealElControlNoCambia() {
        val d = DockSimulado()
        d.seguir(0.5f, 1000); d.seguir(0.5f, 1500)
        assertEquals(12.5f, d.angulo.value, 0.6f)               // 25 grados por segundo durante 0,5 s
    }
}

