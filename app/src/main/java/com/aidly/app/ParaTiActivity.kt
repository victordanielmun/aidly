package com.aidly.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * "Para ti": tarjetas con cosas que le gustan a la persona (datos, recetas, musica, noticias buenas), que Aidly lee en voz alta y sobre las que
 * conversa. Funciona siempre con las tarjetas que trae la app; si la nube esta encendida, ademas busca novedades. Los gustos se guardan
 * cifrados solo en el telefono.
 */
class ParaTiActivity : ActividadAidly() {
    override val protegida get() = true
    private lateinit var voz: Voz

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voz = Voz(this)
        setContent { AidlyTema { Pantalla(voz) { finish() } } }
    }

    override fun onDestroy() {
        voz.cerrar()
        super.onDestroy()
    }
}

@Composable
private fun Pantalla(voz: Voz, cerrar: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var perfil by remember { mutableStateOf(Intereses.cargar(ctx)) }
    var editando by remember { mutableStateOf(perfil.elegidos.isEmpty()) }
    var tarjetas by remember { mutableStateOf<List<Tarjeta>>(emptyList()) }
    var indice by remember { mutableIntStateOf(0) }
    var buscando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf("") }
    var hablando by remember { mutableStateOf(false) }
    var pregunta by remember { mutableStateOf("") }
    var respuesta by remember { mutableStateOf("") }
    var quiereHablar by remember { mutableIntStateOf(0) }

    val habla by voz.hablando.collectAsState()
    val animar = remember { !Preferencias.reducirMovimiento(ctx) }
    fun cambiar(f: (Perfil) -> Perfil) { perfil = Intereses.cambiar(ctx, f) }
    val pedirMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) quiereHablar++ }
    var quiereGusto by remember { mutableIntStateOf(0) }
    var anadiendo by remember { mutableStateOf(false) }
    val pedirMicGusto = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) quiereGusto++ }
    fun hayMic() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Abre una pagina en el navegador (la noticia o una busqueda de Google): la persona sale de Aidly a proposito, al tocar. */
    fun abrir(url: String) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) { SpikeLog.log("PARATI: no pude abrir la pagina (${e::class.simpleName})") }
    }
    fun google(consulta: String) = abrir("https://www.google.com/search?q=" + Uri.encode(consulta))

    suspend fun buscarNovedades(forzar: Boolean) {
        if (!Privacidad.nubeActiva()) { aviso = ctx.getString(R.string.pt_solo_guardado); return }
        buscando = true; aviso = ""
        try {
            val n = FeedTarjetas.obtener(ctx, forzar)
            if (n.tarjetas.isNotEmpty() && (!n.deCache || forzar)) { tarjetas = n.tarjetas; indice = 0; pregunta = ""; respuesta = "" }
            else if (n.tarjetas.isEmpty()) aviso = ctx.getString(R.string.pt_sin_novedades)
        } catch (e: Exception) {
            SpikeLog.log("PARATI: no pude traer novedades (${e::class.simpleName}: ${e.message?.take(80)})")
            aviso = ctx.getString(R.string.pt_sin_novedades)
        }
        buscando = false
    }

    // Lo primero que se ve sale al instante (cache o tarjetas de la app); despues, si hay nube, se buscan novedades sin bloquear.
    val clave = perfil.elegidos.sorted().joinToString(",")
    LaunchedEffect(clave, editando) {
        if (editando || perfil.elegidos.isEmpty()) return@LaunchedEffect
        val r = FeedTarjetas.rapido(ctx)
        tarjetas = r.tarjetas; indice = 0
        if (r.origen != "cache") buscarNovedades(false) else if (!Privacidad.nubeActiva()) aviso = ctx.getString(R.string.pt_solo_guardado)
    }

    val actual = tarjetas.getOrNull(indice)
    LaunchedEffect(actual?.titulo) { actual?.let { t -> cambiar { it.conVisto(t.titulo) }; pregunta = ""; respuesta = "" } }

    fun leer(t: Tarjeta) {
        voz.callar()
        voz.decir(if (t.general) "${t.titulo}. ${t.resumen}" else "${t.titulo}. ${t.resumen} ${Idioma.vozTexto(ctx, R.string.v_pt_segun, t.fuente)}")
    }

    LaunchedEffect(quiereHablar) {
        if (quiereHablar == 0) return@LaunchedEffect
        val t = actual ?: return@LaunchedEffect
        hablando = true; pregunta = ""; respuesta = ""
        try {
            voz.callar()
            withTimeoutOrNull(15_000) { voz.decirYEsperar(Idioma.vozTexto(ctx, R.string.v_pt_dime)) }
            val q = if (Privacidad.nubeActiva()) GeminiStt.escuchar(ctx) else EscuchaSistema.escuchar(ctx)
            if (q == null) { respuesta = ctx.getString(R.string.hablar_error_escuchar) }
            else {
                pregunta = q
                respuesta = AsistenteVoz.responderSobre(ctx, t, q).texto
            }
        } catch (e: Exception) {
            SpikeLog.log("PARATI: error en la conversacion (${e::class.simpleName}: ${e.message?.take(80)})")
            respuesta = ctx.getString(R.string.hablar_error_responder)
        }
        hablando = false
        if (respuesta.isNotBlank()) voz.decir(respuesta)
    }

    // "¿Que te gusta leer o ver?": la persona lo dice, se anota la frase y se buscan cosas parecidas.
    LaunchedEffect(quiereGusto) {
        if (quiereGusto == 0) return@LaunchedEffect
        anadiendo = true; aviso = ""
        try {
            voz.callar()
            withTimeoutOrNull(20_000) { voz.decirYEsperar(Idioma.vozTexto(ctx, R.string.v_pt_gustos_pregunta)) }
            val q = if (Privacidad.nubeActiva()) GeminiStt.escuchar(ctx) else EscuchaSistema.escuchar(ctx)
            val nuevo = q?.let { Perfil.limpiarGusto(it) }
            if (nuevo == null) aviso = ctx.getString(R.string.pt_gusto_no)
            else {
                cambiar { it.conGusto(nuevo) }
                aviso = ctx.getString(R.string.pt_gusto_anotado, nuevo)
                voz.decir(aviso)
                if (Privacidad.nubeActiva()) { anadiendo = false; buscarNovedades(true) }
            }
        } catch (e: Exception) { SpikeLog.log("PARATI: error al anotar un gusto (${e::class.simpleName})"); aviso = ctx.getString(R.string.pt_gusto_no) }
        anadiendo = false
    }

    fun siguiente(reaccion: Int = 0) {
        val t = actual
        if (t != null && reaccion != 0) cambiar { it.conReaccion(t.tema, reaccion) }
        voz.callar()
        if (tarjetas.isNotEmpty()) indice = (indice + 1) % tarjetas.size
    }

    Column(Modifier.fillMaxSize().background(Tema.Fondo).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            EncabezadoPantalla(stringResource(if (editando) R.string.pt_gustos_t else R.string.pt_titulo)) { voz.callar(); cerrar() }

            if (!editando) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AidlyCara(if (habla) Emocion.HABLANDO else if (hablando) Emocion.ESCUCHANDO else Emocion.REPOSO, Modifier.fillMaxWidth(0.3f), animar, habla)
            }
            if (!editando && !perfil.preguntado) Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Tema.RadioPrincipal)).background(Tema.Tarjeta).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.pt_gusto_pregunta_t), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Tema.Turquesa)
                Text(stringResource(R.string.pt_gusto_pregunta_s), fontSize = 20.sp, color = Tema.Texto)
                BotonGrande(stringResource(R.string.pt_decirmelo), Tema.Turquesa, alto = 72.dp) {
                    if (hayMic()) quiereGusto++ else pedirMicGusto.launch(Manifest.permission.RECORD_AUDIO)
                }
                BotonGrande(stringResource(R.string.pt_ahora_no), Tema.Tarjeta, Tema.Texto, alto = 56.dp, tamLetra = 20) { cambiar { it.conPreguntado(true) } }
            }
            if (editando) {
                Text(stringResource(R.string.pt_gustos_sub), fontSize = 20.sp, color = Tema.TextoSuave)
                Intereses.TEMAS.forEach { tema ->
                    InterruptorFila(null, stringResource(tema.nombre), "", tema.id in perfil.elegidos) { on -> cambiar { it.conElegido(tema.id, on) } }
                }
                BotonGrande(stringResource(R.string.pt_listo), if (perfil.elegidos.isEmpty()) Tema.Tarjeta else Tema.Turquesa, if (perfil.elegidos.isEmpty()) Tema.TextoSuave else Tema.Oscuro) {
                    if (perfil.elegidos.isNotEmpty()) editando = false
                }
            } else if (actual == null) {
                if (buscando) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = Tema.Amarillo); Text(stringResource(R.string.pt_buscando), fontSize = 22.sp, color = Tema.Texto)
                } else Text(stringResource(R.string.pt_sin_gustos), fontSize = 22.sp, color = Tema.Texto)
            } else {
                // La tarjeta
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(Tema.RadioPrincipal)).background(Tema.Tarjeta).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (actual.imagen.isNotBlank()) {
                        // La imagen viene de la fuente (BBC, Wikipedia...): solo se baja con la nube encendida y, si falla, la tarjeta sale igual.
                        val foto by produceState<Bitmap?>(null, actual.imagen) { value = ImagenWeb.cargar(actual.imagen) }
                        foto?.let { b ->
                            Image(
                                b.asImageBitmap(), contentDescription = actual.titulo, contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(Tema.RadioFila))
                                    .then(if (actual.enlace.isNotBlank()) Modifier.clickable(role = Role.Button, onClickLabel = stringResource(R.string.pt_leer_noticia)) { abrir(actual.enlace) } else Modifier),
                            )
                            Text(stringResource(R.string.pt_imagen, if (actual.fuente == "Wikipedia") "Wikimedia Commons" else actual.fuente), fontSize = 16.sp, color = Tema.TextoSuave)
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(if (actual.general) R.string.pt_dato else R.string.pt_web), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (actual.general) Tema.Turquesa else Tema.Amarillo)
                        Text(stringResource(R.string.pt_pos, indice + 1, tarjetas.size), fontSize = 18.sp, color = Tema.TextoSuave)
                    }
                    Text(actual.titulo, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Tema.Texto, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    Text(actual.resumen, fontSize = 26.sp, color = Tema.Texto)
                    if (!actual.general) {
                        Text(stringResource(R.string.pt_segun, actual.fuente) + if (actual.fecha.isNotBlank()) " · ${actual.fecha}" else "", fontSize = 18.sp, color = Tema.TextoSuave)
                        if (actual.ia) Text(stringResource(R.string.pt_ia), fontSize = 18.sp, color = Tema.TextoSuave)
                    }
                }
                BotonGrande(stringResource(R.string.pt_leemela), Tema.Turquesa) { leer(actual) }
                BotonGrande(stringResource(if (hablando) R.string.pt_te_escucho else R.string.pt_hablame), Tema.Amarillo) {
                    if (hablando) return@BotonGrande
                    if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) quiereHablar++
                    else pedirMic.launch(Manifest.permission.RECORD_AUDIO)
                }
                if (actual.enlace.isNotBlank()) BotonGrande(stringResource(R.string.pt_leer_noticia), Tema.Tarjeta, Tema.Texto, alto = 64.dp, tamLetra = 20) { abrir(actual.enlace) }
                BotonGrande(stringResource(R.string.pt_google), Tema.Tarjeta, Tema.Texto, alto = 64.dp, tamLetra = 20) { google(actual.titulo) }
                if (pregunta.isNotBlank()) Text(stringResource(R.string.hablar_tu, pregunta), fontSize = 20.sp, color = Tema.TextoSuave)
                if (respuesta.isNotBlank()) {
                    Text(
                        respuesta, Modifier.fillMaxWidth().clip(RoundedCornerShape(Tema.RadioFila)).background(Tema.Tarjeta).padding(14.dp).semantics { liveRegion = LiveRegionMode.Polite },
                        fontSize = 24.sp, color = Tema.Texto,
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) { BotonGrande(stringResource(R.string.pt_me_interesa), Tema.Tarjeta, Tema.Texto, alto = 64.dp, tamLetra = 20) { siguiente(+1) } }
                    Column(Modifier.weight(1f)) { BotonGrande(stringResource(R.string.pt_no_interesa), Tema.Tarjeta, Tema.Texto, alto = 64.dp, tamLetra = 20) { siguiente(-1) } }
                }
                if (tarjetas.size > 1) BotonGrande(stringResource(R.string.pt_otra), Tema.Tarjeta, Tema.Texto, alto = 64.dp, tamLetra = 20) { siguiente() }
            }

            if (!editando) {
                if (buscando && actual != null) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(color = Tema.Amarillo); Text(stringResource(R.string.pt_buscando), fontSize = 20.sp, color = Tema.TextoSuave)
                }
                if (aviso.isNotBlank()) Text(aviso, fontSize = 18.sp, color = Tema.TextoSuave)
                if (Privacidad.nubeActiva() && !buscando) BotonGrande(stringResource(R.string.pt_buscar), Tema.Tarjeta, Tema.Texto, alto = 64.dp, tamLetra = 20) { scope.launch { buscarNovedades(true) } }
                TituloSeccion(stringResource(R.string.pt_gustos_libres))
                perfil.gustos.forEach { g ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(Tema.RadioFila)).background(Tema.Tarjeta).padding(start = 16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(g, Modifier.weight(1f), fontSize = 22.sp, color = Tema.Texto)
                        Text(
                            stringResource(R.string.pt_quitar), Modifier.heightIn(min = 56.dp).clickable(role = Role.Button) { cambiar { it.sinGusto(g) } }.padding(horizontal = 16.dp, vertical = 14.dp),
                            fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Tema.Coral,
                        )
                    }
                }
                BotonGrande(stringResource(if (anadiendo) R.string.pt_te_escucho else R.string.pt_gusto_add), Tema.Tarjeta, Tema.Texto, alto = 64.dp, tamLetra = 20) {
                    if (anadiendo) return@BotonGrande
                    if (hayMic()) quiereGusto++ else pedirMicGusto.launch(Manifest.permission.RECORD_AUDIO)
                }
                InterruptorFila(Iconos.Altavoz, stringResource(R.string.pt_propuestas), stringResource(R.string.pt_propuestas_sub), perfil.propuestas) { on -> cambiar { it.conPropuestas(on) } }
                Text(
                    stringResource(R.string.pt_cambiar),
                    Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { voz.callar(); editando = true }.padding(vertical = 12.dp),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Tema.Turquesa,
                )
            }
        }
    }
}
