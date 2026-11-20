package com.aidly.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Calendar

/**
 * Detalle de un recordatorio (diseno del 19-nov, validado en docs/VALIDACION-CALENDARIO.md): hora con botones grandes,
 * "cada cuanto", cambiar el texto con la voz, guardar y borrar (con confirmacion).
 * Lo que el diseno propone y la app aun NO hace: "entre semana" y "avisar a la familia por este aviso" (ver el documento).
 */
class RecordatorioDetalleActivity : ActividadAidly() {
    override val protegida get() = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getStringExtra(EXTRA_ID).orEmpty()
        val item = Recordatorios.obtener(this, id)
        if (item == null) { finish(); return }
        setContent { AidlyTema { Pantalla(item) { finish() } } }
    }

    companion object { const val EXTRA_ID = "id" }
}

@Composable
private fun Pantalla(item: Recordatorios.Item, cerrar: () -> Unit) {
    val ctx = LocalContext.current
    val inicial = remember { Calendar.getInstance().apply { timeInMillis = item.proxima } }
    var h by remember { mutableIntStateOf(inicial.get(Calendar.HOUR_OF_DAY)) }
    var m by remember { mutableIntStateOf(inicial.get(Calendar.MINUTE)) }
    var cada by remember { mutableStateOf(item.cadaMin) }
    var confirmaBorrar by remember { mutableStateOf(false) }
    val opciones = remember { buildList { add(0L); add(1440L); if (item.cadaMin != 0L && item.cadaMin != 1440L) add(item.cadaMin) } }

    Column(
        Modifier.fillMaxSize().background(Tema.Fondo).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).clickable(role = Role.Button) { cerrar() }.padding(end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconoAidly(Iconos.ChevronIzquierda, Tema.Texto, 30.dp)
            Text(stringResource(R.string.volver), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Tema.Texto)
        }

        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Tema.Tarjeta).padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.det_hora), fontSize = 20.sp, color = Tema.TextoSuave)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Paso(h, stringResource(R.string.det_subir_hora), stringResource(R.string.det_bajar_hora), { h = (h + 1) % 24 }, { h = (h + 23) % 24 })
                Text(":", fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Tema.Texto)
                Paso(m, stringResource(R.string.det_subir_min), stringResource(R.string.det_bajar_min), { m = ((m / 5) * 5 + 5) % 60 }, { m = if (m % 5 == 0) (m + 55) % 60 else (m / 5) * 5 })
            }
            val c = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m) }
            Text(Recordatorios.horaHablada(c), fontSize = 18.sp, color = Tema.TextoSuave)
        }

        Row(
            Modifier.fillMaxWidth().heightIn(min = 84.dp).clip(RoundedCornerShape(24.dp)).background(Tema.Tarjeta).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.det_que), fontSize = 17.sp, color = Tema.TextoSuave)
                Text(item.texto.replaceFirstChar { it.uppercase() }, fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Tema.Texto)
            }
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(Tema.Turquesa)
                    .clickable(role = Role.Button, onClickLabel = stringResource(R.string.det_cambiar_voz)) { ctx.startActivity(Intent(ctx, HablarActivity::class.java)) }
                    .semantics { contentDescription = ctx.getString(R.string.det_cambiar_voz) },
                contentAlignment = Alignment.Center,
            ) { IconoAidly(Iconos.Microfono, Tema.Oscuro, 30.dp) }
        }
        Text(stringResource(R.string.det_cambiar_nota), fontSize = 15.sp, color = Tema.TextoSuave)

        Text(stringResource(R.string.det_cada), fontSize = 17.sp, color = Tema.TextoSuave)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            opciones.forEach { op ->
                val sel = cada == op
                val nombre = when (op) {
                    0L -> stringResource(R.string.det_solo_una)
                    1440L -> stringResource(R.string.det_todos_dias)
                    else -> if (op % 60 == 0L) stringResource(R.string.det_cada_horas, (op / 60).toInt()) else stringResource(R.string.det_cada_min, op.toInt())
                }
                Box(
                    Modifier.weight(1f).heightIn(min = 64.dp).clip(RoundedCornerShape(24.dp))
                        .background(if (sel) Tema.Turquesa else Tema.Fondo)
                        .border(2.dp, if (sel) Tema.Turquesa else Tema.TextoSuave, RoundedCornerShape(24.dp))
                        .clickable(role = Role.RadioButton) { cada = op }.semantics { selected = sel },
                    contentAlignment = Alignment.Center,
                ) { Text(nombre, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = if (sel) Tema.Oscuro else Tema.Texto, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 6.dp)) }
            }
        }

        BotonGrande(stringResource(R.string.det_guardar), Tema.Turquesa, alto = 68.dp, tamLetra = 26) {
            val c = Calendar.getInstance().apply { timeInMillis = item.proxima; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
            // Si la hora elegida ya paso, el aviso queda para la proxima vez (manana).
            if (c.timeInMillis <= System.currentTimeMillis() + 30_000) c.add(Calendar.DAY_OF_MONTH, 1)
            Recordatorios.agregar(ctx, Recordatorios.Item(item.id, item.texto, item.tipo, c.timeInMillis, cada))
            cerrar()
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(26.dp))
                .border(2.dp, if (confirmaBorrar) Tema.Coral else Tema.TextoSuave, RoundedCornerShape(26.dp))
                .background(if (confirmaBorrar) Tema.Coral else Tema.Fondo)
                .clickable(role = Role.Button) {
                    if (!confirmaBorrar) confirmaBorrar = true else { Recordatorios.cancelar(ctx, item.id); cerrar() }
                },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(if (confirmaBorrar) R.string.det_borrar_seguro else R.string.det_borrar), fontSize = 20.sp, fontWeight = FontWeight.Bold,
                color = if (confirmaBorrar) Tema.Oscuro else Tema.Texto,
            )
        }
    }
}

@Composable
private fun Paso(valor: Int, subir: String, bajar: String, alSubir: () -> Unit, alBajar: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Flecha(Iconos.ChevronDerecha, subir, -90f, alSubir)
        Text(valor.toString().padStart(2, '0'), fontSize = 64.sp, fontWeight = FontWeight.Bold, color = Tema.Texto)
        Flecha(Iconos.ChevronDerecha, bajar, 90f, alBajar)
    }
}

@Composable
private fun Flecha(icono: androidx.compose.ui.graphics.vector.ImageVector, descripcion: String, giro: Float, alClic: () -> Unit) {
    Box(
        Modifier.width(88.dp).heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).clickable(role = Role.Button, onClickLabel = descripcion) { alClic() }
            .semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center,
    ) { Box(Modifier.androidx_rotate(giro)) { IconoAidly(icono, Tema.Turquesa, 36.dp) } }
}

private fun Modifier.androidx_rotate(grados: Float): Modifier = this.then(Modifier.rotate(grados))
