package com.aidly.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ParaTiTest {
    // ---------- Perfil ----------
    @Test fun perfilGuardaYLeeSuJson() {
        val p = Perfil().conElegido("musica", true).conElegido("cocina", true).conReaccion("musica", 2).conVisto("Un titulo").conPropuestas(true).conNoMasHasta(123L)
        val q = Perfil.deJson(p.aJson())
        assertEquals(setOf("musica", "cocina"), q.elegidos); assertEquals(2, q.reacciones["musica"])
        assertTrue(q.haVisto("UN TÍTULO")); assertTrue(q.propuestas); assertEquals(123L, q.noMasHasta)
    }

    @Test fun jsonRoto_o_vacio_daPerfilVacio() {
        assertTrue(Perfil.deJson(null).elegidos.isEmpty()); assertTrue(Perfil.deJson("{no es json").elegidos.isEmpty())
        assertFalse(Perfil.deJson("").propuestas)
    }

    @Test fun lasReaccionesSeLimitanYOrdenanLosTemas() {
        var p = Perfil().conElegido("a", true).conElegido("b", true).conElegido("c", true)
        repeat(10) { p = p.conReaccion("b", 1) }
        p = p.conReaccion("c", -1)
        assertEquals(3, p.reacciones["b"])
        assertEquals(listOf("b", "a", "c"), p.temasParaBuscar())
    }

    @Test fun unTemaRechazadoDosVecesYaNoSale() {
        val p = Perfil().conElegido("a", true).conElegido("b", true).conReaccion("a", -1).conReaccion("a", -1)
        assertEquals(listOf("b"), p.temasParaBuscar())
    }

    @Test fun losVistosNoSeRepitenYSeLimitan() {
        var p = Perfil()
        repeat(80) { p = p.conVisto("Noticia $it") }
        assertEquals(Perfil.MAX_VISTOS, p.vistos.size); assertTrue(p.haVisto("noticia 79")); assertFalse(p.haVisto("noticia 0"))
        assertEquals(Perfil.MAX_VISTOS, p.conVisto("noticia 79").vistos.size)
    }

    // ---------- Tarjetas ----------
    private fun tarjeta(t: String, r: String = "Resumen amable.", f: String = "Diario Ejemplo", tema: String = "musica") =
        """{"titulo":"$t","resumen":"$r","tema":"$tema","fuente":"$f","fecha":"2026-11-20"}"""

    @Test fun leeUnaListaConTextoAlrededor() {
        val txt = "Aqui van:\n```json\n[${tarjeta("Uno")}, ${tarjeta("Dos")}]\n```"
        assertEquals(listOf("Uno", "Dos"), Tarjetas.parsear(txt).map { it.titulo })
    }

    @Test fun leeUnObjetoConTarjetas() {
        assertEquals(1, Tarjetas.parsear("""{"tarjetas":[${tarjeta("Uno")}]}""").size)
    }

    @Test fun sinFuenteNoHayTarjeta() {
        assertTrue(Tarjetas.parsear("[${tarjeta("Uno", f = "")}]").isEmpty())
    }

    @Test fun descartaTemasDelicadosEnLosDosIdiomas() {
        val malas = listOf("Muere un famoso actor", "Elecciones: el candidato dice", "Cura milagrosa para todo", "Invierte en criptomonedas ya", "Man killed in shooting", "Election results tonight", "Gana en el casino")
        malas.forEach { assertTrue("debia filtrar: $it", Tarjetas.parsear("[${tarjeta(it)}]").isEmpty()) }
        assertEquals(1, Tarjetas.parsear("[${tarjeta("Una receta de pozole para el domingo")}]").size)
    }

    @Test fun noRepiteLoYaVistoNiDuplicados() {
        val txt = "[${tarjeta("Uno")}, ${tarjeta("UNO")}, ${tarjeta("Dos")}]"
        assertEquals(listOf("Dos"), Tarjetas.parsear(txt, vistos = listOf(normalizarTexto("uno"))).map { it.titulo })
        assertEquals(2, Tarjetas.parsear(txt).size)
    }

    @Test fun maximoTresYTemasValidos() {
        val txt = "[" + (1..6).joinToString(",") { tarjeta("T$it") } + "]"
        assertEquals(Tarjetas.MAX, Tarjetas.parsear(txt).size)
        assertTrue(Tarjetas.parsear("[${tarjeta("Uno", tema = "otro")}]", temasValidos = setOf("musica")).isEmpty())
    }

    @Test fun textoSinJsonDaListaVacia() {
        assertTrue(Tarjetas.parsear("lo siento, no encontre nada").isEmpty()); assertTrue(Tarjetas.parsear("[{").isEmpty())
    }

    // ---------- Politica de ofertas ----------
    private val perfilOk = Perfil().conElegido("musica", true).conPropuestas(true)
    private val medio = 12  // mediodia
    private fun pol() = PoliticaOfertas()

    @Test fun nuncaOfreceSiLaPersonaNoLoAcepto() {
        assertFalse(pol().puedeOfrecer(1_000_000, 5, medio, true, 0, false, Perfil().conElegido("musica", true)))
        assertFalse(pol().puedeOfrecer(1_000_000, 5, medio, true, 0, false, Perfil().conPropuestas(true)))   // sin temas
    }

    @Test fun ofreceCuandoTodoEncaja() {
        assertTrue(pol().puedeOfrecer(1_000_000, 5, medio, true, 0, false, perfilOk))
    }

    @Test fun respetaSilencioPresenciaBienestarYReposo() {
        val p = pol()
        assertFalse(p.puedeOfrecer(1_000_000, 5, 22, true, 0, false, perfilOk))     // noche
        assertFalse(p.puedeOfrecer(1_000_000, 5, 7, true, 0, false, perfilOk))      // madrugada
        assertFalse(p.puedeOfrecer(1_000_000, 5, medio, false, 0, false, perfilOk)) // no esta
        assertFalse(p.puedeOfrecer(1_000_000, 5, medio, true, 0, true, perfilOk))   // preguntando "¿estas bien?"
        assertFalse(p.puedeOfrecer(1_000_000, 5, medio, true, 950_000, false, perfilOk)) // acaba de hablar
    }

    @Test fun noMasPorHoyBloquea() {
        assertFalse(pol().puedeOfrecer(1_000_000, 5, medio, true, 0, false, perfilOk.conNoMasHasta(2_000_000)))
        assertTrue(pol().puedeOfrecer(2_000_001, 5, medio, true, 0, false, perfilOk.conNoMasHasta(2_000_000)))
    }

    @Test fun maximoTresAlDiaYEsperaEntreOfertas() {
        val p = pol(); var ms = 10_000_000L
        repeat(3) {
            assertTrue(p.puedeOfrecer(ms, 5, medio, true, 0, false, perfilOk)); p.ofrecida(ms, 5)
            assertFalse(p.puedeOfrecer(ms + 60_000, 5, medio, true, 0, false, perfilOk))   // muy pronto
            ms += 2_000_000
        }
        assertFalse(p.puedeOfrecer(ms, 5, medio, true, 0, false, perfilOk))              // cuarta del dia
        assertTrue(p.puedeOfrecer(ms, 6, medio, true, 0, false, perfilOk))               // dia siguiente
    }

    // ---------- Curadas ----------
    private fun c(tema: String, n: Int) = Tarjeta("T-$tema-$n", "r", tema, Tarjetas.FUENTE_GENERAL_ES, "")
    private val todas = listOf(c("a", 1), c("a", 2), c("b", 1), c("b", 2), c("c", 1))

    @Test fun curadasRepartidasEntreTemasElegidos() {
        val p = Perfil().conElegido("a", true).conElegido("b", true).conElegido("c", true)
        assertEquals(3, Curadas.elegir(todas, p).map { it.tema }.toSet().size)
    }

    @Test fun curadasSinTemasDaVacio() { assertTrue(Curadas.elegir(todas, Perfil()).isEmpty()) }

    @Test fun curadasPrefierenLasNoVistasYRepitenSiYaVioTodas() {
        val p = Perfil().conElegido("a", true).conVisto("T-a-1")
        assertEquals(listOf("T-a-2"), Curadas.elegir(todas, p).map { it.titulo })
        val todoVisto = p.conVisto("T-a-2")
        assertEquals(2, Curadas.elegir(todas, todoVisto).size)
    }

    @Test fun curadasSeLeenDelJsonEnLosDosIdiomas() {
        val j = """{"es":[{"tema":"a","titulo":"Uno","resumen":"r"}],"en":[{"tema":"a","titulo":"One","resumen":"r"}]}"""
        assertEquals("Uno", Curadas.deJson(j, "es")[0].titulo); assertEquals("One", Curadas.deJson(j, "en")[0].titulo)
        assertTrue(Curadas.deJson(j, "es")[0].general); assertTrue(Curadas.deJson("no", "es").isEmpty())
    }
}
