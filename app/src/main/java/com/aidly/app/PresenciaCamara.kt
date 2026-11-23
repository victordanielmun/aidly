package com.aidly.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facedetector.FaceDetector
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import java.util.concurrent.Executors

/**
 * Presencia de la persona con la camara frontal, TODO en el telefono (MediaPipe: detector de caras y pose ligera).
 * No hay vista previa, no se guarda ningun cuadro y nada sale del aparato: de cada cuadro solo se queda "se ve / no se ve" y
 * donde esta (de -1 a 1 en horizontal, positivo = hacia la derecha de quien mira la pantalla).
 */
class PresenciaCamara(private val ctx: Context) : AutoCloseable {
    class Observacion(val ms: Long, val visible: Boolean, val cara: Boolean, val x: Float)

    private val ejecutor = Executors.newSingleThreadExecutor()
    private var proveedor: ProcessCameraProvider? = null
    private var analisis: ImageAnalysis? = null
    @Volatile private var ultimo = 0L
    @Volatile private var cerrado = false

    private val caras: FaceDetector = FaceDetector.createFromOptions(
        ctx,
        FaceDetector.FaceDetectorOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("mediapipe/blaze_face_short_range.tflite").build())
            .setRunningMode(RunningMode.IMAGE).setMinDetectionConfidence(0.5f).build(),
    )
    private val poses: PoseLandmarker = PoseLandmarker.createFromOptions(
        ctx,
        PoseLandmarker.PoseLandmarkerOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath("mediapipe/pose_landmarker_lite.task").build())
            .setRunningMode(RunningMode.IMAGE).setNumPoses(1)
            .setMinPoseDetectionConfidence(0.5f).setMinPosePresenceConfidence(0.5f).build(),
    )

    /** Analiza un cuadro (ya derecho). La cara tiene prioridad; la pose solo se calcula si no hay cara (ahorra bateria). */
    fun analizar(bmp: Bitmap, ms: Long): Observacion {
        val img = BitmapImageBuilder(bmp).build()
        val dets = caras.detect(img).detections()
        if (dets.isNotEmpty()) {
            val b = dets[0].boundingBox()
            // La camara frontal no esta espejada en el analisis: se invierte para que + sea la derecha de quien mira la pantalla.
            val cx = (b.left + b.right) / 2f / bmp.width
            return Observacion(ms, true, true, -(cx - 0.5f) * 2f)
        }
        val lm = poses.detect(img).landmarks()
        if (lm.isNotEmpty() && lm[0].isNotEmpty()) {
            val cx = lm[0].map { it.x() }.average().toFloat()
            return Observacion(ms, true, false, -(cx - 0.5f) * 2f)
        }
        return Observacion(ms, false, false, 0f)
    }

    /**
     * Activa la camara frontal y llama a [alObservar] unas 6 veces por segundo (en un hilo propio). Solo `ImageAnalysis`, sin vista previa:
     * se enlaza directo con `ProcessCameraProvider` (con `LifecycleCameraController` y sin `PreviewView`, CameraX nunca abria la camara).
     */
    fun iniciar(owner: LifecycleOwner, alObservar: (Observacion) -> Unit) {
        val futuro = ProcessCameraProvider.getInstance(ctx)
        val principal = ContextCompat.getMainExecutor(ctx)
        futuro.addListener({
            try {
                if (cerrado) return@addListener
                val p = futuro.get()
                val a = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                a.setAnalyzer(ejecutor) { proxy -> procesar(proxy, alObservar) }
                p.unbindAll()
                p.bindToLifecycle(owner, CameraSelector.DEFAULT_FRONT_CAMERA, a)
                proveedor = p; analisis = a
                SpikeLog.log("PRESENCIA: camara frontal enlazada")
            } catch (e: Throwable) {
                SpikeLog.log("PRESENCIA: no pude enlazar la camara (${e::class.simpleName}: ${e.message})")
            }
        }, principal)
    }

    // Solo depuracion (SpikeLog es silencioso en la version final): cada 5 s, cuadros analizados por segundo y milisegundos por analisis.
    private var cuadros = 0
    private var sumaMs = 0L
    private var desde = 0L
    private fun medir(ms: Long) {
        val ahora = System.currentTimeMillis()
        if (desde == 0L) desde = ahora
        cuadros++; sumaMs += ms
        if (ahora - desde >= 5_000) {
            SpikeLog.log("PRESENCIA: %.1f cuadros/s, %d ms por analisis".format(cuadros * 1000f / (ahora - desde), sumaMs / cuadros))
            cuadros = 0; sumaMs = 0; desde = ahora
        }
    }

    private fun procesar(proxy: ImageProxy, alObservar: (Observacion) -> Unit) {
        try {
            val ahora = System.currentTimeMillis()
            if (cerrado || ahora - ultimo < 150) return
            ultimo = ahora
            val girado = proxy.toBitmap().let { b ->
                val r = proxy.imageInfo.rotationDegrees
                if (r == 0) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(r.toFloat()) }, true)
            }
            val t0 = System.nanoTime()
            alObservar(analizar(girado, ahora))
            medir((System.nanoTime() - t0) / 1_000_000)
        } catch (e: Throwable) {
            SpikeLog.log("PRESENCIA: fallo al analizar (${e::class.simpleName}: ${e.message})")
        } finally {
            proxy.close()
        }
    }

    fun detener() {
        val p = proveedor; val a = analisis
        proveedor = null; analisis = null
        if (p != null && a != null) ContextCompat.getMainExecutor(ctx).execute { try { p.unbind(a) } catch (_: Throwable) {} }
    }

    override fun close() {
        cerrado = true
        detener()
        ejecutor.shutdown()
        try { caras.close(); poses.close() } catch (_: Throwable) {}
    }
}
