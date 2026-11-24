package com.aidly.app

import android.Manifest
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.launch
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

/** Ajustes: movimiento, permisos que faltan, widgets y pruebas tecnicas (lo que antes era "Mas opciones"). */
class AjustesActivity : ActividadAidly() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AidlyTema { Pantalla { finish() } } }
    }
}

/** Pide al launcher que anada un widget (la persona confirma en un dialogo del sistema). */
fun fijarWidget(ctx: Context, receptor: Class<*>) {
    val awm = AppWidgetManager.getInstance(ctx)
    if (awm.isRequestPinAppWidgetSupported) {
        awm.requestPinAppWidget(ComponentName(ctx, receptor), null, null)
    } else {
        SpikeLog.log("WIDGET: este launcher no permite anadirlo desde la app; usa pulsacion larga en la pantalla de inicio")
    }
}

@Composable
private fun Pantalla(cerrar: () -> Unit) {
    val ctx = LocalContext.current
    var reducir by remember { mutableStateOf(Preferencias.reducirMovimientoPropio(ctx)) }
    var mensajes by remember { mutableStateOf(true) }
    var calendario by remember { mutableStateOf(true) }
    var contactos by remember { mutableStateOf(true) }
    var notificaciones by remember { mutableStateOf(true) }
    var idiomaPreferido by remember { mutableStateOf(Idioma.preferido(ctx)) }
    var rolLlamadas by remember { mutableStateOf(Llamadas.rolConcedido(ctx)) }
    var decirLlamadas by remember { mutableStateOf(Llamadas.anunciar(ctx)) }

    LifecycleResumeEffect(Unit) {
        mensajes = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
        calendario = Agenda.tienePermiso(ctx)
        contactos = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        notificaciones = android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        rolLlamadas = Llamadas.rolConcedido(ctx)
        onPauseOrDispose {}
    }
    val pedirCalendario = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { calendario = Agenda.tienePermiso(ctx) }
    val pedirContactos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { contactos = it }
    val pedirNotif = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notificaciones = it }
    val pedirRolLlamadas = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { rolLlamadas = Llamadas.rolConcedido(ctx) }

    /** Guarda el idioma y vuelve a dibujar la pantalla con los textos nuevos. "" = automatico. */
    fun elegirIdioma(id: String) {
        if (id == idiomaPreferido) return
        Idioma.fijar(ctx, id); idiomaPreferido = id
        val app = ctx.applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default).launch {
            try { AidlyWidget().updateAll(app); AidlyWidgetCompacto().updateAll(app) } catch (e: Exception) { SpikeLog.log("WIDGET: no pude actualizar (${e.message})") }
        }
        (ctx as? android.app.Activity)?.recreate()
    }

    Column(Modifier.fillMaxSize().background(Tema.Fondo).padding(horizontal = 16.dp, vertical = 14.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        EncabezadoPantalla(stringResource(R.string.ajustes_titulo), cerrar)

        TituloSeccion(stringResource(R.string.ajustes_idioma))
        FilaAccion(
            if (idiomaPreferido.isEmpty()) Iconos.Check else Iconos.ChevronDerecha, stringResource(R.string.idioma_automatico),
            if (idiomaPreferido.isEmpty()) stringResource(R.string.idioma_activo) else "", chevron = false,
        ) { elegirIdioma("") }
        Idioma.disponibles(ctx).forEach { op ->
            val activo = idiomaPreferido == op.id
            FilaAccion(
                if (activo) Iconos.Check else Iconos.ChevronDerecha, op.nombre,
                if (activo) stringResource(R.string.idioma_activo) else "", chevron = false,
            ) { elegirIdioma(op.id) }
        }

        InterruptorFila(null, stringResource(R.string.ajustes_reducir), stringResource(R.string.ajustes_reducir_desc), reducir) {
            reducir = it; Preferencias.fijarReducirMovimiento(ctx, it)
        }

        TituloSeccion(stringResource(R.string.ajustes_permisos))
        PermisoFila(stringResource(R.string.perm_avisos), notificaciones) { pedirNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }
        PermisoFila(stringResource(R.string.perm_mensajes), mensajes) { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        PermisoFila(stringResource(R.string.perm_calendario), calendario) {
            pedirCalendario.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR))
        }
        PermisoFila(stringResource(R.string.perm_contactos), contactos) { pedirContactos.launch(Manifest.permission.READ_CONTACTS) }

        if (Llamadas.disponible()) {
            if (rolLlamadas) {
                InterruptorFila(Iconos.Telefono, stringResource(R.string.ajustes_llamadas), stringResource(if (decirLlamadas) R.string.ajustes_llamadas_on else R.string.ajustes_llamadas_off), decirLlamadas) {
                    decirLlamadas = it; Llamadas.fijarAnunciar(ctx, it)
                }
            } else {
                FilaAccion(Iconos.Telefono, stringResource(R.string.ajustes_llamadas), stringResource(R.string.ajustes_llamadas_pedir)) {
                    try { pedirRolLlamadas.launch(Llamadas.pedirRol(ctx)) } catch (e: Exception) { SpikeLog.log("LLAMADAS: no pude pedir el rol (${e.message})") }
                }
            }
        }

        FilaAccion(Iconos.Alerta, stringResource(R.string.ajustes_emergencia), stringResource(R.string.ajustes_emergencia_sub)) {
            ctx.startActivity(Intent(ctx, EmergenciaActivity::class.java))
        }

        FilaAccion(Iconos.Estrella, stringResource(R.string.ajustes_a11y), stringResource(R.string.ajustes_a11y_sub)) {
            ctx.startActivity(Intent(ctx, AyudaOtrasAppsActivity::class.java))
        }

        TituloSeccion(stringResource(R.string.ajustes_inicio))
        FilaAccion(Iconos.Microfono, stringResource(R.string.widget_grande_titulo), stringResource(R.string.widget_grande_sub)) {
            fijarWidget(ctx, AidlyWidgetReceiver::class.java)
        }
        FilaAccion(Iconos.Microfono, stringResource(R.string.widget_pequeno_titulo), stringResource(R.string.widget_pequeno_sub)) {
            fijarWidget(ctx, AidlyWidgetCompactoReceiver::class.java)
        }

        FilaAccion(Iconos.Alerta, stringResource(R.string.ajustes_privacidad), stringResource(R.string.ajustes_privacidad_sub)) {
            ctx.startActivity(Intent(ctx, PrivacidadActivity::class.java))
        }

        FilaAccion(Iconos.Check, stringResource(R.string.ajustes_restablecer), stringResource(R.string.ajustes_restablecer_sub), chevron = false) {
            Idioma.restablecer(ctx); reducir = Preferencias.reducirMovimientoPropio(ctx)
        }
        FilaAccion(Iconos.Corazon, stringResource(R.string.ajustes_bienvenida), stringResource(R.string.ajustes_bienvenida_sub)) {
            ctx.startActivity(Intent(ctx, BienvenidaActivity::class.java))
        }

        TextButton(onClick = { ctx.startActivity(Intent(ctx, MainActivity::class.java)) }) {
            Text(stringResource(R.string.ajustes_pruebas), fontSize = 16.sp, color = Tema.TextoSuave)
        }
    }
}

@Composable
private fun PermisoFila(titulo: String, concedido: Boolean, pedir: () -> Unit) {
    FilaAccion(
        if (concedido) Iconos.Check else Iconos.Alerta, titulo,
        stringResource(if (concedido) R.string.perm_permitido else R.string.perm_falta),
        amarilla = !concedido, chevron = !concedido,
    ) { if (!concedido) pedir() }
}