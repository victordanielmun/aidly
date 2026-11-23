package com.aidly.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.drawscope.rotate as rotarDibujo
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Modo dock: el telefono apoyado en su base (o en cualquier soporte) muestra la cara de Aidly, la persona le habla con la voz
 * (por los parlantes y el microfono del dock cuando esta conectado) y, SOLO si ella lo acepto, la camara frontal sabe si esta
 * frente a Aidly para que el dock la siga. Si despues de conversar un rato la persona deja de verse, Aidly la llama y le pregunta
 * si esta bien. No hay vista previa, no se guarda ni se envia ninguna imagen, y no detecta caidas ni emergencias.
 */
class ModoDockActivity : ActividadAidly() {
    override val protegida get() = true
    private lateinit var voz: Voz

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        voz = Voz(this)
        Circulo.tocar(this)
        val sim = if (BuildConfig.DEBUG) intent.getStringExtra("sim") else null
        val cfg = if (BuildConfig.DEBUG && intent.getIntExtra("noVisibleS", 0) > 0)
            CompaniaAtenta.Config(noVisibleS = intent.getIntExtra("noVisibleS", 90), respuestaS = intent.getIntExtra("respuestaS", 15), esperaEntreRondasS = 30)
        else CompaniaAtenta.Config()
        setContent { AidlyTema { Pantalla(voz, cfg, sim) { finish() } } }
    }

    override fun onDestroy() {
        voz.cerrar()
        super.onDestroy()
    }
}

private enum class PasoD { REPOSO, ESCUCHANDO, PENSANDO, HABLANDO, PREGUNTA }

private fun PasoD.emocion() = when (this) {
    PasoD.REPOSO -> Emocion.REPOSO
    PasoD.ESCUCHANDO -> Emocion.ESCUCHANDO
    PasoD.PENSANDO -> Emocion.PENSANDO
    PasoD.HABLANDO -> Emocion.HABLANDO
    PasoD.PREGUNTA -> Emocion.PREOCUPADO
}

/** "10:6,20:no" -> observaciones simuladas (solo desarrollo): "ver:6,no:40" = 6 s visible y luego 40 s sin verse. */
private fun tramosSim(s: String): List<Pair<Boolean, Int>> =
    s.split(",").mapNotNull { p -> p.split(":").takeIf { it.size == 2 }?.let { (it[0].trim() == "ver") to (it[1].trim().toIntOrNull() ?: 0) } }

