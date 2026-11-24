package com.aidly.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** "Pedir ayuda": confirma antes de avisar (para evitar toques sin querer), avisa a la familia y ofrece el 911. */
class SosActivity : ActividadAidly() {
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

private enum class FaseSos { PREGUNTA, AVISADO }

@Composable
private fun Pantalla(voz: Voz, cerrar: () -> Unit) {
    val ctx = LocalContext.current
    var fase by remember { mutableStateOf(FaseSos.PREGUNTA) }
    val hayFamilia = remember { Circulo.ajustes(ctx).activo }

    LaunchedEffect(Unit) { voz.decir(Idioma.vozTexto(ctx, R.string.v_sos_intro)) }

    val confianza = remember { Preferencias.contactoConfianza(ctx) }
    fun marcador(numero: String = Emergencia.numero(ctx)) {
        try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", numero, null)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        catch (e: Exception) { SpikeLog.log("SOS: no pude abrir el marcador: ${e.message}") }
    }

    Column(
        Modifier.fillMaxSize().background(Tema.Fondo).padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AidlyCara(
            if (fase == FaseSos.PREGUNTA) Emocion.PREOCUPADO else Emocion.CONTENTO,
            Modifier.fillMaxWidth(0.6f), remember { !Preferencias.reducirMovimiento(ctx) }, voz.hablando.collectAsState().value,
        )
        when (fase) {
            FaseSos.PREGUNTA -> {
                Text(stringResource(R.string.sos_pregunta), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Tema.Texto, textAlign = TextAlign.Center)
                if (!hayFamilia) {
                    Text(
                        stringResource(R.string.sos_sin_familia, Emergencia.numero(ctx)),
                        fontSize = 22.sp, color = Tema.Amarillo, textAlign = TextAlign.Center,
                    )
                } else {
                    BotonGrande(stringResource(R.string.sos_si_avisar), Tema.Coral, tamLetra = 24) {
                        voz.callar()
                        Circulo.sosAsync(ctx)
                        fase = FaseSos.AVISADO
                        voz.decir(Idioma.vozTexto(ctx, R.string.v_sos_avise, Emergencia.habla(ctx)))
                    }
                }
                confianza?.let { c -> BotonGrande(stringResource(R.string.sos_llamar_confianza, c.nombre), Tema.Turquesa) { voz.callar(); marcador(c.numero) } }
                BotonGrande(stringResource(R.string.sos_llamar, Emergencia.numero(ctx)), Tema.Amarillo) { voz.callar(); marcador() }
                BotonGrande(stringResource(R.string.sos_no_volver), Tema.TextoSuave, alto = 72.dp) { voz.callar(); cerrar() }
            }
            FaseSos.AVISADO -> {
                Text(stringResource(R.string.sos_avisado), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Tema.Turquesa, textAlign = TextAlign.Center)
                Text(stringResource(R.string.sos_avisado_sub, Emergencia.numero(ctx)), fontSize = 22.sp, color = Tema.Texto, textAlign = TextAlign.Center)
                confianza?.let { c -> BotonGrande(stringResource(R.string.sos_llamar_confianza, c.nombre), Tema.Turquesa) { marcador(c.numero) } }
                BotonGrande(stringResource(R.string.sos_llamar, Emergencia.numero(ctx)), Tema.Amarillo) { marcador() }
                BotonGrande(stringResource(R.string.sos_ya_bien), Tema.Turquesa) {
                    voz.callar(); Circulo.limpiarSos(ctx); voz.decir(Idioma.vozTexto(ctx, R.string.v_que_bueno_alegra)) { cerrar() }
                }
            }
        }
    }
}
