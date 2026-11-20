package com.aidly.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Primer arranque: recorre los pasos de `idiomas.<id>.bienvenida.pasos` (idioma, nombre, permisos, mensajes,
 * calendario y contactos, familia y una prueba). Todo se puede saltar menos el idioma y los permisos basicos,
 * que no tienen "saltar" pero tampoco bloquean: siempre se puede continuar.
 */
class BienvenidaActivity : ActividadAidly() {
    private lateinit var voz: Voz

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voz = Voz(this)
        setContent { AidlyTema { Pantalla(voz) { terminar() } } }
    }

    private fun terminar() {
        Preferencias.fijarBienvenidaHecha(this, true)
        Idioma.aplicarInicial(this)
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

    override fun onDestroy() {
        voz.cerrar()
        super.onDestroy()
    }
}

private class PasoBienvenida(val id: String, val titulo: String, val texto: String, val permisos: List<String>, val obligatorio: Boolean)

private fun pasosDe(ctx: android.content.Context): List<PasoBienvenida> {
    val arr = Idioma.config(ctx).getJSONObject("bienvenida").getJSONArray("pasos")
    return (0 until arr.length()).map { arr.getJSONObject(it) }.map { p ->
        val perms = p.optJSONArray("permisos")
        PasoBienvenida(
            p.getString("id"), p.optString("titulo"), p.optString("texto"),
            (0 until (perms?.length() ?: 0)).map { perms!!.getString(it) }, p.optBoolean("obligatorio", false),
        )
    }
}

/** Permisos de Android de cada permiso del JSON. `acceso_mensajes` es especial (se da en Ajustes del sistema). */
private fun androidPermisos(id: String): List<String> = when (id) {
    "microfono" -> listOf(Manifest.permission.RECORD_AUDIO)
    "notificaciones" -> if (Build.VERSION.SDK_INT >= 33) listOf(Manifest.permission.POST_NOTIFICATIONS) else emptyList()
    "calendario" -> listOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)
    "contactos" -> listOf(Manifest.permission.READ_CONTACTS)
    "ubicacion" -> listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION)
    "camara" -> listOf(Manifest.permission.CAMERA)
    else -> emptyList()
}

private fun concedido(ctx: android.content.Context, id: String): Boolean =
    if (id == "acceso_mensajes") NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)
    else androidPermisos(id).all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }

@Composable
private fun nombrePermiso(id: String): String = stringResource(
    when (id) {
        "microfono" -> R.string.bienv_perm_microfono
        "notificaciones" -> R.string.perm_avisos
        "acceso_mensajes" -> R.string.perm_mensajes
        "calendario" -> R.string.perm_calendario
        "contactos" -> R.string.perm_contactos
        "ubicacion" -> R.string.bienv_perm_ubicacion
        else -> R.string.bienv_perm_camara
    },
)

