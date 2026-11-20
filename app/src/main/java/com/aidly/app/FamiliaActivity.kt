package com.aidly.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * Mi familia: llamar o escribir a hasta 4 contactos favoritos (se eligen con el selector del sistema, sin dar
 * permiso de contactos) y, aparte, "Como estoy": compartir con la familia lo que la persona decida.
 */
class FamiliaActivity : ActividadAidly() {
    override val protegida get() = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AidlyTema { Pantalla { finish() } } }
    }
}

@Composable
private fun Pantalla(cerrar: () -> Unit) {
    val ctx = LocalContext.current
    var favoritos by remember { mutableStateOf(Favoritos.lista(ctx)) }
    var codigo by remember { mutableStateOf(Circulo.ajustes(ctx)) }
    var aviso by remember { mutableStateOf("") }

    LifecycleResumeEffect(Unit) {
        favoritos = Favoritos.lista(ctx); codigo = Circulo.ajustes(ctx)
        onPauseOrDispose {}
    }

    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data ?: return@rememberLauncherForActivityResult
        ctx.contentResolver.query(
            uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null,
        )?.use { c ->
            if (c.moveToFirst()) {
                val ok = Favoritos.agregar(ctx, Favoritos.Favorito(c.getString(0).orEmpty(), c.getString(1).orEmpty()))
                aviso = if (ok) "" else ctx.getString(R.string.familia_error_dup, Favoritos.MAX)
                favoritos = Favoritos.lista(ctx)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(Tema.Fondo).padding(horizontal = 16.dp, vertical = 14.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EncabezadoPantalla(stringResource(R.string.home_familia), cerrar)

        TituloSeccion(stringResource(R.string.home_familia_sub))
        if (favoritos.isEmpty()) {
            Text(stringResource(R.string.familia_elige), fontSize = 20.sp, color = Tema.TextoSuave)
        }
        favoritos.forEach { f ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(Tema.RadioFila)).background(Tema.Tarjeta).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(f.nombre, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Tema.Texto)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BotonContacto(stringResource(R.string.familia_llamar), Iconos.Telefono, Tema.Turquesa, Modifier.weight(1f)) {
                        ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", f.numero, null)))
                    }
                    BotonContacto(stringResource(R.string.familia_whatsapp), Iconos.Mensaje, Tema.Amarillo, Modifier.weight(1f)) { abrirWhatsApp(ctx, f.numero) }
                }
                TextButton(onClick = { Favoritos.quitar(ctx, f.numero); favoritos = Favoritos.lista(ctx) }) {
                    Text(stringResource(R.string.familia_quitar), fontSize = 16.sp, color = Tema.TextoSuave)
                }
            }
        }
        if (favoritos.size < Favoritos.MAX) {
            FilaAccion(Iconos.Personas, stringResource(R.string.familia_agregar), stringResource(R.string.familia_agregar_sub)) {
                elegir.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
            }
        }
        if (aviso.isNotBlank()) Text(aviso, fontSize = 18.sp, color = Tema.Coral)

        TituloSeccion(stringResource(R.string.familia_como_estoy))
        FilaAccion(
            Iconos.Corazon, stringResource(R.string.familia_compartir),
            if (codigo.activo) stringResource(R.string.familia_codigo, codigo.codigoBonito) else stringResource(R.string.familia_ver),
        ) { ctx.startActivity(Intent(ctx, CirculoActivity::class.java)) }
    }
}

@Composable
private fun BotonContacto(texto: String, icono: ImageVector, fondo: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(Tema.RadioFila)).background(fondo)
            .clickable(role = Role.Button, onClickLabel = texto, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        IconoAidly(icono, Tema.Oscuro, 28.dp)
        Spacer(Modifier.width(8.dp))
        Text(texto, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Tema.Oscuro)
    }
}

private fun abrirWhatsApp(ctx: android.content.Context, numero: String) {
    val url = Uri.parse("https://wa.me/${Prefijo.parawhatsapp(ctx, numero)}")
    try {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, url).setPackage("com.whatsapp"))
    } catch (e: Exception) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, url)) } catch (_: Exception) {}
    }
}
