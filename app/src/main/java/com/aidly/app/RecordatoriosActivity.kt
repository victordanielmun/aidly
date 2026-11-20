package com.aidly.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.CalendarContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * "Mis recordatorios y calendario" (diseno del 19-nov, validado en docs/VALIDACION-CALENDARIO.md): tira de la semana con navegacion,
 * lo que toca cada dia (medicinas, recordatorios y citas del calendario del telefono), marca de "hecho", "Leerme el dia" y
 * "Anadir con mi voz". Tocar un recordatorio abre su detalle; tocar una cita abre el calendario del telefono.
 */
class RecordatoriosActivity : ActividadAidly() {
    override val protegida get() = true
    private lateinit var voz: Voz

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voz = Voz(this)
        setContent { AidlyTema { Pantalla(voz) { finish() } } }
    }

    override fun onDestroy() { voz.cerrar(); super.onDestroy() }
}

/** Una cosa que toca en un dia: un aviso (medicina o general), o una cita del calendario del telefono. */
class Ocurrencia(
    val clave: String, val texto: String, val inicio: Long, val tipo: Tipo,
    val itemId: String? = null, val registroId: String? = null, val estado: Recordatorios.Estado? = null,
) {
    enum class Tipo { MEDICINA, RECORDATORIO, CITA }
    val hecho get() = estado == Recordatorios.Estado.TOMADO
    /** Se puede marcar a mano solo si ya hay un aviso registrado (ya sono o ya paso). */
    val marcable get() = registroId != null
}

