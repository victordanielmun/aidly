package com.aidly.app

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Sistema visual de Aidly (rediseno de 19-nov-2026). Todos los colores y medidas viven aqui.
 * Calido y sin deslumbrar: fondo gris oscuro, texto crema, tres acentos. Contrastes medidos >= 6,6:1.
 */
object Tema {
    val Fondo = Color(0xFF2B2B26)
    val Tarjeta = Color(0xFF383832)
    val Texto = Color(0xFFFFF3DC)
    val TextoSuave = Color(0xFFCFC9BA)
    val Turquesa = Color(0xFF40DDDA)
    val Amarillo = Color(0xFFFFE000)
    val Coral = Color(0xFFFF7F50)
    /** Tinta oscura: texto sobre los botones de color. */
    val Oscuro = Color(0xFF1F1F1B)
    val CaraFondo = Color(0xFF2F4A40)

    val RadioFila = 24.dp
    val RadioPrincipal = 36.dp
    val RadioBoton = 28.dp

    val Atkinson = FontFamily(
        Font(R.font.atkinson_regular, FontWeight.Normal),
        Font(R.font.atkinson_bold, FontWeight.Bold),
    )
}

/**
 * Tipografia Atkinson Hyperlegible (pensada para baja vision) y altura de linea que escala con la letra:
 * Material fija 20-24 sp y las lineas se pisan con letra grande.
 */
@Composable
fun AidlyTema(contenido: @Composable () -> Unit) {
    val base = Typography()
    fun TextStyle.a() = copy(fontFamily = Tema.Atkinson, lineHeight = TextUnit.Unspecified)
    val tipografia = Typography(
        displayLarge = base.displayLarge.a(), displayMedium = base.displayMedium.a(), displaySmall = base.displaySmall.a(),
        headlineLarge = base.headlineLarge.a(), headlineMedium = base.headlineMedium.a(), headlineSmall = base.headlineSmall.a(),
        titleLarge = base.titleLarge.a(), titleMedium = base.titleMedium.a(), titleSmall = base.titleSmall.a(),
        bodyLarge = base.bodyLarge.a(), bodyMedium = base.bodyMedium.a(), bodySmall = base.bodySmall.a(),
        labelLarge = base.labelLarge.a(), labelMedium = base.labelMedium.a(), labelSmall = base.labelSmall.a(),
    )
    // Barras del sistema del mismo color del fondo: la pantalla se ve continua.
    val ctx = LocalContext.current
    SideEffect {
        (ctx as? Activity)?.window?.let {
            it.statusBarColor = Tema.Fondo.toArgb()
            it.navigationBarColor = Tema.Fondo.toArgb()
        }
    }
    val colores = darkColorScheme(
        primary = Tema.Turquesa, onPrimary = Tema.Oscuro, secondary = Tema.Amarillo, onSecondary = Tema.Oscuro,
        tertiary = Tema.Coral, onTertiary = Tema.Oscuro, background = Tema.Fondo, onBackground = Tema.Texto,
        surface = Tema.Tarjeta, onSurface = Tema.Texto, surfaceVariant = Tema.Tarjeta, onSurfaceVariant = Tema.TextoSuave,
        outline = Tema.TextoSuave, error = Tema.Coral,
    )
    MaterialTheme(colorScheme = colores, typography = tipografia) {
        // Fondo y color de texto por defecto: sin esto, un Text sin color sale negro sobre el fondo oscuro.
        Surface(modifier = Modifier.fillMaxSize(), color = Tema.Fondo, contentColor = Tema.Texto, content = contenido)
    }
}

@Composable
fun Mascota(tam: Dp = 160.dp, modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.mascota), contentDescription = "Aidly", modifier = modifier.size(tam))
}

/** Boton grande de una sola accion (pantallas secundarias). Crece si la letra del sistema es grande. */
@Composable
fun BotonGrande(
    texto: String,
    fondo: Color,
    letra: Color = Tema.Oscuro,
    alto: Dp = 80.dp,
    tamLetra: Int = 24,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().heightIn(min = alto),
        shape = RoundedCornerShape(Tema.RadioBoton),
        colors = ButtonDefaults.buttonColors(containerColor = fondo, contentColor = letra),
    ) { Text(texto, fontSize = tamLetra.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
}