@Composable
private fun Pantalla(voz: Voz, cfg: CompaniaAtenta.Config, sim: String?, cerrar: () -> Unit) {
    val ctx = LocalContext.current
    val owner = ctx as LifecycleOwner
    val scope = rememberCoroutineScope()
    val animar = remember { !Preferencias.reducirMovimiento(ctx) }
    val compania = remember { CompaniaAtenta(cfg) }
    var dock by remember { mutableStateOf<Dock>(DockSimulado()) }
    val dockActual by rememberUpdatedState(dock)
    val angulo by remember(dock) { dock.angulo }.collectAsState()
    var usbEstado by remember { mutableIntStateOf(0) }          // 0 sin base USB, 1 buscando, 2 conectada
    var reintentoUsb by remember { mutableIntStateOf(0) }
    var audioUsb by remember { mutableStateOf<String?>(null) }

    var permisoMic by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    var permisoCam by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var aceptada by remember { mutableStateOf(Privacidad.camaraAceptada(ctx) || sim != null) }
    var camaraOn by remember { mutableStateOf(sim != null) }
    var visible by remember { mutableStateOf<Boolean?>(null) }
    var paso by remember { mutableStateOf(PasoD.REPOSO) }
    var dicho by remember { mutableStateOf("") }
    var ronda by remember { mutableIntStateOf(0) }
    var ocupado by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf("") }
    val pedirMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permisoMic = it }
    val pedirCam = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permisoCam = it }
    val canal = remember { Channel<PresenciaCamara.Observacion>(Channel.CONFLATED) }

    LaunchedEffect(Unit) { if (!permisoMic) pedirMic.launch(Manifest.permission.RECORD_AUDIO) }

    // Al entrar al modo dock, Aidly saluda (una vez). Con el telefono en la mesa el microfono queda lejos: sube el volumen hasta un minimo del 70 %
    // (nunca lo baja) y lo devuelve a como estaba al salir, si la persona no lo cambio mientras tanto.
    LaunchedEffect(Unit) {
        val n = Preferencias.nombre(ctx)
        val t = if (n.isNotBlank()) Idioma.vozTexto(ctx, R.string.v_dock_hola_nombre, n) else Idioma.vozTexto(ctx, R.string.v_dock_hola)
        dicho = t
        withTimeoutOrNull(20_000) { voz.decirYEsperar(t) }
        if (dicho == t) dicho = ""
    }
    DisposableEffect(Unit) {
        val am = ctx.getSystemService(android.media.AudioManager::class.java)
        val antes = am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)
        val minimo = (am.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC) * 0.7f).toInt()
        val subido = antes < minimo
        if (subido) am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, minimo, 0)
        onDispose {
            if (subido && am.getStreamVolume(android.media.AudioManager.STREAM_MUSIC) == minimo) am.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, antes, 0)
        }
    }

    suspend fun escuchar(): String? = try {
        if (Privacidad.nubeActiva()) GeminiStt.escuchar(ctx) else EscuchaSistema.escuchar(ctx)
    } catch (e: Exception) { SpikeLog.log("DOCK: no pude escuchar (${e::class.simpleName})"); null }

    // ---- Una conversacion: te escucho -> pienso -> hablo ----
    suspend fun turno() {
        if (ocupado || !permisoMic) return
        ocupado = true
        try {
            paso = PasoD.ESCUCHANDO; dicho = ""
            withTimeoutOrNull(20_000) { voz.decirYEsperar(Idioma.vozTexto(ctx, R.string.v_dime_te_escucho)) }
            val q = escuchar()
            if (q == null) { paso = PasoD.REPOSO; dicho = ctx.getString(R.string.hablar_error_escuchar); return }
            compania.interaccion(System.currentTimeMillis())
            paso = PasoD.PENSANDO
            val r = try { Agente.procesar(ctx, q) } catch (e: Exception) { null }
            if (r == null) { paso = PasoD.REPOSO; dicho = ctx.getString(R.string.hablar_error_responder); return }
            dicho = r.texto; paso = PasoD.HABLANDO
            withTimeoutOrNull(60_000) { voz.decirYEsperar(r.texto) }
            compania.interaccion(System.currentTimeMillis())
            r.despues?.invoke()
        } finally { paso = PasoD.REPOSO; ocupado = false }
    }

    // ---- "¿Estas bien?" cuando deja de verse despues de conversar ----
    suspend fun preguntarBienestar(repeticion: Int) {
        if (ocupado) return
        ocupado = true
        try {
            val nombre = Preferencias.nombre(ctx)
            val texto = when {
                repeticion > 1 -> Idioma.vozTexto(ctx, R.string.v_bienestar_2)
                nombre.isNotBlank() -> Idioma.vozTexto(ctx, R.string.v_bienestar_1_nombre, nombre)
                else -> Idioma.vozTexto(ctx, R.string.v_bienestar_1)
            }
            paso = PasoD.PREGUNTA; dicho = texto
            withTimeoutOrNull(30_000) { voz.decirYEsperar(texto) }
            paso = PasoD.ESCUCHANDO
            val r = escuchar()
            if (r.isNullOrBlank()) { SpikeLog.log("DOCK: sin respuesta a la pregunta $repeticion"); return }
            val n = r.lowercase()
            val mal = Lexico.de(ctx).sos.containsMatchIn(java.text.Normalizer.normalize(n, java.text.Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace(Regex("[^a-z0-9 ]"), " ").trim()) ||
                Regex("\\b(no estoy bien|no me siento bien|i am not (ok|okay|fine|well)|im not (ok|okay|fine|well)|not okay)\\b").containsMatchIn(n)
            compania.respondio(System.currentTimeMillis())
            Circulo.bienestarOk(ctx)
            if (mal) {
                SpikeLog.log("DOCK: dijo que no esta bien; se avisa a la familia como en 'ayuda'")
                val a = Agente.procesar(ctx, Idioma.vozTexto(ctx, R.string.v_frase_ayuda))
                dicho = a.texto; paso = PasoD.HABLANDO
                withTimeoutOrNull(40_000) { voz.decirYEsperar(a.texto) }
                a.despues?.invoke()
            } else {
                dicho = Idioma.vozTexto(ctx, R.string.v_que_bueno_alegra); paso = PasoD.HABLANDO
                withTimeoutOrNull(15_000) { voz.decirYEsperar(dicho) }
            }
        } finally { paso = PasoD.REPOSO; ocupado = false; compania.terminoDePreguntar(System.currentTimeMillis()) }
    }

    // ---- Camara: solo con la aceptacion de la persona y los permisos ----
    val presencia = remember { mutableStateOf<PresenciaCamara?>(null) }
    DisposableEffect(camaraOn, aceptada, permisoCam) {
        if (camaraOn && aceptada && permisoCam && sim == null) {
            try {
                val p = PresenciaCamara(ctx)
                presencia.value = p
                compania.reanudar(System.currentTimeMillis())
                p.iniciar(owner) { o -> canal.trySend(o) }
            } catch (e: Throwable) {
                SpikeLog.log("DOCK: no se pudo iniciar la camara (${e::class.simpleName}: ${e.message})"); aviso = ctx.getString(R.string.dock_camara_error)
                camaraOn = false
            }
        }
        onDispose {
            presencia.value?.close(); presencia.value = null
            compania.pausar(); dockActual.detener(); visible = null
        }
    }

    // ---- Simulacion de observaciones (solo desarrollo) ----
    LaunchedEffect(sim, camaraOn) {
        if (sim == null || !camaraOn) return@LaunchedEffect
        compania.reanudar(System.currentTimeMillis()); compania.interaccion(System.currentTimeMillis())
        var x = 0f
        for ((ver, seg) in tramosSim(sim)) {
            repeat(seg * 5) {
                x = if (ver) (kotlin.math.sin(System.currentTimeMillis() / 1500.0)).toFloat() * 0.8f else 0f
                canal.trySend(PresenciaCamara.Observacion(System.currentTimeMillis(), ver, ver, x))
                delay(200)
            }
        }
    }

    // ---- Bucle: observaciones -> maquina de estados -> dock simulado y preguntas ----
    LaunchedEffect(Unit) {
        for (o in canal) {
            visible = o.visible
            if (o.visible) Circulo.bienestarOk(ctx)       // si volvio despues de un "sin confirmar", la familia ve que ya esta
            dock.seguir(if (o.visible) o.x else null, o.ms)
            when (val a = compania.observar(o.ms, o.visible)) {
                is CompaniaAtenta.Accion.Preguntar -> scope.launch { preguntarBienestar(a.repeticion) }
                CompaniaAtenta.Accion.SinRespuesta -> {
                    SpikeLog.log("DOCK: nadie respondio a las preguntas"); aviso = ctx.getString(R.string.dock_sin_respuesta)
                    Circulo.bienestarSinConfirmar(ctx)
                }
                CompaniaAtenta.Accion.Nada -> {}
            }
        }
    }

    LaunchedEffect(ronda) { if (ronda > 0) turno() }

    // ---- Base USB: se busca al abrir (y con el boton); si no hay, queda la simulada. Al salir se manda STOP y se cierra ----
    LaunchedEffect(reintentoUsb) {
        usbEstado = 1
        DockUsb.conectar(ctx, alEvento = { e -> if (e == "BTN") ronda++ }) { d ->
            if (d != null) { (dock as? DockConectado)?.cerrar(); dock = d; usbEstado = 2 } else usbEstado = 0
        }
    }
    DisposableEffect(Unit) { onDispose { (dockActual as? DockConectado)?.cerrar() } }
    LaunchedEffect(usbEstado, camaraOn) { audioUsb = AudioExterno.nombre(ctx) }

    // La cara ocupa un tercio de la altura (entre 120 y 320 dp) y todo se desplaza si no cabe (letra grande o pantalla baja).
    val caraAlto = (androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp * 0.32f).coerceIn(120f, 320f).dp
    Column(
        Modifier.fillMaxSize().background(Tema.Fondo).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(
                Modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).clickable(role = Role.Button) { voz.callar(); cerrar() }.padding(end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconoAidly(Iconos.ChevronIzquierda, Tema.Texto, 30.dp)
                Text(stringResource(R.string.volver), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Tema.Texto)
            }
            EstadoCamara(camaraOn, visible)
        }

        Box(Modifier.fillMaxWidth().height(caraAlto), contentAlignment = Alignment.Center) {
            AidlyCara(paso.emocion(), Modifier.fillMaxHeight().aspectRatio(240f / 200f, matchHeightConstraintsFirst = true).widthIn(max = 420.dp).clickable(role = Role.Button) { if (!ocupado) ronda++ }.semantics { contentDescription = ctx.getString(R.string.dock_tocar_hablar) }, animar)
        }
        Text(
            dicho.ifBlank { aviso.ifBlank { stringResource(R.string.dock_reposo) } },
            fontSize = 22.sp, color = Tema.Texto, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp).semantics { liveRegion = LiveRegionMode.Polite },
        )

        if (!aceptada) {
            // La camara solo se enciende si la persona lo acepta aqui, ella misma.
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Tema.RadioFila)).background(Tema.Tarjeta).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.dock_acepta_t), fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Tema.Turquesa)
                Text(stringResource(R.string.dock_acepta), fontSize = 18.sp, color = Tema.Texto)
                BotonGrande(stringResource(R.string.dock_acepta_si), Tema.Turquesa, alto = 64.dp, tamLetra = 20) {
                    Privacidad.fijarCamaraAceptada(ctx, true); aceptada = true
                    if (!permisoCam) pedirCam.launch(Manifest.permission.CAMERA)
                    camaraOn = true
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            DialDock(angulo, Modifier.size(78.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (usbEstado == 2) stringResource(R.string.dock_usb_conectada, angulo.toInt()) else stringResource(R.string.dock_simulado, angulo.toInt()),
                    fontSize = 18.sp, color = Tema.TextoSuave,
                )
                Text(
                    when (usbEstado) { 2 -> audioUsb?.let { stringResource(R.string.dock_audio_usb, it) } ?: stringResource(R.string.dock_audio_tel); 1 -> stringResource(R.string.dock_usb_buscando); else -> stringResource(R.string.dock_simulado_nota) },
                    fontSize = 18.sp, color = Tema.TextoSuave,
                )
                if (usbEstado == 0) Text(stringResource(R.string.dock_usb_buscar), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Tema.Turquesa,
                    modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { reintentoUsb++ }.padding(vertical = 12.dp))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.weight(1f)) { BotonGrande(stringResource(R.string.dock_hablar), Tema.Turquesa, alto = 68.dp, tamLetra = 22) { if (!ocupado) ronda++ } }
            if (aceptada) Box(Modifier.weight(1f)) {
                BotonGrande(
                    stringResource(if (camaraOn) R.string.dock_pausar else R.string.dock_reanudar),
                    if (camaraOn) Tema.Amarillo else Tema.Tarjeta, if (camaraOn) Tema.Oscuro else Tema.Texto, alto = 68.dp, tamLetra = 20,
                ) {
                    if (camaraOn) camaraOn = false
                    else { if (!permisoCam) pedirCam.launch(Manifest.permission.CAMERA); camaraOn = true }
                }
            }
        }
    }
}

@Composable
private fun EstadoCamara(on: Boolean, visible: Boolean?) {
    val color = if (on) Tema.Turquesa else Tema.TextoSuave
    Row(
        Modifier.border(2.dp, color, RoundedCornerShape(22.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(
            stringResource(if (!on) R.string.dock_camara_off else if (visible == true) R.string.dock_camara_te_veo else R.string.dock_camara_on),
            fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color,
        )
    }
}

/** Flecha que apunta hacia donde "miraria" el dock (simulado). */
@Composable
private fun DialDock(grados: Float, modifier: Modifier) {
    val color = Tema.Turquesa
    val suave = Tema.TextoSuave
    Canvas(modifier) {
        val c = Offset(size.width / 2, size.height / 2)
        drawCircle(suave, radius = size.minDimension / 2 - 2f, center = c, style = Stroke(width = 3f))
        val p = Path().apply {
            moveTo(c.x, c.y - size.minDimension / 2 + 8f)
            lineTo(c.x - 10f, c.y + 6f); lineTo(c.x + 10f, c.y + 6f); close()
        }
        rotarDibujo(grados, c) { drawPath(p, color) }
    }
}