object DiaCalendario {
    fun inicioDelDia(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun sumarDias(t: Long, d: Int): Long = Calendar.getInstance().apply { timeInMillis = t; add(Calendar.DAY_OF_MONTH, d) }.timeInMillis

    /** Inicio de la semana que contiene [t]; el primer dia sale del JSON de idiomas (lunes en espanol, domingo en ingles). */
    fun inicioSemana(ctx: android.content.Context, t: Long): Long {
        val primero = if (Idioma.config(ctx).getJSONObject("formato").optString("primer_dia_semana") == "domingo") Calendar.SUNDAY else Calendar.MONDAY
        val c = Calendar.getInstance().apply { timeInMillis = inicioDelDia(t) }
        while (c.get(Calendar.DAY_OF_WEEK) != primero) c.add(Calendar.DAY_OF_MONTH, -1)
        return c.timeInMillis
    }

    /** Lo que toca ese dia: avisos futuros (expandidos), avisos ya sonados (registros) y citas del calendario. */
    fun construir(ctx: android.content.Context, dia: Long, ahora: Long = System.currentTimeMillis()): List<Ocurrencia> {
        val fin = sumarDias(dia, 1)
        val lista = mutableListOf<Ocurrencia>()
        val registros = Recordatorios.historial(ctx).filter { it.tipo != Recordatorios.Tipo.CHECKIN && it.programada in dia until fin }
        registros.forEach {
            lista += Ocurrencia("r" + it.id, it.texto, it.programada, if (it.tipo == Recordatorios.Tipo.MEDICINA) Ocurrencia.Tipo.MEDICINA else Ocurrencia.Tipo.RECORDATORIO,
                it.recId, it.id, it.estado)
        }
        if (fin > ahora) {                                           // los dias que ya pasaron solo muestran lo que sono
            for (item in Recordatorios.listar(ctx)) {
                var t = item.proxima
                var n = 0
                while (t < fin && n++ < 400) {
                    if (t >= dia && t >= ahora - 60_000 && registros.none { it.recId == item.id && kotlin.math.abs(it.programada - t) < 60_000 }) {
                        lista += Ocurrencia("i${item.id}_$t", item.texto, t, if (item.tipo == Recordatorios.Tipo.MEDICINA) Ocurrencia.Tipo.MEDICINA else Ocurrencia.Tipo.RECORDATORIO, item.id)
                    }
                    if (item.cadaMin <= 0) break
                    t += item.cadaMin * 60_000
                }
            }
        }
        Agenda.citas(ctx, dia, fin).forEach { lista += Ocurrencia("c${it.inicio}_${it.titulo}", it.titulo, it.inicio, Ocurrencia.Tipo.CITA) }
        return lista.sortedBy { it.inicio }
    }
}

@Composable
private fun Pantalla(voz: Voz, cerrar: () -> Unit) {
    val ctx = LocalContext.current
    val hoy = remember { DiaCalendario.inicioDelDia(System.currentTimeMillis()) }
    var semana by remember { mutableStateOf(DiaCalendario.inicioSemana(ctx, hoy)) }
    var dia by remember { mutableStateOf(hoy) }
    var version by remember { mutableIntStateOf(0) }
    var permisoCal by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) }
    val pedirCal = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permisoCal = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED; version++
    }
    LifecycleResumeEffect(Unit) {
        permisoCal = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED
        version++
        onPauseOrDispose { voz.callar() }
    }
    val loc = Idioma.locale(ctx)
    val horaFmt = remember(loc) { SimpleDateFormat(Idioma.config(ctx).getJSONObject("formato").getString("hora_pantalla"), loc) }
    val diasSemana = remember(semana, version) { (0 until 7).map { DiaCalendario.sumarDias(semana, it) } }
    val delDia = remember(dia, version) { DiaCalendario.construir(ctx, dia) }
    val conteos = remember(semana, version) { diasSemana.associateWith { d -> DiaCalendario.construir(ctx, d).size } }
    val ahora = System.currentTimeMillis()
    val siguiente = if (dia == hoy) delDia.firstOrNull { !it.hecho && it.inicio >= ahora - 60_000 && it.estado != Recordatorios.Estado.OMITIDO } else null
    val pendientes = delDia.count { !it.hecho && it.estado != Recordatorios.Estado.OMITIDO && it.estado != Recordatorios.Estado.POSPUESTO }

    fun fmt(patron: String, t: Long) = SimpleDateFormat(patron, loc).format(Date(t)).replaceFirstChar { it.uppercase(loc) }
    // En espanol los dias van en minuscula ("Hoy, viernes 20"); en ingles se escriben con mayuscula ("Today, Friday 20").
    val tituloDia = if (dia == hoy) stringResource(R.string.cal_hoy, fmt("EEEE d", dia).let { if (Idioma.resolver(ctx) == "es") it.replaceFirstChar { c -> c.lowercase(loc) } else it }) else fmt("EEEE d", dia)
    // Con letra muy grande (200 %) no cabe una pantalla fija: todo se desplaza junto, botones incluidos.
    val grande = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f

    Column(
        Modifier.fillMaxSize().background(Tema.Fondo).then(if (grande) Modifier.verticalScroll(rememberScrollState()) else Modifier).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(
                Modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).clickable(role = Role.Button) { voz.callar(); cerrar() }.padding(end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconoAidly(Iconos.ChevronIzquierda, Tema.Texto, 30.dp)
                Text(stringResource(R.string.volver), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Tema.Texto)
            }
        }

        // Mes y flechas para cambiar de semana (arriba, para que la tira use todo el ancho), y los 7 dias.
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BotonSemana(Iconos.ChevronIzquierda, stringResource(R.string.cal_semana_anterior)) { semana = DiaCalendario.sumarDias(semana, -7); dia = semana }
            Text(fmt("LLLL yyyy", dia), fontSize = 22.sp, color = Tema.Texto, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            BotonSemana(Iconos.ChevronDerecha, stringResource(R.string.cal_semana_siguiente)) { semana = DiaCalendario.sumarDias(semana, 7); dia = semana }
        }
        Tira(diasSemana, dia, hoy, conteos, loc) { dia = it }
        if (dia != hoy) {
            Text(
                stringResource(R.string.cal_ir_hoy), fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Tema.Turquesa,
                modifier = Modifier.heightIn(min = 48.dp).clickable(role = Role.Button) { semana = DiaCalendario.inicioSemana(ctx, hoy); dia = hoy }.padding(vertical = 10.dp),
            )
        }

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(tituloDia, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Tema.Texto, modifier = Modifier.weight(1f))
            if (delDia.isNotEmpty()) Text(
                if (pendientes == 0) stringResource(R.string.cal_todo_hecho) else stringResource(R.string.cal_por_hacer, pendientes),
                fontSize = 18.sp, color = Tema.TextoSuave,
            )
        }

        Column(
            (if (grande) Modifier else Modifier.weight(1f).verticalScroll(rememberScrollState())),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!permisoCal) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Tema.RadioFila)).background(Tema.Tarjeta).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.cal_permiso), fontSize = 18.sp, color = Tema.Texto)
                    BotonGrande(stringResource(R.string.cal_dar_permiso), Tema.Turquesa, alto = 60.dp, tamLetra = 19) {
                        pedirCal.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
                    }
                }
            }
            if (delDia.isEmpty()) {
                Box(Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(RoundedCornerShape(24.dp)).border(2.dp, Tema.TextoSuave, RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.cal_vacio), fontSize = 22.sp, color = Tema.TextoSuave, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 20.dp))
                }
            }
            delDia.forEach { o ->
                FilaOcurrencia(o, o === siguiente, horaFmt.format(Date(o.inicio)), onAbrir = {
                    when {
                        o.tipo == Ocurrencia.Tipo.CITA -> try {
                            ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("content://com.android.calendar/time/${o.inicio}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (e: Exception) { SpikeLog.log("CALENDARIO: no pude abrir el calendario (${e::class.simpleName})") }
                        o.itemId != null && Recordatorios.obtener(ctx, o.itemId) != null ->
                            ctx.startActivity(Intent(ctx, RecordatorioDetalleActivity::class.java).putExtra(RecordatorioDetalleActivity.EXTRA_ID, o.itemId))
                    }
                }, onMarcar = {
                    o.registroId?.let { id ->
                        Recordatorios.marcar(ctx, id, if (o.hecho) Recordatorios.Estado.PENDIENTE else Recordatorios.Estado.TOMADO, soloSiPendiente = false)
                        version++
                    }
                })
            }
        }

        BotonContorno(stringResource(R.string.cal_leer_dia), Iconos.Altavoz) {
            voz.callar()
            voz.decir(textoDelDia(ctx, tituloDia, delDia, horaFmt))
        }
        Row(
            Modifier.fillMaxWidth().heightIn(min = 68.dp).clip(RoundedCornerShape(34.dp)).background(Tema.Turquesa)
                .clickable(role = Role.Button) { ctx.startActivity(Intent(ctx, HablarActivity::class.java)) },
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
        ) {
            IconoAidly(Iconos.Microfono, Tema.Oscuro, 30.dp)
            Spacer(Modifier.width(12.dp))
            Text(stringResource(R.string.cal_anadir_voz), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tema.Oscuro, textAlign = TextAlign.Center, modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp))
        }
    }
}

