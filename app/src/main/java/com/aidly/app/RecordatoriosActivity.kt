package com.aidly.app

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** "Mis recordatorios": lo que viene, con boton para borrar, y como van las ultimas tomas. */
class RecordatoriosActivity : ActividadAidly() {
    override val protegida get() = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AidlyTema { Lista { finish() } } }
    }
}

@Composable
private fun Lista(cerrar: () -> Unit) {
    val ctx = LocalContext.current
    var items by remember { mutableStateOf(Recordatorios.listar(ctx)) }
    var historial by remember { mutableStateOf(Recordatorios.historial(ctx).filter { it.estado != Recordatorios.Estado.PENDIENTE }.take(8)) }
    LifecycleResumeEffect(Unit) {
        items = Recordatorios.listar(ctx)
        historial = Recordatorios.historial(ctx).filter { it.estado != Recordatorios.Estado.PENDIENTE }.take(8)
        onPauseOrDispose {}
    }
    val formato = Idioma.config(ctx).getJSONObject("formato")
    val horaFmt = SimpleDateFormat(formato.getString("fecha_corta") + ", " + formato.getString("hora_pantalla"), Idioma.locale(ctx))

    Column(
        Modifier.fillMaxSize().background(Tema.Fondo).padding(20.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Mascota(110.dp)
        Text(stringResource(R.string.recs_titulo), fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Tema.Texto)
        if (items.isEmpty()) {
            Text(stringResource(R.string.recs_vacio), fontSize = 22.sp, color = Tema.TextoSuave)
        }
        items.forEach { r ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Tema.Tarjeta).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(r.texto.replaceFirstChar { it.uppercase() }, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Tema.Texto)
                Text(Recordatorios.describir(r), fontSize = 22.sp, color = Tema.Turquesa)
                BotonGrande(stringResource(R.string.recs_borrar), Tema.Coral, alto = 64.dp, tamLetra = 20) {
                    Recordatorios.cancelar(ctx, r.id)
                    items = Recordatorios.listar(ctx)
                }
            }
        }
        if (historial.isNotEmpty()) {
            Text(stringResource(R.string.recs_ultimos), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Tema.Amarillo)
            historial.forEach { h ->
                val marca = when (h.estado) {
                    Recordatorios.Estado.TOMADO -> stringResource(R.string.recs_hecho)
                    Recordatorios.Estado.OMITIDO -> stringResource(R.string.recs_omitido)
                    Recordatorios.Estado.AYUDA -> stringResource(R.string.recs_ayuda)
                    else -> stringResource(R.string.recs_pospuesto)
                }
                Text("$marca — ${h.texto} (${horaFmt.format(Date(h.programada))})", fontSize = 18.sp, color = Tema.TextoSuave)
            }
        }
        BotonGrande(stringResource(R.string.volver_mayus), Tema.Turquesa, alto = 72.dp) { cerrar() }
    }
}
