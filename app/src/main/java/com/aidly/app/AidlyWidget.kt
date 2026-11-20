package com.aidly.app

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * Widgets de la pantalla de inicio (rediseno 19-nov). Mismos colores y textos que el diseno; la fuente es la del
 * sistema (Glance/RemoteViews no admite tipografias propias).
 *  - Grande (4x3): Hablar + Papel / Medicina / Ayuda.
 *  - Compacto (4x1): Hablar + Ayuda.
 */
class AidlyWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) { provideContent { Grande() } }
}

class AidlyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AidlyWidget()
}

class AidlyWidgetCompacto : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) { provideContent { Compacto() } }
}

class AidlyWidgetCompactoReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AidlyWidgetCompacto()
}

private val FONDO = Color(0xFF2B2B26)
private val CREMA = Color(0xFFFFF3DC)
private val TURQUESA = Color(0xFF40DDDA)
private val AMARILLO = Color(0xFFFFE000)
private val CORAL = Color(0xFFFF7F50)
private val TINTA = Color(0xFF1F1F1B)

private fun pantalla(ctx: Context, c: Class<*>) = actionStartActivity(Intent(ctx, c))

@Composable
private fun Grande() {
    val ctx = LocalContext.current
    val txt = Idioma.envolver(ctx)   // los widgets no pasan por ActividadAidly: se aplica el idioma aqui
    Column(
        modifier = GlanceModifier.fillMaxSize().background(ColorProvider(FONDO)).cornerRadius(28.dp).padding(12.dp),
    ) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Image(ImageProvider(R.drawable.mascota), contentDescription = "Aidly", modifier = GlanceModifier.size(36.dp))
            Spacer(GlanceModifier.width(8.dp))
            Text("Aidly", style = TextStyle(color = ColorProvider(CREMA), fontSize = 24.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth().height(84.dp).background(ColorProvider(TURQUESA)).cornerRadius(28.dp)
                .padding(horizontal = 14.dp).clickable(pantalla(ctx, HablarActivity::class.java)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(ImageProvider(R.drawable.ic_w_mic), contentDescription = null, modifier = GlanceModifier.size(56.dp))
            Spacer(GlanceModifier.width(12.dp))
            Text(txt.getString(R.string.home_hablar), style = TextStyle(color = ColorProvider(TINTA), fontSize = 23.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            Boton(txt.getString(R.string.widget_papel), R.drawable.ic_w_doc, AMARILLO, GlanceModifier.defaultWeight(), pantalla(ctx, AnalizaActivity::class.java))
            Spacer(GlanceModifier.width(8.dp))
            Boton(txt.getString(R.string.widget_medicina), R.drawable.ic_w_pill, AMARILLO, GlanceModifier.defaultWeight(), pantalla(ctx, MedicinaActivity::class.java))
            Spacer(GlanceModifier.width(8.dp))
            Boton(txt.getString(R.string.widget_ayuda), R.drawable.ic_w_alert, CORAL, GlanceModifier.defaultWeight(), pantalla(ctx, SosActivity::class.java))
        }
    }
}

@Composable
private fun Compacto() {
    val ctx = LocalContext.current
    val txt = Idioma.envolver(ctx)
    Row(
        modifier = GlanceModifier.fillMaxSize().background(ColorProvider(FONDO)).cornerRadius(28.dp).padding(10.dp),
    ) {
        Row(
            modifier = GlanceModifier.defaultWeight().fillMaxHeight().background(ColorProvider(TURQUESA)).cornerRadius(26.dp)
                .padding(horizontal = 12.dp).clickable(pantalla(ctx, HablarActivity::class.java)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(ImageProvider(R.drawable.ic_w_mic), contentDescription = null, modifier = GlanceModifier.size(52.dp))
            Spacer(GlanceModifier.width(10.dp))
            Text(txt.getString(R.string.home_hablar), style = TextStyle(color = ColorProvider(TINTA), fontSize = 20.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.width(8.dp))
        Boton(txt.getString(R.string.widget_ayuda), R.drawable.ic_w_alert, CORAL, GlanceModifier.width(92.dp).fillMaxHeight(), pantalla(ctx, SosActivity::class.java))
    }
}

/** Baldosa cuadrada con icono y texto, de un solo color. */
@Composable
private fun Boton(texto: String, icono: Int, fondo: Color, modificador: GlanceModifier, accion: androidx.glance.action.Action) {
    Column(
        modifier = modificador.fillMaxHeight().background(ColorProvider(fondo)).cornerRadius(24.dp).clickable(accion),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(ImageProvider(icono), contentDescription = null, modifier = GlanceModifier.size(30.dp))
        Spacer(GlanceModifier.height(4.dp))
        Text(texto, style = TextStyle(color = ColorProvider(TINTA), fontSize = 17.sp, fontWeight = FontWeight.Bold))
    }
}
