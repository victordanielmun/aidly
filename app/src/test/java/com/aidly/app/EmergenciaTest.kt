package com.aidly.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmergenciaTest {
    @Test fun aceptaNumerosCortosYLargos() {
        assertEquals("123", Emergencia.limpiarNumero("123"))
        assertEquals("3001234567", Emergencia.limpiarNumero(" 300 123-4567 "))
        assertEquals("+573001234567", Emergencia.limpiarNumero("+57 (300) 123 4567"))
    }

    @Test fun rechazaLoQueNoSePuedeMarcar() {
        assertNull(Emergencia.limpiarNumero(""))
        assertNull(Emergencia.limpiarNumero("12"))
        assertNull(Emergencia.limpiarNumero("1234567890123456"))   // 16 digitos
        assertNull(Emergencia.limpiarNumero("30+0123456"))          // el + solo va al comienzo
        assertNull(Emergencia.limpiarNumero("++573001234567"))
        assertNull(Emergencia.limpiarNumero("abc"))
    }
}
