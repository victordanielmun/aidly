package com.aidly.app

import android.Manifest
import android.app.TimePickerDialog
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch
import java.util.Calendar

/** "Mi familia": crear el codigo, elegir que se comparte y dejar de compartir. Pensada para hacerla con un familiar. */
class CirculoActivity : ActividadAidly() {
    override val protegida get() = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AidlyTema { Pantalla { finish() } } }
    }
}

@Composable
private fun Pantalla(cerrar: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var a by remember { mutableStateOf(Circulo.ajustes(ctx)) }
    var nombre by remember { mutableStateOf(Preferencias.nombre(ctx)) }
    var trabajando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var confirmarBorrar by remember { mutableStateOf(false) }
    fun recargar() { a = Circulo.ajustes(ctx) }

    LifecycleResumeEffect(Unit) { recargar(); onPauseOrDispose {} }

    val pedirUbicacion = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val ok = r.values.any { it }
        Circulo.editar(ctx) { putBoolean("ubicacion", ok) }
        if (!ok) error = ctx.getString(R.string.circ_sin_ubic)
        Circulo.sincronizarAsync(ctx); recargar()
    }

    val colores = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Tema.Texto, unfocusedTextColor = Tema.Texto,
        focusedBorderColor = Tema.Turquesa, unfocusedBorderColor = Tema.TextoSuave,
        focusedLabelColor = Tema.Turquesa, unfocusedLabelColor = Tema.TextoSuave, cursorColor = Tema.Turquesa,
    )

    Column(
        Modifier.fillMaxSize().background(Tema.Fondo).padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Mascota(110.dp)
        Text(stringResource(R.string.home_familia), fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Tema.Texto)

        if (!a.activo) {
            Text(
                stringResource(R.string.circ_intro),
                fontSize = 22.sp, color = Tema.TextoSuave, textAlign = TextAlign.Center,
            )
            OutlinedTextField(
                value = nombre, onValueChange = { nombre = it.take(30) }, singleLine = true,
                label = { Text(stringResource(R.string.circ_nombre), fontSize = 18.sp) },
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 26.sp),
                colors = colores, modifier = Modifier.fillMaxWidth(),
            )
            if (trabajando) CircularProgressIndicator(color = Tema.Amarillo)
            BotonGrande(stringResource(R.string.circ_crear), Tema.Turquesa) {
                if (nombre.isBlank()) { error = ctx.getString(R.string.circ_err_nombre); return@BotonGrande }
                error = ""; trabajando = true
                scope.launch {
                    try { Circulo.activar(ctx, nombre) } catch (e: Exception) {
                        error = ctx.getString(R.string.circ_err_red)
                    }
                    trabajando = false; recargar()
                }
            }
        } else {
            Text(stringResource(R.string.circ_tu_codigo), fontSize = 22.sp, color = Tema.TextoSuave)
            Text(
                a.codigoBonito, fontSize = 40.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                color = Tema.Amarillo, textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.circ_entra, Circulo.URL.removePrefix("https://")),
                fontSize = 20.sp, color = Tema.TextoSuave, textAlign = TextAlign.Center,
            )
            BotonGrande(stringResource(R.string.circ_enviar), Tema.Turquesa, tamLetra = 22) {
                val texto = ctx.getString(R.string.circ_mensaje, "${Circulo.URL}/#${a.codigo}", Circulo.URL, a.codigoBonito)
                ctx.startActivity(
                    Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, texto), ctx.getString(R.string.circ_enviar_con)),
                )
            }

            Text(stringResource(R.string.circ_que_comparto), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Tema.Turquesa)
            Interruptor(stringResource(R.string.circ_actividad), a.actividad) {
                Circulo.editar(ctx) { putBoolean("actividad", it) }; Circulo.sincronizarAsync(ctx); recargar()
            }
            Interruptor(stringResource(R.string.circ_medicinas), a.medicinas) {
                Circulo.editar(ctx) { putBoolean("medicinas", it) }; Circulo.sincronizarAsync(ctx); recargar()
            }
            Interruptor(stringResource(R.string.circ_ubicacion), a.ubicacion) { on ->
                if (on && !Circulo.tieneUbicacion(ctx)) {
                    pedirUbicacion.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                } else {
                    Circulo.editar(ctx) { putBoolean("ubicacion", on) }; Circulo.sincronizarAsync(ctx); recargar()
                }
            }
            Interruptor(stringResource(R.string.circ_checkin), a.checkin) {
                Circulo.editar(ctx) { putBoolean("checkin", it) }; Circulo.programarCheckin(ctx); Circulo.sincronizarAsync(ctx); recargar()
            }
            if (a.checkin) {
                val h = a.horaMin / 60; val m = a.horaMin % 60
                BotonGrande(stringResource(R.string.circ_hora, "%02d:%02d".format(h, m)), Tema.Amarillo, alto = 72.dp, tamLetra = 22) {
                    TimePickerDialog(ctx, { _, hh, mm ->
                        Circulo.editar(ctx) { putInt("horaMin", hh * 60 + mm) }
                        Circulo.programarCheckin(ctx); recargar()
                    }, h, m, true).show()
                }
            }
            Text(
                stringResource(R.string.circ_nota),
                fontSize = 17.sp, color = Tema.TextoSuave,
            )
            if (Circulo.ultimaSync(ctx) > 0) {
                val min = (System.currentTimeMillis() - Circulo.ultimaSync(ctx)) / 60_000
                Text(stringResource(R.string.circ_ultimo_envio, if (min < 1) stringResource(R.string.circ_menos_min) else stringResource(R.string.circ_min, min.toInt())), fontSize = 15.sp, color = Tema.TextoSuave)
            }
            if (trabajando) CircularProgressIndicator(color = Tema.Amarillo)
            BotonGrande(
                stringResource(if (confirmarBorrar) R.string.circ_confirmar else R.string.circ_dejar),
                Tema.Coral, alto = 72.dp, tamLetra = 20,
            ) {
                if (!confirmarBorrar) { confirmarBorrar = true; return@BotonGrande }
                trabajando = true
                scope.launch { Circulo.desactivar(ctx); trabajando = false; confirmarBorrar = false; recargar() }
            }
        }
        if (error.isNotBlank()) Text(error, fontSize = 20.sp, color = Tema.Coral, textAlign = TextAlign.Center)
        BotonGrande(stringResource(R.string.volver_mayus), Tema.TextoSuave, alto = 72.dp) { cerrar() }
    }
}

@Composable
private fun Interruptor(texto: String, activo: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Tema.Tarjeta).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(texto, fontSize = 22.sp, color = Tema.Texto, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = activo, onCheckedChange = alCambiar, modifier = Modifier.widthIn(min = 64.dp),
            colors = SwitchDefaults.colors(checkedTrackColor = Tema.Turquesa, checkedThumbColor = Tema.Oscuro),
        )
    }
}
