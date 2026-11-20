package com.aidly.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.withTimeoutOrNull

/** "Hablar con Aidly": la cara muestra en todo momento que esta haciendo (escucha, piensa, habla). */
class HablarActivity : ActividadAidly() {
    override val protegida get() = true

    private lateinit var voz: Voz

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voz = Voz(this)
        Circulo.tocar(this)
        setContent { AidlyTema { HablarScreen(voz) { finish() } } }
    }

    override fun onDestroy() {
        voz.cerrar()
        super.onDestroy()
    }
}

private enum class Paso { REPOSO, ESCUCHANDO, PENSANDO, HABLANDO, LISTO, ERROR }

private fun Paso.emocion() = when (this) {
    Paso.REPOSO -> Emocion.REPOSO
    Paso.ESCUCHANDO -> Emocion.ESCUCHANDO
    Paso.PENSANDO -> Emocion.PENSANDO
    Paso.HABLANDO -> Emocion.HABLANDO
    Paso.LISTO -> Emocion.CONTENTO
    Paso.ERROR -> Emocion.PREOCUPADO
}

@Composable
private fun HablarScreen(voz: Voz, cerrar: () -> Unit) {
    val ctx = LocalContext.current
    var permiso by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permiso = it }
    var paso by remember { mutableStateOf(Paso.REPOSO) }
    var pregunta by remember { mutableStateOf("") }
    var respuesta by remember { mutableStateOf("") }
    var motorFuente by remember { mutableStateOf("") }
    var motorMs by remember { mutableStateOf(0L) }
    var ronda by remember { mutableIntStateOf(0) }
    val animar = remember { !Preferencias.reducirMovimiento(ctx) }

    LaunchedEffect(Unit) { if (!permiso) pedir.launch(Manifest.permission.RECORD_AUDIO) }

    LaunchedEffect(permiso, ronda) {
        if (!permiso) return@LaunchedEffect
        paso = Paso.ESCUCHANDO; pregunta = ""; respuesta = ""; motorFuente = ""; motorMs = 0L
        withTimeoutOrNull(20_000) { voz.decirYEsperar("Dime, te escucho.") }

        val q = try { if (Privacidad.nubeActiva()) GeminiStt.escuchar(ctx) else EscuchaSistema.escuchar(ctx) } catch (e: Exception) {
            SpikeLog.log("HABLAR: fallo al escuchar (${e::class.simpleName}: ${e.message})"); null
        }
        if (q == null) {
            respuesta = ctx.getString(R.string.hablar_error_escuchar)
            paso = Paso.ERROR
            voz.decir(Idioma.vozTexto(ctx, R.string.hablar_error_escuchar))
            return@LaunchedEffect
        }
        pregunta = q; paso = Paso.PENSANDO

        val r = try { Agente.procesar(ctx, q) } catch (e: Exception) {
            SpikeLog.log("HABLAR: fallo al responder (${e::class.simpleName}: ${e.message})"); null
        }
        if (r == null) {
            respuesta = ctx.getString(R.string.hablar_error_responder)
            paso = Paso.ERROR
            voz.decir(Idioma.vozTexto(ctx, R.string.hablar_error_responder))
            return@LaunchedEffect
        }
        respuesta = r.texto
        motorFuente = r.fuente; motorMs = r.ms
        paso = Paso.HABLANDO
        withTimeoutOrNull(60_000) { voz.decirYEsperar(r.texto) }
        paso = Paso.LISTO
        r.despues?.invoke()   // p. ej. abrir Spotify/YouTube, una vez que Aidly termino de hablar
    }

    val emocion = paso.emocion()
    val ayuda = when (paso) {
        Paso.REPOSO -> stringResource(if (permiso) R.string.hablar_reposo else R.string.hablar_sin_mic)
        Paso.ESCUCHANDO -> stringResource(R.string.hablar_escuchando_ayuda)
        Paso.PENSANDO -> stringResource(R.string.hablar_pensando_ayuda)
        else -> ""
    }

    Column(Modifier.fillMaxSize().background(Tema.Fondo).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.fillMaxWidth()) { EncabezadoSoloVolver { voz.callar(); cerrar() } }
            AidlyCara(emocion, Modifier.widthIn(max = 300.dp).fillMaxWidth(), animar)
            Text(
                stringResource(emocion.etiqueta), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Tema.Turquesa, textAlign = TextAlign.Center,
            )
            if (pregunta.isNotBlank()) Text(stringResource(R.string.hablar_tu, pregunta), fontSize = 18.sp, color = Tema.TextoSuave, textAlign = TextAlign.Center)
            // Subtitulo grande: lo que dice Aidly (lo anuncian los lectores de pantalla).
            Box(
                Modifier.fillMaxWidth().heightIn(min = 76.dp).clip(RoundedCornerShape(Tema.RadioFila)).background(Tema.Tarjeta)
                    .semantics { liveRegion = LiveRegionMode.Polite }.padding(horizontal = 18.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) { Text(respuesta.ifBlank { ayuda }, fontSize = 22.sp, color = Tema.Texto) }
            if (motorFuente.isNotBlank()) InsigniaMotor(motorFuente, motorMs)
        }
        if (paso == Paso.LISTO || paso == Paso.ERROR || (paso == Paso.REPOSO && permiso)) {
            EspacioV(12.dp)
            BotonGrande(stringResource(R.string.hablar_otra_vez), Tema.Turquesa, alto = 68.dp) { voz.callar(); ronda++ }
        }
    }
}

/** Solo la fila "‹ Volver" (el titulo lo da la cara). */
@Composable
private fun EncabezadoSoloVolver(volver: () -> Unit) {
    Row(
        Modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp))
            .clickable(role = Role.Button, onClick = volver).padding(end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconoAidly(Iconos.ChevronIzquierda, Tema.Texto, 30.dp)
        Text(stringResource(R.string.volver), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Tema.Texto)
    }
}
