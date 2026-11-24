package com.aidly.app

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Estados de la cara; se corresponden con lo que esta haciendo Aidly en ese momento. */
enum class Emocion(@androidx.annotation.StringRes val etiqueta: Int, @androidx.annotation.StringRes val descripcion: Int) {
    REPOSO(R.string.cara_reposo, R.string.cara_d_reposo),
    ESCUCHANDO(R.string.cara_escuchando, R.string.cara_d_escuchando),
    PENSANDO(R.string.cara_pensando, R.string.cara_d_pensando),
    HABLANDO(R.string.cara_hablando, R.string.cara_d_hablando),
    CONTENTO(R.string.cara_contento, R.string.cara_d_contento),
    PREOCUPADO(R.string.cara_preocupado, R.string.cara_d_preocupado),
}

private fun DrawScope.ovalo(c: Color, o: Offset, t: Size, pixel: Boolean) = if (pixel) drawRect(c, o, t) else drawOval(c, o, t)

/** Curva cuadratica; en el avatar de pixeles es una quebrada que pasa por el punto mas alto/bajo de la curva. */
private fun curva(pixel: Boolean, x0: Float, y0: Float, cx: Float, cy: Float, x1: Float, y1: Float) = Path().apply {
    moveTo(x0, y0)
    if (pixel) { lineTo(cx, .25f * y0 + .5f * cy + .25f * y1); lineTo(x1, y1) } else quadraticTo(cx, cy, x1, y1)
}

/**
 * La cara de Aidly (rediseno 19-nov). Dibujada en un lienzo de 240x200 como el SVG del diseno.
 * [animar] = false (ajuste "Reducir movimiento" o animaciones del sistema apagadas) deja una pose fija
 * pero conserva lo esencial: el aro al escuchar y la boca abierta al hablar.
 * Las animaciones se leen solo al dibujar, asi que no provocan recomposicion; se detienen al salir de pantalla.
 */
