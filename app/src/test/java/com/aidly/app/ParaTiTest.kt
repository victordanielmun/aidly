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

    // ---------- Fuentes web (partes puras) ----------
    private val hoy = java.time.LocalDate.of(2026, 11, 23)

    @Test fun efemeridesDeWikipediaSeConvierteEnTarjetasSeguras() {
        val j = """{"events":[
            {"text":"Se inaugura en la ciudad el primer teatro de la opera del pais, con una gran funcion.","year":1884},
            {"text":"Muere el famoso poeta tras una larga enfermedad en su casa de campo.","year":1901},
            {"text":"Comienza la guerra entre dos paises vecinos por el control de la frontera.","year":1950},
            {"text":"Corto","year":2000}]}"""
        val r = Tarjeta::class.java; val t = FuentesWeb.efemerides(j, "es", hoy)
        assertEquals(1, t.size)
        assertEquals("El 23 de noviembre de 1884", t[0].titulo); assertEquals("Wikipedia", t[0].fuente); assertEquals("historia", t[0].tema); assertFalse(t[0].ia)
        assertEquals("On November 23, 1884", FuentesWeb.efemerides(j, "en", hoy)[0].titulo)
        assertTrue(FuentesWeb.efemerides("no es json", "es", hoy).isEmpty())
    }

    private val rss = """<?xml version="1.0"?><rss><channel><title>BBC</title>
        <item><title>Descubren una nueva especie de mariposa en la selva</title><description>&lt;p&gt;Un equipo la encontro&lt;/p&gt; en una region remota.</description><pubDate>Mon, 23 Nov 2026 10:30:00 GMT</pubDate></item>
        <item><title>Hallan sin vida a un estudiante desaparecido</title><description>Detalles</description><pubDate>Mon, 23 Nov 2026 09:00:00 GMT</pubDate></item>
        <item><title>Corto</title><description>x</description></item>
        </channel></rss>"""

    @Test fun titularesRssSeLeenYSeFiltran() {
        val t = FuentesWeb.titulares(rss, "BBC Mundo")
        assertEquals(1, t.size)
        assertEquals("Descubren una nueva especie de mariposa en la selva", t[0].titulo)
        assertEquals("Un equipo la encontro en una region remota.", t[0].resumen)
        assertEquals("BBC Mundo", t[0].fuente); assertEquals("2026-11-23", t[0].fecha); assertEquals("noticias", t[0].tema)
    }

    @Test fun rssConDoctypeOMalFormadoSeRechaza() {
        assertTrue(FuentesWeb.titulares("<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY a \"b\">]><rss/>", "X").isEmpty())
        assertTrue(FuentesWeb.titulares("<rss><item>", "X").isEmpty())
    }

    @Test fun ciudadDeLaFrase() {
        assertEquals("Bogotá", FuentesWeb.ciudadDe("¿Cómo está el clima hoy en Bogotá?"))
        assertEquals("Madrid", FuentesWeb.ciudadDe("What is the weather like in Madrid today?"))
        assertEquals("Medellín", FuentesWeb.ciudadDe("Dime el clima de Medellín"))
        assertEquals(null, FuentesWeb.ciudadDe("¿Cómo está el clima hoy?"))
        assertEquals(null, FuentesWeb.ciudadDe("Dime el clima en la mañana"))
    }

    @Test fun reconoceLasPreguntasDeClima() {
        assertTrue(FuentesWeb.esClima("como esta el clima hoy en bogota")); assertTrue(FuentesWeb.esClima("will it rain tomorrow"))
        assertFalse(FuentesWeb.esClima("cuanto cuesta el dolar"))
    }

    @Test fun textoDelClimaEnLosDosIdiomas() {
        val j = """{"current":{"temperature_2m":14.4,"weather_code":3},"daily":{"temperature_2m_max":[19.2],"temperature_2m_min":[9.1]}}"""
        assertEquals("Ahora en Bogotá hay 14 grados y está nublado. Hoy la máxima será de 19 y la mínima de 9.", FuentesWeb.textoClima(j, "Bogotá", "es"))
        assertEquals("Right now in Bogotá it is 14 degrees and cloudy. Today the high will be 19 and the low 9.", FuentesWeb.textoClima(j, "Bogotá", "en"))
        assertEquals(null, FuentesWeb.textoClima("{}", "X", "es"))
    }

    @Test fun lasTarjetasDeLaIaLlevanElAviso() {
        assertTrue(Tarjetas.parsear("""[{"titulo":"Un titulo","resumen":"r","tema":"musica","fuente":"Diario"}]""")[0].ia)
    }

    @Test fun leeLosNumerosQueDejaElModelo() {
        assertEquals(listOf(0, 3), Tarjetas.indicesDe("[1, 4]", 5))
        assertEquals(listOf(1), Tarjetas.indicesDe("Dejo estos: [2, 2, 9]", 5))
        assertEquals(emptyList<Int>(), Tarjetas.indicesDe("[]", 5))
        assertEquals(emptyList<Int>(), Tarjetas.indicesDe("ninguno", 5))
    }
}

