package com.aidly.app

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Quien produjo una respuesta: el modelo del telefono, la nube de Google o las reglas del telefono. */
enum class Motor(@StringRes val etiqueta: Int, val color: Color) {
    TELEFONO(R.string.motor_telefono, Tema.Turquesa),
    NUBE(R.string.motor_nube, Tema.Amarillo),
    REGLAS(R.string.motor_reglas, Tema.Texto);

    val icono: ImageVector get() = when (this) { TELEFONO -> Iconos.Celular; NUBE -> Iconos.Nube; REGLAS -> Iconos.Reglas }

    companion object {
        /** `fuente` es lo que devuelven Agente y los validadores: "local" (reglas), "web", o `inferenceSource` ("ON_DEVICE" / "IN_CLOUD"). */
        fun de(fuente: String): Motor {
            val f = fuente.lowercase(Locale.ROOT)
            return when {
                "device" in f -> TELEFONO
                "cloud" in f || "nube" in f || f == "web" -> NUBE
                else -> REGLAS
            }
        }
    }
}

/** Insignia visible con el motor de cada respuesta (criterio de la inferencia hibrida: se ve de donde vino). */
@Composable
fun InsigniaMotor(fuente: String, ms: Long = 0, modifier: Modifier = Modifier) {
    val m = Motor.de(fuente)
    Row(
        modifier.border(2.dp, m.color, RoundedCornerShape(24.dp)).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconoAidly(m.icono, m.color, 22.dp)
        Row(Modifier.width(8.dp)) {}
        Text(stringResource(m.etiqueta), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = m.color)
        if (ms > 0) Text("  ·  " + String.format(Locale.US, "%.1f s", ms / 1000.0), fontSize = 18.sp, color = Tema.TextoSuave)
    }
}

/** "alta" / "media" / "baja" (los valores fijos del esquema de la IA) en el idioma de la pantalla. */
@Composable
fun confianzaTexto(valor: String): String = when (valor.lowercase(Locale.ROOT)) {
    "alta", "high" -> stringResource(R.string.conf_alta)
    "media", "medium" -> stringResource(R.string.conf_media)
    else -> stringResource(R.string.conf_baja)
}
