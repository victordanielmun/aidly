package com.aidly.app

import android.Manifest
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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

/** Pilar 2: leer una caja de medicamento con la camara, con guia por voz y foto automatica. */
class MedicinaActivity : ActividadAidly() {
    override val protegida get() = true

    private lateinit var voz: Voz

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voz = Voz(this)
        // Leer la foto necesita la nube de Google: si esta apagada, se explica y se ofrece encenderla.
        if (!Privacidad.nubeActiva()) { Privacidad.pedirNube(this); finish(); return }
        setContent { AidlyTema { MedicinaScreen(voz) } }
    }

    override fun onDestroy() {
        voz.cerrar()
        super.onDestroy()
    }
}

private enum class Fase { APUNTANDO, LEYENDO, RESULTADO }

private const val INTRO = "Apunta la camara a la caja de tu medicina. Tomare la foto en cinco segundos."
private const val DESCARGO = "Esto es lo que dice la caja. Confirma siempre con tu medico o farmaceutico antes de tomarla."

@Composable
private fun MedicinaScreen(voz: Voz) {
    val ctx = LocalContext.current
    val owner = ctx as LifecycleOwner
    val scope = rememberCoroutineScope()

    var permiso by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val pedirPermiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permiso = it }
    var fase by remember { mutableStateOf(Fase.APUNTANDO) }
    var intento by remember { mutableIntStateOf(0) }
    var cuenta by remember { mutableIntStateOf(0) }
    var res by remember { mutableStateOf<MedicinaValidador.Resultado?>(null) }
    var aviso by remember { mutableStateOf("") }
    var preguntaMed by remember { mutableStateOf("") }
    var respuestaMed by remember { mutableStateOf("") }
    var ocupadoMed by remember { mutableStateOf(false) }
    var quierePreguntar by remember { mutableIntStateOf(0) }
    val pedirMic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) quierePreguntar++ }

    val controller = remember {
        LifecycleCameraController(ctx).apply { setEnabledUseCases(CameraController.IMAGE_CAPTURE) }
    }
    LaunchedEffect(Unit) { if (!permiso) pedirPermiso.launch(Manifest.permission.CAMERA) }
    DisposableEffect(permiso) {
        if (permiso) controller.bindToLifecycle(owner)
        onDispose { controller.unbind() }
    }

    fun hablar(r: MedicinaValidador.Resultado) {
        voz.decir(r.resumenVoz.ifBlank { "Es ${r.nombre}." })
        voz.decir(DESCARGO)
    }

    fun reintentar(@androidx.annotation.StringRes id: Int) {
        aviso = ctx.getString(id)
        voz.decir(Idioma.vozTexto(ctx, id))
        fase = Fase.APUNTANDO
        intento++
    }

    fun tomarFoto() {
        if (fase != Fase.APUNTANDO) return
        fase = Fase.LEYENDO
        cuenta = 0
        aviso = ""
        voz.decir("Un momento, estoy leyendo.")
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
                            val r = withContext(Dispatchers.Default) { MedicinaValidador.analizar(foto) }
                            res = r
                            if (r.confiable) { fase = Fase.RESULTADO; hablar(r) }
                            else reintentar(R.string.med_err_no_leo)
                        } catch (e: Exception) {
                            SpikeLog.log("MEDICINA: error ${e::class.simpleName}: ${e.message}")
                            reintentar(R.string.med_err_problema)
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    SpikeLog.log("MEDICINA: error de camara ${exception.message}")
                    reintentar(R.string.cam_err_foto)
                }
            },
        )
    }

    // Duda sobre la medicina identificada: escucha la pregunta, la responde (con reglas de seguridad) y la lee en voz alta.
    fun preguntar() {
        val r = res ?: return
        if (ocupadoMed) return
        ocupadoMed = true; preguntaMed = ""; respuestaMed = ""
        voz.callar()
        scope.launch {
            try {
                kotlinx.coroutines.withTimeoutOrNull(15_000) { voz.decirYEsperar("Dime tu pregunta.") }
                val q = GeminiStt.escuchar()
                if (q == null) {
                    respuestaMed = ctx.getString(R.string.hablar_error_escuchar)
                } else {
                    preguntaMed = q
                    respuestaMed = withContext(Dispatchers.Default) { MedicinaValidador.preguntar(ctx, r, q) }
                }
            } catch (e: Exception) {
                SpikeLog.log("MEDICINA: error en la pregunta ${e::class.simpleName}: ${e.message}")
                respuestaMed = ctx.getString(R.string.hablar_error_responder)
            }
            voz.decir(respuestaMed)
            ocupadoMed = false
        }
    }
    LaunchedEffect(quierePreguntar) { if (quierePreguntar > 0) preguntar() }

    // Guia por voz y cuenta atras; la foto se toma sola (tambien hay un boton grande).
    LaunchedEffect(permiso, intento) {
        if (!permiso) return@LaunchedEffect
        if (intento == 0) voz.decir(INTRO)
        delay(if (intento == 0) 5500 else 3500)
        for (i in 5 downTo 1) {
            if (fase != Fase.APUNTANDO) return@LaunchedEffect
            cuenta = i
            delay(1000)
        }
        cuenta = 0
        tomarFoto()
    }

    Box(Modifier.fillMaxSize()) {
        if (!permiso) {
            Text(stringResource(R.string.med_permiso_camara), Modifier.padding(24.dp), fontSize = 22.sp)
        } else if (fase != Fase.RESULTADO) {
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
                        fase == Fase.LEYENDO -> stringResource(R.string.med_leyendo)
                        cuenta > 0 -> stringResource(R.string.cam_foto_en, cuenta)
                        else -> stringResource(R.string.med_apunta)
                    },
                    fontSize = 34.sp, fontWeight = FontWeight.Bold,
                )
                if (fase == Fase.LEYENDO) CircularProgressIndicator()
                if (aviso.isNotBlank()) Text(aviso, fontSize = 22.sp)
                Button(
                    onClick = { tomarFoto() },
                    enabled = fase == Fase.APUNTANDO,
                    modifier = Modifier.fillMaxWidth().height(96.dp),
                ) { Text(stringResource(R.string.cam_tomar_ahora), fontSize = 26.sp) }
            }
        } else {
            res?.let { r ->
                Column(
                    Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(r.nombre, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                    listOf(
                        stringResource(R.string.med_principio) to r.principio,
                        stringResource(R.string.med_concentracion) to r.concentracion,
                        stringResource(R.string.med_forma) to r.forma,
                        stringResource(R.string.med_dice_caja) to r.indicaciones,
                        stringResource(R.string.med_advertencias) to r.advertencias,
                    ).filter { it.second.isNotBlank() }.forEach { (k, v) ->
                        Text("$k: $v", fontSize = 24.sp)
                    }
                    Text(stringResource(R.string.med_confirma), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.cam_lectura, r.confianza, r.fuente, r.ms), fontSize = 14.sp)
                    Text(stringResource(R.string.med_duda), fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) quierePreguntar++
                            else pedirMic.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        enabled = !ocupadoMed,
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                    ) { Text(stringResource(if (ocupadoMed) R.string.med_te_escucho else R.string.med_preguntar), fontSize = 24.sp) }
                    if (preguntaMed.isNotBlank()) Text(stringResource(R.string.hablar_tu, preguntaMed), fontSize = 20.sp)
                    if (respuestaMed.isNotBlank()) Text(respuestaMed, fontSize = 24.sp, fontWeight = FontWeight.Medium)
                    Button(
                        onClick = { hablar(r) },
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                    ) { Text(stringResource(R.string.cam_escuchar_otra), fontSize = 24.sp) }
                    Button(
                        onClick = { voz.callar(); res = null; aviso = ""; fase = Fase.APUNTANDO; intento++ },
                        modifier = Modifier.fillMaxWidth().height(80.dp),
                    ) { Text(stringResource(R.string.med_otra_caja), fontSize = 24.sp) }
                }
            }
        }
    }
}
