package com.aidly.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Ventana de voz sobre otra app (desde el boton flotante): translucida, escucha UNA orden, la ejecuta y se cierra. La app de abajo se sigue viendo
 * y, al cerrarse esta ventana, vuelve a ser la activa: asi "toca Buscar" o "atras" actuan sobre ella y no sobre Aidly.
 */
class OrdenActivity : ActividadAidly() {
    override val protegida get() = true
    private lateinit var voz: Voz

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voz = Voz(this)
        setContent { AidlyTema(transparente = true) { Pantalla(voz) { despues -> terminar(despues) } } }
    }

    /** Cierra la ventana y, un instante despues (cuando la otra app ya es la activa), ejecuta la accion. */
    private fun terminar(despues: (() -> Unit)?) {
        voz.callar()
        finish()
        if (despues != null) Handler(Looper.getMainLooper()).postDelayed({ try { despues() } catch (e: Exception) { SpikeLog.log("ORDEN: la accion fallo (${e::class.simpleName})") } }, 450)
    }

    override fun onDestroy() { voz.cerrar(); super.onDestroy() }
}

@Composable
private fun Pantalla(voz: Voz, terminar: ((() -> Unit)?) -> Unit) {
    val ctx = LocalContext.current
    var dicho by remember { mutableStateOf("") }
    var respuesta by remember { mutableStateOf("") }
    val habla by voz.hablando.collectAsState()
    var permiso by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permiso = it }
    LaunchedEffect(Unit) { if (!permiso) pedir.launch(Manifest.permission.RECORD_AUDIO) }

    LaunchedEffect(permiso) {
        if (!permiso) return@LaunchedEffect
        var despues: (() -> Unit)? = null
        try {
            val q = if (Privacidad.nubeActiva()) GeminiStt.escuchar(ctx) else EscuchaSistema.escuchar(ctx)
            if (q == null) respuesta = ctx.getString(R.string.hablar_error_escuchar)
            else {
                dicho = q
                val r = Agente.procesar(ctx, q)
                respuesta = r.texto; despues = r.despues
                withTimeoutOrNull(30_000) { voz.decirYEsperar(r.texto) }
            }
        } catch (e: Exception) { SpikeLog.log("ORDEN: error (${e::class.simpleName})"); respuesta = ctx.getString(R.string.hablar_error_responder) }
        if (despues == null) withTimeoutOrNull(2_500) { kotlinx.coroutines.delay(2_500) }
        terminar(despues)
    }

    Box(Modifier.fillMaxSize().clickable { terminar(null) }, contentAlignment = Alignment.BottomCenter) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp).clip(RoundedCornerShape(Tema.RadioPrincipal)).background(Tema.Fondo.copy(alpha = 0.96f)).padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AidlyCara(
                if (habla) Emocion.HABLANDO else if (respuesta.isBlank()) Emocion.ESCUCHANDO else Emocion.CONTENTO,
                Modifier.fillMaxWidth(0.28f), remember { !Preferencias.reducirMovimiento(ctx) }, habla,
            )
            Text(
                if (respuesta.isNotBlank()) respuesta else if (dicho.isNotBlank()) dicho else stringResource(R.string.pt_te_escucho),
                fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White, textAlign = TextAlign.Center,
            )
        }
    }
}
