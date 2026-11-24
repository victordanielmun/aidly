package com.aidly.app

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Adornos de la cabeza de un avatar. */
enum class Adorno { NINGUNO, BROTE, ANTENAS }

/**
 * Los 6 avatares: la misma cara (mismas 6 emociones y la misma boca que habla) con otros colores y forma.
 * [radio] = redondez de la cabeza; [pixel] = trazos rectos y ojos cuadrados; [orejas] = null si no lleva.
 */
enum class Avatar(
    val id: String, @StringRes val nombre: Int, val contorno: Color, val relleno: Color, val rasgos: Color, val orejas: Color?,
    val radio: Float = 60f, val pixel: Boolean = false, val adorno: Adorno = Adorno.NINGUNO,
) {
    CLASICO("clasico", R.string.av_clasico, Tema.Turquesa, Tema.CaraFondo, Tema.Amarillo, Tema.Amarillo),
    BROTE("brote", R.string.av_brote, Color(0xFF7BD389), Color(0xFF263D2C), Color(0xFFFFF3DC), null, radio = 92f, adorno = Adorno.BROTE),
    PIXEL("pixel", R.string.av_pixel, Color(0xFF40DDDA), Color(0xFF1F2A36), Color(0xFFFFE000), Color(0xFFFFE000), radio = 0f, pixel = true),
    ROSA("rosa", R.string.av_rosa, Color(0xFFF48FB1), Color(0xFF4A2E3B), Color(0xFFFFF3DC), Color(0xFFF48FB1)),
    TURQUESA_SUAVE("turquesa", R.string.av_turquesa, Color(0xFF8FD8D0), Color(0xFF2B4443), Color(0xFFFFF3DC), null),
    TELE("tele", R.string.av_tele, Color(0xFFFFB454), Color(0xFF22262B), Color(0xFF40DDDA), null, radio = 26f, adorno = Adorno.ANTENAS),
    ;

    companion object {
        fun deId(id: String?): Avatar = entries.firstOrNull { it.id == id } ?: CLASICO
    }
}

/** Cuadricula de los 6 avatares; al tocar uno se guarda como el elegido. Se usa en la bienvenida y en Ajustes. */
@androidx.compose.runtime.Composable
fun SelectorAvatar(modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier, alElegir: (Avatar) -> Unit = {}) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    var actual by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Avatar.deId(Preferencias.avatar(ctx))) }
    androidx.compose.foundation.layout.Column(modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
        Avatar.entries.chunked(2).forEach { fila ->
            androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp)) {
                fila.forEach { av ->
                    val elegido = av == actual
                    val nombre = androidx.compose.ui.res.stringResource(av.nombre)
                    val etiqueta = if (elegido) androidx.compose.ui.res.stringResource(R.string.av_elegido, nombre) else nombre
                    androidx.compose.foundation.layout.Column(
                        Modifier.weight(1f)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(Tema.RadioFila))
                            .background(Tema.Tarjeta)
                            .border(if (elegido) 4.dp else 1.dp, if (elegido) Tema.Amarillo else Tema.TextoSuave.copy(alpha = .4f), androidx.compose.foundation.shape.RoundedCornerShape(Tema.RadioFila))
                            .selectable(selected = elegido, role = androidx.compose.ui.semantics.Role.RadioButton) {
                                actual = av; Preferencias.fijarAvatar(ctx, av.id); alElegir(av)
                            }
                            .semantics { contentDescription = etiqueta }
                            .padding(10.dp),
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                    ) {
                        AidlyCara(Emocion.REPOSO, Modifier.fillMaxWidth(), animar = false, avatar = av)
                        androidx.compose.material3.Text(nombre, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, color = if (elegido) Tema.Amarillo else Tema.Texto)
                    }
                }
            }
        }
    }
}
