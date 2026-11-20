package com.aidly.app

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull

/** Pantalla de aviso: se abre sola (tambien con el telefono bloqueado), habla y pide confirmar. */
class RecordatorioActivity : ActividadAidly() {
    override val protegida get() = true

    private lateinit var voz: Voz

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) { setShowWhenLocked(true); setTurnScreenOn(true) }
        else window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        voz = Voz(this)
        val regId = intent.getStringExtra("reg").orEmpty()
        setContent { AidlyTema { Pantalla(voz, regId) { finish() } } }
    }

    override fun onDestroy() {
        voz.cerrar()
        super.onDestroy()
    }
}

@Composable
private fun Pantalla(voz: Voz, regId: String, cerrar: () -> Unit) {
    val ctx = LocalContext.current
    val reg = remember { Recordatorios.registro(ctx, regId) }
    var estado by remember { mutableStateOf(reg?.estado) }

    // Habla y repite cada 25 s (hasta 6 veces) mientras nadie conteste.
    LaunchedEffect(Unit) {
        val r = reg ?: return@LaunchedEffect
        val frase = when (r.tipo) {
            Recordatorios.Tipo.MEDICINA -> Idioma.vozTexto(ctx, R.string.v_hora_medicina, r.texto)
            Recordatorios.Tipo.CHECKIN -> Idioma.vozTexto(ctx, R.string.v_checkin)
            else -> Idioma.vozTexto(ctx, R.string.v_recordatorio, r.texto)
        }
        val rv = Idioma.inicial(ctx).getJSONObject("recordatorios")
        repeat(rv.optInt("repeticiones_de_voz", 6)) {
            if (estado != Recordatorios.Estado.PENDIENTE) return@LaunchedEffect
            withTimeoutOrNull(20_000) { voz.decirYEsperar(frase) }
            delay(rv.optLong("intervalo_de_voz_seg", 25) * 1000)
        }
    }

    Column(
        Modifier.fillMaxSize().background(Tema.Fondo).padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        AidlyCara(
            when (estado) {
                Recordatorios.Estado.PENDIENTE -> Emocion.HABLANDO
                Recordatorios.Estado.AYUDA -> Emocion.PREOCUPADO
                else -> Emocion.CONTENTO
            },
            Modifier.fillMaxWidth(0.7f), remember { !Preferencias.reducirMovimiento(ctx) },
        )
        if (reg == null) {
            Text(stringResource(R.string.rec_no_disponible), fontSize = 26.sp, color = Tema.Texto, textAlign = TextAlign.Center)
            BotonGrande(stringResource(R.string.cerrar_mayus), Tema.Turquesa) { cerrar() }
            return@Column
        }
        val checkin = reg.tipo == Recordatorios.Tipo.CHECKIN
        Text(
            when (reg.tipo) {
                Recordatorios.Tipo.MEDICINA -> stringResource(R.string.rec_titulo_medicina)
                Recordatorios.Tipo.CHECKIN -> stringResource(R.string.rec_titulo_checkin)
                else -> stringResource(R.string.rec_titulo_general)
            },
            fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Tema.Amarillo, textAlign = TextAlign.Center,
        )
        Text(
            if (checkin) stringResource(R.string.rec_checkin_pregunta) else reg.texto.replaceFirstChar { it.uppercase() },
            fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Tema.Texto, textAlign = TextAlign.Center,
        )
        when {
            checkin && estado == Recordatorios.Estado.PENDIENTE -> {
                BotonGrande(stringResource(R.string.rec_estoy_bien), Tema.Turquesa) {
                    voz.callar()
                    Recordatorios.marcar(ctx, regId, Recordatorios.Estado.TOMADO)
                    estado = Recordatorios.Estado.TOMADO
                    voz.decir(Idioma.vozTexto(ctx, R.string.v_que_bueno_dia)) { cerrar() }
                }
                BotonGrande(stringResource(R.string.rec_no_bien), Tema.Coral) {
                    voz.callar()
                    Recordatorios.marcar(ctx, regId, Recordatorios.Estado.AYUDA)
                    estado = Recordatorios.Estado.AYUDA
                    voz.decir(Idioma.vozTexto(ctx, R.string.v_voy_avisar, Emergencia.habla(ctx)))
                }
            }
            estado == Recordatorios.Estado.PENDIENTE -> {
                BotonGrande(stringResource(R.string.rec_ya_hice), Tema.Turquesa) {
                    voz.callar()
                    Recordatorios.marcar(ctx, regId, Recordatorios.Estado.TOMADO)
                    estado = Recordatorios.Estado.TOMADO
                    voz.decir(Idioma.vozTexto(ctx, R.string.v_anotado)) { cerrar() }
                }
                BotonGrande(stringResource(R.string.rec_en_10), Tema.Amarillo) {
                    voz.callar()
                    Recordatorios.posponer(ctx, regId)
                    estado = Recordatorios.Estado.POSPUESTO
                    voz.decir(Idioma.vozTexto(ctx, R.string.v_diez_min)) { cerrar() }
                }
            }
            else -> {
                Text(
                    when (estado) {
                        Recordatorios.Estado.TOMADO -> stringResource(R.string.rec_est_tomado)
                        Recordatorios.Estado.POSPUESTO -> stringResource(R.string.rec_est_pospuesto)
                        Recordatorios.Estado.AYUDA -> stringResource(R.string.rec_est_ayuda, Emergencia.numero(ctx))
                        else -> stringResource(R.string.rec_est_paso)
                    },
                    fontSize = 28.sp, color = Tema.Turquesa, textAlign = TextAlign.Center,
                )
                BotonGrande(stringResource(R.string.cerrar_mayus), Tema.Turquesa) { voz.callar(); cerrar() }
            }
        }
    }
}
