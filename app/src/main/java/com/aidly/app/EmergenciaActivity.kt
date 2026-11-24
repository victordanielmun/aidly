package com.aidly.app

import android.content.Intent
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** "Mi emergencia": numero de emergencias propio (si no, el del pais) y contacto de confianza al que llamar primero. */
class EmergenciaActivity : ActividadAidly() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AidlyTema {
                Column(
                    Modifier.fillMaxSize().background(Tema.Fondo).padding(horizontal = 16.dp, vertical = 14.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    EncabezadoPantalla(stringResource(R.string.em_titulo)) { finish() }
                    EditorEmergencia()
                }
            }
        }
    }
}

/** Los dos campos con su boton de guardar. Lo usan la pantalla "Mi emergencia" y el paso de la bienvenida. */
@Composable
fun EditorEmergencia(conIntro: Boolean = true, alGuardar: () -> Unit = {}) {
    val ctx = LocalContext.current
    var numero by remember { mutableStateOf(Preferencias.numeroEmergencia(ctx)) }
    val c0 = remember { Preferencias.contactoConfianza(ctx) }
    var nombreC by remember { mutableStateOf(c0?.nombre.orEmpty()) }
    var numeroC by remember { mutableStateOf(c0?.numero.orEmpty()) }
    var error by remember { mutableStateOf(false) }
    var guardado by remember { mutableStateOf(false) }

    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val uri = r.data?.data ?: return@rememberLauncherForActivityResult
        ctx.contentResolver.query(
            uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null,
        )?.use { c -> if (c.moveToFirst()) { nombreC = c.getString(0).orEmpty(); numeroC = c.getString(1).orEmpty(); guardado = false } }
    }
    val colores = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Tema.Texto, unfocusedTextColor = Tema.Texto,
        focusedBorderColor = Tema.Turquesa, unfocusedBorderColor = Tema.TextoSuave,
        focusedLabelColor = Tema.Turquesa, unfocusedLabelColor = Tema.TextoSuave, cursorColor = Tema.Turquesa,
    )
    val estiloTexto = androidx.compose.ui.text.TextStyle(fontSize = 26.sp)

    if (conIntro) Text(stringResource(R.string.em_intro), fontSize = 20.sp, color = Tema.TextoSuave)
    TituloSeccion(stringResource(R.string.em_numero))
    OutlinedTextField(
        value = numero, onValueChange = { numero = it.take(16); guardado = false; error = false }, singleLine = true,
        label = { Text(stringResource(R.string.em_numero_pais, Emergencia.numeroDelPais(ctx)), fontSize = 18.sp) },
        textStyle = estiloTexto, colors = colores, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    )
    TituloSeccion(stringResource(R.string.em_confianza))
    Text(stringResource(R.string.em_confianza_sub), fontSize = 18.sp, color = Tema.TextoSuave)
    OutlinedTextField(
        value = nombreC, onValueChange = { nombreC = it.take(40); guardado = false }, singleLine = true,
        label = { Text(stringResource(R.string.em_nombre), fontSize = 18.sp) }, textStyle = estiloTexto, colors = colores, modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = numeroC, onValueChange = { numeroC = it.take(20); guardado = false; error = false }, singleLine = true,
        label = { Text(stringResource(R.string.em_telefono), fontSize = 18.sp) }, textStyle = estiloTexto, colors = colores, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
    )
    FilaAccion(Iconos.Personas, stringResource(R.string.em_elegir), stringResource(R.string.em_elegir_sub)) {
        elegir.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
    }
    if (error) Text(stringResource(R.string.em_err_numero), fontSize = 20.sp, color = Tema.Coral)
    if (guardado) Text(stringResource(R.string.em_guardado), fontSize = 20.sp, color = Tema.Turquesa)
    BotonGrande(stringResource(R.string.em_guardar), Tema.Turquesa) {
        val n = if (numero.isBlank()) "" else Emergencia.limpiarNumero(numero)
        val c = if (numeroC.isBlank()) "" else Emergencia.limpiarNumero(numeroC)
        if (n == null || c == null) { error = true; guardado = false; return@BotonGrande }
        Preferencias.fijarNumeroEmergencia(ctx, n)
        Preferencias.fijarContactoConfianza(ctx, nombreC.ifBlank { c }, c)
        error = false; guardado = true; alGuardar()
    }
    if (numero.isNotBlank() || numeroC.isNotBlank()) {
        TextButton(onClick = {
            numero = ""; nombreC = ""; numeroC = ""
            Preferencias.fijarNumeroEmergencia(ctx, ""); Preferencias.fijarContactoConfianza(ctx, "", "")
            guardado = false; error = false
        }) { Text(stringResource(R.string.em_quitar), fontSize = 18.sp, color = Tema.TextoSuave) }
    }
}