/** Lo que dice "Leerme el dia" (en el idioma de la app). */
private fun textoDelDia(ctx: android.content.Context, titulo: String, lista: List<Ocurrencia>, fmt: SimpleDateFormat): String {
    if (lista.isEmpty()) return Idioma.vozTexto(ctx, R.string.cal_voz_nada, titulo)
    val partes = lista.take(8).joinToString(" ") {
        val c = Calendar.getInstance().apply { timeInMillis = it.inicio }
        Idioma.vozTexto(ctx, R.string.cal_voz_item, Recordatorios.horaHablada(c), it.texto) + if (it.hecho) " " + Idioma.vozTexto(ctx, R.string.cal_voz_hecho) else ""
    }
    return Idioma.vozTexto(ctx, R.string.cal_voz_dia, titulo, lista.size) + " " + partes
}

@Composable
private fun BotonSemana(icono: androidx.compose.ui.graphics.vector.ImageVector, descripcion: String, alClic: () -> Unit) {
    Box(
        Modifier.size(52.dp).clip(CircleShape).background(Tema.Tarjeta).clickable(role = Role.Button, onClickLabel = descripcion) { alClic() }
            .semantics { contentDescription = descripcion },
        contentAlignment = Alignment.Center,
    ) { IconoAidly(icono, Tema.Turquesa, 28.dp) }
}

