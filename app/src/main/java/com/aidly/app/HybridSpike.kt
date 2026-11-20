package com.aidly.app

import com.google.firebase.Firebase
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.ai
import com.google.firebase.ai.ondevice.FirebaseAIOnDevice
import com.google.firebase.ai.ondevice.OnDeviceModelStatus
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI

/** Spike A. Cambiar CLOUD_MODEL si el modelo ya no existe (ver firebase.google.com/docs/ai-logic/models). */
@OptIn(PublicPreviewAPI::class)
object HybridSpike {
    private const val CLOUD_MODEL = "gemini-3.5-flash-lite" // el mismo que usa la app de ejemplo del hackathon
    private const val PROMPT = "Di en una frase corta: hola, soy Aidly."

    suspend fun nanoStatus(): String = try {
        when (FirebaseAIOnDevice.checkStatus()) {
            OnDeviceModelStatus.AVAILABLE -> "AVAILABLE"
            OnDeviceModelStatus.DOWNLOADABLE -> "DOWNLOADABLE"
            OnDeviceModelStatus.DOWNLOADING -> "DOWNLOADING"
            OnDeviceModelStatus.UNAVAILABLE -> "UNAVAILABLE"
            else -> "DESCONOCIDO"
        }
    } catch (e: Throwable) {
        "ERROR ${e::class.simpleName}: ${e.message}"
    }

    suspend fun promptPreferOnDevice(): String = run("PREFER_ON_DEVICE", InferenceMode.PREFER_ON_DEVICE)

    suspend fun promptOnlyOnDevice(): String = run("ONLY_ON_DEVICE", InferenceMode.ONLY_ON_DEVICE)

    suspend fun promptCloudOnly(): String = run("ONLY_IN_CLOUD", InferenceMode.ONLY_IN_CLOUD)

    private suspend fun run(label: String, mode: InferenceMode): String = try {
        val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
            modelName = CLOUD_MODEL,
            onDeviceConfig = OnDeviceConfig(mode = mode),
        )
        val t0 = System.currentTimeMillis()
        val result = model.generateContent(PROMPT)
        val ms = System.currentTimeMillis() - t0
        "[$label] fuente=${result.inferenceSource} ${ms}ms -> ${result.text}"
    } catch (e: Throwable) {
        "[$label] ERROR ${e::class.simpleName}: ${e.message}"
    }
}
