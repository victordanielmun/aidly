package com.aidly.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ContextoActividadTest {
    @Test fun etiquetasAContexto() {
        assertEquals(Contexto.COCINA, Contexto.deEtiquetas(listOf("oven", "cup")))
        assertEquals(Contexto.LEYENDO, Contexto.deEtiquetas(listOf("book")))
        assertEquals(Contexto.ORDENADOR, Contexto.deEtiquetas(listOf("laptop")))
        assertEquals(Contexto.TELE, Contexto.deEtiquetas(listOf("tv")))
        assertNull(Contexto.deEtiquetas(listOf("cup", "chair")))
        assertNull(Contexto.deEtiquetas(emptyList()))
    }

    @Test fun laCocinaGanaALaTele() = assertEquals(Contexto.COCINA, Contexto.deEtiquetas(listOf("tv", "microwave")))

    @Test fun laTeleNoOfreceNada() = assertNull(Contexto.TELE.tema)

    @Test fun hacenFaltaVariasMedicionesParaDarloPorBueno() {
        val c = ContextoEstable(ventana = 8, minimo = 5)
        repeat(4) { assertNull(c.observar(listOf("book"))) }
        assertEquals(Contexto.LEYENDO, c.observar(listOf("book")))
    }

    @Test fun unaDeteccionSueltaNoCuenta() {
        val c = ContextoEstable()
        repeat(7) { c.observar(emptyList()) }
        assertNull(c.observar(listOf("oven")))
    }

    @Test fun losHuecosSinMedirNoCuentan() {
        val c = ContextoEstable(ventana = 8, minimo = 3)
        c.observar(listOf("book")); c.observar(null); c.observar(null); c.observar(listOf("book"))
        assertNull(c.actual())
        assertEquals(Contexto.LEYENDO, c.observar(listOf("book")))
    }

    @Test fun dejaDeSerElContextoCuandoYaNoSeVe() {
        val c = ContextoEstable(ventana = 6, minimo = 4)
        repeat(6) { c.observar(listOf("laptop")) }
        assertEquals(Contexto.ORDENADOR, c.actual())
        repeat(3) { c.observar(emptyList()) }
        assertNull(c.actual())
    }
}
