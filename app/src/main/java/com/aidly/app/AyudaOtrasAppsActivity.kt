package com.aidly.app

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * "Ayuda con otras apps": aviso claro de que hace y que no hace el servicio de accesibilidad, estado, boton para encenderlo en Ajustes y el
 * interruptor del boton flotante. Es opcional y esta apagada hasta que la persona la enciende.
 */
class AyudaOtrasAppsActivity : ActividadAidly() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AidlyTema { Pantalla { finish() } } }
    }
}

@Composable
private fun Pantalla(cerrar: () -> Unit) {
    val ctx = LocalContext.current
    var activa by remember { mutableStateOf(Accesibilidad.activa(ctx)) }
    var boton by remember { mutableStateOf(Accesibilidad.botonActivo(ctx)) }
    LifecycleResumeEffect(Unit) { activa = Accesibilidad.activa(ctx); boton = Accesibilidad.botonActivo(ctx); onPauseOrDispose {} }

    Column(
        Modifier.fillMaxSize().background(Tema.Fondo).padding(horizontal = 16.dp, vertical = 14.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EncabezadoPantalla(stringResource(R.string.a11y_titulo), cerrar)
        Text(stringResource(R.string.a11y_intro), fontSize = 22.sp, color = Tema.Texto)

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Tema.RadioFila)).background(Tema.Tarjeta).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.a11y_hace_t), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tema.Turquesa)
            Text(stringResource(R.string.a11y_hace), fontSize = 20.sp, color = Tema.Texto)
            Text(stringResource(R.string.a11y_no_hace_t), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tema.Amarillo)
            Text(stringResource(R.string.a11y_no_hace), fontSize = 20.sp, color = Tema.Texto)
        }

        FilaAccion(
            if (activa) Iconos.Check else Iconos.Alerta, stringResource(R.string.a11y_estado),
            stringResource(if (activa) R.string.a11y_encendida else R.string.a11y_apagada), amarilla = !activa, chevron = !activa,
        ) { if (!activa) Accesibilidad.abrirAjustes(ctx) }
        if (!activa) Text(stringResource(R.string.a11y_como), fontSize = 20.sp, color = Tema.TextoSuave)

        InterruptorFila(Iconos.Estrella, stringResource(R.string.a11y_boton), stringResource(R.string.a11y_boton_sub), boton) { boton = it; Accesibilidad.fijarBoton(ctx, it) }

        TituloSeccion(stringResource(R.string.a11y_ejemplos_t))
        Text(stringResource(R.string.a11y_ejemplos), fontSize = 20.sp, color = Tema.Texto)
    }
}
