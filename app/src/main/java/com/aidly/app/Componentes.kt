package com.aidly.app

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Componentes del rediseno (tablero "Inicio"). Alturas minimas: crecen si el usuario sube el tamano de letra. */

@Composable
fun IconoAidly(icono: ImageVector, color: Color, tam: Dp = 30.dp, modifier: Modifier = Modifier) {
    Icon(icono, contentDescription = null, modifier = modifier.size(tam), tint = color)
}

/** Mascota + "Aidly" y el boton de Ajustes. */
@Composable
fun CabeceraAidly(avisoPermisos: Boolean = false, onAjustes: () -> Unit) {
    val descripcionAjustes = stringResource(if (avisoPermisos) R.string.cd_ajustes_faltan else R.string.cd_ajustes)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Mascota(52.dp)
            Spacer(Modifier.width(12.dp))
            Text("Aidly", fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Tema.Texto)
        }
        Box {
            Box(
                Modifier.size(52.dp).clip(CircleShape).border(2.dp, Tema.TextoSuave, CircleShape)
                    .semantics { contentDescription = descripcionAjustes }
                    .clickable(role = Role.Button, onClick = onAjustes),
                contentAlignment = Alignment.Center,
            ) { IconoAidly(Iconos.Ajustes, Tema.Texto, 26.dp) }
            // Punto coral: faltan permisos que conviene dar en Ajustes.
            if (avisoPermisos) Box(Modifier.align(Alignment.TopEnd).size(16.dp).clip(CircleShape).background(Tema.Coral))
        }
    }
}

/** La accion principal: enorme, turquesa, con el microfono. */
@Composable
fun TarjetaHablar(titulo: String, subtitulo: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 104.dp).clip(RoundedCornerShape(Tema.RadioPrincipal))
            .background(Tema.Turquesa).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(Tema.Oscuro), contentAlignment = Alignment.Center) {
            IconoAidly(Iconos.Microfono, Tema.Turquesa, 34.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(titulo, fontSize = 27.sp, fontWeight = FontWeight.Bold, color = Tema.Oscuro)
            Text(subtitulo, fontSize = 18.sp, color = Tema.Oscuro)
        }
    }
}

/** Fila de accion secundaria: contorno turquesa sobre tarjeta, o relleno amarillo (acciones con la camara). */
@Composable
fun FilaAccion(icono: ImageVector, titulo: String, subtitulo: String, amarilla: Boolean = false, chevron: Boolean = true, onClick: () -> Unit) {
    val forma = RoundedCornerShape(Tema.RadioFila)
    val fondo = if (amarilla) Tema.Amarillo else Tema.Tarjeta
    val tinta = if (amarilla) Tema.Oscuro else Tema.Texto
    val suave = if (amarilla) Tema.Oscuro else Tema.TextoSuave
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clip(forma).background(fondo)
            .border(2.dp, if (amarilla) Tema.Amarillo else Tema.Turquesa, forma)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconoAidly(icono, if (amarilla) Tema.Oscuro else Tema.Turquesa, 34.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(titulo, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = tinta)
            Text(subtitulo, fontSize = 17.sp, color = suave)
        }
        if (chevron) IconoAidly(Iconos.ChevronDerecha, if (amarilla) Tema.Oscuro else Tema.TextoSuave, 26.dp)
    }
}

/** Fila con interruptor grande (el interruptor de Material es demasiado pequeno para manos cansadas). */
@Composable
fun InterruptorFila(icono: ImageVector?, titulo: String, estado: String, activo: Boolean, alCambiar: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(Tema.RadioFila)).background(Tema.Tarjeta)
            .toggleable(value = activo, role = Role.Switch, onValueChange = alCambiar)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icono != null) { IconoAidly(icono, Tema.Turquesa, 30.dp); Spacer(Modifier.width(14.dp)) }
        Column(Modifier.weight(1f)) {
            Text(titulo, fontSize = 19.sp, fontWeight = FontWeight.Bold, color = Tema.Texto)
            if (estado.isNotBlank()) Text(estado, fontSize = 16.sp, color = Tema.TextoSuave)
        }
        Spacer(Modifier.width(12.dp))
        val borde = if (activo) Tema.Turquesa else Tema.TextoSuave
        val x by animateDpAsState(if (activo) 26.dp else 0.dp, label = "interruptor")
        Box(
            Modifier.size(60.dp, 34.dp).clip(RoundedCornerShape(17.dp))
                .background(if (activo) Tema.Turquesa else Color.Transparent)
                .border(3.dp, borde, RoundedCornerShape(17.dp)),
        ) {
            Box(
                Modifier.offset(x = x).padding(3.dp).size(22.dp).clip(CircleShape)
                    .background(if (activo) Tema.Oscuro else Tema.TextoSuave),
            )
        }
    }
}

/** "Pedir ayuda": siempre abajo y de otro color. Icono de alerta (primero avisa a la familia, luego ofrece llamar). */
@Composable
fun PildoraAyuda(texto: String = stringResource(R.string.home_ayuda), onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 76.dp).clip(RoundedCornerShape(38.dp)).background(Tema.Coral)
            .clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconoAidly(Iconos.Alerta, Tema.Oscuro, 34.dp)
        Spacer(Modifier.width(14.dp))
        Text(texto, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = Tema.Oscuro)
    }
}

/** Fila "‹ Volver" y el titulo grande de la pantalla; comun a todas las pantallas secundarias. */
@Composable
fun EncabezadoPantalla(titulo: String, volver: () -> Unit) {
    Row(
        Modifier.heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp)).clickable(role = Role.Button, onClick = volver)
            .padding(end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconoAidly(Iconos.ChevronIzquierda, Tema.Texto, 30.dp)
        Text(stringResource(R.string.volver), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Tema.Texto)
    }
    Text(titulo, fontSize = 38.sp, fontWeight = FontWeight.Bold, color = Tema.Texto, modifier = Modifier.semantics { heading() })
}

/** Encabezado de seccion dentro de una pantalla. */
@Composable
fun TituloSeccion(texto: String) {
    Text(texto, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Tema.Turquesa, modifier = Modifier.padding(top = 6.dp).semantics { heading() })
}

@Composable
fun EspacioV(alto: Dp) = Spacer(Modifier.height(alto))
