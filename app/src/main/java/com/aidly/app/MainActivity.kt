package com.aidly.app

import android.Manifest
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.firebase.ai.ai
import kotlinx.coroutines.launch

/** Spikes A (hibrido), B (servicio con microfono + voz) y C (notificaciones). */
class MainActivity : ActividadAidly() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AidlyTema { SpikeScreen() } }
        // Prueba sin voz: adb shell am start -n com.aidly.app/.MainActivity --es frase "leeme el correo"
        // (solo registra la respuesta en el log; no abre otras apps).
        // Opcionales: --es sembrar "Sam|Hola" (mensaje) / --es sembrarCorreo "Banco|Asunto|Vista previa" (a la bandeja),
        //             --ez ejecutar true (ejecuta la accion, p. ej. abrir la app, tras la respuesta).
        intent.getStringExtra("sembrar")?.split("|")?.let { p ->
            Bandeja.agregar(this, Bandeja.Item("t${System.nanoTime()}", Bandeja.Tipo.MENSAJE, "Prueba", p[0], p.getOrElse(1) { "" }, "", System.currentTimeMillis(), false))
        }
        intent.getStringExtra("sembrarCorreo")?.split("|")?.let { p ->
            Bandeja.agregar(this, Bandeja.Item("t${System.nanoTime()}", Bandeja.Tipo.CORREO, "Prueba", p[0], p.getOrElse(1) { "" }, p.getOrElse(2) { "" }, System.currentTimeMillis(), false))
        }
        // --es recordatorio "tomar la pastilla|2|med|0" = texto | minutos desde ahora | med/gen | cada N horas (opcional)
        intent.getStringExtra("recordatorio")?.split("|")?.let { p ->
            val item = Recordatorios.Item(
                java.util.UUID.randomUUID().toString(), p[0],
                if (p.getOrElse(2) { "gen" } == "med") Recordatorios.Tipo.MEDICINA else Recordatorios.Tipo.GENERAL,
                System.currentTimeMillis() + p.getOrElse(1) { "1" }.toLong() * 60_000,
                p.getOrElse(3) { "0" }.toLong() * 60,
            )
            Recordatorios.agregar(this, item)
            SpikeLog.log("RECORDATORIO prueba: '${item.texto}' ${Recordatorios.describir(item)}")
        }
        // --es circulo "Nombre" crea el circulo familiar; --ez borrarCirculo true lo borra; --ez sincronizar true fuerza el envio.
        intent.getStringExtra("circulo")?.let { nom ->
            lifecycleScope.launch {
                try { SpikeLog.log("CIRCULO prueba: codigo=" + Circulo.activar(this@MainActivity, nom)) }
                catch (e: Exception) { SpikeLog.log("CIRCULO prueba: error ${e::class.simpleName}: ${e.message}") }
            }
        }
        if (intent.getBooleanExtra("borrarCirculo", false)) lifecycleScope.launch { Circulo.desactivar(this@MainActivity) }
        if (intent.getBooleanExtra("sincronizar", false)) lifecycleScope.launch {
            try { Circulo.sincronizar(this@MainActivity); SpikeLog.log("CIRCULO prueba: sincronizado") }
            catch (e: Exception) { SpikeLog.log("CIRCULO prueba: error ${e::class.simpleName}: ${e.message}") }
        }
        // --es abrir "SosActivity" abre esa pantalla (las demas no estan exportadas y adb no puede abrirlas directo).
        intent.getStringExtra("abrir")?.let { n ->
            try { startActivity(Intent(this, Class.forName("com.aidly.app.$n")).putExtras(intent)) } catch (e: Exception) { SpikeLog.log("abrir $n: ${e.message}") }
        }
        // --ez reiniciarBienvenida true: vuelve a mostrar la bienvenida en el proximo arranque (sin borrar datos).
        if (intent.getBooleanExtra("reiniciarBienvenida", false)) Preferencias.fijarBienvenidaHecha(this, false)
        if (intent.hasExtra("soloNube")) Interprete.soloNube = intent.getBooleanExtra("soloNube", false)
        // --es audio "a.wav|b.wav": para cada WAV de files/audio/ (copiado con run-as) registra lo que oyo Gemini
        // y lo que hizo el agente. Sirve para probar voz sin microfono ni persona (audios de Qwen3-TTS).
        intent.getStringExtra("audio")?.split("|")?.let { nombres ->
            lifecycleScope.launch {
                for (n in nombres.map { it.trim() }.filter { it.isNotEmpty() }) {
                    try {
                        // Carpeta privada (run-as) o, si el dispositivo no permite run-as, la carpeta externa de la app (adb push).
                        val privado = java.io.File(filesDir, "audio/$n")
                        val externo = java.io.File(getExternalFilesDir(null), "audio/$n")
                        val bytes = (if (privado.exists()) privado else externo).readBytes()
                        val oido = GeminiStt.transcribirWav(this@MainActivity, bytes, (bytes.size - 44) / 32)
                        SpikeLog.log("AUDIO $n oido='${oido ?: "(silencio)"}'")
                        if (oido != null) {
                            val r = Agente.procesar(this@MainActivity, oido)
                            SpikeLog.log("AUDIO $n -> [${r.fuente}${if (r.despues != null) ", abre app" else ""}] ${r.texto.take(220)}")
                        }
                    } catch (e: Exception) {
                        SpikeLog.log("AUDIO $n error ${e::class.simpleName}: ${e.message?.take(120)}")
                    }
                }
                SpikeLog.log("AUDIO fin")
            }
        }
        // --es parseLocal "frase1|frase2|...": prueba el interprete local de recordatorios (sin guardar nada).
        intent.getStringExtra("parseLocal")?.split("|")?.forEach { f ->
            val it = if (Idioma.resolver(this) == "en") ParserLocalEn.recordatorio(f) else ParserLocal.recordatorio(f)
            SpikeLog.log("PARSER '$f' -> " + (it?.let { r -> "[${r.tipo}] '${r.texto}' ${Recordatorios.describir(r)} (cada ${r.cadaMin} min)" } ?: "no entendido (iria al modelo)"))
        }
        // --es probarModelos "a,b,c": consulta cada modelo con una frase trivial y registra si responde y cuanto tarda.
        intent.getStringExtra("probarModelos")?.split(",")?.let { lista ->
            lifecycleScope.launch {
                for (nombre in lista.map { it.trim() }.filter { it.isNotEmpty() }) {
                    val t0 = System.currentTimeMillis()
                    try {
                        @OptIn(com.google.firebase.ai.type.PublicPreviewAPI::class)
                        val m = com.google.firebase.Firebase.ai(backend = com.google.firebase.ai.type.GenerativeBackend.googleAI()).generativeModel(nombre)
                        val r = kotlinx.coroutines.withTimeout(30_000) { m.generateContent("Responde solo con la palabra: hola") }
                        SpikeLog.log("MODELO $nombre OK ${System.currentTimeMillis() - t0} ms -> ${r.text?.trim()?.take(40)}")
                    } catch (e: Exception) {
                        SpikeLog.log("MODELO $nombre FALLA ${System.currentTimeMillis() - t0} ms ${e::class.simpleName}: ${e.message?.take(90)}")
                    }
                }
            }
        }
        // --ez cifrado true: informa si lo guardado esta cifrado (solo mira el prefijo; no escribe el contenido en el registro).
        if (intent.getBooleanExtra("cifrado", false)) {
            listOf("recordatorios" to listOf("items", "historial"), "bandeja" to listOf("items"), "favoritos" to listOf("lista"), "circulo" to listOf("codigo", "nombre")).forEach { (a, claves) ->
                val p = getSharedPreferences(a, MODE_PRIVATE)
                claves.forEach { k -> p.getString(k, null)?.let { SpikeLog.log("CIFRADO $a.$k: " + (if (Cifrado.existe(it)) "cifrado" else "EN CLARO") + " (${it.length} car.)") } }
            }
        }
        // --es idioma es|en|auto: cambia el idioma de la app y de la voz para las pruebas.
        intent.getStringExtra("idioma")?.let { Idioma.fijar(this, if (it == "auto") "" else it) }
        // --ez nube true|false: enciende o apaga la nube de Privacidad (las pruebas con voz la necesitan encendida).
        if (intent.hasExtra("nube")) Privacidad.fijarNube(this, intent.getBooleanExtra("nube", false))
        intent.getStringExtra("frase")?.let { f ->
            lifecycleScope.launch {
                val r = try { Agente.procesar(this@MainActivity, f) } catch (e: Exception) {
                    SpikeLog.log("AGENTE error ${e::class.simpleName}: ${e.message}"); return@launch
                }
                SpikeLog.log("AGENTE respuesta [${r.fuente}${if (r.despues != null) ", abriria app" else ""}]: ${r.texto}")
                if (intent.getBooleanExtra("ejecutar", false)) r.despues?.invoke()
            }
        }
    }
}

