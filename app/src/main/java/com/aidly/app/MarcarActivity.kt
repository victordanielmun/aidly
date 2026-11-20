package com.aidly.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.HapticFeedbackConstants
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * "Marcar un numero": teclado enorme con la voz de Aidly diciendo cada digito, marcacion rapida con los favoritos
 * de "Mi familia" y boton "Llamar". Abre el marcador del sistema con el numero escrito (la persona toca el boton
 * verde; Aidly nunca llama sola y no necesita el permiso de llamadas). Mantener el 0 escribe "+".
 */
class MarcarActivity : ActividadAidly() {
    private lateinit var voz: Voz

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voz = Voz(this)
        setContent { AidlyTema { Pantalla(voz) { finish() } } }
    }

    override fun onDestroy() {
        voz.cerrar()
        super.onDestroy()
    }
}

private const val MAX_DIGITOS = 16

/** Solo los digitos de un numero, para comparar "+57 300 123 4567" con "3001234567". */
private fun solo(numero: String) = numero.filter { it.isDigit() }.takeLast(10)

@Composable
private fun Pantalla(voz: Voz, cerrar: () -> Unit) {
    val ctx = LocalContext.current
    val vista = LocalView.current
    var numero by remember { mutableStateOf("") }
    val favoritos = remember { Favoritos.lista(ctx) }
    val nombre = favoritos.firstOrNull { numero.length >= 7 && solo(it.numero) == solo(numero) }?.nombre

    fun tocar() { vista.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
    fun escribir(c: String, decir: String = c) {
        if (numero.length >= MAX_DIGITOS) return
        numero += c; tocar(); voz.callar(); voz.decir(decir)
    }
    fun borrar() { if (numero.isNotEmpty()) { numero = numero.dropLast(1); tocar(); voz.callar() } }
    fun oir() {
        voz.callar()
        voz.decir(if (numero.isEmpty()) Idioma.vozTexto(ctx, R.string.marcar_vacio) else numero.toList().joinToString(", "))
    }
    fun llamar() {
        if (numero.isEmpty()) { oir(); return }
        voz.callar()
        try { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", numero, null))) }
        catch (e: Exception) {
            SpikeLog.log("MARCAR: no pude abrir el marcador (${e::class.simpleName})")
            voz.decir(Idioma.vozTexto(ctx, R.string.marcar_sin_telefono))
        }
    }

    Column(Modifier.fillMaxSize().background(Tema.Fondo).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EncabezadoPantalla(stringResource(R.string.marcar_titulo), cerrar)

            // Pantalla del numero: tocarla lo lee en voz alta; "Borrar" quita el ultimo digito.
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Tema.RadioFila)).background(Tema.Tarjeta).padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = stringResource(R.string.marcar_oir)) { oir() }) {
                    Text(
                        numero.ifEmpty { stringResource(R.string.marcar_vacio) },
                        fontSize = if (numero.isEmpty()) 24.sp else 44.sp, fontWeight = FontWeight.Bold,
                        color = if (numero.isEmpty()) Tema.TextoSuave else Tema.Texto,
                    )
                    Text(nombre.orEmpty().ifEmpty { stringResource(R.string.marcar_oir) }, fontSize = 18.sp, color = if (nombre != null) Tema.Turquesa else Tema.TextoSuave)
                }
                Box(
                    Modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(Tema.RadioFila)).background(Tema.Fondo)
                        .clickable(role = Role.Button, enabled = numero.isNotEmpty()) { borrar() }.padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(stringResource(R.string.marcar_borrar), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = if (numero.isEmpty()) Tema.TextoSuave else Tema.Amarillo) }
            }

            // Marcacion rapida: un toque pone el numero del favorito.
            if (favoritos.isNotEmpty()) {
                TituloSeccion(stringResource(R.string.marcar_rapida))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    favoritos.forEach { f ->
                        Box(
                            Modifier.weight(1f).heightIn(min = 64.dp).clip(RoundedCornerShape(Tema.RadioFila)).background(Tema.Tarjeta)
                                .border(2.dp, Tema.Amarillo, RoundedCornerShape(Tema.RadioFila))
                                .clickable(role = Role.Button) {
                                    numero = f.numero.filter { it.isDigit() || it == '+' }.take(MAX_DIGITOS); tocar()
                                    voz.callar(); voz.decir(Idioma.vozTexto(ctx, R.string.marcar_llamar_a, f.nombre))
                                }.padding(6.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(f.nombre, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Tema.Texto, textAlign = TextAlign.Center, maxLines = 2) }
                    }
                }
            }

            // Teclado 4 x 3. El 0 con pulsacion larga escribe "+".
            val filas = listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("*", "0", "#"))
            filas.forEach { fila ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    fila.forEach { t ->
                        val dicho = when (t) { "*" -> Idioma.vozTexto(ctx, R.string.marcar_asterisco); "#" -> Idioma.vozTexto(ctx, R.string.marcar_numeral); else -> t }
                        Tecla(t, Modifier.weight(1f), if (t == "0") stringResource(R.string.marcar_cero_mas) else null,
                            onLargo = if (t == "0") { { escribir("+", Idioma.vozTexto(ctx, R.string.marcar_mas)) } } else null,
                        ) { escribir(t, dicho) }
                    }
                }
            }
        }
        Box(Modifier.padding(top = 10.dp)) {
            BotonGrande(
                stringResource(R.string.marcar_llamar), if (numero.isEmpty()) Tema.Tarjeta else Tema.Turquesa,
                if (numero.isEmpty()) Tema.TextoSuave else Tema.Oscuro, alto = 76.dp,
            ) { llamar() }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tecla(texto: String, modifier: Modifier, pista: String?, onLargo: (() -> Unit)?, onClick: () -> Unit) {
    val forma = RoundedCornerShape(Tema.RadioFila)
    Column(
        modifier.heightIn(min = 76.dp).fillMaxHeight().clip(forma).background(Tema.Tarjeta).border(2.dp, Tema.Turquesa, forma)
            .combinedClickable(role = Role.Button, onClick = onClick, onLongClick = onLargo)
            .semantics { contentDescription = texto },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        Text(texto, fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Tema.Texto)
        if (pista != null) Text(pista, fontSize = 14.sp, color = Tema.TextoSuave, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp))
    }
}