/** Los 7 dias. Con letra grande no caben: la tira se desplaza en horizontal en vez de apretarse. */
@Composable
private fun Tira(dias: List<Long>, seleccionado: Long, hoy: Long, conteos: Map<Long, Int>, loc: Locale, alElegir: (Long) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val hueco = 4.dp
        // Cada dia necesita mas ancho cuanto mas grande es la letra; si no caben los 7, la tira se desplaza.
        val ancho: Dp = maxOf(44.dp * androidx.compose.ui.platform.LocalDensity.current.fontScale.coerceAtLeast(1f), (maxWidth - hueco * 6) / 7)
        val desplaza = ancho * 7 + hueco * 6 > maxWidth + 1.dp
        val scroll = rememberScrollState()
        val paso = with(androidx.compose.ui.platform.LocalDensity.current) { (ancho + hueco).roundToPx() }
        // El dia elegido siempre queda a la vista, aunque la tira se desplace.
        androidx.compose.runtime.LaunchedEffect(seleccionado, desplaza, paso) {
            if (desplaza) scroll.animateScrollTo((dias.indexOf(seleccionado).coerceAtLeast(0) * paso - paso).coerceAtLeast(0))
        }
        Row(Modifier.then(if (desplaza) Modifier.horizontalScroll(scroll) else Modifier), horizontalArrangement = Arrangement.spacedBy(hueco)) {
            dias.forEach { d ->
                val sel = d == seleccionado
                val n = conteos[d] ?: 0
                val etiqueta = SimpleDateFormat("EEE", loc).format(Date(d)).trimEnd('.').replaceFirstChar { it.uppercase(loc) }
                val completo = SimpleDateFormat("EEEE d", loc).format(Date(d))
                Column(
                    Modifier.width(ancho).heightIn(min = 80.dp).clip(RoundedCornerShape(24.dp))
                        .background(if (sel) Tema.Turquesa else Tema.Tarjeta)
                        .border(2.dp, if (sel) Tema.Turquesa else if (d == hoy) Tema.TextoSuave else Tema.Tarjeta, RoundedCornerShape(24.dp))
                        .clickable(role = Role.Tab) { alElegir(d) }
                        .semantics { selected = sel; contentDescription = completo + ", " + n },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Text(etiqueta, fontSize = 15.sp, color = if (sel) Tema.Oscuro else Tema.Texto, maxLines = 1)
                    Text(SimpleDateFormat("d", loc).format(Date(d)), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (sel) Tema.Oscuro else Tema.Texto)
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (n > 0) (if (sel) Tema.Oscuro else Tema.Turquesa) else Color.Transparent))
                }
            }
        }
    }
}

@Composable
private fun FilaOcurrencia(o: Ocurrencia, esSiguiente: Boolean, hora: String, onAbrir: () -> Unit, onMarcar: () -> Unit) {
    val forma = RoundedCornerShape(24.dp)
    val tipoTxt = stringResource(when (o.tipo) { Ocurrencia.Tipo.MEDICINA -> R.string.cal_t_medicina; Ocurrencia.Tipo.CITA -> R.string.cal_t_cita; else -> R.string.cal_t_recordatorio })
    val icono = when (o.tipo) { Ocurrencia.Tipo.MEDICINA -> Iconos.Pastilla; Ocurrencia.Tipo.CITA -> Iconos.Calendario; else -> Iconos.Campana }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 88.dp).clip(forma).background(Tema.Tarjeta)
            .border(3.dp, if (esSiguiente) Tema.Turquesa else Color.Transparent, forma).padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.weight(1f).clickable(role = Role.Button, onClick = onAbrir), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(hora, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Tema.Texto, modifier = Modifier.widthIn(min = 64.dp))
            Column(Modifier.weight(1f)) {
                if (esSiguiente) Text(stringResource(R.string.cal_siguiente), fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Tema.Turquesa)
                Text(
                    o.texto.replaceFirstChar { it.uppercase() }, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Tema.Texto,
                    textDecoration = if (o.hecho) TextDecoration.LineThrough else TextDecoration.None,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconoAidly(icono, Tema.Turquesa, 18.dp)
                    Text(tipoTxt, fontSize = 16.sp, color = Tema.TextoSuave)
                    when (o.estado) {
                        Recordatorios.Estado.OMITIDO -> Text("· " + stringResource(R.string.cal_omitido), fontSize = 16.sp, color = Tema.Coral)
                        Recordatorios.Estado.POSPUESTO -> Text("· " + stringResource(R.string.cal_pospuesto), fontSize = 16.sp, color = Tema.TextoSuave)
                        else -> {}
                    }
                }
            }
        }
        if (o.marcable) {
            val desc = stringResource(if (o.hecho) R.string.cal_desmarcar else R.string.cal_marcar, o.texto)
            Box(
                Modifier.size(56.dp).clip(CircleShape).border(3.dp, Tema.Turquesa, CircleShape).background(if (o.hecho) Tema.Turquesa else Color.Transparent)
                    .clickable(role = Role.Checkbox, onClickLabel = desc) { onMarcar() }.semantics { contentDescription = desc; selected = o.hecho },
                contentAlignment = Alignment.Center,
            ) { if (o.hecho) IconoAidly(Iconos.Check, Tema.Oscuro, 30.dp) }
        }
    }
}

@Composable
fun BotonContorno(texto: String, icono: androidx.compose.ui.graphics.vector.ImageVector, alClic: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clip(RoundedCornerShape(30.dp)).border(2.dp, Tema.Turquesa, RoundedCornerShape(30.dp)).clickable(role = Role.Button) { alClic() },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center,
    ) {
        IconoAidly(icono, Tema.Turquesa, 28.dp)
        Spacer(Modifier.width(12.dp))
        Text(texto, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tema.Texto)
    }
}