@Composable
private fun SpikeScreen() {
    val ctx = LocalContext.current
    val lines by SpikeLog.lines.collectAsState()
    val scope = rememberCoroutineScope()

    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        SpikeLog.log("Permisos: $r")
        if (r[Manifest.permission.RECORD_AUDIO] == true) AidlyService.start(ctx)
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Aidly - Spikes", style = MaterialTheme.typography.headlineSmall)
        Text("A. Hibrido", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { scope.launch { SpikeLog.log("Estado Nano: " + HybridSpike.nanoStatus()) } }) {
            Text("1. Estado de Gemini Nano")
        }
        Button(onClick = { scope.launch { SpikeLog.log(HybridSpike.promptPreferOnDevice()) } }) {
            Text("2. Prompt PREFER_ON_DEVICE")
        }
        Button(onClick = { scope.launch { SpikeLog.log(HybridSpike.promptCloudOnly()) } }) {
            Text("3. Prompt ONLY_IN_CLOUD")
        }
        Button(onClick = { scope.launch { SpikeLog.log(HybridSpike.promptOnlyOnDevice()) } }) {
            Text("4. Prompt ONLY_ON_DEVICE")
        }

        Text("B. Servicio con microfono", style = MaterialTheme.typography.titleMedium)
        Button(onClick = {
            perms.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.POST_NOTIFICATIONS))
        }) { Text("5. Activar Companero Aidly") }
        Button(onClick = {
            AidlyService.start(ctx, AidlyService.ACTION_SAY, text = "Buenos dias. Ya desayunaste?")
        }) { Text("6. Decir saludo") }
        Button(onClick = { AidlyService.start(ctx, AidlyService.ACTION_LISTEN) }) {
            Text("7. Escuchar")
        }
        Button(onClick = { AidlyService.start(ctx, AidlyService.ACTION_STOP) }) {
            Text("8. Detener servicio")
        }

        Button(onClick = { scope.launch { SttCompare.run(ctx) } }) {
            Text("10. Comparar transcripcion (5 s)")
        }

        Text("E. Cara animada", style = MaterialTheme.typography.titleMedium)
        var emocion by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(
                runCatching { Emocion.valueOf((ctx as android.app.Activity).intent.getStringExtra("cara") ?: "REPOSO") }.getOrDefault(Emocion.REPOSO),
            )
        }
        var quieta by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
        androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth().background(Tema.Fondo).padding(vertical = 12.dp)) {
            AidlyCara(emocion, Modifier.fillMaxWidth().padding(horizontal = 40.dp), animar = !quieta)
        }
        Text("${emocion.name}: ${androidx.compose.ui.res.stringResource(emocion.etiqueta)}")
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Emocion.values().take(3).forEach { e -> Button(onClick = { emocion = e }) { Text(e.name.take(5)) } }
        }
        androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Emocion.values().drop(3).forEach { e -> Button(onClick = { emocion = e }) { Text(e.name.take(5)) } }
            Button(onClick = { quieta = !quieta }) { Text(if (quieta) "movil" else "quieta") }
        }

        Text("D. Medicina", style = MaterialTheme.typography.titleMedium)
        Button(onClick = { ctx.startActivity(Intent(ctx, MedicinaActivity::class.java)) }) {
            Text("11. Leer medicina (camara)")
        }

        Text("C. Notificaciones", style = MaterialTheme.typography.titleMedium)
        Button(onClick = {
            ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }) { Text("9. Dar acceso a notificaciones") }

        Text(lines.joinToString("\n"))
    }
}