@Composable
fun AidlyCara(
    emocion: Emocion, modifier: Modifier = Modifier, animar: Boolean = true, hablando: Boolean = false,
    /** Que avatar dibujar; por defecto el que eligio la persona. */
    avatar: Avatar = Avatar.deId(Preferencias.avatar(androidx.compose.ui.platform.LocalContext.current)),
) {
    val rasgos = avatar.rasgos
    val px = avatar.pixel
    val tapa = if (px) StrokeCap.Square else StrokeCap.Round
    val descripcionCara = stringResource(R.string.cara_desc, stringResource(emocion.descripcion))
    val t = rememberInfiniteTransition(label = "cara")
    val parpadeoT by t.animateFloat(0f, 1f, infiniteRepeatable(tween(4600, easing = LinearEasing)), label = "parpadeo")
    val miradaT by t.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "mirada")
    val pulsoT by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "pulso")
    val pensarT by t.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2600, easing = CubicBezierEasing(.42f, 0f, .58f, 1f)), RepeatMode.Reverse), label = "pensar",
    )
    val hablarY by t.animateFloat(
        .25f, .25f,
        infiniteRepeatable(keyframes { durationMillis = 900; .25f at 0; 1f at 180; .45f at 360; .9f at 540; .35f at 720; .25f at 900 }),
        label = "hablar",
    )
    val reboteT by t.animateFloat(0f, 1f, infiniteRepeatable(tween(1400, easing = EaseInOut), RepeatMode.Reverse), label = "rebote")

    Canvas(
        modifier.aspectRatio(240f / 200f).semantics { contentDescription = descripcionCara },
    ) {
        val k = size.width / 240f
        scale(k, pivot = Offset.Zero) {
            // --- valores animados de este cuadro ---
            val abiertoY = if (animar) {
                val p = parpadeoT   // parpadeo: ojos cerrados un instante cerca del final del ciclo
                when { p < .92f -> 1f; p < .95f -> 1f - (p - .92f) / .03f * .92f; else -> .08f + (p - .95f) / .05f * .92f }
            } else 1f
            val miradaX = if (animar && emocion == Emocion.REPOSO) {
                val p = miradaT
                when { p < .4f -> 0f; p < .5f -> 10f * (p - .4f) / .1f; p < .7f -> 10f; p < .8f -> 10f - 18f * (p - .7f) / .1f; else -> -8f * (1f - (p - .8f) / .2f) }
            } else 0f
            val miradaYv = if (miradaX > 0f) miradaX * .2f else 0f
            val rebote = if (animar && emocion == Emocion.CONTENTO) -6f * reboteT else 0f
            // Si la voz esta sonando, la boca se mueve en cualquier estado (tambien con "reducir movimiento": es informacion, no adorno).
            val habla = hablando || emocion == Emocion.HABLANDO
            val boca = if (animar || hablando) hablarY else .6f

            // Con adorno (brote, antenas) la cara se encoge al 85% pegada abajo para que el adorno quepa en el lienzo.
            val f = if (avatar.adorno == Adorno.NINGUNO) 1f else .85f
            // --- aro que pulsa al escuchar ---
            if (emocion == Emocion.ESCUCHANDO) {
                val s = if (animar) 1f + .12f * pulsoT else 1.06f
                val alfa = if (animar) .7f * (1f - pulsoT) else .5f
                translate(left = 120f * (1f - f), top = 200f * (1f - f)) { scale(f, pivot = Offset.Zero) {
                    scale(s, pivot = Offset(120f, 100f)) {
                        drawRoundRect(avatar.contorno.copy(alpha = alfa), Offset(24f, 8f), Size(192f, 184f), CornerRadius(avatar.radio), style = Stroke(5f))
                    }
                } }
            }

            translate(left = 120f * (1f - f), top = rebote + 200f * (1f - f)) { scale(f, pivot = Offset.Zero) {
                // orejas y cabeza
                avatar.orejas?.let { c ->
                    val r = if (avatar.pixel) 0f else 10f
                    drawRoundRect(c, Offset(4f, 62f), Size(26f, 76f), CornerRadius(r))
                    drawRoundRect(c, Offset(210f, 62f), Size(26f, 76f), CornerRadius(r))
                }
                when (avatar.adorno) {
                    Adorno.BROTE -> {
                        drawLine(avatar.contorno, Offset(120f, 12f), Offset(120f, -6f), 7f, StrokeCap.Round)
                        for (lado in listOf(-1f, 1f)) {
                            val hoja = Path().apply { moveTo(120f, -4f); quadraticTo(120f + lado * 30f, -34f, 120f + lado * 48f, -8f); quadraticTo(120f + lado * 20f, 6f, 120f, -4f); close() }
                            drawPath(hoja, avatar.contorno)
                        }
                    }
                    Adorno.ANTENAS -> {
                        drawLine(avatar.contorno, Offset(100f, 10f), Offset(70f, -16f), 7f, StrokeCap.Round)
                        drawLine(avatar.contorno, Offset(140f, 10f), Offset(170f, -16f), 7f, StrokeCap.Round)
                        drawCircle(avatar.contorno, 7f, Offset(70f, -16f)); drawCircle(avatar.contorno, 7f, Offset(170f, -16f))
                    }
                    Adorno.NINGUNO -> {}
                }
                drawRoundRect(avatar.relleno, Offset(24f, 8f), Size(192f, 184f), CornerRadius(avatar.radio))
                drawRoundRect(avatar.contorno, Offset(24f, 8f), Size(192f, 184f), CornerRadius(avatar.radio), style = Stroke(12f, join = if (avatar.pixel) StrokeJoin.Miter else StrokeJoin.Round))

                // cejas (preocupado)
                if (emocion == Emocion.PREOCUPADO) {
                    drawLine(rasgos, Offset(70f, 60f), Offset(110f, 46f), 9f, tapa)
                    drawLine(rasgos, Offset(130f, 46f), Offset(170f, 60f), 9f, tapa)
                }

                // ojos
                if (emocion == Emocion.CONTENTO) {
                    for (cx in listOf(90f, 150f)) {
                        drawPath(curva(px, cx - 18f, 96f, cx, 68f, cx + 18f, 96f), rasgos, style = Stroke(10f, cap = tapa))
                    }
                } else {
                    val (dx, dy, esc) = when (emocion) {
                        Emocion.ESCUCHANDO -> Triple(0f, 0f, 1.18f)
                        Emocion.PENSANDO -> {
                            val x = if (animar) -10f - 4f * pensarT else -12f
                            val y = if (animar) -12f - 3f * pensarT else -13f
                            Triple(x, y, 1f)
                        }
                        Emocion.PREOCUPADO -> Triple(0f, 5f, .9f)
                        Emocion.REPOSO -> Triple(miradaX, miradaYv, 1f)
                        else -> Triple(0f, 0f, 1f)
                    }
                    translate(dx, dy) {
                        scale(esc, pivot = Offset(120f, 88f)) {
                            for (cx in listOf(90f, 150f)) {
                                scale(1f, abiertoY, pivot = Offset(cx, 88f)) {
                                    ovalo(rasgos, Offset(cx - 14f, 68f), Size(28f, 40f), px)
                                }
                            }
                        }
                    }
                }

                // boca
                if (habla) scale(1f, boca, pivot = Offset(120f, 150f)) {
                    ovalo(rasgos, Offset(98f, 133f), Size(44f, 34f), px)
                } else when (emocion) {
                    Emocion.REPOSO, Emocion.ESCUCHANDO -> {
                        drawPath(curva(px, 90f, 140f, 120f, 170f, 150f, 140f), rasgos, style = Stroke(10f, cap = tapa))
                    }
                    Emocion.PENSANDO -> {
                        drawPath(curva(px, 100f, 152f, 120f, 146f, 140f, 149f), rasgos, style = Stroke(10f, cap = tapa))
                    }
                    Emocion.HABLANDO -> scale(1f, boca, pivot = Offset(120f, 150f)) {
                        ovalo(rasgos, Offset(98f, 133f), Size(44f, 34f), px)
                    }
                    Emocion.PREOCUPADO -> {
                        drawPath(curva(px, 94f, 160f, 120f, 136f, 146f, 160f), rasgos, style = Stroke(10f, cap = tapa))
                    }
                    Emocion.CONTENTO -> {
                        val p = curva(px, 86f, 138f, 120f, 192f, 154f, 138f).apply { close() }
                        drawPath(p, rasgos)
                        drawPath(p, rasgos, style = Stroke(6f, join = if (px) StrokeJoin.Miter else StrokeJoin.Round))
                    }
                }
            } }
        }
    }
}