@Composable
private fun Pantalla(voz: Voz, terminar: () -> Unit) {
    val ctx = LocalContext.current
    val pasos = remember { pasosDe(ctx) }
    var indice by rememberSaveable { mutableIntStateOf(0) }
    val paso = pasos[indice.coerceIn(0, pasos.lastIndex)]
    val ultimo = indice >= pasos.lastIndex
    var refresco by remember { mutableIntStateOf(0) }   // fuerza a releer el estado de los permisos
    var nombre by remember { mutableStateOf(Preferencias.nombre(ctx)) }
    var idiomaPreferido by remember { mutableStateOf(Idioma.preferido(ctx)) }

    LifecycleResumeEffect(Unit) { refresco++; onPauseOrDispose {} }
    val pedir = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresco++ }

    // Al llegar al ultimo paso, saludo de arranque en el idioma activo.
    LaunchedEffect(ultimo) {
        if (ultimo) {
            Idioma.configDe(ctx, Idioma.resolver(ctx))?.getJSONObject("inicial")?.optString("saludo_de_arranque")?.takeIf { it.isNotBlank() }?.let { voz.decir(it) }
        }
    }

    Column(Modifier.fillMaxSize().background(Tema.Fondo).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.bienv_paso, indice + 1, pasos.size), fontSize = 16.sp, color = Tema.TextoSuave)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                pasos.forEachIndexed { i, _ ->
                    Box(Modifier.size(12.dp).clip(CircleShape).background(if (i <= indice) Tema.Turquesa else Tema.Tarjeta))
                }
            }
            AidlyCara(
                if (ultimo) Emocion.CONTENTO else Emocion.REPOSO,
                Modifier.fillMaxWidth(0.42f), remember { !Preferencias.reducirMovimiento(ctx) },
            )
            Text(paso.titulo, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Tema.Texto, textAlign = TextAlign.Center)
            Text(paso.texto, fontSize = 22.sp, color = Tema.TextoSuave, textAlign = TextAlign.Center)

            when (paso.id) {
                "idioma" -> {
                    fun elegir(id: String) {
                        if (id == idiomaPreferido) return
                        Idioma.fijar(ctx, id); idiomaPreferido = id
                        (ctx as? android.app.Activity)?.recreate()   // vuelve a leer titulos y textos del idioma nuevo
                    }
                    FilaAccion(
                        if (idiomaPreferido.isEmpty()) Iconos.Check else Iconos.ChevronDerecha, stringResource(R.string.idioma_automatico),
                        "", chevron = false,
                    ) { elegir("") }
                    Idioma.disponibles(ctx).forEach { op ->
                        FilaAccion(
                            if (idiomaPreferido == op.id) Iconos.Check else Iconos.ChevronDerecha, op.nombre, "", chevron = false,
                        ) { elegir(op.id) }
                    }
                }
                "nombre" -> OutlinedTextField(
                    value = nombre, onValueChange = { nombre = it.take(30); Preferencias.fijarNombre(ctx, it.take(30)) }, singleLine = true,
                    label = { Text(stringResource(R.string.bienv_nombre_hint), fontSize = 18.sp) },
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 26.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Tema.Texto, unfocusedTextColor = Tema.Texto,
                        focusedBorderColor = Tema.Turquesa, unfocusedBorderColor = Tema.TextoSuave,
                        focusedLabelColor = Tema.Turquesa, unfocusedLabelColor = Tema.TextoSuave, cursorColor = Tema.Turquesa,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                "familia" -> BotonGrande(stringResource(R.string.bienv_configurar), Tema.Turquesa, alto = 72.dp) {
                    ctx.startActivity(Intent(ctx, CirculoActivity::class.java))
                }
                "prueba" -> {
                    Text(stringResource(R.string.bienv_ejemplos), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Tema.Turquesa, modifier = Modifier.fillMaxWidth())
                    val ej = Idioma.configDe(ctx, Idioma.resolver(ctx))?.optJSONArray("ejemplos")
                    (0 until minOf(ej?.length() ?: 0, 5)).forEach { Text("• " + ej!!.getString(it), fontSize = 20.sp, color = Tema.Texto, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth()) }
                }
            }

            // Permisos de este paso: estado de cada uno y un solo boton para pedir los que faltan.
            if (paso.permisos.isNotEmpty()) {
                @Suppress("UNUSED_EXPRESSION") refresco
                paso.permisos.forEach { p ->
                    val ok = concedido(ctx, p)
                    FilaAccion(
                        if (ok) Iconos.Check else Iconos.Alerta, nombrePermiso(p),
                        stringResource(if (ok) R.string.perm_permitido else R.string.perm_falta),
                        amarilla = !ok, chevron = false,
                    ) {}
                }
                val faltan = paso.permisos.filterNot { concedido(ctx, it) }
                if (faltan.isNotEmpty()) {
                    val especial = faltan.contains("acceso_mensajes")
                    BotonGrande(stringResource(if (especial) R.string.bienv_abrir_ajustes else R.string.bienv_permitir), Tema.Amarillo, alto = 72.dp) {
                        if (especial) ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                        else pedir.launch(faltan.flatMap { androidPermisos(it) }.toTypedArray())
                    }
                }
            }
        }

        Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            BotonGrande(stringResource(if (ultimo) R.string.bienv_empezar else R.string.bienv_continuar), Tema.Turquesa, alto = 76.dp) {
                if (ultimo) terminar() else indice++
            }
            if (!paso.obligatorio && !ultimo) {
                TextButton(onClick = { indice++ }) {
                    Text(stringResource(R.string.bienv_saltar), fontSize = 18.sp, color = Tema.TextoSuave)
                }
            }
        }
    }
}
