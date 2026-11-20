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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
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

/**
 * La cara de Aidly (rediseno 19-nov). Dibujada en un lienzo de 240x200 como el SVG del diseno.
 * [animar] = false (ajuste "Reducir movimiento" o animaciones del sistema apagadas) deja una pose fija
 * pero conserva lo esencial: el aro al escuchar y la boca abierta al hablar.
 * Las animaciones se leen solo al dibujar, asi que no provocan recomposicion; se detienen al salir de pantalla.
 */
@Composable
fun AidlyCara(emocion: Emocion, modifier: Modifier = Modifier, animar: Boolean = true) {
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
            val boca = if (animar) hablarY else .6f

            // --- aro que pulsa al escuchar ---
            if (emocion == Emocion.ESCUCHANDO) {
                val s = if (animar) 1f + .12f * pulsoT else 1.06f
                val alfa = if (animar) .7f * (1f - pulsoT) else .5f
                scale(s, pivot = Offset(120f, 100f)) {
                    drawRoundRect(Tema.Turquesa.copy(alpha = alfa), Offset(24f, 8f), Size(192f, 184f), CornerRadius(60f), style = Stroke(5f))
                }
            }

            translate(top = rebote) {
                // orejas y cabeza
                drawRoundRect(Tema.Amarillo, Offset(4f, 62f), Size(26f, 76f), CornerRadius(10f))
                drawRoundRect(Tema.Amarillo, Offset(210f, 62f), Size(26f, 76f), CornerRadius(10f))
                drawRoundRect(Tema.CaraFondo, Offset(24f, 8f), Size(192f, 184f), CornerRadius(60f))
                drawRoundRect(Tema.Turquesa, Offset(24f, 8f), Size(192f, 184f), CornerRadius(60f), style = Stroke(12f))

                // cejas (preocupado)
                if (emocion == Emocion.PREOCUPADO) {
                    drawLine(Tema.Amarillo, Offset(70f, 60f), Offset(110f, 46f), 9f, StrokeCap.Round)
                    drawLine(Tema.Amarillo, Offset(130f, 46f), Offset(170f, 60f), 9f, StrokeCap.Round)
                }

                // ojos
                if (emocion == Emocion.CONTENTO) {
                    for (cx in listOf(90f, 150f)) {
                        val p = Path().apply { moveTo(cx - 18f, 96f); quadraticTo(cx, 68f, cx + 18f, 96f) }
                        drawPath(p, Tema.Amarillo, style = Stroke(10f, cap = StrokeCap.Round))
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
                                    drawOval(Tema.Amarillo, Offset(cx - 14f, 68f), Size(28f, 40f))
                                }
                            }
                        }
                    }
                }

                // boca
                when (emocion) {
                    Emocion.REPOSO, Emocion.ESCUCHANDO -> {
                        val p = Path().apply { moveTo(90f, 140f); quadraticTo(120f, 170f, 150f, 140f) }
                        drawPath(p, Tema.Amarillo, style = Stroke(10f, cap = StrokeCap.Round))
                    }
                    Emocion.PENSANDO -> {
                        val p = Path().apply { moveTo(100f, 152f); quadraticTo(120f, 146f, 140f, 149f) }
                        drawPath(p, Tema.Amarillo, style = Stroke(10f, cap = StrokeCap.Round))
                    }
                    Emocion.HABLANDO -> scale(1f, boca, pivot = Offset(120f, 150f)) {
                        drawOval(Tema.Amarillo, Offset(98f, 133f), Size(44f, 34f))
                    }
                    Emocion.PREOCUPADO -> {
                        val p = Path().apply { moveTo(94f, 160f); quadraticTo(120f, 136f, 146f, 160f) }
                        drawPath(p, Tema.Amarillo, style = Stroke(10f, cap = StrokeCap.Round))
                    }
                    Emocion.CONTENTO -> {
                        val p = Path().apply { moveTo(86f, 138f); quadraticTo(120f, 192f, 154f, 138f); close() }
                        drawPath(p, Tema.Amarillo)
                        drawPath(p, Tema.Amarillo, style = Stroke(6f, join = StrokeJoin.Round))
                    }
                }
            }
        }
    }
}
