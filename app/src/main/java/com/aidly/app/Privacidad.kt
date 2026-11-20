package com.aidly.app

import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.appwidget.updateAll
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

/**
 * Privacidad: que se queda en el telefono y que sale a la nube de Google (Gemini), y el control de la persona.
 *  - Antes de la primera vez que algo sale del telefono, la persona decide (pantalla de consentimiento).
 *  - "Nube apagada": solo funciona lo que se resuelve en el telefono (recordatorios, lectura de mensajes, SOS,
 *    llamadas). Leer medicinas o papeles, buscar en internet y conversar necesitan la nube y lo avisan.
 *  - "Borrar todos mis datos": deja la app como recien instalada y borra el circulo familiar de la nube.
 * Sin decision tomada se trata como "apagada": nunca se manda nada por omision.
 */
object Privacidad {
    class NubeApagada : Exception("La nube esta apagada en Privacidad")

    private const val PREFS = "privacidad"
    private const val CLAVE = "nube"
    @Volatile private var nube = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Se llama al arrancar la app: deja la decision guardada en memoria para consultarla sin contexto. */
    fun cargar(ctx: Context) { nube = prefs(ctx).getBoolean(CLAVE, false) }

    /** La persona ya eligio (aceptar o solo telefono). */
    fun decidida(ctx: Context) = prefs(ctx).contains(CLAVE)

    fun nubeActiva() = nube

    fun fijarNube(ctx: Context, v: Boolean) {
        prefs(ctx).edit().putBoolean(CLAVE, v).apply(); nube = v
        SpikeLog.log("PRIVACIDAD: nube ${if (v) "encendida" else "apagada"}")
    }

    /** Las funciones que mandan datos a la nube llaman a esto primero. */
    fun exigirNube() { if (!nube) throw NubeApagada() }

    private val ALMACENES = listOf("bandeja", "recordatorios", "favoritos", "circulo", "ajustes", PREFS)

    /** Borra todo lo guardado: avisos, favoritos, mensajes, ajustes y el circulo (tambien en la nube, si hay internet). */
    suspend fun borrarTodo(ctx: Context) {
        val app = ctx.applicationContext
        try { Circulo.desactivar(app) } catch (e: Exception) { SpikeLog.log("PRIVACIDAD: circulo (${e.message})") }
        try { Recordatorios.listar(app).forEach { Recordatorios.cancelar(app, it.id) } } catch (e: Exception) { SpikeLog.log("PRIVACIDAD: avisos (${e.message})") }
        // El usuario anonimo del circulo tambien se borra del servidor (si no hay internet, al menos se cierra la sesion).
        try { kotlinx.coroutines.withTimeout(10_000) { Firebase.auth.currentUser?.delete()?.aguardar() } } catch (e: Exception) { SpikeLog.log("PRIVACIDAD: usuario (${e.message})") }
        try { Firebase.auth.signOut() } catch (e: Exception) { SpikeLog.log("PRIVACIDAD: sesion (${e.message})") }
        ALMACENES.forEach { app.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
        nube = false
        try { AidlyWidget().updateAll(app); AidlyWidgetCompacto().updateAll(app) } catch (e: Exception) { SpikeLog.log("PRIVACIDAD: widgets (${e.message})") }
        SpikeLog.log("PRIVACIDAD: todo borrado")
    }

    const val EXTRA_MODO = "modo"
    const val MODO_PRIMERA = "primera"      // primer arranque: hay que decidir
    const val MODO_NECESITA = "necesita"    // una funcion pide la nube y esta apagada

    /** Abre la pantalla pidiendo la nube para la funcion que la persona acaba de tocar. */
    fun pedirNube(ctx: Context) =
        ctx.startActivity(Intent(ctx, PrivacidadActivity::class.java).putExtra(EXTRA_MODO, MODO_NECESITA))
}

class PrivacidadActivity : ActividadAidly() {
    override val protegida get() = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val modo = intent.getStringExtra(Privacidad.EXTRA_MODO).orEmpty()
        setContent { AidlyTema { Pantalla(modo, ::finish, ::irAlMenu) } }
    }

    /** Despues de decidir por primera vez se sigue con el menu (que lleva a la bienvenida). */
    private fun irAlMenu() {
        startActivity(Intent(this, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        finish()
    }
}

@Composable
private fun Pantalla(modo: String, cerrar: () -> Unit, irAlMenu: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var nube by remember { mutableStateOf(Privacidad.nubeActiva()) }
    var confirmaBorrar by remember { mutableStateOf(false) }
    var borrado by remember { mutableStateOf(false) }
    val primera = modo == Privacidad.MODO_PRIMERA
    val necesita = modo == Privacidad.MODO_NECESITA

    Column(Modifier.fillMaxSize().background(Tema.Fondo).padding(horizontal = 16.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      // Lo que hay que leer se desplaza; los botones de decidir quedan siempre a la vista, abajo.
      Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (primera) {
            Text(stringResource(R.string.privacidad_titulo), fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Tema.Texto)
        } else {
            EncabezadoPantalla(stringResource(if (necesita) R.string.privacidad_necesita_t else R.string.privacidad_titulo), cerrar)
        }

        if (necesita) Texto(stringResource(R.string.privacidad_necesita), Tema.Texto)

        Bloque(stringResource(R.string.privacidad_local_t), stringResource(R.string.privacidad_local))
        Bloque(stringResource(R.string.privacidad_nube_t), stringResource(R.string.privacidad_nube))
        Bloque(stringResource(R.string.privacidad_familia_t), stringResource(R.string.privacidad_familia))

        Texto(stringResource(R.string.privacidad_sin_nube_nota), Tema.TextoSuave)
        if (!primera && !necesita) {
            InterruptorFila(
                null, stringResource(R.string.privacidad_switch),
                stringResource(if (nube) R.string.privacidad_on else R.string.privacidad_off), nube,
            ) { nube = it; Privacidad.fijarNube(ctx, it) }

            if (borrado) {
                Texto(stringResource(R.string.privacidad_borrado), Tema.Turquesa)
            } else {
                FilaAccion(
                    Iconos.Alerta, stringResource(if (confirmaBorrar) R.string.privacidad_borrar_confirma else R.string.privacidad_borrar),
                    stringResource(R.string.privacidad_borrar_sub), amarilla = confirmaBorrar, chevron = false,
                ) {
                    if (!confirmaBorrar) { confirmaBorrar = true; return@FilaAccion }
                    scope.launch {
                        Privacidad.borrarTodo(ctx)
                        borrado = true
                        ctx.startActivity(Intent(ctx, HomeActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    }
                }
            }
        }
      }
      if (primera || necesita) {
            BotonGrande(stringResource(if (necesita) R.string.privacidad_activar else R.string.privacidad_aceptar), Tema.Turquesa) {
                Privacidad.fijarNube(ctx, true)
                if (primera) irAlMenu() else cerrar()
            }
            BotonGrande(stringResource(if (necesita) R.string.privacidad_ahora_no else R.string.privacidad_solo_telefono), Tema.Tarjeta, Tema.Texto) {
                if (primera) { Privacidad.fijarNube(ctx, false); irAlMenu() } else cerrar()
            }
        }
    }
}

@Composable
private fun Texto(t: String, color: androidx.compose.ui.graphics.Color) = Text(t, fontSize = 19.sp, color = color, lineHeight = 26.sp)

@Composable
private fun Bloque(titulo: String, texto: String) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(Tema.RadioFila)).background(Tema.Tarjeta).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(titulo, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Tema.Turquesa)
        Text(texto, fontSize = 18.sp, color = Tema.Texto, lineHeight = 25.sp)
    }
}
