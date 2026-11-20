package com.aidly.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** "Analiza": foto de un recibo, carta o mensaje sospechoso; Aidly lo explica en voz alta y avisa de estafas. */
class AnalizaActivity : ActividadAidly() {
    override val protegida get() = true

    private lateinit var voz: Voz

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voz = Voz(this)
        // Leer la foto necesita la nube de Google: si esta apagada, se explica y se ofrece encenderla.
        if (!Privacidad.nubeActiva()) { Privacidad.pedirNube(this); finish(); return }
        setContent { AidlyTema { AnalizaScreen(voz) { finish() } } }
    }

    override fun onDestroy() {
        voz.cerrar()
        super.onDestroy()
    }
}

private enum class FaseA { APUNTANDO, LEYENDO, RESULTADO }

private const val INTRO_A =
    "Apunta la camara a lo que quieres que lea: un frasco, una carta, un cartel o un mensaje. Tomare la foto en cinco segundos."
private const val DESCARGO_A =
    "Soy una ayuda, no un experto. Ante la duda, pregunta a un familiar de confianza antes de pagar o dar datos."

@Composable
private fun AnalizaScreen(voz: Voz, cerrar: () -> Unit) {
    val ctx = LocalContext.current
    val owner = ctx as LifecycleOwner
    val scope = rememberCoroutineScope()

    var permiso by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permiso = it }
    var fase by remember { mutableStateOf(FaseA.APUNTANDO) }
    var intento by remember { mutableIntStateOf(0) }
    var cuenta by remember { mutableIntStateOf(0) }
    var res by remember { mutableStateOf<AnalizaValidador.Resultado?>(null) }
    var aviso by remember { mutableStateOf("") }

    val controller = remember {
        LifecycleCameraController(ctx).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE) }
    }
    LaunchedEffect(Unit) { if (!permiso) pedirPermiso.launch(Manifest.permission.CAMERA) }
    DisposableEffect(permiso) {
        if (permiso) controller.bindToLifecycle(owner)
        onDispose { controller.unbind() }
    }

    fun hablar(r: AnalizaValidador.Resultado) {
        voz.callar()
        if (r.riesgo == "alto") voz.decir("Cuidado. Esto tiene señales de estafa.")
        voz.decir(r.resumenVoz)
        if (r.senales.isNotBlank() && r.riesgo != "no_aplica" && r.riesgo != "bajo") voz.decir("Lo que me preocupa: ${r.senales}")
        if (r.queHacer.isNotBlank()) voz.decir("Te recomiendo: ${r.queHacer}")
        voz.decir(DESCARGO_A)
    }

    fun reintentar(@androidx.annotation.StringRes id: Int) {
        aviso = ctx.getString(id)
        voz.decir(Idioma.vozTexto(ctx, id))
        fase = FaseA.APUNTANDO
        intento++
    }

    fun tomarFoto() {
        if (fase != FaseA.APUNTANDO) return
        fase = FaseA.LEYENDO
        cuenta = 0
        aviso = ""
        voz.decir("Un momento, lo estoy revisando.")
        controller.takePicture(
            ContextCompat.getMainExecutor(ctx),
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(image: ImageProxy) {
                    val foto = try {
                        MedicinaValidador.rotar(image.toBitmap(), image.imageInfo.rotationDegrees)
                    } finally {
                        image.close()
                    }
                    scope.launch {
                        try {
                            val r = withContext(Dispatchers.Default) { AnalizaValidador.analizar(foto) }
                            res = r
                            if (r.confiable) { fase = FaseA.RESULTADO; hablar(r) }
                            else reintentar(R.string.ana_err_no_leo)
                        } catch (e: Exception) {
                            SpikeLog.log("ANALIZA: error ${e::class.simpleName}: ${e.message}")
                            reintentar(R.string.ana_err_problema)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    SpikeLog.log("ANALIZA: error de camara ${exception.message}")
                    reintentar(R.string.cam_err_foto)
                }
            },
        )
    }

    LaunchedEffect(permiso, intento) {
        if (!permiso) return@LaunchedEffect
        if (intento == 0) voz.decir(INTRO_A)
        delay(if (intento == 0) 6500 else 3500)
        for (i in 5 downTo 1) {
            if (fase != FaseA.APUNTANDO) return@LaunchedEffect
            cuenta = i
            delay(1000)
        }
        cuenta = 0
        tomarFoto()
    }

    Box(Modifier.fillMaxSize().background(Tema.Fondo)) {
        if (!permiso) {
            Text(stringResource(R.string.ana_permiso), Modifier.padding(24.dp), fontSize = 24.sp, color = Tema.Texto)
        } else if (fase != FaseA.RESULTADO) {
            AndroidView(
                factory = { PreviewView(it).apply { this.controller = controller } },
                modifier = Modifier.fillMaxSize(),
            )
            Column(
                Modifier.fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    when {
                        fase == FaseA.LEYENDO -> stringResource(R.string.ana_revisando)
                        cuenta > 0 -> stringResource(R.string.cam_foto_en, cuenta)
                        else -> stringResource(R.string.ana_apunta)
                    },
                    fontSize = 34.sp, fontWeight = FontWeight.Bold, color = Tema.Amarillo,
                )
                if (fase == FaseA.LEYENDO) CircularProgressIndicator(color = Tema.Amarillo)
                if (aviso.isNotBlank()) Text(aviso, fontSize = 22.sp, color = Tema.Texto)
                BotonGrande(stringResource(R.string.cam_tomar_ahora), Tema.Turquesa) { tomarFoto() }
            }
        } else {
            res?.let { r ->
                val color = when (r.riesgo) { "alto" -> Tema.Coral; "medio" -> Tema.Amarillo; else -> Tema.Turquesa }
                val titulo = when (r.riesgo) {
                    "alto" -> stringResource(R.string.ana_riesgo_alto)
                    "medio" -> stringResource(R.string.ana_riesgo_medio)
                    "bajo" -> stringResource(R.string.ana_riesgo_bajo)
                    else -> tituloTipo(r.tipo)
                }
                Column(
                    Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        titulo,
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(color).padding(16.dp),
                        fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Tema.Oscuro,
                    )
                    Text(r.resumenVoz, fontSize = 26.sp, color = Tema.Texto, fontWeight = FontWeight.Medium)
                    r.datos.forEach { Text("• $it", fontSize = 24.sp, color = Tema.Texto) }
                    if (r.senales.isNotBlank() && r.riesgo != "no_aplica") {
                        Text(stringResource(R.string.ana_preocupa, r.senales), fontSize = 22.sp, color = Tema.Coral)
                    }
                    if (r.queHacer.isNotBlank()) {
                        Text(stringResource(R.string.ana_recomiendo, r.queHacer), fontSize = 24.sp, color = Tema.Amarillo, fontWeight = FontWeight.Bold)
                    }
                    Text(stringResource(R.string.ana_descargo), fontSize = 18.sp, color = Tema.TextoSuave)
                    Text(stringResource(R.string.cam_lectura, r.confianza, r.fuente, r.ms), fontSize = 13.sp, color = Tema.TextoSuave)
                    if (r.esMedicina) {
                        BotonGrande(stringResource(R.string.ana_leer_medicina), Tema.Amarillo) {
                            voz.callar(); ctx.startActivity(Intent(ctx, MedicinaActivity::class.java)); cerrar()
                        }
                    }
                    BotonGrande(stringResource(R.string.cam_escuchar_otra), Tema.Turquesa) { hablar(r) }
                    BotonGrande(stringResource(R.string.ana_otro), Tema.Amarillo) {
                        voz.callar(); res = null; aviso = ""; fase = FaseA.APUNTANDO; intento++
                    }
                    BotonGrande(stringResource(R.string.volver_mayus), Tema.TextoSuave, alto = 72.dp) { voz.callar(); cerrar() }
                }
            }
        }
    }
}

@Composable
private fun tituloTipo(tipo: String): String = when (tipo) {
    "recibo" -> stringResource(R.string.ana_t_recibo)
    "cobro_o_factura" -> stringResource(R.string.ana_t_cobro)
    "carta_o_documento" -> stringResource(R.string.ana_t_carta)
    "mensaje_o_captura" -> stringResource(R.string.ana_t_mensaje)
    "medicina" -> stringResource(R.string.ana_t_medicina)
    "etiqueta_o_cartel" -> stringResource(R.string.ana_t_etiqueta)
    else -> stringResource(R.string.ana_t_otro)
}
