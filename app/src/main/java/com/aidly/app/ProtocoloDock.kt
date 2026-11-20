package com.aidly.app

/** Un puerto de texto hacia el dock (el USB real en [DockUsb]; un puerto simulado en las pruebas). */
interface PuertoDock {
    fun escribir(linea: String)
    /** Siguiente linea recibida, o null si no llego nada en [timeoutMs]. */
    fun leer(timeoutMs: Int): String?
    fun cerrar()
}

/**
 * Cliente del protocolo del dock v1 (dock/PROTOCOLO.md). Regla de seguridad: no manda movimiento hasta que el dispositivo responda
 * al saludo con "AIDLY-DOCK", para no hablarle a un puerto serie cualquiera.
 */
class ClienteDock(private val puerto: PuertoDock, private val alEvento: (String) -> Unit = {}) {
    var saludado = false
        private set
    var version = 0
        private set
    var recorrido = 180
        private set

    /** Lee hasta la siguiente respuesta (las lineas EVT se entregan aparte). */
    private fun respuesta(timeoutMs: Int = 1500): String? {
        val limite = System.currentTimeMillis() + timeoutMs
        while (true) {
            val resto = (limite - System.currentTimeMillis()).toInt()
            if (resto <= 0) return null
            val l = puerto.leer(resto)?.trim() ?: return null
            if (l.startsWith("EVT ")) { alEvento(l.removePrefix("EVT ")); continue }
            if (l.isNotEmpty()) return l
        }
    }

    fun saludar(): Boolean {
        puerto.escribir("HELLO")
        val r = respuesta() ?: return false
        val p = r.split(" ")
        if (p.firstOrNull() != "AIDLY-DOCK") return false
        version = p.getOrNull(1)?.toIntOrNull() ?: return false
        recorrido = p.firstOrNull { it.startsWith("ROT=") }?.removePrefix("ROT=")?.toIntOrNull()?.coerceIn(10, 180) ?: 180
        saludado = true
        return true
    }

    private fun orden(texto: String): Boolean {
        if (!saludado) return false
        puerto.escribir(texto)
        return respuesta() == "OK"
    }

    fun irA(grados: Int): Boolean = orden("GOTO ${grados.coerceIn(-recorrido, recorrido)}")
    fun girar(grados: Int): Boolean = orden("ROT $grados")
    fun origen(): Boolean = orden("HOME")
    fun velocidad(n: Int): Boolean = orden("SPEED ${n.coerceIn(1, 10)}")
    fun parar(): Boolean = orden("STOP")

    class Estado(val grados: Int, val moviendo: Boolean, val limite: Boolean)

    fun estado(): Estado? {
        if (!saludado) return null
        puerto.escribir("STATUS")
        val p = respuesta()?.split(" ") ?: return null
        if (p.size < 6 || p[0] != "POS" || p[2] != "MOVING" || p[4] != "LIM") return null
        return Estado(p[1].toIntOrNull() ?: return null, p[3] == "1", p[5] == "1")
    }

    /** Atiende eventos sueltos (por ejemplo el boton del dock) si no hay una orden en curso. */
    fun escucharEventos(timeoutMs: Int) { respuesta(timeoutMs) }

    fun cerrar() { puerto.cerrar() }
}
