package com.aidly.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Pantalla de inicio (rediseno 19-nov): una accion enorme, cuatro filas, el interruptor de mensajes y
 * "Pedir ayuda" siempre fijo abajo. Con letra grande el contenido se desplaza y la ayuda se queda a la vista.
 */
class HomeActivity : ActividadAidly() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Primer arranque: primero la privacidad (que sale del telefono) y despues la bienvenida.
        if (!Privacidad.decidida(this)) {
            startActivity(Intent(this, PrivacidadActivity::class.java).putExtra(Privacidad.EXTRA_MODO, Privacidad.MODO_PRIMERA))
            finish()
            return
        }
        if (!Preferencias.bienvenidaHecha(this)) {
            startActivity(Intent(this, BienvenidaActivity::class.java))
            finish()
            return
        }
        setContent { AidlyTema { HomeScreen() } }
    }
}

private fun saludo(ctx: android.content.Context): String = Idioma.saludo(ctx, LocalDateTime.now().hour)

private fun fechaHoy(ctx: android.content.Context): String {
    val patron = Idioma.config(ctx).getJSONObject("formato").getString("fecha_larga")
    return LocalDate.now().format(DateTimeFormatter.ofPattern(patron, Idioma.locale(ctx))).replaceFirstChar { it.uppercase() }
}

/** "Próximo: 12:30" / "Próximo: mañana 8:00" / "Sin recordatorios" (en el idioma elegido). */
private fun proximoRecordatorio(ctx: android.content.Context): String {
    val p = Recordatorios.listar(ctx).firstOrNull() ?: return ctx.getString(R.string.home_sin_recordatorios)
    val formato = Idioma.config(ctx).getJSONObject("formato")
    val cuando = Instant.ofEpochMilli(p.proxima).atZone(ZoneId.systemDefault())
    val hora = cuando.format(DateTimeFormatter.ofPattern(formato.getString("hora_pantalla"), Idioma.locale(ctx)))
    val dias = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.now(), cuando.toLocalDate())
    return when (dias) {
        0L -> ctx.getString(R.string.home_proximo, hora)
        1L -> ctx.getString(R.string.home_proximo_manana, hora)
        else -> ctx.getString(
            R.string.home_proximo_fecha,
            cuando.format(DateTimeFormatter.ofPattern(formato.getString("fecha_corta"), Idioma.locale(ctx))), hora,
        )
    }
}
@Composable
private fun HomeScreen() {
    val ctx = LocalContext.current
    var companeroActivo by remember { mutableStateOf(AidlyService.running) }
    var accesoMensajes by remember { mutableStateOf(true) }
    var accesoCalendario by remember { mutableStateOf(true) }
    var accesoContactos by remember { mutableStateOf(true) }
    var proximo by remember { mutableStateOf("") }
    var saludoTexto by remember { mutableStateOf(saludo(ctx)) }
    var aviso by remember { mutableStateOf("") }

    val pedirNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        // Sin este permiso los avisos de medicinas no se verian (Android 13+).
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) pedirNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    val permisosCompanero = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r[Manifest.permission.RECORD_AUDIO] == true) {
            AidlyService.start(ctx)
            companeroActivo = true
            if (!NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)) {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        } else {
            aviso = ctx.getString(R.string.home_sin_microfono)
        }
    }

    // Se refresca al volver de Ajustes u otra pantalla.
    LifecycleResumeEffect(Unit) {
        Circulo.tocar(ctx)
        saludoTexto = saludo(ctx)
        proximo = proximoRecordatorio(ctx)
        accesoCalendario = Agenda.tienePermiso(ctx)
        accesoContactos = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        companeroActivo = AidlyService.running
        accesoMensajes = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        onPauseOrDispose {}
    }

    val faltanPermisos = !accesoMensajes || !accesoCalendario || !accesoContactos
    fun abrir(c: Class<*>) = ctx.startActivity(Intent(ctx, c))

    Column(Modifier.fillMaxSize().background(Tema.Fondo).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CabeceraAidly(avisoPermisos = faltanPermisos) { abrir(AjustesActivity::class.java) }

            Column {
                Text(saludoTexto, fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Tema.Texto)
                Text(fechaHoy(ctx), fontSize = 20.sp, color = Tema.TextoSuave)
            }

            TarjetaHablar(stringResource(R.string.home_hablar), stringResource(R.string.home_hablar_sub)) { abrir(HablarActivity::class.java) }

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FilaAccion(Iconos.Campana, stringResource(R.string.home_recordatorios), proximo) { abrir(RecordatoriosActivity::class.java) }
                FilaAccion(Iconos.Personas, stringResource(R.string.home_familia), stringResource(R.string.home_familia_sub)) { abrir(FamiliaActivity::class.java) }
                FilaAccion(Iconos.Telefono, stringResource(R.string.home_marcar), stringResource(R.string.home_marcar_sub)) { abrir(MarcarActivity::class.java) }
                FilaAccion(Iconos.Documento, stringResource(R.string.home_papel), stringResource(R.string.home_papel_sub), amarilla = true) { abrir(AnalizaActivity::class.java) }
            }

            InterruptorFila(
                Iconos.Altavoz, stringResource(R.string.home_mensajes),
                when {
                    !companeroActivo -> stringResource(R.string.home_apagado)
                    !accesoMensajes -> stringResource(R.string.home_falta_acceso)
                    else -> stringResource(R.string.home_encendido)
                },
                companeroActivo,
            ) { encender ->
                if (encender) {
                    permisosCompanero.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
                } else {
                    AidlyService.start(ctx, AidlyService.ACTION_STOP)
                    companeroActivo = false
                }
            }
            if (aviso.isNotBlank()) Text(aviso, fontSize = 18.sp, color = Tema.Coral)
        }

        EspacioV(10.dp)
        PildoraAyuda { abrir(SosActivity::class.java) }
    }
}
