package com.aidly.app

import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.InferenceMode
import com.google.firebase.ai.OnDeviceConfig
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Convierte una frase hablada en datos estructurados (recordatorios, citas) con el modelo hibrido:
 * primero el del telefono (PREFER_ON_DEVICE: son peticiones cortas y no salen del aparato si hay Gemini Nano)
 * y, si falla o la respuesta no es valida, la nube (ONLY_IN_CLOUD). La fecha y la hora las calcula el codigo
 * (nunca el modelo) y se validan.
 */
@OptIn(PublicPreviewAPI::class)
object Interprete {
    private const val CLOUD_MODEL = "gemini-3.5-flash-lite"
    private val modelos = HashMap<String, GenerativeModel>()

    /** Solo para medir: salta el intento local y va directo a la nube. */
    @Volatile var soloNube = false

    @Synchronized
    private fun modelo(clave: String, modo: InferenceMode, esquema: Schema): GenerativeModel =
        modelos.getOrPut(clave) {
            Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                modelName = CLOUD_MODEL,
                onDeviceConfig = OnDeviceConfig(mode = modo),
                generationConfig = generationConfig {
                    responseMimeType = "application/json"
                    responseSchema = esquema
                    temperature = 0f
                    // Extraer hora y texto de una frase corta no necesita razonar: menos latencia en la voz.
                    thinkingConfig = com.google.firebase.ai.type.thinkingConfig {
                        thinkingLevel = com.google.firebase.ai.type.ThinkingLevel.MINIMAL
                    }
                },
            )
        }

    /** [convertir] devuelve null si el JSON no sirve; entonces se prueba con la nube. */
    suspend fun <T : Any> pedir(etiqueta: String, prompt: String, esquema: Schema, convertir: (JSONObject) -> T?): T? {
        // Nube apagada en Privacidad: solo el modelo del telefono (si no hay, el llamador usa el interprete local).
        val intentos = if (!Privacidad.nubeActiva()) listOf("local" to InferenceMode.ONLY_ON_DEVICE)
        else if (soloNube) listOf("nube" to InferenceMode.ONLY_IN_CLOUD)
        else listOf("hibrido" to InferenceMode.PREFER_ON_DEVICE, "nube" to InferenceMode.ONLY_IN_CLOUD)
        for ((nombre, modo) in intentos) {
            val r = try {
                val t0 = System.currentTimeMillis()
                val m = modelo("$etiqueta-$nombre", modo, esquema)
                val resp = withTimeout(30_000) { m.generateContent(prompt) }
                val crudo = resp.text.orEmpty()
                SpikeLog.log("INTERPRETE $etiqueta [$nombre/${resp.inferenceSource}] ${System.currentTimeMillis() - t0} ms json=${crudo.take(300)}")
                convertir(JSONObject("{" + crudo.substringAfter("{", "").substringBeforeLast("}") + "}"))
            } catch (e: Exception) {
                SpikeLog.log("INTERPRETE $etiqueta: fallo $nombre (${e::class.simpleName}: ${e.message})"); null
            }
            if (r != null) return r
        }
        return null
    }

    /** "Ahora es jueves 19 de noviembre de 2026, 14:35 (zona America/Mexico_City)" para el prompt. */
    fun contexto(ahora: Long): String =
        SimpleDateFormat("EEEE d 'de' MMMM 'de' yyyy, HH:mm", Locale("es", "MX")).format(Date(ahora)) +
            " (zona ${TimeZone.getDefault().id})"

    /**
     * Instante de una peticion: "en N minutos" o hora (HH:mm) + fecha opcional (yyyy-MM-dd).
     * Sin fecha y con la hora ya pasada, es la siguiente vez (manana); con una fecha concreta ya pasada, null.
     */
    fun cuando(hora: String, fecha: String, enMinutos: Int, ahora: Long): Long? {
        val tope = ahora + 366L * 24 * 60 * 60 * 1000
        if (enMinutos in 1..(7 * 24 * 60)) return (ahora + enMinutos * 60_000L).takeIf { it <= tope }
        val m = Regex("^(\\d{1,2}):(\\d{2})$").find(hora.trim()) ?: return null
        val h = m.groupValues[1].toInt(); val mi = m.groupValues[2].toInt()
        if (h > 23 || mi > 59) return null
        val c = Calendar.getInstance().apply { timeInMillis = ahora; set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        val f = Regex("^(\\d{4})-(\\d{2})-(\\d{2})$").find(fecha.trim())
        if (f != null) c.set(f.groupValues[1].toInt(), f.groupValues[2].toInt() - 1, f.groupValues[3].toInt())
        c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, mi)
        if (c.timeInMillis <= ahora + 30_000) {
            if (f != null) return null
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        return c.timeInMillis.takeIf { it <= tope }
    }
}
